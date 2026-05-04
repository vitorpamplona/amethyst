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

import com.vitorpamplona.quic.connection.recovery.SentPacket
import com.vitorpamplona.quic.stream.ReceiveBuffer
import com.vitorpamplona.quic.stream.SendBuffer

/** Per-encryption-level state owned by [QuicConnection]. */
class LevelState {
    val pnSpace = PacketNumberSpaceState()
    val ackTracker =
        com.vitorpamplona.quic.recovery
            .AckTracker()
    val cryptoSend = SendBuffer()
    val cryptoReceive = ReceiveBuffer()
    var sendProtection: PacketProtection? = null
    var receiveProtection: PacketProtection? = null

    /**
     * Per-packet retention for RFC 9002 loss detection + retransmit.
     * Populated by [QuicConnectionWriter] when a packet is built;
     * drained by the ACK handler in [QuicConnectionParser] (step 3 of
     * `quic/plans/2026-05-04-control-frame-retransmit.md`) and by
     * loss detection (step 5).
     *
     * Keyed by packet number. Step 2 only populates this map for
     * Application-level packets — Initial / Handshake-level
     * retransmit (CRYPTO) is out of scope, see plan.
     *
     * Caller of any read/write to this map must hold
     * [QuicConnection.lock].
     */
    val sentPackets: MutableMap<Long, SentPacket> = HashMap()

    /**
     * RFC 9002 §6.1 largest acknowledged packet number observed
     * by an inbound ACK in this space. Updated only when the ACK
     * advances it — duplicate ACKs do not move the value.
     * Used as the high-water mark for packet-threshold loss detection.
     */
    var largestAckedPn: Long? = null

    /**
     * Send time (epoch ms, from the writer's `nowMillis` source) of
     * the SentPacket whose PN is [largestAckedPn]. Used to compute
     * the RTT sample on a newly-advancing ACK (RFC 9002 §5.2).
     * Null until an ACK arrives.
     */
    var largestAckedSentTimeMs: Long? = null
}
