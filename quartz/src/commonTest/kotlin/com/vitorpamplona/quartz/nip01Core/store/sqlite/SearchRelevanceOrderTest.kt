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
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NIP-50: search results are ordered by "quality of search result" (relevance),
 * **not** by `created_at`, and the limit is applied after the score. The store
 * uses FTS5 bm25 (`ORDER BY event_fts.rank`), so a stronger match outranks a
 * newer one.
 */
class SearchRelevanceOrderTest {
    private val signer = NostrSignerSync()

    private fun note(
        content: String,
        createdAt: Long,
    ) = signer.sign(TextNoteEvent.build(content, createdAt = createdAt))

    @Test
    fun strongerMatchOutranksNewer() =
        runBlocking {
            val store = EventStore(dbName = null)
            try {
                // Older, but the term appears 3× in a short doc → most relevant.
                val strong = note("bitcoin bitcoin bitcoin", createdAt = 1_000)
                // Newer, term once buried in a long doc → least relevant.
                val weak = note("bitcoin is one topic among many other unrelated words here padding", createdAt = 9_000)
                // Newer still, but does not match at all.
                val nonMatch = note("completely different subject entirely", createdAt = 9_999)

                store.insert(weak)
                store.insert(strong)
                store.insert(nonMatch)

                val results = store.query<Event>(Filter(search = "bitcoin", limit = 10)).map { it.id }
                // Relevance order (strong before weak) — the opposite of
                // created_at DESC (which would put weak first) — and the
                // non-matching note is absent.
                assertEquals(listOf(strong.id, weak.id), results)
            } finally {
                store.close()
            }
        }

    @Test
    fun limitAppliesAfterRelevanceScore() =
        runBlocking {
            val store = EventStore(dbName = null)
            try {
                // Three docs of decreasing relevance but increasing created_at,
                // so created_at order and relevance order are exact opposites.
                val best = note("apple apple apple apple", createdAt = 1)
                val mid = note("apple apple filler words", createdAt = 2)
                val worst = note("apple among lots of other unrelated filler words here", createdAt = 3)
                store.insert(best)
                store.insert(mid)
                store.insert(worst)

                // limit=2 after scoring keeps the two MOST RELEVANT, not the
                // two newest.
                val top2 = store.query<Event>(Filter(search = "apple", limit = 2)).map { it.id }
                assertEquals(listOf(best.id, mid.id), top2)
            } finally {
                store.close()
            }
        }

    /** A `#t` tag makes this a `search + tag` filter — the combined path. */
    private var idSeq = 0

    private fun hexId(n: Int): String {
        val s = n.toString(16)
        return "0".repeat(64 - s.length) + s
    }

    private fun tagged(
        content: String,
        createdAt: Long,
        topic: String,
    ): Event =
        EventFactory.create(
            hexId(++idSeq),
            "00".repeat(32),
            createdAt,
            1,
            arrayOf(arrayOf("t", topic)),
            content,
            "0".repeat(128),
        )

    @Test
    fun searchWithATagIsAlsoRelevanceOrdered() =
        runBlocking {
            val store = EventStore(dbName = null)
            try {
                // All tagged #t=nostr; relevance decreases as created_at rises,
                // so created_at order would be the exact reverse of relevance.
                val strong = tagged("nostr nostr nostr", createdAt = 1, topic = "nostr")
                val weak = tagged("nostr among many other unrelated filler words here padding", createdAt = 2, topic = "nostr")
                // Matches the term but wrong tag → excluded by the tag filter.
                val wrongTag = tagged("nostr nostr nostr nostr", createdAt = 3, topic = "other")
                // Right tag but doesn't match the term → excluded by search.
                val noMatch = tagged("bitcoin only", createdAt = 4, topic = "nostr")

                store.batchInsert(listOf(strong, weak, wrongTag, noMatch))

                val filter = Filter(search = "nostr", tags = mapOf("t" to listOf("nostr")), limit = 10)
                val ids = store.query<Event>(filter).map { it.id }
                assertEquals(listOf(strong.id, weak.id), ids, "search + tag must be relevance-ordered, tag-scoped")

                // limit after score keeps the most relevant one.
                val top1 = store.query<Event>(filter.copy(limit = 1)).map { it.id }
                assertEquals(listOf(strong.id), top1)
            } finally {
                store.close()
            }
        }
}
