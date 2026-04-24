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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Per-OS Material3 [Shapes] tuned to match each platform's native rounding language.
 * Material's defaults (4 / 4 / 0 dp) read as Android — these values match what users
 * see in their OS's first-party apps.
 *
 * - macOS (Sonoma+): ~10 / 12 / 16 dp continuous-style corners.
 * - GNOME (libadwaita): 9 / 12 / 16 dp — adw_dialog / adw_card baseline.
 * - KDE (Breeze): 6 / 8 / 12 dp — Breeze prefers tighter rounding than libadwaita.
 * - Windows (WinUI 3): 4 / 8 / 8 dp — WinUI's mica surfaces use modest rounding.
 */
object PlatformShapes {
    val current: Shapes by lazy {
        when (PlatformInfo.current) {
            Platform.MACOS -> {
                Shapes(
                    extraSmall = RoundedCornerShape(6.dp),
                    small = RoundedCornerShape(8.dp),
                    medium = RoundedCornerShape(10.dp),
                    large = RoundedCornerShape(14.dp),
                    extraLarge = RoundedCornerShape(20.dp),
                )
            }

            Platform.GNOME -> {
                Shapes(
                    extraSmall = RoundedCornerShape(6.dp),
                    small = RoundedCornerShape(9.dp),
                    medium = RoundedCornerShape(12.dp),
                    large = RoundedCornerShape(16.dp),
                    extraLarge = RoundedCornerShape(24.dp),
                )
            }

            Platform.KDE -> {
                Shapes(
                    extraSmall = RoundedCornerShape(4.dp),
                    small = RoundedCornerShape(6.dp),
                    medium = RoundedCornerShape(8.dp),
                    large = RoundedCornerShape(12.dp),
                    extraLarge = RoundedCornerShape(16.dp),
                )
            }

            Platform.WINDOWS -> {
                Shapes(
                    extraSmall = RoundedCornerShape(4.dp),
                    small = RoundedCornerShape(4.dp),
                    medium = RoundedCornerShape(8.dp),
                    large = RoundedCornerShape(8.dp),
                    extraLarge = RoundedCornerShape(12.dp),
                )
            }

            Platform.LINUX_OTHER, Platform.UNKNOWN -> {
                Shapes(
                    extraSmall = RoundedCornerShape(6.dp),
                    small = RoundedCornerShape(8.dp),
                    medium = RoundedCornerShape(10.dp),
                    large = RoundedCornerShape(14.dp),
                    extraLarge = RoundedCornerShape(20.dp),
                )
            }
        }
    }
}
