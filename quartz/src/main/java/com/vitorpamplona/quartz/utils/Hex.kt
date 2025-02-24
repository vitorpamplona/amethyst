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
package com.vitorpamplona.quartz.utils

object Hex {
    private const val LOWER_CASE_HEX = "0123456789abcdef"
    private const val UPPER_CASE_HEX = "0123456789ABCDEF"

    private val hexToByte: IntArray =
        IntArray(256) { -1 }.apply {
            LOWER_CASE_HEX.forEachIndexed { index, char -> this[char.code] = index }
            UPPER_CASE_HEX.forEachIndexed { index, char -> this[char.code] = index }
        }

    // Encodes both chars in a single Int variable
    private val byteToHex =
        IntArray(256) {
            (LOWER_CASE_HEX[(it shr 4)].code shl 8) or LOWER_CASE_HEX[(it and 0xF)].code
        }

    @JvmStatic
    fun isHex(hex: String?): Boolean {
        if (hex.isNullOrEmpty()) return false
        if (hex.length and 1 != 0) return false

        try {
            for (c in hex.indices) {
                if (hexToByte[hex[c].code] < 0) return false
            }
        } catch (e: IllegalArgumentException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            return false
        }

        return true
    }

    @JvmStatic
    fun decode(hex: String): ByteArray {
        // faster version of hex decoder
        require(hex.length and 1 == 0)
        return ByteArray(hex.length / 2) {
            (hexToByte[hex[2 * it].code] shl 4 or hexToByte[hex[2 * it + 1].code]).toByte()
        }
    }

    @JvmStatic
    fun encode(input: ByteArray): String {
        val out = CharArray(input.size * 2)
        var outIdx = 0
        for (i in 0 until input.size) {
            val chars = byteToHex[input[i].toInt() and 0xFF]
            out[outIdx++] = (chars shr 8).toChar()
            out[outIdx++] = (chars and 0xFF).toChar()
        }
        return String(out)
    }
}
