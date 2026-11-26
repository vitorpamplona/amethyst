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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The two routes a Ktor request can take, for the targets without OkHttp: [direct], and through
 * the Tor SOCKS port [proxyPort] names. The off-JVM counterpart of `DualHttpClientManager`.
 *
 * [build] makes a client: with a SOCKS port for the Tor route, with null for the direct one. A
 * client's proxy is fixed when it is built (on iOS it is the NSURLSession's configuration), so
 * the Tor client is rebuilt whenever the port moves, and the one it replaces is closed.
 *
 * **Fail closed.** A request that must go through Tor while Tor has no port gets [unavailable], a
 * client whose every request fails with [TorUnavailableException] before anything reaches the
 * network. It never gets [direct]: Android falls back to a direct client in that window only by
 * accident (see `TorServiceStatus.socksPort`). The one exception is the user's own "use a regular
 * connection" ([sessionBypass]), which is what that choice means.
 *
 * @param proxyPort the Tor SOCKS port, or null while Tor cannot take a connection (off,
 *   connecting, bypassed). On the apps this is `TorManager.activePortOrNull`.
 * @param sessionBypass the user chose to go without Tor for now (`TorManager.sessionBypass`).
 */
class DualKtorClients(
    private val proxyPort: StateFlow<Int?>,
    private val sessionBypass: StateFlow<Boolean>,
    scope: CoroutineScope,
    private val build: (socksPort: Int?) -> HttpClient,
) {
    /** Every request that is not routed through Tor. Built on first use. */
    val direct: HttpClient by lazy { build(null) }

    /** What a Tor-routed request gets while Tor has no port: every request fails, offline. */
    val unavailable: HttpClient by lazy { torUnavailableClient() }

    // The current Tor client and the port it was built for. Guarded by [lock].
    private val lock = KmpLock()
    private var torClient: HttpClient? = null
    private var torClientPort: Int? = null

    /** The client a request takes: [tor] when it must go through Tor, else [direct]. */
    fun get(useTor: Boolean): HttpClient = if (useTor) tor() else direct

    /** The Tor route as it stands: the proxied client, or [unavailable] (or [direct] when bypassed). */
    fun tor(): HttpClient = torFor(proxyPort.value, sessionBypass.value)

    /** The SOCKS port Tor-routed requests go to right now, or null when there is none. */
    fun currentProxyPort(): Int? = proxyPort.value

    private fun torFor(
        port: Int?,
        bypass: Boolean,
    ): HttpClient {
        if (port == null) return if (bypass) direct else unavailable
        var replaced: HttpClient? = null
        val client =
            lock.withLock {
                val current = torClient
                if (current != null && torClientPort == port) {
                    current
                } else {
                    replaced = current
                    build(port).also {
                        torClient = it
                        torClientPort = port
                    }
                }
            }
        // Lets the requests still running on the old port finish; new ones on it would fail.
        replaced?.close()
        return client
    }

    /**
     * The client relays take for the Tor route, as a token that changes exactly when that route
     * does (`RelayProxyClientConnector` compares it by identity and re-dials on a change). While
     * collected it also keeps `TorManager.status` running, which is what starts Tor.
     */
    val torRoute: StateFlow<Any> =
        combine(proxyPort, sessionBypass) { port, bypass -> torFor(port, bypass) }
            .stateIn(scope, SharingStarted.WhileSubscribed(1000), torFor(proxyPort.value, sessionBypass.value))

    /** The direct route's token. It never changes: the direct client is built once. */
    val directRoute: StateFlow<Any> by lazy { MutableStateFlow<Any>(direct) }
}
