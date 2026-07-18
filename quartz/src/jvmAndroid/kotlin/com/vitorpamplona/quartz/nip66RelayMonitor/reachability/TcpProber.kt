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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors

/**
 * Raw-socket reachability pre-probe: answers "will this relay's port even accept a
 * TCP connection?" far cheaper than a full WebSocket attempt. Complements
 * [RelayProber] (the app-level census): a crawler wires [TcpProber.tcpReachable]
 * in as its per-relay pre-filter so dead droppers are culled before the WS path
 * pays its connect timeout.
 */
object TcpProber {
    private const val PROBE_TIMEOUT_MS = 2000

    // The probe does BLOCKING DNS + TCP connect, and dead-domain DNS lookups can hang
    // far past the connect timeout. On the shared Dispatchers.IO those hanging lookups
    // starve the crawl's own IO — measured +462s on the finishing drain at hop-3. Run
    // them on a dedicated, isolated daemon pool instead so the crawl's IO is untouched.
    private val probeDispatcher =
        Executors
            .newFixedThreadPool(128) { r -> Thread(r, "relay-probe").apply { isDaemon = true } }
            .asCoroutineDispatcher()

    /**
     * Cheap reachability pre-probe: a raw TCP connect (one round trip) with a tight
     * timeout. Returns false only when the port won't even accept a socket — a dead
     * dropper, refusal, or unroutable/onion/LAN host — which the crawler drops into
     * deadHosts before the WS path pays its 7s connectTimeout. A busy-but-alive relay
     * accepts the SYN instantly at the kernel level (its slowness is at the app layer),
     * so it passes here and is left for the real WS attempt. Unparseable host → true,
     * so an odd URL is never culled on a parse quirk — let the WS decide.
     */
    suspend fun tcpReachable(relay: NormalizedRelayUrl): Boolean =
        withContext(probeDispatcher) {
            val hostPort = relayHostPort(relay) ?: return@withContext true
            try {
                Socket().use { it.connect(InetSocketAddress(hostPort.first, hostPort.second), PROBE_TIMEOUT_MS) }
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
        }

    private fun relayHostPort(relay: NormalizedRelayUrl): Pair<String, Int>? =
        try {
            val uri = URI(relay.url)
            val host = uri.host ?: return null
            val port =
                if (uri.port > 0) {
                    uri.port
                } else if (relay.url.startsWith("wss://", ignoreCase = true)) {
                    443
                } else {
                    80
                }
            host to port
        } catch (e: Exception) {
            null
        }
}
