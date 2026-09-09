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
package com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamQuicPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.QuicVarInt
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.addLengthPrefixed

/**
 * ALPN identifiers for `transports/quic.md`.
 *
 * QUIC requires ALPN on every connection, and the binding gives each delivery
 * mode its own so the two can diverge without a version negotiation of their
 * own: a peer that only speaks one simply fails the handshake.
 *
 * This is a raw QUIC binding, not WebTransport — there is no HTTP/3 layer, no
 * Extended CONNECT and no `:protocol` pseudo-header. The records ride directly
 * on QUIC streams.
 */
object MarmotQuicAlpn {
    /** Broker-relayed delivery — the v1 discovery mechanism. */
    val BROKER = "marmot.quic_broker.v1".encodeToByteArray()

    /** Direct point-to-point delivery, where the sender already knows the receiver's endpoint. */
    val DIRECT = "marmot.quic_stream.v1".encodeToByteArray()
}

/** Which side of a broker room a control envelope claims. */
enum class BrokerControlType(
    val code: Int,
) {
    /** Claims the room and streams records into it. Client-opened unidirectional stream. */
    PUBLISH(1),

    /** Joins the room and reads the fan-out. Client-opened bidirectional stream. */
    SUBSCRIBE(2),
    ;

    companion object {
        fun fromCode(code: Int): BrokerControlType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The first frame on a broker stream, framed exactly like a record frame.
 *
 * ```
 * struct {
 *   opaque marmot_broker<1..255>;   // ASCII "marmot.quic_broker.v1"
 *   BrokerControlType control_type; // uint8
 *   opaque stream_id<1..64>;
 *   opaque start_event_id<1..64>;
 * } QuicBrokerControlEnvelopeV1;
 * ```
 *
 * `marmot_broker` is length-prefixed rather than a fixed array on purpose: a
 * future protocol string of a different length still decodes for an old
 * reader, which can then reject it cleanly instead of misparsing the fields
 * behind it.
 *
 * The broker is an untrusted forwarder. Everything it learns is in here — the
 * routing pair — plus ciphertext; it cannot read, author or alter preview
 * plaintext.
 */
class QuicBrokerControlEnvelopeV1(
    val controlType: BrokerControlType,
    val streamId: ByteArray,
    val startEventId: ByteArray,
) {
    init {
        require(streamId.size in 1..MAX_ID_LEN) { "broker control stream_id must be 1..$MAX_ID_LEN bytes" }
        require(startEventId.size in 1..MAX_ID_LEN) { "broker control start_event_id must be 1..$MAX_ID_LEN bytes" }
    }

    fun encode(): ByteArray {
        val out = ArrayList<Byte>()
        out.addLengthPrefixed(PROTOCOL)
        out.add(controlType.code.toByte())
        out.addLengthPrefixed(streamId)
        out.addLengthPrefixed(startEventId)
        return out.toByteArray()
    }

    companion object {
        /** The exact 21 ASCII bytes a broker matches on. */
        val PROTOCOL = "marmot.quic_broker.v1".encodeToByteArray()

        const val MAX_ID_LEN = 64

        fun decode(bytes: ByteArray): QuicBrokerControlEnvelopeV1 {
            var at = 0

            val protocolLen = QuicVarInt.decode(bytes, at)
            at += protocolLen.length
            require(at + protocolLen.value <= bytes.size) { "broker control envelope is truncated while reading marmot_broker" }
            val protocol = bytes.copyOfRange(at, at + protocolLen.value.toInt())
            at += protocolLen.value.toInt()
            require(protocol.contentEquals(PROTOCOL)) {
                "broker control envelope names another protocol: ${protocol.decodeToString()}"
            }

            require(at < bytes.size) { "broker control envelope is truncated while reading control_type" }
            val controlType =
                BrokerControlType.fromCode(bytes[at].toInt() and 0xff)
                    ?: throw IllegalArgumentException("unknown broker control_type: ${bytes[at].toInt() and 0xff}")
            at += 1

            val streamIdLen = QuicVarInt.decode(bytes, at)
            at += streamIdLen.length
            require(at + streamIdLen.value <= bytes.size) { "broker control envelope is truncated while reading stream_id" }
            val streamId = bytes.copyOfRange(at, at + streamIdLen.value.toInt())
            at += streamIdLen.value.toInt()

            val startEventIdLen = QuicVarInt.decode(bytes, at)
            at += startEventIdLen.length
            require(at + startEventIdLen.value <= bytes.size) {
                "broker control envelope is truncated while reading start_event_id"
            }
            val startEventId = bytes.copyOfRange(at, at + startEventIdLen.value.toInt())
            at += startEventIdLen.value.toInt()

            // "A broker MUST reject an envelope whose frame carries trailing
            // bytes after the envelope" — an envelope with a record glued on
            // is a different message than the one we would be acting on.
            require(at == bytes.size) { "broker control envelope has ${bytes.size - at} trailing byte(s)" }

            return QuicBrokerControlEnvelopeV1(controlType, streamId, startEventId)
        }
    }
}

/**
 * Length-delimited record frames on a delivery stream:
 * `uint32 frame_len || AgentTextStreamRecordV1[frame_len]`.
 *
 * The 4-byte big-endian prefix is the transport's only framing; QUIC gives an
 * ordered byte stream, not messages.
 */
object AgentTextStreamFraming {
    /**
     * Header + AEAD-tag allowance the binding adds on top of a group's
     * `max_plaintext_frame_len` when sizing a frame.
     */
    const val FRAME_OVERHEAD_ALLOWANCE = 1024

    /**
     * The cap a broker enforces. It cannot read group state, so it uses the
     * v1 component's largest legal `max_plaintext_frame_len` plus the same
     * allowance. A client that knows the group's actual policy uses that
     * instead, which is always smaller.
     */
    const val BROKER_MAX_FRAME_LEN = AgentTextStreamQuicPolicyV1.MAX_PLAINTEXT_FRAME_LEN.toInt() + FRAME_OVERHEAD_ALLOWANCE

    fun frame(record: AgentTextStreamRecordV1): ByteArray {
        val encoded = record.encode()
        require(encoded.size <= BROKER_MAX_FRAME_LEN) { "agent text stream frame is over the transport cap" }
        val out = ByteArray(4 + encoded.size)
        out[0] = ((encoded.size shr 24) and 0xff).toByte()
        out[1] = ((encoded.size shr 16) and 0xff).toByte()
        out[2] = ((encoded.size shr 8) and 0xff).toByte()
        out[3] = (encoded.size and 0xff).toByte()
        encoded.copyInto(out, 4)
        return out
    }

    /**
     * Incremental frame reader over a QUIC stream's bytes.
     *
     * A QUIC stream hands over whatever arrived, so a record can straddle any
     * number of reads; the reader buffers until a whole frame is present and
     * returns the records it completed.
     *
     * @param maxPlaintextFrameLen the group's policy value when the caller
     *   knows it. A receiver that knows the policy must reject anything above
     *   it; without one the broker's blind cap applies.
     */
    class Reader(
        maxPlaintextFrameLen: Long? = null,
    ) {
        private val maxFrameLen =
            maxPlaintextFrameLen?.let { (it + FRAME_OVERHEAD_ALLOWANCE).coerceAtMost(BROKER_MAX_FRAME_LEN.toLong()).toInt() }
                ?: BROKER_MAX_FRAME_LEN

        private var buffer = ByteArray(0)

        /** The stream id of the first record, which every later record must repeat. */
        var pinnedStreamId: ByteArray? = null
            private set

        /** Buffer the bytes and return whatever records they completed, in order. */
        fun push(chunk: ByteArray): List<AgentTextStreamRecordV1> {
            buffer += chunk
            val out = mutableListOf<AgentTextStreamRecordV1>()
            while (true) {
                if (buffer.size < 4) return out
                val frameLen =
                    ((buffer[0].toLong() and 0xff) shl 24) or
                        ((buffer[1].toLong() and 0xff) shl 16) or
                        ((buffer[2].toLong() and 0xff) shl 8) or
                        (buffer[3].toLong() and 0xff)
                require(frameLen <= maxFrameLen) { "agent text stream frame_len $frameLen is over the cap $maxFrameLen" }
                if (buffer.size < 4 + frameLen) return out

                val record = AgentTextStreamRecordV1.decode(buffer.copyOfRange(4, (4 + frameLen).toInt()))
                buffer = buffer.copyOfRange((4 + frameLen).toInt(), buffer.size)

                val pinned = pinnedStreamId
                if (pinned == null) {
                    pinnedStreamId = record.streamId
                } else {
                    // "A reader MUST reject records whose stream_id differs
                    // from the first record's stream_id on the same stream."
                    require(record.streamId.contentEquals(pinned)) {
                        "agent text stream record carries a different stream_id than the stream it arrived on"
                    }
                }
                out.add(record)
            }
        }
    }
}

/**
 * One `["broker", "quic://<authority>"]` endpoint candidate.
 *
 * Candidates are advisory routing hints, not authenticated stream content: a
 * candidate that points somewhere hostile still cannot forge a record, because
 * every record is authenticated under the group-derived record key. So a
 * candidate that does not parse, does not connect, or serves a different
 * stream is skipped rather than treated as an error — hence [parse] returning
 * null instead of throwing.
 */
class QuicEndpointCandidate(
    val host: String,
    val port: Int,
    /**
     * True for a DNS hostname, false for an IPv4/IPv6 literal. Decides the
     * trust model: a name gets normal DNS-name/SNI validation, a literal is
     * matched against an `iPAddress` subjectAltName and gets no SNI at all.
     */
    val isDnsName: Boolean,
) {
    /** The SNI to send, or null for an IP literal (which must not carry one). */
    val serverNameIndication: String? get() = host.takeIf { isDnsName }

    companion object {
        const val SCHEME = "quic://"

        /** `quic://` + at most 505 authority bytes. */
        const val MAX_CANDIDATE_BYTES = 512

        fun parse(candidate: String): QuicEndpointCandidate? {
            val bytes = candidate.encodeToByteArray()
            if (bytes.size > MAX_CANDIDATE_BYTES) return null
            // Round-tripping catches a candidate that was not valid UTF-8 to
            // begin with: the decoder substitutes U+FFFD and the bytes differ.
            if (!bytes.decodeToString().encodeToByteArray().contentEquals(bytes)) return null
            if (!candidate.startsWith(SCHEME)) return null

            // "The authority ends at the first /, ? or #; everything after
            // that character is ignored."
            val rest = candidate.substring(SCHEME.length)
            val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
            if (authority.isEmpty()) return null

            val host: String
            val portText: String
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                host = authority.substring(1, close)
                if (authority.getOrNull(close + 1) != ':') return null
                portText = authority.substring(close + 2)
                if (!host.contains(':')) return null
            } else {
                val colon = authority.lastIndexOf(':')
                if (colon <= 0) return null
                host = authority.substring(0, colon)
                portText = authority.substring(colon + 1)
                if (host.contains(':')) return null
            }

            if (host.isEmpty()) return null
            val port = portText.toIntOrNull() ?: return null
            if (port !in 1..65535) return null

            return QuicEndpointCandidate(host, port, isDnsName = !looksLikeIpLiteral(host))
        }

        private fun looksLikeIpLiteral(host: String): Boolean {
            if (host.contains(':')) return true
            val parts = host.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && (part.toIntOrNull() ?: 256) <= 255
            }
        }
    }
}
