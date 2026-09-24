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
package com.vitorpamplona.amethyst.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme.CandlestickCartesianLayerColors
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.ProvideAppIcons
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.FontFamilyType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.ui.components.LocalAnimationsEnabled
import com.vitorpamplona.amethyst.commons.ui.components.LocalProfilePictureCache
import com.vitorpamplona.amethyst.commons.ui.theme.AccentBlueDark
import com.vitorpamplona.amethyst.commons.ui.theme.AccentBlueLight
import com.vitorpamplona.amethyst.commons.ui.theme.AccentGreenDark
import com.vitorpamplona.amethyst.commons.ui.theme.AccentGreenLight
import com.vitorpamplona.amethyst.commons.ui.theme.AccentOrangeDark
import com.vitorpamplona.amethyst.commons.ui.theme.AccentOrangeLight
import com.vitorpamplona.amethyst.commons.ui.theme.AccentPinkDark
import com.vitorpamplona.amethyst.commons.ui.theme.AccentPinkLight
import com.vitorpamplona.amethyst.commons.ui.theme.AccentRedDark
import com.vitorpamplona.amethyst.commons.ui.theme.AccentRedLight
import com.vitorpamplona.amethyst.commons.ui.theme.Purple200
import com.vitorpamplona.amethyst.commons.ui.theme.Purple500
import com.vitorpamplona.amethyst.commons.ui.theme.Shapes
import com.vitorpamplona.amethyst.commons.ui.theme.Teal200
import com.vitorpamplona.amethyst.commons.ui.theme.Typography
import com.vitorpamplona.amethyst.commons.ui.theme.amethystDarkColorScheme
import com.vitorpamplona.amethyst.commons.ui.theme.amethystLightColorScheme
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.transparentBackground
import com.vitorpamplona.amethyst.commons.ui.theme.withFontFamily

// The accent color (primary/secondary/tertiary) is user-selectable in Settings -> Accent Color.
// Purple keeps the original Amethyst look (purple primary + teal secondary). Every other accent
// uses its single hue across primary and secondary for a cohesive single-color theme.
private fun accentPrimary(
    accent: AccentColorType,
    dark: Boolean,
): Color =
    when (accent) {
        AccentColorType.PURPLE -> if (dark) Purple200 else Purple500
        AccentColorType.BLUE -> if (dark) AccentBlueDark else AccentBlueLight
        AccentColorType.GREEN -> if (dark) AccentGreenDark else AccentGreenLight
        AccentColorType.ORANGE -> if (dark) AccentOrangeDark else AccentOrangeLight
        AccentColorType.RED -> if (dark) AccentRedDark else AccentRedLight
        AccentColorType.PINK -> if (dark) AccentPinkDark else AccentPinkLight
    }

private fun accentSecondary(
    accent: AccentColorType,
    dark: Boolean,
): Color = if (accent == AccentColorType.PURPLE) Teal200 else accentPrimary(accent, dark)

// Representative colour for an accent option, used by the Settings accent-picker swatches — the
// same primary the theme would apply for the given light/dark mode, so the swatch previews the
// real result.
fun AccentColorType.previewColor(dark: Boolean): Color = accentPrimary(this, dark)

private fun darkColors(accent: AccentColorType): ColorScheme =
    amethystDarkColorScheme(
        primary = accentPrimary(accent, dark = true),
        secondary = accentSecondary(accent, dark = true),
        inversePrimary = accentPrimary(accent, dark = false),
    )

private fun lightColors(accent: AccentColorType): ColorScheme =
    amethystLightColorScheme(
        primary = accentPrimary(accent, dark = false),
        secondary = accentSecondary(accent, dark = false),
        inversePrimary = accentPrimary(accent, dark = true),
    )

val chartLightColors =
    VicoTheme(
        candlestickCartesianLayerColors =
            CandlestickCartesianLayerColors(
                Color(0xff0ac285),
                Color(0xff000000),
                Color(0xffe8304f),
            ),
        columnCartesianLayerColors = listOf(Color(0xff3287ff), Color(0xff0ac285), Color(0xffffab02)),
        lineColor = Color(0xffbcbfc2),
        textColor = Color(0xff000000),
    )

