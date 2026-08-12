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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Reproduces the production ANR seen on a Pixel 8 (2026-08-03, anr_2026-08-03-12-55-26-256):
 * 191 live relay sockets feed EVENT frames for the same handful of subscription ids, so
 * dozens of DefaultDispatcher workers pile onto ONE [RequestSubscriptionState] busy-wait
 * lock. In that trace 51 of 52 runnable workers sat in the inlined spin loop while the
 * single lock holder was parked in `WaitingForGcToComplete`.
 *
 * Two things are measured:
 *  - [contendedThroughput]: aggregate critical sections/s as the contender count grows past
 *    the core count (the "negative scaling" the 2026-07-02 plan measured with 4 feeders,
 *    re-run at production fan-out).
 *  - [victimLatencyUnderSpin]: what an UNRELATED thread (stand-in for the UI thread) sees
 *    while the spinners run — this is the ANR mechanism, not the lock throughput.
 */
class SpinLockConvoyBenchmark {
    private val cores = Runtime.getRuntime().availableProcessors()

    /**
     * Every waiter targets ONE reference on purpose. The lock is striped per relay, so
     * spreading threads over distinct relays would put them on different stripes and
     * this benchmark would measure nothing — the point here is the primitive's behaviour
     * when contention DOES land on a single stripe.
     */
    private val hotRelay = 0

    /** Mirrors the real critical section: a handful of map reads/writes, no I/O. */
    private fun criticalSection(
        state: RequestSubscriptionState<Int>,
        relay: Int,
    ) {
        state.onNewEvent(relay)
        state.currentState(relay)
        state.lastKnownFilterStates(relay)
    }

    @Test
    fun contendedThroughput() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("contendedThroughput skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }
        println("cores = $cores")
        println("threads |   ops/s   | vs 1 thread")
        var baseline = 0.0
        for (threads in listOf(1, 2, 4, 8, 16, 32, 52)) {
            val state = RequestSubscriptionState<Int>()
            val stop = AtomicBoolean(false)
            val ops = AtomicLong(0)
            val start = CountDownLatch(1)
            val workers =
                (0 until threads).map { id ->
                    thread {
                        start.await()
                        var local = 0L
                        while (!stop.get()) {
                            state.withLock(hotRelay) { criticalSection(state, hotRelay) }
                            local++
                        }
                        ops.addAndGet(local)
                    }
                }
            val t0 = System.nanoTime()
            start.countDown()
            Thread.sleep(2000)
            stop.set(true)
            workers.forEach { it.join() }
            val secs = (System.nanoTime() - t0) / 1e9
            val rate = ops.get() / secs
            if (threads == 1) baseline = rate
            println("%7d | %9.0f | %.2fx".format(threads, rate, rate / baseline))
        }
    }

