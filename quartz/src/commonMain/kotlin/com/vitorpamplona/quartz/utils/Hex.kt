/**
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

    // 47ns in debug on the Emulator
    fun isHex(hex: String?): Boolean {
        if (hex == null) return false
        if (hex.length and 1 != 0) return false

        return try {
            internalIsHex(hex, hexToByte)
        } catch (_: IllegalArgumentException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        } catch (_: IndexOutOfBoundsException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        }
    }

    // breaking this function away from the main one improves performance for some reason
    fun internalIsHex(
        hex: String,
        hexToByte: IntArray,
    ): Boolean {
        for (c in hex.indices) {
            if (hexToByte[hex[c].code] < 0) return false
        }
        return true
    }

    // 30% faster than isHex
    fun isHex64(hex: String): Boolean =
        try {
            hexToByte[hex[0].code] >= 0 &&
                hexToByte[hex[1].code] >= 0 &&
                hexToByte[hex[2].code] >= 0 &&
                hexToByte[hex[3].code] >= 0 &&
                hexToByte[hex[4].code] >= 0 &&
                hexToByte[hex[5].code] >= 0 &&
                hexToByte[hex[6].code] >= 0 &&
                hexToByte[hex[7].code] >= 0 &&
                hexToByte[hex[8].code] >= 0 &&
                hexToByte[hex[9].code] >= 0 &&

                hexToByte[hex[10].code] >= 0 &&
                hexToByte[hex[11].code] >= 0 &&
                hexToByte[hex[12].code] >= 0 &&
                hexToByte[hex[13].code] >= 0 &&
                hexToByte[hex[14].code] >= 0 &&
                hexToByte[hex[15].code] >= 0 &&
                hexToByte[hex[16].code] >= 0 &&
                hexToByte[hex[17].code] >= 0 &&
                hexToByte[hex[18].code] >= 0 &&
                hexToByte[hex[19].code] >= 0 &&

                hexToByte[hex[20].code] >= 0 &&
                hexToByte[hex[21].code] >= 0 &&
                hexToByte[hex[22].code] >= 0 &&
                hexToByte[hex[23].code] >= 0 &&
                hexToByte[hex[24].code] >= 0 &&
                hexToByte[hex[25].code] >= 0 &&
                hexToByte[hex[26].code] >= 0 &&
                hexToByte[hex[27].code] >= 0 &&
                hexToByte[hex[28].code] >= 0 &&
                hexToByte[hex[29].code] >= 0 &&

                hexToByte[hex[30].code] >= 0 &&
                hexToByte[hex[31].code] >= 0 &&
                hexToByte[hex[32].code] >= 0 &&
                hexToByte[hex[33].code] >= 0 &&
                hexToByte[hex[34].code] >= 0 &&
                hexToByte[hex[35].code] >= 0 &&
                hexToByte[hex[36].code] >= 0 &&
                hexToByte[hex[37].code] >= 0 &&
                hexToByte[hex[38].code] >= 0 &&
                hexToByte[hex[39].code] >= 0 &&

                hexToByte[hex[40].code] >= 0 &&
                hexToByte[hex[41].code] >= 0 &&
                hexToByte[hex[42].code] >= 0 &&
                hexToByte[hex[43].code] >= 0 &&
                hexToByte[hex[44].code] >= 0 &&
                hexToByte[hex[45].code] >= 0 &&
                hexToByte[hex[46].code] >= 0 &&
                hexToByte[hex[47].code] >= 0 &&
                hexToByte[hex[48].code] >= 0 &&
                hexToByte[hex[49].code] >= 0 &&

                hexToByte[hex[50].code] >= 0 &&
                hexToByte[hex[51].code] >= 0 &&
                hexToByte[hex[52].code] >= 0 &&
                hexToByte[hex[53].code] >= 0 &&
                hexToByte[hex[54].code] >= 0 &&
                hexToByte[hex[55].code] >= 0 &&
                hexToByte[hex[56].code] >= 0 &&
                hexToByte[hex[57].code] >= 0 &&
                hexToByte[hex[58].code] >= 0 &&
                hexToByte[hex[59].code] >= 0 &&

                hexToByte[hex[60].code] >= 0 &&
                hexToByte[hex[61].code] >= 0 &&
                hexToByte[hex[62].code] >= 0 &&
                hexToByte[hex[63].code] >= 0
        } catch (_: IllegalArgumentException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        } catch (_: IndexOutOfBoundsException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        }

    fun decode(hex: String): ByteArray {
        // faster version of hex decoder
        require(hex.length and 1 == 0)
        return ByteArray(hex.length / 2) {
            (hexToByte[hex[2 * it].code] shl 4 or hexToByte[hex[2 * it + 1].code]).toByte()
        }
    }

    fun encode(input: ByteArray): String {
        val out = CharArray(input.size * 2)
        var outIdx = 0
        for (i in 0 until input.size) {
            val chars = byteToHex[input[i].toInt() and 0xFF]
            out[outIdx++] = (chars shr 8).toChar()
            out[outIdx++] = (chars and 0xFF).toChar()
        }
        return out.concatToString()
    }

    fun isEqual(
        id: String,
        ourId: ByteArray,
    ): Boolean {
        var charIndex = 0
        for (i in 0 until ourId.size) {
            val chars = byteToHex[ourId[i].toInt() and 0xFF]
            if (
                id[charIndex++] != (chars shr 8).toChar() ||
                id[charIndex++] != (chars and 0xFF).toChar()
            ) {
                return false
            }
        }
        return true
    }
}
