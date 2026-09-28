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

import java.text.Normalizer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks the pure-Kotlin [NfkcNormalizer] (used on Linux native) against the JDK's
 * `java.text.Normalizer`. The JDK can trail the Unicode version of [NfkcData], so only
 * code points the JDK knows are compared; normalization stability guarantees their
 * results don't change in later Unicode versions.
 */
class NfkcNormalizerJdkParityTest {
    private val definedCodePoints =
        (0..0x10FFFF).filter { Character.isDefined(it) && Character.getType(it) != Character.SURROGATE.toInt() }

    private fun jdk(input: String) = Normalizer.normalize(input, Normalizer.Form.NFKC)

    private fun check(input: String) {
        val expected = jdk(input)
        val actual = NfkcNormalizer.normalize(input)
        if (expected != actual) {
            assertEquals(expected.hex(), actual.hex(), "input: ${input.hex()}")
        }
    }

    @Test
    fun everyCodePointAlone() {
        definedCodePoints.forEach { check(Character.toString(it)) }
    }

    @Test
    fun everyCodePointFollowedByCombiningMarks() {
        definedCodePoints.forEach {
            val cp = Character.toString(it)
            check(cp + "̣̂")
            check(cp + "̣̂")
            check(cp + "̈")
            check("a" + cp + "́")
        }
    }

    @Test
    fun randomSequencesOfInterestingCodePoints() {
        // Code points that change under NFKD or take part in reordering/composition.
        val interesting =
            definedCodePoints.filter {
                val s = Character.toString(it)
                Normalizer.normalize(s, Normalizer.Form.NFKD) != s || Character.getType(it) == Character.NON_SPACING_MARK.toInt()
            } + (0x1100..0x11FF) + listOf('a'.code, 'e'.code, 'A'.code, ' '.code)

        val random = Random(49)
        repeat(200_000) {
            val length = random.nextInt(1, 8)
            val sb = StringBuilder()
            repeat(length) { sb.appendCodePoint(interesting[random.nextInt(interesting.size)]) }
            check(sb.toString())
        }
    }

    private fun String.hex() = codePoints().toArray().joinToString(" ") { it.toString(16) }
}
