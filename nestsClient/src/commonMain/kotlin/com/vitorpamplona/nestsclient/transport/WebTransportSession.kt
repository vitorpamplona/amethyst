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
package com.vitorpamplona.nestsclient.transport

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic WebTransport session, as produced by a successful Extended
 * CONNECT (RFC 9220) handshake.
 *
 * The MoQ layer talks to this interface; the production implementation is
 * [com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory] which
 * sits on top of the pure-Kotlin `:quic` stack. Keeping this abstract lets
 * us:
 *   - unit-test the MoQ framing layer with [FakeWebTransport],
 *   - swap transport implementations (a different QUIC backend, or a
 *     browser-backed bridge as a contingency) without touching audio/UI
 *     code.
 *
 * Lifecycle: the session is opened via [WebTransportFactory.connect] and must
 * be closed with [close] to release the underlying QUIC connection.
 */
interface WebTransportSession {
    /** True once the Extended CONNECT exchange has returned 2xx and before [close] is called. */
    val isOpen: Boolean

    /**
     * Open a new bidirectional WebTransport stream. The returned [WebTransportBidiStream]
     * is writable + readable and closes when either peer half-closes.
     */
    suspend fun openBidiStream(): WebTransportBidiStream

    /**
     * Flow of inbound unidirectional streams initiated by the peer.
     *
     * The flow completes when [close] is called or the peer tears down the session.
     */
    fun incomingUniStreams(): Flow<WebTransportReadStream>

    /** Send a QUIC datagram; returns false if the datagram was dropped by congestion control. */
    suspend fun sendDatagram(payload: ByteArray): Boolean

    /** Flow of inbound datagrams. */
    fun incomingDatagrams(): Flow<ByteArray>

    /** Gracefully close the session with an optional application-level code + reason. */
    suspend fun close(
        code: Int = 0,
        reason: String = "",
    )
}

/** Writable + readable half of a bidirectional WebTransport stream. */
interface WebTransportBidiStream :
    WebTransportReadStream,
    WebTransportWriteStream

/** Read-only WebTransport stream (either a received uni stream or the read half of a bidi). */
interface WebTransportReadStream {
    /** Flow of chunks as they arrive. Completes when the peer closes its write side. */
    fun incoming(): Flow<ByteArray>
}

/** Write-only WebTransport stream. */
interface WebTransportWriteStream {
    suspend fun write(chunk: ByteArray)

    /** Half-close the write side (FIN). No further writes after this call. */
    suspend fun finish()
}

/**
 * Factory that opens a [WebTransportSession] against a nests MoQ endpoint.
 *
 * [authority] is the `host:port` of the WT server, [path] is the URL path
 * (nests defaults to `/moq`), and [bearerToken] is typically the token
 * returned by the `/api/v1/nests/<roomId>` HTTP call (see [NestsClient]).
 */
interface WebTransportFactory {
    suspend fun connect(
        authority: String,
        path: String,
        bearerToken: String? = null,
    ): WebTransportSession
}

/**
 * Single transport-layer exception type, so UI code only has to differentiate
 * on [kind] rather than catching library-specific classes.
 */
class WebTransportException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Kind {
        /** DNS / TCP / TLS / QUIC handshake failed. */
        HandshakeFailed,

        /** WebTransport Extended CONNECT returned a non-2xx response. */
        ConnectRejected,

        /** Peer closed the session or a stream unexpectedly. */
        PeerClosed,

        /** The implementation does not yet support the requested operation. */
        NotImplemented,
    }
}
