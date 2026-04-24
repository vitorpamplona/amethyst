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

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import java.awt.GraphicsEnvironment

/**
 * Resolves a [FontFamily] backed by the host OS's preferred UI font, with a
 * per-OS fallback chain. Uses Skia's font manager to look up the family by name
 * — names not installed on the system are skipped, so the order acts as a chain.
 *
 * Targets per OS:
 * - macOS: SF Pro Text (the system UI font; "Helvetica Neue" / "Helvetica" as fallbacks).
 * - GNOME: Adwaita Sans (47+), Cantarell (older), Inter, Noto Sans.
 * - KDE Plasma: Noto Sans (Plasma's default), then Inter / DejaVu Sans.
 * - Windows: Segoe UI Variable Text (Win 11), Segoe UI (Win 10).
 *
 * The final fallback is `FontFamily.Default` so Compose still has something to draw
 * with on stripped-down systems.
 */
object PlatformFonts {
    val ui: FontFamily by lazy { resolve(uiCandidates()) }

    val mono: FontFamily by lazy { resolve(monoCandidates(), fallback = FontFamily.Monospace) }

    private fun uiCandidates(): List<String> =
        when (PlatformInfo.current) {
            Platform.MACOS -> {
                listOf("SF Pro Text", ".AppleSystemUIFont", "Helvetica Neue", "Helvetica")
            }

            Platform.WINDOWS -> {
                listOf("Segoe UI Variable Text", "Segoe UI Variable", "Segoe UI")
            }

            Platform.GNOME -> {
                listOf("Adwaita Sans", "Cantarell", "Inter", "Noto Sans", "DejaVu Sans")
            }

            Platform.KDE -> {
                listOf("Noto Sans", "Inter", "DejaVu Sans", "Cantarell")
            }

            Platform.LINUX_OTHER -> {
                listOf("Inter", "Noto Sans", "DejaVu Sans", "Cantarell")
            }

            Platform.UNKNOWN -> {
                emptyList()
            }
        }

    private fun monoCandidates(): List<String> =
        when (PlatformInfo.current) {
            Platform.MACOS -> {
                listOf("SF Mono", "Menlo", "Monaco")
            }

            Platform.WINDOWS -> {
                listOf("Cascadia Mono", "Cascadia Code", "Consolas")
            }

            Platform.GNOME -> {
                listOf("Adwaita Mono", "Source Code Pro", "DejaVu Sans Mono")
            }

            Platform.KDE -> {
                listOf("Hack", "Noto Sans Mono", "Source Code Pro")
            }

            Platform.LINUX_OTHER -> {
                listOf("JetBrains Mono", "Source Code Pro", "DejaVu Sans Mono")
            }

            Platform.UNKNOWN -> {
                emptyList()
            }
        }

    private fun resolve(
        candidates: List<String>,
        fallback: FontFamily = FontFamily.Default,
    ): FontFamily {
        if (candidates.isEmpty()) return fallback
        val installed = installedFamilies
        val name = candidates.firstOrNull { it in installed } ?: return fallback
        return systemFamily(name)
    }

    /**
     * Builds a [FontFamily] from a system family name. Skia's `FontMgr.matchFamilyStyle`
     * gives us a regular-weight typeface; Skia synthesizes bold/italic from it on demand,
     * which is how every native UI toolkit on desktop renders system fonts.
     */
    private fun systemFamily(name: String): FontFamily {
        val skTypeface =
            runCatching { FontMgr.default.matchFamilyStyle(name, FontStyle.NORMAL) }
                .getOrNull() ?: return FontFamily.Default
        return FontFamily(Typeface(skTypeface))
    }

    private val installedFamilies: Set<String> by lazy {
        try {
            GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .availableFontFamilyNames
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}
