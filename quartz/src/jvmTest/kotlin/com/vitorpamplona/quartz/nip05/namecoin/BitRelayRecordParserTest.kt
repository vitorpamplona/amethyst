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
package com.vitorpamplona.quartz.nip05.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the shared relay-record parser
 * [NamecoinNameResolver.parseRelayUrls] and the
 * `.bit`-host → `d/<name>` mapping in
 * [NamecoinNameResolver.toNamecoinName].
 *
 * These are the "format spec" tests for the `.bit` relay record shape;
 * end-to-end resolver behaviour (caching, timeouts, error mapping)
 * lives in [BitRelayResolverTest].
 */
class BitRelayRecordParserTest {
    // ── parseRelayUrls ─────────────────────────────────────────────────

    @Test
    fun `parses top-level relay string`() {
        val urls = NamecoinNameResolver.parseRelayUrls("""{"relay":"wss://relay.example.com/"}""")
        assertEquals(listOf("wss://relay.example.com/"), urls)
    }

    @Test
    fun `parses top-level relays array`() {
        val urls =
            NamecoinNameResolver.parseRelayUrls(
                """{"relays":["wss://a.example.com","wss://b.example.com"]}""",
            )
        assertEquals(listOf("wss://a.example.com", "wss://b.example.com"), urls)
    }

    @Test
    fun `parses nested nostr relay shape`() {
        val urls =
            NamecoinNameResolver.parseRelayUrls(
                """{"nostr":{"relay":"wss://nested.example.com/"}}""",
            )
        assertEquals(listOf("wss://nested.example.com/"), urls)
    }

    @Test
    fun `parses pubkey-keyed nostr relays`() {
        val pubkey = "a".repeat(64)
        val urls =
            NamecoinNameResolver.parseRelayUrls(
                """{"nostr":{"relays":{"$pubkey":["wss://pubkey.example.com/"]}}}""",
            )
        assertEquals(listOf("wss://pubkey.example.com/"), urls)
    }

    @Test
    fun `prefers top-level relay over nested`() {
        val urls =
            NamecoinNameResolver.parseRelayUrls(
                """
                {
                  "relay":  "wss://primary.example.com/",
                  "relays": ["wss://primary-2.example.com/"],
                  "nostr":  { "relay": "wss://nested.example.com/" }
                }
                """.trimIndent(),
            )
        // top-level relay first, then top-level relays array, then nested
        assertEquals(
            listOf(
                "wss://primary.example.com/",
                "wss://primary-2.example.com/",
                "wss://nested.example.com/",
            ),
            urls,
        )
    }

    @Test
    fun `de-duplicates equal urls`() {
        val urls =
            NamecoinNameResolver.parseRelayUrls(
                """
                {
                  "relay":  "wss://same.example.com/",
                  "relays": ["wss://same.example.com/", "wss://other.example.com/"]
                }
                """.trimIndent(),
            )
        assertEquals(
            listOf("wss://same.example.com/", "wss://other.example.com/"),
            urls,
        )
    }

    @Test
    fun `ignores non-ws schemes`() {
        val urls =
            NamecoinNameResolver.parseRelayUrls(
                """{"relay":"https://not-a-relay.example.com/"}""",
            )
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `survives malformed json`() {
        assertTrue(NamecoinNameResolver.parseRelayUrls("not json").isEmpty())
        assertTrue(NamecoinNameResolver.parseRelayUrls("{").isEmpty())
        assertTrue(NamecoinNameResolver.parseRelayUrls("").isEmpty())
    }

    // ── toNamecoinName ─────────────────────────────────────────────────

    @Test
    fun `maps bare bit host to d slash name`() {
        assertEquals("d/example", NamecoinNameResolver.toNamecoinName("example.bit"))
        assertEquals("d/example", NamecoinNameResolver.toNamecoinName("EXAMPLE.BIT"))
    }

    @Test
    fun `maps user at bit to d slash name`() {
        assertEquals("d/example", NamecoinNameResolver.toNamecoinName("alice@example.bit"))
        assertEquals("d/example", NamecoinNameResolver.toNamecoinName("_@example.bit"))
    }

    @Test
    fun `passes through d slash and id slash`() {
        assertEquals("d/example", NamecoinNameResolver.toNamecoinName("d/example"))
        assertEquals("id/alice", NamecoinNameResolver.toNamecoinName("id/alice"))
    }

    @Test
    fun `rejects non-namecoin identifiers`() {
        assertNull(NamecoinNameResolver.toNamecoinName("[email protected]"))
        assertNull(NamecoinNameResolver.toNamecoinName("not a name"))
        assertNull(NamecoinNameResolver.toNamecoinName(""))
    }
}
