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
package com.vitorpamplona.quartz.utils.sha256

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Known-answer vectors (FIPS 180-2) for every platform's sha256 actual. The empty
 * input case guards the Apple actual, which used to pin `addressOf(0)` on a
 * zero-length array and throw ArrayIndexOutOfBoundsException.
 */
class Sha256Test {
    private val emptyDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val abcDigest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun hashesEmptyInput() {
        assertEquals(emptyDigest, sha256(ByteArray(0)).hex())
    }

    @Test
    fun hashesAbc() {
        assertEquals(abcDigest, sha256("abc".encodeToByteArray()).hex())
    }

    @Test
    fun sha256IntoHashesEmptyInput() {
        assertEquals(emptyDigest, sha256Into(ByteArray(32), ByteArray(0)).hex())
    }

    @Test
    fun sha256IntoHashesOnlyThePrefix() {
        val out = ByteArray(32)
        assertEquals(abcDigest, sha256Into(out, "abcdef".encodeToByteArray(), 3).hex())
        assertEquals(emptyDigest, sha256Into(out, "abcdef".encodeToByteArray(), 0).hex())
    }
}
