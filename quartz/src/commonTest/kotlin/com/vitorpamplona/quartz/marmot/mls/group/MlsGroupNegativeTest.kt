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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Negative tests: every post-RFC-9420-§6 authenticity check that
 * [MlsGroup.processCommit] performs should reject the tampered input AND
 * leave the group in its pre-commit state (atomic rollback — no partial
 * epoch advance, no mutated tree, no dangling pending proposals).
 */
class MlsGroupNegativeTest {
    private data class TwoMemberFixture(
        val alice: MlsGroup,
        val bob: MlsGroup,
        val charliePkg: ByteArray,
    )

    /**
     * Produce a 2-member group (alice creator, bob joined via Welcome)
     * plus a spare KeyPackage for Charlie. Callers use Charlie's bundle to
     * drive Alice into producing a fresh Add commit that Bob will process.
     */
    private fun twoMemberGroup(): TwoMemberFixture {
        val alice = MlsGroup.create(identity = "alice".encodeToByteArray())

        val bobBundle = alice.createKeyPackage(identity = "bob".encodeToByteArray(), signingKey = ByteArray(32) { 1 })
        val addBob = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addBob.welcomeBytes!!, bobBundle)

        val charlieBundle =
            alice.createKeyPackage(identity = "charlie".encodeToByteArray(), signingKey = ByteArray(32) { 2 })

