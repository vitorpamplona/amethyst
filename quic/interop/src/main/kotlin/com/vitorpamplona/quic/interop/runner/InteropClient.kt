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
package com.vitorpamplona.quic.interop.runner

import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionConfig
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import com.vitorpamplona.quic.tls.TlsConstants
import com.vitorpamplona.quic.tls.TlsSecretsListener
import com.vitorpamplona.quic.transport.UdpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

/**
 * Endpoint that implements the [quic-interop-runner](https://github.com/quic-interop/quic-interop-runner)
 * Docker contract. Reads the env-var protocol the runner pushes into each
 * client container and dispatches by [TESTCASE].
 *
 * Exit codes:
 *  - `0`   — testcase passed
 *  - `1`   — testcase failed (bug in our impl, OR partner)
 *  - `127` — testcase not implemented (runner skips, doesn't fail)
 */
private fun nowMs(): Long = System.currentTimeMillis()

private const val EXIT_OK = 0
private const val EXIT_FAIL = 1
private const val EXIT_UNSUPPORTED = 127

private const val HANDSHAKE_TIMEOUT_SEC = 10L

// Multiplexing generates ~hundreds-to-thousands of small files; download
// throughput on Mac+Rosetta is dominated by Docker filesystem overhead
// per-write. 30s wasn't enough for the larger file counts; the qlog
// against aioquic showed us still actively receiving STREAM frames at
// t=31s when our local timeout fired. 60s gives more headroom without
// inflating turnaround for the cases that actually complete fast.
private const val TRANSFER_TIMEOUT_SEC = 60L

// Per-stream timeout in the parallel-multiplexing path. If any single
// GET hangs past this (e.g. its FIN was lost in the shuffle of
// hundreds of concurrent streams), the await on that future returns
// a status=0 response and the others continue. Without this, a single
// stuck stream would block the whole .map { it.await() } chain.
private const val PER_STREAM_TIMEOUT_SEC = 20L

// Concurrency cap for the parallel-multiplexing path. Each chunk of this
// many requests fires fully in parallel; chunks process sequentially.
// Sized so that conn.lock contention stays manageable while still
// satisfying the runner's "streams overlap on the wire" check (which
// only needs a handful of streams concurrent at any given moment, not
// all of them simultaneously). Empirically validated against aioquic +
// picoquic at this value.
private const val MULTIPLEX_PARALLELISM = 64

