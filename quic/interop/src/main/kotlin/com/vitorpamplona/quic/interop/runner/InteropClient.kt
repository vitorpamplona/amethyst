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

// Multiconnect's per-iteration handshake timeout. The runner's
// handshakeloss / handshakecorruption tests run 50 sequential
// connections under 30% packet drop / bit-flip with burst=3, then
// verify the pcap contains exactly 50 distinct handshakes. Under
// that loss intensity, individual handshakes routinely need three or
// four PTO rounds (1s + 2s + 4s + 8s = 15s of doublings) before
// either side's flight finally lands. The 10s default leaves us
// declaring "handshake_failed" mid-recovery on the unlucky iter.
// 30s gives clean PTO headroom while staying under the runner's
// 300s testcase budget for 50 iters (avg ~5s/iter typical, ≤30s
// worst-case before we give up). Same threshold used for the
// per-iter transfer; the file is 1KB but its STREAM frames have
// to land through the same lossy path.
private const val MULTICONNECT_HANDSHAKE_TIMEOUT_SEC = 30L

// Multiconnect transfer-side per-iter budget. Larger than the
// handshake budget because once 1-RTT keys are up our smoothed_rtt
// reflects the loss-recovery cost — RFC 9002 §5.2 takes the RTT
// sample from the largest-acked packet's send time, and that's the
// PTO-retransmit that finally landed, not the original send. So an
// iteration whose handshake recovered after 2-3 PTO rounds will
// have smoothed_rtt ~1s, which doubles the post-handshake PTO into
// 3s+. Three doublings under 30% bit-flip can hit 24s before our
// GET request is acked, so 30s is right on the cliff. 60s gives
// the slow-recovery iterations real headroom; 50 iters × ~3s
// average + a few slow ones × 60s still fits the runner's 300s
// testcase budget.
private const val MULTICONNECT_TRANSFER_TIMEOUT_SEC = 60L

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
    // Single env-var check, propagated to library code that opts into
    // verbose tracing only when this is set.
    val debugEnv = System.getenv("QUIC_INTEROP_DEBUG")
    if (debugEnv == "1") {
        com.vitorpamplona.quic.connection.writerDebugEnabled = true
        System.err.println(
            "[boot] DEBUG=1; writerDebugEnabled=true; build_id=" +
                "${com.vitorpamplona.quic.connection.WRITER_DEBUG_BUILD_ID}; " +
                "TESTCASE=${System.getenv("TESTCASE") ?: "(unset)"}; " +
                "ROLE=${System.getenv("ROLE") ?: "(unset)"}",
        )
    } else {
        System.err.println("[boot] DEBUG=${debugEnv ?: "(unset)"} writerDebugEnabled=false")
    }

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
            //   ecn                 — runner verifies ECN-CE counts in
            //                         the pcap. Client just does a 100KB
            //                         transfer; the IP-layer ECT codepoint
            //                         is set by the sim and we don't
            //                         need to do anything special.
            //   amplificationlimit  — runner verifies server obeys 3x
            //                         amplification limit. Pure server
            //                         check — client does a normal
            //                         transfer (the runner sets
            //                         TESTCASE_CLIENT=transfer).
            //   blackhole           — sim drops ALL packets for several
            //                         seconds mid-transfer; client must
            //                         resume after blackhole ends. Our
            //                         PTO + retransmit handles this; the
            //                         runner sets TESTCASE_CLIENT=transfer.
            //   keyupdate           — server initiates a 1-RTT key update
            //                         mid-transfer (KEY_PHASE bit flips).
            //                         Our RFC 9001 §6 receive-side key
            //                         update lands the rotation; runner
            //                         verifies the pcap shows packets
            //                         in both phases. Server-side test
            //                         from our perspective.
            "handshake", "chacha20",
            "transfer", "http3", "multiplexing",
            "transferloss", "transfercorruption", "longrtt", "goodput", "crosstraffic",
            "retry", "ipv6",
            "ecn", "amplificationlimit", "blackhole",
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
                    // The runner reuses TESTCASE_CLIENT=transfer for the
                    // multiplexing testcase — discrimination is by URL
                    // count, not testcase name. We were checking
                    // testcase == "multiplexing" which is NEVER true
                    // (we'd see TESTCASE=multiplexing only on a
                    // hypothetical client where the runner explicitly
                    // sets it). Symptom: 60s timeout with 1421/2000
                    // files at 1 stream / RTT — exactly the serial
                    // client.get(...) path. Confirmed via the boot log:
                    //   [boot] TESTCASE=transfer; transfer mode:
                    //   parallel=false urls=1999
                    //
                    // Cheap whitespace tokenization here just counts;
                    // runTransferTest re-parses into URI[] inside.
                    parallel = requests.split(Regex("\\s+")).count { it.isNotBlank() } > 1,
                )
            }

            // keyupdate: same transfer flow but the client initiates a
            // RFC 9001 §6 1-RTT key update once the handshake is
            // confirmed. Runner verifies pcap shows BOTH client and
            // server emitting packets in phase 1 — without our side
            // initiating, aioquic's plain-transfer server doesn't
            // rotate spontaneously and the test fails with "Expected
            // to see packets sent with key phase 1 from both client
            // and server".
            "keyupdate" -> {
                runTransferTest(
                    requests = requests,
                    downloadsDir = downloadsDir,
                    cipherSuites = cipherSuites,
                    offeredAlpns = offeredAlpns,
                    initialVersion = initialVersion,
                    keyLogPath = keyLogPath,
                    qlogDir = qlogDir,
                    parallel = requests.split(Regex("\\s+")).count { it.isNotBlank() } > 1,
                    initiateKeyUpdate = true,
                )
            }

            // The runner reuses TESTCASE_CLIENT=multiconnect for the
            // handshakeloss + handshakecorruption tests (see
            // testcases_quic.py:746). Each URL must be fetched on a fresh
            // QUIC connection — the testcase verifies via tshark that
            // the pcap contains _num_runs (50) distinct handshakes. We
            // could not satisfy this through runTransferTest (one
            // connection, many GETs); fixing required a separate per-
            // URL connection loop.
            "multiconnect" -> {
                runMulticonnectTest(
                    requests = requests,
                    downloadsDir = downloadsDir,
                    cipherSuites = cipherSuites,
                    offeredAlpns = offeredAlpns,
                    initialVersion = initialVersion,
                    keyLogPath = keyLogPath,
                    qlogDir = qlogDir,
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
    initiateKeyUpdate: Boolean = false,
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
                    config =
                        QuicConnectionConfig(
                            // Interop stress sizes — push the receive
                            // window past the largest single-file transfer
                            // any testcase exercises (longrtt does 5 MB,
                            // transferloss / transfercorruption do up to
                            // a few MB). Without this, the peer sends
                            // up to initialMaxStreamDataBidiLocal then
                            // stalls until our parser sends a
                            // MAX_STREAM_DATA — at high RTT (longrtt
                            // is 750 ms one-way) each stall is ~1.5 s
                            // round-trip lost. Setting both connection-
                            // and stream-level windows to 32 MB lets
                            // the peer's CC alone determine throughput.
                            initialMaxData = 32L * 1024 * 1024,
                            initialMaxStreamDataBidiLocal = 32L * 1024 * 1024,
                            initialMaxStreamDataBidiRemote = 32L * 1024 * 1024,
                            initialMaxStreamDataUni = 32L * 1024 * 1024,
                        ),
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
            // Unconditional one-shot log so we can confirm which branch
            // runs even when DEBUG=0 — this is a control-flow boundary,
            // not a hot-path trace.
            System.err.println("[boot] transfer mode: parallel=$parallel urls=${urls.size}")

            // RFC 9001 §6 keyupdate testcase: the runner verifies the pcap
            // shows packets in BOTH key phases from BOTH sides. Without
            // initiating from our side, only the server's natural rotation
            // (if any) would show — aioquic's transfer-server doesn't
            // initiate, so we'd see only phase 0. Initiate after the
            // handshake is confirmed (HANDSHAKE_DONE → status=CONNECTED,
            // RFC 9001 §6.5 prerequisite) but BEFORE we send the GET so the
            // request itself is in phase 1 — the server's response then
            // mirrors phase 1, satisfying the runner's check. Brief poll
            // for status because awaitHandshake returns on TLS-done
            // (1-RTT keys derived) which is one ack ahead of HANDSHAKE_DONE
            // arriving.
            if (initiateKeyUpdate) {
                withTimeoutOrNull(2_000L) {
                    while (conn.status != QuicConnection.Status.CONNECTED) delay(10)
                }
                conn.initiateKeyUpdate()
                System.err.println("[boot] keyupdate: client initiated rotation to phase 1")
            }

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
                            if (debug) {
                                System.err.println(
                                    "[interop] multiplex start: total_urls=${urls.size} " +
                                        "MULTIPLEX_PARALLELISM=$MULTIPLEX_PARALLELISM " +
                                        "expected_chunks=${(urls.size + MULTIPLEX_PARALLELISM - 1) / MULTIPLEX_PARALLELISM}",
                                )
                            }
                            // Pace stream creation against peer's MAX_STREAMS_BIDI budget.
                            // Pre-fix this used a fixed [MULTIPLEX_PARALLELISM]=64
                            // chunk, which exceeded quic-go's tighter
                            // initial_max_streams_bidi=100 cap on the second chunk
                            // (cumulative used=128 > limit=108-ish). Throws
                            // QuicStreamLimitException, kills the test.
                            // aioquic + picoquic advertise 128 so we never noticed.
                            //
                            // Now: each iteration takes the smaller of MULTIPLEX_PARALLELISM
                            // and the live budget (peer cap minus what we've already
                            // consumed). When budget=0, poll briefly waiting for the
                            // peer's MAX_STREAMS_BIDI bump — the peer extends the
                            // limit as our completed streams retire from their
                            // bookkeeping.
                            val totalUrls = urls.size
                            var chunkIdx = 0
                            var remaining = urls
                            while (remaining.isNotEmpty()) {
                                val budget =
                                    (conn.peerMaxStreamsBidiSnapshot() - conn.localBidiStreamsUsedSnapshot())
                                        .toInt()
                                        .coerceAtLeast(0)
                                if (budget == 0) {
                                    // Brief idle while peer's MAX_STREAMS catches up. 50 ms is
                                    // arbitrary but small enough that the matrix budget can
                                    // absorb several rounds, large enough that we don't burn
                                    // CPU spinning. If the test budget runs out before the peer
                                    // bumps the cap, the outer withTimeoutOrNull surfaces it
                                    // as transfer_timeout (clear signal vs a deadlock-looking
                                    // hang).
                                    delay(50)
                                    continue
                                }
                                val take = minOf(MULTIPLEX_PARALLELISM, budget, remaining.size)
                                val chunk = remaining.subList(0, take)
                                remaining = remaining.subList(take, remaining.size)
                                val chunkStartMs = nowMs()
                                if (debug && chunkIdx < 3) {
                                    System.err.println(
                                        "[interop] chunk=$chunkIdx size=${chunk.size} budget=$budget starting prepareRequests",
                                    )
                                }
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
                                            "cumulative=${doneMs - transferStartMs}ms " +
                                            "completed=${totalUrls - remaining.size}/$totalUrls",
                                    )
                                }
                                chunkIdx += 1
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
 * One QUIC connection per URL (handshakeloss / handshakecorruption).
 *
 * The runner's `multiconnect` testcase generates N small files (typ. 50 × 1 KB)
 * and expects N independent handshakes in the pcap. Each iteration creates a
 * fresh socket + connection + driver, performs a single HQ-interop GET,
 * writes the body to /downloads, and closes. The whole loop runs serially —
 * concurrency would only help under a much wider test budget than the runner
 * gives us, and serial keeps the pcap easy to read.
 *
 * Per-iteration qlog files land at `$QLOGDIR/client-N.sqlog` so a failed run
 * leaves a trace for the specific connection that failed; SSL-key-log lines
 * accumulate in a single file (Wireshark de-dupes by client_random).
 */
private fun runMulticonnectTest(
    requests: String,
    downloadsDir: File,
    cipherSuites: IntArray?,
    offeredAlpns: List<Alpn>,
    initialVersion: Int,
    keyLogPath: String?,
    qlogDir: File?,
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

    downloadsDir.mkdirs()
    val keyLogger = keyLogPath?.let { SslKeyLogger(File(it)) }
    qlogDir?.mkdirs()

    System.err.println("[boot] multiconnect: urls=${urls.size}")

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val outcome =
        runBlocking {
            for ((idx, url) in urls.withIndex()) {
                val host = url.host
                val port = url.port.takeIf { it > 0 } ?: 443
                val authority = if (port == 443) host else "$host:$port"

                val socket =
                    try {
                        UdpSocket.connect(host, port)
                    } catch (t: Throwable) {
                        return@runBlocking "udp_failed[$idx]: ${t.message ?: t::class.simpleName}"
                    }
                val qlogWriter =
                    qlogDir?.let { dir ->
                        QlogWriter(file = File(dir, "client-$idx.sqlog"), odcidHex = "client$idx")
                    }
                val conn =
                    QuicConnection(
                        serverName = host,
                        config =
                            QuicConnectionConfig(
                                // Same window sizing as runTransferTest;
                                // multiconnect's per-conn payload is tiny but
                                // matching keeps RTT-stall behavior identical
                                // across testcases for easier triangulation.
                                initialMaxData = 32L * 1024 * 1024,
                                initialMaxStreamDataBidiLocal = 32L * 1024 * 1024,
                                initialMaxStreamDataBidiRemote = 32L * 1024 * 1024,
                                initialMaxStreamDataUni = 32L * 1024 * 1024,
                            ),
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
                    withTimeoutOrNull(MULTICONNECT_HANDSHAKE_TIMEOUT_SEC * 1_000L) {
                        runCatching { conn.awaitHandshake() }
                    }
                if (handshake == null || handshake.isFailure) {
                    runCatching { driver.close() }
                    conn.tls.clientRandom?.let { keyLogger?.flush(it) }
                    runCatching { qlogWriter?.close() }
                    return@runBlocking "handshake_failed[$idx]"
                }

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
                            System.err.println("[multiconnect:$idx] unrecognized ALPN '$negotiated'; defaulting to hq-interop")
                            HqInteropGetClient(conn, driver)
                        }
                    }

                val resp =
                    withTimeoutOrNull(MULTICONNECT_TRANSFER_TIMEOUT_SEC * 1_000L) {
                        client.get(authority, url.path)
                    }

                if (resp == null) {
                    runCatching { driver.close() }
                    conn.tls.clientRandom?.let { keyLogger?.flush(it) }
                    runCatching { qlogWriter?.close() }
                    return@runBlocking "transfer_timeout[$idx]"
                }
                if (resp.status != 200) {
                    System.err.println("[multiconnect:$idx] GET ${url.path} → status ${resp.status}")
                    runCatching { driver.close() }
                    conn.tls.clientRandom?.let { keyLogger?.flush(it) }
                    runCatching { qlogWriter?.close() }
                    return@runBlocking "request_failed[$idx]"
                }
                val name = url.path.substringAfterLast('/').ifBlank { "index" }
                File(downloadsDir, name).writeBytes(resp.body)

                runCatching { driver.close() }
                conn.tls.clientRandom?.let { keyLogger?.flush(it) }
                runCatching { qlogWriter?.close() }
                // Tiny breather so the just-closed driver / socket release
                // their resources before the next iteration grabs a new
                // ephemeral UDP port. Without this, the kernel occasionally
                // hands the same port back before the OS ARP cache has
                // settled and the sim drops the first Initial.
                delay(50)
            }
            "ok"
        }
    scope.cancel()

    return if (outcome == "ok") {
        EXIT_OK
    } else {
        System.err.println("multiconnect $outcome")
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
