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

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class NamecoinNostrResult(
    val pubkey: String,
    val relays: List<String> = emptyList(),
    val namecoinName: String,
    val localPart: String = "_",
)

class NamecoinNameResolver(
    private val electrumxClient: ElectrumxClient = ElectrumxClient(),
    private val lookupTimeoutMs: Long = 20_000L,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    companion object {
        private val HEX_PUBKEY_REGEX = Regex("^[0-9a-fA-F]{64}$")

        fun isNamecoinIdentifier(identifier: String): Boolean {
            val n = identifier.trim().lowercase()
            return n.endsWith(".bit") || n.startsWith("d/") || n.startsWith("id/")
        }
    }

    suspend fun resolve(identifier: String): NamecoinNostrResult? {
        val parsed = parseIdentifier(identifier) ?: return null
        return withTimeoutOrNull(lookupTimeoutMs) { performLookup(parsed) }
    }

    // ── Parsing ────────────────────────────────────────────────────────

    private data class ParsedId(
        val namecoinName: String,
        val localPart: String,
        val ns: NS,
    )

    private enum class NS { DOMAIN, IDENTITY }

    private fun parseIdentifier(raw: String): ParsedId? {
        val input = raw.trim()
        if (input.startsWith("d/", ignoreCase = true)) {
            return ParsedId(input.lowercase(), "_", NS.DOMAIN)
        }
        if (input.startsWith("id/", ignoreCase = true)) {
            return ParsedId(input.lowercase(), "_", NS.IDENTITY)
        }
        if (input.contains("@") && input.endsWith(".bit", ignoreCase = true)) {
            val parts = input.split("@", limit = 2)
            if (parts.size != 2) return null
            val lp = parts[0].lowercase().ifEmpty { "_" }
            val domain = parts[1].removeSuffix(".bit").removeSuffix(".BIT").lowercase()
            if (domain.isEmpty()) return null
            return ParsedId("d/$domain", lp, NS.DOMAIN)
        }
        if (input.endsWith(".bit", ignoreCase = true)) {
            val domain = input.removeSuffix(".bit").removeSuffix(".BIT").lowercase()
            if (domain.isEmpty()) return null
            return ParsedId("d/$domain", "_", NS.DOMAIN)
        }
        return null
    }

    // ── Lookup ─────────────────────────────────────────────────────────

    private suspend fun performLookup(p: ParsedId): NamecoinNostrResult? {
        val nr = electrumxClient.nameShowWithFallback(p.namecoinName) ?: return null
        val obj = tryParseJson(nr.value) ?: return null
        return when (p.ns) {
            NS.DOMAIN -> extractDomain(obj, p)
            NS.IDENTITY -> extractIdentity(obj, p)
        }
    }

    private fun extractDomain(
        v: JsonObject,
        p: ParsedId,
    ): NamecoinNostrResult? {
        val nf = v["nostr"] ?: return null
        if (nf is JsonPrimitive && nf.isString) {
            val pk = nf.content
            if (p.localPart == "_" && isValid(pk)) {
                return NamecoinNostrResult(pk.lowercase(), namecoinName = p.namecoinName)
            }
            if (p.localPart != "_") return null
        }
        if (nf is JsonObject) {
            val names = nf["names"]?.jsonObject ?: return null
            val pe = names[p.localPart] ?: names["_"] ?: return null
            val pk = (pe as? JsonPrimitive)?.content ?: return null
            if (!isValid(pk)) return null
            return NamecoinNostrResult(pk.lowercase(), extractRelays(nf, pk), p.namecoinName, p.localPart)
        }
        return null
    }

    private fun extractIdentity(
        v: JsonObject,
        p: ParsedId,
    ): NamecoinNostrResult? {
        val nf = v["nostr"] ?: return null
        if (nf is JsonPrimitive && nf.isString) {
            val pk = nf.content
            if (isValid(pk)) return NamecoinNostrResult(pk.lowercase(), namecoinName = p.namecoinName)
        }
        if (nf is JsonObject) {
            val pk = (nf["pubkey"] as? JsonPrimitive)?.content
            if (pk != null && isValid(pk)) {
                val relays =
                    try {
                        nf["relays"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                return NamecoinNostrResult(pk.lowercase(), relays, p.namecoinName)
            }
            val names = nf["names"]?.jsonObject
            if (names != null) {
                val rpk = (names["_"] as? JsonPrimitive)?.content
                if (rpk != null && isValid(rpk)) {
                    return NamecoinNostrResult(rpk.lowercase(), extractRelays(nf, rpk), p.namecoinName)
                }
            }
        }
        return null
    }

    private fun extractRelays(
        obj: JsonObject,
        pk: String,
    ): List<String> =
        try {
            val m = obj["relays"]?.jsonObject ?: return emptyList()
            val a = m[pk.lowercase()]?.jsonArray ?: m[pk]?.jsonArray ?: return emptyList()
            a.mapNotNull { (it as? JsonPrimitive)?.content }
        } catch (_: Exception) {
            emptyList()
        }

    private fun tryParseJson(raw: String): JsonObject? =
        try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }

    private fun isValid(s: String) = HEX_PUBKEY_REGEX.matches(s)
}