val chartDarkColors =
    VicoTheme(
        candlestickCartesianLayerColors =
            CandlestickCartesianLayerColors(
                Color(0xff0ac285),
                Color(0xffffffff),
                Color(0xffe8304f),
            ),
        columnCartesianLayerColors = listOf(Color(0xff3287ff), Color(0xff0ac285), Color(0xffffab02)),
        lineColor = Color(0xff494c50),
        textColor = Color(0xffffffff),
    )

val ColorScheme.chartStyle: VicoTheme
    get() = if (isLight) chartLightColors else chartDarkColors

@Composable
fun AmethystTheme(content: @Composable () -> Unit) {
    val uiPrefs = Amethyst.instance.uiPrefs.value
    val theme by uiPrefs.theme.collectAsStateWithLifecycle()
    val accentColor by uiPrefs.accentColor.collectAsStateWithLifecycle()
    val fontFamily by uiPrefs.fontFamily.collectAsStateWithLifecycle()
    val fontSize by uiPrefs.fontSize.collectAsStateWithLifecycle()
    val featureSet by uiPrefs.featureSet.collectAsStateWithLifecycle()

    AmethystTheme(theme, accentColor, fontFamily, fontSize, animationsEnabled = featureSet != FeatureSetType.PERFORMANCE, content = content)
}

@Composable
fun AmethystTheme(
    prefTheme: ThemeType,
    accentColor: AccentColorType = AccentColorType.PURPLE,
    fontFamily: FontFamilyType = FontFamilyType.SYSTEM,
    fontSize: FontSizeType = FontSizeType.NORMAL,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Deliberately no UiModeManager.nightMode write: changing the device night mode needs
    // MODIFY_DAY_NIGHT_MODE, which this app does not declare, so the call silently no-ops — and it
    // ran on every recomposition of the theme, writing device state from inside composition. The
    // in-app choice is applied through the colour scheme below, which is what actually took effect.
    val darkTheme =
        when (prefTheme) {
            ThemeType.DARK -> true
            ThemeType.LIGHT -> false
            else -> isSystemInDarkTheme()
        }
    val colors =
        remember(darkTheme, accentColor) {
            if (darkTheme) darkColors(accentColor) else lightColors(accentColor)
        }

    val resolvedFontFamily = remember(fontFamily) { fontFamily.toFontFamily() }
    val typography = remember(fontFamily) { Typography.withFontFamily(resolvedFontFamily) }

    val density = LocalDensity.current
    val scaledDensity =
        remember(density, fontSize) {
            Density(density.density, density.fontScale * fontSize.scale)
        }

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes,
        content = {
            ProvideAppIcons {
                CompositionLocalProvider(
                    LocalDensity provides scaledDensity,
                    // ImageLoaderSetup registers the avatar thumbnail cache and the local Blossom bridge.
                    LocalProfilePictureCache provides true,
                    // Performance mode turns decorative animations (crossfades) off app-wide.
                    LocalAnimationsEnabled provides animationsEnabled,
                    LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = resolvedFontFamily)),
                    content = content,
                )
            }
        },
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)

            insets.isAppearanceLightNavigationBars = !darkTheme
            insets.isAppearanceLightStatusBars = !darkTheme

            @Suppress("DEPRECATION")
            window.statusBarColor = colors.transparentBackground.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.transparentBackground.toArgb()

            view.setBackgroundColor(colors.background.toArgb())
        }
    }
}

// Maps the user-selected font preference to a Compose [FontFamily].
// SYSTEM returns null so the platform default is used unchanged.
fun FontFamilyType.toFontFamily(): FontFamily? =
    when (this) {
        FontFamilyType.SYSTEM -> null
        FontFamilyType.SANS_SERIF -> FontFamily.SansSerif
        FontFamilyType.SERIF -> FontFamily.Serif
        FontFamilyType.MONOSPACE -> FontFamily.Monospace
    }
