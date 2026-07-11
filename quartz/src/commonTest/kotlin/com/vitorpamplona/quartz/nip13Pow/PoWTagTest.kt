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
package com.vitorpamplona.quartz.nip13Pow

import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoWTagTest {
    @Test
    fun assembleMatchesNip13Example() {
        // NIP-13's example: ["nonce", "776797", "20"]
        assertContentEquals(arrayOf("nonce", "776797", "20"), PoWTag.assemble("776797", 20))
    }

    @Test
    fun assembleWithoutCommitmentOmitsTheThirdEntry() {
        // a null commitment must not serialize as the literal string "null"
        assertContentEquals(arrayOf("nonce", "776797"), PoWTag.assemble("776797", null))
    }

    @Test
    fun parseRoundTrips() {
        val parsed = PoWTag.parse(arrayOf("nonce", "776797", "20"))!!
        assertEquals("776797", parsed.nonce)
        assertEquals(20, parsed.commitment)
        assertContentEquals(arrayOf("nonce", "776797", "20"), parsed.toTagArray())

        val noCommitment = PoWTag.parse(arrayOf("nonce", "776797"))!!
        assertNull(noCommitment.commitment)
        assertContentEquals(arrayOf("nonce", "776797"), noCommitment.toTagArray())
    }
}
