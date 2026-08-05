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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RelayUrlNormalizer.isLocalHost] and [RelayUrlNormalizer.isOnion] decide whether a relay is
 * exempt from Tor, so they must read the authority and nothing else. Relay urls arrive from
 * other people — NIP-65 lists, relay hints, `r` tags — so a url whose *path* can flip those
 * answers is a url that can drop a Tor user's protection.
 */
class RelayUrlAuthorityAnchoringTest {
    @Test
    fun aPathCannotMakeAForeignHostLookLocal() {
        listOf(
            "wss://evil.example.com/127.0.0.1",
            "wss://evil.example.com/localhost",
            "wss://evil.example.com/192.168.1.1",
            "wss://evil.example.com/umbrel",
            "wss://evil.example.com/x.local/y",
            "wss://evil.example.com/[fd00::1]",
            "wss://evil.example.com/[::1]",
            "wss://evil.example.com/?q=127.0.0.1",
            "wss://evil.example.com/?q=[::1]",
        ).forEach {
            assertFalse(RelayUrlNormalizer.isLocalHost(it), "$it must not read as localhost")
        }
    }

    @Test
    fun aPathCannotMakeAForeignHostLookLikeAnOverlayOrOnion() {
        assertFalse(RelayUrlNormalizer.isOverlayNetwork("wss://evil.example.com/[201:d0e:9ba5:8bbc::1]"))
        assertFalse(RelayUrlNormalizer.isOnion("wss://evil.example.com/?u=http://nos.lol/.onion/"))
        assertFalse(RelayUrlNormalizer.isOnion("wss://evil.example.com/abc.onion"))
    }

    @Test
    fun realLocalAndOnionHostsStillMatch() {
        listOf(
            "ws://127.0.0.1:8080/",
            "ws://localhost:4869/",
            "ws://umbrel:4848/",
            "ws://192.168.1.100:8080/",
            "ws://myrelay.local:8080/",
            "ws://myrelay.local/",
            "ws://foo.localhost:8080/",
            "ws://[::1]:4869/",
            "ws://[fd12:3456::1]:8080/",
            "ws://[fe80::1]/",
        ).forEach {
            assertTrue(RelayUrlNormalizer.isLocalHost(it), "$it must read as localhost")
        }
        // schemeless, as fix() sees it before choosing ws:// vs wss://
        assertTrue(RelayUrlNormalizer.isLocalHost("127.0.0.1:8080"))
        assertTrue(RelayUrlNormalizer.isLocalHost("umbrel:4848"))
    }

    /**
     * `.onion:8080` never matched the old `.onion/` test, so an onion relay on an explicit port
     * was not recognized as onion — it skipped the forced-Tor branch and its hostname went to
     * the clearnet DNS resolver.
     */
    @Test
    fun onionRelaysOnAnExplicitPortAreRecognized() {
        assertTrue(RelayUrlNormalizer.isOnion("wss://abc123.onion:8080/"))
        assertTrue(RelayUrlNormalizer.isOnion("wss://abc123.onion/"))
        assertTrue(RelayUrlNormalizer.isOnion("abc123.onion:8080"))
        assertTrue(RelayUrlNormalizer.isOnion("abc123.onion"))
        // and it now gets ws:// like any other onion relay
        assertEquals("ws://abc123.onion:8080/", "abc123.onion:8080".normalizeRelayUrl().url)
        assertFalse(RelayUrlNormalizer.isOnion("wss://notonion.example.com/"))
    }

    /** Canonicalization must never rewrite anything outside the authority. */
    @Test
    fun canonicalizationLeavesPathsAndQueriesAlone() {
        assertEquals(
            "wss://evil.example.com/x[0:0:0:0:0:0:0:1]y",
            "wss://evil.example.com/x[0:0:0:0:0:0:0:1]y".normalizeRelayUrl().url,
        )
        // ...while still folding a real IPv6 authority
        assertEquals(
            "wss://[::1]/x[0:0:0:0:0:0:0:1]y",
            "wss://[0:0:0:0:0:0:0:1]/x[0:0:0:0:0:0:0:1]y".normalizeRelayUrl().url,
        )
    }

