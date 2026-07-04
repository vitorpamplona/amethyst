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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * [SocketFactory] that enables `TCP_NODELAY` — pass to
 * `OkHttpClient.Builder.socketFactory` for every client that holds Nostr
 * relay WebSockets.
 *
 * OkHttp does not disable Nagle's algorithm, and a Nostr client's hottest
 * wire pattern trips over it: CLOSE immediately followed by REQ — every
 * feed or filter switch. Relays never reply to a CLOSE (NIP-01), so its
 * bytes sit unACKed for the peer's delayed-ACK window, and Nagle holds
 * the REQ back behind them until that ACK arrives. Measured on loopback
 * as a flat ~44 ms added to every such REQ's round trip (geode's
 * `WireReqFloorBenchmark`, which is how this was found); WAN delayed-ACK
 * windows are the same order. relayBench's harness client ships the same
 * fix, which is why benchmark numbers never showed it.
 *
 * OkHttp only uses this factory for direct connections — proxied (e.g.
 * Tor SOCKS) connections take a different path and keep their transport's
 * defaults.
 */
object TcpNoDelaySocketFactory : SocketFactory() {
    private fun socket() = Socket().apply { tcpNoDelay = true }

    override fun createSocket(): Socket = socket()

    override fun createSocket(
        host: String?,
        port: Int,
    ): Socket = socket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket =
        socket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(
        host: InetAddress?,
        port: Int,
    ): Socket = socket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket =
        socket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }
}
