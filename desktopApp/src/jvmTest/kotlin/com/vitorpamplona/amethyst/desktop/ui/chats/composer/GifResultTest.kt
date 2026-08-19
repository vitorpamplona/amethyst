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
package com.vitorpamplona.amethyst.desktop.ui.chats.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifResultTest {
    private fun gif(
        url: String,
        desc: String = "",
    ) = GifResult(url, desc)

    @Test
    fun dedupsByUrlKeepingFirstSeenOrder() {
        val raw =
            listOf(
                gif("https://host/a.gif", "cat"),
                gif("https://host/b.gif", "dog"),
                gif("https://host/a.gif", "cat duplicate"),
            )
        val result = mergeGifResults(raw, "")
        assertEquals(2, result.size)
        assertEquals("https://host/a.gif", result[0].url)
        assertEquals("cat", result[0].description) // first-seen wins
        assertEquals("https://host/b.gif", result[1].url)
    }

    @Test
    fun blankQueryReturnsAll() {
        val raw = listOf(gif("https://host/a.gif", "cat"), gif("https://host/b.gif", "dog"))
        assertEquals(2, mergeGifResults(raw, "   ").size)
    }

    @Test
    fun filtersByDescriptionCaseInsensitive() {
        val raw = listOf(gif("https://host/a.gif", "Happy Cat"), gif("https://host/b.gif", "dog"))
        val result = mergeGifResults(raw, "cat")
        assertEquals(1, result.size)
        assertEquals("https://host/a.gif", result[0].url)
    }

    @Test
    fun filtersByUrlWhenDescriptionEmpty() {
        val raw = listOf(gif("https://host/thumbs-up.gif", ""), gif("https://host/b.gif", "dog"))
        val result = mergeGifResults(raw, "thumbs")
        assertEquals(1, result.size)
        assertTrue(result[0].url.contains("thumbs"))
    }

    @Test
    fun noMatchReturnsEmpty() {
        val raw = listOf(gif("https://host/a.gif", "cat"))
        assertTrue(mergeGifResults(raw, "zebra").isEmpty())
    }
}
