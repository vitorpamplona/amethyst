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
package com.vitorpamplona.amethyst.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedImageSupportTest {
    @Test
    fun avifUrlsAreAnimatedImages() {
        assertTrue(isAnimatedImageUrl("https://cdn.example.com/emoji.avif"))
        assertTrue(isAnimatedImageUrl("https://cdn.example.com/emoji.AVIF?name=pack.avif"))
        assertTrue(isAnimatedImageUrl("blossom:abcd.avif?xs=https://cdn.example.com"))
    }

    @Test
    fun gifUrlsStillAreAnimatedImages() {
        assertTrue(isAnimatedImageUrl("https://cdn.example.com/emoji.gif"))
        assertTrue(isAnimatedImageUrl("https://cdn.example.com/emoji.GIF#preview"))
    }

    @Test
    fun staticImageUrlsAreNotAnimatedImages() {
        assertFalse(isAnimatedImageUrl("https://cdn.example.com/emoji.png"))
        assertFalse(isAnimatedImageUrl("https://cdn.example.com/emoji.webp"))
    }

    @Test
    fun animatedImageMimeTypesIncludeAvifAndGif() {
        assertTrue(isAnimatedImageMimeType("image/avif"))
        assertTrue(isAnimatedImageMimeType("IMAGE/GIF"))
        assertFalse(isAnimatedImageMimeType("image/png"))
        assertFalse(isAnimatedImageMimeType(null))
    }
}
