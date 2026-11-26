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

import com.vitorpamplona.amethyst.commons.napplet.blobs.resolvedAddresses
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoute
import com.vitorpamplona.amethyst.commons.service.http.LoopbackProbe
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.Log
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * The only way one iOS data store's pages reach the network: the store's proxy points here, on
 * loopback, and this decides for each connection where it goes, by [route] — nowhere, through Tor's
 * SOCKS port, or straight out. The iOS counterpart of Desktop's `DesktopSocksRouter`, on the shared
 * wire format and policy ([NappletSocks5], [NappletSocksPolicy]).
 *
 * What it adds over a store pointed straight at Tor or at no proxy: on the open web every name is
 * resolved here and connected to by the address that was checked, so a public name that resolves to
 * this device or its network (`localtest.me` → 127.0.0.1, DNS rebinding) is refused instead of reached;
 * WebKit's request filter can only refuse names and literals it sees. Over Tor, names go to Tor
 * unresolved, as before. The app's own `napplet.local` names never leave on any route.
 *
 * Every route change closes the connections already open, so none outlives a switch away from the
 * route it was made on.
 */
class IosNappletSocksRouter(
    /** Tests stand a local server in for the web. */
    private val allowLocal: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Mutex()
    private val lock = KmpLock()
    private val open = HashSet<Job>()
    private var listener: Listener? = null
    private var lastPort = 0

    private class Listener(
        val selector: SelectorManager,
        val server: ServerSocket,
        val port: Int,
        val acceptor: Job,
    ) {
        fun close() {
            acceptor.cancel()
            server.close()
            selector.close()
        }
    }

    /** Where every connection goes now. Starts [NappletRoute.Blocked]. */
    @Volatile
    var route: NappletRoute = NappletRoute.Blocked
        set(value) {
            if (field == value) return
            field = value
            val live = lock.withLock { open.toList().also { open.clear() } }
            if (live.isNotEmpty()) Log.d(TAG) { "route -> $value: closing ${live.size} connections" }
            live.forEach { it.cancel() }
        }

    /** The port the router listens on, opening it first if needed; null when it cannot listen. */
    suspend fun port(): Int? =
        startLock.withLock {
            listener?.let { if (it.acceptor.isActive) return@withLock it.port else it.close() }
            listener = null
            listenLocked()
        }

    /** The port now, without opening anything: null before the first [port]. */
    val currentPort: Int? get() = listener?.takeIf { it.acceptor.isActive }?.port

    /**
     * Listens again (on the same port when it can) if iOS reclaimed the listening socket while the app
     * was suspended. Returns the port, which the store's proxy must follow if it moved.
     */
    suspend fun recheck(): Int? =
        startLock.withLock {
            val current = listener ?: return@withLock null
            if (current.acceptor.isActive && LoopbackProbe.isTcpListening(current.port)) return@withLock current.port
            Log.w(TAG) { "The napplet SOCKS router on ${current.port} stopped accepting; listening again" }
            current.close()
            listener = null
            listenLocked()
        }

    fun close() {
        route = NappletRoute.Blocked
        listener?.close()
        listener = null
        scope.cancel()
    }

    private suspend fun listenLocked(): Int? =
        try {
            val selector = SelectorManager(Dispatchers.IO)
            val server =
                try {
                    bind(selector, lastPort)
                } catch (e: Exception) {
                    selector.close()
                    throw e
                }
            val port = (server.localAddress as InetSocketAddress).port
            val acceptor =
                scope.launch {
                    try {
                        while (isActive) {
                            val socket = server.accept()
                            track(scope.launch { serve(socket, selector) })
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "The napplet SOCKS router stopped listening", e)
                    }
                }
            listener = Listener(selector, server, port, acceptor)
            lastPort = port
            Log.d(TAG) { "napplet SOCKS router on $LOOPBACK:$port" }
            port
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the napplet SOCKS router", e)
            null
        }

    private suspend fun bind(
        selector: SelectorManager,
        preferredPort: Int,
    ): ServerSocket {
        if (preferredPort > 0) {
            try {
                return aSocket(selector).tcp().bind(LOOPBACK, preferredPort) { reuseAddress = true }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG) { "port $preferredPort is taken (${e.message}); taking another" }
            }
        }
        return aSocket(selector).tcp().bind(LOOPBACK, 0)
    }

    private fun track(job: Job) {
        lock.withLock { open += job }
        job.invokeOnCompletion { lock.withLock { open -= job } }
    }

    private suspend fun serve(
        client: Socket,
        selector: SelectorManager,
    ) {
        var upstream: Socket? = null
        try {
            val clientIn = client.openReadChannel()
            val clientOut = client.openWriteChannel(autoFlush = true)
            val clientIo = ChannelIo(clientIn, clientOut)
            val target = withTimeout(HANDSHAKE_TIMEOUT_MS) { NappletSocks5.accept(clientIo) } ?: return
            val route = route
            val host = NappletSocksPolicy.normalize(target.host)
            if (route == NappletRoute.Blocked || NappletSocksPolicy.refusesName(host, allowLocal)) {
                Log.d(TAG) { "refused $host:${target.port} on $route" }
                clientIo.write(NappletSocks5.reply(NappletSocks5.REPLY_NOT_ALLOWED))
                return
            }
            val connected =
                try {
                    withTimeout(CONNECT_TIMEOUT_MS) { connect(route, NappletSocksTarget(host, target.port), selector) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG) { "connect to $host:${target.port} failed: ${e.message}" }
                    null
                }
            if (connected == null) {
                clientIo.write(NappletSocks5.reply(NappletSocks5.REPLY_HOST_UNREACHABLE))
                return
            }
            if (connected is Refusal) {
                clientIo.write(NappletSocks5.reply(connected.code))
                return
            }
            connected as Upstream
            upstream = connected.socket
            // The route may have changed while connecting; then this connection must not stand.
            if (route != this.route) {
                clientIo.write(NappletSocks5.reply(NappletSocks5.REPLY_NOT_ALLOWED))
                return
            }
            clientIo.write(NappletSocks5.reply(NappletSocks5.REPLY_OK))
            coroutineScope {
                val request = launch { pump(clientIn, connected.output) }
                pump(connected.input, clientOut)
                // The server is done: the client has a while to finish its side, then the tunnel goes,
                // so a peer that never finishes can't hold a connection open.
                withTimeoutOrNull(HALF_CLOSE_TIMEOUT_MS) { request.join() }
                request.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The client went away mid-handshake, or a side of the tunnel failed: both close below.
        } finally {
            upstream?.close()
            client.close()
        }
    }

    private sealed interface Connected

    private class Upstream(
        val socket: Socket,
        val input: ByteReadChannel,
        val output: ByteWriteChannel,
    ) : Connected

    private class Refusal(
        val code: Int,
    ) : Connected

    private suspend fun connect(
        route: NappletRoute,
        target: NappletSocksTarget,
        selector: SelectorManager,
    ): Connected =
        when (route) {
            is NappletRoute.Tor -> {
                val socket = aSocket(selector).tcp().connect(LOOPBACK, route.port)
                val input = socket.openReadChannel()
                val output = socket.openWriteChannel(autoFlush = true)
                val reply =
                    try {
                        NappletSocks5.connect(ChannelIo(input, output), target)
                    } catch (e: Exception) {
                        socket.close()
                        throw e
                    }
                if (reply == NappletSocks5.REPLY_OK) {
                    Upstream(socket, input, output)
                } else {
                    socket.close()
                    Refusal(reply)
                }
            }
            NappletRoute.Direct -> connectDirect(target, selector)
            NappletRoute.Blocked -> Refusal(NappletSocks5.REPLY_NOT_ALLOWED)
        }

    /**
     * Resolved and checked before connecting, and connected by the checked address, so a page can't
     * reach this device or its network through a name (DNS rebinding) or an address form the literal
     * check misses, nor learn which local ports are open from how the handshake fails. The acceptable
     * addresses are tried in turn, as a browser would.
     */
    private suspend fun connectDirect(
        target: NappletSocksTarget,
        selector: SelectorManager,
    ): Connected {
        val resolved = withContext(Dispatchers.IO) { NappletIpLiterals.parse(target.host)?.let(::listOf) ?: resolvedAddresses(target.host) }
        if (resolved == null) return Refusal(NappletSocks5.REPLY_HOST_UNREACHABLE)
        val addresses = NappletSocksPolicy.connectable(resolved, allowLocal)
        if (addresses.isEmpty()) {
            Log.d(TAG) { "refused ${target.host}: it resolves only to local addresses" }
            return Refusal(NappletSocks5.REPLY_NOT_ALLOWED)
        }
        var failure: Exception? = null
        for (address in addresses) {
            try {
                val socket = aSocket(selector).tcp().connect(NappletIpLiterals.format(address), target.port)
                return Upstream(socket, socket.openReadChannel(), socket.openWriteChannel(autoFlush = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException("No address for ${target.host}")
    }

    /** Copies [from] to [to] until [from] ends, then passes the end on (closes [to]). */
    private suspend fun pump(
        from: ByteReadChannel,
        to: ByteWriteChannel,
    ) {
        val buffer = ByteArray(BUFFER)
        try {
            while (true) {
                val read = from.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (read > 0) to.writeFully(buffer, 0, read)
            }
        } finally {
            runCatching { to.flushAndClose() }
        }
    }

    private class ChannelIo(
        private val input: ByteReadChannel,
        private val output: ByteWriteChannel,
    ) : NappletSocksIo {
        override suspend fun readFully(count: Int): ByteArray = ByteArray(count).also { if (count > 0) input.readFully(it) }

        override suspend fun write(bytes: ByteArray) {
            output.writeFully(bytes)
            output.flush()
        }
    }

    companion object {
        private const val TAG = "NappletSocksRouter"
        const val LOOPBACK = "127.0.0.1"
        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val HALF_CLOSE_TIMEOUT_MS = 60_000L
        private const val BUFFER = 16 * 1024
    }
}
