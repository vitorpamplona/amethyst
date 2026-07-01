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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import androidx.sqlite.SQLiteConnection
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises a [SQLiteEventStore] created with `enableFullTextSearch = false`.
 *
 * The store must behave exactly like a normal store for every non-search
 * operation while (a) never creating the `event_fts` table or its delete
 * trigger and (b) treating any non-empty `search` term as matching nothing.
 * This is the mode a relay uses when it offloads NIP-50 search to an
 * external engine (e.g. Vespa) and doesn't want to pay the FTS index cost.
 */
class NoFullTextSearchTest {
    private val signer = NostrSignerSync()

    @BeforeTest
    fun setup() {
        Secp256k1Instance
    }

    private fun store() = SQLiteEventStore(dbName = null, enableFullTextSearch = false)

    private fun objectExists(
        db: SQLiteConnection,
        name: String,
    ): Boolean =
        db.prepare("SELECT name FROM sqlite_master WHERE name = ?").use { stmt ->
            stmt.bindText(1, name)
            stmt.step()
        }

    @Test
    fun testNoFtsTableOrTriggerIsCreated() =
        runBlocking {
            val store = store()
            try {
                // Force schema creation by opening the pool for a read.
                store.pool.useReader { db ->
                    assertFalse(objectExists(db, "event_fts"), "event_fts table must not exist when FTS is disabled")
                    assertFalse(objectExists(db, "fts_foreign_key"), "fts_foreign_key trigger must not exist when FTS is disabled")
                    // Sanity: the canonical table is still there.
                    assertTrue(objectExists(db, "event_headers"), "event_headers must still be created")
                }
            } finally {
                store.close()
            }
        }

    @Test
    fun testInsertAndPlainQueriesStillWork() =
        runBlocking {
            val store = store()
            try {
                val note = signer.sign(TextNoteEvent.build("hello nofts world", createdAt = TimeUtils.now()))
                store.insertEvent(note)

                // Query by id, kind, and author all resolve normally.
                store.assertQuery(note, Filter(ids = listOf(note.id)))
                store.assertQuery(note, Filter(kinds = listOf(TextNoteEvent.KIND)))
                store.assertQuery(note, Filter(authors = listOf(note.pubKey)))
                assertEquals(1, store.count(Filter(kinds = listOf(TextNoteEvent.KIND))))
            } finally {
                store.close()
            }
        }

    @Test
    fun testSearchFilterMatchesNothing() =
        runBlocking {
            val store = store()
            try {
                // Content clearly contains the term; with FTS off it must not match.
                val note = signer.sign(TextNoteEvent.build("searchable bitcoin content", createdAt = TimeUtils.now()))
                store.insertEvent(note)

                store.assertQuery(null, Filter(search = "bitcoin"))
                store.assertQuery(null, Filter(kinds = listOf(TextNoteEvent.KIND), search = "bitcoin"))
                store.assertQuery(null, Filter(authors = listOf(note.pubKey), search = "bitcoin"))
                assertEquals(0, store.count(Filter(search = "bitcoin")))
            } finally {
                store.close()
            }
        }

    @Test
    fun testMultiFilterUnionDropsSearchBranchButKeepsOthers() =
        runBlocking {
            val store = store()
            try {
                val note = signer.sign(TextNoteEvent.build("multi filter bitcoin note", createdAt = TimeUtils.now()))
                store.insertEvent(note)

                // The search branch contributes nothing; the kind branch still returns the note.
                val results =
                    store.query<Event>(
                        listOf(
                            Filter(search = "bitcoin"),
                            Filter(kinds = listOf(TextNoteEvent.KIND)),
                        ),
                    )
                assertEquals(1, results.size)
                assertEquals(note.id, results.first().id)

                assertEquals(
                    1,
                    store.count(
                        listOf(
                            Filter(search = "bitcoin"),
                            Filter(kinds = listOf(TextNoteEvent.KIND)),
                        ),
                    ),
                )
            } finally {
                store.close()
            }
        }

    @Test
    fun testDeleteWithSearchFilterRemovesNothing() =
        runBlocking {
            val store = store()
            try {
                val note = signer.sign(TextNoteEvent.build("delete me bitcoin", createdAt = TimeUtils.now()))
                store.insertEvent(note)

                store.delete(Filter(search = "bitcoin"))

                // The event survives — a search delete cannot resolve to any rows.
                store.assertQuery(note, Filter(ids = listOf(note.id)))
            } finally {
                store.close()
            }
        }

    @Test
    fun testEmptySearchStringImposesNoConstraint() =
        runBlocking {
            val store = store()
            try {
                val note = signer.sign(TextNoteEvent.build("empty search body", createdAt = TimeUtils.now()))
                store.insertEvent(note)

                // An empty search term is not a search; the filter still returns the note.
                store.assertQuery(note, Filter(kinds = listOf(TextNoteEvent.KIND), search = ""))
            } finally {
                store.close()
            }
        }

    @Test
    fun testReindexIsANoOp() =
        runBlocking {
            val store = store()
            try {
                val note = signer.sign(TextNoteEvent.build("reindex noop body", createdAt = TimeUtils.now()))
                store.insertEvent(note)

                // Neither reindex entry point should throw or create an index.
                store.reindexFullTextSearch()

                val progress = store.reindexFullTextSearch(resumeFrom = null, batchSize = 100)
                assertTrue(progress.done, "batched reindex must report done immediately when FTS is disabled")
                assertEquals(0, progress.processedThisBatch)

                store.pool.useReader { db ->
                    assertFalse(objectExists(db, "event_fts"), "reindex must not create event_fts when FTS is disabled")
                }

                // Search still matches nothing after the no-op rebuild.
                store.assertQuery(null, Filter(search = "reindex"))
            } finally {
                store.close()
            }
        }
}
