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
package com.vitorpamplona.quartz.experimental.ps1saves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Ps1SaveIconTest {
    // First 512 bytes (title frame + 3 icon frames) of the "SPYRO THE DRAGON"
    // save block seen in the wild
    // (event 178a9a139155f6e3aa799df059149facc635c3c3ae0495e9a3cdc600006b4c43
    // on relay.cyberguy.fyi). Magic "SC", display flag 0x13 = 3 frames.
    private val spyroBlock =
        "534313018272826f82788271826e814082738267826481408263827182608266" +
            "826e826d00000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000bc142e04df393f25590833045f4a00000000000000000000000000004954" +
            "0000006651000000001011141111000010414411111111004444141111111155" +
            "3333545555555555333353555555552530335355555555054033535555555506" +
            "1041135555556506005551666666660000505522222222000000552622220200" +
            "0000502522220000000000662202000000000060260000000000000002000000" +
            "0000007033000000000077773313000070777737333301007377773333334355" +
            "3373374444444444333313111111111130331311111111014033131111111106" +
            "1041131111111106005551666666260000505522222222000000552622220200" +
            "0000502522220000000000662202000000000060260000000000000002000000" +
            "0000001115000000001011441111000000414414111101004444141111115155" +
            "4144444144444444414474777777777741447477777777331044347777777707" +
            "5015347777777707005545334344140000505544444411000000554144440100" +
            "0000504544440000000000114404000000000060140000000000000006000000"

    @Test
    fun parsesThreeAnimationFramesFromDisplayFlag() {
        val icon = Ps1SaveIcon.parse(spyroBlock)
        assertNotNull(icon)
        assertEquals(3, icon.frames.size)
        assertEquals(Ps1SaveIcon.PIXELS, icon.frames[0].size)
    }

    @Test
    fun decodesPaletteIndexedPixelsToArgb() {
        val icon = Ps1SaveIcon.parse(spyroBlock)
        assertNotNull(icon)

        val frame = icon.frames[0]
        // Palette index 0 (raw 0x0000) is transparent.
        assertEquals(0, frame[0])
        // Pixel (6,0) uses palette entry 6 (raw 0x0433 -> RGB555 R=19 G=1 B=1).
        assertEquals(0xFF9C0808.toInt(), frame[6])
        // Pixel (8,0) uses palette entry 1 (raw 0x14bc -> RGB555 R=28 G=5 B=5).
        assertEquals(0xFFE72929.toInt(), frame[8])
        // Pixel (9,0) uses palette entry 5 (raw 0x0859 -> RGB555 R=25 G=2 B=2).
        assertEquals(0xFFCE1010.toInt(), frame[9])
    }

    @Test
    fun animationFramesDiffer() {
        val icon = Ps1SaveIcon.parse(spyroBlock)
        assertNotNull(icon)

        assertFalse(icon.frames[0].contentEquals(icon.frames[1]))
    }

    @Test
    fun parseIsNullWithoutTheScMagic() {
        assertNull(Ps1SaveIcon.parse("4d43" + spyroBlock.drop(4)))
    }

    @Test
    fun parseIsNullForAnUnknownDisplayFlag() {
        assertNull(Ps1SaveIcon.parse(spyroBlock.take(4) + "00" + spyroBlock.drop(6)))
    }

    @Test
    fun parseIsNullWhenTheBlockIsTooShortForItsFrames() {
        // Valid header claiming 3 frames but content ends before the frame data.
        assertNull(Ps1SaveIcon.parse(spyroBlock.take(300)))
    }

    @Test
    fun parseIsNullOnNonHexContent() {
        assertNull(Ps1SaveIcon.parse("not hex at all"))
        assertNull(Ps1SaveIcon.parse(""))
    }
}
