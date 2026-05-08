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
package com.vitorpamplona.quic.connection

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the in-memory [PathValidator] state machine — the
 * piece that implements RFC 9000 §5.1 + §8.2 + §9 client-initiated
 * path validation. Driven without a real socket so the assertions
 * are about the state-machine contract rather than the on-wire
 * encoding (see [PathValidationTest] for the integration shape).
 */
class PathValidatorTest {
    @Test
    fun newConnectionIdStoresInPool() {
        val v = PathValidator()
        val cid = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val tok = ByteArray(16) { 0xAA.toByte() }
        val r = v.recordPeerNewConnectionId(sequenceNumber = 1L, retirePriorTo = 0L, connectionId = cid, statelessResetToken = tok)
        assertEquals(PathValidator.RecordResult.Stored, r)
        assertEquals(1, v.unusedCount())
        assertEquals(listOf(1L), v.unusedSequences())
    }

    @Test
    fun duplicateOfferWithSameBytesIsIdempotent() {
        val v = PathValidator()
        val cid = ByteArray(8) { 0x42 }
        val tok = ByteArray(16) { 0x99.toByte() }
        v.recordPeerNewConnectionId(1L, 0L, cid, tok)
        val r = v.recordPeerNewConnectionId(1L, 0L, cid, tok)
        assertEquals(PathValidator.RecordResult.Duplicate, r)
        assertEquals(1, v.unusedCount())
    }

    @Test
    fun duplicateOfferWithDifferentBytesIsProtocolViolation() {
        val v = PathValidator()
        val seq = 2L
        v.recordPeerNewConnectionId(seq, 0L, ByteArray(8) { 0x11 }, ByteArray(16) { 0x22 })
        val r =
            v.recordPeerNewConnectionId(
                sequenceNumber = seq,
                retirePriorTo = 0L,
                connectionId = ByteArray(8) { 0x33 },
                statelessResetToken = ByteArray(16) { 0x44 },
            )
        assertEquals(PathValidator.RecordResult.DuplicateSequenceMismatch, r)
    }

    @Test
    fun retirePriorToForcesEarlierEntriesIntoRetireQueue() {
        val v = PathValidator()
        v.recordPeerNewConnectionId(1L, 0L, ByteArray(8) { 0x01 }, ByteArray(16) { 0x10 })
        v.recordPeerNewConnectionId(2L, 0L, ByteArray(8) { 0x02 }, ByteArray(16) { 0x20 })
        // New offer with retirePriorTo = 2 forces sequences 0 and 1 into retirement.
        val r = v.recordPeerNewConnectionId(3L, retirePriorTo = 2L, ByteArray(8) { 0x03 }, ByteArray(16) { 0x30 })
        assertEquals(PathValidator.RecordResult.Stored, r)
        assertEquals(2L, v.retirePriorToWatermark)
        // Sequence 1 was in the pool — retired. Sequence 0 was the active CID;
        // PathValidator queues it implicitly via watermark advancement, but
        // only sequences ≥ retirePriorToWatermark (i.e. ≥ 2) survive in
        // unusedCids.
        assertEquals(setOf(2L, 3L), v.unusedSequences().toSet())
        assertTrue(v.pendingRetireSequences.contains(1L), "seq 1 must be queued for retire")
    }

    @Test
    fun retirePriorToRegressionIsClampedNotRejected() {
        // RFC 9000 §19.15: a smaller `retire_prior_to` than what we
        // previously saw "MUST be treated as the largest one it has
        // seen" — i.e. clamp, don't error. Reordered NEW_CONNECTION_ID
        // arrivals are common on the wire and aren't peer protocol
        // violations.
        val v = PathValidator()
        v.recordPeerNewConnectionId(1L, 1L, ByteArray(8) { 0x01 }, ByteArray(16) { 0x10 })
        val r = v.recordPeerNewConnectionId(2L, 0L, ByteArray(8) { 0x02 }, ByteArray(16) { 0x20 })
        assertEquals(PathValidator.RecordResult.Stored, r)
        assertEquals(1L, v.retirePriorToWatermark, "watermark must NOT decrease on a regressed retire_prior_to")
    }

