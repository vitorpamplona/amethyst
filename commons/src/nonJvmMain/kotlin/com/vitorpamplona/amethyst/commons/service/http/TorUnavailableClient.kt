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
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.websocket.WebSocketCapability
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI

/**
 * A request had to go through Tor, and Tor had no SOCKS port to take it. Nothing was sent: the
 * request failed before it reached the network, rather than going direct.
 */
class TorUnavailableException(
    val url: String,
) : IllegalStateException("Tor is required for $url, but Tor is not connected yet")

/**
 * A client whose every request, WebSocket upgrades included, fails with [TorUnavailableException]
 * without touching the network. What [DualKtorClients] hands a Tor-routed request while Tor is down.
 */
fun torUnavailableClient(): HttpClient =
    HttpClient(TorUnavailableEngine()) {
        // So a relay socket's upgrade reaches the engine and fails with the same reason.
        install(WebSockets)
    }

private class TorUnavailableEngine : HttpClientEngineBase("tor-unavailable") {
    override val config = HttpClientEngineConfig()

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(WebSocketCapability)

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData = throw TorUnavailableException(data.url.toString())
}
