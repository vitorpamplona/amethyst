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
package com.vitorpamplona.quic.recovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Round-4 perf #1 regression: [AckTracker.buildAckFrame] must return null when
 * nothing new ack-eliciting has arrived since the last call. Pre-fix every
 * outbound packet (~50/sec for an audio room) carried a redundant ACK frame.
 *
 * The contract:
 *   1. First ack-eliciting reception → buildAckFrame returns a frame; flag clears.
 *   2. Second buildAckFrame without new reception → returns null.
 *   3. Subsequent ack-eliciting reception → next buildAckFrame returns frame.
 *   4. Non-ack-eliciting receptions (PADDING-only, ACK-only) DO NOT trigger a
 *      new ACK on their own.
 */
class AckTrackerGatingTest {
    @Test
    fun first_build_after_ack_eliciting_returns_frame() {
        val tracker = AckTracker()
        tracker.receivedPacket(packetNumber = 5L, ackEliciting = true, receivedAtMillis = 1000L)
        assertNotNull(tracker.buildAckFrame(nowMillis = 1010L))
    }

    @Test
    fun second_build_without_new_reception_returns_null() {
        val tracker = AckTracker()
        tracker.receivedPacket(packetNumber = 5L, ackEliciting = true, receivedAtMillis = 1000L)
        tracker.buildAckFrame(nowMillis = 1010L) // first ack — clears flag
        assertNull(
            tracker.buildAckFrame(nowMillis = 1020L),
            "no new ack-eliciting reception → no redundant ACK",
        )
    }

    @Test
    fun build_re_arms_after_subsequent_ack_eliciting_reception() {
        val tracker = AckTracker()
        tracker.receivedPacket(packetNumber = 5L, ackEliciting = true, receivedAtMillis = 1000L)
        tracker.buildAckFrame(nowMillis = 1010L)
        // New ack-eliciting reception arrives.
        tracker.receivedPacket(packetNumber = 6L, ackEliciting = true, receivedAtMillis = 1100L)
        val ack = tracker.buildAckFrame(nowMillis = 1110L)
        assertNotNull(ack, "fresh ack-eliciting reception must re-arm the gate")
        assertEquals(6L, ack.largestAcknowledged)
    }

    @Test
    fun non_ack_eliciting_reception_alone_does_not_arm_the_gate() {
        // Per RFC 9000 §13.2.1 we don't have to ACK ACK-only packets. The
        // tracker still records the PN (so future ACKs cover it), but the
        // gate doesn't open until something ack-eliciting arrives.
        val tracker = AckTracker()
        tracker.receivedPacket(packetNumber = 1L, ackEliciting = false, receivedAtMillis = 1000L)
        assertNull(
            tracker.buildAckFrame(nowMillis = 1010L),
            "non-ack-eliciting reception alone must not produce an ACK",
        )
    }

    @Test
    fun empty_tracker_returns_null() {
        val tracker = AckTracker()
        assertNull(tracker.buildAckFrame(nowMillis = 1000L))
    }
}
