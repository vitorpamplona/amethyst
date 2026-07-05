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
package com.vitorpamplona.geode.tools

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.RelayIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * Boots a real geode relay (geode's default indexing: FTS + live negentropy
 * index) preloaded with a corpus of nostr events read from an NDJSON file, then
 * serves forever so external clients — `strfry sync`, another geode, the
 * relayBench negentropy sink — can reconcile against it.
 *
 * A benchmark-only source: it exists so the negentropy sync comparison has a
 * geode relay holding the same corpus a strfry source does, reachable over the
 * production WebSocket transport.
 *
 * Usage: `CorpusServerMain <port> <corpus.ndjson> [maxCount]`
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: CorpusServerMain <port> <corpus.ndjson> [maxCount]")
        return
    }
    val port = args[0].toInt()
    val corpus = File(args[1])
    val maxCount = args.getOrNull(2)?.toInt() ?: Int.MAX_VALUE

    // File-backed so a 1M in-memory corpus here doesn't compete for RAM with an
    // in-memory sink in the same box during the comparison. Fresh each boot.
    val dbFile = "/tmp/geode-source-$port.sqlite"
    listOf("", "-wal", "-shm").forEach { File(dbFile + it).delete() }
    val store = EventStore(dbName = dbFile, indexStrategy = RelayIndexingStrategy)
    val engine = RelayEngine(url = "ws://127.0.0.1:$port/".normalizeRelayUrl(), store = store)

    println("CorpusServerMain: loading up to $maxCount events from ${corpus.name}…")
    val loaded =
        runBlocking {
            var total = 0
            val batch = ArrayList<Event>(10_000)
            corpus.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (total >= maxCount) break
                    if (line.isBlank()) continue
                    val event = runCatching { OptimizedJsonMapper.fromJson(line) }.getOrNull() ?: continue
                    batch.add(event)
                    if (batch.size == 10_000) {
                        store.batchInsert(batch)
                        total += batch.size
                        batch.clear()
                        if (total % 200_000 == 0) println("  …loaded $total")
                    }
                }
            }
            if (batch.isNotEmpty()) {
                store.batchInsert(batch)
                total += batch.size
            }
            total
        }
    val count = runBlocking { store.count(Filter()) }

    val server = KtorRelay(engine, host = "127.0.0.1", port = port).start()
    println("CorpusServerMain: READY port=$port loaded=$loaded distinct=$count")

    // Serve until the JVM is killed.
    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
            engine.close()
        },
    )
    CountDownLatch(1).await()
}