    /**
     * REGRESSION GUARD: contended waiters on [RequestSubscriptionState.withLock] must PARK,
     * never busy-wait. Fails if the lock is ever turned back into a spin lock.
     *
     * This is the signature that identified the production ANR: in
     * `anr_2026-08-03-12-55-26-256`, 37 of 52 runnable workers sat at one obfuscated line of
     * `PoolRequests.onIncomingMessage` and 12 more at one line of `syncState$lambda$0` — all
     * `state=R`, `sCount=0`, burning 596% CPU while the lock holder was stuck in
     * `WaitingForGcToComplete`. With a spin lock this test observes ~40/40 waiters RUNNABLE;
     * with a parking lock it observes 0.
     */
    @Test
    fun contendedWaitersParkInsteadOfSpinning() {
        val spinners = 40
        val state = RequestSubscriptionState<Int>()
        val stop = AtomicBoolean(false)
        val start = CountDownLatch(1)

        val holder =
            thread(name = "holder") {
                start.await()
                while (!stop.get()) {
                    state.withLock(hotRelay) {
                        val t = System.nanoTime()
                        while (System.nanoTime() - t < 5_000_000) { /* hold 5ms */ }
                    }
                }
            }
        val threads =
            (0 until spinners).map { id ->
                thread(name = "spinner-$id") {
                    start.await()
                    while (!stop.get()) {
                        state.withLock(hotRelay) { criticalSection(state, hotRelay) }
                    }
                }
            }
        start.countDown()
        Thread.sleep(300)

        // Sample every spinner's stack, exactly like an ANR dump would.
        val points = HashMap<String, Int>()
        var runnable = 0
        threads.forEach { t ->
            if (t.state == Thread.State.RUNNABLE) runnable++
            val top = t.stackTrace.firstOrNull { it.className.contains("SpinLockConvoyBenchmark") || it.className.contains("RequestSubscriptionState") }
            if (top != null) {
                val key = "${top.className.substringAfterLast('.')}.${top.methodName}:${top.lineNumber}"
                points[key] = (points[key] ?: 0) + 1
            }
        }
        stop.set(true)
        threads.forEach { it.join() }
        holder.join()

        println("sampled $spinners waiters: RUNNABLE=$runnable, distinct program points=${points.size}")
        points.entries.sortedByDescending { it.value }.forEach { println("   ${it.value}x ${it.key}") }

        // A parked waiter reports WAITING, so the parking lock measures ~0 here while a
        // spin lock measures ~100%. The line sits at 50% deliberately: it separates the
        // two cases by a mile and leaves headroom on a loaded CI box, where a few waiters
        // can legitimately be mid-acquire when we sample.
        assertTrue(
            "Expected contended waiters to park, but $runnable/$spinners were RUNNABLE — " +
                "RequestSubscriptionState.withLock looks like it is busy-waiting again. " +
                "See PlatformLock's kdoc: this is what caused anr_2026-08-03-12-55-26-256.",
            runnable <= spinners / 2,
        )
    }

    @Test
    fun victimLatencyUnderSpin() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("victimLatencyUnderSpin skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }
        // One holder that briefly stalls inside the critical section (in production: a GC
        // pause, or simply being descheduled). Everyone else spins.
        val spinners = 52
        val state = RequestSubscriptionState<Int>()
        val stop = AtomicBoolean(false)

        fun measureVictim(label: String) {
            // The "UI thread": allocates and measures its own scheduling latency.
            val samples = ArrayList<Long>()
            repeat(200) {
                val t = System.nanoTime()
                // trivial allocation work, like a Compose semantics traversal step
                val junk = ArrayList<String>(64)
                repeat(64) { i -> junk.add("node$i") }
                Thread.yield()
                samples.add(System.nanoTime() - t)
            }
            samples.sort()
            println(
                "%-22s p50=%6.0fus p90=%7.0fus p99=%8.0fus max=%8.0fus".format(
                    label,
                    samples[samples.size / 2] / 1000.0,
                    samples[(samples.size * 90) / 100] / 1000.0,
                    samples[(samples.size * 99) / 100] / 1000.0,
                    samples.last() / 1000.0,
                ),
            )
        }

        measureVictim("idle (no spinners)")

        val start = CountDownLatch(1)
        val holder =
            thread {
                start.await()
                while (!stop.get()) {
                    state.withLock(hotRelay) {
                        // Simulate the holder losing its core / waiting on GC mid-section.
                        val t = System.nanoTime()
                        while (System.nanoTime() - t < 2_000_000) { /* 2ms stall */ }
                    }
                    Thread.sleep(1)
                }
            }
        val threads =
            (0 until spinners).map { id ->
                thread {
                    start.await()
                    while (!stop.get()) {
                        state.withLock(hotRelay) { criticalSection(state, hotRelay) }
                    }
                }
            }
        start.countDown()
        Thread.sleep(500)
        measureVictim("$spinners spinners")
        stop.set(true)
        threads.forEach { it.join() }
        holder.join()

        measureVictim("after (no spinners)")
    }
}
