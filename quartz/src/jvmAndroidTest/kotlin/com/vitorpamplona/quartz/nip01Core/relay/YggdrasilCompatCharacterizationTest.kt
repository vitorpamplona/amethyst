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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterization of how relay URLs on an Yggdrasil overlay behave today.
 *
 * Yggdrasil gives every node an IPv6 address inside `0200::/7` (nodes) and hands out
 * `0300::/8` subnets, with no DNS and no CA-issuable certificate. A relay on the mesh is
 * therefore always reached as a **bracketed IPv6 literal over plain `ws://`** — a shape
 * the relay stack only partially handles.
 *
 * These tests document the CURRENT behavior (including the gaps) so a later fix has a
 * baseline to diff against. Each gap is marked GAP with what a user sees.
 */
class YggdrasilCompatCharacterizationTest {
    // Same node, three legal RFC 4291 spellings of one address.
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
    fun yggdrasilSubnetAddressesAndUppercaseHexWork() {
        assertEquals("ws://[300:1b5d:d0e9:ba58::1]:4848/", "ws://[300:1b5d:d0e9:ba58::1]:4848".normalizeRelayUrl().url)
        // Hex case IS folded, so the uppercase spelling collapses onto the canonical one.
        assertEquals(canonical.normalizeRelayUrl(), uppercase.normalizeRelayUrl())
    }

    /**
     * GAP 1 — zero-compression is NOT canonicalized, so one relay gets two identities.
     *
     * `NormalizedRelayUrl` is the key of the connection pool, the relay-list sets, the NIP-11
     * cache and every per-relay stat map. OkHttp collapses both spellings to one host (below),
     * so the app opens two sockets to the same relay and counts it twice everywhere.
     */
    @Test
    fun gapZeroCompressionSplitsOneRelayIntoTwoIdentities() {
        assertNotEquals(canonical.normalizeRelayUrl(), expanded.normalizeRelayUrl())
        // ...even though they are literally the same host on the wire:
        assertEquals(host(canonical), host(expanded))
    }

    /**
     * GAP 2 — a schemeless IPv6 literal defaults to `wss://`.
     *
     * `isLocalHost()` only knows 127.0.0.1 / localhost / umbrel / 192.168. / .local, so an
     * Yggdrasil address falls through to the clearnet default. No CA issues certificates for
     * `0200::/7` literals, so the resulting wss:// url can only ever fail its TLS handshake.
     */
    @Test
    fun gapSchemelessYggdrasilAddressDefaultsToWss() {
        assertEquals("wss://[201:d0e:9ba5:8bbc::1]:8080/", "[201:d0e:9ba5:8bbc::1]:8080".normalizeRelayUrl().url)
        assertFalse("ws://[201:d0e:9ba5:8bbc::1]:8080/".normalizeRelayUrl().isLocalHost())
    }

    /**
     * GAP 3 — an unbracketed IPv6 literal is rejected outright.
     *
     * `yggdrasilctl getSelf` prints the address unbracketed, which is what a user copies into
     * the "add a relay" field. Normalization returns null and `RelayUrlEditField.submitRelay`
     * has no else branch, so the Add button silently does nothing.
     */
    @Test
    fun gapUnbracketedYggdrasilAddressIsRejected() {
        assertNull(RelayUrlNormalizer.normalizeOrNull("201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5:8080"))
        // Bracketing it by hand is the only accepted form.
        assertTrue(RelayUrlNormalizer.normalizeOrNull("[201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5]:8080") != null)
    }
}
