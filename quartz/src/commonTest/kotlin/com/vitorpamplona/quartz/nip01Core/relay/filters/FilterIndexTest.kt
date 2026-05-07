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
package com.vitorpamplona.quartz.nip01Core.relay.filters

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterIndexTest {
    private val authorA = "a".repeat(64)
    private val authorB = "b".repeat(64)
    private val authorC = "c".repeat(64)

    private val pTag1 = "1".repeat(64)
    private val pTag2 = "2".repeat(64)

    private fun event(
        id: String = "e".repeat(64),
        pubkey: String = authorA,
        kind: Int = 1,
        tags: Array<Array<String>> = emptyArray(),
        createdAt: Long = 1_700_000_000,
    ) = Event(
        id = id,
        pubKey = pubkey,
        createdAt = createdAt,
        kind = kind,
        tags = tags,
        content = "",
        sig = "",
    )

    /** Distinct identity wrapper so tests can hold a stable handle. */
    private data class Sub(
        val name: String,
    )

    @Test
    fun emptyIndexReturnsNoCandidates() {
        val index = FilterIndex<Sub>()
        assertTrue(index.isEmpty())
        assertTrue(index.candidatesFor(event()).isEmpty())
    }

    @Test
    fun authorFilterMatchesByAuthor() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(Filter(authors = listOf(authorA)), s)

        assertTrue(s in index.candidatesFor(event(pubkey = authorA)))
        assertFalse(s in index.candidatesFor(event(pubkey = authorB)))
    }

    @Test
    fun multipleAuthorsAllRoutedToSameSubscriber() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(Filter(authors = listOf(authorA, authorB)), s)

        assertTrue(s in index.candidatesFor(event(pubkey = authorA)))
        assertTrue(s in index.candidatesFor(event(pubkey = authorB)))
        assertFalse(s in index.candidatesFor(event(pubkey = authorC)))
    }

    @Test
    fun kindFilterMatchesByKind() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(Filter(kinds = listOf(1, 7)), s)

        assertTrue(s in index.candidatesFor(event(kind = 1)))
        assertTrue(s in index.candidatesFor(event(kind = 7)))
        assertFalse(s in index.candidatesFor(event(kind = 30023)))
    }

    @Test
    fun authorWinsOverKindWhenBothPresent() {
        // Filter has authors AND kinds — the more-selective dimension
        // (authors) is used. An event with the right kind but a
        // different author must NOT appear in candidates, otherwise
        // the index didn't actually narrow.
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(Filter(authors = listOf(authorA), kinds = listOf(1)), s)

        assertTrue(s in index.candidatesFor(event(pubkey = authorA, kind = 1)))
        // Wrong author, right kind — index excludes correctly.
        assertFalse(s in index.candidatesFor(event(pubkey = authorB, kind = 1)))
        // Right author, wrong kind — index includes; Filter.match
        // would post-reject. Exposed candidate is acceptable.
        assertTrue(s in index.candidatesFor(event(pubkey = authorA, kind = 7)))
    }

    @Test
    fun idFilterMostSelective() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        val targetId = "9".repeat(64)
        index.register(Filter(ids = listOf(targetId), kinds = listOf(1)), s)

        assertTrue(s in index.candidatesFor(event(id = targetId)))
        assertFalse(s in index.candidatesFor(event(id = "8".repeat(64))))
    }

    @Test
    fun tagFilterMatchesEventsCarryingTheTag() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(Filter(tags = mapOf("p" to listOf(pTag1))), s)

        val matching = event(tags = arrayOf(arrayOf("p", pTag1)))
        val nonMatching = event(tags = arrayOf(arrayOf("p", pTag2)))

        assertTrue(s in index.candidatesFor(matching))
        assertFalse(s in index.candidatesFor(nonMatching))
    }

    @Test
    fun unindexedFilterMatchesEverything() {
        // A filter with no narrowing field (e.g. just `since`) lives
        // in the unindexed pool. Every event must include it.
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(Filter(since = 1L), s)

        assertTrue(s in index.candidatesFor(event(pubkey = authorA, kind = 1)))
        assertTrue(s in index.candidatesFor(event(pubkey = authorB, kind = 30023)))
    }

    @Test
    fun registerUnindexedExplicit() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.registerUnindexed(s)

        assertTrue(s in index.candidatesFor(event(pubkey = authorA)))
        assertTrue(s in index.candidatesFor(event(pubkey = authorB)))
    }

    @Test
    fun unregisterRemovesFromAllBuckets() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(Filter(authors = listOf(authorA, authorB)), s)
        assertEquals(1, index.size())

        index.unregister(s)
        assertEquals(0, index.size())
        assertFalse(s in index.candidatesFor(event(pubkey = authorA)))
        assertFalse(s in index.candidatesFor(event(pubkey = authorB)))
    }

    @Test
    fun unregisterOfUnknownIsNoOp() {
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.unregister(s) // should not throw
        assertEquals(0, index.size())
    }

    @Test
    fun multiFilterRegistrationOrsAllSelections() {
        // Subscriber wants events from authorA OR kind 30023.
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        index.register(
            filters =
                listOf(
                    Filter(authors = listOf(authorA)),
                    Filter(kinds = listOf(30023)),
                ),
            subscriber = s,
        )

        assertTrue(s in index.candidatesFor(event(pubkey = authorA, kind = 1)))
        assertTrue(s in index.candidatesFor(event(pubkey = authorB, kind = 30023)))
        assertFalse(s in index.candidatesFor(event(pubkey = authorB, kind = 1)))
    }

    @Test
    fun multipleSubscribersUnionInCandidates() {
        val index = FilterIndex<Sub>()
        val s1 = Sub("s1")
        val s2 = Sub("s2")
        val s3 = Sub("s3")

        index.register(Filter(authors = listOf(authorA)), s1)
        index.register(Filter(authors = listOf(authorB)), s2)
        index.register(Filter(kinds = listOf(1)), s3)

        val cands = index.candidatesFor(event(pubkey = authorA, kind = 1))
        // s1 hits via author, s3 via kind, s2 must be excluded.
        assertTrue(s1 in cands)
        assertTrue(s3 in cands)
        assertFalse(s2 in cands)
    }

    @Test
    fun forEachVisitsEverySubscriberOnce() {
        val index = FilterIndex<Sub>()
        val s1 = Sub("s1")
        val s2 = Sub("s2")
        val s3 = Sub("s3")
        index.register(Filter(authors = listOf(authorA, authorB)), s1)
        index.register(Filter(kinds = listOf(1)), s2)
        index.registerUnindexed(s3)

        val visited = mutableListOf<Sub>()
        index.forEach { visited.add(it) }

        assertEquals(3, visited.size)
        assertEquals(setOf(s1, s2, s3), visited.toSet())
    }

    @Test
    fun registerThenUnregisterLeavesNoStaleBuckets() {
        // Churn test — repeatedly add and remove a subscriber and
        // verify the index ends up empty (no leaked bucket entries).
        val index = FilterIndex<Sub>()
        val s = Sub("s")
        repeat(100) {
            index.register(
                Filter(authors = listOf(authorA), kinds = listOf(1, 7), tags = mapOf("p" to listOf(pTag1))),
                s,
            )
            index.unregister(s)
        }
        assertTrue(index.isEmpty())
        assertTrue(index.candidatesFor(event(pubkey = authorA)).isEmpty())
    }
}
