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
package com.vitorpamplona.quartz.experimental.trustedLists

import com.vitorpamplona.quartz.experimental.trustedLists.addressables.AddressableTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.addressables.tags.AddressMemberTag
import com.vitorpamplona.quartz.experimental.trustedLists.events.EventTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.events.tags.EventMemberTag
import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.ExternalIdTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.tags.ListStatus
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.tags.PubKeyMemberTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustedListEventTest {
    private val observer = "2efaa715bbb46dd5be6b7da8d7700266d11674b913b8178addb5c2e63d987331"
    private val tagEventId = "2f6a8652bde6fb5a974d6e06e4eae3b4f130140fd170b2686a291463f47a7451"
    private val tagAuthor = "e5272de914bd301755c439b88e6959a43c9d2664831f093c51e9c799a16a102f"
    private val dummySig = "00".repeat(64)

    /** The pinned-tag pubkey list published by Tapestry's `refreshPinnedTags.runOnePin`. */
    private fun podcasterList(): Event =
        EventFactory.create<Event>(
            id = "2175c15e58ab5ac8ab05e06f3f56a6c44f7bc36b6a9de70bc125c48c21e1254f",
            pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
            createdAt = 1_787_253_028L,
            kind = UserTrustedListEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("d", "tl-pin-2efaa715-e5272de9-podcaster"),
                    arrayOf("title", "Podcaster"),
                    arrayOf("metric", "pinned-tag-membership"),
                    arrayOf("observer", observer),
                    arrayOf("source-tag", tagEventId, tagAuthor, "podcaster"),
                    arrayOf("cutoff", "1"),
                    arrayOf("min-rank", "2"),
                    arrayOf("p", "b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450", "", "99"),
                    arrayOf("p", "ba2f394833658475e91680b898f9be0f1d850166c6a839dbe084d0266ad6e20a", "", "97"),
                    arrayOf("p", "19fefd7f39c96d2ff76f87f7627ae79145bc971d8ab23205005939a5a913bc2f", "", "100"),
                ),
            content =
                "{\"members\":[" +
                    "{\"pubkey\":\"b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450\",\"endorsements\":4,\"disputes\":0,\"score\":99}," +
                    "{\"pubkey\":\"ba2f394833658475e91680b898f9be0f1d850166c6a839dbe084d0266ad6e20a\",\"endorsements\":2,\"disputes\":0,\"score\":97}," +
                    "{\"pubkey\":\"19fefd7f39c96d2ff76f87f7627ae79145bc971d8ab23205005939a5a913bc2f\",\"endorsements\":1,\"disputes\":0,\"score\":100}" +
                    "]}",
            sig = dummySig,
        )

    @Test
    fun factoryBuildsEachKindInTheFamily() {
        assertTrue(EventFactory.isKnownKind(UserTrustedListEvent.KIND), "kind 30392 should be a known kind")
        assertTrue(EventFactory.isKnownKind(EventTrustedListEvent.KIND), "kind 30393 should be a known kind")
        assertTrue(EventFactory.isKnownKind(AddressableTrustedListEvent.KIND), "kind 30394 should be a known kind")
        assertTrue(EventFactory.isKnownKind(ExternalIdTrustedListEvent.KIND), "kind 30395 should be a known kind")
    }

    @Test
    fun kindsFollowThePlusTenRuleOverNip85() {
        assertEquals(30382 + 10, UserTrustedListEvent.KIND)
        assertEquals(30383 + 10, EventTrustedListEvent.KIND)
        assertEquals(30384 + 10, AddressableTrustedListEvent.KIND)
        assertEquals(30385 + 10, ExternalIdTrustedListEvent.KIND)
    }

    @Test
    fun parsesThePinnedTagPubKeyList() {
        val event = podcasterList()
        assertIs<UserTrustedListEvent>(event)

        assertEquals("tl-pin-2efaa715-e5272de9-podcaster", event.listId())
        assertEquals("Podcaster", event.title())
        assertEquals("pinned-tag-membership", event.metric())
        assertEquals(observer, event.observer())
        assertEquals(1, event.cutoff())
        assertEquals(2, event.minRank())

        val source = event.sourceTag()
        assertEquals(tagEventId, source?.eventId)
        assertEquals(tagAuthor, source?.author)
        assertEquals("podcaster", source?.slug)

        assertEquals(3, event.memberCount())
        assertEquals(
            listOf(
                "b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450",
                "ba2f394833658475e91680b898f9be0f1d850166c6a839dbe084d0266ad6e20a",
                "19fefd7f39c96d2ff76f87f7627ae79145bc971d8ab23205005939a5a913bc2f",
            ),
            event.memberValues(),
        )
        assertEquals(listOf(99, 97, 100), event.members().map { it.score })
        // the empty string at index 2 is a placeholder for the missing relay hint
        assertTrue(event.members().all { it.relayHint == null })
    }

    @Test
    fun theObserverIsProvenanceNotAMember() {
        val event = podcasterList()
        assertIs<UserTrustedListEvent>(event)

        assertFalse(observer in event.memberValues(), "the observer must not be read back as a member")
        assertEquals(observer, event.observer())
    }

    @Test
    fun readsTheContentEcho() {
        val event = podcasterList()
        assertIs<UserTrustedListEvent>(event)

        val echo = event.contentEcho()
        assertEquals(3, echo?.members?.size)
        assertEquals("b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450", echo?.members?.first()?.memberValue())
        assertEquals(4, echo?.members?.first()?.endorsements)
        assertEquals(0, echo?.members?.first()?.disputes)
        assertEquals(99, echo?.members?.first()?.score)
        assertNull(echo?.partial, "a complete list carries no partial marker")
        assertEquals(event.memberValues(), echo?.members?.mapNotNull { it.memberValue() })
    }

    @Test
    fun aMissingTruncatedTagMeansComplete() {
        val event = podcasterList()
        assertIs<UserTrustedListEvent>(event)

        assertFalse(event.isTruncated())
        assertNull(event.truncatedTotal())
    }

    @Test
    fun aTruncatedTagMeansNotExhaustive() {
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
                createdAt = 1_787_253_028L,
                kind = EventTrustedListEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "tl-pin-notes-2efaa715-e5272de9-podcaster"),
                        arrayOf("metric", "pinned-tag-notes"),
                        arrayOf("truncated", "4211"),
                        arrayOf("e", "f00dcafe00000000000000000000000000000000000000000000000000000000", "wss://nos.lol/", "88"),
                    ),
                content = "",
                sig = dummySig,
            )
        assertIs<EventTrustedListEvent>(event)

        assertTrue(event.isTruncated())
        assertEquals(4211, event.truncatedTotal())
        assertNull(event.contentEcho(), "an empty content is not an echo")
    }

    @Test
    fun aTruncatedTagWithoutATotalStillMeansNotExhaustive() {
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
                createdAt = 1_787_253_028L,
                kind = EventTrustedListEvent.KIND,
                tags = arrayOf(arrayOf("d", "tl"), arrayOf("truncated")),
                content = "",
                sig = dummySig,
            )
        assertIs<EventTrustedListEvent>(event)

        assertTrue(event.isTruncated(), "presence of the tag is what signals incompleteness")
        assertNull(event.truncatedTotal())
    }

    @Test
    fun readsTheRetractionMarker() {
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
                createdAt = 1_787_253_028L,
                kind = AddressableTrustedListEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "tag-applicability-nostr-event"),
                        arrayOf("status", "retracted"),
                    ),
                content = "",
                sig = dummySig,
            )
        assertIs<AddressableTrustedListEvent>(event)

        assertEquals(ListStatus.RETRACTED, event.status())
        assertTrue(event.isRetracted())
        assertEquals(emptyList<String>(), event.memberValues())
    }

    @Test
    fun discoveryTagsOnTheNoteListAreNotMembers() {
        val coordinate = "39999:$tagAuthor:podcaster"
        val noteId = "f00dcafe00000000000000000000000000000000000000000000000000000000"
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
                createdAt = 1_787_253_028L,
                kind = EventTrustedListEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "tl-pin-notes-2efaa715-e5272de9-podcaster"),
                        arrayOf("metric", "pinned-tag-notes"),
                        arrayOf("observer", observer),
                        arrayOf("a", coordinate),
                        arrayOf("p", observer),
                        arrayOf("e", noteId, "", "88"),
                    ),
                content = "",
                sig = dummySig,
            )
        assertIs<EventTrustedListEvent>(event)

        assertEquals(listOf(noteId), event.memberValues())
        assertEquals(listOf(coordinate), event.aboutAddresses().map { it.toTag() })
        assertEquals(listOf(observer), event.aboutPubKeys().map { it.pubKey })
    }

    @Test
    fun addressableMembersAreACoordinates() {
        val coordinate = "39999:$tagAuthor:podcaster"
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
                createdAt = 1_787_253_028L,
                kind = AddressableTrustedListEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "tag-applicability-nostr-event"),
                        arrayOf("metric", "tag-applicability"),
                        arrayOf("a", coordinate),
                    ),
                content = "",
                sig = dummySig,
            )
        assertIs<AddressableTrustedListEvent>(event)

        assertEquals(listOf(coordinate), event.memberValues())
        assertEquals(
            39999,
            event
                .members()
                .first()
                .toAddress()
                ?.kind,
        )
        assertEquals(
            tagAuthor,
            event
                .members()
                .first()
                .toAddress()
                ?.pubKeyHex,
        )
        assertEquals(
            "podcaster",
            event
                .members()
                .first()
                .toAddress()
                ?.dTag,
        )
    }

    @Test
    fun externalIdMembersKeepTheirNip73Hint() {
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
                createdAt = 1_787_253_028L,
                kind = ExternalIdTrustedListEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "tl-pin-ext-podcaster"),
                        arrayOf("i", "podcast:guid:c90e609a-df1e-596a-bd5e-57bcc8aad6cc", "https://fountain.fm/show/abc", "91"),
                    ),
                content = "",
                sig = dummySig,
            )
        assertIs<ExternalIdTrustedListEvent>(event)

        val member = event.members().first()
        assertEquals("podcast:guid:c90e609a-df1e-596a-bd5e-57bcc8aad6cc", member.memberValue)
        assertEquals("https://fountain.fm/show/abc", member.hint)
        assertEquals(91, member.score)
    }

    @Test
    fun memberTagsRoundTripThroughTheirWireShape() {
        assertEquals(
            listOf("p", "b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450", "", "99"),
            PubKeyMemberTag("b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450", score = 99).toTagArray().toList(),
        )
        assertEquals(
            listOf("p", "b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450"),
            PubKeyMemberTag("b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450").toTagArray().toList(),
        )
        assertEquals(
            listOf("e", "f00dcafe00000000000000000000000000000000000000000000000000000000", "", "88"),
            EventMemberTag("f00dcafe00000000000000000000000000000000000000000000000000000000", score = 88).toTagArray().toList(),
        )
        assertEquals(
            listOf("a", "39999:$tagAuthor:podcaster"),
            AddressMemberTag("39999:$tagAuthor:podcaster").toTagArray().toList(),
        )
    }
}
