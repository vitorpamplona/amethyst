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

import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.AckRange

/**
 * Tracks received packet numbers for one packet-number space and produces
 * ACK frames per RFC 9000 §19.3.
 *
 * We store packet numbers as a sorted list of disjoint ranges (lo..hi).
 * Acks emit the ranges newest-first as RFC 9000 specifies.
 */
class AckTracker {
    private val ranges = mutableListOf<LongRange>() // sorted descending by lo (i.e. ranges[0] has the largest)
    private var ackElicitingPending: Boolean = false
    private var largestRecvTimeMillis: Long = 0L

    fun receivedPacket(
        packetNumber: Long,
        ackEliciting: Boolean,
        receivedAtMillis: Long,
    ) {
        if (ackEliciting) ackElicitingPending = true
        if (ranges.isEmpty()) {
            ranges += LongRange(packetNumber, packetNumber)
            largestRecvTimeMillis = receivedAtMillis
            return
        }
        if (packetNumber > ranges[0].endInclusive) {
            largestRecvTimeMillis = receivedAtMillis
        }

        // Find the range we need to extend (or insertion point).
        for (i in ranges.indices) {
            val r = ranges[i]
            if (packetNumber > r.endInclusive + 1) {
                ranges.add(i, LongRange(packetNumber, packetNumber))
                return
            }
            if (packetNumber == r.endInclusive + 1) {
                // Extend up; check merge with i-1 (if any)
                ranges[i] = LongRange(r.start, packetNumber)
                if (i > 0 && ranges[i - 1].start == packetNumber + 1) {
                    ranges[i - 1] = LongRange(ranges[i].start, ranges[i - 1].endInclusive)
                    ranges.removeAt(i)
                }
                return
            }
            if (packetNumber in r) {
                // Already known.
                return
            }
            if (packetNumber == r.start - 1) {
                ranges[i] = LongRange(packetNumber, r.endInclusive)
                if (i + 1 < ranges.size && ranges[i + 1].endInclusive == packetNumber - 1) {
                    ranges[i] = LongRange(ranges[i + 1].start, ranges[i].endInclusive)
                    ranges.removeAt(i + 1)
                }
                return
            }
        }
        ranges += LongRange(packetNumber, packetNumber)
    }

    fun hasUnackedAckEliciting(): Boolean = ackElicitingPending

    /**
     * Drop ranges entirely below [threshold] — typically called after the peer
     * acknowledges a packet whose number ≥ threshold, since older ranges no
     * longer need to be advertised in our outbound ACKs. Without this the
     * range list grows unboundedly for the lifetime of long connections.
     */
    fun purgeBelow(threshold: Long) {
        if (threshold <= 0L) return
        // ranges are descending by lo; drop the tail.
        val it = ranges.listIterator(ranges.size)
        while (it.hasPrevious()) {
            val r = it.previous()
            if (r.endInclusive < threshold) {
                it.remove()
            } else {
                break
            }
        }
    }

    fun isEmpty(): Boolean = ranges.isEmpty()

    fun largestReceived(): Long = if (ranges.isEmpty()) -1L else ranges[0].endInclusive

    /** Build an ACK frame covering everything we've received. */
    fun buildAckFrame(
        nowMillis: Long,
        ackDelayExponent: Int = 3,
    ): AckFrame? {
        if (ranges.isEmpty()) return null
        val largest = ranges[0].endInclusive
        val firstRangeLength = largest - ranges[0].start
        val rest = mutableListOf<AckRange>()
        for (i in 1 until ranges.size) {
            val gap = ranges[i - 1].start - ranges[i].endInclusive - 2
            val len = ranges[i].endInclusive - ranges[i].start
            rest += AckRange(gap, len)
        }
        val ackDelayMicros = (nowMillis - largestRecvTimeMillis) * 1000L
        val ackDelay = (ackDelayMicros ushr ackDelayExponent).coerceAtLeast(0L)
        ackElicitingPending = false
        return AckFrame(
            largestAcknowledged = largest,
            ackDelay = ackDelay,
            firstAckRange = firstRangeLength,
            additionalRanges = rest,
        )
    }
}
