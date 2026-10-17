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
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * STORE-F12: a negative `limit` is clamped to zero rows. SQLite reads `LIMIT -1` as "no
 * limit", so passing it through let one filter read the whole table past any page cap.
 */
class NegativeLimitTest : BaseDBTest() {
    val signer = NostrSignerSync()

    @Test
    fun negativeLimitReturnsNothingOnEveryPath() =
        forEachDB { db ->
            val note1 = signer.sign(TextNoteEvent.build("test1 #nostr", createdAt = 1) { hashtag("nostr") })
            val note2 = signer.sign(TextNoteEvent.build("test2 #nostr", createdAt = 2) { hashtag("nostr") })
            val blog = signer.sign(LongFormContentEvent.build("blog body", "title", dTag = "blog", createdAt = 3))

            db.insert(note1)
            db.insert(note2)
            db.insert(blog)

            // simple header-only path
            db.assertQuery(null, Filter(kinds = listOf(TextNoteEvent.KIND), limit = -1))
            db.assertQuery(null, Filter(limit = -1))
            // tag-join path
            db.assertQuery(null, Filter(tags = mapOf("t" to listOf("nostr")), limit = -1))
            // search path
            db.assertQuery(null, Filter(search = "test1", limit = -1))
            // union path
            val union = listOf(Filter(kinds = listOf(TextNoteEvent.KIND), limit = -1), Filter(tags = mapOf("t" to listOf("nostr")), limit = -1))
            assertEquals(0, db.query<Event>(union).size)
            assertEquals(0, db.count(union))

            // a negative limit must not turn a delete into an unbounded one either
            db.delete(Filter(kinds = listOf(TextNoteEvent.KIND), limit = -1))
            db.delete(Filter(tags = mapOf("t" to listOf("nostr")), limit = -1))
            assertEquals(3, db.count(Filter()))
        }
}
