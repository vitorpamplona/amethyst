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

import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionConfig
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.http3.Http3Frame
import com.vitorpamplona.quic.http3.Http3FrameReader
import com.vitorpamplona.quic.http3.Http3StreamType
import com.vitorpamplona.quic.http3.buildClientWebTransportSettings
import com.vitorpamplona.quic.qpack.QpackDecoder
import com.vitorpamplona.quic.stream.QuicStream
import com.vitorpamplona.quic.tls.CertificateValidator
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import com.vitorpamplona.quic.transport.UdpSocket
import com.vitorpamplona.quic.webtransport.QuicWebTransportSessionState
import com.vitorpamplona.quic.webtransport.buildExtendedConnectHeaders
import com.vitorpamplona.quic.webtransport.encodeHeadersFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Pure-Kotlin WebTransport over QUIC v1, sitting on top of every layer in
 * `:quic`. The historical alternative was a Kwik-based JNI binding; that
 * was rejected because no Android-compatible native classifier ships, so
 * we wrote `:quic` from scratch.
 *
 * Lifecycle on [connect]:
 *   1. Open a UDP socket connected to (authority host, authority port).
 *   2. Build a [QuicConnection], spawn a [QuicConnectionDriver]; this drives
 *      the QUIC + TLS 1.3 handshake to completion.
 *   3. Open a unidirectional control stream, push the H3 stream-type byte
 *      (0x00) and a SETTINGS frame announcing ENABLE_CONNECT_PROTOCOL,
 *      H3_DATAGRAM, ENABLE_WEBTRANSPORT.
 *   4. Open a bidirectional request stream, push a HEADERS frame carrying
 *      the Extended CONNECT request: `:method=CONNECT, :protocol=webtransport,
 *      :scheme=https, :authority=…, :path=…, [authorization=Bearer …]`.
 *   5. Wait for the response HEADERS; on `:status = 2xx` the WT session is
 *      open. Wrap the connection + driver + connect stream id in a
 *      [WebTransportSession].
 *
 * For brevity, the WT response-reading is best-effort: we don't currently
 * parse the response HEADERS QPACK to validate `:status` — production code
 * will. The wire is otherwise fully RFC-conformant.
 */
