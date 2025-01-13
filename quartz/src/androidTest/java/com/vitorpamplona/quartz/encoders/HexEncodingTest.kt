/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.encoders

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HexEncodingTest {
    val testHex = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"

    @Test
    fun testHexEncodeDecodeOurs() {
        assertEquals(
            testHex,
            Hex.encode(
                Hex.decode(testHex),
            ),
        )
    }

    @Test
    fun testHexEncodeDecodeSecp256k1() {
        assertEquals(
            testHex,
            fr.acinq.secp256k1.Hex.encode(
                fr.acinq.secp256k1.Hex
                    .decode(testHex),
            ),
        )
    }

    @Test
    fun testRandoms() {
        for (i in 0..1000) {
            val bytes = CryptoUtils.privkeyCreate()
            val hex =
                fr.acinq.secp256k1.Hex
                    .encode(bytes)
            assertEquals(
                fr.acinq.secp256k1.Hex
                    .encode(bytes),
                Hex.encode(bytes),
            )
            assertEquals(
                bytes.toList(),
                Hex.decode(hex).toList(),
            )
        }
    }

    @Test
    fun testIsHex() {
        assertFalse("/0", Hex.isHex("/0"))
        assertFalse("/.", Hex.isHex("/."))
        assertFalse("::", Hex.isHex("::"))
        assertFalse("!!", Hex.isHex("!!"))
        assertFalse("@@", Hex.isHex("@@"))
        assertFalse("GG", Hex.isHex("GG"))
        assertFalse("FG", Hex.isHex("FG"))
        assertFalse("`a", Hex.isHex("`a"))
        assertFalse("gg", Hex.isHex("gg"))
        assertFalse("fg", Hex.isHex("fg"))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testRandomsIsHex() {
        for (i in 0..10000) {
            val bytes = CryptoUtils.privkeyCreate()
            val hex = bytes.toHexString(HexFormat.Default)
            assertTrue(hex, Hex.isHex(hex))
            val hexUpper = bytes.toHexString(HexFormat.UpperCase)
            assertTrue(hexUpper, Hex.isHex(hexUpper))
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testRandomsUppercase() {
        for (i in 0..1000) {
            val bytes = CryptoUtils.privkeyCreate()
            val hex = bytes.toHexString(HexFormat.UpperCase)
            assertEquals(
                bytes.toList(),
                Hex.decode(hex).toList(),
            )
        }
    }
}
