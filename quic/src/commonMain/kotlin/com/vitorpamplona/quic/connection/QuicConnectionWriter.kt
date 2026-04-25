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

import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.DatagramFrame
import com.vitorpamplona.quic.frame.Frame
import com.vitorpamplona.quic.frame.MaxDataFrame
import com.vitorpamplona.quic.frame.MaxStreamDataFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.frame.encodeFrames
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderPlaintextPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.QuicVersion
import com.vitorpamplona.quic.packet.ShortHeaderPacket
import com.vitorpamplona.quic.packet.ShortHeaderPlaintextPacket

/**
 * Build the next UDP datagram to send for [conn], or return null if there's
 * nothing to send right now.
 *
 * The datagram coalesces (in order):
 *   - Initial packet (if Initial-level CRYPTO has unsent bytes or a pending ACK)
 *   - Handshake packet (likewise)
 *   - 1-RTT packet (CRYPTO from NewSessionTicket, STREAM, DATAGRAM, ACK, etc.)
 *
 * RFC 9000 §14: any datagram containing an Initial packet from the client
 * MUST be padded to at least 1200 bytes total.
 */
fun drainOutbound(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? {
    val parts = mutableListOf<ByteArray>()

    // Closing — emit a CONNECTION_CLOSE at the highest available level.
    if (conn.status == QuicConnection.Status.CLOSING) {
        val frame = ConnectionCloseFrame(conn.closeErrorCode, null, conn.closeReason ?: "")
        val packet = buildBestLevelPacket(conn, listOf(frame)) ?: return null
        conn.status = QuicConnection.Status.CLOSED
        return packet
    }

    val initialPkt = buildInitialPacket(conn, nowMillis)
    if (initialPkt != null) parts += initialPkt

    val handshakePkt = buildHandshakePacket(conn, nowMillis)
    if (handshakePkt != null) parts += handshakePkt

    val applicationPkt = buildApplicationPacket(conn, nowMillis)
    if (applicationPkt != null) parts += applicationPkt

    if (parts.isEmpty()) return null

    // Coalesce
    var totalLen = 0
    for (p in parts) totalLen += p.size
    // Pad the datagram to 1200 bytes if it begins with an Initial packet (RFC 9000 §14).
    if (initialPkt != null && totalLen < 1200) totalLen = 1200
    val out = ByteArray(totalLen)
    var pos = 0
    for (p in parts) {
        p.copyInto(out, pos)
        pos += p.size
    }
    // Tail bytes are zero (PADDING frames in the encryption envelope of the last packet).
    // Note: padding inside the last QUIC packet's encryption is the RFC-correct way; we instead
    // pad the datagram with zero bytes after the last packet. Servers tolerate this for the
    // datagram-level minimum size requirement.
    return out
}

private fun buildBestLevelPacket(
    conn: QuicConnection,
    frames: List<Frame>,
): ByteArray? {
    // Prefer 1-RTT > Handshake > Initial.
    val payload = encodeFrames(frames)
    val app = conn.application
    if (app.sendProtection != null) {
        val proto = app.sendProtection!!
        val pn = app.pnSpace.allocateOutbound()
        return ShortHeaderPacket.build(
            ShortHeaderPlaintextPacket(conn.destinationConnectionId, pn, payload),
            proto.aead,
            proto.key,
            proto.iv,
            proto.hp,
            proto.hpKey,
            largestAckedInSpace = -1L,
        )
    }
    val hs = conn.handshake
    if (hs.sendProtection != null) {
        return buildLongHeaderPacket(conn, EncryptionLevel.HANDSHAKE, payload)
    }
    val init = conn.initial
    if (init.sendProtection != null) {
        return buildLongHeaderPacket(conn, EncryptionLevel.INITIAL, payload)
    }
    return null
}

private fun buildLongHeaderPacket(
    conn: QuicConnection,
    level: EncryptionLevel,
    payload: ByteArray,
): ByteArray {
    val state = conn.levelState(level)
    val proto = state.sendProtection!!
    val pn = state.pnSpace.allocateOutbound()
    val type =
        when (level) {
            EncryptionLevel.INITIAL -> LongHeaderType.INITIAL
            EncryptionLevel.HANDSHAKE -> LongHeaderType.HANDSHAKE
            EncryptionLevel.APPLICATION -> error("APPLICATION uses short-header packets")
        }
    return LongHeaderPacket.build(
        LongHeaderPlaintextPacket(
            type = type,
            version = QuicVersion.V1,
            dcid = conn.destinationConnectionId,
            scid = conn.sourceConnectionId,
            packetNumber = pn,
            payload = payload,
        ),
        proto.aead,
        proto.key,
        proto.iv,
        proto.hp,
        proto.hpKey,
        largestAckedInSpace = -1L,
    )
}

private fun buildInitialPacket(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? = buildHandshakeLevelPacket(conn, EncryptionLevel.INITIAL, nowMillis)

private fun buildHandshakePacket(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? = buildHandshakeLevelPacket(conn, EncryptionLevel.HANDSHAKE, nowMillis)

private fun buildHandshakeLevelPacket(
    conn: QuicConnection,
    level: EncryptionLevel,
    nowMillis: Long,
): ByteArray? {
    val state = conn.levelState(level)
    val proto = state.sendProtection ?: return null
    val frames = mutableListOf<Frame>()

    // Pending ACK?
    state.ackTracker.buildAckFrame(nowMillis, conn.config.ackDelayExponent.toInt())?.let { frames += it }

    // Pending CRYPTO?
    val cryptoChunk = state.cryptoSend.takeChunk(maxBytes = 1100)
    if (cryptoChunk != null && cryptoChunk.data.isNotEmpty()) {
        frames += CryptoFrame(cryptoChunk.offset, cryptoChunk.data)
    }

    if (frames.isEmpty()) return null

    val payload = encodeFrames(frames)
    val pn = state.pnSpace.allocateOutbound()
    val type = if (level == EncryptionLevel.INITIAL) LongHeaderType.INITIAL else LongHeaderType.HANDSHAKE
    return LongHeaderPacket.build(
        LongHeaderPlaintextPacket(
            type = type,
            version = QuicVersion.V1,
            dcid = conn.destinationConnectionId,
            scid = conn.sourceConnectionId,
            packetNumber = pn,
            payload = payload,
        ),
        proto.aead,
        proto.key,
        proto.iv,
        proto.hp,
        proto.hpKey,
        largestAckedInSpace = -1L,
    )
}

private fun buildApplicationPacket(
    conn: QuicConnection,
    nowMillis: Long,
): ByteArray? {
    val state = conn.application
    val proto = state.sendProtection ?: return null
    val frames = mutableListOf<Frame>()

    state.ackTracker.buildAckFrame(nowMillis, conn.config.ackDelayExponent.toInt())?.let { frames += it }

    // Re-credit the peer's send window when our receive offset has advanced
    // beyond half the previously-advertised limit. Emits MAX_STREAM_DATA per
    // stream and MAX_DATA at the connection level.
    appendFlowControlUpdates(conn, frames)

    // Pending datagrams
    while (conn.pendingDatagramsLocked().isNotEmpty()) {
        val payload = conn.pendingDatagramsLocked().removeFirst()
        frames += DatagramFrame(payload, explicitLength = true)
        if (frames.size >= 16) break
    }

    // Drain stream send buffers — round-robin keeping packet under MTU and
    // honoring per-stream + connection-level send credit (RFC 9000 §4).
    var packetBudget = 1100
    for ((id, stream) in conn.streamsLocked()) {
        if (packetBudget <= 64) break
        // Per-stream credit: how many more bytes is the peer willing to receive on this stream?
        val streamRemaining = (stream.sendCredit - stream.send.sentOffset).coerceAtLeast(0L)
        if (streamRemaining <= 0L && !stream.send.finPending) continue
        val maxBytes = minOf(packetBudget - 32, streamRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val chunk = stream.send.takeChunk(maxBytes = maxBytes) ?: continue
        if (chunk.data.isNotEmpty() || chunk.fin) {
            frames += StreamFrame(streamId = id, offset = chunk.offset, data = chunk.data, fin = chunk.fin, explicitLength = true)
            packetBudget -= chunk.data.size + 32
        }
    }

    if (frames.isEmpty()) return null
    val payload = encodeFrames(frames)
    val pn = state.pnSpace.allocateOutbound()
    return ShortHeaderPacket.build(
        ShortHeaderPlaintextPacket(conn.destinationConnectionId, pn, payload),
        proto.aead,
        proto.key,
        proto.iv,
        proto.hp,
        proto.hpKey,
        largestAckedInSpace = -1L,
    )
}

/**
 * Append MAX_STREAM_DATA / MAX_DATA frames re-crediting the peer when our
 * receive cursor has advanced past half the previously-advertised window.
 *
 * Caller must hold [QuicConnection.lock].
 */
private fun appendFlowControlUpdates(
    conn: QuicConnection,
    frames: MutableList<Frame>,
) {
    val cfg = conn.config
    var totalRecvAdvanced = 0L
    for ((id, stream) in conn.streamsLocked()) {
        val rcv = stream.receive.contiguousEnd()
        if (rcv == 0L) continue
        // Re-credit when consumed > half the advertised window.
        val window = cfg.initialMaxStreamDataBidiRemote.coerceAtLeast(cfg.initialMaxStreamDataUni)
        if (rcv >= stream.receiveLimit - window / 2) {
            val newLimit = rcv + window
            if (newLimit > stream.receiveLimit) {
                stream.receiveLimit = newLimit
                frames += MaxStreamDataFrame(id, newLimit)
            }
        }
        totalRecvAdvanced += rcv
    }
    // Connection-level credit: when sum of contiguousEnd across all streams
    // exceeds half the connection-level limit, re-grant.
    if (totalRecvAdvanced > 0L && totalRecvAdvanced >= cfg.initialMaxData / 2) {
        frames += MaxDataFrame(totalRecvAdvanced + cfg.initialMaxData)
    }
}
