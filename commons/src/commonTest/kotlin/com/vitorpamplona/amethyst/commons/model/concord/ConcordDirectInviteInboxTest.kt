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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.InviteChannel
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The headless Direct Invite inbox (CORD-05 §6): collects wraps, dedupes by wrap id, skips expired
 * handoffs, validates, parks — and never joins. Plus the accept decision (expired → refuse; held →
 * catch-up keys only on the same base, never a base move).
 */
class ConcordDirectInviteInboxTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val sender = NostrSignerInternal(KeyPair())
    private val me = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    private val vip = "b2".repeat(32)

    private suspend fun community(): NewConcordCommunity = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))

    private fun inviteFor(
        c: NewConcordCommunity,
        expiresAt: Long? = null,
        channels: List<InviteChannel> = emptyList(),
        root: String = c.communityRoot.toHexKey(),
    ) = CommunityInvite(
        communityId = c.communityIdHex,
        owner = c.ownerPubKey,
        ownerSalt = c.ownerSalt.toHexKey(),
        communityRoot = root,
        rootEpoch = c.rootEpoch,
        controlPk = c.controlPkHex,
        channels = channels,
        relays = listOf("wss://relay.example"),
        name = "Nostrichs",
        expiresAt = expiresAt,
    )

    private fun heldEntryOf(c: NewConcordCommunity) =
        ConcordCommunityListEntry(
            id = c.communityIdHex,
            owner = c.ownerPubKey,
            ownerSalt = c.ownerSalt.toHexKey(),
            root = c.communityRoot.toHexKey(),
            rootEpoch = c.rootEpoch,
            controlPk = c.controlPkHex,
            relays = listOf("wss://relay.example"),
            name = "Nostrichs",
            inviteRef = "anchor",
        )

    private fun stateOf(c: NewConcordCommunity): ConcordCommunityState = ConcordCommunityState.fold(ConcordActions.controlEditions(c.genesisWraps, c.controlPlane), c.communityId, c.ownerPubKey)

    /** [c]'s fold with [vip] defined as a live Private Channel. */
    private suspend fun stateWithVip(c: NewConcordCommunity): ConcordCommunityState {
        val editions = ConcordActions.controlEditions(c.genesisWraps, c.controlPlane).toMutableList()
        editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineChannel(owner, c.controlPlane, c.communityId, vip.hexToByteArray(), ChannelEntity(name = "vip", private = true), editions, createdAt = 2L, owner = c.ownerPubKey)), c.controlPlane)
        return ConcordCommunityState.fold(editions, c.communityId, c.ownerPubKey)
    }

    @Test
    fun aValidWrapIsParkedWithItsVerifiedSenderAndDedupedByWrapId() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))

            val first = assertNotNull(inbox.offer(wrap))
            assertEquals(sender.pubKey, first.sender)
            assertEquals(c.communityIdHex, first.invite.communityId)
            assertEquals(setOf(wrap.id), inbox.pending.value.keys)

            // The same wrap again (a re-delivery, or the DM pipeline seeing it too) is the same entry.
            assertSame(first, inbox.offer(wrap))
            assertEquals(1, inbox.pending.value.size)
            assertEquals(wrap.createdAt, inbox.newestWrapCreatedAt)
        }

    @Test
    fun wrapsForSomeoneElseOrForgedOrExpiredAreNotParked() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            // Addressed to someone else.
            assertNull(inbox.offer(ConcordActions.buildDirectInvite(sender, stranger.pubKey, inviteFor(c))))
            // A bundle whose owner proof fails.
            assertNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c).copy(owner = stranger.pubKey))))
            // A handoff whose NIP-40 expiration passed is never decrypted.
            val expired = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, expiresAt = 1_000_000L))
            assertNull(inbox.offer(expired, nowSecs = 1_000L))
            assertTrue(inbox.pending.value.isEmpty())
        }

    @Test
    fun theDmPipelineSealPathParksTheSameInvite() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            val seal = assertIs<SealEvent>(wrap.unwrapOrNull(me))
            val opened = assertNotNull(inbox.offerSeal(wrap.copyNoContent(), seal))
            assertEquals(sender.pubKey, opened.sender)
            assertEquals(wrap.id, opened.wrapId)
            // The sweep delivering the full wrap later doesn't duplicate it.
            assertSame(opened, inbox.offer(wrap))
        }

    @Test
    fun declineDiscardsAndTheWrapNeverResurfaces() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            inbox.offer(wrap)

            assertTrue(inbox.decline(wrap.id))
            assertTrue(inbox.pending.value.isEmpty())
            assertEquals(setOf(wrap.id), inbox.declined.value)
            assertNull(inbox.offer(wrap))
            assertFalse(inbox.decline(wrap.id))

            // After a restart the persisted declines are restored and still win.
            val fresh = ConcordDirectInviteInbox(me)
            fresh.restoreDeclined(inbox.declined.value)
            assertNull(fresh.offer(wrap))
            assertTrue(fresh.pending.value.isEmpty())
        }

    @Test
    fun sinceRewindsTheCursorByTheBackdateWindow() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            assertNull(inbox.since())
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            inbox.offer(wrap)
            assertEquals(wrap.createdAt - 2 * 24 * 60 * 60L, inbox.since())
        }

    @Test
    fun visibleHidesJoinedCommunitiesButKeepsCatchUpsAndFlagsExpiry() =
        runTest {
            val joinedCommunity = community()
            val newCommunity = community()
            val inbox = ConcordDirectInviteInbox(me)

            val toNew = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(newCommunity, expiresAt = 5_000L)), nowSecs = 1L))
            val plainForJoined = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(joinedCommunity))))
            val catchUp = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(joinedCommunity, channels = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip"))))))
            val baseMove = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(joinedCommunity, root = "99".repeat(32), channels = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip"))))))

            val views = ConcordDirectInviteInbox.visible(inbox.pending.value.values, listOf(heldEntryOf(joinedCommunity)), nowMs = 10_000L)
            val byWrap = views.associateBy { it.wrapId }
            assertEquals(setOf(toNew.wrapId, catchUp.wrapId), byWrap.keys)
            assertFalse(plainForJoined.wrapId in byWrap)
            assertFalse(baseMove.wrapId in byWrap)
            assertTrue(byWrap.getValue(catchUp.wrapId).catchUp)
            assertFalse(byWrap.getValue(toNew.wrapId).catchUp)
            assertTrue(byWrap.getValue(toNew.wrapId).expired)
            assertFalse(byWrap.getValue(catchUp.wrapId).expired)
            assertEquals(listOf("vip"), byWrap.getValue(catchUp.wrapId).channelNames)
        }

    @Test
    fun visibleKeepsOneInvitePerCommunity() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val older = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c), createdAt = 1_700_000_000L)))
            val newer = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(stranger, me.pubKey, inviteFor(c), createdAt = 1_700_000_100L)))
            assertEquals(2, inbox.pending.value.size)
            val views = ConcordDirectInviteInbox.visible(inbox.pending.value.values, emptyList())
            assertEquals(listOf(newer.wrapId), views.map { it.wrapId })
            assertFalse(older.wrapId in views.map { it.wrapId })
        }

    @Test
    fun acceptRefusesAnExpiredInvite() =
        runTest {
            val c = community()
            val opened = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, expiresAt = 5_000L)), me))
            assertEquals(DirectInviteAcceptPlan.Expired, ConcordDirectInviteInbox.acceptPlan(opened, null, null, me.pubKey, nowMs = 5_001L))
            assertEquals(DirectInviteAcceptPlan.Join, ConcordDirectInviteInbox.acceptPlan(opened, null, null, me.pubKey, nowMs = 4_999L))
        }

    @Test
    fun acceptOnAHeldCommunityOnlyAddsKeysAndNeverMovesTheBase() =
        runTest {
            val c = community()
            val held = heldEntryOf(c)
            val state = stateWithVip(c)
            val grant = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip"))

            // Same base, new key, from staff (the owner): a catch-up that keeps the held base and anchor.
            val catchUp = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c, channels = grant)), me))
            val plan = assertIs<DirectInviteAcceptPlan.CatchUp>(ConcordDirectInviteInbox.acceptPlan(catchUp, held, state, me.pubKey))
            assertEquals(held.root, plan.entry.root)
            assertEquals(held.rootEpoch, plan.entry.rootEpoch)
            assertEquals(held.controlPk, plan.entry.controlPk)
            assertEquals("anchor", plan.entry.inviteRef)
            assertEquals(listOf(vip), plan.entry.privateChannels.map { it.channelId })
            assertEquals(listOf(vip), plan.channelIds)

            // A plain keyholder can't plant a key, and a channel the fold doesn't know as Private isn't one.
            val fromMember = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, channels = grant)), me))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(fromMember, held, state, me.pubKey))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(catchUp, held, stateOf(c), me.pubKey))

            // No fold yet: the ban verdict is unknown, so it waits.
            assertEquals(DirectInviteAcceptPlan.RosterNotLoaded, ConcordDirectInviteInbox.acceptPlan(catchUp, held, null, me.pubKey))

            // Already holding that key: nothing new.
            val holding = held.let { ConcordCommunityListEntry(it.id, it.owner, it.ownerSalt, it.root, it.rootEpoch, it.controlPk, privateChannels = listOf(PrivateChannelKey(vip, "db".repeat(32), 0, "vip")), relays = it.relays, name = it.name) }
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(catchUp, holding, state, me.pubKey))

            // Even from staff, a bundle never REPLACES a held key — not at a higher, nor an absurd, epoch.
            // A held key moves only through a channel rekey, whose prevcommit proves continuity.
            val hijack = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c, channels = listOf(InviteChannel(vip, "ee".repeat(32), 1_000_000_000L, "vip")))), me))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(hijack, holding, state, me.pubKey))

            // A different base for a held community is never adopted, keys or not.
            val baseMove = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, root = "99".repeat(32), channels = grant)), me))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(baseMove, held, state, me.pubKey))

            // A dissolved community takes no new keys.
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(catchUp, held, state.withDissolved(true), me.pubKey))
        }

    @Test
    fun acceptRefusesACatchUpWhenTheHeldRosterBansUs() =
        runTest {
            val c = community()
            val editions = ConcordActions.controlEditions(c.genesisWraps, c.controlPlane).toMutableList()
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, c.controlPlane, c.communityId, me.pubKey, editions, createdAt = 2L, owner = c.ownerPubKey)), c.controlPlane)
            val banned = ConcordCommunityState.fold(editions, c.communityId, c.ownerPubKey)
            assertTrue(banned.authority.isBanned(me.pubKey))

            val catchUp = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, channels = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip")))), me))
            assertEquals(DirectInviteAcceptPlan.Banned, ConcordDirectInviteInbox.acceptPlan(catchUp, heldEntryOf(c), banned, me.pubKey))
        }
}
