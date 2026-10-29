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

import com.vitorpamplona.quartz.utils.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * The only way Chromium reaches the network. Chromium starts with its proxy pointed here, loopback
 * included, and this decides for each connection where it goes: nowhere, through Tor, or straight
 * out. Chromium can't change its proxy while it runs (JCEF can't hand it the proxy dictionary), so
 * the route is switched here instead, from whatever the open nSites asked for, the way Android's
 * process-wide WebView proxy is.
 *
 * A minimal SOCKS5 server: no authentication, CONNECT only, bound to loopback. Host names go to Tor
 * unresolved, so a Tor route makes no DNS query of its own. Loopback destinations are refused on
 * every route, so a page can't reach services on this machine (this router, Tor's ports) through
 * it; [allowLoopback] exists only for tests that stand in a local server for the web.
 */
class DesktopSocksRouter(
    private val allowLoopback: Boolean = false,
) : Closeable {
    sealed interface Route {
        /** Nothing leaves: every connection is refused. */
        data object Blocked : Route

        /** Through the Tor SOCKS port on loopback. */
        data class Tor(
            val port: Int,
        ) : Route

        /** Straight to the destination. */
        data object Direct : Route
    }

    private val server = ServerSocket(0, 64, InetAddress.getLoopbackAddress())
    private val open = ConcurrentHashMap.newKeySet<Socket>()

    /** Where Chromium points its proxy: `socks5://127.0.0.1:<port>`. */
    val port: Int get() = server.localPort

    @Volatile
    var route: Route = Route.Blocked
        set(value) {
            if (field == value) return
            field = value
            // A new route applies to the connections already open too: none of them may outlive a
            // switch away from the route they were made on.
            open.toList().forEach { closeQuietly(it) }
        }

    init {
        thread("napplet-socks-accept") {
            while (!server.isClosed) {
                val client =
                    try {
                        server.accept()
                    } catch (e: IOException) {
                        if (!server.isClosed) Log.w(TAG, "SOCKS accept failed", e)
                        break
                    }
                thread("napplet-socks-conn") { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) {
        track(client)
        var upstream: Socket? = null
        // Once both sockets are handed to the pumps, they close them; until then, every way out of
        // here closes them, a refusal included (RFC 1928: the server closes after a failure reply).
        var pumping = false
        try {
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()
            if (!greet(input, output)) return
            val target = readConnect(input, output) ?: return
            val route = route
            if (route == Route.Blocked || (!allowLoopback && isLoopback(target.hostString))) {
                reply(output, REPLY_NOT_ALLOWED)
                return
            }
            val connected =
                try {
                    connect(route, target)
                } catch (e: LoopbackRefused) {
                    reply(output, REPLY_NOT_ALLOWED)
                    return
                } catch (e: IOException) {
                    Log.d(TAG) { "SOCKS connect to ${target.hostString}:${target.port} failed: ${e.message}" }
                    reply(output, REPLY_HOST_UNREACHABLE)
                    return
                }
            upstream = connected
            // The route may have changed while connecting; then this connection must not stand.
            if (route != this.route) {
                reply(output, REPLY_NOT_ALLOWED)
                return
            }
            track(connected)
            reply(output, REPLY_OK)
            client.soTimeout = 0
            pumping = true
            thread("napplet-socks-up") { pump(client.getInputStream(), connected.getOutputStream(), client, connected) }
            pump(connected.getInputStream(), output, client, connected)
        } catch (e: IOException) {
            // The client went away mid-handshake, or a pump failed to start; closed below or by the pump.
        } finally {
            if (!pumping) {
                closeQuietly(client)
                upstream?.let(::closeQuietly)
            }
        }
    }

    /** Accepts only "no authentication". */
    private fun greet(
        input: DataInputStream,
        output: OutputStream,
    ): Boolean {
        if (input.readUnsignedByte() != VERSION) {
            closeQuietly(output)
            return false
        }
        val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        if (methods.none { it.toInt() == NO_AUTH }) {
            output.write(byteArrayOf(VERSION.toByte(), NO_ACCEPTABLE.toByte()))
            output.flush()
            return false
        }
        output.write(byteArrayOf(VERSION.toByte(), NO_AUTH.toByte()))
        output.flush()
        return true
    }

    /** Reads a CONNECT request; anything else is refused. */
    private fun readConnect(
        input: DataInputStream,
        output: OutputStream,
    ): InetSocketAddress? {
        if (input.readUnsignedByte() != VERSION) return null
        val command = input.readUnsignedByte()
        input.readUnsignedByte() // reserved
        val host =
            when (input.readUnsignedByte()) {
                ATYP_IPV4 -> InetAddress.getByAddress(ByteArray(4).also { input.readFully(it) }).hostAddress
                ATYP_IPV6 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
                ATYP_DOMAIN -> String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) }, Charsets.US_ASCII)
                else -> {
                    reply(output, REPLY_ADDRESS_UNSUPPORTED)
                    return null
                }
            }
        val port = input.readUnsignedShort()
        if (command != CMD_CONNECT) {
            reply(output, REPLY_COMMAND_UNSUPPORTED)
            return null
        }
        return InetSocketAddress.createUnresolved(host, port)
    }

    private fun connect(
        route: Route,
        target: InetSocketAddress,
    ): Socket =
        when (route) {
            is Route.Tor ->
                Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress(InetAddress.getLoopbackAddress(), route.port))).apply {
                    connect(target, CONNECT_TIMEOUT_MS)
                }
            Route.Direct -> {
                // Resolved and checked before connecting, so a page can't reach this machine through a
                // name (DNS rebinding) or an address form the literal check misses (::ffff:127.0.0.1),
                // nor learn which local ports are open from how the handshake fails.
                val address =
                    InetAddress.getAllByName(target.hostString).firstOrNull { allowLoopback || !isThisMachine(it) }
                        ?: throw LoopbackRefused()
                Socket().apply { connect(InetSocketAddress(address, target.port), CONNECT_TIMEOUT_MS) }
            }
            Route.Blocked -> throw IOException("Blocked")
        }

    private fun pump(
        from: InputStream,
        to: OutputStream,
        a: Socket,
        b: Socket,
    ) {
        try {
            from.copyTo(to)
        } catch (e: IOException) {
            // Either side closing ends both.
        } finally {
            closeQuietly(a)
            closeQuietly(b)
        }
    }

    private fun reply(
        output: OutputStream,
        code: Int,
    ) {
        output.write(byteArrayOf(VERSION.toByte(), code.toByte(), 0, ATYP_IPV4.toByte(), 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun track(socket: Socket) {
        open += socket
        if (socket.isClosed) open -= socket
    }

    private fun closeQuietly(socket: Socket) {
        open -= socket
        try {
            socket.close()
        } catch (e: IOException) {
            // Already gone.
        }
    }

    private fun closeQuietly(stream: OutputStream) {
        try {
            stream.close()
        } catch (e: IOException) {
            // Already gone.
        }
    }

    override fun close() {
        route = Route.Blocked
        try {
            server.close()
        } catch (e: IOException) {
            Log.w(TAG, "SOCKS router did not close", e)
        }
    }

    // Every connection blocks two threads on its sockets, and a page opens dozens: virtual threads
    // park on that I/O instead of holding a platform thread each. They are always daemons.
    private fun thread(
        name: String,
        block: () -> Unit,
    ) = Thread.ofVirtual().name(name).start(block)

    /** A destination that resolves only to this machine. */
    private class LoopbackRefused : IOException("Refused: a local address")

    companion object {
        private const val TAG = "DesktopSocksRouter"

        private const val VERSION = 5
        private const val NO_AUTH = 0
        private const val NO_ACCEPTABLE = 0xFF
        private const val CMD_CONNECT = 1
        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4
        private const val REPLY_OK = 0
        private const val REPLY_NOT_ALLOWED = 2
        private const val REPLY_HOST_UNREACHABLE = 4
        private const val REPLY_COMMAND_UNSUPPORTED = 7
        private const val REPLY_ADDRESS_UNSUPPORTED = 8

        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val CONNECT_TIMEOUT_MS = 30_000

        private fun isThisMachine(address: InetAddress): Boolean = address.isLoopbackAddress || address.isAnyLocalAddress

        private fun isLoopback(host: String): Boolean {
            val name = host.lowercase().trimEnd('.')
            if (name == "localhost" || name.endsWith(".localhost")) return true
            // Literal addresses only: a name is never resolved here for a Tor route, and on the open
            // web it is resolved and checked before connecting.
            return name.startsWith("127.") || name == "::1" || name == "0:0:0:0:0:0:0:1" || name == "0.0.0.0"
        }
    }
}
