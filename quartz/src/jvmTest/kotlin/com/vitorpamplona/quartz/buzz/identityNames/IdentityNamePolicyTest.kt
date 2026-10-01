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
package com.vitorpamplona.quartz.buzz.identityNames

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs Buzz's portable "contextual identity names" v1 fixtures (a vendored, unmodified copy of
 * `mobile/test/shared/identity_names/identity-names.fixtures.json`, Apache-2.0) against the port.
 */
class IdentityNamePolicyTest {
    private val fixtures: JsonObject =
        Json
            .parseToJsonElement(
                IdentityNamePolicyTest::class.java
                    .getResourceAsStream("/buzz/identity-names.fixtures.json")!!
                    .bufferedReader()
                    .readText(),
            ).jsonObject

    private fun JsonObject.string(key: String): String? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    @Test
    fun conformsToEveryPortableFixture() {
        assertEquals(IdentityNamePolicy.VERSION, fixtures["version"]!!.jsonPrimitive.int)
        val cases = fixtures["cases"]!!.jsonArray
        assertTrue(cases.isNotEmpty())

        cases.forEach { element ->
            val case = element.jsonObject
            val identities =
                case["identities"]!!.jsonArray.map {
                    val raw = it.jsonObject
                    NamingIdentity(
                        pubkey = raw.string("pubkey")!!,
                        name = raw.string("name")!!,
                        isAgent = raw["isAgent"]?.jsonPrimitive?.boolean ?: false,
                        ownerPubkey = raw.string("ownerPubkey"),
                    )
                }
            val candidates = case["candidates"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.jsonPrimitive.content }
            val actual = IdentityNamePolicy.resolve(identities, viewer = case.string("viewer"), candidates = candidates)

            val expected =
                case["expected"]!!.jsonObject.mapValues { (_, value) ->
                    val obj = value.jsonObject
                    ResolvedIdentityName(obj.string("name")!!, obj.string("qualifier"))
                }
            assertEquals(expected, actual, "fixture ${case.string("name")}")
        }
    }

    @Test
    fun trimsOnlyTheContractWhitespace() {
        val key = "1".repeat(64)
        // U+0085 is not ECMAScript whitespace; U+3000 is.
        val result = IdentityNamePolicy.resolve(listOf(NamingIdentity(key, "\u0085Honey　")))
        assertEquals(ResolvedIdentityName("\u0085Honey"), result[key])
    }

    @Test
    fun rejectsAnInvalidKey() {
        assertFailsWith<IllegalArgumentException> { IdentityNamePolicy.resolve(listOf(NamingIdentity("nope", "Honey"))) }
        assertFailsWith<IllegalArgumentException> { IdentityNamePolicy.resolve(emptyList(), viewer = "nope") }
    }
}
