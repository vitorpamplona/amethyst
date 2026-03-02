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
            val names = nostrField["names"]?.jsonObject ?: return null
            val pubkeyElem = names[localPart] ?: names["_"] ?: return null
            val pubkey = (pubkeyElem as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
            if (!pubkey.matches(Regex("^[0-9a-fA-F]{64}$"))) return null

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
                localPart = localPart,
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
                return NamecoinNostrResult(pubkey = pubkey.lowercase(), relays = relays, namecoinName = namecoinName)
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
