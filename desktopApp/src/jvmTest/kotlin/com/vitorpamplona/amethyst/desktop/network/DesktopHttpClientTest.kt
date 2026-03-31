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
package com.vitorpamplona.amethyst.desktop.network

import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopHttpClientTest {
    // --- Simple (companion) client tests ---

    @Test
    fun simpleClient_returnsConfiguredClient() {
        val url = NormalizedRelayUrl("wss://relay.damus.io/")
        val client = DesktopHttpClient.getSimpleHttpClient(url)

        assertNotNull(client)
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.pingIntervalMillis)
        assertTrue(client.retryOnConnectionFailure)
    }

    @Test
    fun simpleClient_returnsSameInstance() {
        val url1 = NormalizedRelayUrl("wss://relay.damus.io/")
        val url2 = NormalizedRelayUrl("wss://nos.lol/")
        assertEquals(DesktopHttpClient.getSimpleHttpClient(url1), DesktopHttpClient.getSimpleHttpClient(url2))
    }

    // --- Tor-aware client tests ---

    private fun buildTorAwareClient(
        torPort: Int? = null,
        shouldUseTor: (NormalizedRelayUrl) -> Boolean = { false },
    ): DesktopHttpClient {
        val statusFlow =
            MutableStateFlow<TorServiceStatus>(
                if (torPort != null) TorServiceStatus.Active(torPort) else TorServiceStatus.Off,
            )
        val portFlow = MutableStateFlow<Int?>(torPort)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val fakeTorManager =
            object : com.vitorpamplona.amethyst.commons.tor.ITorManager {
                override val status = statusFlow
                override val activePortOrNull = portFlow

                override suspend fun dormant() {}

                override suspend fun active() {}

                override suspend fun newIdentity() {}
            }
        return DesktopHttpClient(fakeTorManager, shouldUseTor, scope)
    }

    @Test
    fun torOff_returnsDirectClient() {
        val httpClient = buildTorAwareClient(torPort = null)
        val url = NormalizedRelayUrl("wss://relay.damus.io/")
        val client = httpClient.getHttpClient(url)

        assertNotNull(client)
        assertNull(client.proxy, "Should not have proxy when Tor is off")
    }

    @Test
    fun torOn_localhostRelay_returnsDirectClient() {
        val httpClient = buildTorAwareClient(torPort = 9050, shouldUseTor = { true })
        val url = NormalizedRelayUrl("ws://127.0.0.1:8080/")
        val client = httpClient.getHttpClient(url)

        assertNull(client.proxy, "Localhost should never use proxy")
    }

    @Test
    fun torOn_clearnetRelay_shouldUseTorTrue_returnsProxiedClient() {
        val httpClient = buildTorAwareClient(torPort = 9050, shouldUseTor = { true })
        val url = NormalizedRelayUrl("wss://relay.damus.io/")
        val client = httpClient.getHttpClient(url)

        assertNotNull(client.proxy, "Should have SOCKS proxy when Tor routing is enabled")
        assertEquals(java.net.Proxy.Type.SOCKS, client.proxy!!.type())
    }

    @Test
    fun torOn_clearnetRelay_shouldUseTorFalse_returnsDirectClient() {
        val httpClient = buildTorAwareClient(torPort = 9050, shouldUseTor = { false })
        val url = NormalizedRelayUrl("wss://relay.damus.io/")
        val client = httpClient.getHttpClient(url)

        assertNull(client.proxy, "Should not proxy when shouldUseTor returns false")
    }

    @Test
    fun torOn_onionRelay_alwaysProxied() {
        val httpClient = buildTorAwareClient(torPort = 9050, shouldUseTor = { false })
        val url = NormalizedRelayUrl("wss://abc123.onion/")
        val client = httpClient.getHttpClient(url)

        assertNotNull(client.proxy, ".onion relays should always use proxy when Tor is active")
    }

    @Test
    fun proxyClient_hasDoubledTimeouts() {
        val httpClient = buildTorAwareClient(torPort = 9050, shouldUseTor = { true })
        val url = NormalizedRelayUrl("wss://relay.damus.io/")
        val client = httpClient.getHttpClient(url)

        // Desktop uses 2x multiplier: 30 * 2 = 60 seconds
        assertEquals(60_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(60_000, client.writeTimeoutMillis)
    }

    @Test
    fun directClient_hasBaseTimeouts() {
        val httpClient = buildTorAwareClient(torPort = null)
        val client = httpClient.getNonProxyClient()

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }
}
