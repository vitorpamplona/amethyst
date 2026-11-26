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
package com.vitorpamplona.amethyst.commons.service.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

/** The host the Tor SOCKS proxy is named by. See [darwinHttpClient] for why not `127.0.0.1`. */
const val TOR_SOCKS_HOST = "localhost"

/**
 * An NSURLSession client with WebSockets: through the SOCKS proxy at `localhost:[socksPort]` when
 * one is given (Tor), direct otherwise. The build function iOS hands [DualKtorClients].
 *
 * `localhost`, not `127.0.0.1`: iCepa's Tor.framework found that with the IP, `.onion` names were
 * resolved locally on devices and DNS leaked; with the name they go to the proxy
 * (plans/2026-11-25-ios-tor.md, §3.1). Measured in B12a: WebSocket tasks honour the proxy, and with
 * no listener on the port a request fails (-1004) instead of going direct.
 */
fun darwinHttpClient(socksPort: Int?): HttpClient =
    HttpClient(Darwin) {
        install(WebSockets)
        if (socksPort != null) {
            engine { proxy = ProxyBuilder.socks(host = TOR_SOCKS_HOST, port = socksPort) }
        }
    }
