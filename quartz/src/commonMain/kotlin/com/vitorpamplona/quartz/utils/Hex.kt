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
package com.vitorpamplona.quartz.utils

/**
 * Fast, allocation-conscious hex codec used throughout Quartz for keys, event
 * ids and signatures. Backed by pre-computed lookup tables and benchmarked
 * against the secp256k1 codec and Kotlin's stdlib `HexFormat` (see
 * `benchmark/.../HexBenchmark.kt`).
 *
 * Most call sites should prefer the extension functions in
 * [com.vitorpamplona.quartz.nip01Core.core] — `ByteArray.toHexKey()` and
 * `HexKey.hexToByteArray()` — which delegate here. Reach for this object
 * directly when you want to validate without decoding ([isHex] / [isHex64]) or
 * compare a hex string to raw bytes without allocating ([isEqual]).
 *
 * ```kotlin
 * val hex = Hex.encode(bytes) // ByteArray -> lower-case hex
 * val bytes = Hex.decode(hex) // hex (any case) -> ByteArray
 * if (Hex.isHex64(id)) { ... } // is this a valid 32-byte hex id?
 * ```
 */
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

    /**
     * True when [hex] is a non-null, even-length string of only hex digits
     * (upper or lower case). Rejects odd lengths and stray non-hex chars (e.g.
     * emoji in `p` tags) instead of throwing. ~47ns in debug on the Emulator;
     * use [isHex64] when the length is known to be 64.
     */
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

    /**
     * Validates the first 64 chars of [hex] as hex digits — the fast path for
     * checking a 32-byte pubkey or event id. ~30% faster than [isHex] because
     * the length is fixed and the checks are unrolled. Assumes [hex] is at least
     * 64 chars long; it does not verify the total length.
     */
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

    /**
     * Decodes [hex] (upper or lower case) into bytes. Requires an even length —
     * throws [IllegalArgumentException] otherwise. Does not itself validate the
     * characters, so guard untrusted input with [isHex] first (or use
     * `HexKey.hexToByteArrayOrNull()`).
     */
    fun decode(hex: String): ByteArray {
        // faster version of hex decoder
        require(hex.length and 1 == 0) {
            "Invalid hex $hex"
        }
        return ByteArray(hex.length / 2) {
            (hexToByte[hex[2 * it].code] shl 4 or hexToByte[hex[2 * it + 1].code]).toByte()
        }
    }

    /** Encodes [input] as a lower-case hex string (two chars per byte). */
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

    /**
     * True when the hex string [id] encodes exactly the bytes [ourId], compared
     * without allocating a decode buffer. Handy for matching an incoming hex id
     * against bytes you already hold. Assumes [id] is at least `2 * ourId.size`
     * chars and lower-case (as produced by [encode]).
     */
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