    @Test
    fun retirePriorToGreaterThanSequenceIsRejected() {
        val v = PathValidator()
        val r = v.recordPeerNewConnectionId(sequenceNumber = 1L, retirePriorTo = 5L, ByteArray(8), ByteArray(16))
        assertEquals(PathValidator.RecordResult.RetirePriorToExceedsSequence, r)
    }

    @Test
    fun poolFillsUpAndRejectsExcess() {
        val v = PathValidator(maxUnusedCids = 3)
        for (i in 1..3) {
            assertEquals(
                PathValidator.RecordResult.Stored,
                v.recordPeerNewConnectionId(i.toLong(), 0L, ByteArray(8) { i.toByte() }, ByteArray(16) { i.toByte() }),
            )
        }
        val r = v.recordPeerNewConnectionId(4L, 0L, ByteArray(8) { 0xAA.toByte() }, ByteArray(16))
        assertEquals(PathValidator.RecordResult.PoolFull, r)
        assertEquals(3, v.unusedCount())
    }

    @Test
    fun startValidationReturnsNoSpareCidWhenPoolEmpty() {
        val v = PathValidator()
        assertEquals(PathMigrationResult.NoSpareCid, v.tryStartValidation(nowMillis = 0L, currentPtoMillis = 100L))
        assertTrue(v.state is PathValidationState.Idle)
    }

