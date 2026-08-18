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
package com.vitorpamplona.quartz.nip19Bech32.bech32

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Bech32.isDataChar] is the membership test the NIP-19 content scan uses to find where an encoded
 * payload ends, so it has to agree with [Bech32.ALPHABET] exactly — a char wrongly accepted extends
 * a payload past its real end and silently drops the entity when the decode then fails.
 */
class Bech32DataCharTest {
    @Test
    fun acceptsExactlyTheAlphabetInBothCases() {
        for (c in Bech32.ALPHABET) assertTrue(Bech32.isDataChar(c), "expected '$c' to be a data char")
        for (c in Bech32.ALPHABET_UPPERCASE) assertTrue(Bech32.isDataChar(c), "expected '$c' to be a data char")
    }

    @Test
    fun rejectsTheAmbiguousFour() {
        // BIP-173 leaves these out of the alphabet precisely because they are easy to misread.
        for (c in "1bio1BIO") assertFalse(Bech32.isDataChar(c), "expected '$c' to be rejected")
    }

    @Test
    fun agreesWithTheAlphabetAcrossEveryChar() {
        // Sweeps the whole BMP so nothing outside the alphabet sneaks in — including the
        // out-of-range guard for chars beyond the lookup table.
        val expected = (Bech32.ALPHABET + Bech32.ALPHABET_UPPERCASE).toSet()
        for (code in 0..0xFFFF) {
            val c = code.toChar()
            assertEquals(c in expected, Bech32.isDataChar(c), "disagreement at code $code")
        }
    }

    @Test
    fun countsMatchTheSpec() {
        assertEquals(32, Bech32.ALPHABET.length)
        // 32 symbols, but only the 23 letters have a distinct uppercase form — the 9 digits
        // are the same char in both alphabets, so the accepted set is 23*2 + 9, not 64.
        val letters = Bech32.ALPHABET.count { it.isLetter() }
        val digits = Bech32.ALPHABET.count { it.isDigit() }
        assertEquals(32, letters + digits)
        assertEquals(letters * 2 + digits, (0..0xFFFF).count { Bech32.isDataChar(it.toChar()) })
    }
}
