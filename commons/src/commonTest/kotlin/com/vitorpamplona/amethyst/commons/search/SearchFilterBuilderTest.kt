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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchFilterBuilderTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        const val NOTE = "note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv"
        val NOTES = listOf(1)
    }

    private fun build(
        input: String,
        kinds: List<Int>? = null,
        limit: Int = 100,
    ) = SearchFilterBuilder.build(QueryParser.parse(input), kinds, limit)

    private fun Filter.tag(name: String) = tags?.get(name)

    @Test
    fun anEmptyQueryAsksNothing() {
        assertTrue(build("").isEmpty())
    }

    @Test
    fun plainTextIsOneFilterCarryingOnlyTheSearchString() {
        val filters = build("bitcoin lightning", NOTES)
        assertEquals(1, filters.size)
        assertEquals("bitcoin lightning", filters[0].search)
        assertEquals(NOTES, filters[0].kinds)
        assertNull(filters[0].tags)
    }

    @Test
    fun fromBecomesAuthorsAndNotASearchTerm() {
        val filters = build("from:$NPUB zaps", NOTES)
        assertEquals(1, filters.size)
        assertEquals(1, filters[0].authors?.size)
        assertEquals("zaps", filters[0].search)
    }

    @Test
    fun toBecomesAPeeTagAndAnEventPointerAnEeTag() {
        val filters = build("to:$NPUB to:$NOTE", NOTES)
        assertEquals(1, filters.size)
        assertEquals(1, filters[0].tag("p")?.size)
        assertEquals(1, filters[0].tag("e")?.size)
        assertNull(filters[0].search)
    }

    @Test
    fun aDateWindowRidesEveryFilterOfAUnion() {
        val filters = build("#bitcoin since:2026-01-01 until:2026-12-31", NOTES)
        assertTrue(filters.size > 1, "a hashtag fans out")
        assertTrue(filters.all { it.since != null && it.until != null }, "the window must ride every arm")
    }

    @Test
    fun aHashtagAsksTheThreeTagsThatCarryIt() {
        val filters = build("#bitcoin", NOTES + CommentEvent.KIND)
        // #t on the event, #l on a label about it, and #I/#i on comments written about it.
        assertTrue(filters.any { it.tag("t")?.contains("bitcoin") == true })
        assertTrue(filters.any { it.tag("l")?.contains("bitcoin") == true })
        assertTrue(filters.any { it.tag("I")?.contains("#bitcoin") == true })
        assertTrue(filters.any { it.tag("i")?.contains("#bitcoin") == true })
    }

    @Test
    fun theCommentArmsAreSkippedWhenTheKindWindowExcludesThem() {
        val filters = build("#bitcoin", NOTES)
        assertTrue(filters.none { it.kinds == listOf(CommentEvent.KIND) })
    }

    @Test
    fun everyCasingOfAHashtagIsAsked() {
        val values = build("#Bitcoin", NOTES).first { it.tag("t") != null }.tag("t")!!
        assertTrue(values.containsAll(listOf("Bitcoin", "bitcoin", "BITCOIN")), values.toString())
    }

    @Test
    fun aLabelNamesItsOwnKindOverTheCallers() {
        val filters = build("label:review/app", NOTES)
        val label = filters.first { it.kinds == listOf(LabelEvent.KIND) }
        assertEquals(listOf("review/app"), label.tag("l")?.filter { it == "review/app" })
    }

    @Test
    fun aGroupAsksTheAitchTagAndItsOwnMetadata() {
        val filters = build("group:dev", NOTES)
        assertTrue(filters.any { it.tag("h") == listOf("dev") })
        val meta = filters.first { it.kinds == listOf(GroupMetadataEvent.KIND) }
        assertEquals(listOf("dev"), meta.tag("d"))
        // A secondary arm takes a smaller slice: a relay applies `limit` per filter.
        assertTrue((meta.limit ?: 0) < 100)
    }

    @Test
    fun aScopeIsOnlyEverACommentsQuestion() {
        val filters = build("site:example.com", NOTES)
        assertTrue(filters.isNotEmpty())
        assertTrue(filters.all { it.kinds == listOf(CommentEvent.KIND) })
        assertTrue(filters.any { it.tag("I")?.contains("https://example.com") == true })
    }

    @Test
    fun nip50ExtensionsRideTheSearchStringNotAFilterField() {
        val filters = build("bitcoin lang:en domain:nostr.com", NOTES)
        assertEquals("bitcoin language:en domain:nostr.com", filters[0].search)
    }

    @Test
    fun anUnresolvedNameAsksNothingRatherThanEverything() {
        // `from:vitor` is a name the picker has not turned into a key. It is not a filter field,
        // and a filter constrained only by its kinds is an unbounded REQ to every relay.
        assertTrue(build("from:vitor", NOTES).isEmpty())
        // Once there is something askable alongside it, the query is sent — minus the name.
        val withText = build("from:vitor bitcoin", NOTES)
        assertEquals(1, withText.size)
        assertEquals("bitcoin", withText[0].search)
        assertNull(withText[0].authors)
    }

    @Test
    fun theGroupMetadataArmDoesNotInheritTheQuerysTextOrAuthor() {
        // A kind-39000 event is written by the host relay and carries the room's name, not the
        // searched author or the searched words — inheriting either finds nothing.
        val filters = build("group:dev from:$NPUB bitcoin", NOTES)
        val meta = filters.first { it.kinds == listOf(GroupMetadataEvent.KIND) }
        assertNull(meta.search)
        assertNull(meta.authors)
        assertEquals(listOf("dev"), meta.tag("d"))
    }

    @Test
    fun everyArmOfAUnionCarriesTheMentionTagsAndAuthors() {
        val filters = build("from:$NPUB to:$NOTE #bitcoin", NOTES)
        assertTrue(filters.size > 1)
        assertTrue(filters.all { it.authors?.size == 1 && it.tag("e")?.size == 1 })
    }

    // ---- what the search screen's empty state reads ----------------------------------------
    //
    // A query that asks nothing gets "type something to search"; one that asks and comes back
    // with nothing gets "no results". Telling those apart is this method's answer, so the two
    // messages cannot swap places without one of these failing.

    @Test
    fun aBareKindWindowAsksNothing() {
        // "Every recent article" is an unbounded REQ, not a search — which is why a screen that
        // seeds only its kind opens with a chip and no results.
        assertTrue(build("kind:article").isEmpty())
        assertTrue(build("kind:picture").isEmpty())
        assertTrue(build("kind:reply").isEmpty())
    }

    @Test
    fun aKindWithAnythingElseAsksSomething() {
        assertTrue(build("kind:article bitcoin").isNotEmpty())
        assertTrue(build("kind:article from:$NPUB").isNotEmpty())
        assertTrue(build("kind:article #nostr").isNotEmpty())
        assertTrue(build("kind:article since:2026-01-01").isNotEmpty())
    }

    @Test
    fun theOtherSeededWindowsAllAskSomethingOnTheirOwn() {
        // Only a kind window is unaskable alone; every other seed a screen can hand over names
        // something a relay indexes, so those open with results rather than a hint.
        assertTrue(build("from:$NPUB").isNotEmpty())
        assertTrue(build("#nostr").isNotEmpty())
        assertTrue(build("geo:9q8yy").isNotEmpty())
        assertTrue(build("group:dev").isNotEmpty())
        assertTrue(build("to:$NPUB").isNotEmpty())
    }
}
