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
package com.vitorpamplona.quartz.marmot.conformance

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaV2
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The reference implementation's own `imeta` fixtures, run against our parser.
 *
 * These are `mdk/fixtures/encrypted-media/imeta-{v1,v2}.json`, copied verbatim.
 * The file says what they are for: "Shared by marmot-app, marmot-uniffi, and
 * wn-cli tests so every layer agrees on validation verdicts and exact wire
 * round-trips." We are another layer, and until now we agreed with nobody but
 * ourselves — our own tests would happily bless a parser that read the tag
 * differently from every other client, because they were written from the same
 * reading of the spec as the parser.
 *
 * The interesting half is the rejections. A parser that is merely lenient
 * passes every golden case and still cannot be interoperated with: it accepts
 * tags a conformant sender never emits, so it silently renders media that
 * another client refuses, and the two disagree about what the group contains.
 */
class EncryptedMediaImetaVectorTest {
    private fun fixture(name: String) = Json.parseToJsonElement(TestResourceLoader().loadString("marmot/conformance/$name")).jsonObject

    private val v2 by lazy { fixture("imeta-v2.json") }
    private val v1 by lazy { fixture("imeta-v1.json") }

    private fun cases(fixture: kotlinx.serialization.json.JsonObject) = fixture["cases"]!!.jsonArray.map { it.jsonObject }

    private fun tagOf(case: kotlinx.serialization.json.JsonObject) = case["tag"]!!.jsonArray.map { it.jsonPrimitive.content }.toTypedArray()

    private fun nameOf(case: kotlinx.serialization.json.JsonObject) = case["name"]!!.jsonPrimitive.content

    @Test
    fun everyGoldenV2CaseParsesToExactlyTheExpectedFields() {
        val golden = cases(v2).filter { it["valid"]!!.jsonPrimitive.content == "true" }
        assertTrue(golden.size >= 5, "expected the full golden set, got ${golden.size}")

        for (case in golden) {
            val name = nameOf(case)
            val reference =
                try {
                    EncryptedMediaV2.parseImetaTag(tagOf(case))
                } catch (e: Exception) {
                    fail("golden case '$name' was rejected: ${e.message}")
                }
            val expected = case["expected"]!!.jsonObject

            assertEquals(
                expected["locators"]!!.jsonArray.map {
                    it.jsonObject["kind"]!!.jsonPrimitive.content to it.jsonObject["value"]!!.jsonPrimitive.content
                },
                reference.locators.map { it.kind to it.value },
                "$name locators (order is the producer's and is preserved)",
            )
            assertEquals(expected["ciphertext_sha256"]!!.jsonPrimitive.content, reference.ciphertextSha256.toHexKey(), "$name ciphertext_sha256")
            assertEquals(expected["plaintext_sha256"]!!.jsonPrimitive.content, reference.plaintextSha256.toHexKey(), "$name plaintext_sha256")
            assertEquals(expected["nonce_hex"]!!.jsonPrimitive.content, reference.nonce.toHexKey(), "$name nonce")
            assertEquals(expected["media_type"]!!.jsonPrimitive.content, reference.mediaType, "$name m")
            assertEquals(expected["file_name"]!!.jsonPrimitive.content, reference.filename, "$name filename")

            // The fixture distinguishes absent (null) from present-but-empty
            // (""), and so must we: a hint that was written and left blank is
            // not the same wire state as one that was never written, and
            // collapsing them changes what a re-encode emits.
            assertEquals(optional(expected, "dim"), reference.dim, "$name dim")
            assertEquals(optional(expected, "thumbhash"), reference.thumbhash, "$name thumbhash")
        }
    }

    @Test
    fun everyRejectionV2CaseIsRejected() {
        val rejections = cases(v2).filter { it["valid"]!!.jsonPrimitive.content == "false" }
        assertTrue(rejections.size >= 5, "expected the full rejection set, got ${rejections.size}")

        for (case in rejections) {
            val name = nameOf(case)
            assertNull(
                EncryptedMediaV2.parseImetaTagOrNull(tagOf(case)),
                "'$name' must be rejected — the fixture expects '${case["error_contains"]?.jsonPrimitive?.content}'",
            )
        }
    }

    @Test
    fun aV1TagIsNotReadableAsV2() {
        // "Component id `0x8008` remains the frozen v1 policy and MUST NOT be
        // reinterpreted as v2." The two share a field layout closely enough
        // that a parser keying only on the fields would happily read one as the
        // other — and then derive a file key under the wrong scheme. So every
        // v1 case, including the ones v1 itself calls valid, has to bounce off
        // the v2 parser.
        val v1Cases = cases(v1)
        assertTrue(v1Cases.isNotEmpty(), "expected the v1 fixture to carry cases")
        assertEquals("encrypted-media-v1", v1["media_version"]!!.jsonPrimitive.content)

        for (case in v1Cases) {
            assertNull(
                EncryptedMediaV2.parseImetaTagOrNull(tagOf(case)),
                "v1 case '${nameOf(case)}' must not parse as ${EncryptedMediaPolicyV2.MEDIA_FORMAT}",
            )
        }
    }

    /** A fixture field that is absent, JSON null, or a real string. */
    private fun optional(
        obj: kotlinx.serialization.json.JsonObject,
        key: String,
    ): String? =
        obj[key]?.let { element ->
            val primitive = element.jsonPrimitive
            if (primitive.isString) primitive.content else null
        }
}
