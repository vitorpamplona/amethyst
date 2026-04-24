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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Per-OS Material3 [ColorScheme]s tuned to match each platform's native surface
 * tones. The OS accent (resolved via [PlatformAccent]) is plumbed in as `primary`
 * so links, focus rings, and selection highlights match the rest of the user's
 * system.
 *
 * Surface tones come from each OS's reference palettes:
 * - macOS dark: NSWindowBackgroundColor / NSAlternateSelectedControlColor.
 * - macOS light: NSColor systemBackgroundColor (#FFFFFF) / window background (#ECECEC).
 * - GNOME dark: libadwaita `@window_bg_color` (#242424) / `@view_bg_color` (#1E1E1E).
 * - GNOME light: libadwaita `@window_bg_color` (#FAFAFA) / `@view_bg_color` (#FFFFFF).
 * - KDE Breeze dark/light defaults.
 * - Windows 11 dark/light: WinUI mica reference values.
 */
object PlatformColorScheme {
    fun resolve(
        dark: Boolean,
        accent: Color,
    ): ColorScheme =
        when (PlatformInfo.current) {
            Platform.MACOS -> if (dark) macOSDark(accent) else macOSLight(accent)
            Platform.GNOME -> if (dark) gnomeDark(accent) else gnomeLight(accent)
            Platform.KDE -> if (dark) kdeDark(accent) else kdeLight(accent)
            Platform.WINDOWS -> if (dark) windowsDark(accent) else windowsLight(accent)
            else -> if (dark) genericDark(accent) else genericLight(accent)
        }

    // ── macOS ─────────────────────────────────────────────────────────────────

    private fun macOSDark(accent: Color) =
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFF1E1E1E),
            onBackground = Color(0xFFE5E5E5),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE5E5E5),
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFB8B8B8),
            surfaceContainer = Color(0xFF252525),
            surfaceContainerHigh = Color(0xFF2D2D2D),
            surfaceContainerHighest = Color(0xFF353535),
            surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainerLowest = Color(0xFF141414),
            surfaceDim = Color(0xFF1A1A1A),
            surfaceBright = Color(0xFF353535),
            outline = Color(0xFF6A6A6A),
            outlineVariant = Color(0xFF3A3A3A),
        )

    private fun macOSLight(accent: Color) =
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFFECECEC),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF2F2F2),
            onSurfaceVariant = Color(0xFF555555),
            surfaceContainer = Color(0xFFF6F6F6),
            surfaceContainerHigh = Color(0xFFEDEDED),
            surfaceContainerHighest = Color(0xFFE5E5E5),
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            outline = Color(0xFFB0B0B0),
            outlineVariant = Color(0xFFD8D8D8),
        )

    // ── GNOME (libadwaita) ────────────────────────────────────────────────────

    private fun gnomeDark(accent: Color) =
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFF242424),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF323232),
            onSurfaceVariant = Color(0xFFCCCCCC),
            surfaceContainer = Color(0xFF2C2C2C),
            surfaceContainerHigh = Color(0xFF383838),
            surfaceContainerHighest = Color(0xFF424242),
            surfaceContainerLow = Color(0xFF222222),
            surfaceContainerLowest = Color(0xFF1A1A1A),
            outline = Color(0xFF5E5E5E),
            outlineVariant = Color(0xFF3A3A3A),
        )

    private fun gnomeLight(accent: Color) =
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF5E5E5E),
            surfaceContainer = Color(0xFFF4F4F4),
            surfaceContainerHigh = Color(0xFFEDEDED),
            surfaceContainerHighest = Color(0xFFE5E5E5),
            surfaceContainerLow = Color(0xFFF9F9F9),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            outline = Color(0xFFB0B0B0),
            outlineVariant = Color(0xFFD4D4D4),
        )

    // ── KDE Plasma (Breeze) ───────────────────────────────────────────────────

    private fun kdeDark(accent: Color) =
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFF1B1E20),
            onBackground = Color(0xFFFCFCFC),
            surface = Color(0xFF232629),
            onSurface = Color(0xFFFCFCFC),
            surfaceVariant = Color(0xFF2A2E32),
            onSurfaceVariant = Color(0xFFBDC3C7),
            surfaceContainer = Color(0xFF272A2E),
            surfaceContainerHigh = Color(0xFF31353A),
            surfaceContainerHighest = Color(0xFF3B4045),
            surfaceContainerLow = Color(0xFF1F2225),
            surfaceContainerLowest = Color(0xFF18191B),
            outline = Color(0xFF4D5258),
            outlineVariant = Color(0xFF34383C),
        )

    private fun kdeLight(accent: Color) =
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFFEFF0F1),
            onBackground = Color(0xFF232629),
            surface = Color(0xFFFCFCFC),
            onSurface = Color(0xFF232629),
            surfaceVariant = Color(0xFFE5E9EC),
            onSurfaceVariant = Color(0xFF4D4D4D),
            surfaceContainer = Color(0xFFF2F3F4),
            surfaceContainerHigh = Color(0xFFEAECEE),
            surfaceContainerHighest = Color(0xFFE0E3E5),
            surfaceContainerLow = Color(0xFFF7F8F9),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            outline = Color(0xFFBABEC2),
            outlineVariant = Color(0xFFD9DCDF),
        )

    // ── Windows 11 (WinUI 3 / Mica) ───────────────────────────────────────────

    private fun windowsDark(accent: Color) =
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFF202020),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF2B2B2B),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF323232),
            onSurfaceVariant = Color(0xFFCCCCCC),
            surfaceContainer = Color(0xFF272727),
            surfaceContainerHigh = Color(0xFF323232),
            surfaceContainerHighest = Color(0xFF3D3D3D),
            surfaceContainerLow = Color(0xFF1F1F1F),
            surfaceContainerLowest = Color(0xFF1A1A1A),
            outline = Color(0xFF5E5E5E),
            outlineVariant = Color(0xFF3A3A3A),
        )

    private fun windowsLight(accent: Color) =
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFFF3F3F3),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFBFBFB),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFEDEDED),
            onSurfaceVariant = Color(0xFF555555),
            surfaceContainer = Color(0xFFF6F6F6),
            surfaceContainerHigh = Color(0xFFEDEDED),
            surfaceContainerHighest = Color(0xFFE5E5E5),
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            outline = Color(0xFFB0B0B0),
            outlineVariant = Color(0xFFD8D8D8),
        )

    // ── Generic (other Linux DEs / Unknown) ──────────────────────────────────

    private fun genericDark(accent: Color) =
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
            background = Color(0xFF1E1E1E),
            surface = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFF2A2A2A),
        )

    private fun genericLight(accent: Color) =
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            secondary = accent,
            tertiary = accent,
        )

    /**
     * Picks readable text color (white or black) on top of the given accent based on
     * its perceived luminance (Rec. 709 weights).
     */
    private fun onAccent(accent: Color): Color {
        val luminance = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
        return if (luminance > 0.55f) Color.Black else Color.White
    }
}
