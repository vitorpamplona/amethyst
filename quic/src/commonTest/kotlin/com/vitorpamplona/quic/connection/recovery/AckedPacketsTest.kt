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
package com.vitorpamplona.quic.connection.recovery

import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.AckRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step 3 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
 * `drainAckedSentPackets` removes SentPacket entries for every packet
 * number covered by an inbound `AckFrame`'s ranges (RFC 9000
 * §19.3.1). Equivalent in scope to the `remove_acked` test in
 * neqo's `recovery/mod.rs:1262`, plus a few range-walk edge cases.
 */
class AckedPacketsTest {
    private fun sentPacket(pn: Long): SentPacket =
        SentPacket(
            packetNumber = pn,
            sentAtMillis = 0L,
            ackEliciting = true,
            sizeBytes = 64,
            tokens = listOf(RecoveryToken.MaxStreamsUni(maxStreams = pn * 10)),
        )

    @Test
    fun simpleRange_drainsAllAckedPns() {
        val sent = mutableMapOf<Long, SentPacket>()
        for (pn in 0L..9L) sent[pn] = sentPacket(pn)

        // ACK [3..7]: largest=7, firstRange=4 → covers 3..7.
        val ack =
            AckFrame(
                largestAcknowledged = 7L,
                ackDelay = 0L,
                firstAckRange = 4L,
            )

        val drained = drainAckedSentPackets(sent, ack)
        assertEquals(5, drained.size, "5 PNs in [3..7]")
        assertEquals(setOf(3L, 4L, 5L, 6L, 7L), drained.map { it.packetNumber }.toSet())
        // Map keeps everything outside the range.
        assertEquals(setOf(0L, 1L, 2L, 8L, 9L), sent.keys)
    }

    @Test
    fun multipleRanges_drainsAllAckedPns() {
        val sent = mutableMapOf<Long, SentPacket>()
        for (pn in 0L..20L) sent[pn] = sentPacket(pn)

        // ACK ranges: [18..20] then [10..12] then [0..3].
        // Encoding (descending):
        //   firstRange covers [18..20] → largest=20, firstRange=2
        //   gap before next: previousSmallest=18, nextLargest=12 ⇒ gap=18-12-2=4
        //   nextLength: 12..10 ⇒ length=2
        //   gap before next: previousSmallest=10, nextLargest=3 ⇒ gap=10-3-2=5
        //   nextLength: 3..0 ⇒ length=3
        val ack =
            AckFrame(
                largestAcknowledged = 20L,
                ackDelay = 0L,
                firstAckRange = 2L,
                additionalRanges =
                    listOf(
                        AckRange(gap = 4L, ackRangeLength = 2L),
                        AckRange(gap = 5L, ackRangeLength = 3L),
                    ),
            )

        val drained = drainAckedSentPackets(sent, ack)
        val drainedPns = drained.map { it.packetNumber }.toSet()
        assertEquals(setOf(18L, 19L, 20L, 10L, 11L, 12L, 0L, 1L, 2L, 3L), drainedPns)
        // Map keeps everything outside the ranges.
        assertEquals(setOf(4L, 5L, 6L, 7L, 8L, 9L, 13L, 14L, 15L, 16L, 17L), sent.keys)
    }

    @Test
    fun ackForUnsentPn_isNoOp() {
        val sent = mutableMapOf<Long, SentPacket>()
        sent[5L] = sentPacket(5L)

        // ACK PN 100 (never sent). drainAckedSentPackets should walk the
        // range, find no matching key, and leave the map untouched.
        // RFC 9000 §13.1 says ACK for unsent PN is a connection error —
        // detecting that is the connection-state machine's job; this
        // helper just drains, doesn't validate.
        val ack =
            AckFrame(
                largestAcknowledged = 100L,
                ackDelay = 0L,
                firstAckRange = 0L,
            )

        val drained = drainAckedSentPackets(sent, ack)
        assertEquals(0, drained.size)
        assertEquals(1, sent.size)
        assertNotNull(sent[5L])
    }

    @Test
    fun emptyMap_returnsEmptyDrain() {
        val sent = mutableMapOf<Long, SentPacket>()
        val ack =
            AckFrame(
                largestAcknowledged = 5L,
                ackDelay = 0L,
                firstAckRange = 5L,
            )
        val drained = drainAckedSentPackets(sent, ack)
        assertTrue(drained.isEmpty())
    }

    @Test
    fun singlePacketAck_drainsOne() {
        val sent = mutableMapOf<Long, SentPacket>()
        sent[42L] = sentPacket(42L)

        val ack =
            AckFrame(
                largestAcknowledged = 42L,
                ackDelay = 0L,
                firstAckRange = 0L,
            )

        val drained = drainAckedSentPackets(sent, ack)
        assertEquals(1, drained.size)
        assertEquals(42L, drained.single().packetNumber)
        assertNull(sent[42L])
    }

    @Test
    fun ackBoundary_pn0Inclusive() {
        // First range [0..2] — must include PN 0 and not under-flow.
        val sent = mutableMapOf<Long, SentPacket>()
        for (pn in 0L..2L) sent[pn] = sentPacket(pn)

        val ack =
            AckFrame(
                largestAcknowledged = 2L,
                ackDelay = 0L,
                firstAckRange = 2L,
            )

        val drained = drainAckedSentPackets(sent, ack)
        assertEquals(setOf(0L, 1L, 2L), drained.map { it.packetNumber }.toSet())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun forEachAckedPacketNumber_iteratesDescending() {
        val ack =
            AckFrame(
                largestAcknowledged = 5L,
                ackDelay = 0L,
                firstAckRange = 2L,
            )
        val pns = mutableListOf<Long>()
        forEachAckedPacketNumber(ack) { pns += it }
        // First range is [3..5]; iteration order is descending.
        assertEquals(listOf(5L, 4L, 3L), pns)
    }

    @Test
    fun forEachAckedPacketNumber_acrossMultipleRanges_descending() {
        // ACK [18..20] then [10..12] then [0..3] (same as multipleRanges_).
        val ack =
            AckFrame(
                largestAcknowledged = 20L,
                ackDelay = 0L,
                firstAckRange = 2L,
                additionalRanges =
                    listOf(
                        AckRange(gap = 4L, ackRangeLength = 2L),
                        AckRange(gap = 5L, ackRangeLength = 3L),
                    ),
            )
        val pns = mutableListOf<Long>()
        forEachAckedPacketNumber(ack) { pns += it }
        assertEquals(
            listOf(20L, 19L, 18L, 12L, 11L, 10L, 3L, 2L, 1L, 0L),
            pns,
        )
    }

    @Test
    fun returnedDrain_preservesTokens() {
        val sent = mutableMapOf<Long, SentPacket>()
        sent[7L] =
            SentPacket(
                packetNumber = 7L,
                sentAtMillis = 1_000L,
                ackEliciting = true,
                sizeBytes = 100,
                tokens =
                    listOf(
                        RecoveryToken.MaxStreamsUni(maxStreams = 200L),
                        RecoveryToken.MaxData(maxData = 1_000_000L),
                    ),
            )

        val ack =
            AckFrame(
                largestAcknowledged = 7L,
                ackDelay = 0L,
                firstAckRange = 0L,
            )
        val drained = drainAckedSentPackets(sent, ack)

        assertEquals(1, drained.size)
        val sp = drained.single()
        assertEquals(7L, sp.packetNumber)
        assertEquals(2, sp.tokens.size)
        assertEquals(RecoveryToken.MaxStreamsUni(200L), sp.tokens[0])
        assertEquals(RecoveryToken.MaxData(1_000_000L), sp.tokens[1])
    }
}
