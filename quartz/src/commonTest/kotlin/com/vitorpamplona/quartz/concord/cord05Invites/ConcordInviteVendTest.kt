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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.ControlFixtures
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Direct Invite for an already-joined community is a catch-up: it may only add Private Channel
 * keys on the SAME base (root, epoch, control_pk) — never move the base (Armada `catchUpChannelIds`).
 */
class ConcordInviteVendTest {
    private val communityId = "11".repeat(32)
    private val root = "22".repeat(32)
    private val controlPk = "33".repeat(32)
    private val chanA = "a1".repeat(32)
    private val chanB = "b2".repeat(32)
    private val keyA = "ca".repeat(32)
    private val keyB = "db".repeat(32)

    private val held =
        ConcordCommunityListEntry(
            id = communityId,
            owner = "44".repeat(32),
            ownerSalt = "55".repeat(32),
            root = root,
            rootEpoch = 3,
            controlPk = controlPk,
            privateChannels = listOf(PrivateChannelKey(chanA, keyA, 1, "mods")),
            relays = listOf("wss://relay.example"),
            name = "Nostrichs",
            inviteRef = "naddr1ref",
        )

    private fun bundle(
        root: String = this.root,
        epoch: Long = 3,
        controlPk: String? = this.controlPk,
        channels: List<InviteChannel>,
    ) = CommunityInvite(
        communityId = communityId,
        owner = held.owner,
        ownerSalt = held.ownerSalt,
        communityRoot = root,
        rootEpoch = epoch,
        controlPk = controlPk,
        channels = channels,
        name = "Nostrichs",
    )

    @Test
    fun aNewPrivateChannelKeyOnTheSameBaseIsACatchUp() {
        val b = bundle(channels = listOf(InviteChannel(chanA, keyA, 1, "mods"), InviteChannel(chanB.uppercase(), keyB, 0, "vip")))
        assertEquals(listOf(chanB), ConcordInviteVend.catchUpChannelIds(held, b))

        val adopted = ConcordInviteVend.adoptCatchUp(held, b)
        assertNotNull(adopted)
        // The base never moves.
        assertEquals(root, adopted.root)
        assertEquals(3, adopted.rootEpoch)
        assertEquals(controlPk, adopted.controlPk)
        assertEquals(held.inviteRef, adopted.inviteRef)
        assertEquals(setOf(chanA to keyA, chanB to keyB), adopted.privateChannels.map { it.channelId to it.key }.toSet())
    }

    @Test
    fun aBundleNeverReplacesAHeldKey() {
        // A held key moves only through a channel rekey (prevcommit continuity). A bare bundle at a
        // higher — even absurd — epoch contributes nothing, so a keyholder can't park us on a dead key.
        val bogus = "ee".repeat(32)
        for (epoch in listOf(2L, 1_000_000_000L)) {
            val b = bundle(channels = listOf(InviteChannel(chanA, bogus, epoch, "mods")))
            assertTrue(ConcordInviteVend.catchUpChannelIds(held, b).isEmpty())
            assertNull(ConcordInviteVend.adoptCatchUp(held, b))
        }
    }

    @Test
    fun aBundleOnAnotherBaseIsNeverACatchUp() {
        val grant = listOf(InviteChannel(chanB, keyB, 0, "vip"))
        assertTrue(ConcordInviteVend.catchUpChannelIds(held, bundle(root = "99".repeat(32), channels = grant)).isEmpty())
        assertTrue(ConcordInviteVend.catchUpChannelIds(held, bundle(epoch = 4, channels = grant)).isEmpty())
        assertTrue(ConcordInviteVend.catchUpChannelIds(held, bundle(controlPk = "98".repeat(32), channels = grant)).isEmpty())
        assertTrue(ConcordInviteVend.catchUpChannelIds(held, bundle(controlPk = null, channels = grant)).isEmpty())
        assertNull(ConcordInviteVend.adoptCatchUp(held, bundle(root = "99".repeat(32), channels = grant)))
    }

    @Test
    fun nothingHeldMeansNoCatchUpAndKeylessGrantsDeliverNothing() {
        assertTrue(ConcordInviteVend.catchUpChannelIds(null, bundle(channels = listOf(InviteChannel(chanB, keyB, 0)))).isEmpty())
        assertTrue(ConcordInviteVend.catchUpChannelIds(held, bundle(channels = listOf(InviteChannel(chanB, "", 0)))).isEmpty())
        assertNull(ConcordInviteVend.adoptCatchUp(held, bundle(channels = emptyList())))
    }

    // ---- entitlement (Armada channelAccess.ts) and catch-up adoption (catchUpAdoption.ts) --------

    private val owner = "0f".repeat(32)
    private val alice = "a1".repeat(32)
    private val bob = "b2".repeat(32)
    private val modRole = "71".repeat(32)
    private val accessRole = "72".repeat(32)

    private fun role(
        roleId: String,
        json: String,
    ) = ControlEdition(ControlEntityKind.ROLE, roleId.hexToByteArray(), 0, null, null, json, owner, "role-$roleId", 0)

    private fun grant(
        member: String,
        roleIds: List<String>,
        version: Long = 0,
        prev: ControlEdition? = null,
    ) = ControlEdition(
        ControlEntityKind.GRANT,
        ControlFixtures.grantEid(member).hexToByteArray(),
        version,
        prev?.hash,
        null,
        """{"member":"$member","role_ids":[${roleIds.joinToString(",") { "\"$it\"" }}]}""",
        owner,
        "grant-$member-$version",
        version,
    )

