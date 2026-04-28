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

/**
 * Preferred Material Symbols stroke weight per host OS. Each desktop UI language
 * draws icons at a different visual weight; matching them makes the in-app icons
 * feel at home next to the OS's own. Values use the Material Symbols variation
 * axis (100 = Thin, 200 = ExtraLight, 300 = Light, 400 = Regular, …, 700 = Bold).
 *
 * Reference calibration:
 * - macOS: SF Symbols "Regular" is visually lighter than 400 Material Symbols;
 *   200 (Thin/ExtraLight) matches the delicate stroke macOS users expect.
 * - GNOME: libadwaita symbolic icons are a hair thinner than Regular — 300 matches.
 * - KDE Breeze: Breeze icons are thin-to-medium, also 300.
 * - Windows (Fluent): Fluent Icons have moderate weight, 400 is the sweet spot.
 */
object PlatformIconWeight {
    val current: Int by lazy {
        when (PlatformInfo.current) {
            Platform.MACOS -> 200
            Platform.GNOME -> 300
            Platform.KDE -> 300
            Platform.WINDOWS -> 400
            Platform.LINUX_OTHER, Platform.UNKNOWN -> 300
        }
    }
}
