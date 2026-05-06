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
import kotlinx.coroutines.cancel
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

fun main() {
    val role = System.getenv("ROLE") ?: "client"
    if (role != "client") {
        System.err.println("server role not implemented")
        exitProcess(EXIT_UNSUPPORTED)
    }

    val testcase = System.getenv("TESTCASE")?.trim().orEmpty()
    val requests = System.getenv("REQUESTS")?.trim().orEmpty()
    val keyLogPath = System.getenv("SSLKEYLOGFILE")?.takeIf { it.isNotBlank() }

    System.err.println("== quic-interop client ==")
    System.err.println("testcase:       $testcase")
    System.err.println("requests:       $requests")
    System.err.println("sslkeylogfile:  ${keyLogPath ?: "(unset)"}")

    val cipherSuites =
        when (testcase) {
            "chacha20" -> intArrayOf(TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256)
            else -> null
        }

    val code =
        when (testcase) {
            // Both `handshake` and `chacha20` only require the handshake to
            // complete; `chacha20` adds the constraint that we offered only
            // ChaCha20-Poly1305 (verified by the runner via tshark on the
            // sim's pcap, decrypted using SSLKEYLOGFILE).
            "handshake", "chacha20" -> runHandshakeTest(requests, cipherSuites, keyLogPath)

            else -> EXIT_UNSUPPORTED
        }
    exitProcess(code)
}

private fun runHandshakeTest(
    requests: String,
    cipherSuites: IntArray?,
    keyLogPath: String?,
): Int {
    val target =
        parseFirstTarget(requests) ?: run {
            System.err.println("no parseable target in REQUESTS")
            return EXIT_FAIL
        }
    val (host, port) = target
    System.err.println("handshake target: $host:$port")

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
            val result =
                when {
                    handshake == null -> "timeout"
                    handshake.isSuccess -> "ok"
                    else -> "failed: ${handshake.exceptionOrNull()?.message ?: "?"}"
                }
            runCatching { driver.close() }
            conn.tls.clientRandom?.let { keyLogger?.flush(it) }
            delay(50)
            result
        }
    scope.cancel()

    return if (outcome == "ok") {
        System.err.println("handshake ok")
        EXIT_OK
    } else {
        System.err.println("handshake $outcome")
        EXIT_FAIL
    }
}

private fun parseFirstTarget(requests: String): Pair<String, Int>? {
    val first = requests.split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: return null
    val uri =
        try {
            URI(first)
        } catch (_: Throwable) {
            return null
        }
    val host = uri.host ?: return null
    val port = uri.port.takeIf { it > 0 } ?: 443
    return host to port
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
