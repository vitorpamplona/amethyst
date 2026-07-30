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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which secret addresses a Concord channel (CORD-03 §1).
 *
 * The bug this pins down: every call site used to derive *every* channel's plane from the shared
 * `community_root`, including `private: true` ones. That put private traffic on an address any member
 * of the community could derive — so a private channel was readable and postable by people who were
 * never granted its key — and, symmetrically, made a correctly-addressed private channel from another
 * client look permanently empty.
 */
class ConcordChannelPlannerTest {
    private val owner = "0f".repeat(32)
    private val communityId = "1c".repeat(32)
    private val root = "aa".repeat(32)
    private val priorRoot = "ab".repeat(32)
    private val publicChannelId = "c1".repeat(32)
    private val privateChannelId = "c2".repeat(32)
    private val privateKey = "77".repeat(32)
    private val rootEpoch = 3L
    private val privateEpoch = 5L

    private fun ed(
        kind: ControlEntityKind,
        eid: String,
        content: String,
    ) = ControlEdition(kind, eid.hexToByteArray(), 0, null, null, content, owner, "r-$eid", 0)

    /** A fold with one public channel and one private channel. */
    private fun state(): ConcordCommunityState =
        ConcordCommunityState.fold(
            listOf(
                ed(ControlEntityKind.CHANNEL, publicChannelId, """{"name":"general"}"""),
                ed(ControlEntityKind.CHANNEL, privateChannelId, """{"name":"mods","private":true}"""),
            ),
            owner,
        )

    private fun entry(
        privateChannels: List<PrivateChannelKey> = emptyList(),
        heldRoots: List<HeldRoot> = emptyList(),
    ) = ConcordCommunityListEntry(
        id = communityId,
        owner = owner,
        ownerSalt = "01".repeat(32),
        root = root,
        rootEpoch = rootEpoch,
        heldRoots = heldRoots,
        privateChannels = privateChannels,
        relays = listOf("wss://r.example"),
    )

    private val heldPrivate = PrivateChannelKey(privateChannelId, privateKey, privateEpoch, "mods")

    @Test
    fun publicChannelIsAddressedByTheCommunityRootAtTheRootEpoch() {
        val planes = ConcordChannelPlanner.channelPlanesFor(entry(), state(), publicChannelId)!!
        assertEquals(false, planes.isPrivate)
        assertEquals(rootEpoch, planes.write.epoch)
        assertEquals(
            ConcordActions.publicChannel(root.hexToByteArray(), publicChannelId.hexToByteArray(), rootEpoch).publicKeyHex,
            planes.write.key.publicKeyHex,
        )
    }

    @Test
    fun privateChannelIsAddressedByItsOwnDeliveredKeyAtItsOwnEpoch() {
        val planes = ConcordChannelPlanner.channelPlanesFor(entry(privateChannels = listOf(heldPrivate)), state(), privateChannelId)!!

        assertEquals(true, planes.isPrivate)
        // Its own epoch, not the community's — the wraps are bound to it, so taking the root epoch
        // here would make every private message fail its bind check on arrival.
        assertEquals(privateEpoch, planes.write.epoch)
        assertEquals(
            ConcordActions.privateChannel(privateKey.hexToByteArray(), privateChannelId.hexToByteArray(), privateEpoch).publicKeyHex,
            planes.write.key.publicKeyHex,
        )
        // The regression: the root-derived address is a DIFFERENT plane, and must not be used.
        assertNotEquals(
            ConcordActions.publicChannel(root.hexToByteArray(), privateChannelId.hexToByteArray(), rootEpoch).publicKeyHex,
            planes.write.key.publicKeyHex,
        )
    }

    @Test
    fun aPrivateChannelWeHoldNoKeyForIsOmittedEntirely() {
        val entry = entry() // community member, but never granted the private channel's key
        val planes = ConcordChannelPlanner.channelPlanes(entry, state())

        assertEquals(listOf(publicChannelId), planes.map { it.channelIdHex })
        assertNull(ConcordChannelPlanner.channelPlanesFor(entry, state(), privateChannelId))
        // The important half: no write coordinate at all, so no send path can fall back to the root.
        assertNull(ConcordChannelPlanner.writePlane(entry, state(), privateChannelId))
    }