class QuicWebTransportFactory(
    private val parentScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /**
     * Certificate validator. Defaults to [JdkCertificateValidator], which
     * delegates to the platform / JDK system trust store. Tests or self-signed
     * dev environments can pass a permissive validator explicitly.
     */
    private val certificateValidator: CertificateValidator = JdkCertificateValidator(),
    /**
     * Maximum time we'll suspend waiting for the server's HEADERS response on
     * the Extended CONNECT request stream before giving up with HandshakeFailed.
     */
    private val connectTimeoutMillis: Long = 10_000L,
    /**
     * WebTransport sub-protocols to advertise on the Extended CONNECT request,
     * via the `wt-available-protocols` header (RFC 8941 Structured Field List
     * of strings — see draft-ietf-webtrans-http3-14 §3.3).
     *
     * For nests, this MUST contain `moq-lite-03`; without it, moq-relay falls
     * back to the legacy in-band SETUP exchange (moq-lite-02) and our first
     * post-CONNECT message is decoded as SETUP_CLIENT, producing
     * `connection closed err=invalid value` on the relay side and a stalled
     * subscribe / `subscribe stream FIN before reply` on the client side.
     */
    private val webTransportSubProtocols: List<String> = listOf("moq-lite-03"),
) : WebTransportFactory {
    override suspend fun connect(
        authority: String,
        path: String,
        bearerToken: String?,
    ): WebTransportSession {
        val (host, port) = splitAuthority(authority)
        val socket = UdpSocket.connect(host, port)
        val conn =
            QuicConnection(
                serverName = host,
                config = QuicConnectionConfig(),
                tlsCertificateValidator = certificateValidator,
            )
        val driver = QuicConnectionDriver(conn, socket, parentScope)
        driver.start()

        try {
            conn.awaitHandshake()
        } catch (t: Throwable) {
            driver.close()
            throw WebTransportException(
                kind = WebTransportException.Kind.HandshakeFailed,
                message = "QUIC handshake failed: ${t.message}",
                cause = t,
            )
        }
        if (conn.status != QuicConnection.Status.CONNECTED) {
            driver.close()
            throw WebTransportException(
                kind = WebTransportException.Kind.HandshakeFailed,
                message = "QUIC handshake did not complete (status=${conn.status})",
            )
        }

        // Everything from here through readResponseStatus needs cleanup on
        // any exception — wrap so a thrown SocketException / coroutine cancel
        // doesn't leak the driver + UDP socket.
        try {
            // Open the H3 control stream and push SETTINGS.
            val controlStream = conn.openUniStream()
            val controlBytes =
                byteArrayOf(Http3StreamType.CONTROL.toByte()) + buildClientWebTransportSettings().encodeFrame()
            controlStream.send.enqueue(controlBytes)
            driver.wakeup()

            // RFC 9220 §3.1 strictly requires waiting for the server's SETTINGS
            // frame confirming SETTINGS_ENABLE_WEBTRANSPORT=1 before sending
            // CONNECT. Wiring peerSettings to gate the CONNECT requires
            // restructuring (the demux lives inside QuicWebTransportSessionState
            // which we don't build until after the request bidi opens).
            // Tolerant servers (aioquic, quic-go's interop) accept early CONNECT;
            // strict servers (Chromium) may close the stream. Tracked as a
            // known limitation of the v1 stack.

            // Open the Extended CONNECT request stream.
            val requestStream = conn.openBidiStream()
            val extraHeaders =
                if (webTransportSubProtocols.isNotEmpty()) {
                    listOf("wt-available-protocols" to encodeSfStringList(webTransportSubProtocols))
                } else {
                    emptyList()
                }
            val headers = buildExtendedConnectHeaders(authority, path, bearerToken, extraHeaders)
            requestStream.send.enqueue(encodeHeadersFrame(headers))
            driver.wakeup()

            // Wait for the response HEADERS and verify :status is 2xx before
            // declaring the WebTransport session open. Per RFC 9220 a non-2xx
            // status means the server rejected the upgrade.
            val responseStatus =
                kotlinx.coroutines.withTimeoutOrNull(connectTimeoutMillis) {
                    readResponseStatus(requestStream)
                } ?: -1
            if (responseStatus < 0) {
                driver.close()
                throw WebTransportException(
                    kind = WebTransportException.Kind.HandshakeFailed,
                    message = "WebTransport CONNECT response timed out after ${connectTimeoutMillis}ms",
                )
            }
            if (responseStatus !in 200..299) {
                driver.close()
                throw WebTransportException(
                    kind = WebTransportException.Kind.ConnectRejected,
                    message = "WebTransport CONNECT returned :status=$responseStatus",
                )
            }

            val state = QuicWebTransportSessionState(conn, driver, requestStream.streamId)
            return QuicWebTransportSession(state)
        } catch (we: WebTransportException) {
            throw we
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Preserve cancellation semantics — wrapping it in HandshakeFailed
            // would break structured concurrency. But still close the driver.
            driver.close()
            throw ce
        } catch (t: Throwable) {
            driver.close()
            throw WebTransportException(
                kind = WebTransportException.Kind.HandshakeFailed,
                message = "WebTransport setup failed: ${t.message}",
                cause = t,
            )
        }
    }

    /**
     * Drain bytes from [requestStream] through an [Http3FrameReader] until a
     * HEADERS frame arrives, then decode the QPACK field section and pull the
     * `:status` pseudo-header.
     *
     * Returns 0 if the stream closes without a HEADERS frame (the caller treats
     * that as a connect rejection).
     */
    private suspend fun readResponseStatus(requestStream: QuicStream): Int {
        val reader = Http3FrameReader()
        val incoming = requestStream.incoming
        try {
            incoming.collect { chunk ->
                reader.push(chunk)
                while (true) {
                    val frame = reader.next() ?: break
                    if (frame is Http3Frame.Headers) {
                        val pairs = QpackDecoder().decodeFieldSection(frame.qpackPayload)
                        val status = pairs.firstOrNull { it.first == ":status" }?.second?.toIntOrNull() ?: 0
                        throw HeadersReceived(status)
                    }
                }
            }
        } catch (e: HeadersReceived) {
            return e.status
        }
        return 0
    }

    private class HeadersReceived(
        val status: Int,
    ) : RuntimeException()

    /**
     * Encode [items] as an RFC 8941 Structured Field List of bare strings
     * (the format `wt-available-protocols` requires per draft-ietf-webtrans-http3
     * §3.3). Each entry becomes `"value"`; the items are comma-separated.
     *
     * The values we emit (e.g. `moq-lite-03`) are bare ASCII so we don't
     * need to handle escaping — assert it instead of silently mis-emitting.
     */
    private fun encodeSfStringList(items: List<String>): String {
        require(items.all { p -> p.all { it in 0x20.toChar()..0x7e.toChar() && it != '"' && it != '\\' } }) {
            "wt-available-protocols entry contains characters that need RFC 8941 escaping: $items"
        }
        return items.joinToString(", ") { "\"$it\"" }
    }

    private fun splitAuthority(authority: String): Pair<String, Int> {
        val idx = authority.lastIndexOf(':')
        if (idx <= 0) return authority to 443
        val host = authority.substring(0, idx)
        val port = authority.substring(idx + 1).toIntOrNull() ?: 443
        return host to port
    }
}

