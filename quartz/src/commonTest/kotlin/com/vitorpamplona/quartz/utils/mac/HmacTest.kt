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

import com.vitorpamplona.quartz.utils.sha2.Sha256Digest
import com.vitorpamplona.quartz.utils.sha2.Sha512Digest
import kotlin.test.Test
import kotlin.test.assertEquals

/** RFC 4231 vectors for the pure-Kotlin HMAC behind the native [MacInstance]. */
class HmacTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private class Case(
        val key: ByteArray,
        val message: ByteArray,
        val sha256: String,
        val sha512: String,
    )

    private val cases =
        listOf(
            // Test case 1
            Case(
                ByteArray(20) { 0x0b },
                "Hi There".encodeToByteArray(),
                "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
                "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde" +
                    "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854",
            ),
            // Test case 2: a key shorter than the output
            Case(
                "Jefe".encodeToByteArray(),
                "what do ya want for nothing?".encodeToByteArray(),
                "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea250554" +
                    "9758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
            ),
            // Test case 6: a key longer than the block, hashed first
            Case(
                ByteArray(131) { 0xaa.toByte() },
                "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray(),
                "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
                "80b24263c7c1a3ebb71493c1dd7be8b49b46d1f41b4aeec1121b013783f8f352" +
                    "6b56d037e05f2598bd0fd2215d6a1e5295e64f73f63f0aec8b915a985d786598",
            ),
        )

    @Test
    fun rfc4231() {
        for (case in cases) {
            assertEquals(case.sha256, Hmac(Sha256Digest(), case.key).apply { update(case.message) }.doFinal().hex())
            assertEquals(case.sha512, Hmac(Sha512Digest(), case.key).apply { update(case.message) }.doFinal().hex())
        }
    }

    @Test
    fun staysKeyedAfterDoFinal() {
        val case = cases[1]
        val mac = Hmac(Sha256Digest(), case.key)
        repeat(3) {
            mac.update(case.message)
            assertEquals(case.sha256, mac.doFinal().hex())
        }
    }
}
