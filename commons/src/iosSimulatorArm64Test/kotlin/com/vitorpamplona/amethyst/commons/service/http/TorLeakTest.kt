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

import com.vitorpamplona.amethyst.commons.video.hls.FakeHlsServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The B12f leak checks that run on the simulator (plans/2026-11-25-ios-tor.md, B12a item 4): the
 * Tor client the app builds ([darwinHttpClient] with a SOCKS port) must fail, never go direct,
 * whatever way the proxy fails, and a SOCKS listener that dies under a client (iOS reclaiming it in
 * a suspension) must leave that client failing closed until the listener is bound again.
 *
 * The hosts are under `.invalid`, which only the proxy is ever told: every CONNECT must carry the
 * name (ATYP 0x03), and a failure must never be NSURLSession's "cannot find host" (-1003), which is
 * what a fallback to a direct connection gets, since it would resolve the name on the phone (a DNS
 * leak before any IP leak). A loopback origin cannot tell those apart, because NSURLSession never
 * proxies loopback at all ([aLoopbackTargetSkipsTheProxyEvenWhenItWorks]); a LAN address was tried
 * and is no better on a Mac whose local-network privacy holds the test process's LAN connections.
 */
class TorLeakTest {
    private val host = "leak.tor-test.invalid"
    private val target = "http://$host/leak-test"
    private val wsTarget = "ws://$host/leak-test"

    /** How a request ended: the body, or the NSURLSession error code (null for a timeout). */
    private sealed interface Outcome {
        data class Body(
            val text: String,
        ) : Outcome

        data class Failed(
            val code: Long?,
            val message: String?,
        ) : Outcome
    }

    private fun Throwable.urlErrorCode(): Long? =
        (this as? DarwinHttpRequestException)?.origin?.code ?: message?.let {
            CODE
                .find(it)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        }

    private suspend fun tryGet(
        client: HttpClient,
        url: String = target,
    ): Outcome =
        try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { Outcome.Body(client.get(url).bodyAsText()) } ?: Outcome.Failed(null, "timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failed(e.urlErrorCode(), e.message?.take(200))
        }

