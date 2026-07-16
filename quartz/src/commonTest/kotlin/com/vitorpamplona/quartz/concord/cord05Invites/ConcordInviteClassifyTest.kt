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
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VskTag
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Resolving an invite's addressable coordinate per CORD-05 §2: a live `vsk=6` bundle
 * opens, a `vsk=9` revocation tombstone wins even over a still-openable stale copy,
 * an unknown/mis-posted sub-kind is unreadable, and an empty fetch is absent.
 */
class ConcordInviteClassifyTest {
    private val owner = NostrSignerInternal(KeyPair())

    private fun inviteFor(community: NewConcordCommunity) =
        CommunityInvite(
            communityId = community.communityIdHex,
            owner = community.ownerPubKey,
            ownerSalt = community.ownerSalt.toHexKey(),
            communityRoot = community.communityRoot.toHexKey(),
            rootEpoch = community.rootEpoch,
            relays = listOf("wss://relay.example"),
            name = "Nostrichs",
        )

    /** A raw kind-33301 event at the link-signer coordinate carrying an arbitrary [vsk] wire value. */
    private fun coordinateEvent(
        linkSignerPubKey: String,
        vsk: String,
        createdAt: Long,
        content: String = "",
    ) = Event(
        id = "00".repeat(32),
        pubKey = linkSignerPubKey,
        createdAt = createdAt,
        kind = ConcordInviteBundleEvent.KIND,
        tags = arrayOf(arrayOf("d", ""), VskTag.TAG_NAME.let { arrayOf(it, vsk) }),
        content = content,
        sig = "00".repeat(64),
    )

    @Test
    fun liveBundleOpens() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", inviteFor(community), createdAt = 1L, relays = listOf("wss://relay.example"))

            val status = ConcordInviteBundle.classify(listOf(minted.bundleEvent), minted.token)
            assertTrue(status is InviteBundleStatus.Live)
            assertEquals(community.communityIdHex, status.invite.communityId)
        }

    @Test
    fun revocationTombstoneWinsOverStaleBundle() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", inviteFor(community), createdAt = 1L, relays = listOf("wss://relay.example"))

            // A newer vsk=9 tombstone at the same coordinate buries the still-openable bundle.
            val tombstone = coordinateEvent(minted.linkSignerPubKey, ControlEntityKind.INVITE_REVOKED.wire, createdAt = 2L)

            assertEquals(InviteBundleStatus.Revoked, ConcordInviteBundle.classify(listOf(minted.bundleEvent, tombstone), minted.token))
            // Order of the fetched list must not matter — newest createdAt wins regardless.
            assertEquals(InviteBundleStatus.Revoked, ConcordInviteBundle.classify(listOf(tombstone, minted.bundleEvent), minted.token))
        }

    @Test
    fun unknownSubKindIsUnreadable() =
        runTest {
            // A mis-posted registry (vsk=8) at the bundle coordinate — the exact shape of the
            // relayop.xyz link that hung — is present but not a vsk=6 bundle we can open.
            val registry = coordinateEvent("aa".repeat(32), ControlEntityKind.INVITE_REGISTRY.wire, createdAt = 1L, content = "unopenable")
            assertEquals(InviteBundleStatus.Unreadable, ConcordInviteBundle.classify(listOf(registry), ByteArray(16)))
        }

    @Test
    fun emptyFetchIsAbsent() {
        assertEquals(InviteBundleStatus.Absent, ConcordInviteBundle.classify(emptyList(), ByteArray(16)))
    }

    @Test
    fun realRelayopBundleIsUnreadable() {
        // The actual kind-33301 event behind the reported relayop.xyz/invite link (vsk=8), plus the
        // 16-byte token from its #fragment. It fetches fine but can't be opened, so a compliant
        // redeemer reports Unreadable instead of spinning forever.
        val json =
            """{"content":"ApoDjyzcHUg2imEiqw6Gsfpc2O86r+CMtMor+jc8ZlgrYwlI6CCmX7qGGEQvEJ5537nINE9H09Ro8RtEghpYgwkhdPHS274RpklFmuyLMdcoC5u1EVhppu8BrlHZ0YBfw3GX1Ui0uwy3V/J+rvrYiLhdREmwlK39JAX8sZfzCUhVtDMCgLVy03dwdpTC1Kj/ZeZJTYhJ8qmaN2273jgBTno/bFLzJlYvbANss69Tg53mljcmdSyhMlZ8z1kuenm1zkrPO5yHvi//r25tXkXb580OCkWxTmEwFzo20ntMgFnVSwVRvLZelOZt++tMevqi2Z5asvDgG7RytHP/0vLxxPzmjH0No+nITsxcmDbEweoKvSSzoc/7DYzENmfmrLXgP2KU/eE6CpTcSNaedLVKbAu9XptdtV8ruZxHjVBh1wpOwXkETEdqqvbCiR4TCNWzqbmwRKJ+acvZLBxhXcpfqmRsolaATU4sZKLs4iu92YpMIuUDh2Pquu0Daiz/IGnVe7BPb7E/gSd9NBFIxds6Nk1DbP8XKMRtYmWdTforUPWZqdM4EOtt8AcNpALRmsbEF26Gyd6t4/81bQPh+7WhI97lR/KkdWtKxNjjJ4CoJLgceyHuwbxXnFR23IWhzvQpBY12MBeYOw9oizvEzEGhEqpUns6LkH2sUNRRXbneNNvVgCEk6BK7j6Dxi95mcGJDEtOW+coE1SjhnfrwjIsdJL7cUEyC5DHFKuvxUi0iw/1I6b3AfZV5+A1tssEE2dhDv8uw6B3/a5EfMURFDqSfmGw1btdPPJ3+yjo1yYu2BtbYa4U++GtaAJfmNPrsB9lm4YgXuwCCRSpI2+TR9H2ntWM2j3HVdXqOpg3kfX82o9KFndo2g+7vGrOAyfL1jcybluq7AxPEV6D5yBky82MjoMeS0vSM6ytYu+0jheWPwDVs/3iPTELHPeDXAZOaw76ISBvNsXcxHvFsSiZBguBr+ucZOUnazVRAYIsmm/WNcIJu+6tfbyupqFCo5wkus6lKN2RNYIH1SRIi163cdBDhTBOdZoI2WcDr+SSW2fHtZutk7fW5IkJvSuy5xlke+YW/u3uzvriAIRmVDtk/fKISKEnMj2G47JdGn6EiHf+2+XfUSuDiliJb62pPXWBupinbb9HEW0tuyPHYGACH0/GA/egr6KMgI6YSh+BWS8vniMRTkmouKCzL5Csvc+2txC9LrfodrMF2R3jFZ1nig0mYzTQ9HvhqA2Uc+YG06iZtRaU7KqH6fMZYzPbjrxVOliyXR2G6","created_at":1784122846,"id":"112701bc1541c10b92f5a105e2e1f1813e591936e20075ec6a53c8bb8d235d81","kind":33301,"pubkey":"7177ccb8e8786c152e4960765f03fbceb7419d36a26e693a6399319760e7fd30","sig":"80eb4b49d70d73d35c1026b9c06d0fab280787950b5df412ebe4cdd05fcacadb4d20419c4e732749ed2698186a321069b4329ebd93fe21d8594d7392bf6445e0","tags":[["d",""],["vsk","8"]]}"""
        val event = Event.fromJson(json)
        val token = "c0277c415fe2ecc901a22b2f23dca5bf".hexToByteArray()
        assertEquals(InviteBundleStatus.Unreadable, ConcordInviteBundle.classify(listOf(event), token))
    }
}
