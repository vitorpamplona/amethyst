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
private const val EXIT_OK = 0
private const val EXIT_FAIL = 1
private const val EXIT_UNSUPPORTED = 127

private const val HANDSHAKE_TIMEOUT_SEC = 10L
private const val TRANSFER_TIMEOUT_SEC = 30L

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

    // One-line context header. Verbose per-field dump deferred to debug
    // mode (env var QUIC_INTEROP_DEBUG=1) so the runner's aggregated output
    // stays readable across a full matrix run.
    if (System.getenv("QUIC_INTEROP_DEBUG") == "1") {
        System.err.println("== quic-interop client ==")
        System.err.println("testcase:       $testcase")
        System.err.println("requests:       $requests")
        System.err.println("downloads dir:  ${downloadsDir.absolutePath} (exists=${downloadsDir.isDirectory})")
        System.err.println("sslkeylogfile:  ${keyLogPath ?: "(unset)"}")
    }

    val cipherSuites =
        when (testcase) {
            "chacha20" -> intArrayOf(TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256)
            else -> null
        }

    // ALPN per quic-interop-runner convention. Most testcases use
    // `hq-interop` (HTTP/0.9 over QUIC, no H3/QPACK). Only the `http3` and
    // `multiplexing` testcases use `h3`. quic-go-qns enforces this strictly
    // (CRYPTO_ERROR 0x178 / TLS no_application_protocol on mismatch);
    // aioquic and picoquic accept either. Match the per-test convention
    // so we work everywhere.
    val alpn =
        when (testcase) {
            "http3", "multiplexing" -> Alpn.H3
            else -> Alpn.HQ_INTEROP
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
            "handshake", "chacha20", "handshakeloss",
            "transfer", "http3", "multiplexing",
            "transferloss", "transfercorruption", "longrtt", "goodput", "crosstraffic",
            -> {
                runTransferTest(
                    requests = requests,
                    downloadsDir = downloadsDir,
                    cipherSuites = cipherSuites,
                    alpn = alpn,
                    keyLogPath = keyLogPath,
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
    alpn: Alpn,
    keyLogPath: String?,
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
            val conn =
                QuicConnection(
                    serverName = host,
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                    alpnList = listOf(alpn.wireBytes),
                    cipherSuites =
                        cipherSuites
                            ?: intArrayOf(
                                TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256,
                                TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256,
                            ),
                    extraSecretsListener = keyLogger?.listener,
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
                return@runBlocking "handshake_failed"
            }

            val client: GetClient =
                when (alpn) {
                    Alpn.H3 -> Http3GetClient(conn).also { it.init() }
                    Alpn.HQ_INTEROP -> HqInteropGetClient(conn)
                }

            val authority = if (port == 443) host else "$host:$port"
            val outcome =
                withTimeoutOrNull(TRANSFER_TIMEOUT_SEC * 1_000L) {
                    val responses =
                        if (parallel) {
                            // Open every request stream up-front so they
                            // genuinely overlap on the wire — what the
                            // multiplexing testcase verifies.
                            coroutineScope {
                                urls
                                    .map { url -> async { url to client.get(authority, url.path) } }
                                    .map { it.await() }
                            }
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
