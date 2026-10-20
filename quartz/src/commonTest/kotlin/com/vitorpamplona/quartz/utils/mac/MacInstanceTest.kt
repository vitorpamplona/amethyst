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
package com.vitorpamplona.quartz.utils.mac

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [MacInstance] on every platform (JCA, CommonCrypto, pure Kotlin) with RFC 4231
 * vectors: reuse after [MacInstance.doFinal], and rekeying with [MacInstance.init] the
 * way HKDF does, with the same algorithm.
 */
class MacInstanceTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private val key1 = ByteArray(20) { 0x0b }
    private val message1 = "Hi There".encodeToByteArray()
    private val key2 = "Jefe".encodeToByteArray()
    private val message2 = "what do ya want for nothing?".encodeToByteArray()

    private val sha256Case1 = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"
    private val sha256Case2 = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
    private val sha512Case2 =
        "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea250554" +
            "9758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737"

    @Test
    fun reusesAndRekeys() {
        val mac = MacInstance("HmacSHA256", key1)
        assertEquals(32, mac.getMacLength())
        repeat(2) {
            mac.update(message1)
            assertEquals(sha256Case1, mac.doFinal().hex())
        }

        mac.init(key2, "HmacSHA256")
        message2.forEach { mac.update(it) }
        assertEquals(sha256Case2, mac.doFinal().hex())
    }

    @Test
    fun writesAtOffset() {
        val mac = MacInstance("HmacSHA512", key2)
        assertEquals(64, mac.getMacLength())
        mac.update(message2)
        val out = ByteArray(70)
        mac.doFinal(out, 3)
        assertEquals(sha512Case2, out.copyOfRange(3, 67).hex())
    }
}
