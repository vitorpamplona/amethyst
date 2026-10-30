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
package com.vitorpamplona.fantasyconsole.cart

/**
 * The two ways a cart's ROM stores its code at 0x4300 (up to 0x8000): plain text ending at a
 * zero byte, the old `:c:\0` format (a 59-character table plus back-references), and the newer
 * `\0pxa` format (move-to-front literals and back-references over a bit stream).
 */
object CodeCompression {
    private const val OLD_TABLE = "\n 0123456789abcdefghijklmnopqrstuvwxyz!#%(){}[]<>+=/*:;.,~_"

    class Corrupt(
        message: String,
    ) : Exception(message)

    /** The code in [rom] from [start] to its end, decompressed as needed, as P8SCII. */
    fun decode(
        rom: ByteArray,
        start: Int,
        end: Int = rom.size,
    ): String {
        fun at(i: Int) = if (i < end) rom[i].toInt() and 0xff else 0
        return when {
            at(start) == ':'.code && at(start + 1) == 'c'.code && at(start + 2) == ':'.code && at(start + 3) == 0 ->
                decodeOld(rom, start, end)
            at(start) == 0 && at(start + 1) == 'p'.code && at(start + 2) == 'x'.code && at(start + 3) == 'a'.code ->
                decodePxa(rom, start, end)
            else -> {
                var stop = start
                while (stop < end && rom[stop].toInt() != 0) stop++
                P8scii.fromBytes(rom, start, stop)
            }
        }
    }

    private fun decodeOld(
        rom: ByteArray,
        start: Int,
        end: Int,
    ): String {
        fun at(i: Int): Int {
            if (i >= end) throw Corrupt("code runs past the end of the cart")
            return rom[i].toInt() and 0xff
        }
        val length = (at(start + 4) shl 8) or at(start + 5)
        val out = StringBuilder(length)
        var i = start + 8
        while (out.length < length) {
            val b = at(i++)
            when {
                b == 0 -> out.append(at(i++).toChar())
                b <= 0x3b -> out.append(OLD_TABLE[b - 1])
                else -> {
                    val next = at(i++)
                    val offset = (b - 0x3c) * 16 + (next and 0xf)
                    val count = (next shr 4) + 2
                    val from = out.length - offset
                    if (offset <= 0 || from < 0) throw Corrupt("bad back-reference")
                    for (k in 0 until count) out.append(out[from + k])
                }
            }
        }
        return out.toString()
    }

    private fun decodePxa(
        rom: ByteArray,
        start: Int,
        end: Int,
    ): String {
        val length = ((rom[start + 4].toInt() and 0xff) shl 8) or (rom[start + 5].toInt() and 0xff)
        var bitPos = (start + 8) * 8
        val limit = end * 8

        fun bit(): Int {
            if (bitPos >= limit) throw Corrupt("code runs past the end of the cart")
            val b = (rom[bitPos shr 3].toInt() shr (bitPos and 7)) and 1
            bitPos++
            return b
        }

        fun bits(n: Int): Int {
            var v = 0
            for (k in 0 until n) v = v or (bit() shl k)
            return v
        }

        val mtf = IntArray(256) { it }
        val out = StringBuilder(length)
        while (out.length < length) {
            if (bit() == 1) {
                var unary = 0
                while (bit() == 1) unary++
                val index = bits(4 + unary) + (((1 shl unary) - 1) shl 4)
                if (index > 255) throw Corrupt("bad literal index")
                val c = mtf[index]
                for (k in index downTo 1) mtf[k] = mtf[k - 1]
                mtf[0] = c
                out.append(c.toChar())
            } else {
                val offsetBits = if (bit() == 1) (if (bit() == 1) 5 else 10) else 15
                val offset = bits(offsetBits) + 1
                if (offsetBits == 10 && offset == 1) {
                    // A run of raw bytes, ended by a zero.
                    while (true) {
                        val c = bits(8)
                        if (c == 0) break
                        out.append(c.toChar())
                    }
                } else {
                    var count = 3
                    while (true) {
                        val part = bits(3)
                        count += part
                        if (part != 7) break
                    }
                    val from = out.length - offset
                    if (from < 0) throw Corrupt("bad back-reference")
                    for (k in 0 until count) out.append(out[from + k])
                }
            }
        }
        return out.toString()
    }
}