fun main() {
    val role = System.getenv("ROLE") ?: "client"
    if (role != "client") {
        System.err.println("server role not implemented")
        exitProcess(EXIT_UNSUPPORTED)
    }

    val testcase = System.getenv("TESTCASE")?.trim().orEmpty()
    val requests = System.getenv("REQUESTS")?.trim().orEmpty()
    // The runner mounts $CLIENT_DOWNLOADS to /downloads as a Docker volume
    // (see quic-interop-runner's docker-compose.yml `client.volumes`). It
    // does NOT export a DOWNLOADS env var. Hard-code the mount path.
    val downloadsDir = File("/downloads")
    val keyLogPath = System.getenv("SSLKEYLOGFILE")?.takeIf { it.isNotBlank() }
    val qlogDir = System.getenv("QLOGDIR")?.takeIf { it.isNotBlank() }?.let { File(it) }

    // One-line context header. Verbose per-field dump deferred to debug
    // mode (env var QUIC_INTEROP_DEBUG=1) so the runner's aggregated output
    // stays readable across a full matrix run.
    if (System.getenv("QUIC_INTEROP_DEBUG") == "1") {
        System.err.println("== quic-interop client ==")
        System.err.println("testcase:       $testcase")
        System.err.println("requests:       $requests")
        System.err.println("downloads dir:  ${downloadsDir.absolutePath} (exists=${downloadsDir.isDirectory})")
        System.err.println("sslkeylogfile:  ${keyLogPath ?: "(unset)"}")
        System.err.println("qlogdir:        ${qlogDir?.absolutePath ?: "(unset)"}")
    }

    val cipherSuites =
        when (testcase) {
            "chacha20" -> intArrayOf(TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256)
            else -> null
        }

    // For the versionnegotiation testcase the runner expects us to send
    // an Initial advertising a version the server doesn't support, then
    // honor its VN response by retrying with v1. agent A's
    // QuicVersion.FORCE_VERSION_NEGOTIATION drives that flow.
    val initialVersion =
        when (testcase) {
            "versionnegotiation" -> com.vitorpamplona.quic.packet.QuicVersion.FORCE_VERSION_NEGOTIATION
            else -> com.vitorpamplona.quic.packet.QuicVersion.V1
        }

    // ALPN selection. Different servers configure different ALPNs PER
    // testcase, with no consistent convention:
    //   - quic-go-qns         — strictly hq-interop for non-http3 tests
    //   - aioquic-qns         — accepts either
    //   - picoquic-qns        — strictly h3 for ALL testcases
    //
    // Solution: offer BOTH `h3` and `hq-interop` in the ClientHello. TLS
    // ALPN negotiation lets the server pick whichever matches its config.
    // For testcases that REQUIRE H3 framing (http3, multiplexing) we
    // restrict to h3 so any server that picks hq-interop fails fast.
    val offeredAlpns =
        when (testcase) {
            "http3", "multiplexing" -> listOf(Alpn.H3)
            else -> listOf(Alpn.HQ_INTEROP, Alpn.H3)
        }

    val code =
        when (testcase) {
            // All these testcases require: successful handshake + file
            // transferred to /downloads. Per-testcase notes:
            //   chacha20            — runner verifies the negotiated cipher
            //                         was ChaCha20-Poly1305 via tshark on the
            //                         pcap (decrypted with SSLKEYLOGFILE).
            //   handshakeloss/      — same client behaviour against the
            //   transferloss          runner's sim with random packet loss.
            //   transfercorruption  — random bit-flips (recovery via
            //                         AEAD AUTH FAIL → drop + retransmit).
            //   longrtt             — emulated high-latency link.
            //   goodput / crosstraffic — throughput-floor / competing-flow
            //                         scenarios.
            //   multiplexing        — H3 GETs issued in parallel; runner
            //                         verifies overlap on the wire via tshark.
            //   retry               — server sends a Retry packet first; our
            //                         applyRetry path (RFC 9000 §17.2.5 +
            //                         RFC 9001 §5.8) handles DCID swap +
            //                         token threading + key re-derivation.
            //   ipv6                — same flow over an IPv6 socket;
            //                         JDK DatagramChannel.connect handles
            //                         the v6 address resolution natively.
            "handshake", "chacha20", "handshakeloss",
            "transfer", "http3", "multiplexing",
            "transferloss", "transfercorruption", "longrtt", "goodput", "crosstraffic",
            "retry", "ipv6",
            // NOTE: the runner does NOT have a `versionnegotiation` testcase
            // (its Available list excludes it). The :quic VN-handling code
            // (applyVersionNegotiation, FORCE_VERSION_NEGOTIATION constant)
            // stays as defensive support for any server that decides to
            // send a VN packet at us, but no testcase exercises it directly.
            -> {
                runTransferTest(
                    requests = requests,
                    downloadsDir = downloadsDir,
                    cipherSuites = cipherSuites,
                    offeredAlpns = offeredAlpns,
                    initialVersion = initialVersion,
                    keyLogPath = keyLogPath,
                    qlogDir = qlogDir,
                    parallel = (testcase == "multiplexing"),
                )
            }

            else -> {
                EXIT_UNSUPPORTED
            }
        }
    exitProcess(code)
}

internal enum class Alpn(
    val wireBytes: ByteArray,
) {
    /** RFC 9114 — full HTTP/3 + QPACK + H3 framing. */
    H3("h3".encodeToByteArray()),

    /** quic-interop-runner convention — HTTP/0.9 over QUIC. Just `GET /path\r\n`
     *  on a fresh bidi stream, server returns the body, FIN both sides. No
     *  control stream, no QPACK, no SETTINGS. Used for handshake / chacha20 /
     *  transfer / loss-variant testcases. */
    HQ_INTEROP("hq-interop".encodeToByteArray()),
}

