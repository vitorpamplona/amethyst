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
package com.vitorpamplona.quartz.experimental.decentralizedLists

import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.HeaderResolution
import com.vitorpamplona.quartz.experimental.decentralizedLists.concepts.WordWrapper
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.itemKinds
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.ItemKindTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentList
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.EventTagging
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.TaggingTarget
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritType
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/** Regressions found in the audit: each failed before its fix. */
class AuditRegressionTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val ta = "d".repeat(64)
    private val other = "f".repeat(64)

    private fun item(tags: Array<Array<String>>) = assertIs<AddressableListItemEvent>(EventFactory.create("e".repeat(64), alice, 1L, 39999, tags, "", ""))

    // Event Taggings § "Concept namespaces & federation": one nostr-event-tag `z` per namespace.
    @Test
    fun federatedTaggingsDoNotMistakeOtherNamespacesForTaggingHeaders() {
        val tagging =
            item(
                arrayOf(
                    arrayOf("d", "event-tag-awesome-tag-cccccccc-aaaaaaaa"),
                    arrayOf("a", "39999:$bob:good-tag"),
                    arrayOf("z", "39998:$ta:nostr-event-tag"),
                    arrayOf("z", "39998:$other:nostr-event-tag"),
                    arrayOf("z", "39999:$bob:tagging:awesome-tag-tagging"),
                ),
            )

        val parsed = EventTagging.parse(tagging, setOf("39998:$ta:nostr-event-tag", "39998:$other:nostr-event-tag"))!!
        assertEquals(listOf("39999:$bob:tagging:awesome-tag-tagging"), parsed.taggingHeaders)
    }

    @Test
    fun federatedTaggingsCanBeBuiltForSeveralNamespaces() {
        val template =
            EventTagging.build(
                nostrEventTagConcepts = listOf("39998:$ta:nostr-event-tag", "39998:$other:nostr-event-tag"),
                taggingHeader = Address(39999, bob, "tagging:awesome-tag-tagging"),
                tagSlug = "awesome-tag",
                target = TaggingTarget.ByEventId("1".repeat(64)),
                asserter = alice,
            )
        assertEquals(
            listOf("39998:$ta:nostr-event-tag", "39998:$other:nostr-event-tag", "39999:$bob:tagging:awesome-tag-tagging"),
            template.tags.filter { it[0] == "z" }.map { it[1] },
        )
    }

    // Two versions of the same personal header must resolve to the newest, not whichever came first.
    @Test
    fun governingHeaderIsTheNewestVersionOfTheWinningAuthor() {
        val old = assertIs<AddressableListHeaderEvent>(EventFactory.create("1".repeat(64), alice, 1L, 39998, arrayOf(arrayOf("d", "dogs")), "", ""))
        val new = assertIs<AddressableListHeaderEvent>(EventFactory.create("2".repeat(64), alice, 5L, 39998, arrayOf(arrayOf("d", "dogs")), "", ""))
        assertEquals(new, HeaderResolution.governing(alice, "dogs", null, listOf(old, new)))
    }

    // Inherit-From: element 2 is an a-tag; the value form is closed.
    @Test
    fun bTagsWhoseTargetIsNotACoordinateAreIgnored() {
        val node = item(arrayOf(arrayOf("d", "x"), arrayOf("b", "hello", "inherit"), arrayOf("b", "39998:$bob:dogs", "inherit")))
        assertEquals(listOf("39998:$bob:dogs"), node.inheritFromTargets(InheritType.INHERIT))
    }

    @Test
    fun itemKindIsTagAgreesWithParse() {
        val tag = arrayOf("item-kind", "-1")
        assertEquals(ItemKindTag.parse(tag) != null, ItemKindTag.isTag(tag))
        assertEquals(emptyList(), arrayOf(tag).itemKinds())
    }

    // A long list name containing a colon is still a name, and never reaches the address parser.
    @Test
    fun longNamesWithColonsStayNames() {
        val name = "a list of: " + "x".repeat(70)
        val node = item(arrayOf(arrayOf("d", "x"), arrayOf("z", name)))
        assertIs<ParentList.Name>(node.parentLists().single())
        assertFalse(name in node.linkedAddressIds())
    }

    // The `d` must separate two addressable targets. kind 39999 is addressable, so a colliding
    // `d` means the second tagging silently replaces the first.
    @Test
    fun twoAddressableTargetsByTheSameAuthorGetDifferentDTags() {
        val first = TaggingTarget.ByAddress(Address(39999, bob, "good-tag"))
        val second = TaggingTarget.ByAddress(Address(39999, bob, "other-tag"))

        assertNotEquals(
            EventTagging.dTag("awesome-tag", first, alice),
            EventTagging.dTag("awesome-tag", second, alice),
        )
    }

    // Same idea across the other two coordinate segments: only one of the three may differ.
    @Test
    fun addressableTargetsDifferingOnlyByKindGetDifferentDTags() {
        assertNotEquals(
            EventTagging.dTag("awesome-tag", TaggingTarget.ByAddress(Address(39999, bob, "tag")), alice),
            EventTagging.dTag("awesome-tag", TaggingTarget.ByAddress(Address(30023, bob, "tag")), alice),
        )
    }

    @Test
    fun addressableTargetsDifferingOnlyByAuthorGetDifferentDTags() {
        assertNotEquals(
            EventTagging.dTag("awesome-tag", TaggingTarget.ByAddress(Address(39999, bob, "tag")), alice),
            EventTagging.dTag("awesome-tag", TaggingTarget.ByAddress(Address(39999, other, "tag")), alice),
        )
    }

    // ...while the `d` stays deterministic, which is what lets a re-tag replace its own assertion
    // instead of piling up a second one. Pinned against SHA-256 prefixes of the coordinates
    // computed outside this codebase, so the test cross-checks the derivation rather than
    // restating it: a change of hash input would silently orphan every assertion already signed.
    @Test
    fun theAddressablePrefixIsTheHashOfTheWholeCoordinate() {
        assertEquals(
            "event-tag-awesome-tag-6a5e1c40-aaaaaaaa",
            EventTagging.dTag("awesome-tag", TaggingTarget.ByAddress(Address(39999, bob, "good-tag")), alice),
        )
        assertEquals(
            "event-tag-awesome-tag-e89b797c-aaaaaaaa",
            EventTagging.dTag("awesome-tag", TaggingTarget.ByAddress(Address(39999, bob, "other-tag")), alice),
        )
        assertEquals(
            "event-tag-awesome-tag-96856fd8-aaaaaaaa",
            EventTagging.dTag("awesome-tag", TaggingTarget.ByAddress(Address(30023, bob, "good-tag")), alice),
        )
    }

    // A plain event is still named by its own id: it already covers the whole event, and hashing
    // it would only cost a round of SHA-256 per assertion built.
    @Test
    fun plainEventTargetsStillUseTheirOwnId() {
        assertEquals(
            "event-tag-awesome-tag-11111111-aaaaaaaa",
            EventTagging.dTag("awesome-tag", TaggingTarget.ByEventId("1".repeat(64)), alice),
        )
    }

    // Authored JSON: an explicit null in a list field must not make the whole section unreadable.
    @Test
    fun explicitNullListsInTheWordSectionAreTolerated() {
        val wrapper = WordWrapper.parse("""{"word":{"slug":"dogs","wordTypes":null,"coreMemberOf":null}}""")!!
        assertEquals("dogs", wrapper.word()?.slug)
    }
}
