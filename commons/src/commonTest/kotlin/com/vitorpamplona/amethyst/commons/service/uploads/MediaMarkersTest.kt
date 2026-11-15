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
package com.vitorpamplona.amethyst.commons.service.uploads

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaMarkersTest {
    @Test
    fun idsComeInReadingOrder() {
        assertEquals(listOf(7, 2), MediaMarkers.idsIn("a\n[[media:7]]\nb [[media:2]]"))
        assertEquals(emptyList(), MediaMarkers.idsIn("no markers [[media:x]]"))
    }

    @Test
    fun replaceSwapsMarkersForUrls() {
        val text = "Look\n[[media:1]]\nand\n[[media:2]]\n"
        assertEquals("Look\nhttps://a.jpg\nand\nhttps://b.jpg\n", MediaMarkers.replace(text) { "https://${if (it == 1) "a" else "b"}.jpg" })
    }

    @Test
    fun stripLeavesNoEmptyLines() {
        assertEquals("Look\nand\n", MediaMarkers.strip("Look\n[[media:1]]\nand\n[[media:2]]\n"))
        assertEquals("only text", MediaMarkers.strip("only text"))
    }

    @Test
    fun aMissingFileDropsItsMarkerAndKeepsTheOthers() {
        assertEquals("x\nhttps://b\n", MediaMarkers.replace("x\n[[media:1]]\n[[media:2]]\n") { if (it == 2) "https://b" else null })
    }

    @Test
    fun insertionPutsTheMarkerOnItsOwnLine() {
        assertEquals("[[media:1]]\n", MediaMarkers.insertion("", 0, 1))
        assertEquals("\n[[media:1]]\n", MediaMarkers.insertion("Hello", 5, 1))
        assertEquals("[[media:1]]", MediaMarkers.insertion("Hello\n\nmore", 6, 1))
    }
}
