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
import com.vitorpamplona.quic.transport.UdpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    System.err.println("== quic-interop client ==")
    System.err.println("testcase: $testcase")
    System.err.println("requests: $requests")

    val code =
        when (testcase) {
            "handshake" -> runHandshakeTest(requests)
            else -> EXIT_UNSUPPORTED
        }
    exitProcess(code)
}

private fun runHandshakeTest(requests: String): Int {
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
            val conn =
                QuicConnection(
                    serverName = host,
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
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
