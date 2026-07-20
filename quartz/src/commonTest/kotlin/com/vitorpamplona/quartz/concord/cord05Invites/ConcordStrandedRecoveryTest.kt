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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stranded recovery: a member left out of a Refounding's recipient set gets no rekey at
 * all, so the only way they learn about the new epoch is by re-resolving the invite link
 * they joined through and finding a higher-epoch bundle there.
 */
class ConcordStrandedRecoveryTest {
    private val communityId = "11".repeat(32)
    private val token = ByteArray(16) { it.toByte() }
    private val linkSigner = KeyPair().pubKey.toHexKey()

    private val inviteRef = ConcordInviteLink.buildUrl("https://amethyst.social", linkSigner, token).substringAfter("/invite/")

    private fun entry(
        epoch: Long,
        ref: String? = inviteRef,
        heldRoots: List<HeldRoot> = emptyList(),
    ) = ConcordCommunityListEntry(
        id = communityId,
        owner = "0f".repeat(32),
        ownerSalt = "aa".repeat(32),
        root = "bb".repeat(32),
        rootEpoch = epoch,
        heldRoots = heldRoots,
        relays = listOf("wss://relay.example"),
        name = "Gamers",
        addedAt = 1_700_000_000_000L,
        inviteRef = ref,
    )

    private fun bundle(
        epoch: Long,
        root: String = "cc".repeat(32),
        id: String = communityId,
    ) = CommunityInvite(
        communityId = id,
        owner = "0f".repeat(32),
        ownerSalt = "aa".repeat(32),
        communityRoot = root,
        rootEpoch = epoch,
        relays = listOf("wss://relay.example"),
        name = "Gamers",
    )

    // ---- merge forward --------------------------------------------------------

    @Test
    fun higherEpochBundleMergesForwardKeepingAnchorAndHistory() {
        val prior = HeldRoot(0L, "aa".repeat(32))
        val stranded = entry(epoch = 1, heldRoots = listOf(prior))

        val merged = ConcordStrandedRecovery.mergeForward(stranded, bundle(epoch = 5))
        assertNotNull(merged, "a higher-epoch bundle at our own invite link means we were left behind")

        // adopted the new epoch's access root
        assertEquals(5L, merged.rootEpoch)
        assertEquals("cc".repeat(32), merged.root)

        // the anchor survives, or the *next* exclusion would be unrecoverable
        assertEquals(inviteRef, merged.inviteRef)

        // prior-epoch history we legitimately hold is not lost, and the root we just left
        // is added so epoch-1 channels stay derivable
        assertEquals(setOf(0L, 1L), merged.heldRoots.map { it.epoch }.toSet())
        assertTrue(merged.heldRoots.any { it.epoch == 0L && it.key == prior.key })
        assertTrue(merged.heldRoots.any { it.epoch == 1L && it.key == "bb".repeat(32) })

        // identity is untouched and we record where we were dropped
        assertEquals(communityId, merged.id)
        assertEquals(stranded.addedAt, merged.addedAt)
        assertEquals(1L, merged.excludedAtEpoch)
    }

    @Test
    fun sameEpochBundleIsANoOp() {
        assertNull(ConcordStrandedRecovery.mergeForward(entry(epoch = 5), bundle(epoch = 5)))
        assertFalse(ConcordStrandedRecovery.isStranded(entry(epoch = 5), bundle(epoch = 5)))
    }

    @Test
    fun lowerEpochBundleIsANoOp() {
        // Epoch-monotonic: a stale bundle must never walk the membership backwards.
        assertNull(ConcordStrandedRecovery.mergeForward(entry(epoch = 7), bundle(epoch = 3)))
    }

    @Test
    fun entryWithoutInviteRefIsInert() {
        // Direct invites and legacy entries have no anchor — expected, not an error.
        val noAnchor = entry(epoch = 1, ref = null)
        assertFalse(ConcordStrandedRecovery.isStranded(noAnchor, bundle(epoch = 9)))
        assertNull(ConcordStrandedRecovery.mergeForward(noAnchor, bundle(epoch = 9)))
    }

    @Test
    fun bundleForAnotherCommunityIsIgnored() {
        assertNull(ConcordStrandedRecovery.mergeForward(entry(epoch = 1), bundle(epoch = 9, id = "99".repeat(32))))
    }

    // ---- the bare `<naddr>#<fragment>` anchor form ----------------------------

    @Test
    fun bareFormStripsTheHostSoAnyFrontEndsLinkMatches() {
        val amethyst = ConcordInviteLink.buildUrl("https://amethyst.social", linkSigner, token)
        val armada = ConcordInviteLink.buildUrl("https://someother.example", linkSigner, token)

        val bare = ConcordInviteLink.bareForm(amethyst)
        assertNotNull(bare)
        assertFalse(bare.contains("amethyst.social"))
        assertFalse(bare.contains("/invite/"))
        assertTrue(bare.startsWith("naddr"))

        // domain-agnostic: the same invite shared through a different front end reduces
        // to the identical anchor, which is the whole point of storing the bare form
        assertEquals(bare, ConcordInviteLink.bareForm(armada))
    }

