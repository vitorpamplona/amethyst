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
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdversarialSearchRankTest {
    private var idSeq = 0

    private fun hexId(n: Int): String {
        val s = n.toString(16)
        return "0".repeat(64 - s.length) + s
    }

    private fun ev(
        content: String,
        createdAt: Long,
        tags: Array<Array<String>>,
        kind: Int = 1,
    ): Event =
        EventFactory.create(
            hexId(++idSeq),
            "00".repeat(32),
            createdAt,
            kind,
            tags,
            content,
            "0".repeat(128),
        )

    // A filter with TWO tag keys + search → self-join on event_tags + rank projection.
    @Test
    fun twoTagKeysPlusSearchRanksAndDoesNotDuplicate() =
        runBlocking {
            val store = EventStore(dbName = null)
            try {
                // has both t=nostr and e=<x>, strong match
                val strong = ev("nostr nostr nostr", 1, arrayOf(arrayOf("t", "nostr"), arrayOf("e", "aa".repeat(32))))
                // has both tags, weak match (newer)
                val weak = ev("nostr among many other filler words here padding", 2, arrayOf(arrayOf("t", "nostr"), arrayOf("e", "aa".repeat(32))))
                // matches term + t but MISSING e tag → excluded by tagsAll-like AND of two keys
                val missingE = ev("nostr nostr nostr nostr", 3, arrayOf(arrayOf("t", "nostr")))
                store.batchInsert(listOf(strong, weak, missingE))

                val filter =
                    Filter(
                        search = "nostr",
                        tags = mapOf("t" to listOf("nostr"), "e" to listOf("aa".repeat(32))),
                        limit = 10,
                    )
                val ids = store.query<Event>(filter).map { it.id }
                // relevance order, both tags required, no duplicate rows
                assertEquals(listOf(strong.id, weak.id), ids, "two-tag + search must rank and require both tags with no dupes")

                // count parity with query for the same filter
                assertEquals(ids.size, store.count(filter), "count must equal query size for two-tag search")
            } finally {
                store.close()
            }
        }

    // count(filter) vs query(filter).size for a search+tag filter WITH a limit
    // smaller than the match set — NIP-45 parity.
    @Test
    fun countEqualsQuerySizeForSearchTagWithLimit() =
        runBlocking {
            val store = EventStore(dbName = null)
            try {
                val a = ev("apple apple apple", 1, arrayOf(arrayOf("t", "fruit")))
                val b = ev("apple apple filler", 2, arrayOf(arrayOf("t", "fruit")))
                val c = ev("apple filler filler words here", 3, arrayOf(arrayOf("t", "fruit")))
                store.batchInsert(listOf(a, b, c))

                val filter = Filter(search = "apple", tags = mapOf("t" to listOf("fruit")), limit = 2)
                val qSize = store.query<Event>(filter).size
                val cnt = store.count(filter)
                assertEquals(2, qSize, "query must honor limit")
                assertEquals(qSize, cnt, "count must match query size under a limit (NIP-45)")
            } finally {
                store.close()
            }
        }

    // Deleting a NON-searchable event (never had an FTS row) fires the
    // contentless delete trigger against a non-existent rowid.
    @Test
    fun deletingNonSearchableEventDoesNotCrash() =
        runBlocking {
            val store = EventStore(dbName = null)
            try {
                // kind 7 reaction is not a SearchableEvent → no FTS row inserted.
                val reaction = ev("+", 1, arrayOf(arrayOf("e", "bb".repeat(32))), kind = 7)
                val note = ev("hello searchable world", 2, arrayOf())
                store.batchInsert(listOf(reaction, note))

                // Trigger fires DELETE FROM event_fts WHERE rowid = <reaction row_id>
                // which never existed. Must be a harmless no-op.
                store.store.delete(reaction.id)

                // Searchable note still findable; store intact.
                assertEquals(note.id, store.query<Event>(Filter(search = "searchable")).single().id)
                assertTrue(store.query<Event>(Filter(ids = listOf(reaction.id))).isEmpty())
            } finally {
                store.close()
            }
        }
}
