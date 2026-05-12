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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.server.Nip11HttpRoute
import com.vitorpamplona.geode.server.Nip86HttpRoute
import com.vitorpamplona.geode.server.WebSocketSessionPump
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip86RelayManagement.server.Nip86Server
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Hosts a [RelayEngine] over a real `ws://` endpoint backed by Ktor + CIO.
 *
 * Use this when something other than the in-process
 * [com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket]
 * needs to talk to the relay — Android instrumented tests, the `cli`
 * tooling, external clients, or a standalone "run a Nostr relay"
 * process.
 *
 * For unit-test wiring inside a single JVM, prefer [InProcessRelays] + the
 * in-process socket — same protocol, no socket overhead.
 *
 * Lifecycle:
 * ```
 * val server = KtorRelay(RelayEngine(url = ...)).start()
 * println("listening on ${server.url}")
 * // ... do stuff ...
 * server.stop()
 * ```
 */
class KtorRelay(
    val relay: RelayEngine,
    val host: String = "127.0.0.1",
    /** Pass 0 to let the OS pick a free port. Read [url] after [start] to learn it. */
    val port: Int = 0,
    val path: String = "/",
    /**
     * Pubkeys allowed to call NIP-86 admin RPCs. Empty (the default)
     * disables the admin endpoint entirely — POSTs return 403.
     * Otherwise: HTTP POSTs to [path] with `Content-Type:
     * application/nostr+json+rpc` are dispatched to [Nip86Server],
     * gated by NIP-98 HTTP-Auth membership in this set.
     */
    val adminPubkeys: Set<HexKey> = emptySet(),
    /**
     * Canonical public URL the relay is reachable at, e.g.
     * `https://relay.example.com/`. NIP-98 admin requests must sign
     * the **same** URL string they're sending to. When the relay sits
     * behind TLS termination or a reverse proxy, the `Host` header
     * the relay sees does not match what the client signs, so the
     * verifier must compare against this configured value.
     *
     * `null` (the default) falls back to the request's `Host` header
     * with `http://` — fine for local-loopback unit tests, **NOT
     * SAFE** in a public deployment because an attacker can spoof
     * `Host` and bind their signature to any URL.
     */
    val publicUrl: String? = null,
    /**
     * Ktor CIO acceptor-thread count. `null` keeps Ktor's default.
     * Lift on machines with many cores when targeting 10k+
     * concurrent connections — see `[network]` config docs.
     */
    val connectionGroupSize: Int? = null,
    /** Ktor CIO worker-thread count. `null` keeps Ktor's default. */
    val workerGroupSize: Int? = null,
    /** Ktor CIO call-handling thread count. `null` keeps Ktor's default. */
    val callGroupSize: Int? = null,
) {
    private val infoHolder =
        object : Nip86Server.InfoHolder {
            override fun get(): Nip11RelayInformation = relay.info.document

            override fun set(info: Nip11RelayInformation) {
                relay.updateInfo { info }
            }
        }

    private val nip86Server = Nip86Server(banStore = relay.banStore, infoHolder = infoHolder, store = relay.store)
    private val nip86Route =
        Nip86HttpRoute(
            server = nip86Server,
            verifier = Nip98AuthVerifier(),
            allowList = adminPubkeys.mapTo(HashSet()) { it.lowercase() },
            // 1 MiB. Bounded *before* NIP-98 auth verification — we
            // have to read the body to compute its sha256 for the
            // payload binding, so an unbounded read would let an
            // unauthenticated attacker stream gigabytes and OOM the
            // relay. NIP-86 RPC payloads are a few hundred bytes;
            // 1 MiB is ~1000× any plausible request.
            maxBodyBytes = 1 shl 20,
            signedUrlFor = { call ->
                publicUrl ?: ("http://" + (call.request.header(HttpHeaders.Host) ?: "$host:$resolvedPort") + path)
            },
        )
    private val nip11Route = Nip11HttpRoute(liveJson = { relay.info.json })

    private var engine: CIOApplicationEngine? = null
    private var resolvedPort: Int = -1

    /**
     * Set when [stop] begins. Once true, the WebSocket handler refuses
     * new upgrades — Ktor's `engine.stop` will eventually do this too,
     * but Ktor's grace window means new connections can land between
     * `notifyShutdown` and the actual port-close, missing the NOTICE
     * we just sent to existing clients.
     */
    @Volatile
    private var shuttingDown: Boolean = false

    /**
     * Active client sessions, registered when their WebSocket handler
     * runs and removed on disconnect. Exposed (read-only) so [stop] can
     * NOTICE every connected client during graceful drain, and so tests
     * can assert lifecycle bookkeeping.
     */
    private val activeSessions: MutableSet<RelaySession> = ConcurrentHashMap.newKeySet()

    /** Number of WebSocket sessions currently connected to the server. */
    val activeSessionCount: Int get() = activeSessions.size

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
    fun start(): KtorRelay {
        // Snapshot the constructor-supplied overrides into locals so
        // the `configure` lambda below can assign to its receiver
        // without the names colliding with outer properties.
        val connGrp = connectionGroupSize
        val workGrp = workerGroupSize
        val callGrp = callGroupSize
        val bindHost = host
        val bindPort = port
        val server =
            embeddedServer(
                factory = CIO,
                rootConfig =
                    serverConfig {
                        module {
                            install(WebSockets)
                            routing {
                                // NIP-11: GET on the relay URL with Accept:
                                // application/nostr+json returns the relay info doc.
                                // We mount this *before* the webSocket route so Ktor
                                // serves NIP-11 for plain HTTP GETs and only upgrades
                                // to a WebSocket when the request is a WS upgrade.
                                get(path) {
                                    nip11Route.handle(call)
                                }
                                // NIP-86: POST application/nostr+json+rpc with a NIP-98
                                // signed Authorization header → JSON-RPC dispatch.
                                post(path) {
                                    nip86Route.handle(call)
                                }
                                webSocket(path) {
                                    if (shuttingDown) {
                                        // Just return — Ktor closes the WS for us.
                                        return@webSocket
                                    }
                                    WebSocketSessionPump(this).pump(
                                        server = relay.server,
                                        registerSession = activeSessions::add,
                                        unregisterSession = activeSessions::remove,
                                    )
                                }
                            }
                        }
                    },
                configure = {
                    connector {
                        host = bindHost
                        port = bindPort
                    }
                    // Keep Ktor defaults unless the operator overrode
                    // them — Ktor's per-CPU sizing is sensible for
                    // most deployments, and over-threading hurts L1/L2
                    // locality at low connection counts.
                    connGrp?.let { connectionGroupSize = it }
                    workGrp?.let { workerGroupSize = it }
                    callGrp?.let { callGroupSize = it }
                },
            )
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

    /**
     * Graceful shutdown. Safe to call multiple times.
     *
     * 1. Sends a NOTICE("closing: …") to every currently-connected
     *    client so well-behaved clients know to reconnect later.
     * 2. Stops the Ktor engine: rejects new connections immediately,
     *    then waits up to [gracePeriodMillis] for active WebSocket
     *    handlers to finish whatever they're processing (so an in-flight
     *    `EVENT` lands its `OK` reply before the socket dies). After
     *    the grace window, in-progress handlers are cancelled and the
     *    engine waits up to [timeoutMillis] - [gracePeriodMillis] for
     *    that cancellation to complete.
     *
     * Defaults to 5 s grace / 10 s total — generous enough that a
     * SQLite write + reply round-trip can land for typical event
     * sizes. Override either with a tighter budget if your operator
     * knows their workload.
     */
    fun stop(
        gracePeriodMillis: Long = 5_000,
        timeoutMillis: Long = 10_000,
    ) {
        val e = engine ?: return
        // Order: (1) refuse new connections so they don't slip in and
        // miss the NOTICE; (2) NOTICE every existing session so
        // well-behaved clients reconnect later; (3) hand off to Ktor
        // for the grace + timeout dance.
        shuttingDown = true
        notifyShutdown()
        e.stop(gracePeriodMillis, timeoutMillis)
        engine = null
        resolvedPort = -1
    }

    /**
     * Best-effort NOTICE to every active client. Failures are
     * swallowed — a flaky socket on its way out is exactly the case
     * where a NOTICE will fail anyway, and the client's read of the
     * close frame is the authoritative shutdown signal.
     */
    private fun notifyShutdown() {
        val notice = NoticeMessage("closing: relay is shutting down — please reconnect later")
        activeSessions.forEach { session ->
            runCatching { session.send(notice) }
        }
    }
}
