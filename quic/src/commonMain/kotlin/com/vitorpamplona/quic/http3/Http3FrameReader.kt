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
package com.vitorpamplona.quic.http3

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.Varint

/**
 * Stateful HTTP/3 frame reader (RFC 9114 §7).
 *
 * Each HTTP/3 frame on a stream is `(varint type)(varint length)(body[length])`.
 * The reader buffers inbound bytes and yields complete frames; partial frames
 * stay buffered until enough bytes arrive.
 *
 * Use one [Http3FrameReader] per HTTP/3 stream. Feed bytes via [push]; drain
 * frames via [next] until it returns null.
 *
 * Stream-context enforcement (RFC 9114 §7.2):
 *  * On the [StreamContext.CONTROL] stream, the FIRST frame MUST be SETTINGS
 *    (else H3_MISSING_SETTINGS); DATA / HEADERS / PUSH_PROMISE are not
 *    allowed (H3_FRAME_UNEXPECTED).
 *  * On a [StreamContext.REQUEST] stream, SETTINGS / GOAWAY / MAX_PUSH_ID /
 *    CANCEL_PUSH are not allowed (H3_FRAME_UNEXPECTED).
 *  * On a [StreamContext.PUSH] stream, SETTINGS / GOAWAY / MAX_PUSH_ID /
 *    PUSH_PROMISE / CANCEL_PUSH are not allowed.
 *  * Reserved frame types `0x02, 0x06, 0x08, 0x09` are explicitly rejected
 *    (H3_FRAME_UNEXPECTED) per RFC 9114 §7.2.8 — only the
 *    `0x21 + 0x1f * N` reserved-greasing pattern is ignored as Unknown.
 *  * On [StreamContext.WT_BIDI_DATA] / [StreamContext.WT_UNI_DATA]
 *    (post-prefix WebTransport stream payload), no HTTP/3 framing applies
 *    — the caller never feeds bytes through here. Provided for symmetry.
 *
 * Memory safety:
 *  * Pending buffered bytes are capped at [maxPendingBytes]; a peer that
 *    streams a partial-frame prefix without ever delivering the body
 *    cannot pin unbounded heap. Exceeding the cap throws
 *    [QuicCodecException], which the caller surfaces as a connection
 *    error.
 *  * Per-frame body length is capped by [maxFrameBodyBytes]. A peer that
 *    advertises a 2 GiB length on the wire is rejected before the
 *    `ByteArray(len.toInt())` allocation.
 */
