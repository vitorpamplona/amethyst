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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.ui.theme.DefaultPrimary
import com.vitorpamplona.amethyst.commons.ui.theme.Primary50
import com.vitorpamplona.amethyst.commons.ui.theme.Purple200
import com.vitorpamplona.amethyst.commons.ui.theme.Purple700

// Custom dark scheme matching Amethyst brand
private val AmethystDarkColorScheme =
    darkColorScheme(
        primary = DefaultPrimary,
        secondary = Purple200,
        tertiary = Color(0xFF03DAC5),
    )

// Custom light scheme
private val AmethystLightColorScheme =
    lightColorScheme(
        primary = Purple700,
        secondary = Primary50,
        tertiary = Color(0xFF018786),
        background = Color(0xFFFFFBFE),
        surface = Color(0xFFFFFBFE),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
    )

/**
 * Resolve the active [ColorScheme] based on the user's [ThemeMode] preference.
 */
@Composable
fun resolveColorScheme(): ColorScheme {
    val themeMode by AppSettings.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.DARK -> AmethystDarkColorScheme
        ThemeMode.LIGHT -> AmethystLightColorScheme
        ThemeMode.SYSTEM -> if (systemDark) AmethystDarkColorScheme else AmethystLightColorScheme
    }
}

/**
 * Build a [Typography] scaled by the user's font size preference.
 */
@Composable
fun resolveTypography(): Typography {
    val fontPref by AppSettings.fontSize.collectAsState()
    val scale =
        when (fontPref) {
            FontSizePreference.SMALL -> 0.85f
            FontSizePreference.MEDIUM -> 1.0f
            FontSizePreference.LARGE -> 1.18f
        }
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * scale),
        displayMedium = base.displayMedium.copy(fontSize = base.displayMedium.fontSize * scale),
        displaySmall = base.displaySmall.copy(fontSize = base.displaySmall.fontSize * scale),
        headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * scale),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * scale),
        headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * scale),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * scale),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * scale),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * scale),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * scale),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * scale),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * scale),
        labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * scale),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * scale),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * scale),
    )
}

/**
 * Top-level theme wrapper that respects the user's appearance settings.
 */
@Composable
fun AmethystTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = resolveColorScheme(),
        typography = resolveTypography(),
        content = content,
    )
}
