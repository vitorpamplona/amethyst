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
package com.vitorpamplona.amethyst.commons.model.chats

import com.vitorpamplona.amethyst.commons.model.buzz.BuzzCommunityMembership
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every posting gate each protocol can actually produce, and the invariant that ties them together:
 * `canPost()` is *defined* as `postingGate() == Allowed`, so a room whose composer is hidden always
 * has a reason to show in its place. That is the property the old boolean couldn't express — a banned
 * Concord member got `canPost() == false` and an empty slot, because the only explanatory branch on
 * the screen tested dissolution.
 *
 * [PostingGate.NoKey] covers both "we can't place this account in the community" (the mapping for
 * `ConcordMembership.NONE`, which the fold cannot currently reach) and "this is a private channel
 * whose key we were never granted" — the second of which is real, and is the backstop for reaching
 * such a channel despite it being hidden from every list.
 */
class PostingGateTest {
    // ---- Concord ------------------------------------------------------------------------------

    private val owner = "0f".repeat(32)
    private val banned = "c3".repeat(32)
    private val channelIdHex = "ce".repeat(32)

    private fun ed(
        kind: ControlEntityKind,
        eid: String,
        content: String,
    ) = ControlEdition(kind, eid.hexToByteArray(), 0, null, null, content, owner, "r-$eid", 0)

    private fun concordState(
        dissolved: Boolean = false,
        banlist: List<String> = emptyList(),
    ): ConcordCommunityState {
        val editions =
            buildList {
                add(ed(ControlEntityKind.CHANNEL, channelIdHex, """{"name":"general"}"""))
                if (banlist.isNotEmpty()) {
                    add(ed(ControlEntityKind.BANLIST, "44".repeat(32), banlist.joinToString(",", "[", "]") { "\"$it\"" }))
                }
                if (dissolved) add(ed(ControlEntityKind.DISSOLVED, "dd".repeat(32), """{}"""))
            }
        return ConcordCommunityState.fold(editions, owner)
    }

    private fun concordChannel(
        viewer: String,
        dissolved: Boolean = false,
        banlist: List<String> = emptyList(),
    ) = ConcordChannel(ConcordChannelId(owner, channelIdHex)).apply {
        updateFrom(concordState(dissolved, banlist), emptySet(), viewer)
    }

    @Test
    fun concordMemberInGoodStandingIsAllowed() {
        val channel = concordChannel(viewer = owner)
        assertEquals(PostingGate.Allowed, channel.postingGate())
        assertTrue(channel.canPost())
    }

    @Test
    fun concordBannedMemberIsToldWhy() {
        // The regression this whole type exists for: before, this was a false boolean and a blank space.
        val channel = concordChannel(viewer = banned, banlist = listOf(banned))
        assertEquals(PostingGate.Banned, channel.postingGate())
        assertFalse(channel.canPost())
    }

    @Test
    fun concordDissolvedCommunitySealsEvenTheOwner() {
        val channel = concordChannel(viewer = owner, dissolved = true)
        assertEquals(PostingGate.Dissolved, channel.postingGate())
        assertFalse(channel.canPost())
    }

    @Test
    fun aPrivateChannelWeHoldNoKeyForExplainsItselfInsteadOfOfferingAComposer() {
        // Such a channel is normally omitted from every list (ConcordChannelPlanner drops it), so this
        // is the arrive-anyway path: a stale route must not present a composer whose message would
        // have nowhere valid to go.
        val channel = ConcordChannel(ConcordChannelId(owner, channelIdHex))
        channel.updateFrom(concordState(), emptySet(), owner, holdsChannelKey = false)

        assertEquals(PostingGate.NoKey, channel.postingGate())
        assertFalse(channel.canPost())
    }

    @Test
    fun dissolutionOutranksAPersonalBan() {
        // Both apply. Dissolution is the complete answer — it blocks everyone — so naming the ban too
        // would add a fact that changes nothing about what the user can do.
        val channel = concordChannel(viewer = banned, dissolved = true, banlist = listOf(banned))
        assertEquals(PostingGate.Dissolved, channel.postingGate())
    }

