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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** A cart written by the JDK's own PNG encoder must read back bit for bit. */
class PngCartTest {
    @Test
    fun readsTheRomHiddenInAPng() {
        val rom = ByteArray(0x8000)
        Random(7).nextBytes(rom, 0, 0x4300)
        val code = "printh('hi')"
        code.encodeToByteArray().copyInto(rom, 0x4300)

        // Each byte goes in the two low bits of A, R, G and B over an arbitrary picture.
        val image = BufferedImage(160, 205, BufferedImage.TYPE_INT_ARGB)
        val noise = Random(11)
        for (i in 0 until 160 * 205) {
            val b = if (i < rom.size) rom[i].toInt() and 0xff else 0
            val base = noise.nextInt() and 0xfcfcfcfc.toInt()
            val pixel = base or ((b shr 6 and 3) shl 24) or ((b shr 4 and 3) shl 16) or ((b shr 2 and 3) shl 8) or (b and 3)
            image.setRGB(i % 160, i / 160, pixel)
        }
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

        val cart = CartLoader.load(png)
        assertContentEquals(rom.copyOf(0x4300), cart.rom.copyOf(0x4300))
        assertEquals(code, cart.code)
    }
}
