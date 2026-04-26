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

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.DatagramFrame
import com.vitorpamplona.quic.frame.HandshakeDoneFrame
import com.vitorpamplona.quic.frame.MaxDataFrame
import com.vitorpamplona.quic.frame.MaxStreamDataFrame
import com.vitorpamplona.quic.frame.MaxStreamsFrame
import com.vitorpamplona.quic.frame.NewConnectionIdFrame
import com.vitorpamplona.quic.frame.NewTokenFrame
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.ResetStreamFrame
import com.vitorpamplona.quic.frame.StopSendingFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.frame.decodeFrames
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.ShortHeaderPacket
import com.vitorpamplona.quic.stream.StreamId
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
            // Per RFC 9001 §5.5, drop ONLY the failing packet, not subsequent
            // coalesced ones. Use peekHeader to advance over a packet whose
            // payload we couldn't decrypt; only break the loop on a header
            // that's totally unparseable (then we don't know where the next
            // packet starts).
            val peeked = LongHeaderPacket.peekHeader(datagram, offset) ?: break
            val consumed = feedLongHeaderPacket(conn, datagram, offset, nowMillis)
            offset += consumed ?: peeked.totalLength
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

    dispatchFrames(conn, level, parsed.packet.payload, parsed.packet.packetNumber, nowMillis)
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
    dispatchFrames(conn, EncryptionLevel.APPLICATION, parsed.packet.payload, parsed.packet.packetNumber, nowMillis)
}

