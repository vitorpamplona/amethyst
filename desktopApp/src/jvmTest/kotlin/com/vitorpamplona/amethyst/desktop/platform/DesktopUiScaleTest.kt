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
package com.vitorpamplona.amethyst.desktop.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * X11 at a fractional scale: the desktop says 125% through `Xft.dpi`, AWT draws at 100%, and the
 * app makes up the rest.
 */
class DesktopUiScaleTest {
    @Test
    fun readsXftDpiFromTheResourceDatabase() {
        // `xrdb -query` on KDE Plasma at 125%.
        val resources = "*customization:\t-color\nXcursor.size:\t24\nXcursor.theme:\tbreeze_cursors\nXft.antialias:\t1\nXft.dpi:\t120\nXft.hinting:\t1\n"
        assertEquals(120f, parseXftDpi(resources))
    }

    @Test
    fun noXftDpiMeansNoScale() {
        assertNull(parseXftDpi("Xcursor.size:\t24\n"))
        assertNull(parseXftDpi(""))
    }

    @Test
    fun nonsenseDpiIsIgnored() {
        assertNull(parseXftDpi("Xft.dpi:\tabc\n"))
        assertNull(parseXftDpi("Xft.dpi:\t0\n"))
        assertNull(parseXftDpi("Xft.dpi:\t9600\n"))
    }

    @Test
    fun fractionalScaleOnTopOfAwtsWholeOne() {
        assertEquals(1.25f, extraScale(desktopScale = 1.25f, awtScale = 1f))
        assertEquals(1.5f, extraScale(desktopScale = 1.5f, awtScale = 1f))
        // GDK_SCALE=2 with Xft.dpi at 250%: AWT does 2x, the app the last 1.25x.
        assertEquals(1.25f, extraScale(desktopScale = 2.5f, awtScale = 2f))
    }

    @Test
    fun nothingExtraWhenAwtAlreadyMatches() {
        assertEquals(1f, extraScale(desktopScale = 1f, awtScale = 1f))
        assertEquals(1f, extraScale(desktopScale = 2f, awtScale = 2f))
        // Xft.dpi 97: rounding noise, not a scale.
        assertEquals(1f, extraScale(desktopScale = 97f / 96f, awtScale = 1f))
    }

    @Test
    fun neverSmallerThanAwt() {
        assertEquals(1f, extraScale(desktopScale = 1.5f, awtScale = 2f))
    }

    @Test
    fun aManualScaleMayAlsoShrink() {
        assertEquals(1.25f, manualExtraScale(manualScale = 1.25f, awtScale = 1f))
        // AMETHYST_SCALE=1.5 on a GDK_SCALE=2 session: three quarters of AWT's.
        assertEquals(0.75f, manualExtraScale(manualScale = 1.5f, awtScale = 2f))
        assertEquals(1f, manualExtraScale(manualScale = 2f, awtScale = 2f))
    }
}