    /**
     * Private IPv4 was substring-matched, which was wrong in both directions.
     */
    @Test
    fun allPrivateIpv4RangesCountAsLocal() {
        listOf(
            "ws://127.0.0.1:8080/",
            "ws://127.1.2.3:8080/",
            "ws://10.0.0.5:4869/",
            "ws://172.16.3.4:4869/",
            "ws://172.31.255.1/",
            "ws://192.168.1.5:4869/",
            "ws://169.254.1.1:4869/",
            "ws://0.0.0.0:4869/",
        ).forEach {
            assertTrue(RelayUrlNormalizer.isLocalHost(it), "$it must read as localhost")
        }
        // a LAN relay therefore gets ws://, not a wss:// that can never hold a certificate
        assertEquals("ws://10.0.0.5:4869/", "10.0.0.5:4869".normalizeRelayUrl().url)
    }

    @Test
    fun publicIpv4AndPrivateLookalikeDomainsAreNotLocal() {
        listOf(
            "wss://127.0.0.1.evil.com/",
            "wss://192.168.evil.com/",
            "wss://10.0.0.5.evil.com/",
            "wss://8.8.8.8:4869/",
            "wss://172.32.0.1/",
            "wss://193.168.1.5/",
            "wss://relay.damus.io/",
            "wss://notlocalhost.example.com/",
            "wss://mylocalhost.io/",
        ).forEach {
            assertFalse(RelayUrlNormalizer.isLocalHost(it), "$it must not read as localhost")
        }
    }

    /**
     * Host names are case-insensitive (RFC 4343), and `fix()` asks these questions before the
     * RFC 3986 pass folds the case — so a case-sensitive test handed `LOCALHOST:8080` and
     * `ABC.ONION:8080` a `wss://` scheme neither host can serve.
     */
    @Test
    fun hostTestsAreCaseInsensitive() {
        assertTrue(RelayUrlNormalizer.isLocalHost("wss://LocalHost:8080/"))
        assertTrue(RelayUrlNormalizer.isLocalHost("LOCALHOST:8080"))
        assertTrue(RelayUrlNormalizer.isLocalHost("wss://MyRelay.LOCAL/"))
        assertTrue(RelayUrlNormalizer.isOnion("wss://ABC123.ONION/"))
        assertTrue(RelayUrlNormalizer.isOnion("ABC.ONION:8080"))
        assertEquals("ws://localhost:8080/", "LOCALHOST:8080".normalizeRelayUrl().url)
        assertEquals("ws://abc.onion:8080/", "ABC.ONION:8080".normalizeRelayUrl().url)
    }

    /** RFC 1034's fully-qualified form ends in a dot; it names the same host. */
    @Test
    fun trailingDotFqdnIsTheSameHost() {
        assertTrue(RelayUrlNormalizer.isLocalHost("wss://localhost./"))
        assertTrue(RelayUrlNormalizer.isLocalHost("wss://myrelay.local./"))
        assertTrue(RelayUrlNormalizer.isOnion("wss://abc123.onion./"))
        assertTrue(RelayUrlNormalizer.isOnion("wss://abc123.onion.:8080/"))
    }

    /**
     * A `://` inside a path is not a scheme separator; only a real RFC 3986 scheme starts the
     * authority. Otherwise the path gets read as the host.
     */
    @Test
    fun aColonSlashSlashInThePathIsNotASchemeSeparator() {
        assertFalse(RelayUrlNormalizer.isLocalHost("relay.example.com/x://127.0.0.1"))
        assertFalse(RelayUrlNormalizer.isOnion("nos.lol/?u=x://abc.onion"))
        // a real scheme still starts the authority
        assertTrue(RelayUrlNormalizer.isLocalHost("wss://127.0.0.1/x://evil.com"))
    }
}
