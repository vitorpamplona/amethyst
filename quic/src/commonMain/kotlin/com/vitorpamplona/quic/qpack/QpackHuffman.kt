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
package com.vitorpamplona.quic.qpack

import com.vitorpamplona.quic.QuicCodecException

/**
 * HPACK / QPACK Huffman codec per RFC 7541 Appendix B.
 *
 * We implement decoding only — the encoder always emits Huffman=0 literals.
 * The codec is a static prefix code; we walk the input bit-by-bit through a
 * lazily-built lookup tree.
 */
object QpackHuffman {
    /** RFC 7541 Appendix B — (code, length) for each of the 256 symbols + EOS at index 256. */
    private val table: Array<IntArray> =
        arrayOf(
            intArrayOf(0x1ff8, 13),
            intArrayOf(0x7fffd8, 23),
            intArrayOf(0xfffffe2, 28),
            intArrayOf(0xfffffe3, 28),
            intArrayOf(0xfffffe4, 28),
            intArrayOf(0xfffffe5, 28),
            intArrayOf(0xfffffe6, 28),
            intArrayOf(0xfffffe7, 28),
            intArrayOf(0xfffffe8, 28),
            intArrayOf(0xffffea, 24),
            intArrayOf(0x3ffffffc, 30),
            intArrayOf(0xfffffe9, 28),
            intArrayOf(0xfffffea, 28),
            intArrayOf(0x3ffffffd, 30),
            intArrayOf(0xfffffeb, 28),
            intArrayOf(0xfffffec, 28),
            intArrayOf(0xfffffed, 28),
            intArrayOf(0xfffffee, 28),
            intArrayOf(0xfffffef, 28),
            intArrayOf(0xffffff0, 28),
            intArrayOf(0xffffff1, 28),
            intArrayOf(0xffffff2, 28),
            intArrayOf(0x3ffffffe, 30),
            intArrayOf(0xffffff3, 28),
            intArrayOf(0xffffff4, 28),
            intArrayOf(0xffffff5, 28),
            intArrayOf(0xffffff6, 28),
            intArrayOf(0xffffff7, 28),
            intArrayOf(0xffffff8, 28),
            intArrayOf(0xffffff9, 28),
            intArrayOf(0xffffffa, 28),
            intArrayOf(0xffffffb, 28),
            intArrayOf(0x14, 6),
            intArrayOf(0x3f8, 10),
            intArrayOf(0x3f9, 10),
            intArrayOf(0xffa, 12),
            intArrayOf(0x1ff9, 13),
            intArrayOf(0x15, 6),
            intArrayOf(0xf8, 8),
            intArrayOf(0x7fa, 11),
            intArrayOf(0x3fa, 10),
            intArrayOf(0x3fb, 10),
            intArrayOf(0xf9, 8),
            intArrayOf(0x7fb, 11),
            intArrayOf(0xfa, 8),
            intArrayOf(0x16, 6),
            intArrayOf(0x17, 6),
            intArrayOf(0x18, 6),
            intArrayOf(0x0, 5),
            intArrayOf(0x1, 5),
            intArrayOf(0x2, 5),
            intArrayOf(0x19, 6),
            intArrayOf(0x1a, 6),
            intArrayOf(0x1b, 6),
            intArrayOf(0x1c, 6),
            intArrayOf(0x1d, 6),
            intArrayOf(0x1e, 6),
            intArrayOf(0x1f, 6),
            intArrayOf(0x5c, 7),
            intArrayOf(0xfb, 8),
            intArrayOf(0x7ffc, 15),
            intArrayOf(0x20, 6),
            intArrayOf(0xffb, 12),
            intArrayOf(0x3fc, 10),
            intArrayOf(0x1ffa, 13),
            intArrayOf(0x21, 6),
            intArrayOf(0x5d, 7),
            intArrayOf(0x5e, 7),
            intArrayOf(0x5f, 7),
            intArrayOf(0x60, 7),
            intArrayOf(0x61, 7),
            intArrayOf(0x62, 7),
            intArrayOf(0x63, 7),
            intArrayOf(0x64, 7),
            intArrayOf(0x65, 7),
            intArrayOf(0x66, 7),
            intArrayOf(0x67, 7),
            intArrayOf(0x68, 7),
            intArrayOf(0x69, 7),
            intArrayOf(0x6a, 7),
            intArrayOf(0x6b, 7),
            intArrayOf(0x6c, 7),
            intArrayOf(0x6d, 7),
            intArrayOf(0x6e, 7),
            intArrayOf(0x6f, 7),
            intArrayOf(0x70, 7),
            intArrayOf(0x71, 7),
            intArrayOf(0x72, 7),
            intArrayOf(0xfc, 8),
            intArrayOf(0x73, 7),
            intArrayOf(0xfd, 8),
            intArrayOf(0x1ffb, 13),
            intArrayOf(0x7fff0, 19),
            intArrayOf(0x1ffc, 13),
            intArrayOf(0x3ffc, 14),
            intArrayOf(0x22, 6),
            intArrayOf(0x7ffd, 15),
            intArrayOf(0x3, 5),
            intArrayOf(0x23, 6),
            intArrayOf(0x4, 5),
            intArrayOf(0x24, 6),
            intArrayOf(0x5, 5),
            intArrayOf(0x25, 6),
            intArrayOf(0x26, 6),
            intArrayOf(0x27, 6),
            intArrayOf(0x6, 5),
            intArrayOf(0x74, 7),
            intArrayOf(0x75, 7),
            intArrayOf(0x28, 6),
            intArrayOf(0x29, 6),
            intArrayOf(0x2a, 6),
            intArrayOf(0x7, 5),
            intArrayOf(0x2b, 6),
            intArrayOf(0x76, 7),
            intArrayOf(0x2c, 6),
            intArrayOf(0x8, 5),
            intArrayOf(0x9, 5),
            intArrayOf(0x2d, 6),
            intArrayOf(0x77, 7),
            intArrayOf(0x78, 7),
            intArrayOf(0x79, 7),
            intArrayOf(0x7a, 7),
            intArrayOf(0x7b, 7),
            intArrayOf(0x7ffe, 15),
            intArrayOf(0x7fc, 11),
            intArrayOf(0x3ffd, 14),
            intArrayOf(0x1ffd, 13),
            intArrayOf(0xffffffc, 28),
            intArrayOf(0xfffe6, 20),
            intArrayOf(0x3fffd2, 22),
            intArrayOf(0xfffe7, 20),
            intArrayOf(0xfffe8, 20),
            intArrayOf(0x3fffd3, 22),
            intArrayOf(0x3fffd4, 22),
            intArrayOf(0x3fffd5, 22),
            intArrayOf(0x7fffd9, 23),
            intArrayOf(0x3fffd6, 22),
            intArrayOf(0x7fffda, 23),
            intArrayOf(0x7fffdb, 23),
            intArrayOf(0x7fffdc, 23),
            intArrayOf(0x7fffdd, 23),
            intArrayOf(0x7fffde, 23),
            intArrayOf(0xffffeb, 24),
            intArrayOf(0x7fffdf, 23),
            intArrayOf(0xffffec, 24),
            intArrayOf(0xffffed, 24),
            intArrayOf(0x3fffd7, 22),
            intArrayOf(0x7fffe0, 23),
            intArrayOf(0xffffee, 24),
            intArrayOf(0x7fffe1, 23),
            intArrayOf(0x7fffe2, 23),
            intArrayOf(0x7fffe3, 23),
            intArrayOf(0x7fffe4, 23),
            intArrayOf(0x1fffdc, 21),
            intArrayOf(0x3fffd8, 22),
            intArrayOf(0x7fffe5, 23),
            intArrayOf(0x3fffd9, 22),
            intArrayOf(0x7fffe6, 23),
            intArrayOf(0x7fffe7, 23),
            intArrayOf(0xffffef, 24),
            intArrayOf(0x3fffda, 22),
            intArrayOf(0x1fffdd, 21),
            intArrayOf(0xfffe9, 20),
            intArrayOf(0x3fffdb, 22),
            intArrayOf(0x3fffdc, 22),
            intArrayOf(0x7fffe8, 23),
            intArrayOf(0x7fffe9, 23),
            intArrayOf(0x1fffde, 21),
            intArrayOf(0x7fffea, 23),
            intArrayOf(0x3fffdd, 22),
            intArrayOf(0x3fffde, 22),
            intArrayOf(0xfffff0, 24),
            intArrayOf(0x1fffdf, 21),
            intArrayOf(0x3fffdf, 22),
            intArrayOf(0x7fffeb, 23),
            intArrayOf(0x7fffec, 23),
            intArrayOf(0x1fffe0, 21),
            intArrayOf(0x1fffe1, 21),
            intArrayOf(0x3fffe0, 22),
            intArrayOf(0x1fffe2, 21),
            intArrayOf(0x7fffed, 23),
            intArrayOf(0x3fffe1, 22),
            intArrayOf(0x7fffee, 23),
            intArrayOf(0x7fffef, 23),
            intArrayOf(0xfffea, 20),
            intArrayOf(0x3fffe2, 22),
            intArrayOf(0x3fffe3, 22),
            intArrayOf(0x3fffe4, 22),
            intArrayOf(0x7ffff0, 23),
            intArrayOf(0x3fffe5, 22),
            intArrayOf(0x3fffe6, 22),
            intArrayOf(0x7ffff1, 23),
            intArrayOf(0x3ffffe0, 26),
            intArrayOf(0x3ffffe1, 26),
            intArrayOf(0xfffeb, 20),
            intArrayOf(0x7fff1, 19),
            intArrayOf(0x3fffe7, 22),
            intArrayOf(0x7ffff2, 23),
            intArrayOf(0x3fffe8, 22),
            intArrayOf(0x1ffffec, 25),
            intArrayOf(0x3ffffe2, 26),
            intArrayOf(0x3ffffe3, 26),
            intArrayOf(0x3ffffe4, 26),
            intArrayOf(0x7ffffde, 27),
            intArrayOf(0x7ffffdf, 27),
            intArrayOf(0x3ffffe5, 26),
            intArrayOf(0xfffff1, 24),
            intArrayOf(0x1ffffed, 25),
            intArrayOf(0x7fff2, 19),
            intArrayOf(0x1fffe3, 21),
            intArrayOf(0x3ffffe6, 26),
            intArrayOf(0x7ffffe0, 27),
            intArrayOf(0x7ffffe1, 27),
            intArrayOf(0x3ffffe7, 26),
            intArrayOf(0x7ffffe2, 27),
            intArrayOf(0xfffff2, 24),
            intArrayOf(0x1fffe4, 21),
            intArrayOf(0x1fffe5, 21),
            intArrayOf(0x3ffffe8, 26),
            intArrayOf(0x3ffffe9, 26),
            intArrayOf(0xffffffd, 28),
            intArrayOf(0x7ffffe3, 27),
            intArrayOf(0x7ffffe4, 27),
            intArrayOf(0x7ffffe5, 27),
            intArrayOf(0xfffec, 20),
            intArrayOf(0xfffff3, 24),
            intArrayOf(0xfffed, 20),
            intArrayOf(0x1fffe6, 21),
            intArrayOf(0x3fffe9, 22),
            intArrayOf(0x1fffe7, 21),
            intArrayOf(0x1fffe8, 21),
            intArrayOf(0x7ffff3, 23),
            intArrayOf(0x3fffea, 22),
            intArrayOf(0x3fffeb, 22),
            intArrayOf(0x1ffffee, 25),
            intArrayOf(0x1ffffef, 25),
            intArrayOf(0xfffff4, 24),
            intArrayOf(0xfffff5, 24),
            intArrayOf(0x3ffffea, 26),
            intArrayOf(0x7ffff4, 23),
            intArrayOf(0x3ffffeb, 26),
            intArrayOf(0x7ffffe6, 27),
            intArrayOf(0x3ffffec, 26),
            intArrayOf(0x3ffffed, 26),
            intArrayOf(0x7ffffe7, 27),
            intArrayOf(0x7ffffe8, 27),
            intArrayOf(0x7ffffe9, 27),
            intArrayOf(0x7ffffea, 27),
            intArrayOf(0x7ffffeb, 27),
            intArrayOf(0xffffffe, 28),
            intArrayOf(0x7ffffec, 27),
            intArrayOf(0x7ffffed, 27),
            intArrayOf(0x7ffffee, 27),
            intArrayOf(0x7ffffef, 27),
            intArrayOf(0x7fffff0, 27),
            intArrayOf(0x3ffffee, 26),
            intArrayOf(0x3fffffff, 30),
        )