    @Test
    fun holdingTheKeyMakesThePrivateChannelAppear() {
        val planes = ConcordChannelPlanner.channelPlanes(entry(privateChannels = listOf(heldPrivate)), state())
        assertEquals(setOf(publicChannelId, privateChannelId), planes.map { it.channelIdHex }.toSet())
    }

    @Test
    fun aBundleHeldPrivateChannelShowsBeforeItsDefinitionFolds() {
        // A fresh join holds its private-channel keys before the Control Plane catches up; the room
        // shouldn't vanish in the meantime. Also covers a null state (nothing folded at all).
        val entry = entry(privateChannels = listOf(heldPrivate))
        assertEquals(listOf(privateChannelId), ConcordChannelPlanner.channelPlanes(entry, null).map { it.channelIdHex })

        val planes = ConcordChannelPlanner.channelPlanesFor(entry, null, privateChannelId)!!
        assertEquals(true, planes.isPrivate)
        assertEquals(privateEpoch, planes.write.epoch)
    }

    @Test
    fun publicChannelReadsSpanHeldEpochsAndPrivateOnesDoNot() {
        val entry =
            entry(
                privateChannels = listOf(heldPrivate),
                heldRoots = listOf(HeldRoot(rootEpoch - 1, priorRoot)),
            )
        val planes = ConcordChannelPlanner.channelPlanes(entry, state()).associateBy { it.channelIdHex }

        // Public: current epoch first, then the prior-epoch plane that holds pre-Refounding history.
        val public = planes.getValue(publicChannelId)
        assertEquals(listOf(rootEpoch, rootEpoch - 1), public.reads.map { it.epoch })
        assertEquals(
            public.write.key.publicKeyHex,
            public.reads
                .first()
                .key.publicKeyHex,
        )
        assertEquals(
            ConcordActions.publicChannel(priorRoot.hexToByteArray(), publicChannelId.hexToByteArray(), rootEpoch - 1).publicKeyHex,
            public.reads[1].key.publicKeyHex,
        )

        // Private: exactly one plane. A per-channel rekey replaces the delivered key rather than
        // accumulating epochs, and a held ROOT never derives a private channel's address.
        val private = planes.getValue(privateChannelId)
        assertEquals(listOf(privateEpoch), private.reads.map { it.epoch })
    }

    @Test
    fun heldRootsContainingTheCurrentEpochDoNotDuplicateThePlane() {
        // Stranded recovery folds the current root into heldRoots, so the same address can arrive
        // twice; subscribing to it twice would just pad every REQ's author list.
        val entry = entry(heldRoots = listOf(HeldRoot(rootEpoch, root), HeldRoot(rootEpoch - 1, priorRoot)))
        val reads = ConcordChannelPlanner.channelPlanesFor(entry, state(), publicChannelId)!!.reads

        assertEquals(reads.size, reads.distinctBy { it.key.publicKeyHex }.size)
        assertEquals(listOf(rootEpoch, rootEpoch - 1), reads.map { it.epoch })
    }

    @Test
    fun anUnknownChannelHasNoPlanes() {
        assertNull(ConcordChannelPlanner.channelPlanesFor(entry(), state(), "ff".repeat(32)))
    }

    @Test
    fun everyResolvedPlaneCarriesItsOwnChannelId() {
        // The id travels with the plane because ingest routes an inbound wrap by address and needs to
        // know which channel it landed in — a mismatch here would file messages under the wrong room.
        val planes = ConcordChannelPlanner.channelPlanes(entry(privateChannels = listOf(heldPrivate)), state())
        assertTrue(planes.all { channel -> channel.reads.all { it.channelIdHex == channel.channelIdHex } })
    }
}
