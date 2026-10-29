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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks [DesktopSocksRouter]'s one route from what the open nSites asked for, as Android's
 * `WebViewProxyPolicy` arbitrates its process-wide WebView proxy:
 *
 * - any site on Tor → everything goes through Tor (a site that chose the open web is then
 *   [routedOverTor] too, and its window says so);
 * - otherwise any site on the open web → direct;
 * - no site open → nothing leaves.
 *
 * With Tor on but its port unknown the route is blocked, never direct: a site that asked for Tor
 * waits for it ([claim]) and fails closed if it doesn't come.
 */
class DesktopNappletRoutes(
    private val router: DesktopSocksRouter,
    private val torPort: StateFlow<Int?>,
    scope: CoroutineScope,
) {
    private val claims = HashMap<Any, Boolean>()

    private val mutableRoutedOverTor = MutableStateFlow(false)

    /** Whether Chromium's traffic goes through Tor right now, whatever a given site asked for. */
    val routedOverTor: StateFlow<Boolean> = mutableRoutedOverTor.asStateFlow()

    /** Whether Tor's SOCKS port is known right now. */
    val torRunning: Boolean get() = torPort.value != null

    init {
        // Tor can restart on another port, or stop: the route follows.
        scope.launch { torPort.collect { apply() } }
    }

    /**
     * Records that [owner] wants [useTor], applies the route, and returns whether that site's
     * traffic can go out the way it asked. A Tor site waits up to [timeoutMs] for Tor's port.
     */
    suspend fun claim(
        owner: Any,
        useTor: Boolean,
        timeoutMs: Long,
    ): Boolean {
        synchronized(claims) { claims[owner] = useTor }
        apply()
        if (!useTor) return true
        val port = withTimeoutOrNull(timeoutMs) { torPort.first { it != null } }
        apply()
        return port != null
    }

    /**
     * Waits up to [timeoutMs] for Tor's port without claiming a route: a napplet's blobs go through
     * Tor when Tor is on, but its page has no network of its own.
     */
    suspend fun awaitTor(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) { torPort.first { it != null } } != null

    fun release(owner: Any) {
        synchronized(claims) { claims.remove(owner) }
        apply()
    }

    private fun apply() {
        val route =
            synchronized(claims) {
                val port = torPort.value
                when {
                    claims.values.any { it } -> if (port != null) DesktopSocksRouter.Route.Tor(port) else DesktopSocksRouter.Route.Blocked
                    claims.isNotEmpty() -> DesktopSocksRouter.Route.Direct
                    else -> DesktopSocksRouter.Route.Blocked
                }
            }
        router.route = route
        mutableRoutedOverTor.value = route is DesktopSocksRouter.Route.Tor
    }
}
