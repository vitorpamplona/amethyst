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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOverlayNetwork
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Relay URLs on an Yggdrasil overlay, end to end through the normalizer.
 *
 * Yggdrasil gives every node an IPv6 address inside `0200::/7` (nodes in `0200::/8`, subnets in
 * `0300::/8`), with no DNS and no CA-issuable certificate. A relay on the mesh is therefore
 * always a **bracketed IPv6 literal over plain `ws://`**.
 *
 * The differential assertions against OkHttp are the point of this file living in
 * `jvmAndroidTest`: OkHttp is what actually dials the socket, so a normalized url that
 * disagrees with OkHttp's own canonical host is a relay the app tracks under a name it does
 * not connect to.
 */
class YggdrasilCompatCharacterizationTest {
    // Same node, several legal RFC 4291 spellings of one address.
    private val canonical = "ws://[201:d0e:9ba5:8bbc::1]:8080"
    private val expanded = "ws://[201:0d0e:9ba5:8bbc:0000:0000:0000:0001]:8080"
    private val uppercase = "ws://[201:D0E:9BA5:8BBC::1]:8080"

    private fun host(url: String) =
        Request
            .Builder()
            .url(url)
            .build()
            .url.host

    @Test
    fun bracketedLiteralsSurviveNormalizationAndReachOkHttp() {
        val n = canonical.normalizeRelayUrl()
        assertEquals("ws://[201:d0e:9ba5:8bbc::1]:8080/", n.url)
        assertEquals("201:d0e:9ba5:8bbc::1", host(n.url))
        // NIP-11 / relay-icon fetches derive their http url from the same string.
        assertEquals("http://[201:d0e:9ba5:8bbc::1]:8080/", n.toHttp())
    }

    @Test
    fun yggdrasilSubnetAddressesWork() {
        assertEquals("ws://[300:1b5d:d0e9:ba58::1]:4848/", "ws://[300:1b5d:d0e9:ba58::1]:4848".normalizeRelayUrl().url)
    }

    /**
     * Every legal spelling of one address collapses to one [NormalizedRelayUrl] — the key of the
     * connection pool, the relay-list sets, the NIP-11 cache and the per-relay stat maps. Without
     * this the app dials one relay twice and shows it twice.
     */
    @Test
    fun everySpellingOfOneAddressIsOneRelay() {
        val identities = listOf(canonical, expanded, uppercase).map { it.normalizeRelayUrl() }.toSet()
        assertEquals(setOf("ws://[201:d0e:9ba5:8bbc::1]:8080/"), identities.map { it.url }.toSet())
        // ...and that one identity is the host OkHttp dials for all of them.
        assertEquals(setOf("201:d0e:9ba5:8bbc::1"), listOf(canonical, expanded, uppercase).map { host(it) }.toSet())
    }

    @Test
    fun normalizedIdentityAlwaysMatchesTheHostOkHttpDials() {
        listOf(
            "ws://[201:0d0e:9ba5:8bbc:0000:0000:0000:0001]:8080",
            "ws://[300:1b5d:d0e9:ba58:0:0:0:1]:4848",
            "ws://[2001:0DB8:0000:0000:0000:0000:0000:0001]:7777",
            "ws://[::1]:4869",
        ).forEach { raw ->
            val normalized = raw.normalizeRelayUrl().url
            assertEquals(host(normalized), host(raw), "identity for $raw disagrees with the dialed host")
        }
    }

    /**
     * A schemeless overlay address defaults to `ws://`: no CA issues certificates for
     * `0200::/7`, so `wss://` could only ever fail its handshake. The mesh already encrypts
     * end to end, so this is not a downgrade.
     */
    @Test
    fun schemelessOverlayAddressDefaultsToWs() {
        assertEquals("ws://[201:d0e:9ba5:8bbc::1]:8080/", "[201:d0e:9ba5:8bbc::1]:8080".normalizeRelayUrl().url)
        assertTrue("ws://[201:d0e:9ba5:8bbc::1]:8080/".normalizeRelayUrl().isOverlayNetwork())
        // A clearnet IPv6 relay keeps requiring TLS.
        assertEquals("wss://[2001:db8::1]:8080/", "[2001:db8::1]:8080".normalizeRelayUrl().url)
        assertFalse("wss://[2001:db8::1]:8080/".normalizeRelayUrl().isOverlayNetwork())
    }

    /**
     * `::1`, `fc00::/7` and `fe80::/10` are the IPv6 twins of 127.0.0.1 and 192.168., so they
     * answer [isLocalHost] the same way — no TLS, no Tor, never advertised to the network.
     */
    @Test
    fun ipv6LoopbackAndPrivateRangesCountAsLocalHost() {
        assertEquals("ws://[::1]:4869/", "[::1]:4869".normalizeRelayUrl().url)
        assertTrue("ws://[::1]:4869/".normalizeRelayUrl().isLocalHost())
        assertTrue("ws://[fd12:3456::1]:8080/".normalizeRelayUrl().isLocalHost(), "unique local address")
        assertTrue("ws://[fe80::1]:8080/".normalizeRelayUrl().isLocalHost(), "link local address")
        assertFalse("wss://[2001:db8::1]:8080/".normalizeRelayUrl().isLocalHost(), "clearnet ipv6")
        // An overlay relay is reachable across the mesh, so it is NOT localhost.
        assertFalse("ws://[201:d0e:9ba5:8bbc::1]:8080/".normalizeRelayUrl().isLocalHost())
    }

    /**
     * `yggdrasilctl getSelf` prints the address unbracketed, which is what a user pastes into
     * the "add a relay" field. It is bracketed automatically rather than rejected.
     */
    @Test
    fun bareUnbracketedLiteralIsBracketedAutomatically() {
        assertEquals(
            "ws://[201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5]/",
            "201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5".normalizeRelayUrl().url,
        )
        assertEquals("ws://[201:d0e:9ba5:8bbc::1]/", "201:d0e:9ba5:8bbc::1".normalizeRelayUrl().url)
        assertNotNull(RelayUrlNormalizer.normalizeOrNull("[201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5]:8080"))
    }

    /**
     * Auto-bracketing must not swallow the other colon-bearing strings that reach the
     * normalizer. Only a string that parses as a whole IPv6 address is bracketed.
     */
    @Test
    fun autoBracketingDoesNotCaptureNonAddresses() {
        assertEquals("wss://relay.example.com:8080/", "relay.example.com:8080".normalizeRelayUrl().url)
        assertEquals("ws://localhost:4869/", "localhost:4869".normalizeRelayUrl().url)
        // addressable-event pointer, not a relay
        assertEquals(null, RelayUrlNormalizer.normalizeOrNull("31990:abcdef:mydtag"))
        // two hex-looking groups are a host and a port, not an address
        assertEquals("wss://abcd:1234/", "abcd:1234".normalizeRelayUrl().url)
    }
}
