/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.RequestSubscriptionState
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Compares the three candidate designs for `PoolRequests`' subscription-state lock,
 * under the real production topology: R relay-consumer coroutines on a
 * limited-parallelism dispatcher (`Dispatchers.IO` = 64) all delivering EVENTs for a
 * small number of subscription ids.
 *
 *  - **PER_SUB_BLOCKING** — today: one blocking lock per subId, shared by every relay
 *    that sub runs on. Waiters park, which fixed the CPU burn, but a parked waiter
 *    still OCCUPIES its dispatcher thread.
 *  - **PER_SUB_MUTEX** — kotlinx `Mutex`: a waiter *suspends* and releases its thread.
 *    Requires making the whole listener chain `suspend` (262 overrides, 110 call sites).
 *  - **STRIPED** — one lock per (subId, relay). Every critical section in PoolRequests
 *    is already scoped to a single relay, so this removes the contention outright and
 *    needs no API change.
 *
 * The metric that matters is NOT lock throughput — it is whether unrelated work can
 * still get a thread while the lock is contended. `bystanderLatency` models that: a
 * task that never touches the lock, submitted to the same dispatcher.
 */
class LockDesignComparisonBenchmark {
    private val relays = 191
    private val dispatcherThreads = 64
    private val durationMs = 3000L

    /** One relay's slice of a subscription's state — all real fields are relay-keyed. */
    private class PerRelay {
        val lock = ReentrantLock()
        var status: Int = 0
        var filters: List<String>? = null
        var lastKnown: List<String>? = null
    }

    private class Striped {
        val perRelay = ConcurrentHashMap<Int, PerRelay>()

        inline fun <R> withLock(
            relay: Int,
            block: (PerRelay) -> R,
        ): R {
            val s = perRelay.computeIfAbsent(relay) { PerRelay() }
            s.lock.lock()
            try {
                return block(s)
            } finally {
                s.lock.unlock()
            }
        }
    }

    private class PerSubBlocking {
        val lock = ReentrantLock()
        val status = HashMap<Int, Int>()
        val filters = HashMap<Int, List<String>>()
    }

    private class PerSubMutex {
        val mutex = Mutex()
        val status = HashMap<Int, Int>()
        val filters = HashMap<Int, List<String>>()
    }

    private fun report(
        name: String,
        subs: Int,
        ops: Long,
        bystanderSamples: List<Long>,
    ) {
        val sorted = bystanderSamples.sorted()
        val p50 = sorted[sorted.size / 2] / 1000.0
        val p99 = sorted[(sorted.size * 99) / 100] / 1000.0
        val max = sorted.last() / 1000.0
        println(
            "%-18s subs=%-3d ops/s=%,10d   bystander p50=%8.1fus p99=%9.1fus max=%9.1fus  (n=%d)".format(
                name,
                subs,
                ops * 1000 / durationMs,
                p50,
                p99,
                max,
                sorted.size,
            ),
        )
    }

    @Test
    fun compareDesigns() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("compareDesigns skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }
        println("relays=$relays dispatcherThreads=$dispatcherThreads window=${durationMs}ms")
        println("bystander = a task that NEVER touches the lock, on the same dispatcher.")
        println("Lower bystander latency = the lock is not stealing dispatcher threads.\n")
        for (subs in listOf(1, 4, 16)) {
            runPerSubBlocking(subs)
            runPerSubMutex(subs)
            runStriped(subs)
            runRealStriped(subs)
            println()
        }
    }

    private fun runPerSubBlocking(subs: Int) =
        runBlocking {
            @Suppress("DEPRECATION")
            val dispatcher = Dispatchers.IO.limitedParallelism(dispatcherThreads)
            val states = Array(subs) { PerSubBlocking() }
            val stop = AtomicBoolean(false)
            val ops = AtomicLong(0)
            val bystander = ArrayList<Long>()
            val jobs =
                (0 until relays).map { relay ->
                    launch(dispatcher) {
                        var n = 0L
                        while (!stop.get()) {
                            val s = states[relay % subs]
                            s.lock.lock()
                            try {
                                s.status[relay] = 1
                                s.filters[relay] = SAMPLE
                                s.status[relay]
                            } finally {
                                s.lock.unlock()
                            }
                            n++
                            // Models `for (message in incomingMessages)`: every real
                            // iteration suspends, letting the dispatcher multiplex.
                            kotlinx.coroutines.yield()
                        }
                        ops.addAndGet(n)
                    }
                }
            val by =
                launch(dispatcher) {
                    while (!stop.get()) {
                        val t = System.nanoTime()
                        kotlinx.coroutines.yield()
                        bystander.add(System.nanoTime() - t)
                    }
                }
            Thread.sleep(durationMs)
            stop.set(true)
            jobs.forEach { it.join() }
            by.join()
            report("PER_SUB_BLOCKING", subs, ops.get(), bystander.ifEmpty { listOf(0L) })
        }