    private val roles =
        listOf(
            // A staff role (MANAGE_CHANNELS) with server scope, and a bit-less access role scoped to chanA.
            role(modRole, """{"role_id":"$modRole","name":"Mod","position":2,"permissions":"2"}"""),
            role(accessRole, """{"role_id":"$accessRole","name":"mods-room","position":10,"permissions":"0","scope":{"kind":"channel","channel_id":"$chanA"}}"""),
        )

    private fun authority(vararg extra: ControlEdition): AuthorityResolver = ControlFixtures.resolve(roles + extra, owner)

    @Test
    fun theOwnerAndScopedRoleHoldersAreEntitled() {
        val aliceIn = grant(alice, listOf(accessRole))
        val bobMod = grant(bob, listOf(modRole))
        val a = authority(aliceIn, bobMod)
        assertEquals(setOf(owner, alice), ConcordInviteVend.entitledMembers(a, chanA))
        assertTrue(ConcordInviteVend.isEntitled(a, owner, chanB))
        // A server-scoped staff role grants authority, never read access.
        assertTrue(!ConcordInviteVend.isEntitled(a, bob, chanA))
        // A link has no recipient and vends nothing; a member gets exactly their channels.
        val held = listOf(PrivateChannelKey(chanA, keyA, 1, "mods"), PrivateChannelKey(chanB, keyB, 0, "vip"))
        assertTrue(ConcordInviteVend.vendableChannels(held, a, null).isEmpty())
        assertEquals(listOf(chanA), ConcordInviteVend.vendableChannels(held, a, alice).map { it.channelId })
        assertEquals(listOf(chanA, chanB), ConcordInviteVend.vendableChannels(held, a, owner).map { it.channelId })
    }

    @Test
    fun accessChangesNameWhoToVendAndWhoARotationMustCut() {
        val aliceIn = grant(alice, listOf(accessRole))
        val before = authority()
        val afterGrant = authority(aliceIn)
        val granted = ConcordInviteVend.accessChanges(before, afterGrant, listOf(chanA, chanB)).single()
        assertEquals(chanA, granted.channelIdHex)
        assertEquals(setOf(alice), granted.gained)
        assertTrue(granted.lost.isEmpty())

        val afterRevoke = authority(aliceIn, grant(alice, emptyList(), version = 1, prev = aliceIn))
        val revoked = ConcordInviteVend.accessChanges(afterGrant, afterRevoke, listOf(chanA)).single()
        assertEquals(setOf(alice), revoked.lost)
        assertTrue(ConcordInviteVend.accessChanges(afterGrant, afterGrant, listOf(chanA)).isEmpty())
    }

    @Test
    fun aStaffSentCatchUpForAnEntitledChannelIsAdoptedWithoutAClick() {
        val member = held
        val grantedChan = bundle(channels = listOf(InviteChannel(chanB, keyB, 0, "vip")))
        val scopedB = role("73".repeat(32), """{"role_id":"${"73".repeat(32)}","name":"vip","position":11,"permissions":"0","scope":{"kind":"channel","channel_id":"$chanB"}}""")
        val a = authority(scopedB, grant(alice, listOf("73".repeat(32))), grant(bob, listOf(modRole)))
        val live = setOf(chanA, chanB)
        assertEquals(ConcordInviteVend.CatchUpVerdict.ADOPT, ConcordInviteVend.judgeCatchUp(a, live, alice, owner, grantedChan, member))
        assertEquals(ConcordInviteVend.CatchUpVerdict.ADOPT, ConcordInviteVend.judgeCatchUp(a, live, alice, bob, grantedChan, member))
        assertEquals(ConcordInviteVend.CatchUpVerdict.NO_FOLD, ConcordInviteVend.judgeCatchUp(null, live, alice, owner, grantedChan, member))
        // A plain keyholder (alice) can't plant a key in bob's list automatically.
        assertEquals(ConcordInviteVend.CatchUpVerdict.SENDER_NOT_STAFF, ConcordInviteVend.judgeCatchUp(a, live, bob, alice, grantedChan, member))
        // Bob holds no role scoped to chanB.
        assertEquals(ConcordInviteVend.CatchUpVerdict.NOT_ENTITLED, ConcordInviteVend.judgeCatchUp(a, live, bob, owner, grantedChan, member))
        assertEquals(ConcordInviteVend.CatchUpVerdict.NOTHING_NEW, ConcordInviteVend.judgeCatchUp(a, live, alice, owner, bundle(channels = listOf(InviteChannel(chanA, keyA, 1))), member))
        // A channel the fold doesn't know as a live Private Channel is never auto-adopted.
        assertEquals(ConcordInviteVend.CatchUpVerdict.NOT_ENTITLED, ConcordInviteVend.judgeCatchUp(a, setOf(chanA), alice, owner, grantedChan, member))

        // Manual accept: staff sender and a live Private Channel, entitlement waived by the click.
        assertEquals(listOf(chanB), ConcordInviteVend.admissibleCatchUpIds(member, grantedChan, a, live, owner))
        assertTrue(ConcordInviteVend.admissibleCatchUpIds(member, grantedChan, a, live, alice).isEmpty())
        assertTrue(ConcordInviteVend.admissibleCatchUpIds(member, grantedChan, a, setOf(chanA), owner).isEmpty())
    }

    @Test
    fun aCatchUpNeverRestoresAKeyBelowACut() {
        val cut = ConcordChannelKeyring.withoutChannel(held, chanA, 2)
        assertTrue(ConcordInviteVend.catchUpChannelIds(cut, bundle(channels = listOf(InviteChannel(chanA, keyA, 1)))).isEmpty())
        assertEquals(listOf(chanA), ConcordInviteVend.catchUpChannelIds(cut, bundle(channels = listOf(InviteChannel(chanA, keyB, 2)))))
    }
}
