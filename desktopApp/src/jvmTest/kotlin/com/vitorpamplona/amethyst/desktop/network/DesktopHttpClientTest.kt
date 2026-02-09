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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopHttpClientTest {
    @Test
    fun testGetHttpClientReturnsConfiguredClient() {
        val url = NormalizedRelayUrl("wss://relay.damus.io")
        val client = DesktopHttpClient.getHttpClient(url)

        assertNotNull(client)
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.pingIntervalMillis)
        assertTrue(client.retryOnConnectionFailure)
    }

    @Test
    fun testGetHttpClientReturnsSameInstance() {
        val url1 = NormalizedRelayUrl("wss://relay.damus.io")
        val url2 = NormalizedRelayUrl("wss://nos.lol")

        val client1 = DesktopHttpClient.getHttpClient(url1)
        val client2 = DesktopHttpClient.getHttpClient(url2)

        // Should return the same singleton instance
        assertEquals(client1, client2)
    }

    @Test
    fun testHttpClientHasExpectedTimeouts() {
        val url = NormalizedRelayUrl("wss://relay.nostr.band")
        val client = DesktopHttpClient.getHttpClient(url)

        // Verify all timeouts are 30 seconds
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.pingIntervalMillis)
    }

    @Test
    fun testHttpClientHasRetryEnabled() {
        val url = NormalizedRelayUrl("wss://relay.snort.social")
        val client = DesktopHttpClient.getHttpClient(url)

        assertTrue(client.retryOnConnectionFailure, "Retry on connection failure should be enabled")
    }

    @Test
    fun testHttpClientIsLazyInitialized() {
        // This test verifies the lazy initialization pattern
        // The client should be created only once even with multiple calls
        val url1 = NormalizedRelayUrl("wss://relay1.example.com")
        val url2 = NormalizedRelayUrl("wss://relay2.example.com")
        val url3 = NormalizedRelayUrl("wss://relay3.example.com")

        val client1 = DesktopHttpClient.getHttpClient(url1)
        val client2 = DesktopHttpClient.getHttpClient(url2)
        val client3 = DesktopHttpClient.getHttpClient(url3)

        // All should be the same instance due to lazy singleton
        assertEquals(client1, client2)
        assertEquals(client2, client3)
        assertEquals(client1, client3)
    }
}
