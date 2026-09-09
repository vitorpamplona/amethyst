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
package com.vitorpamplona.marmotquic

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import kotlinx.coroutines.flow.Flow

/**
 * One delivery stream of an agent text stream preview, as
 * `transports/quic.md` defines it: a QUIC stream carrying
 * `uint32 frame_len || AgentTextStreamRecordV1` frames, preceded on the broker
 * path by one control envelope framed the same way.
 *
 * The transport is deliberately narrow. It moves opaque ciphertext records and
 * knows nothing about what they say: the record key comes from the group's MLS
 * exporter, so a broker — and this layer — sees only the routing pair
 * `(stream_id, start_event_id)` and bytes it cannot read.
 */
interface MarmotQuicStream {
    /** Append one record to the stream. */
    suspend fun send(record: AgentTextStreamRecordV1)

    /**
     * Records as they arrive, already de-framed and with the binding's
     * stream-id pinning applied. Completes when the peer finishes the stream.
     *
     * Ordering, replay and gap handling belong to the caller — they need the
     * transcript to decide, and this layer has no key to fold one with.
     */
    fun incoming(): Flow<AgentTextStreamRecordV1>

    /** Finish our write side cleanly; the stream ends when both sides have. */
    suspend fun finish()

    /** Tear the whole thing down, including the QUIC connection under it. */
    suspend fun close()
}

/**
 * Opens preview delivery streams against a `quic://` candidate.
 *
 * A candidate is advisory: one that fails to connect, fails TLS, or serves a
 * different `(stream_id, start_event_id)` is unusable and the caller moves to
 * the next. Implementations therefore surface a failure as
 * [MarmotQuicException] rather than pretending a stream exists.
 */
interface MarmotQuicTransport {
    /**
     * Claim a broker room and stream records into it.
     *
     * The publisher path is a client-opened UNIDIRECTIONAL stream: it writes
     * a `publish` control envelope and then the record frames. A broker
     * rejects a publish envelope that arrives on a bidirectional stream.
     */
    suspend fun publish(
        candidate: String,
        streamId: ByteArray,
        startEventId: ByteArray,
    ): MarmotQuicStream

    /**
     * Join a broker room and read the fan-out.
     *
     * The subscriber path is a client-opened BIDIRECTIONAL stream: it writes a
     * `subscribe` control envelope and reads record frames on the return
     * direction. A broker rejects a subscribe envelope on a unidirectional
     * stream, because it would have nowhere to answer.
     */
    suspend fun subscribe(
        candidate: String,
        streamId: ByteArray,
        startEventId: ByteArray,
    ): MarmotQuicStream
}

/** Why a candidate turned out to be unusable. */
class MarmotQuicException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Kind {
        /** The `quic://` candidate does not parse, or is over the 512-byte bound. */
        BadCandidate,

        /** UDP, QUIC or TLS never got as far as a connection. */
        HandshakeFailed,

        /** The endpoint does not speak our ALPN, so it is not a Marmot endpoint. */
        AlpnRejected,

        /** The peer closed the stream or the connection under us. */
        PeerClosed,
    }
}
