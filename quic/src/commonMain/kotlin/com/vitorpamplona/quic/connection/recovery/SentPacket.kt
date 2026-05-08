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

/**
 * Per-packet metadata retained from send time until the packet is
 * either ACK'd or declared lost. Mirrors Firefox neqo's
 * `recovery/sent.rs::Packet`.
 *
 * Held in a per-packet-number-space map on
 * [com.vitorpamplona.quic.connection.QuicConnection], keyed by
 * [packetNumber]. On ACK arrival the matching entry is removed and
 * its tokens dropped silently. On loss-detection (RFC 9002 §6.1)
 * the entry is removed and each token is dispatched to its `onLost`
 * handler, which decides whether to re-emit.
 *
 * [ackEliciting] determines whether the packet's loss matters at all
 * for retransmit — non-ack-eliciting packets (ACK-only, PADDING-only,
 * CONNECTION_CLOSE) are never retransmitted per RFC 9000 §13.2.1.
 * They're still tracked so the loss-detection algorithm has consistent
 * timing data, but their tokens are limited to [RecoveryToken.Ack].
 *
 * [sizeBytes] is the on-wire encrypted packet size; used by congestion
 * control to release in-flight bytes when the packet is ACK'd or lost.
 * Recorded here even though `:quic` has no congestion controller today
 * — the field lets a future CC pass plug in without changing the
 * sent-packet schema.
 */
data class SentPacket(
    /** Packet number assigned at encrypt time. Unique within its space. */
    val packetNumber: Long,
    /**
     * Wall-clock epoch milliseconds at the moment the packet was
     * handed to the UDP socket. Source is
     * [com.vitorpamplona.quic.connection.QuicConnection.nowMillis], not
     * `System.currentTimeMillis` — keeps unit tests deterministic.
     */
    val sentAtMillis: Long,
    /**
     * RFC 9000 §13.2.1: whether the packet contains a frame that elicits
     * an ACK from the peer. Loss detection only fires for ack-eliciting
     * packets; non-ack-eliciting packets (ACK-only, PADDING-only,
     * CONNECTION_CLOSE) are never retransmitted.
     */
    val ackEliciting: Boolean,
    /**
     * Encrypted on-wire size in bytes, including AEAD tag and packet
     * header. Used by congestion controllers to release in-flight
     * bytes on ACK / loss.
     */
    val sizeBytes: Int,
    /**
     * Frames in this packet that need to be tracked for retransmit.
     * Empty list is legal for non-ack-eliciting packets that have
     * nothing retransmittable (e.g. ACK-only). Otherwise non-empty.
     */
    val tokens: List<RecoveryToken>,
)
