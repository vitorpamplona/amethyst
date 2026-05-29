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
import androidx.compose.ui.graphics.compositeOver
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystBlue
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystBlueDark
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPurple

/**
 * Unified Amethyst brand [ColorScheme] — consistent visual identity across all
 * platforms. Uses cyan/blue (#0096FF) as primary accent, with Amethyst purple
 * as tertiary heritage color.
 */
object PlatformColorScheme {
    fun resolve(dark: Boolean): ColorScheme = if (dark) amethystDark() else amethystLight()

    private fun amethystLight() =
        lightColorScheme(
            primary = AmethystBlue,
            onPrimary = Color.White,
            primaryContainer = AmethystBlue.copy(alpha = 0.12f).compositeOver(Color.White),
            onPrimaryContainer = AmethystBlue,
            secondary = Color(0xFF5E8FAD),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF5E8FAD).copy(alpha = 0.12f).compositeOver(Color.White),
            onSecondaryContainer = Color(0xFF5E8FAD),
            tertiary = AmethystPurple,
            onTertiary = Color.White,
            tertiaryContainer = AmethystPurple.copy(alpha = 0.12f).compositeOver(Color.White),
            onTertiaryContainer = AmethystPurple,
            background = Color(0xFFF2F2F7),
            onBackground = Color(0xFF1C1C1E),
            surface = Color.White,
            onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFF0F0F5),
            onSurfaceVariant = Color(0xFF6E6E73),
            surfaceContainer = Color(0xFFF7F7FA),
            surfaceContainerHigh = Color(0xFFEEEEF2),
            surfaceContainerHighest = Color(0xFFE5E5EA),
            surfaceContainerLow = Color(0xFFFAFAFC),
            surfaceContainerLowest = Color.White,
            surfaceDim = Color(0xFFE8E8ED),
            surfaceBright = Color.White,
            outline = Color(0xFFE0E0E0),
            outlineVariant = Color(0xFFEBEBEB),
            inverseSurface = Color(0xFF2C2C2E),
            inverseOnSurface = Color(0xFFF2F2F7),
            inversePrimary = AmethystBlueDark,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )

    private fun amethystDark() =
        darkColorScheme(
            primary = AmethystBlueDark,
            onPrimary = Color.White,
            primaryContainer = AmethystBlueDark.copy(alpha = 0.16f).compositeOver(Color(0xFF1E1E1E)),
            onPrimaryContainer = AmethystBlueDark,
            secondary = Color(0xFF7EAEC8),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF7EAEC8).copy(alpha = 0.16f).compositeOver(Color(0xFF1E1E1E)),
            onSecondaryContainer = Color(0xFF7EAEC8),
            tertiary = Color(0xFFB6A0E0),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFB6A0E0).copy(alpha = 0.16f).compositeOver(Color(0xFF1E1E1E)),
            onTertiaryContainer = Color(0xFFB6A0E0),
            background = Color(0xFF121212),
            onBackground = Color(0xFFE5E5EA),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE5E5EA),
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFF9E9EA3),
            surfaceContainer = Color(0xFF252525),
            surfaceContainerHigh = Color(0xFF2E2E2E),
            surfaceContainerHighest = Color(0xFF383838),
            surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainerLowest = Color(0xFF141414),
            surfaceDim = Color(0xFF1A1A1A),
            surfaceBright = Color(0xFF383838),
            outline = Color(0xFF3A3A3A),
            outlineVariant = Color(0xFF2E2E2E),
            inverseSurface = Color(0xFFE5E5EA),
            inverseOnSurface = Color(0xFF1C1C1E),
            inversePrimary = AmethystBlue,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
}