    private suspend fun tryWebSocket(client: HttpClient): Outcome =
        try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                client.webSocket(wsTarget) { }
                Outcome.Body("open")
            } ?: Outcome.Failed(null, "timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failed(e.urlErrorCode(), e.message?.take(200))
        }

    /** A failure that did not come from resolving the name on the phone. */
    private fun assertFailedClosed(
        outcome: Outcome,
        what: String,
    ) {
        assertTrue(outcome is Outcome.Failed, "$what must fail: $outcome")
        assertNotEquals(CANNOT_FIND_HOST, outcome.code, "$what resolved the name itself, as a direct dial does: $outcome")
    }

    @Test
    fun noWayAProxyFailsSendsTheRequestAroundIt() =
        withScope { scope ->
            val failures =
                listOf(
                    SocksBehavior.HangUp,
                    SocksBehavior.RefuseMethods,
                    SocksBehavior.FailConnect(reply = 1),
                    SocksBehavior.FailConnect(reply = 4),
                    SocksBehavior.FailConnect(reply = 5),
                    SocksBehavior.FailConnect(reply = 6),
                    SocksBehavior.Silent,
                )
            val seen = mutableListOf<String>()
            failures.forEach { behavior ->
                val socks = FakeSocks5.start(scope, behavior = behavior)
                val client = darwinHttpClient(socks.port)
                try {
                    val get = tryGet(client)
                    val ws = tryWebSocket(client)
                    assertFailedClosed(get, "$behavior GET")
                    assertFailedClosed(ws, "$behavior WebSocket")
                    assertTrue(socks.acceptedCount() >= 2, "$behavior: both went to the proxy")
                    if (behavior is SocksBehavior.FailConnect) {
                        assertTrue(socks.connects().isNotEmpty() && socks.connects().all { it == SocksConnect(FakeSocks5.DOMAIN_NAME, host, 80) }, "$behavior: by name")
                    }
                    seen += "$behavior: GET ${(get as Outcome.Failed).code} WS ${(ws as Outcome.Failed).code}"
                } finally {
                    client.close()
                    socks.close()
                }
            }
            // Pinned, so a change in how NSURLSession reports these shows up here.
            println("B12f leak: ${seen.joinToString("; ")}")
        }

    @Test
    fun aListenerThatDiesUnderAClientFailsClosedUntilItIsBoundAgain() =
        withScope { scope ->
            val first = FakeSocks5.start(scope)
            val port = first.port
            val client = darwinHttpClient(port)
            try {
                assertEquals(Outcome.Body("ok"), tryGet(client))
                assertEquals(1, first.connects().size)

                // The listener goes, as iOS reclaims it while the app is suspended.
                first.close()
                assertFalse(LoopbackProbe.isSocks5Listening(port), "the probe sees the dead listener")
                val get = tryGet(client)
                assertFailedClosed(get, "GET on the dead listener")
                assertEquals(CANNOT_CONNECT, (get as Outcome.Failed).code, "refused by the dead port: $get")
                assertFailedClosed(tryWebSocket(client), "WebSocket on the dead listener")

                // Bound again on the same port, as TorService.recoverListener prefers: the old client works.
                val again = FakeSocks5.start(scope, port = port)
                try {
                    assertTrue(LoopbackProbe.isSocks5Listening(port))
                    assertEquals(Outcome.Body("ok"), tryGet(client))
                    assertEquals(listOf(SocksConnect(FakeSocks5.DOMAIN_NAME, host, 80)), again.connects())
                } finally {
                    again.close()
                }
            } finally {
                client.close()
            }
        }

    /**
     * Pins a CFNetwork rule the routing has to know: a loopback host is never proxied, so a relay on
     * the phone itself (`ws://127.0.0.1:…`) is dialed directly even when the Tor settings route it
     * through Tor. That never leaves the device, so it leaks nothing; on Android the same dial goes
     * to Arti, whose exit cannot reach the phone's loopback, and fails.
     */
    @Test
    fun aLoopbackTargetSkipsTheProxyEvenWhenItWorks() =
        withScope { scope ->
            val origin = FakeHlsServer.start(scope) { FakeHlsServer.Answer("origin", "text/plain") }
            val socks = FakeSocks5.start(scope, upstreamPort = origin.port)
            val client = darwinHttpClient(socks.port)
            try {
                assertEquals(Outcome.Body("origin"), tryGet(client, "http://127.0.0.1:${origin.port}/loopback"))
                assertEquals(emptyList(), socks.connects(), "NSURLSession went around the proxy")
                assertEquals(1, origin.hits().size)
            } finally {
                client.close()
                socks.close()
                origin.close()
            }
        }

    @Test
    fun theListenerProbeTellsASocksListenerFromAnythingElse() =
        withScope { scope ->
            val working = FakeSocks5.start(scope)
            val hangUp = FakeSocks5.start(scope, behavior = SocksBehavior.HangUp)
            val refusing = FakeSocks5.start(scope, behavior = SocksBehavior.RefuseMethods)
            val silent = FakeSocks5.start(scope, behavior = SocksBehavior.Silent)
            val http = FakeHlsServer.start(scope) { FakeHlsServer.Answer("origin", "text/plain") }
            val closed = FakeSocks5.start(scope).also { it.close() }
            try {
                assertTrue(LoopbackProbe.isSocks5Listening(working.port))
                delay(SETTLE_MS)
                assertEquals(emptyList(), working.connects(), "a probe sends no CONNECT")
                assertFalse(LoopbackProbe.isSocks5Listening(hangUp.port))
                assertFalse(LoopbackProbe.isSocks5Listening(refusing.port))
                assertFalse(LoopbackProbe.isSocks5Listening(silent.port, timeoutMs = 500))
                assertFalse(LoopbackProbe.isSocks5Listening(closed.port))
                assertFalse(LoopbackProbe.isSocks5Listening(http.port, timeoutMs = 500), "an HTTP server is not a SOCKS proxy")
                assertTrue(LoopbackProbe.isTcpListening(http.port))
                assertFalse(LoopbackProbe.isTcpListening(closed.port))
            } finally {
                working.close()
                hangUp.close()
                refusing.close()
                silent.close()
                http.close()
            }
        }

    private fun withScope(block: suspend (CoroutineScope) -> Unit) =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                withTimeout(TEST_TIMEOUT_MS) { block(scope) }
            } finally {
                scope.cancel()
            }
        }

    companion object {
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val TEST_TIMEOUT_MS = 180_000L
        const val SETTLE_MS = 300L

        /** NSURLErrorCannotFindHost: the name was resolved on the phone. */
        const val CANNOT_FIND_HOST = -1003L

        /** NSURLErrorCannotConnectToHost: here, the proxy port refused. */
        const val CANNOT_CONNECT = -1004L

        private val CODE = Regex("""Code=(-?\d+)""")
    }
}
