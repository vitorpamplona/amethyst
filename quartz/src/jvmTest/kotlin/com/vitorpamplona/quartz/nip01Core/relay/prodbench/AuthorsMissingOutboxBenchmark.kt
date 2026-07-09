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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Head-to-head for `IEventStore.authorsMissingOutbox()` — "give me every
 * author with events but no NIP-65 relay list (kind 10002)" — at 1,000,000
 * events, comparing the two implementations that ship:
 *
 *  - **generic** — the `IEventStore` interface default: query the 10002
 *    owners into a set, then stream EVERY event (`query(Filter())`) and keep
 *    the authors not in that set. Correct for any store, but it decodes all
 *    1M events off SQLite into `Event` objects.
 *  - **sqlite** — `EventStore.authorsMissingOutbox()`, an index-only `EXCEPT`
 *    over `event_headers` (all authors minus the 10002 owners) that never
 *    decodes an event, riding the `(kind, pubkey, created_at)` covering index.
 *
 * Corpus: the benchmark first **syncs a real sample from a popular relay**
 * (kind 1 notes + kind 10002 relay lists from [RELAY]) so the pubkey
 * cardinality, per-author event fan-out, tag/content sizes, and the fraction
 * of authors that actually advertise relays are all real. It then replicates
 * that sample — cloning each real event with a fresh id and timestamp but the
 * SAME pubkey/kind/tags/content — up to [TARGET] rows. Replication preserves
 * the real distinct-author set and the real 10002-owner set exactly (so the
 * answer is unchanged), it only grows each author's history the way a
 * long-lived relay would. A live 1M download is bandwidth-bound and isn't
 * what we're measuring; the query is.
 *
 * The store keeps the `indexEventsByPubkeyAlone` index a relay actually keeps
 * (this query is a relay / outbox-model concern) — that is the index the
 * `DISTINCT pubkey ... NOT EXISTS` scan rides. NIP-50 full-text indexing is
 * turned off: it is pure insert-path cost that neither query touches, so
 * dropping it just makes seeding 1M rows fast without changing either timing.
 *
 * Network + heavy, so gated like the other prod benches:
 *   ./gradlew :quartz:jvmTest --tests "*.AuthorsMissingOutboxBenchmark" -PprodRelayBench=1
 */
class AuthorsMissingOutboxBenchmark {
    companion object {
        const val RELAY = "wss://relay.damus.io"
        const val TARGET = 1_000_000
        const val SAMPLE_NOTES = 25_000
        const val SAMPLE_RELAY_LISTS = 15_000
        const val FETCH_TIMEOUT_MS = 90_000L
        const val INSERT_CHUNK = 2_000
        val SIG = "0".repeat(128)
    }

    private fun idFor(counter: Long): String = "%064x".format(counter)

