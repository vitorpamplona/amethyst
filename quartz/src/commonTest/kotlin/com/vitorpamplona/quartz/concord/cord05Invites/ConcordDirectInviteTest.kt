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
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordDirectInviteTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val sender = NostrSignerInternal(KeyPair())
    private val recipient = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    private suspend fun community(): NewConcordCommunity = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))

    private fun inviteFor(
        community: NewConcordCommunity,
        expiresAt: Long? = null,
        relays: List<String> = listOf("wss://relay.example"),
        channels: List<InviteChannel> = emptyList(),
    ) = CommunityInvite(
        communityId = community.communityIdHex,
        owner = community.ownerPubKey,
        ownerSalt = community.ownerSalt.toHexKey(),
        communityRoot = community.communityRoot.toHexKey(),
        rootEpoch = community.rootEpoch,
        controlPk = community.controlPkHex,
        channels = channels,
        relays = relays,
        name = "Nostrichs",
        expiresAt = expiresAt,
    )

    /** Wraps an arbitrary [seal] to [to] exactly like [ConcordDirectInvite.build] does (ephemeral author, p + k tags). */
    private suspend fun wrapSeal(
        seal: Event,
        to: String,
    ): GiftWrapEvent {
        val eph = NostrSignerInternal(KeyPair())
        return eph.sign(
            createdAt = seal.createdAt,
            kind = GiftWrapEvent.KIND,
            tags = arrayOf(arrayOf("p", to), arrayOf("k", "3313")),
            content = eph.nip44Encrypt(seal.toJson(), to),
        )
    }

    /** A seal from [sealer] carrying a kind-[kind] rumor that CLAIMS [claimedAuthor]. */
    private suspend fun forgedSeal(
        sealer: NostrSigner,
        claimedAuthor: String,
        content: String,
        kind: Int = ConcordDirectInvite.KIND,
    ): SealEvent {
        val rumor = RumorAssembler.assembleRumor<Event>(claimedAuthor, 1_700_000_000L, kind, emptyArray(), content)
        return SealEvent.create(rumor, recipient.pubKey, sealer, createdAt = 1_700_000_000L)
    }

    private fun json(invite: CommunityInvite) = ConcordJson.instance.encodeToString(CommunityInvite.serializer(), invite)

    @Test
    fun directInviteRoundTripsWithTheVerifiedSender() =
        runTest {
            val c = community()
            val wrap = ConcordDirectInvite.build(sender, recipient.pubKey, inviteFor(c), createdAt = 1_700_000_000L)

            // Wrap is a giftwrap tagged for the recipient and indexable by k=3313, from an ephemeral author.
            assertEquals(GiftWrapEvent.KIND, wrap.kind)
            assertEquals(recipient.pubKey, wrap.tags.first { it[0] == "p" }[1])
            assertEquals("3313", wrap.tags.first { it[0] == "k" }[1])
            assertFalse(wrap.pubKey == sender.pubKey)

            val opened = ConcordDirectInvite.open(wrap, recipient)
            assertNotNull(opened)
            assertEquals(sender.pubKey, opened.sender)
            assertEquals(wrap.id, opened.wrapId)
            assertEquals(1_700_000_000L, opened.sentAt)
            assertEquals("Nostrichs", opened.invite.name)
            assertEquals(c.communityIdHex, opened.invite.communityId)
            assertEquals(c.controlPkHex, opened.invite.controlPk)

            // The legacy parse keeps working.
            assertEquals(c.communityIdHex, ConcordDirectInvite.parse(wrap, recipient)?.communityId)
        }

    @Test
    fun strangersCannotOpenIt() =
        runTest {
            val wrap = ConcordDirectInvite.build(sender, recipient.pubKey, inviteFor(community()), createdAt = 1L)
            assertNull(ConcordDirectInvite.open(wrap, stranger))
        }

    @Test
    fun aRumorClaimingSomeoneElseIsRefused() =
        runTest {
            // The attacker seals (and so is the verified sender) a rumor claiming the owner wrote it.
            val c = community()
            val spoofed = wrapSeal(forgedSeal(stranger, claimedAuthor = owner.pubKey, content = json(inviteFor(c))), recipient.pubKey)
            assertNull(ConcordDirectInvite.open(spoofed, recipient))

            // The very same rumor claiming its real sealer opens.
            val honest = wrapSeal(forgedSeal(stranger, claimedAuthor = stranger.pubKey, content = json(inviteFor(c))), recipient.pubKey)
            assertEquals(stranger.pubKey, ConcordDirectInvite.open(honest, recipient)?.sender)
        }

    @Test
    fun theRumorKindIsTheAuthorityNotTheKTag() =
        runTest {
            // A k=3313-tagged wrap whose rumor is a kind-14 DM is not an invite.
            val c = community()
            val dm = wrapSeal(forgedSeal(sender, claimedAuthor = sender.pubKey, content = json(inviteFor(c)), kind = 14), recipient.pubKey)
            assertNull(ConcordDirectInvite.open(dm, recipient))
        }

    @Test
    fun wrapCarriesNip40ExpirationMatchingExpiresAt() =
        runTest {
            val c = community()
            val expiresAtMs = 1_800_000_123_456L
            val wrap = ConcordDirectInvite.build(sender, recipient.pubKey, inviteFor(c, expiresAt = expiresAtMs), createdAt = 1_700_000_000L)
            assertEquals(1_800_000_123L, wrap.tags.expiration())

            assertFalse(ConcordDirectInvite.isWrapExpired(wrap, nowSecs = 1_800_000_122L))
            assertTrue(ConcordDirectInvite.isWrapExpired(wrap, nowSecs = 1_800_000_123L))

            // No expires_at, no expiration tag.
            val open = ConcordDirectInvite.build(sender, recipient.pubKey, inviteFor(c), createdAt = 1_700_000_000L)
            assertNull(open.tags.expiration())
            assertFalse(ConcordDirectInvite.isWrapExpired(open, nowSecs = Long.MAX_VALUE))

            // An expired bundle still opens (a parked invite renders), but reports itself expired.
            val opened = ConcordDirectInvite.open(wrap, recipient)
            assertNotNull(opened)
            assertTrue(opened.isExpired(nowMs = expiresAtMs + 1))
            assertFalse(opened.isExpired(nowMs = expiresAtMs - 1))
        }

    @Test
    fun sealAndWrapAreBackdatedWithinTwoDaysButTheRumorKeepsTheRealTime() =
        runTest {
            val c = community()
            val now = 1_700_000_000L
            val outer = mutableListOf<Long>()
            repeat(6) {
                val wrap = ConcordDirectInvite.build(sender, recipient.pubKey, inviteFor(c), createdAt = now)
                val seal = wrap.unwrapOrNull(recipient)
                assertIs<SealEvent>(seal)
                for (t in listOf(wrap.createdAt, seal.createdAt)) {
                    assertTrue(t <= now, "outer timestamp $t is in the future")
                    assertTrue(t > now - ConcordDirectInvite.MAX_BACKDATE_SECS, "outer timestamp $t is backdated past two days")
                    outer += t
                }
                assertEquals(now, ConcordDirectInvite.open(wrap, recipient)?.sentAt)
            }
            // Twelve independent draws over a two-day range are not all "now".
            assertTrue(outer.any { it < now })
        }

    @Test
    fun theSection1BoundsApply() =
        runTest {
            val c = community()
            val sixRelays = (1..6).map { "wss://r$it.example" }
            val bounded = ConcordDirectInvite.open(ConcordDirectInvite.build(sender, recipient.pubKey, inviteFor(c, relays = sixRelays), createdAt = 1L), recipient)
            assertEquals(sixRelays.take(ConcordInviteBundle.MAX_COMMUNITY_RELAYS), bounded?.invite?.relays)

            val tooMany = (0..ConcordInviteBundle.MAX_BUNDLE_CHANNELS).map { InviteChannel(id = it.toString(16).padStart(64, '0'), key = "cd".repeat(32), epoch = 0) }
            assertNull(ConcordDirectInvite.open(ConcordDirectInvite.build(sender, recipient.pubKey, inviteFor(c, channels = tooMany), createdAt = 1L), recipient))
        }

    @Test
    fun aBundleWhoseOwnerProofFailsIsRefused() =
        runTest {
            // A real community's id with someone else's owner: the id does not self-certify it.
            val c = community()
            val forged = inviteFor(c).copy(owner = stranger.pubKey)
            assertNull(ConcordDirectInvite.open(ConcordDirectInvite.build(sender, recipient.pubKey, forged, createdAt = 1L), recipient))
        }

    @Test
    fun inboxSinceRewindsByTheBackdateWindow() {
        assertNull(ConcordDirectInvite.inboxSince(null))
        assertNull(ConcordDirectInvite.inboxSince(100L))
        assertEquals(1_700_000_000L - ConcordDirectInvite.MAX_BACKDATE_SECS, ConcordDirectInvite.inboxSince(1_700_000_000L))
    }
}