        return TwoMemberFixture(alice, bob, charlieBundle.keyPackage.toTlsBytes())
    }

    /**
     * Decode a framedCommitBytes (MlsMessage(PublicMessage(commit))) into
     * the fields [MlsGroup.processCommit] consumes. Tests re-use this to
     * mutate individual fields before re-invoking.
     */
    private data class CommitParts(
        val content: ByteArray,
        val senderLeafIndex: Int,
        val confirmationTag: ByteArray,
        val signature: ByteArray,
        val pubMsg: PublicMessage,
    )

    private fun parseCommit(framedBytes: ByteArray): CommitParts {
        val mlsMsg = MlsMessage.decodeTls(TlsReader(framedBytes))
        val pub = PublicMessage.decodeTls(TlsReader(mlsMsg.payload))
        return CommitParts(
            content = pub.content,
            senderLeafIndex = pub.sender.leafIndex,
            confirmationTag = pub.confirmationTag!!,
            signature = pub.signature,
            pubMsg = pub,
        )
    }

    /** Baseline: an honest commit applies and advances Bob's epoch. */
    @Test
    fun honestCommitIsAccepted() {
        val fx = twoMemberGroup()
        val bobEpochBefore = fx.bob.epoch

        val commit = fx.alice.addMember(fx.charliePkg)
        val parts = parseCommit(commit.framedCommitBytes)
        fx.bob.processCommit(
            commitBytes = parts.content,
            senderLeafIndex = parts.senderLeafIndex,
            confirmationTag = parts.confirmationTag,
            signature = parts.signature,
            wireFormat = WireFormat.PUBLIC_MESSAGE,
        )
        assertEquals(bobEpochBefore + 1, fx.bob.epoch)
    }

    /**
     * Tampered confirmation_tag — flipping a single bit must fail the
     * HMAC compare, throw, and leave Bob on the original epoch.
     */
    @Test
    fun tamperedConfirmationTagIsRejectedAndStateRolledBack() {
        val fx = twoMemberGroup()
        val epochBefore = fx.bob.epoch

        val commit = fx.alice.addMember(fx.charliePkg)
        val parts = parseCommit(commit.framedCommitBytes)
        val tamperedTag = parts.confirmationTag.copyOf().apply { this[0] = (this[0].toInt() xor 0x01).toByte() }

        assertFailsWith<IllegalArgumentException> {
            fx.bob.processCommit(
                commitBytes = parts.content,
                senderLeafIndex = parts.senderLeafIndex,
                confirmationTag = tamperedTag,
                signature = parts.signature,
                wireFormat = WireFormat.PUBLIC_MESSAGE,
            )
        }
        assertEquals(epochBefore, fx.bob.epoch)

        // And the honest commit still applies cleanly afterwards — proving
        // no mutable state leaked across the failed attempt.
        fx.bob.processCommit(
            commitBytes = parts.content,
            senderLeafIndex = parts.senderLeafIndex,
            confirmationTag = parts.confirmationTag,
            signature = parts.signature,
            wireFormat = WireFormat.PUBLIC_MESSAGE,
        )
        assertEquals(epochBefore + 1, fx.bob.epoch)
    }

    /**
     * Tampered FramedContentTBS signature — receiver reconstructs the
     * exact same TBS bytes the sender signed; any bit-flip in the
     * signature fails Ed25519 verification.
     */
    @Test
    fun tamperedSignatureIsRejectedAndStateRolledBack() {
        val fx = twoMemberGroup()
        val epochBefore = fx.bob.epoch

        val commit = fx.alice.addMember(fx.charliePkg)
        val parts = parseCommit(commit.framedCommitBytes)
        val tamperedSig = parts.signature.copyOf().apply { this[0] = (this[0].toInt() xor 0x80).toByte() }

        assertFailsWith<IllegalArgumentException> {
            fx.bob.processCommit(
                commitBytes = parts.content,
                senderLeafIndex = parts.senderLeafIndex,
                confirmationTag = parts.confirmationTag,
                signature = tamperedSig,
                wireFormat = WireFormat.PUBLIC_MESSAGE,
            )
        }
        assertEquals(epochBefore, fx.bob.epoch)
    }

    /**
     * Claiming the commit came from the wrong leaf index — the signature
     * was bound to the original sender leaf, so verifying against a
     * different leaf's pre-commit signatureKey fails.
     */
    @Test
    fun spoofedSenderLeafIndexIsRejected() {
        val fx = twoMemberGroup()
        val epochBefore = fx.bob.epoch

        val commit = fx.alice.addMember(fx.charliePkg)
        val parts = parseCommit(commit.framedCommitBytes)
        // Alice is leaf 0, Bob is leaf 1. Swap.
        val wrongLeaf = if (parts.senderLeafIndex == 0) 1 else 0

        assertFailsWith<IllegalArgumentException> {
            fx.bob.processCommit(
                commitBytes = parts.content,
                senderLeafIndex = wrongLeaf,
                confirmationTag = parts.confirmationTag,
                signature = parts.signature,
                wireFormat = WireFormat.PUBLIC_MESSAGE,
            )
        }
        assertEquals(epochBefore, fx.bob.epoch)
    }

    /**
     * Declaring the wrong wire_format at the receiver — the FramedContentTBS
     * hash diverges and signature verification fails, because the sender
     * mixed wire_format=PUBLIC_MESSAGE into their TBS bytes.
     */
    @Test
    fun wrongWireFormatIsRejected() {
        val fx = twoMemberGroup()
        val epochBefore = fx.bob.epoch

        val commit = fx.alice.addMember(fx.charliePkg)
        val parts = parseCommit(commit.framedCommitBytes)

        assertFailsWith<IllegalArgumentException> {
            fx.bob.processCommit(
                commitBytes = parts.content,
                senderLeafIndex = parts.senderLeafIndex,
                confirmationTag = parts.confirmationTag,
                signature = parts.signature,
                wireFormat = WireFormat.PRIVATE_MESSAGE,
            )
        }
        assertEquals(epochBefore, fx.bob.epoch)
    }

    /**
     * Empty confirmation_tag is rejected explicitly — RFC 9420 §6 requires
     * every commit to carry a confirmation_tag, and an omitted tag means
     * the receiver has no authentication of the post-commit epoch secrets.
     */
    @Test
    fun emptyConfirmationTagIsRejected() {
        val fx = twoMemberGroup()
        val epochBefore = fx.bob.epoch

        val commit = fx.alice.addMember(fx.charliePkg)
        val parts = parseCommit(commit.framedCommitBytes)

        assertFailsWith<IllegalArgumentException> {
            fx.bob.processCommit(
                commitBytes = parts.content,
                senderLeafIndex = parts.senderLeafIndex,
                confirmationTag = ByteArray(0),
                signature = parts.signature,
                wireFormat = WireFormat.PUBLIC_MESSAGE,
            )
        }
        assertEquals(epochBefore, fx.bob.epoch)
    }

    /**
     * Tampered membership_tag — PublicMessage commits from a member must
     * carry an HMAC(membership_key, TBM) that matches what the receiver
     * can reconstruct from the current epoch's membership_key. Without
     * this check, an outsider that learned the outer exporter secret
     * could forge arbitrary commit bodies.
     *
     * This is enforced at the MarmotInboundProcessor layer before
     * [MlsGroup.processCommit] is called; the group exposes
     * [MlsGroup.verifyPublicMessageCommitMembershipTag] so the processor
     * can short-circuit on tag mismatch.
     */
    @Test
    fun tamperedMembershipTagFailsPublicMessageCheck() {
        val fx = twoMemberGroup()

        val commit = fx.alice.addMember(fx.charliePkg)
        val parts = parseCommit(commit.framedCommitBytes)

        // Honest tag is valid.
        assertTrue(fx.bob.verifyPublicMessageCommitMembershipTag(parts.pubMsg))

        // Flip one bit in the wire tag — must be rejected.
        val original = parts.pubMsg.membershipTag!!
        val tampered = original.copyOf().apply { this[0] = (this[0].toInt() xor 0x01).toByte() }
        val badPub = parts.pubMsg.copy(membershipTag = tampered)
        assertFalse(fx.bob.verifyPublicMessageCommitMembershipTag(badPub))

        // A missing tag is also rejected.
        val missingPub = parts.pubMsg.copy(membershipTag = null)
        assertFalse(fx.bob.verifyPublicMessageCommitMembershipTag(missingPub))

        // And the honest commit still applies — nothing got mutated by the checks.
        val epochBefore = fx.bob.epoch
        fx.bob.processCommit(
            commitBytes = parts.content,
            senderLeafIndex = parts.senderLeafIndex,
            confirmationTag = parts.confirmationTag,
            signature = parts.signature,
            wireFormat = WireFormat.PUBLIC_MESSAGE,
        )
        assertEquals(epochBefore + 1, fx.bob.epoch)
        assertNotEquals(epochBefore, fx.bob.epoch)
    }
}
