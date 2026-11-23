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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_PIN_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupLiveStateFilter
import com.vitorpamplona.quartz.buzz.pmPinnedMessages.ChannelPinsEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Buzz pins (NIP-PM, buzz e95d7a950): the relay-signed 30623 is the channel's pin list, newest pin
 * first, and anyone who may post may pin. NIP-29 keeps its 39005 and moderator-only 9010.
 */
class RelayGroupBuzzPinsTest {
    private val relaySelf = "aa".repeat(32)
    private val alice = "bb".repeat(32)
    private val bob = "cc".repeat(32)
    private val carol = "dd".repeat(32)
    private val gid = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b"
    private val relay = RelayUrlNormalizer.normalize("ws://127.0.0.1:7500")
    private val msg1 = "11".repeat(32)
    private val msg2 = "22".repeat(32)

    @BeforeTest
    fun setUp() = BuzzRelayDialect.clearForTesting()

    @AfterTest
    fun tearDown() = BuzzRelayDialect.clearForTesting()

    private fun channel() = RelayGroupChannel(GroupId(gid, relay))

    /** A list as `buzz-db/src/store/pins.rs` writes it: `d`, `h`, then the pins newest first. */
    private fun pins(
        createdAt: Long,
        vararg ids: String,
        signer: String = relaySelf,
    ): ChannelPinsEvent {
        val tags = (listOf(arrayOf("d", gid), arrayOf("h", gid)) + ids.map { arrayOf("e", it) }).toTypedArray()
        return EventFactory.create(createdAt.toString(16).padStart(64, '0'), signer, createdAt, ChannelPinsEvent.KIND, tags, "", "22".repeat(64)) as ChannelPinsEvent
    }

    private fun metadata(
        createdAt: Long,
        extra: List<Array<String>> = emptyList(),
    ): GroupMetadataEvent {
        val tags = (listOf(arrayOf("d", gid), arrayOf("public")) + extra).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupMetadataEvent.KIND, tags, "", "22".repeat(64)) as GroupMetadataEvent
    }

    private fun buzzMembers(vararg entries: Pair<String, String>): GroupMembersEvent {
        val tags = (listOf(arrayOf("d", gid)) + entries.map { arrayOf("p", it.first, "", it.second) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, 100, GroupMembersEvent.KIND, tags, "", "22".repeat(64)) as GroupMembersEvent
    }

    @Test
    fun theRelaysListBecomesThePinsInDisplayOrder() {
        val c = channel()
        c.updateBuzzPins(pins(100, msg2, msg1))
        assertEquals(listOf(EventPin(msg2), EventPin(msg1)), c.pins)
        assertEquals(listOf(msg2, msg1), c.pinnedEventIds)
        assertTrue(c.isPinned(msg1))

        c.updateBuzzPins(pins(50, msg1)) // older: dropped
        assertEquals(listOf(msg2, msg1), c.pinnedEventIds)

        c.updateBuzzPins(pins(200)) // newer and empty: no pins
        assertTrue(c.pins.isEmpty())
        assertFalse(c.isPinned(msg1))
    }

    @Test
    fun onBuzzAnyoneWhoMayPostMayPin() {
        BuzzRelayDialect.mark(relay)
        val c = channel()
        c.updateGroupInfo(metadata(100, extra = listOf(arrayOf("join_role", "member"))))
        c.updateMembers(buzzMembers(alice to "member", bob to "guest"))

        assertTrue(c.canPin(alice), "a plain member pins: NIP-PM checks the rule for posting, not a moderator role")
        assertFalse(c.canPin(bob), "a guest may not post, so may not pin")
        assertTrue(c.canPin(carol), "an open channel takes posts from any relay member")
    }

    @Test
    fun nobodyPinsInAnArchivedBuzzChannel() {
        BuzzRelayDialect.mark(relay)
        val c = channel()
        c.updateGroupInfo(metadata(100, extra = listOf(arrayOf("join_role", "member"), arrayOf("archived", "true"))))
        c.updateMembers(buzzMembers(alice to "owner"))
        assertFalse(c.canPin(alice))
    }

    @Test
    fun nip29PinsStayWithModerators() {
        val c = channel()
        val admins = EventFactory.create("00".repeat(32), relaySelf, 100, GroupAdminsEvent.KIND, arrayOf(arrayOf("d", gid), arrayOf("p", alice, "admin")), "", "22".repeat(64)) as GroupAdminsEvent
        c.updateAdmins(admins)
        c.updateMembers(EventFactory.create("00".repeat(32), relaySelf, 100, GroupMembersEvent.KIND, arrayOf(arrayOf("d", gid), arrayOf("p", bob)), "", "22".repeat(64)) as GroupMembersEvent)
        assertTrue(c.canPin(alice))
        assertFalse(c.canPin(bob), "a NIP-29 member is not a moderator")
    }

    @Test
    fun theCacheFoldsTheRelaySignedListIntoTheChannel() {
        val cache = EventCache()
        assertTrue(cache.justConsume(pins(100, msg1, msg2), relay, true))
        assertEquals(listOf(msg1, msg2), cache.getRelayGroupChannelIfExists(GroupId(gid, relay))?.pinnedEventIds)
    }

    @Test
    fun theLiveBuzzStateFilterAsksForThePinListByH() {
        assertTrue(ChannelPinsEvent.KIND in RELAY_GROUP_PIN_KINDS)
        val pinFilter = buildRelayGroupLiveStateFilter(GroupId(gid, relay)).single { ChannelPinsEvent.KIND in it.filter.kinds.orEmpty() }
        assertEquals(mapOf("h" to listOf(gid)), pinFilter.filter.tags)
    }

    @Test
    fun aBuzzNip11MarksTheRelay() {
        assertFalse(BuzzRelayDialect.looksLikeBuzzRelay(relay, Nip11RelayInformation(software = "git+https://github.com/hoytech/strfry.git")))
        assertFalse(BuzzRelayDialect.isBuzz(relay))
        assertTrue(BuzzRelayDialect.looksLikeBuzzRelay(relay, Nip11RelayInformation(software = "https://github.com/block/buzz")))
        assertTrue(BuzzRelayDialect.isBuzz(relay))
    }
}
