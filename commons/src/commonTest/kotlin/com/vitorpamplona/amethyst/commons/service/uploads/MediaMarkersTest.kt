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
    private fun m(id: Int) = MediaMarkers.of(id)

    @Test
    fun textAPersonTypesIsNeverAMarker() {
        val typed = "Write [[media:1]] where the picture goes"
        assertEquals(emptyList(), MediaMarkers.idsIn(typed))
        assertEquals(typed, MediaMarkers.replace(typed) { "https://a.jpg" })
    }

    @Test
    fun anIdTooLongForAnIntIsNotAMarker() {
        val text = "${MediaMarkers.OPEN}media:99999999999${MediaMarkers.CLOSE}"
        assertEquals(emptyList(), MediaMarkers.idsIn(text))
        assertEquals(null, MediaMarkers.markerAt(text, 0))
        assertEquals(text, MediaMarkers.strip(text))
    }

    @Test
    fun idsComeInReadingOrder() {
        assertEquals(listOf(7, 2), MediaMarkers.idsIn("a\n${m(7)}\nb ${m(2)}"))
        assertEquals(emptyList(), MediaMarkers.idsIn("no markers ${MediaMarkers.OPEN}media:x${MediaMarkers.CLOSE}"))
    }

    @Test
    fun replaceSwapsMarkersForUrls() {
        val text = "Look\n${m(1)}\nand\n${m(2)}\n"
        assertEquals("Look\nhttps://a.jpg\nand\nhttps://b.jpg\n", MediaMarkers.replace(text) { "https://${if (it == 1) "a" else "b"}.jpg" })
    }

    @Test
    fun stripLeavesNoEmptyLines() {
        assertEquals("Look\nand\n", MediaMarkers.strip("Look\n${m(1)}\nand\n${m(2)}\n"))
        assertEquals("only text", MediaMarkers.strip("only text"))
    }

    @Test
    fun aMissingFileDropsItsMarkerAndKeepsTheOthers() {
        assertEquals("x\nhttps://b\n", MediaMarkers.replace("x\n${m(1)}\n${m(2)}\n") { if (it == 2) "https://b" else null })
    }

    @Test
    fun insertionPutsTheMarkerOnItsOwnLine() {
        assertEquals("${m(1)}\n", MediaMarkers.insertion("", 0, 1))
        assertEquals("\n${m(1)}\n", MediaMarkers.insertion("Hello", 5, 1))
        assertEquals("${m(1)}", MediaMarkers.insertion("Hello\n\nmore", 6, 1))
    }

    @Test
    fun markerAtFindsTheMarkerTheCursorTouches() {
        val text = "a\n${m(3)}\nb"
        assertEquals(3, MediaMarkers.markerAt(text, 2))
        assertEquals(3, MediaMarkers.markerAt(text, 2 + m(3).length))
        assertEquals(null, MediaMarkers.markerAt(text, 3 + m(3).length))
        assertEquals(3, MediaMarkers.idAt(text, 2))
        assertEquals(null, MediaMarkers.idAt(text, 3))
    }

    @Test
    fun aUrlNeverTouchesTheTextAroundIt() {
        assertEquals("Look\nhttps://a.jpg\nWorth it", MediaMarkers.replace("Look${m(1)}Worth it") { "https://a.jpg" })
        assertEquals("https://a.jpg\nhttps://b.jpg", MediaMarkers.replace("${m(1)}${m(2)}") { if (it == 1) "https://a.jpg" else "https://b.jpg" })
    }
}
