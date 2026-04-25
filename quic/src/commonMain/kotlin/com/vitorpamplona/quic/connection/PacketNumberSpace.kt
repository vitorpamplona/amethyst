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

/**
 * The three QUIC packet-number spaces per RFC 9000 §12.3.
 *
 * Each space has independent monotonically-increasing packet numbers,
 * independent ACK state, and is protected with a different set of keys.
 */
enum class PacketNumberSpace {
    INITIAL,
    HANDSHAKE,
    APPLICATION,
}

/**
 * Per-space outbound packet-number generator and inbound largest-received tracker.
 *
 * RFC 9000 §17.1: packet numbers in a space MUST start at 0 and increase by
 * exactly 1 for each packet sent. Packet numbers MUST NOT exceed 2^62 - 1 in a
 * single connection, but in practice we run out of bytes long before then.
 */
class PacketNumberSpaceState {
    /** Next packet number to assign on send. RFC 9000 §17.1 — starts at 0. */
    var nextPacketNumber: Long = 0L
        private set

    /** Largest packet number we've received that decrypted successfully. */
    var largestReceived: Long = -1L
        private set

    /** Time (ms since epoch) the [largestReceived] packet was received. */
    var largestReceivedTime: Long = 0L
        private set

    /** Allocate the next outbound packet number. */
    fun allocateOutbound(): Long = nextPacketNumber++

    /** Note that an inbound packet was successfully decrypted. */
    fun observeInbound(
        packetNumber: Long,
        receivedAtMillis: Long,
    ) {
        if (packetNumber > largestReceived) {
            largestReceived = packetNumber
            largestReceivedTime = receivedAtMillis
        }
    }

    /**
     * Decode a truncated wire packet number using RFC 9000 Appendix A.3.
     *
     * `expectedPn` is `largestReceived + 1` (or 0 when nothing has been
     * received yet). `truncatedPn` is the value that was on the wire after
     * removing header protection. `pnLen` is the wire length in bytes (1..4).
     */
    fun decodePacketNumber(
        truncatedPn: Long,
        pnLen: Int,
    ): Long = decodePacketNumber(largestReceived, truncatedPn, pnLen)

    companion object {
        fun decodePacketNumber(
            largestReceived: Long,
            truncatedPn: Long,
            pnLen: Int,
        ): Long {
            val expected = largestReceived + 1
            val pnNbits = pnLen * 8
            val pnWin = 1L shl pnNbits
            val pnHwin = pnWin / 2
            val pnMask = pnWin - 1
            val candidate = (expected and pnMask.inv()) or truncatedPn
            return when {
                candidate <= expected - pnHwin && candidate < (1L shl 62) - pnWin -> candidate + pnWin
                candidate > expected + pnHwin && candidate >= pnWin -> candidate - pnWin
                else -> candidate
            }
        }

        /**
         * Choose the minimum number of bytes needed to encode [packetNumber]
         * given the largest acked packet — RFC 9000 §17.1 / §A.2.
         *
         * The encoded length must satisfy `2^(8*len - 1) >= num_unacked`,
         * which gives the table:
         *   num_unacked ≤ 128         → 1 byte
         *   num_unacked ≤ 32768       → 2 bytes
         *   num_unacked ≤ 8388608     → 3 bytes
         *   otherwise                 → 4 bytes
         */
        fun encodeLength(
            packetNumber: Long,
            largestAcked: Long,
        ): Int {
            val numUnacked = if (largestAcked < 0) packetNumber + 1 else packetNumber - largestAcked
            return when {
                numUnacked <= 128L -> 1
                numUnacked <= 32_768L -> 2
                numUnacked <= 8_388_608L -> 3
                else -> 4
            }
        }
    }
}
