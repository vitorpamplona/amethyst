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
package com.vitorpamplona.quartz.experimental.graperank

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [GrapeRankDataCrawler.authorityOf] is the key the crawl's timeout-eviction counts
 * on. It must collapse the many per-user path URLs the outbox model mints for one
 * server into a single host, WITHOUT folding a distinct sibling host (e.g. a
 * `filter.` subdomain) into its parent.
 */
class GrapeRankAuthorityTest {
    private fun auth(url: String) = GrapeRankDataCrawler.authorityOf(url)

    @Test
    fun bareHostIsItsOwnAuthority() {
        assertEquals("relay.damus.io", auth("wss://relay.damus.io"))
        assertEquals("relay.damus.io", auth("wss://relay.damus.io/"))
        assertEquals("nos.lol", auth("ws://nos.lol"))
    }

    @Test
    fun perUserPathUrlsOnOneHostCollapseToOneAuthority() {
        val a = auth("wss://filter.nostr.wine/npub1aaaa?broadcast=true")
        val b = auth("wss://filter.nostr.wine/npub1bbbb?broadcast=true&global=all")
        val c = auth("wss://filter.nostr.wine/?global=all")
        assertEquals("filter.nostr.wine", a)
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun filterSubdomainIsNotFoldedIntoBareHost() {
        // nostr.wine reads are open; filter.nostr.wine is a different server that may
        // stall — evicting one must never take out the other.
        assertNotEquals(auth("wss://filter.nostr.wine/npub1x"), auth("wss://nostr.wine"))
    }

    @Test
    fun portIsPartOfTheAuthority() {
        assertEquals("relay.veganostr.com:443", auth("wss://relay.veganostr.com:443/npub1z"))
        assertEquals("81.68.170.122:7114", auth("ws://81.68.170.122:7114/"))
        assertNotEquals(auth("wss://example.com:443"), auth("wss://example.com:8080"))
    }
}
