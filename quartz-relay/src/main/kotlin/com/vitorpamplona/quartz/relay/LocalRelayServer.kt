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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.relay.admin.Nip86Server
import com.vitorpamplona.quartz.relay.admin.Nip98AuthVerifier
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

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
    /**
     * Per-frame size cap; mirrors `[limits].max_ws_frame_bytes` in the
     * config. Frames larger than this are rejected at the WebSocket
     * layer, which is the only layer that sees the raw bytes. `null`
     * uses Ktor's default (~1 MiB).
     */
    val maxFrameBytes: Long? = null,
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
     * Maximum body size accepted on the NIP-86 POST endpoint, in
     * bytes. Bounded *before* auth verification because we read the
     * body to compute its sha256 for NIP-98's payload binding —
     * unbounded reads would let an unauthenticated attacker stream
     * gigabytes and OOM the relay. 1 MiB easily fits any plausible
     * RPC payload.
     */
    val maxAdminBodyBytes: Int = 1 shl 20,
) {
    /**
     * Bridges the relay's mutable [RelayInfo] to [Nip86Server.InfoHolder]
     * so admin RPCs can rewrite the NIP-11 doc atomically.
     */
    private val infoHolder =
        object : Nip86Server.InfoHolder {
            override fun get() = relay.info

            override fun set(info: RelayInfo) {
                relay.updateInfo { info.document }
            }
        }

    private val nip86 = Nip86Server(banStore = relay.banStore, infoHolder = infoHolder, store = relay.store)
    private val nip98 = Nip98AuthVerifier()

    private val adminAllowList: Set<HexKey> = adminPubkeys.mapTo(HashSet()) { it.lowercase() }
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
    fun start(): LocalRelayServer {
        val server =
            embeddedServer(CIO, host = host, port = port) {
                install(WebSockets) {
                    maxFrameBytes?.let { maxFrameSize = it }
                }
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
                    // NIP-86: POST application/nostr+json+rpc with a NIP-98
                    // signed Authorization header → JSON-RPC dispatch.
                    post(path) {
                        handleNip86(call)
                    }
                    webSocket(path) {
                        if (shuttingDown) {
                            // Just return — Ktor closes the WS for us.
                            // We can't `close(reason)` here because the
                            // CIO engine's outgoing channel may already
                            // be torn down during shutdown.
                            return@webSocket
                        }
                        // Per-session outbound queue. The relay's
                        // `connect` callback runs on whatever thread
                        // produced the message — it can't suspend, so
                        // we hand off to a dedicated writer coroutine
                        // that does suspend on `outgoing.send` and thus
                        // applies real backpressure on slow clients.
                        // When the queue fills, that's a slow consumer
                        // — drop the connection cleanly so subscribers
                        // don't silently miss EVENT/EOSE.
                        val outQueue =
                            kotlinx.coroutines.channels
                                .Channel<String>(capacity = SESSION_OUTGOING_BUFFER)
                        val writerJob =
                            launch {
                                try {
                                    for (json in outQueue) {
                                        outgoing.send(Frame.Text(json))
                                    }
                                } catch (_: kotlinx.coroutines.channels.ClosedSendChannelException) {
                                    // socket closed — let the handler's
                                    // finally block run normal teardown
                                }
                            }
                        var droppedForBackpressure = false
                        val session =
                            relay.server.connect { json ->
                                val res = outQueue.trySend(json)
                                if (!res.isSuccess && !res.isClosed) {
                                    // Buffer is full → slow client.
                                    // Mark + close the queue; the
                                    // writer drains, then we let the
                                    // outer handler's finally close
                                    // the WS session.
                                    droppedForBackpressure = true
                                    outQueue.close()
                                }
                            }
                        activeSessions.add(session)
                        try {
                            incoming.consumeEach { frame ->
                                if (droppedForBackpressure) return@consumeEach
                                if (frame is Frame.Text) {
                                    session.receive(frame.readText())
                                }
                            }
                        } finally {
                            outQueue.close()
                            writerJob.cancel()
                            activeSessions.remove(session)
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
     * Handles a NIP-86 admin RPC request:
     *  1. 403 if no admin pubkey list is configured (endpoint disabled).
     *  2. 401 if the NIP-98 Authorization header is missing/invalid.
     *  3. 403 if the verified pubkey isn't in [adminAllowList].
     *  4. 400 if the body isn't a valid Nip86Request.
     *  5. 200 with a Nip86Response JSON body otherwise.
     *
     * `application/nostr+json+rpc` is the wire content type prescribed
     * by NIP-86; we send it on responses and accept any body on the
     * request (the auth event's payload-hash already binds the body).
     */
    private suspend fun handleNip86(call: io.ktor.server.application.ApplicationCall) {
        if (adminAllowList.isEmpty()) {
            call.respondText(
                "NIP-86 management API is not enabled on this relay.",
                ContentType.Text.Plain,
                HttpStatusCode.Forbidden,
            )
            return
        }

        // Cap the body BEFORE we read it. We have to read the bytes
        // (NIP-98 payload-hash binds them), but unauthenticated
        // attackers shouldn't be able to stream gigabytes here.
        val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > maxAdminBodyBytes) {
            call.respondText(
                "request body exceeds $maxAdminBodyBytes-byte cap",
                ContentType.Text.Plain,
                HttpStatusCode.PayloadTooLarge,
            )
            return
        }
        val body = readBoundedBody(call, maxAdminBodyBytes) ?: return

        val authHeader = call.request.header(HttpHeaders.Authorization)
        // The URL the client signed must match the relay's CANONICAL
        // public URL — not whatever `Host` header reaches us. An
        // attacker can spoof `Host`, and behind TLS termination the
        // verifier would compare against the wrong scheme. Operators
        // configure [publicUrl] explicitly. The Host fallback is for
        // local loopback unit tests only and is documented as unsafe.
        val signedUrl =
            publicUrl
                ?: ("http://" + (call.request.header(HttpHeaders.Host) ?: "$host:$resolvedPort") + path)
        val verification = nip98.verify(authHeader, method = "POST", url = signedUrl, body = body)

        val pubkey =
            when (verification) {
                is Nip98AuthVerifier.Result.Verified -> {
                    verification.pubkey
                }

                Nip98AuthVerifier.Result.Missing -> {
                    call.response.headers.append(HttpHeaders.WWWAuthenticate, Nip98AuthVerifier.SCHEME.trim())
                    call.respondText(
                        "missing Authorization header (NIP-98)",
                        ContentType.Text.Plain,
                        HttpStatusCode.Unauthorized,
                    )
                    return
                }

                is Nip98AuthVerifier.Result.Malformed -> {
                    call.respondText(
                        "invalid NIP-98 Authorization: ${verification.reason}",
                        ContentType.Text.Plain,
                        HttpStatusCode.Unauthorized,
                    )
                    return
                }
            }

        if (pubkey.lowercase() !in adminAllowList) {
            call.respondText(
                "pubkey is not on the admin list",
                ContentType.Text.Plain,
                HttpStatusCode.Forbidden,
            )
            return
        }

        val req =
            try {
                JsonMapper.fromJson<Nip86Request>(body.decodeToString())
            } catch (e: Exception) {
                call.respondText(
                    "invalid Nip86Request: ${e.message ?: e::class.simpleName}",
                    ContentType.Text.Plain,
                    HttpStatusCode.BadRequest,
                )
                return
            }

        val response: Nip86Response = nip86.dispatch(req)

        // Audit log: structured single line so an operator can grep
        // "nip86" / pubkey / method without a logging framework
        // dependency. Keep it best-effort — System.err is already what
        // the rest of Main.kt uses, and a missing log line shouldn't
        // fail the response.
        runCatching {
            System.err.println(
                "nip86 audit pubkey=$pubkey method=${req.method} ok=${response.error == null}" +
                    (response.error?.let { " error=$it" } ?: ""),
            )
        }

        call.respondText(
            JsonMapper.toJson(response),
            ContentType.parse("application/nostr+json+rpc"),
            HttpStatusCode.OK,
        )
    }

    /**
     * Reads up to [maxBytes] bytes from the request body and returns
     * them. If the stream produces more than [maxBytes] (i.e. a
     * lying or absent `Content-Length`), responds 413 and returns
     * `null` — caller stops handling.
     */
    private suspend fun readBoundedBody(
        call: io.ktor.server.application.ApplicationCall,
        maxBytes: Int,
    ): ByteArray? {
        val ch = call.receiveChannel()
        val buf = ByteArray(maxBytes + 1)
        var pos = 0
        while (pos <= maxBytes) {
            val read = ch.readAvailable(buf, pos, buf.size - pos)
            if (read <= 0) break
            pos += read
        }
        if (pos > maxBytes) {
            call.respondText(
                "request body exceeds $maxBytes-byte cap",
                ContentType.Text.Plain,
                HttpStatusCode.PayloadTooLarge,
            )
            return null
        }
        return buf.copyOfRange(0, pos)
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

    companion object {
        /**
         * Per-session outbound buffer size. When a slow client falls
         * this many frames behind, we close their connection rather
         * than silently dropping further frames (which would corrupt
         * NIP-01 by missing EVENT/EOSE messages).
         */
        const val SESSION_OUTGOING_BUFFER: Int = 1024
    }
}
