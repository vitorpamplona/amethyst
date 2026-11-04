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
package com.vitorpamplona.amethyst.desktop.napplet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopSocksRouterTest {
    private val closeables = mutableListOf<AutoCloseable>()

    @After
    fun tearDown() {
        closeables.reversed().forEach { runCatching { it.close() } }
    }

    private fun <T : AutoCloseable> T.closing(): T = also { closeables += it }

    /** A server on loopback that answers every line with the same bytes. */
    private fun echoServer(): ServerSocket =
        ServerSocket(0, 8, InetAddress.getLoopbackAddress()).closing().also { server ->
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { client.getInputStream().copyTo(client.getOutputStream()) } }
                }
            }
        }

    /** Opens [host]:[port] through the router at [routerPort]; returns the SOCKS reply code and the socket. */
    private fun connect(
        routerPort: Int,
        host: String,
        port: Int,
    ): Pair<Int, Socket> {
        val socket = Socket(InetAddress.getLoopbackAddress(), routerPort).closing()
        socket.soTimeout = 10_000
        val out = socket.getOutputStream()
        val input = DataInputStream(socket.getInputStream())
        out.write(byteArrayOf(5, 1, 0))
        assertEquals(5, input.readUnsignedByte())
        assertEquals(0, input.readUnsignedByte(), "no authentication")
        val name = host.toByteArray(Charsets.US_ASCII)
        out.write(byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf((port shr 8).toByte(), port.toByte()))
        out.flush()
        assertEquals(5, input.readUnsignedByte())
        val code = input.readUnsignedByte()
        input.readFully(ByteArray(8))
        return code to socket
    }

    private fun Socket.echoes(): Boolean {
        getOutputStream().apply {
            write("ping\n".toByteArray())
            flush()
        }
        val back = ByteArray(5)
        DataInputStream(getInputStream()).readFully(back)
        return String(back) == "ping\n"
    }

    @Test
    fun nothingLeavesWhileBlocked() {
        val router = DesktopSocksRouter(allowLocal = true).closing()
        val (code, _) = connect(router.port, "127.0.0.1", echoServer().localPort)
        assertEquals(2, code, "connection not allowed")
    }

    @Test
    fun aRefusedConnectionIsClosed() {
        val router = DesktopSocksRouter(allowLocal = true).closing()
        val (code, socket) = connect(router.port, "127.0.0.1", echoServer().localPort)
        assertEquals(2, code)
        // The router hung up after its reply, instead of leaving the socket open until a route change.
        assertEquals(-1, socket.getInputStream().read())
        val loopbackGuard = DesktopSocksRouter().closing()
        loopbackGuard.route = DesktopSocksRouter.Route.Direct
        val (refused, other) = connect(loopbackGuard.port, "localhost", 9)
        assertEquals(2, refused)
        assertEquals(-1, other.getInputStream().read())
    }

    @Test
    fun theOpenWebGoesStraightOut() {
        val router = DesktopSocksRouter(allowLocal = true).closing()
        router.route = DesktopSocksRouter.Route.Direct
        val (code, socket) = connect(router.port, "127.0.0.1", echoServer().localPort)
        assertEquals(0, code)
        assertTrue(socket.echoes())
    }

    @Test
    fun aPageCannotReachThisMachine() {
        val router = DesktopSocksRouter().closing()
        router.route = DesktopSocksRouter.Route.Direct
        val echo = echoServer().localPort
        assertEquals(2, connect(router.port, "127.0.0.1", echo).first)
        assertEquals(2, connect(router.port, "localhost", echo).first)
        // Not even the router itself.
        assertEquals(2, connect(router.port, "127.0.0.1", router.port).first)
    }

    @Test
    fun aPageCannotReachTheLocalNetwork() {
        val router = DesktopSocksRouter().closing()
        router.route = DesktopSocksRouter.Route.Direct
        val own =
            NetworkInterface
                .getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .map { it.hostAddress.substringBefore('%') }
                .toList()
        // A home router, a cloud metadata endpoint, carrier-grade NAT, IPv6's private range, an IPv4
        // address written as IPv6, and this machine's own addresses.
        val local = listOf("192.168.1.1", "10.0.0.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "fd00::1", "::ffff:192.168.1.1", "0.0.0.0") + own
        local.forEach { assertEquals(2, connect(router.port, it, 80).first, "$it is refused") }
    }

    @Test
    fun theAppsOwnNamesNeverLeave() {
        // A site's per-account origin would tell the DNS server, or a Tor exit, which sites are open.
        val router = DesktopSocksRouter(allowLocal = true).closing()
        router.route = DesktopSocksRouter.Route.Direct
        assertEquals(2, connect(router.port, "napplet.local", 443).first)
        assertEquals(2, connect(router.port, "n0123456789abcdef.napplet.local", 443).first)
        assertEquals(2, connect(router.port, "N0123.NAPPLET.LOCAL.", 443).first)
    }

    @Test
    fun aClientThatFinishesSendingStillGetsTheAnswer() {
        // A server that answers only once the request has ended, as some protocols do.
        val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress()).closing()
        thread(isDaemon = true) {
            val client = runCatching { server.accept() }.getOrNull() ?: return@thread
            val request = client.getInputStream().readBytes()
            client.getOutputStream().write("got ${request.size}".toByteArray())
            client.close()
        }
        val router = DesktopSocksRouter(allowLocal = true).closing()
        router.route = DesktopSocksRouter.Route.Direct
        val (code, socket) = connect(router.port, "127.0.0.1", server.localPort)
        assertEquals(0, code)
        socket.getOutputStream().write("hello".toByteArray())
        socket.shutdownOutput()
        assertEquals("got 5", String(socket.getInputStream().readBytes()))
    }

    @Test
    fun torCarriesTheConnectionThroughItsProxy() {
        // Another router stands in for Tor's SOCKS port.
        val tor = DesktopSocksRouter(allowLocal = true).closing()
        val router = DesktopSocksRouter(allowLocal = true).closing()
        router.route = DesktopSocksRouter.Route.Tor(tor.port)
        val echo = echoServer().localPort

        tor.route = DesktopSocksRouter.Route.Direct
        val (code, socket) = connect(router.port, "127.0.0.1", echo)
        assertEquals(0, code)
        assertTrue(socket.echoes())

        // When "Tor" refuses, so does the route: nothing falls back to going out directly.
        tor.route = DesktopSocksRouter.Route.Blocked
        assertEquals(4, connect(router.port, "127.0.0.1", echo).first, "host unreachable")
    }

    @Test
    fun switchingTheRouteClosesWhatWasOpen() {
        val router = DesktopSocksRouter(allowLocal = true).closing()
        router.route = DesktopSocksRouter.Route.Direct
        val (_, socket) = connect(router.port, "127.0.0.1", echoServer().localPort)
        assertTrue(socket.echoes())
        router.route = DesktopSocksRouter.Route.Blocked
        assertEquals(-1, socket.getInputStream().read(), "the open connection was cut")
    }

    @Test
    fun theRouteFollowsWhatTheOpenSitesAskedFor() =
        runBlocking {
            val router = DesktopSocksRouter().closing()
            val torPort = MutableStateFlow<Int?>(9050)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val routes = DesktopNappletRoutes(router, torPort, scope)
            val openWebSite = Any()
            val torSite = Any()

            assertIs<DesktopSocksRouter.Route.Blocked>(router.route)
            assertTrue(routes.claim(openWebSite, useTor = false, timeoutMs = 100))
            assertIs<DesktopSocksRouter.Route.Direct>(router.route)

            // Any site on Tor takes everything through Tor, the open-web site included.
            assertTrue(routes.claim(torSite, useTor = true, timeoutMs = 100))
            assertEquals(DesktopSocksRouter.Route.Tor(9050), router.route)
            assertTrue(routes.routedOverTor.value)

            routes.release(torSite)
            assertIs<DesktopSocksRouter.Route.Direct>(router.route)
            assertFalse(routes.routedOverTor.value)
            routes.release(openWebSite)
            assertIs<DesktopSocksRouter.Route.Blocked>(router.route)

            // Tor on but not running: the site fails closed, and nothing goes out directly.
            torPort.value = null
            assertFalse(routes.claim(torSite, useTor = true, timeoutMs = 100))
            assertIs<DesktopSocksRouter.Route.Blocked>(router.route)
            torPort.value = 9150
            withTimeout(5_000) { while (router.route != DesktopSocksRouter.Route.Tor(9150)) delay(20) }
            scope.cancel()
        }
}
