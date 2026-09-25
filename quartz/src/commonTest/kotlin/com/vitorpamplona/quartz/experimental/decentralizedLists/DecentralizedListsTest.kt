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

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.ListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.recommended
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.required
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRule
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRuleType
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.ListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemPubKey
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemString
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.name
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.parentListName
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentList
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Tapestry Decentralized Lists tag layer against the spec's own examples
 * (https://github.com/nous-clawds4/tapestry/blob/main/protocols/nips/decentralized-lists.md).
 */
class DecentralizedListsTest {
    private val author = "dee45a23c4f1d93f3a2043650c5081e4ac14a778e0acbef03de3768e4f81ac7b"
    private val derek = "3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24"
    private val headerId = "9d1b6b9562e66f2ecf35eb0a3c2decc736c47fddb13d6fb8f87185a153ea3634"
    private val otherHeaderId = "1fc80cf813f1af33d5a435862b7ef7fb96b47e68a48f1abcadf8081f5a545550"

    private fun event(
        kind: Int,
        tags: Array<Array<String>>,
        id: String = "a".repeat(64),
    ): Event = EventFactory.create(id, author, 1719888496L, kind, tags, "", "")

    @Test
    fun theFactoryBuildsEachKind() {
        assertIs<ListHeaderEvent>(event(9998, emptyArray()))
        assertIs<AddressableListHeaderEvent>(event(39998, emptyArray()))
        assertIs<ListItemEvent>(event(9999, emptyArray()))
        assertIs<AddressableListItemEvent>(event(39999, emptyArray()))
    }

    // Example 1: a list of nostr developers.
    @Test
    fun theSpecsHeaderExampleParses() {
        val header =
            assertIs<ListHeaderEvent>(
                event(
                    9998,
                    arrayOf(
                        arrayOf("names", "nostr developer", "nostr developers"),
                        arrayOf("description", "This is a list of developers who build within the nostr ecosystem"),
                        arrayOf("required", "p"),
                        arrayOf("required", "name"),
                    ),
                    id = headerId,
                ),
            )

        assertEquals("nostr developer", header.names()?.singular)
        assertEquals("nostr developers", header.names()?.plural)
        assertEquals("This is a list of developers who build within the nostr ecosystem", header.description())
        assertEquals(listOf("p", "name"), header.requiredTags())
        assertEquals(emptyList(), header.allowedTags())
        assertEquals(headerId, header.listPointer())
    }

    @Test
    fun ruleDescriptionsAreTheOptionalThirdElement() {
        val header =
            assertIs<ListHeaderEvent>(
                event(
                    9998,
                    arrayOf(
                        arrayOf("names", "endorsement", "endorsements"),
                        arrayOf("required", "p", "Pubkey of the person being endorsed"),
                        arrayOf("allowed", "comments", "Optional textual reason"),
                        arrayOf("disallowed", "e"),
                        arrayOf("recommended", "title"),
                        arrayOf("required", ""),
                    ),
                ),
            )

        assertEquals(
            listOf(
                TagRule(TagRuleType.REQUIRED, "p", "Pubkey of the person being endorsed"),
                TagRule(TagRuleType.ALLOWED, "comments", "Optional textual reason"),
                TagRule(TagRuleType.DISALLOWED, "e", null),
                TagRule(TagRuleType.RECOMMENDED, "title", null),
            ),
            header.tagRules(),
        )
    }

    @Test
    fun aNamesTagMissingItsPluralIsDropped() {
        val header = assertIs<ListHeaderEvent>(event(9998, arrayOf(arrayOf("names", "dog"))))
        assertNull(header.names())
    }

    // Example 1, item side, and Example 4 (one item on two lists).
    @Test
    fun theSpecsItemExampleParses() {
        val item =
            assertIs<ListItemEvent>(
                event(
                    9999,
                    arrayOf(
                        arrayOf("z", headerId),
                        arrayOf("z", otherHeaderId),
                        arrayOf("name", "Derek Ross"),
                        arrayOf("p", derek),
                    ),
                ),
            )

        assertEquals(listOf(ParentList.EventId(headerId), ParentList.EventId(otherHeaderId)), item.parentLists())
        assertEquals("Derek Ross", item.name())
        assertEquals(listOf(derek), item.itemPubKeys().map { it.pubKey })
        assertTrue(headerId in item.linkedEventIds())
        assertFalse(item.declaresList())
    }

    // Example 7: pointing at an editable (39998) header by coordinate.
    @Test
    fun zTagsPointingAtCoordinatesParseAsAddresses() {
        val coordinate = "39998:$author:dogs"
        val item = assertIs<ListItemEvent>(event(9999, arrayOf(arrayOf("z", coordinate), arrayOf("t", "Fido"))))

        val parent = assertIs<ParentList.Coordinate>(item.parentLists().single())
        assertEquals(39998, parent.address.kind)
        assertEquals(author, parent.address.pubKeyHex)
        assertEquals("dogs", parent.address.dTag)
        assertEquals(coordinate, parent.value)
        assertEquals(listOf("Fido"), item.itemStrings())
        assertTrue(coordinate in item.linkedAddressIds())
    }

    // Example 4, alternate form: undeclared lists named by their singular name.
    @Test
    fun zTagsWithPlainNamesParseAsNames() {
        val item =
            assertIs<ListItemEvent>(
                event(9999, arrayOf(arrayOf("z", "dog"), arrayOf("z", "animal"), arrayOf("t", "Fido"))),
            )

        assertEquals(listOf(ParentList.Name("dog"), ParentList.Name("animal")), item.parentLists())
        assertEquals(listOf("dog", "animal"), item.parentListPointers())
    }

    // Example 2: the spec puts an naddr in the item's `a` tag.
    @Test
    fun naddrItemsParseAsAddresses() {
        val naddr =
            "naddr1qvzqqqr4gupzq4rqjpyzsnf2z5wgma397sxr382z8mg90l80jf7m3z2k628z9wsrqythwumn8ghj7cnfw33k76twv4ezuum0vd5kzmp0qythwumn8ghj7ct5d3shxtnwdaehgu3wd3skuep0qq3kv6tpwskkxatjwfjkucme946xsefdwd5kcetwwskhg6tdv5khg6rfv4nqnxv6fx"
        val item =
            assertIs<ListItemEvent>(
                event(
                    9999,
                    arrayOf(
                        arrayOf("z", headerId),
                        arrayOf("a", naddr),
                        arrayOf("title", "Fiat Currency: The Silent Time Thief"),
                    ),
                ),
            )

        assertEquals(30023, item.itemAddresses().single().kind)
        assertEquals("Fiat Currency: The Silent Time Thief", item.title())
    }

    // "Nonstandard methods to declare a list": a 9999 on the list of lists carries header tags.
    @Test
    fun anItemCanDeclareAListTheNonstandardWay() {
        val item =
            assertIs<ListItemEvent>(
                event(
                    9999,
                    arrayOf(
                        arrayOf("z", "list"),
                        arrayOf("names", "dog", "dogs"),
                        arrayOf("description", "This is a list (by name) of individual dogs."),
                        arrayOf("required", "t"),
                    ),
                ),
            )

        assertTrue(item.declaresList())
        assertEquals("dogs", item.names()?.plural)
        assertEquals(listOf("t"), item.requiredTags())
    }

    @Test
    fun theHeaderBuilderWritesTheSpecsShape() {
        val template =
            ListHeaderEvent.build("dog name", "dog names", "This is a list of commonly used dog names.") {
                required("t")
                recommended("comments", "Why this name")
            }

        assertEquals(9998, template.kind)
        assertEquals(
            listOf(
                listOf("names", "dog name", "dog names"),
                listOf("description", "This is a list of commonly used dog names."),
                listOf("required", "t"),
                listOf("recommended", "comments", "Why this name"),
            ),
            template.tags.map { it.toList() },
        )
    }

    @Test
    fun itemsPointAtRegularHeadersByIdAndAddressableHeadersByCoordinate() {
        val header = assertIs<ListHeaderEvent>(event(9998, arrayOf(arrayOf("names", "dog", "dogs")), id = headerId))
        val byId = ListItemEvent.build(header) { itemString("Fido") }
        assertEquals(
            listOf(listOf("z", headerId), listOf("t", "Fido")),
            byId.tags.map { it.toList() },
        )

        val editable =
            assertIs<AddressableListHeaderEvent>(
                event(39998, arrayOf(arrayOf("d", "dogs"), arrayOf("names", "dog", "dogs"))),
            )
        val byCoordinate = AddressableListItemEvent.build(editable, dTag = "fido") { itemString("Fido") }
        assertEquals(
            listOf(listOf("d", "fido"), listOf("z", "39998:$author:dogs"), listOf("t", "Fido")),
            byCoordinate.tags.map { it.toList() },
        )
    }

    @Test
    fun itemBuilderAcceptsUndeclaredListsAndPubkeys() {
        val template =
            ListItemEvent.build(ParentList.Name("nostr developer")) {
                parentListName("nostr developer")
                name("Derek Ross")
                itemPubKey(derek)
            }

        // the repeated parent is written once
        assertEquals(
            listOf(listOf("z", "nostr developer"), listOf("name", "Derek Ross"), listOf("p", derek)),
            template.tags.map { it.toList() },
        )
    }

    @Test
    fun searchIndexesTheHumanTextAndBothPathsAgree() {
        val header =
            assertIs<ListHeaderEvent>(
                event(
                    9998,
                    arrayOf(
                        arrayOf("names", "dog", "dogs"),
                        arrayOf("titles", "Dog", "Dogs"),
                        arrayOf("description", "Good boys."),
                        arrayOf("required", "t"),
                    ),
                ),
            )
        assertEquals("dog\ndogs\nDog\nDogs\nGood boys.", header.indexableContent())

        val item =
            assertIs<ListItemEvent>(
                event(
                    9999,
                    arrayOf(arrayOf("z", headerId), arrayOf("name", "Fido"), arrayOf("comments", "Very good"), arrayOf("t", "Fido")),
                ),
            )
        assertEquals("Fido\nVery good\nFido", item.indexableContent())

        listOf<SearchableEvent>(header, item).forEach { event ->
            val fields = mutableListOf<String>()
            event.forEachIndexableField { field ->
                field?.let { fields.add(it) }
                true
            }
            assertEquals(event.indexableContent(), fields.joinToString(event.indexableSeparator()))
        }

        assertEquals("", assertIs<ListItemEvent>(event(9999, emptyArray())).indexableContent())
    }
}
