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
    fun aNewerEpochOfAHeldChannelReplacesIt() {
        val newer = "ee".repeat(32)
        val b = bundle(channels = listOf(InviteChannel(chanA, newer, 2, "mods")))
        assertEquals(listOf(chanA), ConcordInviteVend.catchUpChannelIds(held, b))
        val adopted = assertNotNull(ConcordInviteVend.adoptCatchUp(held, b))
        assertEquals(listOf(Triple(chanA, newer, 2L)), adopted.privateChannels.map { Triple(it.channelId, it.key, it.epoch) })

        // Same or older epoch contributes nothing.
        assertTrue(ConcordInviteVend.catchUpChannelIds(held, bundle(channels = listOf(InviteChannel(chanA, newer, 1)))).isEmpty())
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
}
