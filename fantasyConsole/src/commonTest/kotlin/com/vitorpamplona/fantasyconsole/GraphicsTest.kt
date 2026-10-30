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

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphicsTest {
    @Test
    fun primitivesWriteTheScreen() {
        val out =
            run(
                """
                cls(1)
                rectfill(10, 10, 12, 12, 8)
                printh(pget(11, 11) .. pget(13, 13))
                circfill(64, 64, 3, 9)
                printh(pget(64, 61) .. " " .. pget(64, 60))
                line(0, 0, 5, 0, 7)
                printh(pget(5, 0) .. " " .. pget(6, 0))
                pset(100, 100)
                printh(pget(100, 100))
                """.trimIndent(),
            )
        // pset with no color uses the pen, which the last call with a color set to 7.
        assertEquals(listOf("81", "9 1", "7 1", "7"), out)
    }

    @Test
    fun cameraClipAndPalette() {
        val out =
            run(
                """
                cls()
                camera(10, 0)
                pset(10, 0, 8)
                camera()
                printh(pget(0, 0))
                clip(0, 0, 4, 4)
                rectfill(0, 0, 10, 10, 9)
                clip()
                printh(pget(3, 3) .. pget(4, 4))
                pal(7, 12)
                pset(20, 20, 7)
                printh(pget(20, 20))
                pal()
                fillp(0b1000000000000000.1)
                rectfill(32, 32, 35, 35, 11)
                fillp()
                printh(pget(32, 32) .. pget(33, 32))
                """.trimIndent(),
            )
        assertEquals(listOf("8", "90", "12", "011"), out)
    }

    @Test
    fun spritesHonorTransparencyAndFlip() {
        val out =
            run(
                """
                cls(5)
                sset(0, 0, 0) sset(1, 0, 8)
                spr(0, 40, 40)
                printh(pget(40, 40) .. " " .. pget(41, 40))
                spr(0, 50, 50, 1, 1, true)
                printh(pget(57, 50) .. " " .. pget(56, 50))
                palt(0, false)
                spr(0, 60, 60)
                printh(pget(60, 60))
                """.trimIndent(),
            )
        assertEquals(listOf("5 8", "5 8", "0"), out)
    }

    @Test
    fun printReturnsTheRightEdgeAndMovesTheCursor() {
        val out =
            run(
                """
                cls()
                printh(print("abc", 10, 20, 7))
                cursor(0, 0)
                print("x")
                print("y")
                printh(peek(0x5f27))
                """.trimIndent(),
            )
        assertEquals(listOf("22", "12"), out)
    }

    @Test
    fun rendersThroughTheScreenPalette() {
        val t = TestConsole("cls(8) pal(8, 129, 1)").frames(1)
        val pixels = IntArray(128 * 128)
        t.console.render(pixels)
        assertEquals(0xff111D35.toInt(), pixels[0])
    }
}