    /** The generic path — a verbatim copy of the `IEventStore` interface default. */
    private suspend fun genericAuthorsMissingOutbox(store: EventStore): List<HexKey> {
        val withOutbox = HashSet<HexKey>()
        store.query<Event>(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND))) { withOutbox.add(it.pubKey) }

        val missing = LinkedHashSet<HexKey>()
        store.query<Event>(Filter()) { event ->
            if (event.kind != GiftWrapEvent.KIND && event.pubKey !in withOutbox) missing.add(event.pubKey)
        }
        return missing.toList()
    }

    @Test
    fun authorsMissingOutboxScaling() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("AuthorsMissingOutboxBenchmark skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        println("=== authorsMissingOutbox 1M benchmark === cores=${Runtime.getRuntime().availableProcessors()}")

        // ── 1. SYNC a real sample from a popular relay ──────────────────────
        val notes = ArrayList<Event>(SAMPLE_NOTES)
        val relayLists = ArrayList<Event>(SAMPLE_RELAY_LISTS)
        val relay = RELAY.normalizeRelayUrl()
        runBlocking {
            val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
            try {
                val t0 = System.nanoTime()
                client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = SAMPLE_NOTES)), FETCH_TIMEOUT_MS) { notes.add(it) }
                client.fetchAllPages(relay, listOf(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), limit = SAMPLE_RELAY_LISTS)), FETCH_TIMEOUT_MS) { relayLists.add(it) }
                println("  synced from $RELAY in %.1fs: %,d notes + %,d relay-lists".format((System.nanoTime() - t0) / 1e9, notes.size, relayLists.size))
            } finally {
                client.close()
            }
        }
        httpClient.dispatcher.executorService.shutdown()

        val pool = (notes + relayLists).distinctBy { it.id }
        require(pool.isNotEmpty()) { "relay returned no events — cannot build corpus" }

        val outboxOwners = relayLists.mapTo(HashSet()) { it.pubKey }
        val allAuthors = pool.mapTo(HashSet()) { it.pubKey }
        val expectedMissing = allAuthors - outboxOwners
        println(
            "  real sample: %,d events, %,d distinct authors, %,d with a 10002 (%.1f%%) → %,d missing".format(
                pool.size,
                allAuthors.size,
                outboxOwners.size,
                100.0 * outboxOwners.size / allAuthors.size,
                expectedMissing.size,
            ),
        )

        // ── 2. SCALE to TARGET by replicating the real sample ───────────────
        // kind 10002 is replaceable — one row survives per owner no matter how
        // many times it is re-cloned — so the store is filled with note (kind 1)
        // clones and each owner's relay list is inserted exactly once. That
        // lands a genuine TARGET rows while keeping the real author set and
        // outbox-owner set intact.
        val notePool = notes.distinctBy { it.id }
        require(notePool.isNotEmpty()) { "relay returned no kind-1 notes — cannot fill the corpus" }
        val relayListPerOwner = relayLists.associateBy { it.pubKey }.values.toList()
        val noteCloneTarget = (TARGET - relayListPerOwner.size).coerceAtLeast(0)

        // FS/FTS off, pubkey+created_at indexes on: representative of the query,
        // fast to seed. See the class KDoc.
        val strategy =
            DefaultIndexingStrategy(
                indexEventsByCreatedAtAlone = true,
                indexEventsByPubkeyAlone = true,
                useAndIndexIdOnOrderBy = true,
                indexFullTextSearch = false,
            )
        val dbFile = Files.createTempFile("authors-missing-outbox-", ".db")
        Files.deleteIfExists(dbFile)
        val store = EventStore(dbName = dbFile.toAbsolutePath().toString(), relay = null, indexStrategy = strategy)
        try {
            val baseTime = 1_600_000_000L
            var counter = 0L
            val seedT0 = System.nanoTime()
            val batch = ArrayList<Event>(INSERT_CHUNK)

            suspend fun flush() {
                if (batch.isNotEmpty()) {
                    store.batchInsert(batch)
                    batch.clear()
                }
            }
            runBlocking {
                // One relay list per owner (fresh id; content/tags preserved).
                for (src in relayListPerOwner) {
                    batch.add(EventFactory.create(idFor(++counter), src.pubKey, baseTime + counter, src.kind, src.tags, src.content, SIG))
                    if (batch.size == INSERT_CHUNK) flush()
                }
                // Fill the rest with note clones cycling the real notes.
                var made = 0L
                while (made < noteCloneTarget) {
                    val src = notePool[(made % notePool.size).toInt()]
                    batch.add(EventFactory.create(idFor(++counter), src.pubKey, baseTime + counter, src.kind, src.tags, src.content, SIG))
                    made++
                    if (batch.size == INSERT_CHUNK) flush()
                }
                flush()
            }
            val total = runBlocking { store.count(Filter()) }
            println("  seeded %,d rows (stored %,d) in %.1fs".format(counter, total, (System.nanoTime() - seedT0) / 1e9))

            // ── 3. MEASURE both implementations on the same store ──────────
            // Warm the page cache with one throwaway pass of each so neither
            // eats the cold-cache penalty for the other.
            runBlocking {
                store.authorsMissingOutbox()
                genericAuthorsMissingOutbox(store)
            }

            val runs = 3
            var sqliteResult: List<HexKey> = emptyList()
            var genericResult: List<HexKey> = emptyList()
            val sqliteMs = DoubleArray(runs)
            val genericMs = DoubleArray(runs)
            runBlocking {
                repeat(runs) { i ->
                    var t = System.nanoTime()
                    sqliteResult = store.authorsMissingOutbox()
                    sqliteMs[i] = (System.nanoTime() - t) / 1e6

                    t = System.nanoTime()
                    genericResult = genericAuthorsMissingOutbox(store)
                    genericMs[i] = (System.nanoTime() - t) / 1e6
                }
            }

            // Correctness: both must return the same author set, and it must
            // match the ground truth computed from the real sample.
            assertEquals(sqliteResult.toSet(), genericResult.toSet(), "sqlite and generic disagree")
            assertEquals(expectedMissing, sqliteResult.toSet(), "result does not match the seeded distribution")

            val sqliteBest = sqliteMs.min()
            val genericBest = genericMs.min()
            println("\n  result: %,d authors missing an outbox (of %,d distinct authors)".format(sqliteResult.size, allAuthors.size))
            println("  ── timings over $runs runs (best-of) ──")
            println("  generic (decode all %,d events)  best=%,9.1f ms  runs=%s".format(total, genericBest, genericMs.joinToString { "%.0f".format(it) }))
            println("  sqlite  (index-only EXCEPT)       best=%,9.1f ms  runs=%s".format(sqliteBest, sqliteMs.joinToString { "%.1f".format(it) }))
            println("  → sqlite is %.1f× faster at %,d events".format(genericBest / sqliteBest, total))
        } finally {
            store.close()
            listOf("", "-wal", "-shm").forEach {
                Files.deleteIfExists(
                    java.nio.file.Path
                        .of(dbFile.toAbsolutePath().toString() + it),
                )
            }
        }
    }
}