class Http3FrameReader(
    private val context: StreamContext = StreamContext.UNCHECKED,
    private val maxPendingBytes: Int = DEFAULT_MAX_PENDING_BYTES,
    private val maxFrameBodyBytes: Int = DEFAULT_MAX_FRAME_BODY_BYTES,
) {
    private var buf: ByteArray = ByteArray(0)
    private var pos: Int = 0
    private var framesEmitted: Int = 0

    /** Bytes currently buffered awaiting a complete frame. Diagnostic; tests use this. */
    val bufferedBytes: Int get() = buf.size - pos

    /**
     * Stream context for [Http3FrameReader] frame validation. The reader is
     * stateless about which kind of stream it's running on unless the
     * caller tells it. [UNCHECKED] preserves pre-fix behaviour for tests
     * that intentionally feed mixed frames; production callers should
     * specify the actual context.
     */
    enum class StreamContext {
        /** No per-context validation (pre-fix behaviour, tests). */
        UNCHECKED,

        /** RFC 9114 §6.2.1 server CONTROL unidi stream. */
        CONTROL,

        /** RFC 9114 §6.1 client/server REQUEST bidi stream. */
        REQUEST,

        /** RFC 9114 §6.2.2 PUSH unidi stream. */
        PUSH,

        /**
         * Post-prefix WT bidi/uni stream payload. WebTransport bytes don't
         * use HTTP/3 framing — this exists so the type system can prevent
         * accidental use of [Http3FrameReader] on raw WT data.
         */
        WT_BIDI_DATA,
        WT_UNI_DATA,
    }

    fun push(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        // Amortized compaction: only shift bytes down when the consumed
        // prefix is at least half the buffer. Otherwise we'd do O(N) memcpy
        // on every chunk → O(N²) total over a long stream.
        if (pos * 2 > buf.size) {
            buf = buf.copyOfRange(pos, buf.size)
            pos = 0
        }
        val newPending = (buf.size - pos).toLong() + bytes.size.toLong()
        if (newPending > maxPendingBytes) {
            throw QuicCodecException(
                "HTTP/3 frame reader buffer would exceed cap " +
                    "($newPending > $maxPendingBytes); peer is streaming a partial " +
                    "frame without delivering the body — likely H3_EXCESSIVE_LOAD",
            )
        }
        val combined = ByteArray(buf.size + bytes.size)
        buf.copyInto(combined, 0)
        bytes.copyInto(combined, buf.size)
        buf = combined
    }

    /** Pop the next complete frame, or null if the buffer doesn't contain one yet. */
    fun next(): Http3Frame? {
        val typeRes = Varint.decode(buf, pos) ?: return null
        val typeEnd = pos + typeRes.bytesConsumed
        val lenRes = Varint.decode(buf, typeEnd) ?: return null
        val bodyStart = typeEnd + lenRes.bytesConsumed
        val len = lenRes.value
        if (len < 0 || len > maxFrameBodyBytes.toLong()) {
            throw QuicCodecException(
                "HTTP/3 frame length out of range: $len (cap $maxFrameBodyBytes)",
            )
        }
        val bodyEnd = bodyStart + len.toInt()
        if (bodyEnd > buf.size) return null // not all body bytes present yet
        val type = typeRes.value
        validateFrameType(type)
        val body = buf.copyOfRange(bodyStart, bodyEnd)
        pos = bodyEnd
        framesEmitted++
        return when (type) {
            Http3FrameType.DATA -> Http3Frame.Data(body)
            Http3FrameType.HEADERS -> Http3Frame.Headers(body)
            Http3FrameType.SETTINGS -> Http3Frame.Settings(Http3Settings.decodeBody(body))
            Http3FrameType.GOAWAY -> Http3Frame.Goaway(body)
            else -> Http3Frame.Unknown(type, body)
        }
    }

    /**
     * Enforce RFC 9114 §7.2 per-stream-context rules. Throws
     * [QuicCodecException] (mapped upstream to H3_FRAME_UNEXPECTED /
     * H3_MISSING_SETTINGS) on violation.
     */
    private fun validateFrameType(type: Long) {
        // RFC 9114 §7.2.8: explicit reserved-and-forbidden frame types.
        // Distinct from the reserved-greasing pattern `0x21 + 0x1f * N`
        // (which is allowed and falls through as Unknown).
        if (type == 0x02L || type == 0x06L || type == 0x08L || type == 0x09L) {
            throw QuicCodecException(
                "H3_FRAME_UNEXPECTED: reserved HTTP/3 frame type 0x${type.toString(16)}",
            )
        }
        when (context) {
            StreamContext.UNCHECKED -> {
                Unit
            }

            StreamContext.CONTROL -> {
                // §7.2.4: SETTINGS MUST be the first frame on the
                // control stream; any non-SETTINGS first frame =>
                // H3_MISSING_SETTINGS.
                if (framesEmitted == 0 && type != Http3FrameType.SETTINGS) {
                    throw QuicCodecException(
                        "H3_MISSING_SETTINGS: first CONTROL-stream frame was 0x${type.toString(16)}",
                    )
                }
                // §7.2.4: a second SETTINGS is also forbidden.
                if (framesEmitted > 0 && type == Http3FrameType.SETTINGS) {
                    throw QuicCodecException(
                        "H3_FRAME_UNEXPECTED: duplicate SETTINGS on CONTROL stream",
                    )
                }
                // §7.2.1, §7.2.2, §7.2.5: DATA / HEADERS / PUSH_PROMISE
                // are forbidden on the control stream.
                if (type == Http3FrameType.DATA ||
                    type == Http3FrameType.HEADERS ||
                    type == Http3FrameType.PUSH_PROMISE
                ) {
                    throw QuicCodecException(
                        "H3_FRAME_UNEXPECTED: 0x${type.toString(16)} on CONTROL stream",
                    )
                }
            }

            StreamContext.REQUEST -> {
                // §7.2.4 / §7.2.6 / §7.2.7: control-only frames are
                // forbidden on request streams.
                if (type == Http3FrameType.SETTINGS ||
                    type == Http3FrameType.GOAWAY ||
                    type == Http3FrameType.MAX_PUSH_ID ||
                    type == Http3FrameType.CANCEL_PUSH
                ) {
                    throw QuicCodecException(
                        "H3_FRAME_UNEXPECTED: 0x${type.toString(16)} on REQUEST stream",
                    )
                }
            }

            StreamContext.PUSH -> {
                if (type == Http3FrameType.SETTINGS ||
                    type == Http3FrameType.GOAWAY ||
                    type == Http3FrameType.MAX_PUSH_ID ||
                    type == Http3FrameType.PUSH_PROMISE ||
                    type == Http3FrameType.CANCEL_PUSH
                ) {
                    throw QuicCodecException(
                        "H3_FRAME_UNEXPECTED: 0x${type.toString(16)} on PUSH stream",
                    )
                }
            }

            StreamContext.WT_BIDI_DATA, StreamContext.WT_UNI_DATA -> {
                // WebTransport stream payload doesn't use HTTP/3 framing.
                // The caller shouldn't be feeding bytes through this
                // reader for those contexts; flag any attempt loudly.
                throw QuicCodecException(
                    "WebTransport stream payload routed through Http3FrameReader",
                )
            }
        }
    }

    companion object {
        /**
         * Default cap on pending unparsed bytes (1 MiB). This is the
         * "frame in flight but body not yet complete" headroom — more
         * than enough for a legitimate HEADERS / SETTINGS / WT capsule
         * preamble plus a single in-progress DATA chunk, far less than
         * the heap a hostile peer could pin.
         */
        const val DEFAULT_MAX_PENDING_BYTES: Int = 1 shl 20

        /**
         * Default cap on a single HTTP/3 frame body (16 MiB). Exceeds
         * any plausible HEADERS payload (RFC 9114 only ever expects
         * tens of KiB) and any legitimate per-frame DATA chunk that
         * fits inside QUIC's per-packet payload budget. Setting this
         * lower than [DEFAULT_MAX_PENDING_BYTES] would require
         * splitting; setting it equal keeps the two caps aligned for
         * single-frame stalls.
         */
        const val DEFAULT_MAX_FRAME_BODY_BYTES: Int = 16 shl 20
    }
}

/** A parsed HTTP/3 frame. */
sealed class Http3Frame {
    /** RFC 9114 §7.2.1 DATA frame body. */
    data class Data(
        val body: ByteArray,
    ) : Http3Frame()

    /** RFC 9114 §7.2.2 HEADERS frame body — QPACK-encoded field section. */
    data class Headers(
        val qpackPayload: ByteArray,
    ) : Http3Frame()

    /** RFC 9114 §7.2.4 SETTINGS frame parsed body. */
    data class Settings(
        val settings: Http3Settings,
    ) : Http3Frame()

    /** RFC 9114 §7.2.6 GOAWAY frame body — single varint stream id (raw). */
    data class Goaway(
        val body: ByteArray,
    ) : Http3Frame()

    /** Frame whose type is unknown to us; per RFC 9114 §9, ignore unless on a reserved type. */
    data class Unknown(
        val type: Long,
        val body: ByteArray,
    ) : Http3Frame()
}
