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
package com.vitorpamplona.quic.frame

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter

/**
 * QUIC frame type codes per RFC 9000 §19. Only the ones we actually emit or
 * route are listed.
 */
object FrameType {
    const val PADDING: Long = 0x00
    const val PING: Long = 0x01
    const val ACK: Long = 0x02
    const val ACK_ECN: Long = 0x03
    const val RESET_STREAM: Long = 0x04
    const val STOP_SENDING: Long = 0x05
    const val CRYPTO: Long = 0x06
    const val NEW_TOKEN: Long = 0x07

    // STREAM frames are 0x08..0x0f based on OFF/LEN/FIN flags
    const val STREAM_BASE: Long = 0x08
    const val STREAM_FIN_BIT: Long = 0x01
    const val STREAM_LEN_BIT: Long = 0x02
    const val STREAM_OFF_BIT: Long = 0x04

    const val MAX_DATA: Long = 0x10
    const val MAX_STREAM_DATA: Long = 0x11
    const val MAX_STREAMS_BIDI: Long = 0x12
    const val MAX_STREAMS_UNI: Long = 0x13
    const val DATA_BLOCKED: Long = 0x14
    const val STREAM_DATA_BLOCKED: Long = 0x15
    const val STREAMS_BLOCKED_BIDI: Long = 0x16
    const val STREAMS_BLOCKED_UNI: Long = 0x17
    const val NEW_CONNECTION_ID: Long = 0x18
    const val RETIRE_CONNECTION_ID: Long = 0x19
    const val PATH_CHALLENGE: Long = 0x1A
    const val PATH_RESPONSE: Long = 0x1B
    const val CONNECTION_CLOSE_TRANSPORT: Long = 0x1C
    const val CONNECTION_CLOSE_APP: Long = 0x1D
    const val HANDSHAKE_DONE: Long = 0x1E

    /** RFC 9221 — DATAGRAM frame. 0x30 = no length, 0x31 = length-prefixed. */
    const val DATAGRAM: Long = 0x30
    const val DATAGRAM_LEN: Long = 0x31
}

sealed class Frame {
    abstract fun encode(out: QuicWriter)
}

object PaddingFrame : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.PADDING.toInt())
    }
}

object PingFrame : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.PING.toInt())
    }
}

class CryptoFrame(
    val offset: Long,
    val data: ByteArray,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.CRYPTO.toInt())
        out.writeVarint(offset)
        out.writeVarint(data.size.toLong())
        out.writeBytes(data)
    }
}

class StreamFrame(
    val streamId: Long,
    val offset: Long,
    val data: ByteArray,
    val fin: Boolean,
    val explicitLength: Boolean = true,
) : Frame() {
    override fun encode(out: QuicWriter) {
        var type = FrameType.STREAM_BASE
        if (offset > 0) type = type or FrameType.STREAM_OFF_BIT
        if (explicitLength) type = type or FrameType.STREAM_LEN_BIT
        if (fin) type = type or FrameType.STREAM_FIN_BIT
        out.writeByte(type.toInt())
        out.writeVarint(streamId)
        if (offset > 0) out.writeVarint(offset)
        if (explicitLength) out.writeVarint(data.size.toLong())
        out.writeBytes(data)
    }
}

class AckFrame(
    val largestAcknowledged: Long,
    val ackDelay: Long,
    /** Pairs of (gap, ackRangeLength). The first range covers `largestAcknowledged - first_range_length`. */
    val firstAckRange: Long,
    val additionalRanges: List<AckRange> = emptyList(),
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.ACK.toInt())
        out.writeVarint(largestAcknowledged)
        out.writeVarint(ackDelay)
        out.writeVarint(additionalRanges.size.toLong())
        out.writeVarint(firstAckRange)
        for (r in additionalRanges) {
            out.writeVarint(r.gap)
            out.writeVarint(r.ackRangeLength)
        }
    }
}

data class AckRange(
    val gap: Long,
    val ackRangeLength: Long,
)

