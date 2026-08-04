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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelayUrlFormatterTest {
    @Test
    fun format() {
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("wss://nostr.mom")?.url)
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("nostr.mom")?.url)
        assertEquals("ws://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("ws://nostr.mom")?.url)
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("wss://nostr.mom/")?.url)
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("https://nostr.mom/")?.url)
        assertEquals("ws://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("http://nostr.mom/")?.url)

        assertEquals("wss://localhost:3030/", RelayUrlNormalizer.normalizeOrNull("wss://localhost:3030")?.url)
        assertEquals("ws://localhost:3030/", RelayUrlNormalizer.normalizeOrNull("localhost:3030")?.url)

        assertEquals("wss://a.onion/", RelayUrlNormalizer.normalizeOrNull("wss://a.onion")?.url)
        assertEquals("ws://a.onion/", RelayUrlNormalizer.normalizeOrNull("a.onion")?.url)
        assertEquals("wss://a.onion/", RelayUrlNormalizer.normalizeOrNull("wss://a.onion/")?.url)
        assertEquals("ws://a.onion/", RelayUrlNormalizer.normalizeOrNull("a.onion/")?.url)

        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("wss://nostr.mom")?.url)

        assertEquals("wss://relay.nostr.band/", RelayUrlNormalizer.normalizeOrNull("Wss://relay.nostr.band")?.url)
    }

    @Test
    fun weirdRelay() {
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://relay%20list%20to%20discover%20the%20user's%20content"))
    }

    @Test
    fun httpWithPathIsNotARelay() {
        // Mastodon/bridge actor urls from `proxy` tags: web resources, not relays
        assertNull(RelayUrlNormalizer.normalizeOrNull("https://mastodon.social/users/amanita_muscaria"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("https://fosstodon.org/ap/users/115532410310000993"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("http://example.com/relay"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("https://nostr.mom/?author=0"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("https://nostr.mom/#section"))

        // but bare hosts still convert, with or without port and trailing slash
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("https://nostr.mom")?.url)
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("https://nostr.mom/")?.url)
        assertEquals("wss://nostr.mom:4443/", RelayUrlNormalizer.normalizeOrNull("https://nostr.mom:4443/")?.url)
        assertEquals("ws://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("http://nostr.mom")?.url)
    }

    @Test
    fun wsWithPathIsStillARelay() {
        assertEquals("wss://relay.nostr.band/all", RelayUrlNormalizer.normalizeOrNull("wss://relay.nostr.band/all")?.url)
        assertEquals(
            "wss://bostr.lecturify.net/?accept=0,1",
            RelayUrlNormalizer.normalizeOrNull("wss://bostr.lecturify.net/?accept=0,1")?.url,
        )
    }

    @Test
    fun brokenSchemeGarbage() {
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss:"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://https//nostr.watch/relay/nostr.21crypto.ch"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://https://lockbox.fiatjaf.com"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("ws://http//nos.lol"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://://plebstr.com"))
    }

    @Test
    fun nostrUriIsNotARelay() {
        assertNull(RelayUrlNormalizer.normalizeOrNull("nostr://nrelay1qqxhwumn8ghj77tpvf6jumt9e2ckgn/"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("nostr://npub1dwy079xmpz7mk02kvz6wan49h02635umk32aa4ufek8t8mjxv58qy2nr22/"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("nostr:nrelay1qq8k2cnfwejhyum99eek7cmfv9kqsm7sdm"))
    }

    @Test
    fun addressablePointerIsNotARelay() {
        assertNull(RelayUrlNormalizer.normalizeOrNull("31990:6be38f8c63df7dbf84db7ec4a6e6fbbd8d19dca3b980efad18585c46f04b26f9:mostr"))
    }

    @Test
    fun authorityGarbage() {
        // userinfo, percent-encoding and commas never appear in a real relay host
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://catuaba@plebs.place/"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://africa.nostr.joburg%0A/"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://bitcoiner,social/"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://#web3/"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("name@domain.com"))
    }

    @Test
    fun interiorWhitespaceAndBackslashes() {
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://nos lol"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://nos.lol/ wss:/nostr.land/  avatar wss:/nostr.wine/"))
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://\\\\relay.damus.io/"))
    }

    @Test
    fun invisibleCharactersAreStripped() {
        assertEquals("wss://nos.lol/", RelayUrlNormalizer.normalizeOrNull("wss://\u200Bnos.lol")?.url)
        assertEquals("wss://nos.lol/", RelayUrlNormalizer.normalizeOrNull("\uFEFFwss://nos.lol")?.url)
    }

    @Test
    fun protocolRelativeUrls() {
        assertEquals("wss://relay.most.pub/", RelayUrlNormalizer.normalizeOrNull("//relay.most.pub/")?.url)
        assertEquals("wss://nos.lol/", RelayUrlNormalizer.normalizeOrNull("//nos.lol/")?.url)
    }

    @Test
    fun ipv6AndLanHostsStillWork() {
        assertEquals("ws://[31b:6f20:c7f2:3ddf::3221]/", RelayUrlNormalizer.normalizeOrNull("ws://[31b:6f20:c7f2:3ddf::3221]/")?.url)
        assertEquals("ws://geyser-relay:7777/", RelayUrlNormalizer.normalizeOrNull("ws://geyser-relay:7777/")?.url)
    }
}