    private fun runPerSubMutex(subs: Int) =
        runBlocking {
            @Suppress("DEPRECATION")
            val dispatcher = Dispatchers.IO.limitedParallelism(dispatcherThreads)
            val states = Array(subs) { PerSubMutex() }
            val stop = AtomicBoolean(false)
            val ops = AtomicLong(0)
            val bystander = ArrayList<Long>()
            val jobs =
                (0 until relays).map { relay ->
                    launch(dispatcher) {
                        var n = 0L
                        while (!stop.get()) {
                            val s = states[relay % subs]
                            s.mutex.withLock {
                                s.status[relay] = 1
                                s.filters[relay] = SAMPLE
                                s.status[relay]
                            }
                            n++
                            // Models `for (message in incomingMessages)`: every real
                            // iteration suspends, letting the dispatcher multiplex.
                            kotlinx.coroutines.yield()
                        }
                        ops.addAndGet(n)
                    }
                }
            val by =
                launch(dispatcher) {
                    while (!stop.get()) {
                        val t = System.nanoTime()
                        kotlinx.coroutines.yield()
                        bystander.add(System.nanoTime() - t)
                    }
                }
            Thread.sleep(durationMs)
            stop.set(true)
            jobs.forEach { it.join() }
            by.join()
            report("PER_SUB_MUTEX", subs, ops.get(), bystander.ifEmpty { listOf(0L) })
        }

    private fun runStriped(subs: Int) =
        runBlocking {
            @Suppress("DEPRECATION")
            val dispatcher = Dispatchers.IO.limitedParallelism(dispatcherThreads)
            val states = Array(subs) { Striped() }
            val stop = AtomicBoolean(false)
            val ops = AtomicLong(0)
            val bystander = ArrayList<Long>()
            val jobs =
                (0 until relays).map { relay ->
                    launch(dispatcher) {
                        var n = 0L
                        while (!stop.get()) {
                            states[relay % subs].withLock(relay) { s ->
                                s.status = 1
                                s.filters = SAMPLE
                                s.lastKnown = SAMPLE
                            }
                            n++
                            // Models `for (message in incomingMessages)`: every real
                            // iteration suspends, letting the dispatcher multiplex.
                            kotlinx.coroutines.yield()
                        }
                        ops.addAndGet(n)
                    }
                }
            val by =
                launch(dispatcher) {
                    while (!stop.get()) {
                        val t = System.nanoTime()
                        kotlinx.coroutines.yield()
                        bystander.add(System.nanoTime() - t)
                    }
                }
            Thread.sleep(durationMs)
            stop.set(true)
            jobs.forEach { it.join() }
            by.join()
            report("STRIPED", subs, ops.get(), bystander.ifEmpty { listOf(0L) })
        }

    /**
     * Same topology, but driving the REAL shipped [RequestSubscriptionState] (striped per
     * relay) instead of a prototype — so the measured win is a property of the code that
     * ships, not of this file.
     */
    private fun runRealStriped(subs: Int) =
        runBlocking {
            @Suppress("DEPRECATION")
            val dispatcher = Dispatchers.IO.limitedParallelism(dispatcherThreads)
            val states = Array(subs) { RequestSubscriptionState<Int>() }
            val stop = AtomicBoolean(false)
            val ops = AtomicLong(0)
            val bystander = ArrayList<Long>()
            val filters = listOf(Filter(kinds = listOf(1)))
            val jobs =
                (0 until relays).map { relay ->
                    launch(dispatcher) {
                        var n = 0L
                        val state = states[relay % subs]
                        while (!stop.get()) {
                            state.withLock(relay) {
                                state.onNewEvent(relay)
                                state.currentState(relay)
                                state.onOpenReq(relay, filters)
                                state.lastKnownFilterStates(relay)
                            }
                            n++
                            kotlinx.coroutines.yield()
                        }
                        ops.addAndGet(n)
                    }
                }
            val by =
                launch(dispatcher) {
                    while (!stop.get()) {
                        val t = System.nanoTime()
                        kotlinx.coroutines.yield()
                        bystander.add(System.nanoTime() - t)
                    }
                }
            Thread.sleep(durationMs)
            stop.set(true)
            jobs.forEach { it.join() }
            by.join()
            report("REAL_STRIPED", subs, ops.get(), bystander.ifEmpty { listOf(0L) })
        }

    companion object {
        private val SAMPLE = listOf("kinds:1", "authors:abc")
    }
}
