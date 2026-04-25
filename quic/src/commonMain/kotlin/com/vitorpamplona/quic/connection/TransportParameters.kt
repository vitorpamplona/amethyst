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

import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.Varint

/**
 * QUIC transport parameter identifiers per RFC 9000 §18.2 + RFC 9221 (datagrams).
 *
 * Each parameter is carried as `(id varint)(length varint)(value)`.
 */
object TransportParameterId {
    const val ORIGINAL_DESTINATION_CONNECTION_ID: Long = 0x00
    const val MAX_IDLE_TIMEOUT: Long = 0x01
    const val STATELESS_RESET_TOKEN: Long = 0x02
    const val MAX_UDP_PAYLOAD_SIZE: Long = 0x03
    const val INITIAL_MAX_DATA: Long = 0x04
    const val INITIAL_MAX_STREAM_DATA_BIDI_LOCAL: Long = 0x05
    const val INITIAL_MAX_STREAM_DATA_BIDI_REMOTE: Long = 0x06
    const val INITIAL_MAX_STREAM_DATA_UNI: Long = 0x07
    const val INITIAL_MAX_STREAMS_BIDI: Long = 0x08
    const val INITIAL_MAX_STREAMS_UNI: Long = 0x09
    const val ACK_DELAY_EXPONENT: Long = 0x0a
    const val MAX_ACK_DELAY: Long = 0x0b
    const val DISABLE_ACTIVE_MIGRATION: Long = 0x0c
    const val PREFERRED_ADDRESS: Long = 0x0d
    const val ACTIVE_CONNECTION_ID_LIMIT: Long = 0x0e
    const val INITIAL_SOURCE_CONNECTION_ID: Long = 0x0f
    const val RETRY_SOURCE_CONNECTION_ID: Long = 0x10

    /** RFC 9221 — `max_datagram_frame_size`. */
    const val MAX_DATAGRAM_FRAME_SIZE: Long = 0x20
}

/**
 * QUIC transport parameters as exchanged inside the TLS QUIC transport_params
 * extension.
 *
 * Only the parameters we actually advertise / interpret are surfaced as named
 * fields. Unknown parameters are kept in [unknown] to be re-emitted verbatim
 * if needed (we don't currently echo).
 */
