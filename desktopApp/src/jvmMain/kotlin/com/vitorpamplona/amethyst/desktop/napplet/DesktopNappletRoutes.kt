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

import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies the shared [NappletProxyClaims] (Tor always wins, as for Android's process-wide WebView
 * proxy) to [DesktopSocksRouter], Chromium's only way out:
 *
 * - any site on Tor → everything goes through Tor (a site that chose the open web is then
 *   [routedOverTor] too, and its window says so);
 * - otherwise any site on the open web → direct;
 * - no site open, or a Tor site while Tor's port is unknown → nothing leaves.
 *
 * Each site's claim is filed again whenever Tor's port changes, so a Tor restart moves the route
 * and Tor stopping blocks it.
 */
class DesktopNappletRoutes(
    private val router: DesktopSocksRouter,
    private val torPort: StateFlow<Int?>,
    scope: CoroutineScope,
) {
    // Which open site wants Tor; the claims are rebuilt from it with the current port.
    private val wantsTor = LinkedHashMap<Any, Boolean>()
    private val claims = NappletProxyClaims()

    private val mutableRoutedOverTor = MutableStateFlow(false)

    /** Whether Chromium's traffic goes through Tor right now, whatever a given site asked for. */
    val routedOverTor: StateFlow<Boolean> = mutableRoutedOverTor.asStateFlow()

    /** Whether Tor's SOCKS port is known right now. */
    val torRunning: Boolean get() = torPort.value != null

    init {
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
        synchronized(wantsTor) { wantsTor[owner] = useTor }
        apply()
        if (!useTor) return true
        val running = awaitTor(timeoutMs)
        apply()
        return running
    }

    /**
     * Waits up to [timeoutMs] for Tor's port without claiming a route: a napplet's blobs go through
     * Tor when Tor is on, but its page has no network of its own.
     */
    suspend fun awaitTor(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) { torPort.first { it != null } } != null

    fun release(owner: Any) {
        synchronized(wantsTor) {
            wantsTor.remove(owner)
            claims.release(owner)
        }
        apply()
    }

    private fun apply() {
        val route =
            synchronized(wantsTor) {
                val port = torPort.value ?: NappletProxyClaims.TOR_NOT_READY
                wantsTor.forEach { (owner, tor) -> claims.claim(owner, if (tor) port else NappletProxyClaims.NO_PROXY) }
                val shared = claims.route()
                when {
                    claims.isEmpty() || shared.blocked -> DesktopSocksRouter.Route.Blocked
                    shared.usesTor -> DesktopSocksRouter.Route.Tor(shared.torPort)
                    else -> DesktopSocksRouter.Route.Direct
                }
            }
        router.route = route
        mutableRoutedOverTor.value = route is DesktopSocksRouter.Route.Tor
    }
}
