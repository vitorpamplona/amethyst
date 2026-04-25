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

import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.DatagramFrame
import com.vitorpamplona.quic.frame.HandshakeDoneFrame
import com.vitorpamplona.quic.frame.MaxDataFrame
import com.vitorpamplona.quic.frame.MaxStreamDataFrame
import com.vitorpamplona.quic.frame.MaxStreamsFrame
import com.vitorpamplona.quic.frame.NewConnectionIdFrame
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.frame.decodeFrames
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.ShortHeaderPacket
import com.vitorpamplona.quic.tls.TlsClient

/**
 * Decode every QUIC packet inside a single inbound UDP datagram and dispatch
 * its frames to [conn]'s state.
 *
 * QUIC permits *coalescing* multiple packets into one datagram (RFC 9000 §12.2)
 * — typically Initial + Handshake from the server in the same datagram during
 * the handshake. We loop until the datagram is fully consumed or a packet
 * fails to parse (which we drop silently per RFC 9001 §5.5).
 */
fun feedDatagram(
    conn: QuicConnection,
    datagram: ByteArray,
    nowMillis: Long,
) {
    var offset = 0
    while (offset < datagram.size) {
        val first = datagram[offset].toInt() and 0xFF
        val isLong = (first and 0x80) != 0
        if (isLong) {
            val consumed = feedLongHeaderPacket(conn, datagram, offset, nowMillis) ?: break
            offset += consumed
        } else {
            // Short-header — consumes the rest of the datagram.
            feedShortHeaderPacket(conn, datagram, offset, nowMillis)
            return
        }
    }
}

private fun feedLongHeaderPacket(
    conn: QuicConnection,
    datagram: ByteArray,
    offset: Int,
    nowMillis: Long,
): Int? {
    val peeked = LongHeaderPacket.peekHeader(datagram, offset) ?: return null
    val level =
        when (peeked.type) {
            LongHeaderType.INITIAL -> EncryptionLevel.INITIAL
            LongHeaderType.HANDSHAKE -> EncryptionLevel.HANDSHAKE
            LongHeaderType.ZERO_RTT, LongHeaderType.RETRY -> return null // unsupported in client
        }
    val state = conn.levelState(level)
    val proto = state.receiveProtection ?: return null
    val parsed =
        LongHeaderPacket.parseAndDecrypt(
            bytes = datagram,
            offset = offset,
            aead = proto.aead,
            key = proto.key,
            iv = proto.iv,
            hp = proto.hp,
            hpKey = proto.hpKey,
            largestReceivedInSpace = state.pnSpace.largestReceived,
        ) ?: return null

    state.pnSpace.observeInbound(parsed.packet.packetNumber, nowMillis)

    // The server's source CID becomes our destination CID for subsequent packets.
    if (level == EncryptionLevel.INITIAL) {
        conn.destinationConnectionId = parsed.packet.scid
    }

    dispatchFrames(conn, level, parsed.packet.payload, nowMillis)
    return parsed.consumed
}

private fun feedShortHeaderPacket(
    conn: QuicConnection,
    datagram: ByteArray,
    offset: Int,
    nowMillis: Long,
) {
    val state = conn.levelState(EncryptionLevel.APPLICATION)
    val proto = state.receiveProtection ?: return
    val parsed =
        ShortHeaderPacket.parseAndDecrypt(
            bytes = datagram,
            offset = offset,
            dcidLen = conn.sourceConnectionId.length,
            aead = proto.aead,
            key = proto.key,
            iv = proto.iv,
            hp = proto.hp,
            hpKey = proto.hpKey,
            largestReceivedInSpace = state.pnSpace.largestReceived,
        ) ?: return
    state.pnSpace.observeInbound(parsed.packet.packetNumber, nowMillis)
    dispatchFrames(conn, EncryptionLevel.APPLICATION, parsed.packet.payload, nowMillis)
}

private fun dispatchFrames(
    conn: QuicConnection,
    level: EncryptionLevel,
    payload: ByteArray,
    nowMillis: Long,
) {
    val frames = decodeFrames(payload)
    val state = conn.levelState(level)
    var ackEliciting = false
    for (frame in frames) {
        when (frame) {
            is AckFrame -> {
                // We don't currently retransmit, so we just absorb ACKs; once
                // Phase F adds retransmission this updates the inflight tracker.
            }

            is CryptoFrame -> {
                ackEliciting = true
                state.cryptoReceive.insert(frame.offset, frame.data)
                val contiguous = state.cryptoReceive.readContiguous()
                if (contiguous.isNotEmpty()) {
                    val tlsLevel =
                        when (level) {
                            EncryptionLevel.INITIAL -> TlsClient.Level.INITIAL
                            EncryptionLevel.HANDSHAKE -> TlsClient.Level.HANDSHAKE
                            EncryptionLevel.APPLICATION -> TlsClient.Level.APPLICATION
                        }
                    conn.tls.pushHandshakeBytes(tlsLevel, contiguous)
                    // Pull any new outbound CRYPTO bytes out of TLS into our
                    // send queue at the matching encryption level.
                    drainTlsOutbound(conn)
                }
            }

            is StreamFrame -> {
                ackEliciting = true
                val stream = conn.getOrCreatePeerStream(frame.streamId)
                stream.receive.insert(frame.offset, frame.data, frame.fin)
                val data = stream.receive.readContiguous()
                if (data.isNotEmpty()) {
                    stream.deliverIncoming(data)
                }
                if (stream.receive.finReceived) {
                    stream.closeIncoming()
                }
            }

            is DatagramFrame -> {
                ackEliciting = true
                conn.incomingDatagramsBuffer().addLast(frame.data)
            }

            is MaxDataFrame -> {
                // Updates connection-level send credit; left to the orchestrator.
            }

            is MaxStreamDataFrame -> {
                conn.streamById(frame.streamId)?.let {
                    if (frame.maxStreamData > it.sendCredit) it.sendCredit = frame.maxStreamData
                }
            }

            is MaxStreamsFrame -> {
                // Tracking left for a later phase.
            }

            is NewConnectionIdFrame -> {
                // We don't support migration; ignore.
            }

            is ConnectionCloseFrame -> {
                conn.status = QuicConnection.Status.CLOSED
            }

            is HandshakeDoneFrame -> {
                conn.status = QuicConnection.Status.CONNECTED
            }

            is PingFrame -> {
                ackEliciting = true
            }

            else -> {
                // PADDING + DATA_BLOCKED + STREAM_DATA_BLOCKED + STREAMS_BLOCKED
                // are non-eliciting / cleared during decode.
            }
        }
    }
    if (ackEliciting) {
        // Get largest received from pn space and feed into ack tracker.
        val pn = state.pnSpace.largestReceived
        if (pn >= 0) {
            state.ackTracker.receivedPacket(pn, ackEliciting = true, receivedAtMillis = nowMillis)
        }
    }
}

private fun drainTlsOutbound(conn: QuicConnection) {
    for (lvl in TlsClient.Level.entries) {
        while (true) {
            val bytes = conn.tls.pollOutbound(lvl) ?: break
            val state =
                when (lvl) {
                    TlsClient.Level.INITIAL -> conn.initial
                    TlsClient.Level.HANDSHAKE -> conn.handshake
                    TlsClient.Level.APPLICATION -> conn.application
                }
            state.cryptoSend.enqueue(bytes)
        }
    }
}
