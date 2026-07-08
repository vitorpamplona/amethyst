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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Client-side derivations in [RelayGroupChannel] — the relay-signed roster is the
 * source of truth, so this exercises how the channel folds 39000/39001/39002 into
 * a membership answer and a member count, and that the `createdAt` supersede
 * guards drop stale events. Protocol parsing itself is covered by Quartz's
 * Nip29ArmadaInteropTest; here we test the fold, not the parse.
 */
class RelayGroupChannelTest {
    private val relaySelf = "aa".repeat(32)
    private val alice = "bb".repeat(32)
    private val bob = "cc".repeat(32)
    private val carol = "dd".repeat(32)
    private val gid = "0123456789abcdef"
    private val relay = RelayUrlNormalizer.normalize("wss://relay.example.com")

    private fun channel() = RelayGroupChannel(GroupId(gid, relay))

    private fun metadata(
        createdAt: Long,
        flags: List<String> = emptyList(),
        name: String? = null,
    ): GroupMetadataEvent {
        val tags =
            buildList {
                add(arrayOf("d", gid))
                name?.let { add(arrayOf("name", it)) }
                flags.forEach { add(arrayOf(it)) }
            }.toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupMetadataEvent.KIND, tags, "", "22".repeat(64)) as GroupMetadataEvent
    }

    private fun members(
        createdAt: Long,
        vararg pubkeys: String,
    ): GroupMembersEvent {
        val tags = (listOf(arrayOf("d", gid)) + pubkeys.map { arrayOf("p", it) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupMembersEvent.KIND, tags, "", "22".repeat(64)) as GroupMembersEvent
    }

    private fun admins(
        createdAt: Long,
        vararg entries: Pair<String, List<String>>,
    ): GroupAdminsEvent {
        val tags = (listOf(arrayOf("d", gid)) + entries.map { (pk, roles) -> arrayOf("p", pk, *roles.toTypedArray()) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupAdminsEvent.KIND, tags, "", "22".repeat(64)) as GroupAdminsEvent
    }

    @Test
    fun membershipFromRolesAndMembers() {
        val c = channel()
        c.updateAdmins(admins(100, alice to listOf("admin"), bob to listOf("moderator")))
        c.updateMembers(members(100, alice, bob, carol))

        assertEquals(RelayGroupMembership.ADMIN, c.membershipOf(alice))
        assertEquals(RelayGroupMembership.MODERATOR, c.membershipOf(bob))
        // carol is only in the members list — a plain member.
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(carol))
        // unknown key.
        assertEquals(RelayGroupMembership.NONE, c.membershipOf("ee".repeat(32)))
    }

    @Test
    fun adminInAdminsListButAbsentFromMembersStillCounts() {
        // Relays commonly list admins in 39001 only, not 39002. The admin must
        // still resolve as a member, not NONE.
        val c = channel()
        c.updateAdmins(admins(100, alice to listOf("admin")))
        c.updateMembers(members(100, bob))

        assertEquals(RelayGroupMembership.ADMIN, c.membershipOf(alice))
        assertTrue(c.membershipOf(alice).isMember())
    }

    @Test
    fun adminWithUnknownRoleStillModerates() {
        // Presence in the 39001 admins list is the moderation signal; an
        // unrecognized (or empty) role label must not demote to plain MEMBER.
        val c = channel()
        c.updateAdmins(admins(100, alice to listOf("ceo"), bob to emptyList()))
        assertEquals(RelayGroupMembership.MODERATOR, c.membershipOf(alice))
        assertEquals(RelayGroupMembership.MODERATOR, c.membershipOf(bob))
        assertTrue(c.membershipOf(alice).canModerate())
    }

    @Test
    fun equalCreatedAtDoesNotResupersede() {
        val c = channel()
        c.updateMembers(members(100, alice, bob))
        // A second 39002 with the SAME createdAt but fewer members must not win.
        c.updateMembers(members(100, alice))
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(bob))
        assertEquals(2, c.memberCount())
    }

    @Test
    fun memberCountDedupesAdminsAndMembers() {
        val c = channel()
        // alice appears in BOTH lists; she must be counted once.
        c.updateAdmins(admins(100, alice to listOf("admin")))
        c.updateMembers(members(100, alice, bob, carol))
        assertEquals(3, c.memberCount())
    }

    @Test
    fun staleMembersEventDoesNotSupersede() {
        val c = channel()
        c.updateMembers(members(200, alice, bob))
        // An OLDER roster arrives late (out-of-order relay delivery) — ignore it.
        c.updateMembers(members(100, alice))
        assertEquals(2, c.memberCount())
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(bob))
    }

    @Test
    fun newerMembersEventReplaces() {
        val c = channel()
        c.updateMembers(members(100, alice))
        c.updateMembers(members(200, alice, bob))
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(bob))
        assertEquals(2, c.memberCount())
    }

    @Test
    fun staleMetadataDoesNotSupersedeFlagsOrName() {
        val c = channel()
        c.updateGroupInfo(metadata(200, flags = listOf("private", "closed"), name = "Newer"))
        c.updateGroupInfo(metadata(100, flags = emptyList(), name = "Older"))

        assertEquals("Newer", c.toBestDisplayName())
        assertTrue(c.isPrivate())
        assertTrue(c.isClosed())
    }

    @Test
    fun flagsReflectLatestMetadata() {
        val c = channel()
        assertFalse(c.isPrivate())
        assertFalse(c.isClosed())
        c.updateGroupInfo(metadata(100, flags = listOf("private")))
        assertTrue(c.isPrivate())
        assertFalse(c.isClosed())
    }

    @Test
    fun displayNameFallsBackToGroupIdWithoutMetadata() {
        assertEquals(gid, channel().toBestDisplayName())
    }
}
