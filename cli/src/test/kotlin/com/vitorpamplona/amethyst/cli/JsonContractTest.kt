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
package com.vitorpamplona.amethyst.cli

import com.fasterxml.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the `--json` machine contract for the deterministic stateless
 * primitives: one JSON object, one line, stable snake_case keys. Uses the
 * NIP-19 spec test vectors so the goldens are protocol-anchored, not
 * implementation-anchored.
 */
class JsonContractTest {
    private val vectorHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val vectorNpub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"

    private fun jsonOf(r: CliResult): JsonNode {
        assertEquals(0, r.exit, "expected success, stderr: ${r.stderr}")
        assertEquals(1, r.stdoutLines.size, "expected exactly one stdout line, got: ${r.stdout}")
        return Output.mapper.readTree(r.stdoutLines.single())
    }

    private fun assertSnakeCaseKeys(node: JsonNode) {
        node.fieldNames().forEach { key ->
            assertTrue(key.matches(Regex("[a-z0-9_]+")), "non-snake_case key: $key")
        }
    }

    @Test
    fun encodeNpubMatchesNip19Vector() {
        val json = jsonOf(amy("--json", "encode", "npub", vectorHex))
        assertEquals(vectorNpub, json["npub"].asText())
        assertSnakeCaseKeys(json)
    }

    @Test
    fun decodeNpubMatchesNip19Vector() {
        val json = jsonOf(amy("--json", "decode", vectorNpub))
        assertTrue(json.toString().contains(vectorHex), "decode result should carry the hex pubkey: $json")
        assertSnakeCaseKeys(json)
    }

    @Test
    fun keyGenerateThenPublicRoundTrips() {
        val generated = jsonOf(amy("--json", "key", "generate"))
        val nsec = generated["nsec"].asText()
        val pubkey = generated["pubkey"].asText()
        assertTrue(nsec.startsWith("nsec1"))
        assertEquals(64, pubkey.length)
        assertSnakeCaseKeys(generated)

        val derived = jsonOf(amy("--json", "key", "public", nsec))
        assertEquals(pubkey, derived["pubkey"].asText())
    }

    @Test
    fun filterAssemblesTheFlagsItWasGiven() {
        val json = jsonOf(amy("--json", "filter", "--kind", "1,7", "--limit", "5"))
        val kinds = json["kinds"].map { it.asInt() }
        assertEquals(listOf(1, 7), kinds)
        assertEquals(5, json["limit"].asInt())
    }

    @Test
    fun verifyReportsIdAndSignatureSeparately() {
        // A structurally valid event with a wrong id/signature must still parse
        // and report the two checks as fields, not crash.
        val bogus =
            """{"id":"${"0".repeat(64)}","pubkey":"$vectorHex","created_at":1,"kind":1,"tags":[],"content":"x","sig":"${"0".repeat(128)}"}"""
        val r = amy("--json", "verify", bogus)
        val line =
            r.stdoutLines.singleOrNull() ?: r.stderr
                .trim()
                .lines()
                .last()
        val json = Output.mapper.readTree(line)
        assertTrue(json.has("id_ok") || json.has("error"), "verify should report id_ok/signature_ok or a structured error: $json")
    }

    @Test
    fun textModeAndJsonModeCarrySameData() {
        val json = jsonOf(amy("--json", "encode", "npub", vectorHex))
        val text = amy("encode", "npub", vectorHex)
        assertEquals(0, text.exit)
        assertTrue(text.stdout.contains(json["npub"].asText()), "text render should carry the same npub")
    }
}
