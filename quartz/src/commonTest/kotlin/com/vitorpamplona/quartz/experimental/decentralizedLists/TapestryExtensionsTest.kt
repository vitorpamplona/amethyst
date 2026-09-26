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

import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.CurationCopy
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.HeaderResolution
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.dListAssistant
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.dListCuration
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.dListCurations
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.replaceDListCuration
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.tags.DListCuration
import com.vitorpamplona.quartz.experimental.decentralizedLists.concepts.WordWrapper
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.ItemKind
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.elementOf
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.subsetOf
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.EventTagging
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.PubKeyTagging
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.TagApplicabilityHint
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.TagElement
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.TaggingHeader
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.TaggingTarget
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.applicabilityHints
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.polarity
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags.Polarity
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritFromTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritType
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Tapestry drafts layered on Decentralized Lists, against their own examples. */
class TapestryExtensionsTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val assistant = "c".repeat(64)
    private val ta = "d".repeat(64)

    private fun event(
        kind: Int,
        tags: Array<Array<String>>,
        pubKey: String = alice,
        content: String = "",
        id: String = "e".repeat(64),
    ): Event = EventFactory.create(id, pubKey, 1L, kind, tags, content, "")

    private fun tagsOf(tags: Array<Array<String>>) = tags.map { it.toList() }

    // Inherit-From

    @Test
    fun bTagTypesAndTheirFailSafeDefault() {
        val header =
            assertIs<AddressableListHeaderEvent>(
                event(
                    39998,
                    arrayOf(
                        arrayOf("d", "dogs"),
                        arrayOf("b", "39998:$bob:dogs", "inherit"),
                        arrayOf("b", "39998:$ta:dogs", "inherit-items"),
                        arrayOf("b", "39998:$ta:canines"),
                        arrayOf("b", "39998:$ta:hounds", "something-new"),
                    ),
                ),
            )

        assertEquals(
            listOf(
                InheritFromTag("39998:$bob:dogs", InheritType.INHERIT),
                InheritFromTag("39998:$ta:dogs", InheritType.INHERIT_ITEMS),
                InheritFromTag("39998:$ta:canines", InheritType.POINTER),
                InheritFromTag("39998:$ta:hounds", InheritType.POINTER),
            ),
            header.inheritFrom(),
        )
        assertEquals(listOf("39998:$bob:dogs"), header.inheritFromTargets(InheritType.INHERIT))
        assertFalse(header.isDeliberatelyUnaffiliated())
    }

    @Test
    fun theDeferredSentinelIsAMarkerNotATarget() {
        val header = assertIs<AddressableListHeaderEvent>(event(39998, arrayOf(arrayOf("d", "dogs"), arrayOf("b", "b-tag-deferred"))))
        assertTrue(header.isDeliberatelyUnaffiliated())
        assertEquals(emptyList(), header.inheritFrom())
    }

    // Tapestry Concepts

    @Test
    fun conceptGraphIsReadOrComputed() {
        val without = assertIs<AddressableListHeaderEvent>(event(39998, arrayOf(arrayOf("d", "dogs"))))
        assertEquals("39999:$alice:dogs-concept-graph", without.conceptGraph())

        val with = assertIs<AddressableListHeaderEvent>(event(39998, arrayOf(arrayOf("d", "dogs"), arrayOf("concept-graph", "39999:$bob:elsewhere"))))
        assertEquals("39999:$bob:elsewhere", with.conceptGraph())
    }

    @Test
    fun wordWrapperJsonParses() {
        val json =
            """{"word":{"slug":"concept-header-for-the-concept-of-dogs","name":"concept header for the concept of dogs","wordTypes":["word","conceptHeader"]},""" +
                """"conceptHeader":{"description":"Dog is a concept.","oNames":{"singular":"dog","plural":"dogs"}}}"""
        val item = assertIs<AddressableListItemEvent>(event(39999, arrayOf(arrayOf("d", "dogs"), arrayOf("json", json))))

        val wrapper = item.wordWrapper()!!
        assertEquals("concept-header-for-the-concept-of-dogs", wrapper.word()?.slug)
        assertEquals(listOf("word", "conceptHeader"), wrapper.word()?.wordTypes)
        assertEquals("dogs", wrapper.conceptHeader()?.oNames?.plural)
        assertNull(wrapper.section("superset"))
        assertNull(WordWrapper.parse("[1,2]"))
        assertNull(WordWrapper.parse("not json"))
    }

    // Class Thread Relationships

    @Test
    fun classThreadTagsRoundTrip() {
        val set = Address(39999, alice, "the-set-of-paid-nostr-relays")
        val superset = Address(39999, alice, "superset-of-relays")
        val template =
            AddressableListItemEvent.build(ParentListTag.classify(set.toValue()), dTag = "relay-x") {
                elementOf(set)
                subsetOf(superset)
            }
        val signed = assertIs<AddressableListItemEvent>(event(39999, template.tags))
        assertEquals(listOf(set.toValue()), signed.elementOf())
        assertEquals(listOf(superset.toValue()), signed.subsetOf())
    }

    // Cross-NIP Compatibility

    @Test
    fun itemKindsWidenTheAcceptedKinds() {
        val plain = assertIs<AddressableListHeaderEvent>(event(39998, arrayOf(arrayOf("d", "communities"))))
        assertEquals(listOf(9999, 39999), plain.acceptedItemKinds())

        val widened =
            assertIs<AddressableListHeaderEvent>(
                event(
                    39998,
                    arrayOf(
                        arrayOf("d", "communities"),
                        arrayOf("item-kind", "39999"),
                        arrayOf("item-kind", "34550", "NIP-72 community-definition events"),
                        arrayOf("item-kind", "not-a-number"),
                    ),
                ),
            )
        assertEquals(listOf(ItemKind(39999), ItemKind(34550, "NIP-72 community-definition events")), widened.itemKinds())
        assertEquals(listOf(39999, 34550), widened.acceptedItemKinds())
    }

    @Test
    fun foreignKindsCanListThemselves() {
        val community = event(34550, arrayOf(arrayOf("d", "lfo"), arrayOf("z", "39998:$ta:communities")))
        assertEquals(listOf("39998:$ta:communities"), community.dListParentPointers())
    }

    // Tags & Taggings

    @Test
    fun tagElementsCarryTheirJsonAndHints() {
        val template = TagElement.build("39998:$ta:tag", "podcaster", "Podcaster", "Makes podcasts", setOf(TagApplicabilityHint.PUBKEY))
        assertEquals(
            listOf(listOf("d", "podcaster"), listOf("z", "39998:$ta:tag"), listOf("z", "tag-for-nostr-pubkey")),
            tagsOf(template.tags),
        )
        val signed = assertIs<AddressableListItemEvent>(event(39999, template.tags, content = template.content))
        assertEquals("Podcaster", TagElement.content(signed)?.tag?.name)
        assertEquals(listOf(TagApplicabilityHint.PUBKEY), signed.tags.applicabilityHints())
    }

    @Test
    fun pubKeyTaggingsUseTheDeterministicShape() {
        val tag = Address(39999, bob, "podcaster")
        val template = PubKeyTagging.build("39998:$ta:nostr-user-tag", tag, "f".repeat(64), target = assistant, asserter = alice, apply = false)

        assertEquals(
            listOf(
                listOf("d", "profile-tag-podcaster-cccccccc-aaaaaaaa"),
                listOf("z", "39998:$ta:nostr-user-tag"),
                listOf("p", assistant),
                listOf("a", "39999:$bob:podcaster"),
                listOf("e", "f".repeat(64)),
                listOf("polarity", "-1"),
            ),
            tagsOf(template.tags),
        )

        val signed = assertIs<AddressableListItemEvent>(event(39999, template.tags))
        val parsed = PubKeyTagging.parse(signed, "39998:$ta:nostr-user-tag")!!
        assertEquals(assistant, parsed.target)
        assertEquals(tag, parsed.tag)
        assertEquals(Polarity.DISPUTED, parsed.polarity)
        assertNull(PubKeyTagging.parse(signed, "39998:$ta:some-other-concept"))
    }

    @Test
    fun polarityBuckets() {
        fun p(vararg tags: Array<String>) = arrayOf(*tags).polarity()
        assertEquals(Polarity.APPLIED, p())
        assertEquals(Polarity.APPLIED, p(arrayOf("polarity", "1")))
        assertEquals(Polarity.DISPUTED, p(arrayOf("polarity", "-1")))
        assertEquals(Polarity.UNCOUNTED, p(arrayOf("polarity", "0.2")))
        assertEquals(Polarity.UNCOUNTED, p(arrayOf("polarity", "yes")))
    }

    // Event Taggings: the spec's worked example

    @Test
    fun eventTaggingsFollowTheWorkedExample() {
        val awesomeTag = Address(39999, bob, "awesome-tag")
        val header = TaggingHeader.build("39998:$ta:tagging-with-specific-tag", awesomeTag, "Tagging of an event as an Awesome Tag", "Taggings of events as Awesome Tags")
        assertEquals("tagging:awesome-tag-tagging", header.tags.first { it[0] == "d" }[1])

        val headerAddress = Address(39999, bob, "tagging:awesome-tag-tagging")
        val goodTag = Address(39999, assistant, "good-tag")
        val assertion = EventTagging.build("39998:$ta:nostr-event-tag", headerAddress, "awesome-tag", TaggingTarget.ByAddress(goodTag), asserter = alice)

        assertEquals(
            listOf(
                // 4d7a80b1 is sha256("39999:${"c".repeat(64)}:good-tag").take(8) — the whole
                // coordinate, so two of the assistant's tags cannot share this `d`.
                listOf("d", "event-tag-awesome-tag-4d7a80b1-aaaaaaaa"),
                listOf("z", "39998:$ta:nostr-event-tag"),
                listOf("z", "39999:$bob:tagging:awesome-tag-tagging"),
                listOf("a", "39999:$assistant:good-tag"),
                listOf("polarity", "1"),
            ),
            tagsOf(assertion.tags),
        )

        val signed = assertIs<AddressableListItemEvent>(event(39999, assertion.tags))
        val parsed = EventTagging.parse(signed, "39998:$ta:nostr-event-tag")!!
        assertEquals(TaggingTarget.ByAddress(goodTag), parsed.target)
        assertEquals(listOf("39999:$bob:tagging:awesome-tag-tagging"), parsed.taggingHeaders)
    }

    // Assistant Designation

    @Test
    fun treasureMapEntriesParseAndStayOutOfEachOther() {
        val map =
            arrayOf(
                arrayOf("30382:rank", ta, "wss://nip85.brainstorm.world"),
                arrayOf("39998:dlist-header", assistant, ""),
                arrayOf("39998:dogs", assistant, "wss://dcosl.brainstorm.world"),
                arrayOf("39999:tagging:with:colons", assistant, "wss://dcosl.brainstorm.world"),
                arrayOf("39999:dlist-header", assistant, ""),
            )

        assertEquals(assistant, map.dListAssistant()?.assistant)
        assertNull(map.dListAssistant()?.relay)
        assertEquals(listOf("dogs", "tagging:with:colons"), map.dListCurations().map { it.dTag })
        assertEquals(Address(39998, assistant, "dogs"), map.dListCuration(39998, "dogs")?.headerAddress())

        val updated = map.replaceDListCuration(DListCuration(39998, "dogs", bob))
        assertEquals(map.size, updated.size)
        assertEquals(bob, updated.dListCuration(39998, "dogs")?.assistant)
        assertEquals(listOf("30382:rank", ta, "wss://nip85.brainstorm.world"), updated[0].toList())
    }

    @Test
    fun curationCopiesCarryOnlyWhatTheSpecLists() {
        val original =
            assertIs<AddressableListItemEvent>(
                event(
                    39999,
                    arrayOf(
                        arrayOf("d", "fido"),
                        arrayOf("z", "39998:$ta:dogs"),
                        arrayOf("name", "Fido"),
                        arrayOf("t", "Fido"),
                        arrayOf("n", "39999:$bob:good-dogs"),
                        arrayOf("json", "{}"),
                    ),
                    pubKey = bob,
                    content = "a good dog",
                ),
            )
        val header = Address(39998, assistant, "dogs")
        val copy = CurationCopy.build(header, original, null)

        val expectedD = "copy-" + sha256("39998:$assistant:dogs\n39999:$bob:fido".encodeToByteArray()).toHexKey()
        assertEquals(
            listOf(
                listOf("d", expectedD),
                listOf("z", "39998:$assistant:dogs"),
                listOf("q", "39999:$bob:fido", ""),
                listOf("q", original.id, "", bob),
                listOf("name", "Fido"),
                listOf("t", "Fido"),
            ),
            tagsOf(copy.tags),
        )
        assertEquals("a good dog", copy.content)
    }

    @Test
    fun personalHeadersWinOverTheAssistantsRegardlessOfAge() {
        val map = arrayOf(arrayOf("39998:dlist-header", assistant, ""))
        assertEquals(
            listOf(Address(39998, alice, "dogs"), Address(39998, assistant, "dogs")),
            HeaderResolution.candidates(alice, "dogs", map),
        )

        val personal = assertIs<AddressableListHeaderEvent>(EventFactory.create("1".repeat(64), alice, 1L, 39998, arrayOf(arrayOf("d", "dogs")), "", ""))
        val newerByAssistant = assertIs<AddressableListHeaderEvent>(EventFactory.create("2".repeat(64), assistant, 99L, 39998, arrayOf(arrayOf("d", "dogs")), "", ""))

        assertEquals(personal, HeaderResolution.governing(alice, "dogs", map, listOf(newerByAssistant, personal)))
        assertEquals(newerByAssistant, HeaderResolution.governing(alice, "dogs", map, listOf(newerByAssistant)))
        assertNull(HeaderResolution.governing(alice, "dogs", null, listOf(newerByAssistant)))
    }
}