data class TransportParameters(
    val initialMaxData: Long? = null,
    val initialMaxStreamDataBidiLocal: Long? = null,
    val initialMaxStreamDataBidiRemote: Long? = null,
    val initialMaxStreamDataUni: Long? = null,
    val initialMaxStreamsBidi: Long? = null,
    val initialMaxStreamsUni: Long? = null,
    val maxIdleTimeoutMillis: Long? = null,
    val maxUdpPayloadSize: Long? = null,
    val ackDelayExponent: Long? = null,
    val maxAckDelay: Long? = null,
    val activeConnectionIdLimit: Long? = null,
    val disableActiveMigration: Boolean = false,
    val initialSourceConnectionId: ByteArray? = null,
    val originalDestinationConnectionId: ByteArray? = null,
    val retrySourceConnectionId: ByteArray? = null,
    val statelessResetToken: ByteArray? = null,
    val maxDatagramFrameSize: Long? = null,
    val unknown: Map<Long, ByteArray> = emptyMap(),
) {
    fun encode(): ByteArray {
        val w = QuicWriter()

        fun writeVarintParam(
            id: Long,
            value: Long,
        ) {
            w.writeVarint(id)
            w.writeVarint(Varint.size(value).toLong())
            w.writeVarint(value)
        }

        fun writeBytesParam(
            id: Long,
            value: ByteArray,
        ) {
            w.writeVarint(id)
            w.writeVarint(value.size.toLong())
            w.writeBytes(value)
        }

        fun writeFlagParam(id: Long) {
            w.writeVarint(id)
            w.writeVarint(0L)
        }

        initialMaxData?.let { writeVarintParam(TransportParameterId.INITIAL_MAX_DATA, it) }
        initialMaxStreamDataBidiLocal?.let { writeVarintParam(TransportParameterId.INITIAL_MAX_STREAM_DATA_BIDI_LOCAL, it) }
        initialMaxStreamDataBidiRemote?.let { writeVarintParam(TransportParameterId.INITIAL_MAX_STREAM_DATA_BIDI_REMOTE, it) }
        initialMaxStreamDataUni?.let { writeVarintParam(TransportParameterId.INITIAL_MAX_STREAM_DATA_UNI, it) }
        initialMaxStreamsBidi?.let { writeVarintParam(TransportParameterId.INITIAL_MAX_STREAMS_BIDI, it) }
        initialMaxStreamsUni?.let { writeVarintParam(TransportParameterId.INITIAL_MAX_STREAMS_UNI, it) }
        maxIdleTimeoutMillis?.let { writeVarintParam(TransportParameterId.MAX_IDLE_TIMEOUT, it) }
        maxUdpPayloadSize?.let { writeVarintParam(TransportParameterId.MAX_UDP_PAYLOAD_SIZE, it) }
        ackDelayExponent?.let { writeVarintParam(TransportParameterId.ACK_DELAY_EXPONENT, it) }
        maxAckDelay?.let { writeVarintParam(TransportParameterId.MAX_ACK_DELAY, it) }
        activeConnectionIdLimit?.let { writeVarintParam(TransportParameterId.ACTIVE_CONNECTION_ID_LIMIT, it) }
        if (disableActiveMigration) writeFlagParam(TransportParameterId.DISABLE_ACTIVE_MIGRATION)
        initialSourceConnectionId?.let { writeBytesParam(TransportParameterId.INITIAL_SOURCE_CONNECTION_ID, it) }
        originalDestinationConnectionId?.let { writeBytesParam(TransportParameterId.ORIGINAL_DESTINATION_CONNECTION_ID, it) }
        retrySourceConnectionId?.let { writeBytesParam(TransportParameterId.RETRY_SOURCE_CONNECTION_ID, it) }
        statelessResetToken?.let { writeBytesParam(TransportParameterId.STATELESS_RESET_TOKEN, it) }
        maxDatagramFrameSize?.let { writeVarintParam(TransportParameterId.MAX_DATAGRAM_FRAME_SIZE, it) }
        for ((id, bytes) in unknown) writeBytesParam(id, bytes)

        return w.toByteArray()
    }

    companion object {
        fun decode(bytes: ByteArray): TransportParameters {
            val r = QuicReader(bytes)
            var initialMaxData: Long? = null
            var initialMaxStreamDataBidiLocal: Long? = null
            var initialMaxStreamDataBidiRemote: Long? = null
            var initialMaxStreamDataUni: Long? = null
            var initialMaxStreamsBidi: Long? = null
            var initialMaxStreamsUni: Long? = null
            var maxIdleTimeoutMillis: Long? = null
            var maxUdpPayloadSize: Long? = null
            var ackDelayExponent: Long? = null
            var maxAckDelay: Long? = null
            var activeConnectionIdLimit: Long? = null
            var disableActiveMigration = false
            var initialSourceConnectionId: ByteArray? = null
            var originalDestinationConnectionId: ByteArray? = null
            var retrySourceConnectionId: ByteArray? = null
            var statelessResetToken: ByteArray? = null
            var maxDatagramFrameSize: Long? = null
            val unknown = mutableMapOf<Long, ByteArray>()

            while (r.hasMore()) {
                val id = r.readVarint()
                val len = r.readVarint().toInt()
                val sub = QuicReader(r.readBytes(len))
                when (id) {
                    TransportParameterId.INITIAL_MAX_DATA -> initialMaxData = sub.readVarint()
                    TransportParameterId.INITIAL_MAX_STREAM_DATA_BIDI_LOCAL -> initialMaxStreamDataBidiLocal = sub.readVarint()
                    TransportParameterId.INITIAL_MAX_STREAM_DATA_BIDI_REMOTE -> initialMaxStreamDataBidiRemote = sub.readVarint()
                    TransportParameterId.INITIAL_MAX_STREAM_DATA_UNI -> initialMaxStreamDataUni = sub.readVarint()
                    TransportParameterId.INITIAL_MAX_STREAMS_BIDI -> initialMaxStreamsBidi = sub.readVarint()
                    TransportParameterId.INITIAL_MAX_STREAMS_UNI -> initialMaxStreamsUni = sub.readVarint()
                    TransportParameterId.MAX_IDLE_TIMEOUT -> maxIdleTimeoutMillis = sub.readVarint()
                    TransportParameterId.MAX_UDP_PAYLOAD_SIZE -> maxUdpPayloadSize = sub.readVarint()
                    TransportParameterId.ACK_DELAY_EXPONENT -> ackDelayExponent = sub.readVarint()
                    TransportParameterId.MAX_ACK_DELAY -> maxAckDelay = sub.readVarint()
                    TransportParameterId.ACTIVE_CONNECTION_ID_LIMIT -> activeConnectionIdLimit = sub.readVarint()
                    TransportParameterId.DISABLE_ACTIVE_MIGRATION -> disableActiveMigration = true
                    TransportParameterId.INITIAL_SOURCE_CONNECTION_ID -> initialSourceConnectionId = sub.src.copyOfRange(0, len)
                    TransportParameterId.ORIGINAL_DESTINATION_CONNECTION_ID -> originalDestinationConnectionId = sub.src.copyOfRange(0, len)
                    TransportParameterId.RETRY_SOURCE_CONNECTION_ID -> retrySourceConnectionId = sub.src.copyOfRange(0, len)
                    TransportParameterId.STATELESS_RESET_TOKEN -> statelessResetToken = sub.src.copyOfRange(0, len)
                    TransportParameterId.MAX_DATAGRAM_FRAME_SIZE -> maxDatagramFrameSize = sub.readVarint()
                    else -> unknown[id] = sub.src.copyOfRange(0, len)
                }
            }
            return TransportParameters(
                initialMaxData = initialMaxData,
                initialMaxStreamDataBidiLocal = initialMaxStreamDataBidiLocal,
                initialMaxStreamDataBidiRemote = initialMaxStreamDataBidiRemote,
                initialMaxStreamDataUni = initialMaxStreamDataUni,
                initialMaxStreamsBidi = initialMaxStreamsBidi,
                initialMaxStreamsUni = initialMaxStreamsUni,
                maxIdleTimeoutMillis = maxIdleTimeoutMillis,
                maxUdpPayloadSize = maxUdpPayloadSize,
                ackDelayExponent = ackDelayExponent,
                maxAckDelay = maxAckDelay,
                activeConnectionIdLimit = activeConnectionIdLimit,
                disableActiveMigration = disableActiveMigration,
                initialSourceConnectionId = initialSourceConnectionId,
                originalDestinationConnectionId = originalDestinationConnectionId,
                retrySourceConnectionId = retrySourceConnectionId,
                statelessResetToken = statelessResetToken,
                maxDatagramFrameSize = maxDatagramFrameSize,
                unknown = unknown,
            )
        }
    }
}
