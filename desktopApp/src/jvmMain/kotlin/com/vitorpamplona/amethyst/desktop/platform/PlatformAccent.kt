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

import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.ui.theme.DefaultPrimary
import com.vitorpamplona.quartz.utils.Log

/**
 * Resolves the user's preferred OS accent color. Falls back to Amethyst's purple
 * brand color if the OS doesn't expose an accent or detection fails.
 *
 * - macOS: `defaults read -g AppleAccentColor` returns -1..7 (named accents) or
 *   `AppleHighlightColor` carries an RGB triplet for the multicolor case.
 * - GNOME 47+: `gsettings get org.gnome.desktop.interface accent-color`.
 * - KDE: parses the `AccentColor` key from kdeglobals.
 * - Windows: registry `ColorizationColor` (ABGR DWORD).
 *
 * Returning the brand color is a safe default — Amethyst purple matches the app's
 * identity and looks intentional rather than broken.
 */
object PlatformAccent {
    fun systemAccent(): Color =
        when (PlatformInfo.current) {
            Platform.MACOS -> macOSAccent()
            Platform.GNOME -> gnomeAccent()
            Platform.KDE -> kdeAccent()
            Platform.WINDOWS -> windowsAccent()
            else -> DefaultPrimary
        } ?: DefaultPrimary

    // macOS named accent colors (NSColor controlAccentColor variants).
    // Index matches AppleAccentColor defaults value; -1 = Multicolor (use highlight).
    private val macAccents =
        mapOf(
            -1 to Color(0xFF0064E1), // Multicolor → blue (system default)
            0 to Color(0xFFEF5743), // Red
            1 to Color(0xFFEC8E2C), // Orange
            2 to Color(0xFFF8BA00), // Yellow
            3 to Color(0xFF62BA46), // Green
            4 to Color(0xFF0064E1), // Blue
            5 to Color(0xFFC44495), // Purple
            6 to Color(0xFFF74F9E), // Pink
            7 to Color(0xFF8C8C8C), // Graphite
        )

    private fun macOSAccent(): Color? {
        val out = exec("defaults", "read", "-g", "AppleAccentColor")
        val idx = out?.trim()?.toIntOrNull()
        if (idx != null) return macAccents[idx]

        // Highlight color triple ("0.7 0.45 0.85 Purple") — first 3 floats are RGB 0..1.
        val highlight = exec("defaults", "read", "-g", "AppleHighlightColor") ?: return null
        val rgb = highlight.trim().split(" ").mapNotNull { it.toFloatOrNull() }
        if (rgb.size >= 3) return Color(rgb[0].coerceIn(0f, 1f), rgb[1].coerceIn(0f, 1f), rgb[2].coerceIn(0f, 1f))
        return null
    }

    // GNOME 47+ accent names mapped to the libadwaita reference accent colors.
    private val gnomeAccents =
        mapOf(
            "blue" to Color(0xFF3584E4),
            "teal" to Color(0xFF2190A4),
            "green" to Color(0xFF3A944A),
            "yellow" to Color(0xFFC88800),
            "orange" to Color(0xFFED5B00),
            "red" to Color(0xFFE62D42),
            "pink" to Color(0xFFD56199),
            "purple" to Color(0xFF9141AC),
            "slate" to Color(0xFF6F8396),
        )

    private fun gnomeAccent(): Color? {
        val out = exec("gsettings", "get", "org.gnome.desktop.interface", "accent-color") ?: return null
        val name = out.trim().trim('\'').lowercase()
        return gnomeAccents[name]
    }

    private fun kdeAccent(): Color? {
        // kdeglobals stores AccentColor as comma-separated RGB ("123,45,200").
        val home = System.getProperty("user.home") ?: return null
        val candidates =
            listOf(
                "$home/.config/kdeglobals",
                "$home/.kde/share/config/kdeglobals",
            )
        for (path in candidates) {
            val file = java.io.File(path)
            if (!file.exists()) continue
            try {
                val text = file.readText()
                val match = Regex("(?m)^AccentColor\\s*=\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)").find(text)
                if (match != null) {
                    val (r, g, b) = match.destructured
                    return Color(r.toInt(), g.toInt(), b.toInt())
                }
            } catch (e: Exception) {
                Log.d("PlatformAccent") { "Failed to parse KDE accent at $path: ${e.message}" }
            }
        }
        return null
    }

    private fun windowsAccent(): Color? {
        // HKCU\Software\Microsoft\Windows\DWM has AccentColor as a DWORD in 0xAABBGGRR (ABGR).
        val out =
            exec(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\DWM",
                "/v",
                "AccentColor",
            ) ?: return null
        val match = Regex("AccentColor\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)").find(out) ?: return null
        val value = match.groupValues[1].toLongOrNull(16) ?: return null
        // ABGR: high byte = A, then B, G, R
        val r = (value and 0xFFL).toInt()
        val g = ((value shr 8) and 0xFFL).toInt()
        val b = ((value shr 16) and 0xFFL).toInt()
        return Color(r, g, b)
    }

    private fun exec(vararg cmd: String): String? =
        try {
            val proc =
                ProcessBuilder(*cmd)
                    .redirectErrorStream(true)
                    .start()
            val out =
                proc.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            val finished = proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                null
            } else if (proc.exitValue() != 0) {
                null
            } else {
                out
            }
        } catch (e: Exception) {
            Log.d("PlatformAccent") { "Failed to exec ${cmd.joinToString(" ")}: ${e.message}" }
            null
        }
}
