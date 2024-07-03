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

/** Makes the distinction between String and Hex * */
typealias HexKey = String

fun ByteArray.toHexKey(): HexKey = Hex.encode(this)

fun HexKey.hexToByteArray(): ByteArray = Hex.decode(this)

object HexValidator {
    private fun isHexChar(c: Char): Boolean =
        when (c) {
            in '0'..'9' -> true
            in 'a'..'f' -> true
            in 'A'..'F' -> true
            else -> false
        }

    fun isHex(hex: String?): Boolean {
        if (hex == null) return false
        if (hex.length % 2 != 0) return false // must be even
        var isHex = true

        for (c in hex) {
            if (!isHexChar(c)) {
                isHex = false
                break
            }
        }
        return isHex
    }
}

object Hex {
    val hexCode =
        arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    // Faster if no calculations are needed.
    private fun hexToBin(ch: Char): Int =
        when (ch) {
            in '0'..'9' -> ch - '0'
            in 'a'..'f' -> ch - 'a' + 10
            in 'A'..'F' -> ch - 'A' + 10
            else -> throw IllegalArgumentException("illegal hex character: $ch")
        }

    @JvmStatic
    fun decode(hex: String): ByteArray {
        // faster version of hex decoder
        require(hex.length % 2 == 0)
        val outSize = hex.length / 2
        val out = ByteArray(outSize)

        for (i in 0 until outSize) {
            out[i] = (hexToBin(hex[2 * i]) * 16 + hexToBin(hex[2 * i + 1])).toByte()
        }

        return out
    }

    @JvmStatic
    fun encode(input: ByteArray): String {
        val len = input.size
        val out = CharArray(len * 2)
        for (i in 0 until len) {
            out[i * 2] = hexCode[(input[i].toInt() shr 4) and 0xF]
            out[i * 2 + 1] = hexCode[input[i].toInt() and 0xF]
        }
        return String(out)
    }
}
