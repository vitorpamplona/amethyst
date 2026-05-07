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
package com.vitorpamplona.quartz.relay

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking

/**
 * Hosts a [Relay] over a real `ws://` endpoint backed by Ktor + CIO.
 *
 * Use this when something other than the in-process [InProcessWebSocket] needs
 * to talk to the relay — Android instrumented tests, the `cli` tooling,
 * external clients, or a standalone "run a Nostr relay" process.
 *
 * For unit-test wiring inside a single JVM, prefer [RelayHub] +
 * [InProcessWebSocket] — same protocol, no socket overhead.
 *
 * Lifecycle:
 * ```
 * val server = LocalRelayServer(Relay(url = ...)).start()
 * println("listening on ${server.url}")
 * // ... do stuff ...
 * server.stop()
 * ```
 */
class LocalRelayServer(
    val relay: Relay,
    val host: String = "127.0.0.1",
    /** Pass 0 to let the OS pick a free port. Read [url] after [start] to learn it. */
    val port: Int = 0,
    val path: String = "/",
) {
    private var engine: CIOApplicationEngine? = null
    private var resolvedPort: Int = -1

    /** `ws://host:port/path` — only valid after [start]. */
    val url: String
        get() {
            check(resolvedPort != -1) { "Server not started" }
            return "ws://$host:$resolvedPort$path"
        }

    /**
     * Binds the Ktor engine. Returns once the engine reports ready, so
     * [url] is safe to read on the very next line.
     */
    fun start(): LocalRelayServer {
        val server =
            embeddedServer(CIO, host = host, port = port) {
                install(WebSockets)
                routing {
                    // NIP-11: GET on the relay URL with Accept:
                    // application/nostr+json returns the relay info doc.
                    // We mount this *before* the webSocket route so Ktor
                    // serves NIP-11 for plain HTTP GETs and only upgrades
                    // to a WebSocket when the request is a WS upgrade.
                    get(path) {
                        val accept = call.request.header(HttpHeaders.Accept).orEmpty()
                        if (accept.contains("application/nostr+json")) {
                            call.response.headers.append("Access-Control-Allow-Origin", "*")
                            call.respondText(
                                relay.info.json,
                                ContentType.parse("application/nostr+json"),
                            )
                        } else {
                            call.respondText(
                                "Use a Nostr client (NIP-01 WebSocket) or send Accept: application/nostr+json (NIP-11).",
                                ContentType.Text.Plain,
                                HttpStatusCode.UpgradeRequired,
                            )
                        }
                    }
                    webSocket(path) {
                        val session =
                            relay.server.connect { json ->
                                // ktor-websockets schedules outgoing frames on its own
                                // dispatcher; trySend never blocks the relay thread.
                                outgoing.trySend(Frame.Text(json))
                            }
                        try {
                            incoming.consumeEach { frame ->
                                if (frame is Frame.Text) {
                                    session.receive(frame.readText())
                                }
                            }
                        } finally {
                            session.close()
                        }
                    }
                }
            }
        server.start(wait = false)
        engine = server.engine
        // Ktor 3.x made resolvedConnectors() suspend. We block here so
        // start() returns synchronously with [url] readable on the next line.
        resolvedPort =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        return this
    }

    /** Stops the engine. Safe to call multiple times. */
    fun stop(
        gracePeriodMillis: Long = 100,
        timeoutMillis: Long = 1_000,
    ) {
        engine?.stop(gracePeriodMillis, timeoutMillis)
        engine = null
        resolvedPort = -1
    }
}
