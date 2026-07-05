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
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproduces the ingest event-loss found in the geode↔geode relayBench sync:
 * one geode stored 19 fewer events than its peer, all kind-1 with empty tags,
 * reported `OK true` (Accepted) yet never persisted. This drives the exact
 * store path — `batchInsertEvents` with geode's indexing strategy, in 64-event
 * batches like the group-commit `IngestQueue` — against the same corpus, and
 * checks every batch for a kind-1 (regular, never replaced/deleted here) that
 * was Accepted but is absent afterwards. On a hit it dumps the whole batch so
 * the co-batched culprit is visible.
 *
 * Corpus via `-DlossCorpus=/path.ndjson` (a plain NDJSON of events); defaults
 * to the checked-out clean 200k slice. Skips cleanly if the file is absent.
 */
class BatchInsertLossTest {
    private val corpusPath =
        System.getProperty("lossCorpus")
            ?: "relayBench/.corpus-cache/clean-200k-no-vanish-no-expiry.ndjson"

    private fun geodeStore() =
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

    @Test
    fun batchInsertNeverLosesAcceptedRegularEvents() =
        runBlocking {
            Secp256k1Instance
            val file = File(corpusPath).takeIf { it.exists() } ?: File("../$corpusPath")
            if (!file.exists()) {
                println("─ BatchInsertLossTest: corpus not found at $corpusPath — skipping ─")
                return@runBlocking
            }

            val events =
                file.useLines { lines ->
                    lines
                        .mapNotNull { line ->
                            if (line.isBlank()) {
                                null
                            } else {
                                runCatching { OptimizedJsonMapper.fromJson(line) }.getOrNull()
                            }
                        }.toList()
                }
            println("─ BatchInsertLossTest: ${events.size} events from ${file.name} ─")

            val store = geodeStore()
            val batchSize = 64
            var lostBatches = 0
            var totalLost = 0

            var i = 0
            while (i < events.size) {
                val batch = events.subList(i, minOf(i + batchSize, events.size))
                val outcomes = store.batchInsert(batch)

                // Kind-1 with a non-empty tag set never collapses (not
                // replaceable) and is never deleted here (corpus has no kind-5
                // / kind-62), so an Accepted kind-1 MUST be queryable after.
                for ((j, e) in batch.withIndex()) {
                    if (e.kind != 1) continue
                    if (outcomes[j] !is IEventStore.InsertOutcome.Accepted) continue
                    val present = store.query<Event>(Filter(ids = listOf(e.id))).isNotEmpty()
                    if (!present) {
                        totalLost++
                        if (lostBatches < 5) {
                            println("  ✗ LOST kind-1 id=${e.id.take(12)}… (batch index $j) — batch composition:")
                            batch.forEachIndexed { k, be ->
                                val tags = be.tags.mapNotNull { it.getOrNull(0) }.joinToString(",")
                                val mark = if (be.id == e.id) " <-- lost" else ""
                                println("      [$k] kind=${be.kind} tags=[$tags] outcome=${outcomes[k]::class.simpleName}$mark")
                            }
                        }
                        lostBatches++
                    }
                }
                i += batchSize
            }

            val stored = store.count(Filter())
            println("  stored total: $stored, lost Accepted kind-1: $totalLost across $lostBatches detections")
            store.close()
            assertEquals(0, totalLost, "batchInsert reported Accepted for $totalLost kind-1 events that were not persisted")
        }
}
