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
package com.vitorpamplona.amethyst.ios.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/** Theme mode: follow system, force light, or force dark. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Font size preference for note content. */
enum class FontSizePreference {
    SMALL,
    MEDIUM,
    LARGE,
}

/** Note density preference for feed layout. */
enum class NoteDensity {
    COMPACT,
    COMFORTABLE,
}

/**
 * Singleton that persists appearance settings to NSUserDefaults and
 * exposes reactive StateFlows so Compose recomposes on change.
 */
object AppSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    private const val KEY_THEME = "app_theme_mode"
    private const val KEY_FONT_SIZE = "app_font_size"
    private const val KEY_NOTE_DENSITY = "app_note_density"

    // ── Theme ──

    private val _themeMode = MutableStateFlow(loadTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        defaults.setObject(mode.name, KEY_THEME)
        _themeMode.value = mode
    }

    private fun loadTheme(): ThemeMode =
        try {
            defaults.stringForKey(KEY_THEME)?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

    // ── Font Size ──

    private val _fontSize = MutableStateFlow(loadFontSize())
    val fontSize: StateFlow<FontSizePreference> = _fontSize.asStateFlow()

    fun setFontSize(size: FontSizePreference) {
        defaults.setObject(size.name, KEY_FONT_SIZE)
        _fontSize.value = size
    }

    private fun loadFontSize(): FontSizePreference =
        try {
            defaults.stringForKey(KEY_FONT_SIZE)?.let { FontSizePreference.valueOf(it) } ?: FontSizePreference.MEDIUM
        } catch (_: Exception) {
            FontSizePreference.MEDIUM
        }

    // ── Note Density ──

    private val _noteDensity = MutableStateFlow(loadNoteDensity())
    val noteDensity: StateFlow<NoteDensity> = _noteDensity.asStateFlow()

    fun setNoteDensity(density: NoteDensity) {
        defaults.setObject(density.name, KEY_NOTE_DENSITY)
        _noteDensity.value = density
    }

    private fun loadNoteDensity(): NoteDensity =
        try {
            defaults.stringForKey(KEY_NOTE_DENSITY)?.let { NoteDensity.valueOf(it) } ?: NoteDensity.COMFORTABLE
        } catch (_: Exception) {
            NoteDensity.COMFORTABLE
        }
}
