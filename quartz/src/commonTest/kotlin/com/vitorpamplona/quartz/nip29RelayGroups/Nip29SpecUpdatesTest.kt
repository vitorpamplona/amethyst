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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previous
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previousEvents
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.PreviousTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * NIP-29 revisions from 2026-07/08: the single multi-value `previous` tag, the `banner`
 * metadata field, carrying unknown metadata through a kind-9002 edit, and URL-decoded
 * `naddr1…?invite=` codes.
 */
class Nip29SpecUpdatesTest {
    private val gid = "0123456789abcdef"
    private val relaySelf = "aa".repeat(32)

    @Test
    fun previousIsOneTagWithEveryPrefix() {
        val template = eventTemplate<Event>(9, "hi") { previous(listOf("eb96c864", "2db75638", "b5d1065f")) }
        val previousTags = template.tags.filter { it[0] == PreviousTag.TAG_NAME }

        // Spec example: ["previous", "eb96c864", "2db75638", "b5d1065f"] — relay29 reads only the first tag.
        assertEquals(1, previousTags.size)
        assertEquals(listOf("previous", "eb96c864", "2db75638", "b5d1065f"), previousTags.single().toList())
    }

    @Test
    fun noPreviousTagWhenThereAreNoReferences() {
        val template = eventTemplate<Event>(9, "hi") { previous(emptyList()) }
        assertEquals(0, template.tags.count { it[0] == PreviousTag.TAG_NAME })
    }

    @Test
    fun previousEventsReadsAllValuesOfTheSpecForm() {
        val tags = arrayOf(arrayOf("h", gid), arrayOf("previous", "eb96c864", "2db75638", "b5d1065f"))
        assertEquals(listOf("eb96c864", "2db75638", "b5d1065f"), tags.previousEvents())
    }

    @Test
    fun previousEventsToleratesTheLegacyOneTagPerPrefixForm() {
        val tags = arrayOf(arrayOf("previous", "eb96c864"), arrayOf("h", gid), arrayOf("previous", "2db75638", "b5d1065f"), arrayOf("previous", ""))
        assertEquals(listOf("eb96c864", "2db75638", "b5d1065f"), tags.previousEvents())
    }

    @Test
    fun editMetadataCarriesPreviousAsOneTag() {
        val template = GroupEditMetadataEvent.build(gid, name = "x", previousEvents = listOf("11111111", "22222222"))
        assertEquals(listOf(listOf("previous", "11111111", "22222222")), template.tags.filter { it[0] == "previous" }.map { it.toList() })
    }

    @Test
    fun bannerRoundTripsThroughMetadataAndEdit() {
        val meta = GroupMetadataEvent.build(gid, name = "Pizza", picture = "https://p/p.png", banner = "https://p/banner.png")
        val parsed = GroupMetadataEvent("00".repeat(32), relaySelf, 1, meta.tags, "", "22".repeat(64))
        assertEquals("https://p/banner.png", parsed.banner())
        assertEquals("https://p/p.png", parsed.picture())

        val edit = GroupEditMetadataEvent.build(gid, name = "Pizza", banner = "https://p/banner2.png")
        assertEquals("https://p/banner2.png", GroupEditMetadataEvent("00".repeat(32), relaySelf, 1, edit.tags, "", "22".repeat(64)).banner())
    }

    @Test
    fun unmanagedMetadataTagsSurviveAnEdit() {
        val tags =
            arrayOf(
                arrayOf("d", gid),
                arrayOf("name", "Pizza"),
                arrayOf("banner", "https://p/banner.png"),
                arrayOf("private"),
                arrayOf("livekit"),
                arrayOf("supported_kinds", "9", "11"),
                arrayOf("future_field", "v"),
                arrayOf("t", "food"),
                arrayOf("parent", "root"),
            )
        val current = GroupMetadataEvent("00".repeat(32), relaySelf, 1, tags, "", "22".repeat(64))

        assertEquals(
            listOf(listOf("livekit"), listOf("supported_kinds", "9", "11"), listOf("future_field", "v")),
            current.unmanagedTags().map { it.toList() },
        )

        val edit =
            GroupEditMetadataEvent.build(
                gid,
                name = "Pizza Lovers",
                banner = current.banner(),
                extraTags = current.unmanagedTags() + listOf(arrayOf("name", "should be ignored")),
            )
        val names = edit.tags.map { it[0] }
        assertEquals(1, names.count { it == "name" })
        assertEquals("Pizza Lovers", edit.tags.first { it[0] == "name" }[1])
        assertEquals(listOf("h", "name", "banner", "livekit", "supported_kinds", "future_field"), names)
    }

    @Test
    fun inviteCodeIsUrlDecoded() {
        assertEquals("a/b c", GroupNAddrInvite.parse("?invite=a%2Fb%20c"))
        assertEquals("café", GroupNAddrInvite.parse("?invite=caf%C3%A9"))
        assertEquals("100%", GroupNAddrInvite.parse("?invite=100%"))
        assertEquals("a+b", GroupNAddrInvite.parse("?invite=a+b"))
    }

    @Test
    fun parsesAWholeNaddrWithInviteSuffix() {
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://groups.example.com")!!
        val naddr = NAddress.create(GroupMetadataEvent.KIND, relaySelf, gid, relay)

        val withCode = assertNotNull(GroupNAddrInvite.parseReference("$naddr?invite=xyz%21"))
        assertEquals(GroupId(gid, relay), withCode.groupId)
        assertEquals("xyz!", withCode.inviteCode)

        val bare = assertNotNull(GroupNAddrInvite.parseReference("nostr:$naddr"))
        assertEquals(GroupId(gid, relay), bare.groupId)
        assertNull(bare.inviteCode)

        // Not a group: wrong kind, or no relay hint to host it.
        assertNull(GroupNAddrInvite.parseReference(NAddress.create(30023, relaySelf, gid, relay)))
        assertNull(GroupNAddrInvite.parseReference(NAddress.create(GroupMetadataEvent.KIND, relaySelf, gid, null)))
        assertNull(GroupNAddrInvite.parseReference("wss://groups.example.com"))
    }

    @Test
    fun pinReferencesParseToEventOrAddressPins() {
        val id = "ab".repeat(32)
        val author = "cd".repeat(32)
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

        assertEquals(EventPin(id), GroupPin.fromReference(id))
        assertEquals(EventPin(id), GroupPin.fromReference(NNote.create(id)))
        assertEquals(EventPin(id), GroupPin.fromReference("nostr:" + NEvent.create(id, author, 1, relay)))
        assertEquals<GroupPin?>(AddressPin(Address(30023, author, "art")), GroupPin.fromReference("30023:$author:art"))
        assertEquals<GroupPin?>(AddressPin(Address(30023, author, "art")), GroupPin.fromReference(NAddress.create(30023, author, "art", relay)))
        assertNull(GroupPin.fromReference("not a reference"))
    }

    @Test
    fun replaceMovesAGroupToANewRelayInOneVersion() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val old = GroupTag(gid, "wss://old.example.com/", "Pizza")
            val other = GroupTag("other", "wss://old.example.com/", null)
            val list = SimpleGroupListEvent.create(publicGroups = listOf(old, other), signer = signer)

            val moved = SimpleGroupListEvent.replace(list, old, GroupTag(gid, "wss://new.example.com/", "Pizza"), signer)

            assertEquals(
                listOf(gid to "wss://new.example.com/", "other" to "wss://old.example.com/").toSet(),
                moved.publicGroups().map { it.groupId to it.relayUrl }.toSet(),
            )
            assertEquals(2, moved.publicGroups().size)
        }
}
