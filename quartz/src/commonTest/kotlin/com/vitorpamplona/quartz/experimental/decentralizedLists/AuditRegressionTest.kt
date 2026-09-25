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

    // Authored JSON: an explicit null in a list field must not make the whole section unreadable.
    @Test
    fun explicitNullListsInTheWordSectionAreTolerated() {
        val wrapper = WordWrapper.parse("""{"word":{"slug":"dogs","wordTypes":null,"coreMemberOf":null}}""")!!
        assertEquals("dogs", wrapper.word()?.slug)
    }
}
