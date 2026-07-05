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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproduces the geode↔geode sync event-loss at its real layer: the concurrent
 * [IngestQueue] pipeline (parallel Schnorr verify across cores + greedy-drain
 * group-commit + a deferred-FTS catch-up worker taking the writer in the gaps),
 * driven by windowed concurrent publishes — exactly what a delta transfer of
 * ~40k events over one connection does. The sequential store path is already
 * proven lossless ([BatchInsertLossTest]); this adds the concurrency.
 *
 * Every submitted event's outcome is recorded; afterwards every event the queue
 * reported `Accepted` must be queryable by id. Any Accepted-but-absent id is the
 * ack-without-persist bug.
 *
 * Corpus via `-DlossCorpus`; defaults to the clean 200k slice. Skips if absent.
 */
@OptIn(ExperimentalAtomicApi::class)
class ConcurrentIngestLossTest {
    private val corpusPath =
        System.getProperty("lossCorpus")
            ?: "relayBench/.corpus-cache/clean-200k-no-vanish-no-expiry.ndjson"

    @Test
    fun concurrentIngestNeverLosesAcceptedEvents() =
        runBlocking {
            Secp256k1Instance
            val file = File(corpusPath).takeIf { it.exists() } ?: File("../$corpusPath")
            if (!file.exists()) {
                println("─ ConcurrentIngestLossTest: corpus not found — skipping ─")
                return@runBlocking
            }
            val events =
                file.useLines { lines ->
                    lines
                        .mapNotNull { line -> if (line.isBlank()) null else runCatching { OptimizedJsonMapper.fromJson(line) }.getOrNull() }
                        .toList()
                }
            println("─ ConcurrentIngestLossTest: ${events.size} events from ${file.name} ─")

            val store =
                EventStore(
                    dbName = null,
                    indexStrategy =
                        DefaultIndexingStrategy(
                            indexEventsByCreatedAtAlone = true,
                            indexEventsByPubkeyAlone = true,
                            indexFullTextSearch = true,
                            deferFullTextSearchIndexing = true,
                            maintainLiveNegentropyIndex = true,
                        ),
                )

            val queueJob = Job()
            val ingest =
                IngestQueue(
                    store = store,
                    parentContext = Dispatchers.Default + queueJob,
                    verify = { it.verify() },
                )

            // Competing background writer, as the geode process runs it: the
            // deferred-FTS catch-up worker takes the pool's writer in the gaps
            // while ingest is committing. If the writer mutex leaks, a
            // background transaction could clobber ingest rows.
            val bgRunning =
                java.util.concurrent.atomic
                    .AtomicBoolean(true)
            val bgJob =
                launch(Dispatchers.Default) {
                    while (bgRunning.get()) {
                        runCatching { store.ftsCatchUp() }
                        delay(2)
                    }
                }

            // Mirror/relay-to-relay trust path: skip re-verifying events from a
            // peer that already verified them (what geode's mirror worker does).
            // Keeps this fast and matches the sync ingest path.
            val skipVerify = true

            val accepted = ConcurrentHashMap.newKeySet<String>()
            val rejected = AtomicInteger()
            val window = Semaphore(200)
            val done = CompletableDeferred<Unit>()
            val remaining = AtomicInteger(events.size)

            for (e in events) {
                window.acquire()
                ingest.submit(e, skipVerify) { outcome ->
                    when (outcome) {
                        is IEventStore.InsertOutcome.Accepted -> accepted.add(e.id)
                        is IEventStore.InsertOutcome.Rejected -> rejected.incrementAndGet()
                    }
                    window.release()
                    if (remaining.decrementAndGet() == 0) done.complete(Unit)
                }
            }
            done.await()
            bgRunning.set(false)
            bgJob.cancel()

            // Every Accepted id must be present. Kind-1 / regular events are
            // never displaced or deleted in this corpus; a replaceable that was
            // Accepted then superseded by a newer version in the same run is a
            // legitimate absence, so only assert on non-replaceable kinds.
            val byId = events.associateBy { it.id }
            var lost = 0
            for (id in accepted) {
                val ev = byId[id] ?: continue
                if (ev.kind.let { it == 0 || it == 3 || it in 10000..19999 || it in 30000..39999 }) continue
                if (store.count(Filter(ids = listOf(id))) == 0) {
                    lost++
                    if (lost <= 10) println("  ✗ LOST accepted id=${id.take(12)}… kind=${ev.kind} created_at=${ev.createdAt}")
                }
            }
            val stored = store.count(Filter())
            println("  submitted=${events.size} accepted=${accepted.size} rejected=${rejected.get()} stored=$stored lostAcceptedRegular=$lost")

            ingest.close()
            queueJob.cancel()
            store.close()
            assertEquals(0, lost, "concurrent ingest reported Accepted for $lost regular events that were not persisted")
        }
}