class ConnectionCloseFrame(
    val errorCode: Long,
    val frameType: Long?,
    val reason: String,
) : Frame() {
    override fun encode(out: QuicWriter) {
        if (frameType != null) {
            out.writeByte(FrameType.CONNECTION_CLOSE_TRANSPORT.toInt())
            out.writeVarint(errorCode)
            out.writeVarint(frameType)
        } else {
            out.writeByte(FrameType.CONNECTION_CLOSE_APP.toInt())
            out.writeVarint(errorCode)
        }
        val reasonBytes = reason.encodeToByteArray()
        out.writeVarint(reasonBytes.size.toLong())
        out.writeBytes(reasonBytes)
    }
}

/**
 * RFC 9000 §19.4 — peer abruptly terminates the send side of a stream.
 *
 * Audit-4 finding: peers (aioquic, picoquic) routinely emit RESET_STREAM and
 * the prior parser dropped the connection on first arrival because the frame
 * type wasn't decoded. We accept and surface it; cleanup of the affected
 * receive buffer is left to the orchestrator (no existing test exercises a
 * post-reset read, but the parser must not crash).
 */
class ResetStreamFrame(
    val streamId: Long,
    val applicationErrorCode: Long,
    val finalSize: Long,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.RESET_STREAM.toInt())
        out.writeVarint(streamId)
        out.writeVarint(applicationErrorCode)
        out.writeVarint(finalSize)
    }
}

/**
 * RFC 9000 §19.5 — peer asks us to stop sending on a stream we own. We don't
 * model an outbound abort yet (MoQ-minimal scope), so we accept the frame
 * for survival and let the application read [streamId]/[applicationErrorCode]
 * if it ever wires a handler.
 */
class StopSendingFrame(
    val streamId: Long,
    val applicationErrorCode: Long,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.STOP_SENDING.toInt())
        out.writeVarint(streamId)
        out.writeVarint(applicationErrorCode)
    }
}

/**
 * RFC 9000 §19.7 — server provides a token for use in a future Initial. We
 * don't do 0-RTT or stateful resumption, so the token is dropped, but the
 * frame MUST decode without killing the connection.
 */
class NewTokenFrame(
    val token: ByteArray,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.NEW_TOKEN.toInt())
        out.writeVarint(token.size.toLong())
        out.writeBytes(token)
    }
}

class MaxDataFrame(
    val maxData: Long,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.MAX_DATA.toInt())
        out.writeVarint(maxData)
    }
}

class MaxStreamDataFrame(
    val streamId: Long,
    val maxStreamData: Long,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.MAX_STREAM_DATA.toInt())
        out.writeVarint(streamId)
        out.writeVarint(maxStreamData)
    }
}

class MaxStreamsFrame(
    val bidi: Boolean,
    val maxStreams: Long,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(if (bidi) FrameType.MAX_STREAMS_BIDI.toInt() else FrameType.MAX_STREAMS_UNI.toInt())
        out.writeVarint(maxStreams)
    }
}

/**
 * RFC 9000 §19.14. Sent by an endpoint that wants to open a stream of the
 * given direction but has exhausted its peer-granted cap. Carries the
 * highest stream id the sender currently believes it is allowed to open
 * (i.e. the count it received in the last MAX_STREAMS frame for that
 * direction).
 *
 * Required for proper flow-control bookkeeping — without it, the peer
 * has no signal that we're starved and may delay extending credit. The
 * frame itself is informational; it doesn't mutate the cap.
 */
class StreamsBlockedFrame(
    val bidi: Boolean,
    val streamLimit: Long,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(if (bidi) FrameType.STREAMS_BLOCKED_BIDI.toInt() else FrameType.STREAMS_BLOCKED_UNI.toInt())
        out.writeVarint(streamLimit)
    }
}

class DatagramFrame(
    val data: ByteArray,
    val explicitLength: Boolean = true,
) : Frame() {
    override fun encode(out: QuicWriter) {
        if (explicitLength) {
            out.writeByte(FrameType.DATAGRAM_LEN.toInt())
            out.writeVarint(data.size.toLong())
        } else {
            out.writeByte(FrameType.DATAGRAM.toInt())
        }
        out.writeBytes(data)
    }
}

class HandshakeDoneFrame : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.HANDSHAKE_DONE.toInt())
    }
}

