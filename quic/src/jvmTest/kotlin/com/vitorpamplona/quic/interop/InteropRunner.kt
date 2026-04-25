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
package com.vitorpamplona.quic.interop

import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionConfig
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import com.vitorpamplona.quic.transport.UdpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Standalone interop runner. Drives [QuicConnection] against a real QUIC
 * server (picoquic, quic-go, quiche, nests-rs, etc.) and reports whether
 * the handshake completes.
 *
 * Run via:
 *
 *   ./gradlew :quic:jvmTest --tests '*InteropRunner*' \
 *     -DinteropHost=localhost -DinteropPort=4433
 *
 * Or invoke main() directly from an IDE.
 *
 * The runner uses [PermissiveCertificateValidator] — pointing this at a
 * production server is a security misconfiguration; it's intentionally
 * test-only.
 */
fun main(args: Array<String>) {
    val host = System.getProperty("interopHost") ?: args.getOrNull(0) ?: "127.0.0.1"
    val port = (System.getProperty("interopPort") ?: args.getOrNull(1) ?: "4433").toInt()
    val timeoutSec = (System.getProperty("interopTimeoutSec") ?: "10").toLong()

    println("== :quic interop runner ==")
    println("target:  $host:$port")
    println("timeout: ${timeoutSec}s")
    println()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val outcome =
        runBlocking {
            val socket =
                try {
                    UdpSocket.connect(host, port)
                } catch (t: Throwable) {
                    return@runBlocking InteropOutcome.UdpFailed(t.message ?: t::class.simpleName ?: "?")
                }

            val conn =
                QuicConnection(
                    serverName = host,
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                )
            val driver = QuicConnectionDriver(conn, socket, scope)
            driver.start()

            val handshakeResult =
                withTimeoutOrNull(timeoutSec * 1_000L) {
                    runCatching { conn.awaitHandshake() }
                }
            val outcome =
                when {
                    handshakeResult == null -> {
                        InteropOutcome.Timeout
                    }

                    handshakeResult.isSuccess -> {
                        InteropOutcome.Connected(conn)
                    }

                    else -> {
                        InteropOutcome.HandshakeFailed(
                            handshakeResult.exceptionOrNull()?.message ?: "?",
                        )
                    }
                }
            try {
                driver.close()
            } catch (_: Throwable) {
            }
            outcome
        }

    when (outcome) {
        is InteropOutcome.Connected -> {
            println("✓ HANDSHAKE COMPLETE")
            println("  status:               ${outcome.conn.status}")
            println("  negotiated ALPN:      ${outcome.conn.tls.negotiatedAlpn?.decodeToString()}")
            println("  peer transport params: ${outcome.conn.peerTransportParameters?.let { tpSummary(it) } ?: "(none)"}")
        }

        is InteropOutcome.HandshakeFailed -> {
            println("✗ HANDSHAKE FAILED")
            println("  reason: ${outcome.reason}")
            kotlin.system.exitProcess(1)
        }

        InteropOutcome.Timeout -> {
            println("✗ TIMED OUT after ${timeoutSec}s")
            kotlin.system.exitProcess(1)
        }

        is InteropOutcome.UdpFailed -> {
            println("✗ UDP CONNECT FAILED")
            println("  reason: ${outcome.reason}")
            kotlin.system.exitProcess(1)
        }
    }
}

private fun tpSummary(tp: com.vitorpamplona.quic.connection.TransportParameters): String =
    listOfNotNull(
        tp.initialMaxData?.let { "max_data=$it" },
        tp.initialMaxStreamsBidi?.let { "max_streams_bidi=$it" },
        tp.initialMaxStreamsUni?.let { "max_streams_uni=$it" },
        tp.maxIdleTimeoutMillis?.let { "idle_timeout=${it}ms" },
        tp.maxDatagramFrameSize?.let { "max_datagram=$it" },
    ).joinToString(", ")

private sealed class InteropOutcome {
    data class Connected(
        val conn: QuicConnection,
    ) : InteropOutcome()

    data class HandshakeFailed(
        val reason: String,
    ) : InteropOutcome()

    data class UdpFailed(
        val reason: String,
    ) : InteropOutcome()

    object Timeout : InteropOutcome()
}
