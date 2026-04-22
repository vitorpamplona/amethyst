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

/**
 * JVM + Android [WebTransportFactory] that will wrap the Kwik QUIC library
 * (plus Flupke for HTTP/3) once the Extended CONNECT handshake lands.
 *
 * **Status:** Phase 3b-1 only ships the interface contract — calling
 * [connect] throws [WebTransportException] with
 * [WebTransportException.Kind.NotImplemented]. This lets the Phase 3c MoQ
 * framing layer develop and test against a stable [WebTransportSession]
 * abstraction (see [FakeWebTransport]) while the real Kwik integration is
 * delivered in Phase 3b-2.
 *
 * The expected implementation sequence:
 *   1. QUIC dial via Kwik with ALPN "h3" to `authority`.
 *   2. HTTP/3 SETTINGS exchange including `SETTINGS_ENABLE_CONNECT_PROTOCOL=1`
 *      and the WebTransport draft's `SETTINGS_ENABLE_WEBTRANSPORT=1`.
 *   3. Extended CONNECT request: `:method=CONNECT :protocol=webtransport
 *      :scheme=https :authority=<host> :path=<path>` plus
 *      `Authorization: Bearer <bearerToken>` when supplied.
 *   4. On 2xx, wrap the resulting bidi streams / datagrams in WebTransport
 *      framing (stream type 0x54 for client-initiated bidi).
 */
class KwikWebTransportFactory : WebTransportFactory {
    override suspend fun connect(
        authority: String,
        path: String,
        bearerToken: String?,
    ): WebTransportSession =
        throw WebTransportException(
            kind = WebTransportException.Kind.NotImplemented,
            message = "Kwik-backed WebTransport handshake lands in Phase 3b-2",
        )
}
