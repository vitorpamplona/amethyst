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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contrast the two NIP-50 result orderings the contentless FTS index enables.
 * The FTS rowid is `event_headers.row_id` (ingestion order), so:
 *  - default: `ORDER BY created_at DESC` — strict recency.
 *  - [IndexingStrategy.searchOrderByRowId]: `ORDER BY event_fts.rowid DESC` —
 *    ingestion order (early-terminating, corpus-independent).
 *
 * Events are inserted in an order that deliberately disagrees with their
 * `created_at`, so the two orderings produce visibly different results.
 */
class SearchOrderByRowIdTest {
    private val signer = NostrSignerSync()

    private fun note(createdAt: Long) = signer.sign(TextNoteEvent.build("uniqorder token body", createdAt = createdAt))

    private fun store(rowIdOrder: Boolean) =
        EventStore(
            dbName = null,
            indexStrategy = DefaultIndexingStrategy(searchOrderByRowId = rowIdOrder),
        )

    @Test
    fun defaultOrdersByCreatedAt_rowIdFlagOrdersByIngestion() =
        runBlocking {
            // created_at: A newest, B oldest, C middle. Inserted A, B, C — so
            // ingestion (row_id) order is A < B < C.
            val byCreatedAt = store(rowIdOrder = false)
            val byRowId = store(rowIdOrder = true)
            try {
                val a = note(300)
                val b = note(100)
                val c = note(200)
                for (store in listOf(byCreatedAt, byRowId)) {
                    store.insert(a)
                    store.insert(b)
                    store.insert(c)
                }

                val filter = Filter(search = "uniqorder", limit = 10)

                // created_at DESC: A(300), C(200), B(100)
                assertEquals(
                    listOf(a.id, c.id, b.id),
                    byCreatedAt.query<Event>(filter).map { it.id },
                    "default search must order by created_at DESC",
                )

                // rowid DESC = ingestion order reversed: C, B, A
                assertEquals(
                    listOf(c.id, b.id, a.id),
                    byRowId.query<Event>(filter).map { it.id },
                    "searchOrderByRowId must order by ingestion (row_id) DESC",
                )
            } finally {
                byCreatedAt.close()
                byRowId.close()
            }
        }

    @Test
    fun rowIdOrderStillHonorsLimitAndSecondaryFilters() =
        runBlocking {
            val store = store(rowIdOrder = true)
            try {
                val notes = (0 until 6).map { note(1_700_000_000L + it) }
                notes.forEach { store.insert(it) }

                // limit slices the newest-ingested 3.
                val top3 = store.query<Event>(Filter(search = "uniqorder", limit = 3)).map { it.id }
                assertEquals(notes.takeLast(3).reversed().map { it.id }, top3)

                // kind filter still applies alongside rowid ordering.
                val wrongKind = store.query<Event>(Filter(kinds = listOf(30023), search = "uniqorder", limit = 10))
                assertEquals(0, wrongKind.size)
            } finally {
                store.close()
            }
        }
}
