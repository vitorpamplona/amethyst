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
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamecoinNameResolverTest {
    // ── isNamecoinIdentifier ───────────────────────────────────────────

    @Test
    fun `recognizes dot-bit domains`() {
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("example.bit"))
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("alice@example.bit"))
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("_@example.bit"))
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("EXAMPLE.BIT"))
    }

    @Test
    fun `recognizes d-slash names`() {
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("d/example"))
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("D/Example"))
    }

    @Test
    fun `recognizes id-slash names`() {
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("id/alice"))
        assertTrue(NamecoinNameResolver.isNamecoinIdentifier("ID/Alice"))
    }

    @Test
    fun `rejects non-namecoin identifiers`() {
        assertFalse(NamecoinNameResolver.isNamecoinIdentifier("[email protected]"))
        assertFalse(NamecoinNameResolver.isNamecoinIdentifier("npub1abc"))
        assertFalse(NamecoinNameResolver.isNamecoinIdentifier("some random text"))
        assertFalse(NamecoinNameResolver.isNamecoinIdentifier(""))
    }

    // ── Value format: simple pubkey in d/ ──────────────────────────────

    @Test
    fun `parses simple nostr field from domain value`() {
        val value = """{"nostr":"b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9"}"""
        val result = extractNostrFromValue(value, "d/example", "_")
        assertNotNull(result)
        assertEquals("b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9", result!!.pubkey)
    }

    // ── Value format: extended NIP-05-like in d/ ───────────────────────

    @Test
    fun `parses extended nostr names from domain value`() {
        val value = """{
            "nostr": {
                "names": {
                    "_": "aaaa000000000000000000000000000000000000000000000000000000000001",
                    "alice": "bbbb000000000000000000000000000000000000000000000000000000000002"
                },
                "relays": {
                    "bbbb000000000000000000000000000000000000000000000000000000000002": [
                        "wss://relay.example.com"
                    ]
                }
            }
        }"""

        // Root lookup
        val rootResult = extractNostrFromValue(value, "d/example", "_")
        assertNotNull(rootResult)
        assertEquals("aaaa000000000000000000000000000000000000000000000000000000000001", rootResult!!.pubkey)

        // Named lookup
        val aliceResult = extractNostrFromValue(value, "d/example", "alice")
        assertNotNull(aliceResult)
        assertEquals("bbbb000000000000000000000000000000000000000000000000000000000002", aliceResult!!.pubkey)
        assertEquals(listOf("wss://relay.example.com"), aliceResult.relays)
    }

    @Test
    fun `falls back to root when named user not found`() {
        val value = """{
            "nostr": {
                "names": {
                    "_": "aaaa000000000000000000000000000000000000000000000000000000000001"
                }
            }
        }"""

        val result = extractNostrFromValue(value, "d/example", "nonexistent")
        assertNotNull(result)
        assertEquals("aaaa000000000000000000000000000000000000000000000000000000000001", result!!.pubkey)
    }

    @Test
    fun `root lookup falls back to first entry when no underscore key`() {
        val value = """{
            "nostr": {
                "names": {
                    "m": "6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d"
                }
            }
        }"""

        val result = extractNostrFromValue(value, "d/testls", "_")
        assertNotNull(result)
        assertEquals("6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d", result!!.pubkey)
        assertEquals("m", result.localPart)
    }

    @Test
    fun `non-root lookup does NOT fall back to first entry`() {
        val value = """{
            "nostr": {
                "names": {
                    "m": "6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d"
                }
            }
        }"""

        val result = extractNostrFromValue(value, "d/testls", "alice")
        assertNull(result)
    }

    // ── Value format: single-identity form in d/ ──────────────────────
    //
    // ifa-0001 doesn't mandate that domain records use the `nostr.names`
    // sub-dictionary. A `d/` record can also publish a single identity using
    // the same shape that `id/` uses:
    //   { "nostr": { "pubkey": "hex", "relays": [...] } }
    // This is the natural "this name = this one pubkey" convention; only the
    // root local-part (`_`) is resolvable from this shape.

    @Test
    fun `parses single-identity object form from domain value`() {
        val value = """{
            "nostr": {
                "pubkey": "43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0",
                "relays": ["wss://relay.testls.bit/", "wss://relay.nostr.wine/"]
            }
        }"""

        val result = extractNostrFromValue(value, "d/mstrofnone", "_")
        assertNotNull(result)
        assertEquals(
            "43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0",
            result!!.pubkey,
        )
        assertEquals("_", result.localPart)
        assertEquals(
            listOf("wss://relay.testls.bit/", "wss://relay.nostr.wine/"),
            result.relays,
        )
    }

    @Test
    fun `single-identity object form does NOT resolve non-root local-part`() {
        // The single-identity shape has no `names` map, so a request for
        // `alice@example.bit` must fall through to null — we don't silently
        // pretend the root pubkey owns every sub-identity.
        val value = """{
            "nostr": {
                "pubkey": "43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"
            }
        }"""

        val result = extractNostrFromValue(value, "d/mstrofnone", "alice")
        assertNull(result)
    }

    @Test
    fun `single-identity object form without relays still resolves`() {
        val value = """{
            "nostr": {
                "pubkey": "43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"
            }
        }"""

        val result = extractNostrFromValue(value, "d/mstrofnone", "_")
        assertNotNull(result)
        assertEquals(emptyList<String>(), result!!.relays)
    }

    @Test
    fun `single-identity form with malformed pubkey is rejected`() {
        val value = """{
            "nostr": {
                "pubkey": "not-a-hex-pubkey"
            }
        }"""

        val result = extractNostrFromValue(value, "d/mstrofnone", "_")
        assertNull(result)
    }

    // ── Value format: id/ namespace ────────────────────────────────────

    @Test
    fun `parses simple nostr field from identity value`() {
        val value = """{
            "nostr": "cccc000000000000000000000000000000000000000000000000000000000003",
            "email": "[email protected]"
        }"""
        val result = extractNostrFromIdentityValue(value, "id/alice")
        assertNotNull(result)
        assertEquals("cccc000000000000000000000000000000000000000000000000000000000003", result!!.pubkey)
    }

    @Test
    fun `parses object nostr field from identity value`() {
        val value = """{
            "nostr": {
                "pubkey": "dddd000000000000000000000000000000000000000000000000000000000004",
                "relays": ["wss://relay.example.com", "wss://relay2.example.com"]
            }
        }"""
        val result = extractNostrFromIdentityValue(value, "id/bob")
        assertNotNull(result)
        assertEquals("dddd000000000000000000000000000000000000000000000000000000000004", result!!.pubkey)
        assertEquals(2, result.relays.size)
    }

    // ── Invalid data ───────────────────────────────────────────────────

    @Test
    fun `rejects invalid pubkey lengths`() {
        val value = """{"nostr":"tooshort"}"""
        val result = extractNostrFromValue(value, "d/bad", "_")
        assertNull(result)
    }

    @Test
    fun `rejects non-hex pubkeys`() {
        val value = """{"nostr":"zzzz000000000000000000000000000000000000000000000000000000000000"}"""
        val result = extractNostrFromValue(value, "d/bad", "_")
        assertNull(result)
    }

    @Test
    fun `handles missing nostr field`() {
        val value = """{"ip":"1.2.3.4","map":{"www":{"ip":"1.2.3.4"}}}"""
        val result = extractNostrFromValue(value, "d/example", "_")
        assertNull(result)
    }

    @Test
    fun `handles malformed JSON gracefully`() {
        val value = "not json at all"
        val result = extractNostrFromValue(value, "d/broken", "_")
        assertNull(result)
    }

    // ── Test helpers ───────────────────────────────────────────────────

    /**
     * Directly test value parsing without network access.
     * Simulates what NamecoinNameResolver does after receiving a name_show result.
     */
    private fun extractNostrFromValue(
        jsonValue: String,
        namecoinName: String,
        localPart: String,
    ): NamecoinNostrResult? {
        val json =
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        val obj =
            try {
                json.parseToJsonElement(jsonValue).jsonObject
            } catch (_: Exception) {
                return null
            }

        val nostrField = obj["nostr"] ?: return null

        // Simple form
        if (nostrField is kotlinx.serialization.json.JsonPrimitive && nostrField.isString) {
            val pubkey = nostrField.content
            if (localPart == "_" && pubkey.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                return NamecoinNostrResult(pubkey = pubkey.lowercase(), namecoinName = namecoinName)
            }
            return null
        }

        // Extended form
        if (nostrField is kotlinx.serialization.json.JsonObject) {
            // Helper for single-identity form { "pubkey": "hex", "relays": [...] }
            fun extractSingleIdentity(): NamecoinNostrResult? {
                if (localPart != "_") return null
                val pk =
                    (nostrField["pubkey"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: return null
                if (!pk.matches(Regex("^[0-9a-fA-F]{64}$"))) return null
                val r =
                    try {
                        nostrField["relays"]?.jsonArray?.mapNotNull {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                return NamecoinNostrResult(
                    pubkey = pk.lowercase(),
                    relays = r,
                    namecoinName = namecoinName,
                    localPart = "_",
                )
            }

            val names =
                nostrField["names"]?.jsonObject
                    ?: return extractSingleIdentity()

            // Resolve: exact match → "_" root → first entry (root lookups only)
            val resolvedLocalPart: String
            val pubkey: String

            val exactMatch = names[localPart]
            val rootMatch = names["_"]
            val firstEntry = if (localPart == "_") names.entries.firstOrNull() else null

            when {
                exactMatch is kotlinx.serialization.json.JsonPrimitive &&
                    exactMatch.content.matches(Regex("^[0-9a-fA-F]{64}$")) -> {
                    resolvedLocalPart = localPart
                    pubkey = exactMatch.content
                }

                rootMatch is kotlinx.serialization.json.JsonPrimitive &&
                    rootMatch.content.matches(Regex("^[0-9a-fA-F]{64}$")) -> {
                    resolvedLocalPart = "_"
                    pubkey = rootMatch.content
                }

                firstEntry != null &&
                    firstEntry.value is kotlinx.serialization.json.JsonPrimitive &&
                    (firstEntry.value as kotlinx.serialization.json.JsonPrimitive)
                        .content
                        .matches(Regex("^[0-9a-fA-F]{64}$")) -> {
                    resolvedLocalPart = firstEntry.key
                    pubkey = (firstEntry.value as kotlinx.serialization.json.JsonPrimitive).content
                }

                localPart != "_" -> {
                    // Non-root lookup: don't silently fall back to bare
                    // `pubkey` — that would hand `alice@example.bit` the
                    // root operator's identity.
                    return null
                }

                else -> {
                    // Root lookup, names present but no usable entry: fall
                    // through to single-identity.
                    return extractSingleIdentity()
                }
            }

            val relays =
                try {
                    val relaysMap = nostrField["relays"]?.jsonObject
                    relaysMap?.get(pubkey.lowercase())?.jsonArray?.mapNotNull {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

            return NamecoinNostrResult(
                pubkey = pubkey.lowercase(),
                relays = relays,
                namecoinName = namecoinName,
                localPart = resolvedLocalPart,
            )
        }
        return null
    }

    private fun extractNostrFromIdentityValue(
        jsonValue: String,
        namecoinName: String,
    ): NamecoinNostrResult? {
        val json =
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        val obj =
            try {
                json.parseToJsonElement(jsonValue).jsonObject
            } catch (_: Exception) {
                return null
            }

        val nostrField = obj["nostr"] ?: return null

        if (nostrField is kotlinx.serialization.json.JsonPrimitive && nostrField.isString) {
            val pubkey = nostrField.content
            if (pubkey.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                return NamecoinNostrResult(pubkey = pubkey.lowercase(), namecoinName = namecoinName)
            }
        }

        if (nostrField is kotlinx.serialization.json.JsonObject) {
            val pubkey = (nostrField["pubkey"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (pubkey != null && pubkey.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                val relays =
                    try {
                        nostrField["relays"]?.jsonArray?.mapNotNull {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                return NamecoinNostrResult(
                    pubkey = pubkey.lowercase(),
                    relays = relays,
                    namecoinName = namecoinName,
                )
            }
        }
        return null
    }

    // Needed for jsonArray and jsonObject extensions
    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = this as kotlinx.serialization.json.JsonObject
    private val kotlinx.serialization.json.JsonElement.jsonArray
        get() = this as kotlinx.serialization.json.JsonArray
}
