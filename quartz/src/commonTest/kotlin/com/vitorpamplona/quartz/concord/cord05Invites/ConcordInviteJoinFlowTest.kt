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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The full public-invite path: create a community → mint an invite link → a
 * stranger redeems the link, reconstructs the root, and reads the community's
 * genesis Control Plane. This is the create-and-invite flow the app drives.
 */
class ConcordInviteJoinFlowTest {
    private val owner = NostrSignerInternal(KeyPair())

    private suspend fun inviteFor(community: com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity) =
        CommunityInvite(
            communityId = community.communityIdHex,
            owner = community.ownerPubKey,
            ownerSalt = community.ownerSalt.toHexKey(),
            communityRoot = community.communityRoot.toHexKey(),
            rootEpoch = community.rootEpoch,
            relays = listOf("wss://relay.example"),
            name = "Nostrichs",
        )

    @Test
    fun createMintRedeemAndRead() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", inviteFor(community), createdAt = 1L, relays = listOf("wss://relay.example"))

            // The joiner only has the URL. Extract the token from the private fragment.
            val parsedUrl = ConcordInviteLink.parseUrl(minted.url)
            assertNotNull(parsedUrl)
            assertEquals(minted.linkSignerPubKey, parsedUrl.linkSignerPubKey)

            // Decrypt the fetched bundle with that token and verify self-certification.
            val invite = ConcordInviteBundle.parse(minted.bundleEvent, parsedUrl.fragment.token)
            assertNotNull(invite)
            assertTrue(ConcordInviteBundle.validate(invite))
            assertEquals(community.communityIdHex, invite.communityId)

            // Reconstruct the root, derive the Control Plane, and read the genesis.
            val controlPlane =
                ConcordKeyDerivation.controlPlaneKey(
                    invite.communityRoot.hexToByteArray(),
                    invite.communityId.hexToByteArray(),
                    invite.rootEpoch,
                )
            val editions = community.genesisWraps.mapNotNull { ControlEdition.fromRumor(ConcordStreamEnvelope.open(it, controlPlane).rumor) }
            val state = ConcordCommunityState.fold(editions, invite.owner)
            assertEquals("Nostrichs", state.metadata?.name)
            assertTrue(state.channels.isNotEmpty()) // #general is visible to the new member
        }

    @Test
    fun wrongTokenCannotOpenTheBundle() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Secret", createdAt = 1L)
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", inviteFor(community), createdAt = 1L)
            assertNull(ConcordInviteBundle.parse(minted.bundleEvent, ByteArray(16) { 0x01 })) // random token fails
        }

    @Test
    fun validateRejectsForgedOwner() {
        // owner + salt that do not reproduce the claimed community_id
        val forged =
            CommunityInvite(
                communityId = "00".repeat(32),
                owner = KeyPair().pubKey.toHexKey(),
                ownerSalt = "aa".repeat(32),
                communityRoot = "bb".repeat(32),
            )
        assertFalse(ConcordInviteBundle.validate(forged))
    }

    @Test
    fun expiryBlocksJoiningButNotPreview() {
        val invite = CommunityInvite("id", "o", "s", "r", expiresAt = 1_000L)
        assertTrue(ConcordInviteBundle.isExpired(invite, nowMs = 2_000L))
        assertFalse(ConcordInviteBundle.isExpired(invite, nowMs = 500L))
    }
}