    /**
     * Lookup tables grouped by code length. `byLength[L]` is a HashMap from
     * `code` (an Int up to 30 bits) to `symbol` (0..255) for all symbols
     * whose Huffman code is exactly L bits long. Lengths used in the table
     * range from 5 to 30. We omit the EOS (length 30, code 0x3FFFFFFF) since
     * it must never appear in valid input.
     */
    private val byLength: Array<HashMap<Int, Int>> = buildLookupByLength()

    /** Sorted ascending list of distinct code lengths actually used by the table. */
    private val lengths: IntArray = byLength.indices.filter { byLength[it].isNotEmpty() }.toIntArray()

    private fun buildLookupByLength(): Array<HashMap<Int, Int>> {
        val out = Array(31) { HashMap<Int, Int>() }
        for (sym in 0..255) {
            val code = table[sym][0]
            val len = table[sym][1]
            out[len][code] = sym
        }
        return out
    }

    /** Decode a Huffman-encoded byte sequence into a UTF-8 string. */
    fun decode(encoded: ByteArray): ByteArray {
        val result = ArrayList<Byte>(encoded.size * 2) // rough upper bound
        var bitBuf = 0L
        var bitsAvailable = 0
        var i = 0
        while (i < encoded.size || bitsAvailable >= 5) {
            // Pull more bits.
            while (bitsAvailable < 32 && i < encoded.size) {
                bitBuf = (bitBuf shl 8) or (encoded[i].toLong() and 0xFF)
                bitsAvailable += 8
                i++
            }
            // Try matching at each used code length, ascending. The first
            // match wins (Huffman is a prefix code, so this is unambiguous).
            var matched = false
            for (len in lengths) {
                if (len > bitsAvailable) break
                val candidate = ((bitBuf ushr (bitsAvailable - len)) and ((1L shl len) - 1)).toInt()
                val sym = byLength[len][candidate]
                if (sym != null) {
                    result.add(sym.toByte())
                    bitsAvailable -= len
                    bitBuf = bitBuf and ((1L shl bitsAvailable) - 1)
                    matched = true
                    break
                }
            }
            if (!matched) {
                // Either trailing padding (all 1s up to 7 bits) or error.
                if (i >= encoded.size) {
                    if (bitsAvailable in 1..7) {
                        val pad = (bitBuf and ((1L shl bitsAvailable) - 1))
                        if (pad == ((1L shl bitsAvailable) - 1)) break
                    }
                    if (bitsAvailable == 0) break
                }
                throw QuicCodecException("invalid Huffman bit stream")
            }
        }
        return result.toByteArray()
    }
}
