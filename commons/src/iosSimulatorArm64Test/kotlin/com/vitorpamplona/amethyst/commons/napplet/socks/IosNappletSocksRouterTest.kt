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
package com.vitorpamplona.amethyst.commons.napplet.socks

import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoute
import com.vitorpamplona.amethyst.commons.service.http.FakeSocks5
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iOS napplet SOCKS router on real loopback sockets: what each route lets through, and what no
 * route ever does (the app's names, this device and its network, whatever a name resolves to).
 */
class IosNappletSocksRouterTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selector = SelectorManager(Dispatchers.IO)

    /**
     * The test's coroutines go before the selector they wait on. Closed first, the selector fails
     * every pending accept and write with an IOException on its own thread; one that lands before
     * the cancel fails its coroutine, which on Kotlin/Native aborts the whole test process, during
     * whichever test runs next (CI saw it as IosConnectivityTest dying).
     */
    @AfterTest
    fun tearDown() {
        scope.cancel()
        selector.close()
    }

    /** A loopback server that answers every connection with "hello" and counts them. */
    private suspend fun helloServer(): Pair<Int, MutableStateFlow<Int>> {
        val server = aSocket(selector).tcp().bind("127.0.0.1", 0)
        val accepted = MutableStateFlow(0)
        scope.launch {
            try {
                while (true) {
                    val socket = server.accept()
                    accepted.update { it + 1 }
                    launch {
                        try {
                            val out = socket.openWriteChannel(autoFlush = true)
                            out.writeFully("hello".encodeToByteArray())
                            out.flushAndClose()
                        } catch (e: IOException) {
                            // The client hung up first.
                        } finally {
                            socket.close()
                        }
                    }
                }
            } catch (e: IOException) {
                // The selector closed under the server: it is done.
            }
        }
        return (server.localAddress as InetSocketAddress).port to accepted
    }

    private class Client(
        val input: ByteReadChannel,
        val output: ByteWriteChannel,
    ) : NappletSocksIo {
        override suspend fun readFully(count: Int): ByteArray = ByteArray(count).also { input.readFully(it) }

        override suspend fun write(bytes: ByteArray) = output.writeFully(bytes)
    }

    /** A SOCKS5 CONNECT to [host]:[port] through the router on [routerPort]: the reply code, and the first bytes after it. */
    private suspend fun connectThrough(
        routerPort: Int,
        host: String,
        port: Int,
    ): Pair<Int, String> =
        withTimeout(20_000) {
            val socket = aSocket(selector).tcp().connect("127.0.0.1", routerPort)
            try {
                val client = Client(socket.openReadChannel(), socket.openWriteChannel(autoFlush = true))
                val code = NappletSocks5.connect(client, NappletSocksTarget(host, port))
                val buffer = ByteArray(16)
                val read = if (code == NappletSocks5.REPLY_OK) client.input.readAvailable(buffer, 0, buffer.size) else -1
                code to (if (read > 0) buffer.decodeToString(0, read) else "")
            } finally {
                socket.close()
            }
        }

    @Test
    fun directReachesTheWebButNeverThisDevice() =
        runBlocking {
            val (port, accepted) = helloServer()
            val router = IosNappletSocksRouter()
            try {
                val routerPort = router.port()!!
                router.route = NappletRoute.Direct
                // A literal of this device, a name that only means it, and the app's own names.
                for (host in listOf("127.0.0.1", "localhost", "[::1]", "nabc.napplet.local", "printer.local")) {
                    assertEquals(NappletSocks5.REPLY_NOT_ALLOWED, connectThrough(routerPort, host, port).first, host)
                }
                // A public name that resolves to loopback (public DNS: localtest.me is 127.0.0.1).
                val rebind = connectThrough(routerPort, "localtest.me", port).first
                assertTrue(rebind == NappletSocks5.REPLY_NOT_ALLOWED || rebind == NappletSocks5.REPLY_HOST_UNREACHABLE, "localtest.me: $rebind")
                assertEquals(0, accepted.value, "this device was reached through the router")
            } finally {
                router.close()
            }
        }

    @Test
    fun directConnectsAndTunnels() =
        runBlocking {
            val (port, accepted) = helloServer()
            // A local server stands in for the web here.
            val router = IosNappletSocksRouter(allowLocal = true)
            try {
                val routerPort = router.port()!!
                router.route = NappletRoute.Direct
                assertEquals(NappletSocks5.REPLY_OK to "hello", connectThrough(routerPort, "127.0.0.1", port))
                assertEquals(1, accepted.value)
                // Even then, the app's names never leave.
                assertEquals(NappletSocks5.REPLY_NOT_ALLOWED, connectThrough(routerPort, "napplet.local", port).first)
            } finally {
                router.close()
            }
        }

    @Test
    fun torGetsTheNameUnresolved() =
        runBlocking {
            val (port, accepted) = helloServer()
            val tor = FakeSocks5.start(scope, upstreamPort = port)
            val router = IosNappletSocksRouter()
            try {
                val routerPort = router.port()!!
                router.route = NappletRoute.Tor(tor.port)
                assertEquals(NappletSocks5.REPLY_OK to "hello", connectThrough(routerPort, "abcdefghij.onion", 80))
                val asked = tor.awaitConnect("abcdefghij.onion")
                assertEquals(FakeSocks5.DOMAIN_NAME, asked.addressType)
                assertEquals(1, accepted.value)
                // Local destinations are refused before Tor sees them.
                assertEquals(NappletSocks5.REPLY_NOT_ALLOWED, connectThrough(routerPort, "192.168.1.1", 80).first)
                assertTrue(tor.connects().none { it.host == "192.168.1.1" })
            } finally {
                router.close()
                tor.close()
            }
        }

    @Test
    fun blockedRefusesEverythingAndARouteChangeCutsOpenTunnels() =
        runBlocking {
            val tor = FakeSocks5.start(scope)
            val router = IosNappletSocksRouter()
            try {
                val routerPort = router.port()!!
                assertEquals(NappletRoute.Blocked, router.route)
                assertEquals(NappletSocks5.REPLY_NOT_ALLOWED, connectThrough(routerPort, "example.org", 443).first)
                assertTrue(tor.connects().isEmpty())

                // A tunnel open on Tor ends when the route moves to the open web.
                router.route = NappletRoute.Tor(tor.port)
                val socket = aSocket(selector).tcp().connect("127.0.0.1", routerPort)
                val client = Client(socket.openReadChannel(), socket.openWriteChannel(autoFlush = true))
                assertEquals(NappletSocks5.REPLY_OK, NappletSocks5.connect(client, NappletSocksTarget("example.org", 80)))
                router.route = NappletRoute.Direct
                val ended = withTimeout(10_000) { client.input.readAvailable(ByteArray(1), 0, 1) }
                assertEquals(-1, ended, "the tunnel outlived the route it was made on")
                socket.close()
            } finally {
                router.close()
                tor.close()
            }
        }
}
