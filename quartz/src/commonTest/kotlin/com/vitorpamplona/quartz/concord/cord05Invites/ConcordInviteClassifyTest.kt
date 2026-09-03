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
import com.vitorpamplona.quartz.nip01Core.crypto.verify
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
    fun remintAtTheSameCoordinateWinsOverAStaleCopy() =
        runTest {
            // The Refounding path (CORD-05 §1): every live link is re-minted at its own coordinate
            // carrying the new epoch's root. A relay that still serves the pre-Refounding bundle
            // must not be able to hand a joiner the epoch the community just left.
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val before = inviteFor(community).copy(communityRoot = "aa".repeat(32), rootEpoch = 1L)
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", before, createdAt = 1000L)

            val after = before.copy(communityRoot = "bb".repeat(32), rootEpoch = 2L)
            val remint = ConcordInviteBundle.build(minted.linkSignerPrivKey, minted.token, after, createdAt = 2000L)

            // Both fetch orders must resolve to the re-mint — `fetchAll` gives no ordering guarantee.
            listOf(listOf(minted.bundleEvent, remint), listOf(remint, minted.bundleEvent)).forEach { wraps ->
                val status = ConcordInviteBundle.classify(wraps, minted.token)
                assertTrue(status is InviteBundleStatus.Live)
                assertEquals("bb".repeat(32), status.invite.communityRoot)
                assertEquals(2L, status.invite.rootEpoch)
            }
        }

    @Test
    fun unknownSubKindIsUnreadable() =
        runTest {
            // A mis-posted registry (vsk=8) at the bundle coordinate — the exact shape of the
            // relayop.xyz link that hung — is present but not a vsk=6 bundle we can open.
            val registry = coordinateEvent("aa".repeat(32), ControlEntityKind.INVITE_REGISTRY.wire, createdAt = 1L, content = "unopenable")
            assertEquals(InviteBundleStatus.Unreadable, ConcordInviteBundle.classify(listOf(registry), ByteArray(16)))
        }

    /**
     * Regression: `expires_at` used to be decorative — [ConcordInviteBundle.isExpired] had no
     * production caller, so an expired link redeemed forever. Enforcement lives in [classify],
     * which every redeeming path (Account.joinConcordViaInvite) funnels through.
     */
    @Test
    fun expiredBundleDoesNotResolveLive() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val expiresAtMs = 2_000_000L
            val invite =
                CommunityInvite(
                    communityId = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    communityRoot = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    relays = listOf("wss://relay.example"),
                    name = "Nostrichs",
                    expiresAt = expiresAtMs,
                )
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", invite, createdAt = 1L, relays = listOf("wss://relay.example"))
            val wraps = listOf(minted.bundleEvent)

            // Before the expiry the very same bundle still opens…
            val live = ConcordInviteBundle.classify(wraps, minted.token, nowMs = expiresAtMs - 1)
            assertTrue(live is InviteBundleStatus.Live)

            // …and after it, the join path must refuse it (not Live) while the preview data survives.
            val expired = ConcordInviteBundle.classify(wraps, minted.token, nowMs = expiresAtMs + 1)
            assertTrue(expired is InviteBundleStatus.Expired)
            assertEquals(community.communityIdHex, expired.invite.communityId)
        }

    /** No `expires_at` means "never expires" — it must not be read as "expired at epoch 0". */
    @Test
    fun bundleWithoutExpiryNeverExpires() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", inviteFor(community), createdAt = 1L, relays = listOf("wss://relay.example"))
            assertTrue(ConcordInviteBundle.classify(listOf(minted.bundleEvent), minted.token, nowMs = Long.MAX_VALUE) is InviteBundleStatus.Live)
        }

    @Test
    fun emptyFetchIsAbsent() {
        assertEquals(InviteBundleStatus.Absent, ConcordInviteBundle.classify(emptyList(), ByteArray(16)))
    }

    @Test
    fun buildRevocationEmitsTheWireShapeArmadaEmits() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", inviteFor(community), createdAt = 1L, relays = listOf("wss://relay.example"))

            val grave = ConcordInviteBundle.buildRevocation(minted.linkSignerPrivKey, createdAt = 2L)

            // The interop contract, byte for byte: kind 33301 at the SAME addressable coordinate
            // (same author, same empty d tag), empty content, vsk=9. Anything else here and a
            // non-Amethyst client keeps serving a link its creator believes is dead.
            assertEquals(ConcordInviteBundleEvent.KIND, grave.kind)
            assertEquals(minted.linkSignerPubKey, grave.pubKey, "a tombstone at a different author retires nothing")
            assertEquals("", grave.content, "the grave carries no keys — nothing to encrypt")
            assertEquals(listOf(listOf("d", ""), listOf("vsk", "9")), grave.tags.map { it.toList() })
            assertTrue(grave.verify(), "must be signed by the link signer the creator kept")
        }

    @Test
    fun aBuiltRevocationRetiresItsOwnLink() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val minted = ConcordInviteBundle.mintLink("https://vector.chat", inviteFor(community), createdAt = 1L, relays = listOf("wss://relay.example"))

            // End to end: what the creator publishes is what every redeemer then resolves.
            val grave = ConcordInviteBundle.buildRevocation(minted.linkSignerPrivKey, createdAt = 2L)
            assertEquals(InviteBundleStatus.Revoked, ConcordInviteBundle.classify(listOf(minted.bundleEvent, grave), minted.token))

            // And a re-mint that lands AFTER the grave un-revokes the link, which is exactly why the
            // refresh path must skip a coordinate it did not resolve Live first.
            val remint = ConcordInviteBundle.build(minted.linkSignerPrivKey, minted.token, inviteFor(community), createdAt = 3L)
            assertTrue(ConcordInviteBundle.classify(listOf(minted.bundleEvent, grave, remint), minted.token) is InviteBundleStatus.Live)
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
