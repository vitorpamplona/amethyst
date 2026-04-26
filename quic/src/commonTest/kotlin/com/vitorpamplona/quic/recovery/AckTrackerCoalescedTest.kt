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
import kotlin.test.assertTrue

/**
 * Regression coverage for the bug where AckTracker only saw the largest PN
 * per inbound batch (because the parser was passing
 * `state.pnSpace.largestReceived` instead of the just-decrypted packet's
 * actual PN). With two coalesced packets in one datagram, only the larger PN
 * was tracked — the server retransmits the smaller forever.
 */
class AckTrackerCoalescedTest {
    @Test
    fun two_packets_with_distinct_pns_both_get_acked() {
        val tracker = AckTracker()
        // Two coalesced packets in one datagram: PN 5 then PN 6.
        tracker.receivedPacket(packetNumber = 5L, ackEliciting = true, receivedAtMillis = 1000L)
        tracker.receivedPacket(packetNumber = 6L, ackEliciting = true, receivedAtMillis = 1000L)
        val ack = tracker.buildAckFrame(nowMillis = 1010L)
        assertNotNull(ack)
        assertEquals(6L, ack.largestAcknowledged)
        assertEquals(1L, ack.firstAckRange, "first range covers PNs 5..6 (length 1 = 2 packets)")
        assertTrue(ack.additionalRanges.isEmpty(), "no gap between PN 5 and PN 6 — single contiguous range")
    }

    @Test
    fun gapped_pns_produce_two_ranges() {
        val tracker = AckTracker()
        tracker.receivedPacket(packetNumber = 0L, ackEliciting = true, receivedAtMillis = 1000L)
        tracker.receivedPacket(packetNumber = 2L, ackEliciting = true, receivedAtMillis = 1000L) // gap at 1
        val ack = tracker.buildAckFrame(nowMillis = 1010L)!!
        assertEquals(2L, ack.largestAcknowledged)
        assertEquals(0L, ack.firstAckRange, "first range covers PN 2 alone")
        assertEquals(1, ack.additionalRanges.size)
        // gap = previous_smallest - current_largest - 2 = 2 - 0 - 2 = 0 (one packet missing between)
        assertEquals(0L, ack.additionalRanges[0].gap)
        assertEquals(0L, ack.additionalRanges[0].ackRangeLength, "second range covers PN 0 alone")
    }

    @Test
    fun out_of_order_arrival_still_yields_one_contiguous_range() {
        val tracker = AckTracker()
        // Arrive 7, 5, 6 — out of order but contiguous.
        tracker.receivedPacket(7L, true, 1000L)
        tracker.receivedPacket(5L, true, 1001L)
        tracker.receivedPacket(6L, true, 1002L)
        val ack = tracker.buildAckFrame(1010L)!!
        assertEquals(7L, ack.largestAcknowledged)
        assertEquals(2L, ack.firstAckRange, "5..7 is 3 packets = length 2")
        assertTrue(ack.additionalRanges.isEmpty())
    }
}
