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
package com.vitorpamplona.quartz.nip5aStaticWebsites

import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NappletIconPathTest {
    private fun paths(vararg p: String) = p.mapIndexed { i, path -> PathTag(path, "hash$i") }

    @Test
    fun emptyReturnsNull() {
        assertNull(NappletIconPath.choose(emptyList()))
    }

    @Test
    fun noIconLikeFileReturnsNull() {
        assertNull(NappletIconPath.choose(paths("/index.html", "/app.js", "/style.css")))
    }

    @Test
    fun picksFaviconPng() {
        val chosen = NappletIconPath.choose(paths("/index.html", "/favicon.png"))
        assertEquals("/favicon.png", chosen?.path)
    }

    @Test
    fun appleTouchIconBeatsFavicon() {
        val chosen = NappletIconPath.choose(paths("/favicon.png", "/apple-touch-icon.png"))
        assertEquals("/apple-touch-icon.png", chosen?.path)
    }

    @Test
    fun rasterPngBeatsIcoAndSvg() {
        val chosen = NappletIconPath.choose(paths("/favicon.ico", "/favicon.svg", "/icon.png"))
        assertEquals("/icon.png", chosen?.path)
    }

    @Test
    fun matchIsCaseInsensitive() {
        val chosen = NappletIconPath.choose(paths("/Favicon.PNG"))
        assertEquals("/Favicon.PNG", chosen?.path)
    }

    @Test
    fun nestedIconMatchesByBasename() {
        val chosen = NappletIconPath.choose(paths("/index.html", "/assets/icon.png"))
        assertEquals("/assets/icon.png", chosen?.path)
    }

    @Test
    fun rootIconPreferredOverNested() {
        val chosen = NappletIconPath.choose(paths("/assets/deep/icon.png", "/icon.png"))
        assertEquals("/icon.png", chosen?.path)
    }

    @Test
    fun looseFallbackMatchesIconLikeRaster() {
        // No exact conventional name, but a raster file whose name contains an icon stem.
        val chosen = NappletIconPath.choose(paths("/index.html", "/my-app-logo.webp"))
        assertEquals("/my-app-logo.webp", chosen?.path)
    }

    @Test
    fun looseFallbackIgnoresNonRasterIconNames() {
        // icon.css is an icon-named file but not a decodable raster image.
        assertNull(NappletIconPath.choose(paths("/index.html", "/icon-fonts.css")))
    }

    @Test
    fun icoChosenWhenItIsTheOnlyOption() {
        val chosen = NappletIconPath.choose(paths("/index.html", "/favicon.ico"))
        assertEquals("/favicon.ico", chosen?.path)
    }
}
