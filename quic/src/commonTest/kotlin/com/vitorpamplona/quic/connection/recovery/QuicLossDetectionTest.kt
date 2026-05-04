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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step 5 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
 * RFC 9002 §5–§6 RTT + loss detection. Mirrors the subset of
 * neqo's `recovery/mod.rs` tests that don't depend on PTO (step 7)
 * or CRYPTO/handshake-space behavior (out of scope).
 */
class QuicLossDetectionTest {
    private fun sentPacket(
        pn: Long,
        sentAt: Long,
        ackEliciting: Boolean = true,
    ): SentPacket =
        SentPacket(
            packetNumber = pn,
            sentAtMillis = sentAt,
            ackEliciting = ackEliciting,
            sizeBytes = 64,
            tokens = listOf(RecoveryToken.MaxStreamsUni(maxStreams = pn * 100L)),
        )

    @Test
    fun firstRttSample_setsAllRttFieldsAtomically() {
        val ld = QuicLossDetection()
        ld.onRttSample(largestAckedSentTimeMs = 1_000L, ackDelayMs = 0L, nowMs = 1_050L)
        assertTrue(ld.hasFirstRttSample)
        assertEquals(50L, ld.smoothedRttMs, "first sample becomes smoothed_rtt")
        assertEquals(50L, ld.minRttMs)
        assertEquals(50L, ld.latestRttMs)
        assertEquals(25L, ld.rttVarMs, "rttvar = sample/2 on first sample")
    }

    @Test
    fun secondRttSample_movesSmoothedRttTowardSample() {
        val ld = QuicLossDetection()
        ld.onRttSample(largestAckedSentTimeMs = 0L, ackDelayMs = 0L, nowMs = 100L)
        // First sample: smoothed=100, rttvar=50, min=100.
        // Second sample at sample=120 (raw): adjusted=120 (no ack-delay).
        // smoothed' = (7*100 + 120)/8 = 820/8 = 102 (integer)
        // rttvar' = (3*50 + |100-120|)/4 = (150 + 20)/4 = 42
        ld.onRttSample(largestAckedSentTimeMs = 100L, ackDelayMs = 0L, nowMs = 220L)
        assertEquals(102L, ld.smoothedRttMs)
        assertEquals(42L, ld.rttVarMs)
        assertEquals(100L, ld.minRttMs, "minRtt only decreases")
    }

    @Test
    fun ackDelay_clampedAgainstMinRtt() {
        val ld = QuicLossDetection()
        // First sample establishes minRtt=50.
        ld.onRttSample(largestAckedSentTimeMs = 0L, ackDelayMs = 0L, nowMs = 50L)
        // Second sample raw=100, peer reports ackDelay=80.
        // adjusted would be 100 - 80 = 20 < minRtt(50), so clamp:
        // adjusted := raw = 100.
        ld.onRttSample(largestAckedSentTimeMs = 100L, ackDelayMs = 80L, nowMs = 200L)
        assertEquals(100L, ld.latestRttMs)
        // smoothed' = (7*50 + 100)/8 = 56
        assertEquals(56L, ld.smoothedRttMs)
    }

    @Test
    fun negativeRttSample_isIgnored() {
        // Clock skew or packet number reuse — sample is negative; ignore.
        val ld = QuicLossDetection()
        ld.onRttSample(largestAckedSentTimeMs = 100L, ackDelayMs = 0L, nowMs = 50L)
        assertEquals(false, ld.hasFirstRttSample)
        assertEquals(QuicLossDetection.INITIAL_RTT_MS, ld.smoothedRttMs)
    }

    @Test
    fun lossDelay_floor() {
        val ld = QuicLossDetection()
        // Initial: smoothed=333, latest=333. Loss delay = 333*9/8 = 374.
        assertEquals(374L, ld.lossDelayMs())
    }

    @Test
    fun packetThresholdLost_removesPacketsBelowThreshold() {
        val ld = QuicLossDetection()
        val sent = mutableMapOf<Long, SentPacket>()
        for (pn in 0L..10L) sent[pn] = sentPacket(pn, sentAt = 0L)
        // largestAckedPn=10. Packet threshold: lost iff pn < 10 - 3 = 7.
        // Pns 0..6 ⇒ 7 packets lost. Pns 7..10 not lost (pn >= 7).
        // (Pn 10 is the largest-acked itself — not in flight; it was just removed by drain.
        //  We model "after-drain" by removing 10 from sent before calling detectAndRemoveLost.)
        sent.remove(10L)
        val lost = ld.detectAndRemoveLost(sent, largestAckedPn = 10L, nowMs = 1L)
        // sentAt=0, nowMs=1, lossDelayMs=374 → time threshold cutoff at -373: nothing lost by time threshold
        // (sentAt 0 is NOT <= -373). But packet threshold removes pns 0..6.
        assertEquals(setOf(0L, 1L, 2L, 3L, 4L, 5L, 6L), lost.map { it.packetNumber }.toSet())
        assertEquals(setOf(7L, 8L, 9L), sent.keys, "pns 7..9 remain in flight; pn 10 was drained earlier")
    }