class NewConnectionIdFrame(
    val sequenceNumber: Long,
    val retirePriorTo: Long,
    val connectionId: ByteArray,
    val statelessResetToken: ByteArray,
) : Frame() {
    override fun encode(out: QuicWriter) {
        out.writeByte(FrameType.NEW_CONNECTION_ID.toInt())
        out.writeVarint(sequenceNumber)
        out.writeVarint(retirePriorTo)
        require(statelessResetToken.size == 16) { "stateless reset token must be 16 bytes" }
        out.writeByte(connectionId.size)
        out.writeBytes(connectionId)
        out.writeBytes(statelessResetToken)
    }
}

/**
 * Decode a stream of frames from [data]. Padding bytes (0x00) are silently
 * absorbed. Unknown frame types raise [QuicCodecException] (per RFC 9000 §19
 * we MUST close the connection with FRAME_ENCODING_ERROR).
 */
fun decodeFrames(data: ByteArray): List<Frame> {
    val out = mutableListOf<Frame>()
    val r = QuicReader(data)
    while (r.hasMore()) {
        // Frame types are varints per RFC 9000 §12.4; for codes < 0x40 the
        // varint is a single byte, but we MUST decode as varint so future
        // extension frame types ≥ 0x40 are correctly recognized (or rejected).
        val type = r.readVarint()
        if (type == FrameType.PADDING) continue

        when {
            type == FrameType.PING -> {
                out += PingFrame
            }

            type == FrameType.ACK || type == FrameType.ACK_ECN -> {
                val largest = r.readVarint()
                val delay = r.readVarint()
                val numRangesRaw = r.readVarint()
                // Cap range count at remaining bytes — each range needs ≥ 2 varint bytes.
                val numRanges = boundedRangeCount(numRangesRaw, r.remaining)
                val firstRange = r.readVarint()
                val ranges = ArrayList<AckRange>(numRanges)
                for (i in 0 until numRanges) {
                    val gap = r.readVarint()
                    val len = r.readVarint()
                    ranges += AckRange(gap, len)
                }
                if (type == FrameType.ACK_ECN) {
                    // Skip ECT0, ECT1, CE counts.
                    r.readVarint()
                    r.readVarint()
                    r.readVarint()
                }
                out += AckFrame(largest, delay, firstRange, ranges)
            }

            type == FrameType.RESET_STREAM -> {
                val streamId = r.readVarint()
                val errorCode = r.readVarint()
                val finalSize = r.readVarint()
                out += ResetStreamFrame(streamId, errorCode, finalSize)
            }

            type == FrameType.STOP_SENDING -> {
                val streamId = r.readVarint()
                val errorCode = r.readVarint()
                out += StopSendingFrame(streamId, errorCode)
            }

            type == FrameType.CRYPTO -> {
                val offset = r.readVarint()
                val len = boundedLength(r.readVarint(), r.remaining, "CRYPTO")
                val data2 = r.readBytes(len)
                out += CryptoFrame(offset, data2)
            }

            type == FrameType.NEW_TOKEN -> {
                val tokenLen = boundedLength(r.readVarint(), r.remaining, "NEW_TOKEN")
                out += NewTokenFrame(r.readBytes(tokenLen))
            }

            type in FrameType.STREAM_BASE..(FrameType.STREAM_BASE or 0x07) -> {
                val flags = (type - FrameType.STREAM_BASE)
                val hasOff = (flags and FrameType.STREAM_OFF_BIT) != 0L
                val hasLen = (flags and FrameType.STREAM_LEN_BIT) != 0L
                val fin = (flags and FrameType.STREAM_FIN_BIT) != 0L
                val streamId = r.readVarint()
                val offset = if (hasOff) r.readVarint() else 0L
                val payload =
                    if (hasLen) {
                        val ln = boundedLength(r.readVarint(), r.remaining, "STREAM")
                        r.readBytes(ln)
                    } else {
                        // "remainder of the packet"
                        r.readBytes(r.remaining)
                    }
                out += StreamFrame(streamId, offset, payload, fin, hasLen)
            }

            type == FrameType.MAX_DATA -> {
                out += MaxDataFrame(r.readVarint())
            }

            type == FrameType.MAX_STREAM_DATA -> {
                out += MaxStreamDataFrame(r.readVarint(), r.readVarint())
            }

            type == FrameType.MAX_STREAMS_BIDI -> {
                out += MaxStreamsFrame(true, r.readVarint())
            }

            type == FrameType.MAX_STREAMS_UNI -> {
                out += MaxStreamsFrame(false, r.readVarint())
            }

            type == FrameType.DATA_BLOCKED -> {
                r.readVarint()
            }

            // ignored
            type == FrameType.STREAM_DATA_BLOCKED -> {
                r.readVarint()
                r.readVarint()
            }

            type == FrameType.STREAMS_BLOCKED_BIDI -> {
                out += StreamsBlockedFrame(bidi = true, streamLimit = r.readVarint())
            }

            type == FrameType.STREAMS_BLOCKED_UNI -> {
                out += StreamsBlockedFrame(bidi = false, streamLimit = r.readVarint())
            }

            type == FrameType.NEW_CONNECTION_ID -> {
                val seq = r.readVarint()
                val retire = r.readVarint()
                val cidLen = r.readByte()
                if (cidLen !in 1..20) {
                    throw QuicCodecException("NEW_CONNECTION_ID cidLen out of range: $cidLen")
                }
                val cid = r.readBytes(cidLen)
                val token = r.readBytes(16)
                out += NewConnectionIdFrame(seq, retire, cid, token)
            }

            type == FrameType.RETIRE_CONNECTION_ID -> {
                r.readVarint()
            }

            type == FrameType.PATH_CHALLENGE -> {
                r.readBytes(8)
            }

            type == FrameType.PATH_RESPONSE -> {
                r.readBytes(8)
            }

            type == FrameType.CONNECTION_CLOSE_TRANSPORT -> {
                val err = r.readVarint()
                val frameType2 = r.readVarint()
                val reasonLen = boundedLength(r.readVarint(), r.remaining, "CONNECTION_CLOSE reason")
                val reason = r.readBytes(reasonLen).decodeToString()
                out += ConnectionCloseFrame(err, frameType2, reason)
            }

            type == FrameType.CONNECTION_CLOSE_APP -> {
                val err = r.readVarint()
                val reasonLen = boundedLength(r.readVarint(), r.remaining, "CONNECTION_CLOSE reason")
                val reason = r.readBytes(reasonLen).decodeToString()
                out += ConnectionCloseFrame(err, null, reason)
            }

            type == FrameType.HANDSHAKE_DONE -> {
                out += HandshakeDoneFrame()
            }

            type == FrameType.DATAGRAM -> {
                val payload = r.readBytes(r.remaining)
                out += DatagramFrame(payload, explicitLength = false)
            }

            type == FrameType.DATAGRAM_LEN -> {
                val ln = boundedLength(r.readVarint(), r.remaining, "DATAGRAM")
                out += DatagramFrame(r.readBytes(ln), explicitLength = true)
            }

            else -> {
                throw QuicCodecException("unknown frame type 0x${type.toString(16)}")
            }
        }
    }
    return out
}

/** Encode a list of frames to bytes (no padding inserted). */
fun encodeFrames(frames: List<Frame>): ByteArray {
    val w = QuicWriter()
    for (f in frames) f.encode(w)
    return w.toByteArray()
}

/**
 * Validate that a varint length value can be represented as a non-negative
 * Int and fits within the remaining buffer. Hostile peers may send 62-bit
 * lengths that, if uncritically truncated by `.toInt()`, become negative or
 * absurdly large and lead to a crash or DoS allocation.
 */
private fun boundedLength(
    value: Long,
    remaining: Int,
    field: String,
): Int {
    if (value < 0L || value > remaining) {
        throw QuicCodecException("$field length $value out of bounds (remaining=$remaining)")
    }
    return value.toInt()
}

/** Same as [boundedLength] but for an ACK frame range count (each range needs ≥ 2 varint bytes). */
private fun boundedRangeCount(
    value: Long,
    remaining: Int,
): Int {
    val maxRanges = remaining / 2 // varint min 1 byte; 2 varints per range
    if (value < 0L || value > maxRanges) {
        throw QuicCodecException("ACK range count $value out of bounds (remaining=$remaining)")
    }
    return value.toInt()
}