private fun runTransferTest(
    requests: String,
    downloadsDir: File,
    cipherSuites: IntArray?,
    offeredAlpns: List<Alpn>,
    initialVersion: Int,
    keyLogPath: String?,
    qlogDir: File?,
    parallel: Boolean,
): Int {
    val urls =
        requests
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { runCatching { URI(it) }.getOrNull() }
            .filter { it != null && it.host != null }
            .map { it!! }
    if (urls.isEmpty()) {
        System.err.println("no parseable URL in REQUESTS")
        return EXIT_FAIL
    }
    val first = urls[0]
    val host = first.host
    val port = first.port.takeIf { it > 0 } ?: 443

    downloadsDir.mkdirs()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val outcome =
        runBlocking {
            val socket =
                try {
                    UdpSocket.connect(host, port)
                } catch (t: Throwable) {
                    return@runBlocking "udp_failed: ${t.message ?: t::class.simpleName}"
                }
            val keyLogger = keyLogPath?.let { SslKeyLogger(File(it)) }
            val qlogWriter =
                qlogDir?.let { dir ->
                    dir.mkdirs()
                    // ODCID is unknown until the connection generates one in
                    // its init block; we'd need to plumb through, but for the
                    // header it's fine to start with a placeholder and the
                    // packet-sent events will carry SCID/DCID anyway.
                    QlogWriter(file = File(dir, "client.sqlog"), odcidHex = "client")
                }
            val conn =
                QuicConnection(
                    serverName = host,
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                    alpnList = offeredAlpns.map { it.wireBytes },
                    initialVersion = initialVersion,
                    cipherSuites =
                        cipherSuites
                            ?: intArrayOf(
                                TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256,
                                TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256,
                            ),
                    extraSecretsListener = keyLogger?.listener,
                    qlogObserver = qlogWriter ?: com.vitorpamplona.quic.observability.QlogObserver.NoOp,
                )
            val driver = QuicConnectionDriver(conn, socket, scope)
            driver.start()

            val handshake =
                withTimeoutOrNull(HANDSHAKE_TIMEOUT_SEC * 1_000L) {
                    runCatching { conn.awaitHandshake() }
                }
            if (handshake == null || handshake.isFailure) {
                runCatching { driver.close() }
                conn.tls.clientRandom?.let { keyLogger?.flush(it) }
                runCatching { qlogWriter?.close() }
                return@runBlocking "handshake_failed"
            }

            // Dispatch the GET client by what the server actually picked.
            // If the server didn't pick or picked something unrecognized,
            // fall back to HQ-interop (simpler, more permissive).
            val negotiated =
                conn.tls.negotiatedAlpn
                    ?.decodeToString()
                    .orEmpty()
            val client: GetClient =
                when (negotiated) {
                    "h3" -> {
                        Http3GetClient(conn, driver).also { it.init(scope) }
                    }

                    "hq-interop" -> {
                        HqInteropGetClient(conn, driver)
                    }

                    else -> {
                        System.err.println("unrecognized negotiated ALPN '$negotiated'; defaulting to hq-interop")
                        HqInteropGetClient(conn, driver)
                    }
                }

            val authority = if (port == 443) host else "$host:$port"
            val outcome =
                withTimeoutOrNull(TRANSFER_TIMEOUT_SEC * 1_000L) {
                    val responses =
                        if (parallel) {
                            // Multiplexing throughput note. Spawning 1999
                            // simultaneous coroutines all racing the same
                            // conn.lock cratered throughput (~23 streams/sec
                            // on Mac+Rosetta, qlog-measured against aioquic).
                            // Lock contention scales superlinearly with
                            // suspended coroutines: every drainOutbound
                            // walks streamsList O(N), every openBidiStream
                            // queues behind every other waiter, and the
                            // dispatcher thrashes context-switching.
                            //
                            // Bound concurrency: process in chunks of
                            // [MULTIPLEX_PARALLELISM]. Each chunk is
                            // batched in two phases:
                            //   1. SERIAL prepareRequest for every URL
                            //      in the chunk — opens the bidi
                            //      stream, encodes the request, FINs.
                            //      Synchronous; no async / no per-call
                            //      wakeup.
                            //   2. SINGLE driver.wakeup() so the send
                            //      loop drains all 64 enqueued requests
                            //      in coalesced packets (multi-stream
                            //      framing per drain) instead of one
                            //      tiny packet per stream.
                            //   3. PARALLEL await — one async per
                            //      stream collects its response with
                            //      a per-stream timeout so a hung
                            //      stream surfaces as status=0 instead
                            //      of blocking its peers.
                            //
                            // Earlier shape (per-call wakeup inside
                            // client.get()) produced ~23 streams/sec
                            // because each individual enqueue tripped
                            // the send loop, which then drained alone
                            // (the other 63 coroutines hadn't queued
                            // yet on the dispatcher). Result: one
                            // ~80-byte packet per stream instead of
                            // ~10 streams/packet. Coalescing recovered
                            // by batching enqueues + single wake.
                            val collected = mutableListOf<Pair<URI, GetResponse>>()
                            // Quiet by default. QUIC_INTEROP_DEBUG=1 emits one
                            // line per chunk to stderr — wall-clock split between
                            // "all enqueued" and "all responded" lets us see
                            // whether time is spent in the writer (lots of ms
                            // before responses start arriving) or the server
                            // (responses dribble in over a long stretch).
                            val debug = System.getenv("QUIC_INTEROP_DEBUG") == "1"
                            val transferStartMs = nowMs()
                            urls.chunked(MULTIPLEX_PARALLELISM).forEachIndexed { chunkIdx, chunk ->
                                val chunkStartMs = nowMs()
                                // Single lock-held batch open + enqueue.
                                // Without this, openBidiStream's per-call
                                // lock acquire / release lets the send loop
                                // interject between opens and drain one
                                // stream per packet.
                                val handles = client.prepareRequests(authority, chunk.map { it.path })
                                driver.wakeup()
                                val enqueuedMs = nowMs()
                                coroutineScope {
                                    val deferreds =
                                        chunk.zip(handles).map { (url, handle) ->
                                            async {
                                                val resp =
                                                    withTimeoutOrNull(PER_STREAM_TIMEOUT_SEC * 1_000L) {
                                                        client.awaitResponse(handle)
                                                    }
                                                url to (resp ?: GetResponse(status = 0, body = ByteArray(0)))
                                            }
                                        }
                                    deferreds.forEach { collected += it.await() }
                                }
                                if (debug) {
                                    val doneMs = nowMs()
                                    System.err.println(
                                        "[interop] chunk=$chunkIdx size=${chunk.size} " +
                                            "enqueue=${enqueuedMs - chunkStartMs}ms " +
                                            "responses=${doneMs - enqueuedMs}ms " +
                                            "cumulative=${doneMs - transferStartMs}ms",
                                    )
                                }
                            }
                            collected
                        } else {
                            urls.map { url -> url to client.get(authority, url.path) }
                        }
                    var anyFailed = false
                    for ((url, resp) in responses) {
                        if (resp.status != 200) {
                            System.err.println("GET ${url.path} → status ${resp.status}")
                            anyFailed = true
                            continue
                        }
                        val name = url.path.substringAfterLast('/').ifBlank { "index" }
                        File(downloadsDir, name).writeBytes(resp.body)
                    }
                    if (anyFailed) "request_failed" else "ok"
                } ?: "transfer_timeout"

            runCatching { driver.close() }
            conn.tls.clientRandom?.let { keyLogger?.flush(it) }
            runCatching { qlogWriter?.close() }
            delay(50)
            outcome
        }
    scope.cancel()

    return if (outcome == "ok") {
        EXIT_OK
    } else {
        System.err.println("transfer $outcome")
        EXIT_FAIL
    }
}

