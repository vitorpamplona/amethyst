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
package com.vitorpamplona.amethyst.desktop.network

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.BitRelayUrlRewriter
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.TlsaConnectionPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver

/**
 * Desktop-specific relay connection manager that configures OkHttp for websockets.
 *
 * - Tor-aware: passes the [DesktopHttpClient]'s `getHttpClient` which selects
 *   proxy or direct client per relay URL based on Tor settings.
 * - `.bit`-aware (via [bitRelayResolver]): rewrites `wss://example.bit/`
 *   handshake URLs to the real `wss://...` endpoint published in the
 *   Namecoin record at connect time, and pins the TLS handshake against
 *   any TLSA records published in the same record. The canonical relay
 *   URL stays `wss://example.bit/` for relay tags / UI.
 */
class DesktopRelayConnectionManager(
    httpClient: DesktopHttpClient,
    bitRelayResolver: BitRelayResolver? = null,
) : RelayConnectionManager(
        websocketBuilder =
            if (bitRelayResolver != null) {
                val tlsaConnectionPolicy = TlsaConnectionPolicy(bitRelayResolver)
                BasicOkHttpWebSocket.Builder(
                    httpClient = httpClient::getHttpClient,
                    urlRewriter =
                        BitRelayUrlRewriter(
                            resolver = bitRelayResolver,
                            // Desktop has no `.onion` Tor routing for `.bit`
                            // relays yet (DesktopHttpClient handles Tor for
                            // clearnet endpoints, but the Namecoin `_tor.txt`
                            // / `tor` field path needs more wiring on this
                            // side). Always prefer the clearnet endpoint for
                            // now; the rewriter cleanly returns `null` when a
                            // record only publishes `.onion` and the connect
                            // fails loudly rather than silently misrouting.
                            preferOnion = { _ -> false },
                        ),
                    clientDecorator = tlsaConnectionPolicy::decorate,
                )
            } else {
                BasicOkHttpWebSocket.Builder(httpClient = httpClient::getHttpClient)
            },
    )
