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

    var ackTracker =
        com.vitorpamplona.quic.recovery
            .AckTracker()
        private set

    var cryptoSend = SendBuffer()
        private set

    var cryptoReceive = ReceiveBuffer()
        private set

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

    /**
     * RFC 9001 §4.9: latches true once [discardKeys] runs. Used by
     * the writer / parser to short-circuit operations on a discarded
     * level (parser already drops packets via the
     * [receiveProtection] null check; this flag is for the symmetry
     * tests + future code that wants to assert "level is dead").
     */
    var keysDiscarded: Boolean = false
        private set

    /**
     * RFC 9001 §4.9: drop all key material + buffered state for this
     * encryption level once the level is no longer needed. Idempotent.
     *
     *   - §4.9.1: a client MUST discard Initial keys when it first
     *     sends a Handshake packet. Trigger lives in
     *     [com.vitorpamplona.quic.connection.QuicConnectionWriter] —
     *     after we build a Handshake-level packet, we discard Initial.
     *   - §4.9.2: an endpoint MUST discard Handshake keys when the
     *     handshake is confirmed. Per §4.1.2 a client confirms the
     *     handshake on receipt of a HANDSHAKE_DONE frame; the trigger
     *     lives in [com.vitorpamplona.quic.connection.QuicConnectionParser].
     *
     * Frees the AEAD cipher state, drops any unsent / unacked CRYPTO
     * bytes (no longer reachable since they were handshake-only), and
     * resets the loss-detection accounting. Future inbound packets at
     * this encryption level are dropped silently because
     * [receiveProtection] is null. Future outbound builds skip the
     * level for the same reason on [sendProtection].
     *
     * The class-level docstring on [LevelState] still describes the
     * fields as if they're permanent; after [discardKeys] those
     * descriptions only apply while [keysDiscarded] is false.
     */
    fun discardKeys() {
        if (keysDiscarded) return
        sendProtection = null
        receiveProtection = null
        cryptoSend = SendBuffer()
        cryptoReceive = ReceiveBuffer()
        ackTracker =
            com.vitorpamplona.quic.recovery
                .AckTracker()
        sentPackets.clear()
        largestAckedPn = null
        largestAckedSentTimeMs = null
        keysDiscarded = true
    }
}
