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
 * `Long` and invokes [block] for each ACK'd PN.
 *
 * NOTE: this primitive walks every PN in the range. Production code
 * MUST use [drainAckedSentPackets] (which is bounded by the in-flight
 * map) — a hostile peer with `firstAckRange = 2^62-1` would otherwise
 * pin a core forever inside this loop. This entry point is retained
 * for tests that intentionally inspect the on-wire range expansion
 * with small, well-formed inputs.
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
 *
 * DoS hardening: instead of walking every PN inside each ACK range
 * (up to 2^62 with a hostile peer — single 8-byte varint pinning a
 * core forever), we walk the [sentPackets] keys (bounded by the
 * congestion window, typically ≤ a few thousand) and check
 * membership against the parsed range list. O(N·M) where N = sent
 * packets in flight, M = ACK ranges (bounded by packet payload
 * size). A peer with `firstAckRange = 2^62-1` simply matches every
 * legitimate in-flight PN once, then returns.
 */
fun drainAckedSentPackets(
    sentPackets: MutableMap<Long, SentPacket>,
    ack: AckFrame,
): List<SentPacket> {
    if (sentPackets.isEmpty()) return emptyList()
    val ranges = parseAckRanges(ack)
    if (ranges.isEmpty()) return emptyList()
    val drained = mutableListOf<SentPacket>()
    val it = sentPackets.entries.iterator()
    while (it.hasNext()) {
        val entry = it.next()
        val pn = entry.key
        for (i in ranges.indices) {
            val r = ranges[i]
            if (pn in r) {
                drained += entry.value
                it.remove()
                break
            }
        }
    }
    return drained
}

/**
 * Parse an [AckFrame]'s on-wire ranges into a list of `[smallest, largest]`
 * `LongRange` entries, descending. Each range is clamped to non-negative
 * PNs and bounded against [Long] underflow on the additional-ranges
 * walk. Stops early when the descending walk crosses zero (per RFC 9000
 * §19.3.1, all PNs are non-negative). The result is small (bounded by
 * the ACK frame's payload size).
 */
private fun parseAckRanges(ack: AckFrame): List<LongRange> {
    val largest = ack.largestAcknowledged
    if (largest < 0L) return emptyList()
    val firstSmallest = (largest - ack.firstAckRange).coerceAtLeast(0L)
    val out = ArrayList<LongRange>(ack.additionalRanges.size + 1)
    out += firstSmallest..largest
    var prevSmallest = firstSmallest
    for (range in ack.additionalRanges) {
        // RFC 9000 §19.3.1: nextLargest = prevSmallest - gap - 2.
        val nextLargest = prevSmallest - range.gap - 2L
        if (nextLargest < 0L) break
        val nextSmallest = (nextLargest - range.ackRangeLength).coerceAtLeast(0L)
        out += nextSmallest..nextLargest
        prevSmallest = nextSmallest
    }
    return out
}
