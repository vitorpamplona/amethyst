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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Namecoin `map`-tree walker added to support multi-label
 * `.bit` hosts (e.g. `relay.testls.bit` resolved through `d/testls`).
 *
 * Spec reference: namecoin/proposals ifa-0001 §"map".
 */
class NamecoinSubdomainTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private fun obj(s: String): JsonObject = json.parseToJsonElement(s).jsonObject

    // ── parseHostFlat ──────────────────────────────────────────────────

    @Test
    fun `parseHostFlat splits relay testls bit into d testls and relay`() {
        val r = NamecoinNameResolver.parseHostFlat("relay.testls.bit")
        assertNotNull(r)
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals(listOf("relay"), r.subdomainLabels)
    }

    @Test
    fun `parseHostFlat handles bare testls bit as no subdomain`() {
        val r = NamecoinNameResolver.parseHostFlat("testls.bit")
        assertNotNull(r)
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals(emptyList<String>(), r.subdomainLabels)
    }

    @Test
    fun `parseHostFlat preserves DNS order most-specific first`() {
        val r = NamecoinNameResolver.parseHostFlat("a.b.c.testls.bit")
        assertNotNull(r)
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals(listOf("a", "b", "c"), r.subdomainLabels)
    }

    @Test
    fun `parseHostFlat lowercases case and strips trailing dot`() {
        val r = NamecoinNameResolver.parseHostFlat("RELAY.Testls.BIT.")
        assertNotNull(r)
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals(listOf("relay"), r.subdomainLabels)
    }

    @Test
    fun `parseHostFlat returns null for non-bit hosts and bare TLD`() {
        assertNull(NamecoinNameResolver.parseHostFlat("relay.example.com"))
        assertNull(NamecoinNameResolver.parseHostFlat(".bit"))
        assertNull(NamecoinNameResolver.parseHostFlat(""))
        assertNull(NamecoinNameResolver.parseHostFlat("bit"))
    }

    // ── walkSubdomain ──────────────────────────────────────────────────

    @Test
    fun `walkSubdomain with empty labels returns root unchanged`() {
        val root = obj("""{"ip":["1.2.3.4"],"map":{"sub":{"ip":["5.6.7.8"]}}}""")
        val result = NamecoinNameResolver.walkSubdomain(root, emptyList())
        assertNotNull(result)
        assertEquals(root["ip"], result!!["ip"])
    }

    @Test
    fun `walkSubdomain follows exact label match`() {
        val root = obj("""{"map":{"relay":{"ip":["10.0.0.1"]}}}""")
        val result = NamecoinNameResolver.walkSubdomain(root, listOf("relay"))
        assertNotNull(result)
        assertEquals("[\"10.0.0.1\"]", result!!["ip"].toString())
    }

    @Test
    fun `walkSubdomain falls back to wildcard when exact not found`() {
        val root = obj("""{"map":{"*":{"ip":["10.0.0.99"]}}}""")
        val result = NamecoinNameResolver.walkSubdomain(root, listOf("relay"))
        assertNotNull(result)
        assertEquals("[\"10.0.0.99\"]", result!!["ip"].toString())
    }

    @Test
    fun `walkSubdomain prefers exact match over wildcard`() {
        val root =
            obj(
                """
                {
                  "map": {
                    "relay": {"ip":["1.1.1.1"]},
                    "*":     {"ip":["9.9.9.9"]}
                  }
                }
                """.trimIndent(),
            )
        val result = NamecoinNameResolver.walkSubdomain(root, listOf("relay"))
        assertNotNull(result)
        assertEquals("[\"1.1.1.1\"]", result!!["ip"].toString())
    }

    @Test
    fun `walkSubdomain returns null when neither exact nor wildcard matches`() {
        val root = obj("""{"map":{"other":{"ip":["1.1.1.1"]}}}""")
        val result = NamecoinNameResolver.walkSubdomain(root, listOf("relay"))
        assertNull(result)
    }

    @Test
    fun `walkSubdomain returns null when there is no map at all`() {
        val root = obj("""{"ip":["1.1.1.1"]}""")
        val result = NamecoinNameResolver.walkSubdomain(root, listOf("relay"))
        assertNull(result)
    }

    @Test
    fun `walkSubdomain descends multi-level map tree`() {
        // nested user record: a.b.c.testls.bit -> d/testls.map.c.map.b.map.a
        val root =
            obj(
                """
                {"map":{"c":{"map":{"b":{"map":{"a":{"ip":["7.7.7.7"]}}}}}}}
                """.trimIndent(),
            )
        // parseHostFlat returns labels in DNS order ["a","b","c"]; walker
        // consumes them parent-first, so it walks c -> b -> a.
        val result = NamecoinNameResolver.walkSubdomain(root, listOf("a", "b", "c"))
        assertNotNull(result)
        assertEquals("[\"7.7.7.7\"]", result!!["ip"].toString())
    }

    @Test
    fun `walkSubdomain promotes string shorthand into ip object`() {
        // Per ifa-0001: "map":{"sub":"1.2.3.4"} expands to "map":{"sub":{"ip":["1.2.3.4"]}}
        val root = obj("""{"map":{"sub":"1.2.3.4"}}""")
        val result = NamecoinNameResolver.walkSubdomain(root, listOf("sub"))
        assertNotNull(result)
        assertEquals("[\"1.2.3.4\"]", result!!["ip"].toString())
    }

    @Test
    fun `walkSubdomain merges empty-key defaults at parent level`() {
        // map[""] is a default for items not directly present at the parent
        // level. Items present at the parent take precedence.
        val root =
            obj(
                """
                {
                  "ip": ["overridden"],
                  "map": {
                    "": { "ip": ["ignored"], "tls": [[3,1,1,"AAA="]] }
                  }
                }
                """.trimIndent(),
            )
        val result = NamecoinNameResolver.walkSubdomain(root, emptyList())
        assertNotNull(result)
        // Parent's ip wins; tls (only in default) leaks through.
        assertEquals("[\"overridden\"]", result!!["ip"].toString())
        assertNotNull(result["tls"])
    }

    @Test
    fun `walkSubdomain does NOT inherit tls from parent into subdomain`() {
        // Spec compliance: a parent's `tls` MUST NOT silently authorise a
        // subdomain. Every subdomain that wants TLS pinning must publish
        // its own `tls` (possibly via `map.*` or `map.<sub>`).
        val root =
            obj(
                """
                {
                  "tls": [[2,1,1,"PARENT"]],
                  "map": {
                    "relay": {"relay":"wss://r.example.com/"}
                  }
                }
                """.trimIndent(),
            )
        val sub = NamecoinNameResolver.walkSubdomain(root, listOf("relay"))
        assertNotNull(sub)
        assertNull("subdomain must not inherit parent tls", sub!!["tls"])
    }

    // ── parseRelayUrls(rawValueJson, subdomainLabels) ───────────────────

    @Test
    fun `parseRelayUrls reads top-level relay when no subdomain`() {
        val v = """{"relay":"wss://relay.example.com/"}"""
        assertEquals(listOf("wss://relay.example.com/"), NamecoinNameResolver.parseRelayUrls(v))
    }

    @Test
    fun `parseRelayUrls reads relay from named subdomain via map`() {
        val v =
            """
            {
              "map": {
                "relay": {"relay":"wss://relay.testls.bit/"},
                "mqtt":  {"relay":"wss://mqtt.testls.bit/"}
              }
            }
            """.trimIndent()
        assertEquals(
            listOf("wss://relay.testls.bit/"),
            NamecoinNameResolver.parseRelayUrls(v, listOf("relay")),
        )
        assertEquals(
            listOf("wss://mqtt.testls.bit/"),
            NamecoinNameResolver.parseRelayUrls(v, listOf("mqtt")),
        )
    }

    @Test
    fun `parseRelayUrls falls back to wildcard subdomain`() {
        val v =
            """
            {"map":{"*":{"relay":"wss://wildcard.testls.bit/"}}}
            """.trimIndent()
        assertEquals(
            listOf("wss://wildcard.testls.bit/"),
            NamecoinNameResolver.parseRelayUrls(v, listOf("anything")),
        )
    }

    @Test
    fun `parseRelayUrls returns empty when subdomain is missing`() {
        val v = """{"map":{"other":{"relay":"wss://x/"}}}"""
        assertEquals(
            emptyList<String>(),
            NamecoinNameResolver.parseRelayUrls(v, listOf("relay")),
        )
    }

    @Test
    fun `parseRelayUrls top-level relay does NOT leak into subdomains`() {
        // Operator publishes a relay at testls.bit, but a different
        // subdomain has no relay; we MUST NOT silently route there.
        val v = """{"relay":"wss://testls.bit/","map":{"sub":{"ip":["1.2.3.4"]}}}"""
        assertEquals(
            emptyList<String>(),
            NamecoinNameResolver.parseRelayUrls(v, listOf("sub")),
        )
    }

    // ── parseTlsaRecords(rawValueJson, subdomainLabels) ─────────────────

    @Test
    fun `parseTlsaRecords reads tls under wildcard subdomain`() {
        // The shape live d/testls currently uses: TLSA at map.*.tls.
        val v =
            """
            {
              "ip": ["107.152.38.155"],
              "map": {
                "*": {"tls":[[2,1,1,"Tg9YFjkBrBpRUsuEm1kYZFmLka4mRXsN5ISWy+1E8gk="]]}
              }
            }
            """.trimIndent()
        val recs = NamecoinNameResolver.parseTlsaRecords(v, listOf("relay"))
        assertEquals(1, recs.size)
        assertEquals(NamecoinNameResolver.TlsaUsage.DANE_TA, recs[0].usage)
        assertEquals(
            "Tg9YFjkBrBpRUsuEm1kYZFmLka4mRXsN5ISWy+1E8gk=",
            recs[0].associationDataBase64,
        )
    }

    @Test
    fun `parseTlsaRecords reads tls under named subdomain`() {
        val v =
            """
            {
              "map": {
                "relay": {"tls":[[3,1,1,"AAAAAAAAAAAA"]]},
                "*":     {"tls":[[2,1,1,"WILDCARD"]]}
              }
            }
            """.trimIndent()
        // Exact match wins over wildcard.
        val recs = NamecoinNameResolver.parseTlsaRecords(v, listOf("relay"))
        assertEquals(1, recs.size)
        assertEquals(NamecoinNameResolver.TlsaUsage.DANE_EE, recs[0].usage)
        assertEquals("AAAAAAAAAAAA", recs[0].associationDataBase64)
    }

    @Test
    fun `parseTlsaRecords returns empty when subdomain has no tls`() {
        val v = """{"map":{"relay":{"ip":["1.2.3.4"]}}}"""
        assertTrue(NamecoinNameResolver.parseTlsaRecords(v, listOf("relay")).isEmpty())
    }

    @Test
    fun `parseTlsaRecords still reads top-level when no subdomain passed`() {
        // Backward-compat: existing call sites with no labels read top-level.
        val v = """{"tls":[[2,1,1,"AA=="]]}"""
        val recs = NamecoinNameResolver.parseTlsaRecords(v)
        assertEquals(1, recs.size)
        assertEquals("AA==", recs[0].associationDataBase64)
    }

    @Test
    fun `parseTlsaRecords does NOT inherit parent tls into subdomain`() {
        // Mirror of the relay test: a subdomain that doesn't publish its
        // own tls MUST NOT be treated as if it inherited the parent's.
        val v = """{"tls":[[2,1,1,"PARENT"]],"map":{"sub":{"ip":["1.2.3.4"]}}}"""
        assertTrue(NamecoinNameResolver.parseTlsaRecords(v, listOf("sub")).isEmpty())
    }

    // ── End-to-end shape: the live d/testls scenario ───────────────────

    @Test
    fun `live d testls shape resolves relay testls bit to wildcard tls`() {
        // What the user's live d/testls currently looks like, plus a
        // map.relay node added so the relay-URL resolves.
        val v =
            """
            {
              "ip": "107.152.38.155",
              "map": {
                "*":     { "tls": [[2,1,1,"Tg9YFjkBrBpRUsuEm1kYZFmLka4mRXsN5ISWy+1E8gk="]] },
                "relay": { "relay": "wss://relay.testls.bit/" }
              },
              "nostr": { "names": { "_": "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c" } }
            }
            """.trimIndent()

        // map.relay has the relay URL.
        assertEquals(
            listOf("wss://relay.testls.bit/"),
            NamecoinNameResolver.parseRelayUrls(v, listOf("relay")),
        )
        // map.relay has NO `tls` of its own, so parseTlsaRecords(...,["relay"])
        // must return empty -- the wildcard tls only matches if walkSubdomain
        // resolves to map["*"]. Exact-match wins for "relay" (it exists), so
        // we expect no TLSA. This is the critical behaviour: a record that
        // explicitly lists `relay` must also include its own tls.
        assertTrue(
            "exact-match subdomain without tls must return empty",
            NamecoinNameResolver.parseTlsaRecords(v, listOf("relay")).isEmpty(),
        )

        // For the same record but a subdomain not present in map, the
        // wildcard catches and tls is inherited.
        assertEquals(
            1,
            NamecoinNameResolver.parseTlsaRecords(v, listOf("not-there")).size,
        )
    }

    @Test
    fun `live d testls shape with tls also under map relay works end-to-end`() {
        // The recommended shape: map.relay has BOTH the relay URL AND its
        // own tls (so the exact-match path captures everything).
        val v =
            """
            {
              "ip": "107.152.38.155",
              "map": {
                "*":     { "tls": [[2,1,1,"WILDCARD_HASH"]] },
                "relay": {
                  "relay": "wss://relay.testls.bit/",
                  "tls":   [[2,1,1,"RELAY_HASH"]]
                }
              }
            }
            """.trimIndent()

        assertEquals(
            listOf("wss://relay.testls.bit/"),
            NamecoinNameResolver.parseRelayUrls(v, listOf("relay")),
        )
        val recs = NamecoinNameResolver.parseTlsaRecords(v, listOf("relay"))
        assertEquals(1, recs.size)
        assertEquals("RELAY_HASH", recs[0].associationDataBase64)
    }
}