    @Test
    fun bareFormReParsesBackToTheSamePointerAndToken() {
        val bare = ConcordInviteLink.bareForm(ConcordInviteLink.buildUrl("https://amethyst.social", linkSigner, token))!!
        val parsed = ConcordInviteLink.parseUrl(bare)
        assertNotNull(parsed, "the stored bare anchor must be re-resolvable, or recovery can never fire")
        assertEquals(linkSigner, parsed.linkSignerPubKey)
        assertEquals(token.toList(), parsed.fragment.token.toList())
    }

    @Test
    fun bareFormOfGarbageIsNull() {
        assertNull(ConcordInviteLink.bareForm("https://amethyst.social/invite/nope"))
        assertNull(ConcordInviteLink.bareForm("not a link"))
    }

    // ---- wire round-trip (Armada interop) ------------------------------------

    @Test
    fun inviteRefAndExcludedAtEpochRoundTripOnTheWire() {
        val original =
            ConcordCommunityListEntry(
                id = communityId,
                owner = "0f".repeat(32),
                ownerSalt = "aa".repeat(32),
                root = "cc".repeat(32),
                rootEpoch = 5,
                heldRoots = listOf(HeldRoot(0L, "aa".repeat(32)), HeldRoot(1L, "bb".repeat(32))),
                relays = listOf("wss://relay.example"),
                name = "Gamers",
                addedAt = 1_700_000_000_000L,
                inviteRef = inviteRef,
                excludedAtEpoch = 1L,
            )

        val json = ConcordCommunityList.encode(listOf(original))

        // Armada's field names, verbatim — a mismatch silently breaks interop both ways.
        assertTrue(json.contains("\"invite_ref\""), "must serialize as invite_ref: $json")
        assertTrue(json.contains("\"excluded_at_epoch\""), "must serialize as excluded_at_epoch: $json")

        val back = ConcordCommunityList.decode(json).single()
        assertEquals(inviteRef, back.inviteRef)
        assertEquals(1L, back.excludedAtEpoch)
        assertEquals(5L, back.rootEpoch)
        assertEquals("cc".repeat(32), back.root)
        assertEquals(listOf(0L, 1L), back.heldRoots.map { it.epoch })
    }

    @Test
    fun decodesArmadaEntryCarryingInviteRef() {
        // Shape as Armada's communityList.ts writes it, with invite_ref / excluded_at_epoch
        // at the ENTRY level (not inside the JoinMaterial).
        val json =
            """
            {
              "entries": [
                {
                  "community_id": "$communityId",
                  "current": {
                    "community_id": "$communityId",
                    "owner": "${"0f".repeat(32)}",
                    "owner_salt": "${"aa".repeat(32)}",
                    "community_root": "${"cc".repeat(32)}",
                    "root_epoch": 4
                  },
                  "added_at": 1700000000000,
                  "invite_ref": "$inviteRef",
                  "excluded_at_epoch": 2
                }
              ],
              "tombstones": []
            }
            """.trimIndent()

        val entry = ConcordCommunityList.decode(json).single()
        assertEquals(inviteRef, entry.inviteRef)
        assertEquals(2L, entry.excludedAtEpoch)
        assertEquals(4L, entry.rootEpoch)
    }

    @Test
    fun anEntryWithoutInviteRefStillDecodes() {
        // Legacy lists (and Armada entries joined by direct invite) carry no invite_ref.
        val json = ConcordCommunityList.encode(listOf(entry(epoch = 1, ref = null)))
        val back = ConcordCommunityList.decode(json).single()
        assertNull(back.inviteRef)
        assertNull(back.excludedAtEpoch)
    }

    @Test
    fun mergeKeepsTheAnchorWhenTheHigherEpochCopyLacksIt() {
        // Two devices: one holds the anchor at the old epoch, the other rotated forward
        // without it. Dropping the anchor here would disarm recovery permanently.
        val anchored = entry(epoch = 1, ref = inviteRef)
        val rotatedNoAnchor = entry(epoch = 4, ref = null)

        val merged = ConcordCommunityList.merge(listOf(anchored), listOf(rotatedNoAnchor)).single()
        assertEquals(4L, merged.rootEpoch)
        assertEquals(inviteRef, merged.inviteRef)

        // and the same regardless of argument order
        val other = ConcordCommunityList.merge(listOf(rotatedNoAnchor), listOf(anchored)).single()
        assertEquals(4L, other.rootEpoch)
        assertEquals(inviteRef, other.inviteRef)
    }
}