    // ---- NIP-29 / Buzz relay groups ----------------------------------------------------------

    private val relaySelf = "aa".repeat(32)
    private val alice = "bb".repeat(32)
    private val bob = "cc".repeat(32)
    private val gid = "0123456789abcdef"
    private val relay = RelayUrlNormalizer.normalize("wss://relay.example.com")

    @AfterTest
    fun resetDialect() {
        BuzzRelayDialect.clearForTesting()
        BuzzCommunityMembership.clearForTesting()
    }

    private fun metadata(flags: List<String>): GroupMetadataEvent {
        val tags = (listOf(arrayOf("d", gid)) + flags.map { arrayOf(it) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, 100, GroupMetadataEvent.KIND, tags, "", "22".repeat(64)) as GroupMetadataEvent
    }

    private fun members(vararg pubkeys: String): GroupMembersEvent {
        val tags = (listOf(arrayOf("d", gid)) + pubkeys.map { arrayOf("p", it) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, 100, GroupMembersEvent.KIND, tags, "", "22".repeat(64)) as GroupMembersEvent
    }

    private fun relayGroup(flags: List<String>): RelayGroupChannel =
        RelayGroupChannel(GroupId(gid, relay)).apply {
            updateGroupInfo(metadata(flags))
            updateMembers(members(alice))
        }

    @Test
    fun rosterMemberIsAllowed() {
        val channel = relayGroup(flags = emptyList())
        assertEquals(PostingGate.Allowed, channel.postingGate(alice))
        assertTrue(channel.canPost(alice))
    }

    @Test
    fun nonMemberOfAnOpenGroupIsPointedAtJoin() {
        // Standard NIP-29: membership is required to post to every group, so an open group's answer is
        // "join" — an action the top bar actually offers.
        val channel = relayGroup(flags = emptyList())
        assertEquals(PostingGate.NotAMember, channel.postingGate(bob))
        assertFalse(channel.canPost(bob))
    }

    @Test
    fun nonMemberOfAClosedGroupIsToldAnInviteIsNeeded() {
        // A closed group ignores kind-9021 join requests, so offering Join would be a dead end.
        val channel = relayGroup(flags = listOf("closed"))
        assertEquals(PostingGate.InviteOnly, channel.postingGate(bob))
    }

    @Test
    fun openBuzzChannelAllowsANonMemberBecauseTheRelayWould() {
        // Buzz accepts kind-9 on a non-private channel from any authenticated tenant member with no
        // per-channel join — and it stamps "closed" on every channel, which must not read as a gate.
        BuzzRelayDialect.mark(relay)
        val channel = relayGroup(flags = listOf("closed"))
        assertEquals(PostingGate.Allowed, channel.postingGate(bob))
        assertTrue(channel.canPost(bob))
    }

    @Test
    fun privateBuzzChannelStillGatesANonMember() {
        // "private" is the real write ACL on Buzz. Since every Buzz channel also carries "closed", the
        // honest answer there is invite-only: Buzz admits people by invite claim, not by 9021.
        BuzzRelayDialect.mark(relay)
        val channel = relayGroup(flags = listOf("private", "closed"))
        assertEquals(PostingGate.InviteOnly, channel.postingGate(bob))
        assertFalse(channel.canPost(bob))
    }

    @Test
    fun everyBlockedGateKeepsCanPostFalseAndEveryAllowedGateKeepsItTrue() {
        // The invariant that makes the composer and its replacement notice impossible to disagree.
        val cases =
            listOf(
                concordChannel(viewer = owner).postingGate() to true,
                concordChannel(viewer = banned, banlist = listOf(banned)).postingGate() to false,
                concordChannel(viewer = owner, dissolved = true).postingGate() to false,
            )
        cases.forEach { (gate, expectedPostable) ->
            assertEquals(expectedPostable, gate == PostingGate.Allowed)
            assertEquals(!expectedPostable, gate is PostingGate.Blocked)
        }
    }
}
