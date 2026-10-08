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
package com.vitorpamplona.quartz.utils.unicode

import com.vitorpamplona.quartz.utils.UnicodeNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Runs on every target, so each platform's [UnicodeNormalizer] (JDK, NSString, and the
 * pure-Kotlin [NfkcNormalizer] on Linux) must agree with these UAX #15 results.
 */
class UnicodeNormalizerTest {
    private val cases =
        listOf(
            // NIP-49 spec example: the password must be NFKC-normalized before scrypt.
            "ÅΩẛ̣" to "ÅΩṩ",
            // Decomposed Latin recomposes.
            "é" to "é",
            "Å" to "Å",
            // Compatibility mappings.
            " " to " ",
            "ﬁ" to "fi",
            "①" to "1",
            "Ａｂｃ" to "Abc",
            "½" to "1⁄2",
            "㎒" to "MHz",
            // Canonical ordering: dot below (220) sorts before circumflex (230), then composes.
            "ậ" to "ậ",
            "ậ" to "ậ",
            // Hangul: conjoining jamo compose into syllables, syllables stay composed.
            "각" to "각",
            "각" to "각",
            // Supplementary plane: mathematical bold A maps to A.
            "𝐀" to "A",
            // A leading combining mark has nothing to compose with.
            "́e" to "́e",
            // Singleton decomposition: ANGSTROM SIGN is never recomposed.
            "Å" to "Å",
            // Composition exclusion: DEVANAGARI QA stays decomposed.
            "क़" to "क़",
        )

    @Test
    fun platformNormalizerMatchesReferenceResults() {
        val normalizer = UnicodeNormalizer()
        cases.forEach { (input, expected) ->
            assertEquals(expected, normalizer.normalizeNFKC(input), "input: ${input.hexCodePoints()}")
        }
    }

    @Test
    fun pureKotlinNormalizerMatchesReferenceResults() {
        cases.forEach { (input, expected) ->
            assertEquals(expected, NfkcNormalizer.normalize(input), "input: ${input.hexCodePoints()}")
        }
    }

    @Test
    fun asciiIsReturnedUnchanged() {
        val ascii = "correct horse battery staple 123!@#"
        assertSame(ascii, NfkcNormalizer.normalize(ascii))
        assertEquals("", NfkcNormalizer.normalize(""))
    }

    private fun String.hexCodePoints() = map { it.code.toString(16) }
}
