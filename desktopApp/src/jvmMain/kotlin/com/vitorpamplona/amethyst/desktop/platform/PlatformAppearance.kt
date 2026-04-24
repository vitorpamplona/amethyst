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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vitorpamplona.quartz.utils.Log
import java.awt.Window
import java.awt.event.WindowFocusListener

/**
 * Reads the OS's preferred light/dark appearance.
 *
 * - macOS: `defaults read -g AppleInterfaceStyle` returns "Dark" only when dark is on.
 * - GNOME: `gsettings get org.gnome.desktop.interface color-scheme` → 'prefer-dark' / 'prefer-light' / 'default'.
 * - KDE: `kreadconfig5 --group General --key ColorScheme` (or kreadconfig6) — name contains "Dark" when dark.
 * - Windows: HKCU AppsUseLightTheme registry value (0 = dark).
 *
 * Detection is stale-tolerant: re-runs on window focus so users who flip their
 * system theme see Amethyst follow within a second of bringing the window forward.
 */
object PlatformAppearance {
    fun isSystemDark(): Boolean =
        when (PlatformInfo.current) {
            Platform.MACOS -> isMacOSDark()
            Platform.GNOME -> isGnomeDark()
            Platform.KDE -> isKdeDark()
            Platform.WINDOWS -> isWindowsDark()
            else -> true
        }

    private fun isMacOSDark(): Boolean {
        val out = exec("defaults", "read", "-g", "AppleInterfaceStyle") ?: return false
        return out.contains("Dark", ignoreCase = true)
    }

    private fun isGnomeDark(): Boolean {
        val out = exec("gsettings", "get", "org.gnome.desktop.interface", "color-scheme") ?: return true
        if (out.contains("prefer-dark", ignoreCase = true)) return true
        if (out.contains("prefer-light", ignoreCase = true)) return false
        // 'default' → fall back to legacy gtk-theme heuristic
        val theme = exec("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme").orEmpty()
        return theme.contains("dark", ignoreCase = true)
    }

    private fun isKdeDark(): Boolean {
        // Try Plasma 6 first, fall back to 5.
        val tools = listOf("kreadconfig6", "kreadconfig5")
        for (tool in tools) {
            val out = exec(tool, "--group", "General", "--key", "ColorScheme")
            if (!out.isNullOrBlank()) return out.contains("dark", ignoreCase = true)
        }
        return true
    }

    private fun isWindowsDark(): Boolean {
        val out =
            exec(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme",
            ) ?: return false
        // Output line looks like: "    AppsUseLightTheme    REG_DWORD    0x0"
        val match = Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)").find(out) ?: return false
        return match.groupValues[1].toIntOrNull(16) == 0
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
            Log.d("PlatformAppearance") { "Failed to exec ${cmd.joinToString(" ")}: ${e.message}" }
            null
        }
}

/**
 * Returns a Compose [State]<Boolean> that tracks the OS dark/light preference and
 * refreshes whenever the given AWT [Window] gains focus. Lightweight (~30ms shell-out
 * on focus) and avoids polling.
 */
@Composable
fun rememberSystemDark(awtWindow: Window?): State<Boolean> {
    val state = remember { mutableStateOf(PlatformAppearance.isSystemDark()) }
    DisposableEffect(awtWindow) {
        if (awtWindow == null) return@DisposableEffect onDispose {}
        val listener =
            object : WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {
                    state.value = PlatformAppearance.isSystemDark()
                }

                override fun windowLostFocus(e: java.awt.event.WindowEvent?) = Unit
            }
        awtWindow.addWindowFocusListener(listener)
        onDispose { awtWindow.removeWindowFocusListener(listener) }
    }
    return state
}
