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
package com.vitorpamplona.fantasyconsole

import com.vitorpamplona.fantasyconsole.cart.CartLoader
import com.vitorpamplona.fantasyconsole.cart.CodeCompression
import com.vitorpamplona.fantasyconsole.cart.P8scii
import com.vitorpamplona.fantasyconsole.runtime.ConsoleHost
import com.vitorpamplona.fantasyconsole.runtime.FantasyConsole
import kotlin.test.Test
import kotlin.test.assertEquals

class CartLoaderTest {
    private val p8 =
        """
        pico-8 cartridge // http://www.pico-8.com
        version 42
        __lua__
        -- glyphs read as button numbers
        printh(⬅️ .. ➡️ .. ❎)
        printh(sget(0, 0) .. sget(1, 0) .. sget(2, 0))
        printh(fget(1) .. " " .. mget(1, 0))
        printh(peek(0x3200 + 65) .. " " .. peek2(0x3200) .. " " .. peek(0x3100))
        __gfx__
        01230000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
        __gff__
        0003
        __map__
        0102
        __sfx__
        000a00001d0501d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d0001d000
        __music__
        01 41424344
        __meta:title__
        not code
        """.trimIndent()

    @Test
    fun readsTextCarts() {
        val cart = CartLoader.load(p8.encodeToByteArray())
        val log = ArrayList<String>()
        FantasyConsole(
            cart,
            object : ConsoleHost {
                override fun log(message: String) {
                    log.add(message)
                }
            },
        ).step()
        // sfx note 0: pitch 0x1d, waveform 0, volume 5, effect 0 → 0x1d | 5 << 9.
        assertEquals(listOf("015", "012", "3 2", "10 2589 193"), log)
    }

    @Test
    fun decodesOldCompression() {
        // "ab", then a copy of the last 2 characters, then a newline.
        val rom = ByteArray(0x8000)
        val bytes = byteArrayOf(':'.code.toByte(), 'c'.code.toByte(), ':'.code.toByte(), 0, 0, 5, 0, 0, 13, 14, 0x3c, 0x02, 1)
        bytes.copyInto(rom, 0x4300)
        assertEquals("abab\n", CodeCompression.decode(rom, 0x4300))
    }

    @Test
    fun decodesPxaCompression() {
        val bits = BitWriter()
        // Literal 'a': move-to-front index 97 = 48 + 49, written as unary 2 then 6 bits.
        bits.write(1, 1)
        bits.write(1, 1)
        bits.write(1, 1)
        bits.write(0, 1)
        bits.write(49, 6)
        // Literal 'b': index 98 (only 'a' moved ahead of it) = 48 + 50.
        bits.write(1, 1)
        bits.write(1, 1)
        bits.write(1, 1)
        bits.write(0, 1)
        bits.write(50, 6)
        // Copy 3 characters from 2 back, with a 5-bit offset.
        bits.write(0, 1)
        bits.write(1, 1)
        bits.write(1, 1)
        bits.write(1, 5)
        bits.write(0, 3)
        val body = bits.bytes()
        val header = byteArrayOf(0, 'p'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 0, 5, 0, (8 + body.size).toByte())
        val rom = ByteArray(0x8000)
        (header + body).copyInto(rom, 0x4300)
        assertEquals("ababa", CodeCompression.decode(rom, 0x4300))
    }

    @Test
    fun p8sciiRoundTrips() {
        val all = (0 until 256).map { it.toChar() }.joinToString("")
        val text = P8scii.toUnicode(all)
        assertEquals(all.substring(1), P8scii.fromUnicode(text).substring(1))
        assertEquals("⬅️", P8scii.toUnicode(139.toChar().toString()))
        assertEquals(255.toChar().toString(), P8scii.fromUnicode("◝"))
    }

    private class BitWriter {
        private val out = ArrayList<Byte>()
        private var current = 0
        private var count = 0

        fun write(
            value: Int,
            n: Int,
        ) {
            for (k in 0 until n) {
                current = current or (((value shr k) and 1) shl count)
                if (++count == 8) {
                    out.add(current.toByte())
                    current = 0
                    count = 0
                }
            }
        }

        fun bytes(): ByteArray = (if (count > 0) out + current.toByte() else out).toByteArray()
    }
}
