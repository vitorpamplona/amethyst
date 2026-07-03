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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.LiveEventStore
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Decomposes the fixed per-REQ cost on SMALL results — relayBench shows
 * geode ~2.5× slower than strfry when a REQ returns ~20 events
 * (author-archive 1.18 vs 0.48 ms EOSE p50 at 50k), while geode WINS the
 * 500-event scenarios. With tiny result sets the per-REQ floor dominates,
 * so this isolates its stages:
 *
 *  A. raw store query (SQL + row decode) — the unavoidable part
 *  B. LiveEventStore.queryRaw to EOSE — adds live-subscription
 *     registration (FilterIndex), the per-REQ dedupe HashSet, and the
 *     search-drain check
 *  C. full session dispatch (RelaySession.receive of the REQ json) —
 *     adds command parse, policy, coroutine launch, frame assembly,
 *     and the send callback
 *
 * B−A is the live machinery; C−B is dispatch+serialization. Printed
 * per-stage medians tell which one owns the floor. No speed assertions —
 * numbers are informational; changes get A/B'd in relayBench.
 */
class SmallReqFloorBenchmark {
    companion object {
        const val EVENTS = 50_000
        const val AUTHORS = 2_500 // ~20 events per author, matching author-archive
        const val ROUNDS = 400
        const val WARMUP = 100
    }

    private fun hexId(seed: Int): String = seed.toString(16).padStart(64, '0')

    private fun pubkey(seed: Int): String = (seed % AUTHORS).toString(16).padStart(64, 'a')

    private val sig = "0".repeat(128)

    private fun event(seed: Int): Event =
        EventFactory.create(
            id = hexId(seed),
            pubKey = pubkey(seed),
            createdAt = 1_600_000_000L + (seed * 7919) % 1_000_000,
            kind = 1,
            tags = emptyArray(),
            content = "small req floor benchmark $seed",
            sig = sig,
        )

    private fun median(samples: LongArray): Double {
        samples.sort()
        return samples[samples.size / 2] / 1e6
    }

    @Test
    fun perReqFloorAt50k() =
        runBlocking {
            val store = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy(indexEventsByPubkeyAlone = true))
            (1..EVENTS).chunked(2000).forEach { chunk -> store.batchInsert(chunk.map { event(it) }) }

            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val live = LiveEventStore(store, IngestQueue(store, scope.coroutineContext))
            val ctx =
                object : RequestContext {
                    override val connectionId = 0L
                    override val policy = EmptyPolicy
                    override val authenticatedUsers = emptySet<String>()
                }
            val server = NostrServer(store = store, policyBuilder = { EmptyPolicy }, parentContext = scope.coroutineContext)

            fun filterFor(round: Int) = Filter(authors = listOf(pubkey(round)), kinds = listOf(1), limit = 50)

            fun filterJson(round: Int) = """["REQ","floor$round",{"authors":["${pubkey(round)}"],"kinds":[1],"limit":50}]"""

            // --- A: raw store query ---
            val a = LongArray(ROUNDS)
            var rowsA = 0
            repeat(WARMUP) { store.rawQuery(listOf(filterFor(it))) {} }
            repeat(ROUNDS) { round ->
                val t0 = System.nanoTime()
                var n = 0
                store.rawQuery(listOf(filterFor(round))) { n++ }
                a[round] = System.nanoTime() - t0
                rowsA += n
            }

            // --- B: backend queryRaw to EOSE (live machinery included) ---
            suspend fun timeBackend(round: Int): Long {
                val eose = CompletableDeferred<Long>()
                val t0 = System.nanoTime()
                val job =
                    scope.launch {
                        live.queryRaw(
                            ctx = ctx,
                            filters = listOf(filterFor(round)),
                            onEachStored = {},
                            onEachLive = {},
                            onEose = { eose.complete(System.nanoTime() - t0) },
                        )
                    }
                val nanos = eose.await()
                job.cancel()
                return nanos
            }
            repeat(WARMUP) { timeBackend(it) }
            val b = LongArray(ROUNDS)
            repeat(ROUNDS) { b[it] = timeBackend(it) }

            // --- C: full session dispatch, REQ json in → EOSE frame out ---
            suspend fun timeSession(round: Int): Long {
                val eose = CompletableDeferred<Long>()
                val t0 = System.nanoTime()
                val session =
                    server.connect { frame ->
                        if (frame.startsWith("[\"EOSE\"")) eose.complete(System.nanoTime() - t0)
                    }
                session.receive(filterJson(round))
                val nanos = eose.await()
                session.close()
                return nanos
            }
            repeat(WARMUP) { timeSession(it) }
            val c = LongArray(ROUNDS)
            repeat(ROUNDS) { c[it] = timeSession(it) }

            assertEquals(true, rowsA > 0, "author filters must return rows")

            val mA = median(a)
            val mB = median(b)
            val mC = median(c)
            println("SmallReqFloorBenchmark @ ${EVENTS / 1000}k events, ~${rowsA / ROUNDS} rows/req, medians of $ROUNDS")
            println("  A raw store query:        ${"%6.3f".format(mA)} ms")
            println("  B backend queryRaw→EOSE:  ${"%6.3f".format(mB)} ms  (live machinery +${"%6.3f".format(mB - mA)})")
            println("  C session REQ→EOSE:       ${"%6.3f".format(mC)} ms  (dispatch+frames +${"%6.3f".format(mC - mB)})")

            server.close()
            scope.cancel()
        }
}
