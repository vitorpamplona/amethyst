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
package com.vitorpamplona.geode.mirror

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Measures geode's mirror sync throughput (events/second) pulling a large set
 * into an empty downstream geode over the real WebSocket transport.
 *
 * Source selection:
 *  - default (`-DsyncSourceUrl` unset): an **in-process geode** upstream
 *    ([KtorRelay]) preloaded with N events — the **geode→geode** number.
 *  - `-DsyncSourceUrl=ws://host:port`: mirror that external relay (e.g. a strfry
 *    loaded with the corpus) — the **strfry→geode** number. `-DsyncExpect=N`
 *    sets the convergence target.
 *
 * Size with `-DsyncN` (default 1,000,000). Timed from worker start to the
 * downstream reaching the target (or plateauing).
 */
class MirrorSyncThroughputTest {
    // geode's real relay config (deferred FTS, live negentropy index) — not the
    // default EventStore(null), whose synchronous FTS tokenization on every
    // insert would dominate ingest and misrepresent the sync rate.
    // `-DsyncLiveIndex=false` disables the live negentropy index to isolate its
    // O(n)-insert cost during out-of-order backfill.
    private val liveIndex = System.getProperty("syncLiveIndex")?.toBoolean() ?: true
    private val strategy =
        DefaultIndexingStrategy(
            indexEventsByCreatedAtAlone = true,
            indexEventsByPubkeyAlone = true,
            indexFullTextSearch = true,
            deferFullTextSearchIndexing = true,
            maintainLiveNegentropyIndex = liveIndex,
        )
    private val downstreamStore = EventStore(dbName = null, indexStrategy = strategy)
    private val downstream =
        RelayEngine(url = "ws://127.0.0.1:7899/".normalizeRelayUrl(), store = downstreamStore, parallelVerify = true)

    private var upstreamStore: EventStore? = null
    private var upstream: RelayEngine? = null
    private var server: KtorRelay? = null
    private var worker: MirrorWorker? = null

    @AfterTest
    fun tearDown() {
        worker?.close()
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        upstream?.close()
        downstream.close()
    }

    private val hex = "0123456789abcdef"

    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun hex64(
        salt: Long,
        index: Int,
    ): String {
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(salt * 1_000_003 + index.toLong() * 4 + w)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                out[(w * 8 + b) * 2] = hex[byte ushr 4]
                out[(w * 8 + b) * 2 + 1] = hex[byte and 0xF]
            }
        }
        return String(out)
    }

    @Test
    fun mirrorSyncThroughput() =
        runBlocking {
            val n = System.getProperty("syncN")?.toInt() ?: 1_000_000
            val externalUrl = System.getProperty("syncSourceUrl")
            val expect = System.getProperty("syncExpect")?.toInt() ?: n

            val sourceUrl: String
            if (externalUrl != null) {
                sourceUrl = externalUrl
                println("─ MirrorSyncThroughput: mirroring EXTERNAL source $externalUrl, expect $expect ─")
            } else {
                // Source is infrastructure (serves the REQ); keep its preload
                // fast by not maintaining the live index here.
                val srcStrategy =
                    DefaultIndexingStrategy(
                        indexEventsByCreatedAtAlone = true,
                        indexEventsByPubkeyAlone = true,
                        indexFullTextSearch = true,
                        deferFullTextSearchIndexing = true,
                        maintainLiveNegentropyIndex = false,
                    )
                val store = EventStore(dbName = null, indexStrategy = srcStrategy).also { upstreamStore = it }
                val now = TimeUtils.now()
                val sig = "f".repeat(128)
                val batch = ArrayList<Event>(10_000)
                var loaded = 0
                for (i in 0 until n) {
                    batch.add(
                        Event(
                            id = hex64(7, i),
                            pubKey = hex64(3, i % 20_000),
                            createdAt = now - 60 - (i % 20_000),
                            kind = 1,
                            tags = emptyArray(),
                            content = "e$i",
                            sig = sig,
                        ),
                    )
                    if (batch.size == 10_000) {
                        store.batchInsert(batch)
                        loaded += batch.size
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) store.batchInsert(batch)
                val eng = RelayEngine(url = "ws://127.0.0.1:7898/".normalizeRelayUrl(), store = store).also { upstream = it }
                val srv = KtorRelay(eng, host = "127.0.0.1", port = 0).start().also { server = it }
                val port =
                    srv.url
                        .normalizeRelayUrl()
                        .url
                        .substringAfterLast(':')
                        .trimEnd('/')
                        .toInt()
                sourceUrl = "ws://127.0.0.1:$port/"
                println("─ MirrorSyncThroughput: geode→geode, in-process source preloaded $loaded on $port ─")
            }

            val startNanos = System.nanoTime()
            worker =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(
                                url = sourceUrl.normalizeRelayUrl(),
                                trusted = true,
                                backfillSeconds = 90_000,
                            ),
                        ),
                    server = downstream.server,
                ).also { it.start() }

            var reached = 0
            var last = -1
            var stable = 0
            var lastLog = startNanos
            var lastLogCount = 0
            withTimeoutOrNull(600_000) {
                while (true) {
                    val c = downstreamStore.count(Filter())
                    reached = c
                    if (c >= expect) break
                    val nowNanos = System.nanoTime()
                    if (nowNanos - lastLog >= 3_000_000_000L) {
                        val instRate = ((c - lastLogCount) / ((nowNanos - lastLog) / 1e9)).toLong()
                        println("    …$c/$expect  (%,d ev/s inst)".format(instRate))
                        lastLog = nowNanos
                        lastLogCount = c
                    }
                    if (c == last) {
                        if (++stable >= 30) break // ~15s no progress
                    } else {
                        stable = 0
                        last = c
                    }
                    delay(500)
                }
            }
            val secs = (System.nanoTime() - startNanos) / 1e9
            val eps = (reached / secs).toLong()
            val src = if (externalUrl != null) "external" else "geode"
            println("════════════════════════════════════════════")
            println("$src→geode: synced $reached/$expect in %.1fs  =>  %,d events/s".format(secs, eps))
            println("════════════════════════════════════════════")
        }
}