    @Test
    fun startValidationPicksLowestSequenceAndQueuesChallenge() {
        val supplied = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x12, 0x34, 0x56, 0x78)
        val v = PathValidator(challengePayloadFactory = { supplied.copyOf() })
        v.recordPeerNewConnectionId(1L, 0L, ByteArray(8) { 0x01 }, ByteArray(16) { 0x10 })
        v.recordPeerNewConnectionId(2L, 0L, ByteArray(8) { 0x02 }, ByteArray(16) { 0x20 })
        assertEquals(PathMigrationResult.Started, v.tryStartValidation(nowMillis = 0L, currentPtoMillis = 200L))
        val state = v.state as PathValidationState.Validating
        assertEquals(1L, state.newCidSequence, "lowest sequence number must be picked first")
        assertContentEquals(supplied, state.challengeData)
        assertEquals(1, v.pendingChallenges.size)
        assertContentEquals(supplied, v.pendingChallenges.first())
    }

    @Test
    fun startValidationDoesNothingIfAlreadyInProgress() {
        val v = PathValidator(challengePayloadFactory = { ByteArray(8) { 0x07 } })
        v.recordPeerNewConnectionId(1L, 0L, ByteArray(8) { 0x01 }, ByteArray(16))
        v.recordPeerNewConnectionId(2L, 0L, ByteArray(8) { 0x02 }, ByteArray(16))
        assertEquals(PathMigrationResult.Started, v.tryStartValidation(0L, 100L))
        // Second trigger before resolution — must not pick a second CID.
        assertEquals(PathMigrationResult.AlreadyInProgress, v.tryStartValidation(0L, 100L))
        // The second CID is still in the pool; only one challenge is in flight.
        assertEquals(1, v.pendingChallenges.size)
    }

    @Test
    fun pathResponseWithMatchingPayloadValidatesAndQueuesRetire() {
        val payload = ByteArray(8) { 0x55 }
        val v = PathValidator(challengePayloadFactory = { payload.copyOf() })
        v.recordPeerNewConnectionId(1L, 0L, ByteArray(8) { 0xAA.toByte() }, ByteArray(16) { 0xBB.toByte() })
        v.tryStartValidation(0L, 100L)
        val outcome = v.applyPathResponse(payload)
        assertTrue(outcome is PathValidator.ValidationOutcome.Validated, "got $outcome")
        assertEquals(1L, outcome.newSequence)
        assertEquals(0L, outcome.retiredSequence)
        assertContentEquals(ByteArray(8) { 0xAA.toByte() }, outcome.connectionId)
        assertEquals(1L, v.activeCidSequence)
        assertTrue(v.state is PathValidationState.Succeeded)
        assertTrue(v.pendingRetireSequences.contains(0L), "old sequence must be queued for RETIRE_CONNECTION_ID")
        assertEquals(1L, v.successfulValidations)
    }

    @Test
    fun pathResponseWithMismatchedPayloadIsIgnored() {
        val v = PathValidator(challengePayloadFactory = { ByteArray(8) { 0x77 } })
        v.recordPeerNewConnectionId(1L, 0L, ByteArray(8) { 0x01 }, ByteArray(16))
        v.tryStartValidation(0L, 100L)
        val outcome = v.applyPathResponse(ByteArray(8) { 0x11 })
        assertEquals(PathValidator.ValidationOutcome.PayloadMismatch, outcome)
        assertTrue(v.state is PathValidationState.Validating, "still validating; mismatched response is dropped")
    }

    @Test
    fun pathResponseWithoutOutstandingChallengeIsIgnored() {
        val v = PathValidator()
        val outcome = v.applyPathResponse(ByteArray(8))
        assertEquals(PathValidator.ValidationOutcome.NotValidating, outcome)
    }

    @Test
    fun validationTimeoutAfter3PtoTransitionsToFailedAndRetiresFailedCid() {
        // Bug-2 regression: on 3 * PTO timeout the failed CID's
        // sequence MUST be queued for RETIRE_CONNECTION_ID.
        // Without this the peer keeps the routing entry forever
        // because we sent a packet with that DCID (the challenge)
        // but never told them we abandoned it.
        val v = PathValidator(challengePayloadFactory = { ByteArray(8) { 0x44 } })
        v.recordPeerNewConnectionId(1L, 0L, ByteArray(8) { 0x01 }, ByteArray(16))
        val pto = 1_000L
        v.tryStartValidation(nowMillis = 0L, currentPtoMillis = pto)

        assertNull(v.checkValidationTimeout(nowMillis = pto * 2L), "still within budget")
        val abandoned = v.checkValidationTimeout(nowMillis = pto * 4L)
        assertTrue(abandoned != null, "validation must time out at 3*PTO")
        assertEquals(1L, abandoned.newCidSequence)
        assertTrue(v.state is PathValidationState.Failed)
        assertEquals(1L, v.failedValidations)
        assertTrue(
            v.pendingRetireSequences.contains(1L),
            "abandoned CID sequence must be queued for RETIRE_CONNECTION_ID — pending=${v.pendingRetireSequences}",
        )
    }

    @Test
    fun acknowledgeTerminalReturnsToIdle() {
        val v = PathValidator(challengePayloadFactory = { ByteArray(8) { 0x33 } })
        v.recordPeerNewConnectionId(1L, 0L, ByteArray(8), ByteArray(16))
        v.tryStartValidation(0L, 100L)
        v.applyPathResponse(ByteArray(8) { 0x33 })
        assertTrue(v.state is PathValidationState.Succeeded)
        v.acknowledgeTerminal()
        assertTrue(v.state is PathValidationState.Idle)
    }

    @Test
    fun staleOfferBelowWatermarkQueuesRetireWithoutStoring() {
        val v = PathValidator()
        // Seed watermark to 5 via a normal offer with retirePriorTo = 5.
        v.recordPeerNewConnectionId(5L, 5L, ByteArray(8) { 0x05 }, ByteArray(16) { 0x50 })
        assertEquals(5L, v.retirePriorToWatermark)
        // Now a reordered offer arrives with seq=3 and retirePriorTo=3
        // (the value the peer sent before raising it to 5). Even
        // though seq=3 ≥ retirePriorTo=3 (so it isn't a frame error),
        // the watermark we already raised says seq 3 has been retired.
        // The validator clamps retirePriorTo to the watermark and
        // detects that seq is now below the watermark — so the entry
        // is queued for RETIRE_CONNECTION_ID rather than stored.
        val r = v.recordPeerNewConnectionId(3L, 3L, ByteArray(8) { 0x03 }, ByteArray(16) { 0x30 })
        assertEquals(PathValidator.RecordResult.AlreadyRetired, r)
        assertTrue(v.pendingRetireSequences.contains(3L), "stale offer must be retired immediately")
        assertEquals(1, v.unusedCount(), "stale entry must not be stored in pool")
    }
}