/**
 * Writes [NSS Key Log Format](https://firefox-source-docs.mozilla.org/security/nss/legacy/key_log_format/index.html)
 * lines so Wireshark can decrypt the sim's captured pcap.
 *
 *   CLIENT_HANDSHAKE_TRAFFIC_SECRET <client_random_hex> <secret_hex>
 *   SERVER_HANDSHAKE_TRAFFIC_SECRET <client_random_hex> <secret_hex>
 *   CLIENT_TRAFFIC_SECRET_0 <client_random_hex> <secret_hex>
 *   SERVER_TRAFFIC_SECRET_0 <client_random_hex> <secret_hex>
 */
internal class SslKeyLogger(
    private val file: File,
) {
    private val pending = mutableListOf<Pair<String, ByteArray>>()

    val listener: TlsSecretsListener =
        object : TlsSecretsListener {
            override fun onHandshakeKeysReady(
                cipherSuite: Int,
                clientSecret: ByteArray,
                serverSecret: ByteArray,
            ) {
                pending += "CLIENT_HANDSHAKE_TRAFFIC_SECRET" to clientSecret
                pending += "SERVER_HANDSHAKE_TRAFFIC_SECRET" to serverSecret
            }

            override fun onApplicationKeysReady(
                cipherSuite: Int,
                clientSecret: ByteArray,
                serverSecret: ByteArray,
            ) {
                pending += "CLIENT_TRAFFIC_SECRET_0" to clientSecret
                pending += "SERVER_TRAFFIC_SECRET_0" to serverSecret
            }

            override fun onHandshakeComplete() = Unit
        }

    fun flush(clientRandom: ByteArray) {
        val randomHex = clientRandom.toHex()
        file.parentFile?.mkdirs()
        file.appendText(
            buildString {
                for ((label, secret) in pending) {
                    append(label)
                        .append(' ')
                        .append(randomHex)
                        .append(' ')
                        .append(secret.toHex())
                        .append('\n')
                }
            },
        )
        pending.clear()
    }
}

internal fun ByteArray.toHex(): String =
    buildString(size * 2) {
        for (b in this@toHex) {
            val v = b.toInt() and 0xff
            append("0123456789abcdef"[v ushr 4])
            append("0123456789abcdef"[v and 0xf])
        }
    }
