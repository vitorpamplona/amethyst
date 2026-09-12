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

import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyRouteTrackerTest {
    private fun socks(port: Int) = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))

    @Test
    fun firstProxiedBuildDoesNotEvict() {
        // Nothing is pooled on the old route because there is no old route.
        assertFalse(ProxyRouteTracker().shouldEvictFor(socks(9050)))
    }

    @Test
    fun directBuildNeverEvicts() {
        val tracker = ProxyRouteTracker()

        assertFalse(tracker.shouldEvictFor(null))
        assertFalse(tracker.shouldEvictFor(null))
    }

    @Test
    fun sameProxyRebuiltDoesNotEvict() {
        val tracker = ProxyRouteTracker()
        tracker.shouldEvictFor(socks(9050))

        // A fresh-but-equal Proxy is what every rebuild hands us: buildLocalSocksProxy
        // allocates a new instance each time, so this must compare by value.
        assertFalse(tracker.shouldEvictFor(socks(9050)))
    }

    @Test
    fun changedProxyPortEvicts() {
        val tracker = ProxyRouteTracker()
        tracker.shouldEvictFor(socks(9050))

        assertTrue(tracker.shouldEvictFor(socks(9150)))
    }

    /**
     * The regression this class exists for. [DualHttpClientManager] mints both variants from one
     * factory — `defaultHttpClient` always proxied, `defaultHttpClientWithoutProxy` always direct
     * — and both `stateIn` flows re-emit on every `isMobileDataProvider` change and every
     * resubscribe. A single "last proxy" field saw that alternation as a route change every time
     * and wiped the connection pool the two variants SHARE.
     */
    @Test
    fun alternatingBetweenProxiedAndDirectNeverEvicts() {
        val tracker = ProxyRouteTracker()

        repeat(10) {
            assertFalse(tracker.shouldEvictFor(socks(9050)))
            assertFalse(tracker.shouldEvictFor(null))
        }
    }

    @Test
    fun directBuildsBetweenProxyChangesDoNotMaskTheChange() {
        val tracker = ProxyRouteTracker()
        tracker.shouldEvictFor(socks(9050))
        tracker.shouldEvictFor(null)

        // The direct build in the middle must not reset what the proxied variant last used.
        assertTrue(tracker.shouldEvictFor(socks(9150)))
    }
}
