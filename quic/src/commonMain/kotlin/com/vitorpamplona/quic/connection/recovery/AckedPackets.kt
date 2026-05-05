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

/**
 * Walks the ACK ranges of an [AckFrame] in RFC 9000 §19.3.1 form and
 * yields each ACK'd packet number, descending. The first range covers
 * `[largestAcked - firstAckRange, largestAcked]`; each additional
 * range's largest PN is `previousSmallest - gap - 2` and its smallest
 * is `largest - ackRangeLength`.
 *
 * Ranges below packet number 0 are clamped — the spec disallows
 * negative PNs, but defensive clamping keeps a malformed peer ACK
 * from blowing up the iterator.
 *
 * The function does not allocate per-PN; it iterates by primitive
 * `Long` and invokes [block] for each ACK'd PN. Hot path on every
 * inbound ACK frame, so allocation matters.
 */
inline fun forEachAckedPacketNumber(
    ack: AckFrame,
    block: (Long) -> Unit,
) {
    var currentLargest = ack.largestAcknowledged
    var currentSmallest = currentLargest - ack.firstAckRange
    if (currentSmallest < 0L) currentSmallest = 0L
    var pn = currentLargest
    while (pn >= currentSmallest) {
        block(pn)
        pn -= 1L
    }
    for (range in ack.additionalRanges) {
        // Next range's largest = previousSmallest - gap - 2.
        // Next range's smallest = largest - ackRangeLength.
        val nextLargest = currentSmallest - range.gap - 2L
        if (nextLargest < 0L) break
        val nextSmallest = (nextLargest - range.ackRangeLength).coerceAtLeast(0L)
        var pn2 = nextLargest
        while (pn2 >= nextSmallest) {
            block(pn2)
            pn2 -= 1L
        }
        currentSmallest = nextSmallest
    }
}

/**
 * Drain [sentPackets] of every entry whose packet number is covered
 * by [ack]. Returns the drained packets so callers can pass their
 * tokens to congestion-control / RTT estimation layers (step 5+ of
 * `quic/plans/2026-05-04-control-frame-retransmit.md`).
 *
 * Caller must hold the connection lock.
 */
fun drainAckedSentPackets(
    sentPackets: MutableMap<Long, SentPacket>,
    ack: AckFrame,
): List<SentPacket> {
    val drained = mutableListOf<SentPacket>()
    forEachAckedPacketNumber(ack) { pn ->
        sentPackets.remove(pn)?.let { drained += it }
    }
    return drained
}
