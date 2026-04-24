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
 * Identifies the host operating system and (on Linux) the desktop environment so the
 * UI can adopt native fonts, shapes, accent colors, and window chrome.
 *
 * Detection happens once at JVM start and is cached for the process lifetime.
 */
enum class Platform {
    MACOS,
    WINDOWS,
    GNOME,
    KDE,
    LINUX_OTHER,
    UNKNOWN,
    ;

    val isLinux: Boolean get() = this == GNOME || this == KDE || this == LINUX_OTHER
}

object PlatformInfo {
    val current: Platform by lazy { detect() }

    val isMacOS: Boolean get() = current == Platform.MACOS
    val isWindows: Boolean get() = current == Platform.WINDOWS
    val isGnome: Boolean get() = current == Platform.GNOME
    val isKde: Boolean get() = current == Platform.KDE
    val isLinux: Boolean get() = current.isLinux

    private fun detect(): Platform {
        val osName = System.getProperty("os.name", "").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") -> Platform.MACOS
            osName.contains("win") -> Platform.WINDOWS
            osName.contains("nux") || osName.contains("nix") -> detectLinuxEnv()
            else -> Platform.UNKNOWN
        }
    }

    private fun detectLinuxEnv(): Platform {
        // Per the freedesktop spec, XDG_CURRENT_DESKTOP is the canonical hint.
        // It's a colon-separated list — match any token.
        val xdg = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase().orEmpty()
        val session = System.getenv("XDG_SESSION_DESKTOP")?.lowercase().orEmpty()
        val desktop = System.getenv("DESKTOP_SESSION")?.lowercase().orEmpty()
        val combined = "$xdg:$session:$desktop"

        return when {
            "gnome" in combined || "unity" in combined || "pantheon" in combined -> Platform.GNOME
            "kde" in combined || "plasma" in combined -> Platform.KDE
            else -> Platform.LINUX_OTHER
        }
    }
}
