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
package com.vitorpamplona.quic.webtransport

import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.stream.QuicStream

/**
 * Container that bundles a [QuicConnection], its [QuicConnectionDriver], and
 * the QUIC stream id of the CONNECT bidi (the WT session id).
 *
 * Application code (nestsClient's MoQ layer) gets at this via the
 * [QuicWebTransportFactory] adapter which exposes the platform-agnostic
 * [com.vitorpamplona.nestsclient.transport.WebTransportSession] interface.
 */
class QuicWebTransportSessionState(
    val connection: QuicConnection,
    val driver: QuicConnectionDriver,
    val connectStreamId: Long,
) {
    /** True until [close] is called or the underlying QUIC connection terminates. */
    val isOpen: Boolean
        get() = connection.status == QuicConnection.Status.CONNECTED

    /** Open a new client-initiated bidirectional WebTransport stream. */
    suspend fun openBidiStream(): QuicStream {
        val s = connection.openBidiStream()
        // Prefix bytes go onto the new stream first.
        s.send.enqueue(encodeWtBidiStreamPrefix(connectStreamId))
        driver.wakeup()
        return s
    }

    /** Open a new client-initiated unidirectional WebTransport stream. */
    suspend fun openUniStream(): QuicStream {
        val s = connection.openUniStream()
        s.send.enqueue(encodeWtUniStreamPrefix(connectStreamId))
        driver.wakeup()
        return s
    }

    /** Send a WebTransport datagram via QUIC's datagram extension. */
    suspend fun sendDatagram(payload: ByteArray) {
        val wrapped = WtDatagram.encode(connectStreamId, payload)
        connection.queueDatagram(wrapped)
        driver.wakeup()
    }

    suspend fun pollIncomingDatagram(): ByteArray? {
        val raw = connection.pollIncomingDatagram() ?: return null
        val decoded = WtDatagram.decode(raw) ?: return null
        if (decoded.sessionStreamId != connectStreamId) return null
        return decoded.payload
    }

    suspend fun pollIncomingPeerStream(): QuicStream? = connection.pollIncomingPeerStream()

    suspend fun close(
        errorCode: Int = 0,
        reason: String = "",
    ) {
        connection.streamById(connectStreamId)?.let {
            it.send.enqueue(encodeCloseSessionCapsule(errorCode, reason))
            it.send.finish()
        }
        driver.close()
    }
}