private fun dispatchFrames(
    conn: QuicConnection,
    level: EncryptionLevel,
    payload: ByteArray,
    packetNumber: Long,
    nowMillis: Long,
) {
    // Audit-4 #1: malformed frames in an otherwise-AEAD-validated payload (or
    // unknown frame types from a future-extension peer) used to throw straight
    // through the read loop's `finally` block, dropping the connection without
    // ever sending CONNECTION_CLOSE. Catch decode exceptions and turn them
    // into a graceful close so the peer learns why we tore down.
    val frames =
        try {
            decodeFrames(payload)
        } catch (e: QuicCodecException) {
            conn.markClosedExternally("frame decode failed: ${e.message}")
            return
        }
    val state = conn.levelState(level)
    var ackEliciting = false
    for (frame in frames) {
        when (frame) {
            is AckFrame -> {
                // We don't currently retransmit, so we just absorb ACKs. But
                // we DO purge our own ACK tracker below the peer's largest
                // acknowledged: the peer has confirmed receipt of those ACKs,
                // so we don't need to keep advertising them — without this
                // the range list grows unboundedly on long connections.
                state.ackTracker.purgeBelow(frame.largestAcknowledged - frame.firstAckRange)
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
                // Audit-4 #5: reject peer-attempted CLIENT_BIDI / CLIENT_UNI
                // stream IDs that don't match a stream we've opened. Per RFC
                // 9000 §19.8, only the side that owns the parity may open;
                // a server squatting on a CLIENT_* id is a protocol violation
                // and could otherwise inject phantom streams into newPeerStreams.
                if (StreamId.isClientInitiated(frame.streamId) &&
                    conn.streamByIdLocked(frame.streamId) == null
                ) {
                    conn.markClosedExternally(
                        "peer opened stream ${frame.streamId} on client-initiated id space (STREAM_STATE_ERROR)",
                    )
                    return
                }
                val stream = conn.getOrCreatePeerStreamLocked(frame.streamId)
                // RFC 9000 §4.1: peer MUST NOT send beyond the limit we advertised.
                // The connection-level kill protects against unbounded memory
                // growth from a misbehaving peer.
                val frameEnd = frame.offset + frame.data.size
                if (frameEnd > stream.receiveLimit) {
                    conn.markClosedExternally(
                        "peer exceeded stream ${frame.streamId} receive limit ($frameEnd > ${stream.receiveLimit})",
                    )
                    return
                }
                stream.receive.insert(frame.offset, frame.data, frame.fin)
                val data = stream.receive.readContiguous()
                if (data.isNotEmpty()) {
                    // Round-4 perf #9: mark the stream as needing a flow-
                    // control re-credit check. Writer's
                    // appendFlowControlUpdates consults this flag instead of
                    // walking every open stream on every drain.
                    stream.receiveDirtyForFlowControl = true
                    val delivered = stream.deliverIncoming(data)
                    if (!delivered) {
                        // Audit-4 #3: incoming channel saturated. Closing the
                        // connection beats silently dropping bytes — a stalled
                        // consumer is better surfaced as an error than as a
                        // mysterious hole in the application's data. Use
                        // INTERNAL_ERROR (RFC 9000 §20.1).
                        conn.markClosedExternally(
                            "INTERNAL_ERROR: stream ${frame.streamId} consumer overflowed " +
                                "incoming channel (slow consumer)",
                        )
                        return
                    }
                }
                // Audit-4 #4: only close the incoming channel once the
                // contiguous read frontier has actually reached the FIN
                // offset. Closing on FIN-arrival drops any later-arriving
                // fill chunks silently because trySend on a closed channel
                // returns failure — the application would see a truncated
                // stream with no error signal.
                if (stream.receive.finReceived && stream.receive.isFullyRead()) {
                    stream.closeIncoming()
                }
            }

            is DatagramFrame -> {
                ackEliciting = true
                // Audit-4 #8: cap the inbound datagram queue. RFC 9221
                // datagrams are outside connection flow control; a peer can
                // otherwise pin arbitrary memory by spamming DATAGRAM frames.
                // We drop the OLDEST queued datagram when full — preferable
                // for audio rooms (live streams) over rejecting fresh ones.
                val queue = conn.incomingDatagramsLocked()
                if (queue.size >= QuicConnection.MAX_INCOMING_DATAGRAM_QUEUE) {
                    queue.removeFirst()
                }
                queue.addLast(frame.data)
                conn.signalIncomingDatagram()
            }

            is MaxDataFrame -> {
                // RFC 9000 §13.2.1: MAX_DATA is ack-eliciting. Without this,
                // a packet carrying only MAX_DATA would record the PN but
                // never trigger an ACK (round-4 ACK gating regression).
                ackEliciting = true
                // RFC 9000 §19.9: MAX_DATA only ever raises the cap.
                if (frame.maxData > conn.sendConnectionFlowCredit) {
                    conn.sendConnectionFlowCredit = frame.maxData
                }
            }

            is MaxStreamDataFrame -> {
                ackEliciting = true
                conn.streamByIdLocked(frame.streamId)?.let {
                    if (frame.maxStreamData > it.sendCredit) it.sendCredit = frame.maxStreamData
                }
            }

            is MaxStreamsFrame -> {
                ackEliciting = true
                // RFC 9000 §19.11: MAX_STREAMS only ever raises the cap.
                // Frames with values smaller than the current cap are ignored.
                // Bidi vs uni is signaled via the frame's `bidi` flag.
                if (frame.bidi) {
                    if (frame.maxStreams > conn.peerMaxStreamsBidi) {
                        conn.peerMaxStreamsBidi = frame.maxStreams
                    }
                } else {
                    if (frame.maxStreams > conn.peerMaxStreamsUni) {
                        conn.peerMaxStreamsUni = frame.maxStreams
                    }
                }
            }

            is ResetStreamFrame -> {
                // RFC 9000 §3.5: RESET_STREAM is the peer aborting THEIR send
                // side of the stream. Round-5 #2: it's only legal on streams
                // where the peer owns a send side (server-initiated streams,
                // or our own bidi). A peer RESETting one of OUR uni streams
                // (CLIENT_UNI = id%4==2) is STREAM_STATE_ERROR — they don't
                // have a send side to abort.
                ackEliciting = true
                if (StreamId.kindOf(frame.streamId) == StreamId.Kind.CLIENT_UNI) {
                    conn.markClosedExternally(
                        "STREAM_STATE_ERROR: peer RESET_STREAM on client-uni id ${frame.streamId} (peer has no send side)",
                    )
                    return
                }
                // Mark the peer's stream aborted and close our read side; the
                // application sees a truncated incoming flow.
                conn.streamByIdLocked(frame.streamId)?.closeIncoming()
            }

            is StopSendingFrame -> {
                // Round-4 #2: peer asks us to stop sending on its read side.
                // We don't model an outbound abort yet — this is acknowledged
                // and dropped. A future enhancement should emit RESET_STREAM
                // back per RFC 9000 §3.5.
                ackEliciting = true
            }

            is NewTokenFrame -> {
                // Round-4 #2: 0-RTT/resumption token. Out-of-scope; drop.
                ackEliciting = true
            }

            is NewConnectionIdFrame -> {
                // RFC 9000 §13.2.1: NEW_CONNECTION_ID is ack-eliciting. We
                // don't support migration but still need to ACK to keep
                // peer's loss-recovery happy.
                ackEliciting = true
            }

            is ConnectionCloseFrame -> {
                // Audit-4 #13: any frames following CONNECTION_CLOSE in the
                // same payload MUST NOT be dispatched — they could create
                // streams or deliver bytes on an already-closed connection.
                conn.markClosedExternally("peer CONNECTION_CLOSE: ${frame.reason}")
                return
            }

            is HandshakeDoneFrame -> {
                // Audit-4 #14: HANDSHAKE_DONE is permitted ONLY at Application
                // level (RFC 9000 §19.20). Anywhere else is PROTOCOL_VIOLATION.
                if (level != EncryptionLevel.APPLICATION) {
                    conn.markClosedExternally(
                        "HANDSHAKE_DONE at $level (PROTOCOL_VIOLATION; allowed only at APPLICATION)",
                    )
                    return
                }
                ackEliciting = true
                // Round-5 #13: only flip to CONNECTED if we're still in
                // HANDSHAKING. Pre-fix this unconditionally overwrote the
                // status, which would resurrect a connection that
                // applyPeerTransportParameters had just closed via
                // markClosedExternally because of a CID validation failure.
                if (conn.status == QuicConnection.Status.HANDSHAKING) {
                    conn.status = QuicConnection.Status.CONNECTED
                }
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
    // Always record the packet's actual PN — even non-ack-eliciting packets
    // need to appear in our ACK ranges so the peer's loss-recovery sees a
    // contiguous picture of what we received.
    state.ackTracker.receivedPacket(packetNumber, ackEliciting = ackEliciting, receivedAtMillis = nowMillis)
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