/** Adapter that wraps the :quic [QuicWebTransportSessionState] in the nestsClient interface. */
class QuicWebTransportSession(
    private val state: QuicWebTransportSessionState,
) : WebTransportSession {
    override val isOpen: Boolean get() = state.isOpen

    /**
     * Diagnostic-only passthrough to
     * [com.vitorpamplona.quic.connection.QuicConnection.flowControlSnapshot].
     * Used by `SendTraceScenario` to dump the connection's
     * flow-control accounting at known points (pre-pump, mid-pump,
     * post-pump-grace) when investigating loss patterns. Not part of
     * the [WebTransportSession] common interface — callers downcast
     * explicitly when they need it.
     */
    suspend fun quicFlowControlSnapshot(): com.vitorpamplona.quic.connection.QuicFlowControlSnapshot = state.connection.flowControlSnapshot()

    override suspend fun openBidiStream(): WebTransportBidiStream {
        val s = state.openBidiStream()
        return QuicBidiStreamAdapter(s, state.driver)
    }

    override suspend fun openUniStream(): WebTransportWriteStream {
        val s = state.openUniStream()
        return QuicUniWriteStreamAdapter(s, state.driver)
    }

    override fun incomingUniStreams(): Flow<WebTransportReadStream> =
        flow {
            // Surface only unidirectional WT streams whose prefix bytes
            // (0x54 + quarter session id) have been stripped. The H3 control
            // stream and QPACK encoder/decoder streams are drained internally
            // by the demux and never reach here.
            state.incomingStrippedStreams.collect { stripped ->
                if (stripped.isUnidirectional) {
                    emit(StrippedWtReadStreamAdapter(stripped))
                }
            }
        }

    override fun incomingBidiStreams(): Flow<WebTransportBidiStream> =
        flow {
            // Surface only peer-initiated bidi streams whose WT_BIDI_STREAM
            // prefix has been stripped by the demux. send/finish are
            // wired through the driver wakeup.
            state.incomingStrippedStreams.collect { stripped ->
                if (!stripped.isUnidirectional) {
                    emit(StrippedWtBidiStreamAdapter(stripped))
                }
            }
        }

    override suspend fun sendDatagram(payload: ByteArray): Boolean {
        state.sendDatagram(payload)
        return true
    }

    override fun incomingDatagrams(): Flow<ByteArray> =
        flow {
            while (state.isOpen) {
                val d = state.pollIncomingDatagram()
                if (d != null) emit(d)
                kotlinx.coroutines.delay(5)
            }
        }

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        state.close(code, reason)
    }
}

private class QuicBidiStreamAdapter(
    private val stream: QuicStream,
    private val driver: com.vitorpamplona.quic.connection.QuicConnectionDriver,
) : WebTransportBidiStream {
    override fun incoming(): Flow<ByteArray> = stream.incoming

    override suspend fun write(chunk: ByteArray) {
        stream.send.enqueue(chunk)
        driver.wakeup()
    }

    override suspend fun finish() {
        stream.send.finish()
        driver.wakeup()
    }
}

private class QuicReadStreamAdapter(
    private val stream: QuicStream,
) : WebTransportReadStream {
    override fun incoming(): Flow<ByteArray> = stream.incoming
}

/**
 * Write-only adapter over a locally-opened uni QUIC stream. The
 * underlying [QuicWebTransportSessionState.openUniStream] has already
 * pushed the WT framing prefix (0x54 + quarter session id), so the
 * caller's [write] payload goes straight onto the wire.
 */
private class QuicUniWriteStreamAdapter(
    private val stream: QuicStream,
    private val driver: com.vitorpamplona.quic.connection.QuicConnectionDriver,
) : WebTransportWriteStream {
    override suspend fun write(chunk: ByteArray) {
        stream.send.enqueue(chunk)
        driver.wakeup()
    }

    override suspend fun finish() {
        stream.send.finish()
        driver.wakeup()
    }
}

/** Adapter for a WT peer-initiated uni stream whose prefix has been stripped. */
private class StrippedWtReadStreamAdapter(
    private val stripped: com.vitorpamplona.quic.webtransport.StrippedWtStream,
) : WebTransportReadStream {
    override fun incoming(): Flow<ByteArray> = stripped.data
}

/**
 * Adapter for a peer-initiated bidi WT stream whose WT_BIDI_STREAM prefix
 * has been stripped. Routes [write] / [finish] through the demux's
 * driver-aware closures so application bytes actually leave the
 * connection.
 */
private class StrippedWtBidiStreamAdapter(
    private val stripped: com.vitorpamplona.quic.webtransport.StrippedWtStream,
) : WebTransportBidiStream {
    init {
        check(!stripped.isUnidirectional) {
            "StrippedWtBidiStreamAdapter requires a bidi stream, got uni"
        }
    }

    override fun incoming(): Flow<ByteArray> = stripped.data

    override suspend fun write(chunk: ByteArray) {
        val send =
            stripped.send
                ?: error("peer-initiated bidi stream has no send half — demux didn't wire one")
        send(chunk)
    }

    override suspend fun finish() {
        val finish =
            stripped.finish
                ?: error("peer-initiated bidi stream has no finish — demux didn't wire one")
        finish()
    }
}
