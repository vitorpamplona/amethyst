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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [FilterMatcher] was rewritten to allocate nothing per event. This pins the rewrite against the
 * implementation it replaced, by running both over randomly generated events and filters and
 * asserting they never disagree.
 *
 * A hand-written example set would not have caught the cases that actually worry me here — an
 * empty value list, a tag key the event does not carry at all, a duplicated tag value, a
 * `tagsAll` whose values span two separate tags — so the reference implementation is kept
 * verbatim below and fuzzed against instead.
 */
class FilterMatcherEquivalenceTest {
    /** The implementation as it stood before the rewrite, kept byte-for-byte as the oracle. */
    private fun reference(
        event: Event,
        ids: List<String>? = null,
        authors: List<String>? = null,
        kinds: List<Int>? = null,
        tags: Map<String, List<String>>? = null,
        tagsAll: Map<String, List<String>>? = null,
        since: Long? = null,
        until: Long? = null,
    ): Boolean {
        if (ids?.contains(event.id) == false) return false
        if (kinds?.contains(event.kind) == false) return false
        if (authors?.contains(event.pubKey) == false) return false
        tags?.forEach { tag ->
            val valueSet = tag.value.toSet()
            if (!event.tags.any { it.size > 1 && it[0] == tag.key && it[1] in valueSet }) return false
        }
        tagsAll?.forEach { tag ->
            val eventTagValueSet =
                event.tags.mapNotNullTo(mutableSetOf()) {
                    if (it.size > 1 && it[0] == tag.key) it[1] else null
                }
            for (tagValue in tag.value) {
                if (tagValue !in eventTagValueSet) return false
            }
        }
        if (event.createdAt !in (since ?: Long.MIN_VALUE)..(until ?: Long.MAX_VALUE)) return false
        return true
    }

    private val ids = listOf("a".repeat(64), "b".repeat(64), "c".repeat(64))
    private val keys = listOf("d".repeat(64), "e".repeat(64))
    private val tagNames = listOf("e", "p", "t", "h")
    private val tagValues = listOf("alpha", "beta", "gamma")

    private fun <T> Random.pick(from: List<T>) = from[nextInt(from.size)]

    private fun <T> Random.maybeList(from: List<T>): List<T>? =
        when (nextInt(4)) {
            0 -> null
            // An empty list is the edge the two implementations could most plausibly differ on.
            1 -> emptyList()
            else -> List(nextInt(1, 3)) { pick(from) }
        }

    private fun Random.maybeTags(): Map<String, List<String>>? {
        if (nextInt(3) == 0) return null
        return buildMap {
            repeat(nextInt(1, 3)) {
                put(pick(tagNames), maybeList(tagValues) ?: emptyList())
            }
        }
    }

    private fun Random.event(): Event {
        val tags =
            Array(nextInt(0, 5)) {
                when (nextInt(5)) {
                    // Short and empty tags: the `size > 1` guard has to hold for both.
                    0 -> arrayOf(pick(tagNames))
                    1 -> emptyArray()
                    else -> arrayOf(pick(tagNames), pick(tagValues))
                }
            }
        return Event(pick(ids), pick(keys), nextLong(100, 110), nextInt(0, 3), tags, "", "")
    }

    @Test
    fun theRewriteNeverDisagreesWithTheImplementationItReplaced() {
        val random = Random(20260907)
        repeat(20000) { round ->
            val event = random.event()
            val filterIds = random.maybeList(ids)
            val filterAuthors = random.maybeList(keys)
            val filterKinds = random.maybeList(listOf(0, 1, 2))
            val filterTags = random.maybeTags()
            val filterTagsAll = random.maybeTags()
            val since = if (random.nextInt(3) == 0) random.nextLong(100, 110) else null
            val until = if (random.nextInt(3) == 0) random.nextLong(100, 110) else null

            assertEquals(
                reference(event, filterIds, filterAuthors, filterKinds, filterTags, filterTagsAll, since, until),
                FilterMatcher.match(event, filterIds, filterAuthors, filterKinds, filterTags, filterTagsAll, since, until),
                "round $round disagreed for event(kind=${event.kind}, createdAt=${event.createdAt}, " +
                    "tags=${event.tags.joinToString { it.joinToString(",") }}) against " +
                    "ids=$filterIds authors=$filterAuthors kinds=$filterKinds tags=$filterTags " +
                    "tagsAll=$filterTagsAll since=$since until=$until",
            )
        }
    }

    @Test
    fun theBoundsOfSinceAndUntilAreBothInclusive() {
        val event = Event(ids[0], keys[0], 100, 1, emptyArray(), "", "")
        assertEquals(true, FilterMatcher.match(event, since = 100))
        assertEquals(true, FilterMatcher.match(event, until = 100))
        assertEquals(false, FilterMatcher.match(event, since = 101))
        assertEquals(false, FilterMatcher.match(event, until = 99))
    }
}