    @Test
    fun timeThresholdLost_removesPacketsSentTooLongAgo() {
        val ld = QuicLossDetection()
        // Force an RTT sample so loss delay is small (10ms*9/8 = 11ms with floor 1).
        ld.onRttSample(largestAckedSentTimeMs = 0L, ackDelayMs = 0L, nowMs = 10L)
        assertEquals(11L, ld.lossDelayMs(), "10*9/8 = 11.25 → 11")

        val sent = mutableMapOf<Long, SentPacket>()
        sent[0L] = sentPacket(0L, sentAt = 100L) // old: 100 + 11 = 111, now=200 ⇒ lost (sentAt<=189)
        sent[1L] = sentPacket(1L, sentAt = 195L) // recent: 195 + 11 = 206, now=200 ⇒ not lost
        // pn 2 is the largest-acked, drained earlier.

        val lost = ld.detectAndRemoveLost(sent, largestAckedPn = 2L, nowMs = 200L)
        assertEquals(listOf(0L), lost.map { it.packetNumber })
        assertEquals(setOf(1L), sent.keys)
    }

    @Test
    fun packetEqualToLargestAcked_notLost() {
        // Edge case: pn == largestAckedPn shouldn't be in the in-flight
        // map at this point (drain already removed it), but defensively
        // detectAndRemoveLost should not declare it lost either.
        val ld = QuicLossDetection()
        val sent = mutableMapOf<Long, SentPacket>()
        sent[5L] = sentPacket(5L, sentAt = 0L)
        val lost = ld.detectAndRemoveLost(sent, largestAckedPn = 5L, nowMs = 1L)
        assertTrue(lost.isEmpty())
        assertNotNull(sent[5L])
    }

    @Test
    fun emptyMap_returnsEmpty() {
        val ld = QuicLossDetection()
        val sent = mutableMapOf<Long, SentPacket>()
        val lost = ld.detectAndRemoveLost(sent, largestAckedPn = 100L, nowMs = 1L)
        assertTrue(lost.isEmpty())
    }

    @Test
    fun lostPackets_carryOriginalTokens() {
        val ld = QuicLossDetection()
        val sent = mutableMapOf<Long, SentPacket>()
        sent[0L] =
            SentPacket(
                packetNumber = 0L,
                sentAtMillis = 0L,
                ackEliciting = true,
                sizeBytes = 100,
                tokens =
                    listOf(
                        RecoveryToken.MaxStreamsUni(maxStreams = 150L),
                        RecoveryToken.MaxData(maxData = 5_000L),
                    ),
            )
        // PN 0 is below largestAckedPn=10 - threshold(3) = 7, so lost by packet threshold.
        val lost = ld.detectAndRemoveLost(sent, largestAckedPn = 10L, nowMs = 1L)
        assertEquals(1, lost.size)
        assertEquals(2, lost.single().tokens.size)
        assertEquals(RecoveryToken.MaxStreamsUni(150L), lost.single().tokens[0])
        assertEquals(RecoveryToken.MaxData(5_000L), lost.single().tokens[1])
        assertNull(sent[0L])
    }

    @Test
    fun packetThresholdAndTimeThresholdMatch_singleRemoval() {
        // Verify packet is only dropped from the map once even when both
        // thresholds say "lost" (defensive — Map.iterator.remove() should
        // be called exactly once per entry).
        val ld = QuicLossDetection()
        ld.onRttSample(largestAckedSentTimeMs = 0L, ackDelayMs = 0L, nowMs = 10L)
        val sent = mutableMapOf<Long, SentPacket>()
        sent[0L] = sentPacket(0L, sentAt = 0L) // old AND below threshold
        val lost = ld.detectAndRemoveLost(sent, largestAckedPn = 10L, nowMs = 200L)
        assertEquals(1, lost.size)
        assertTrue(sent.isEmpty())
    }
}
