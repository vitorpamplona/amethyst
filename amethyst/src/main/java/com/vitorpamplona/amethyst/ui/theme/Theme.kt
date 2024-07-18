/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.resolveDefaults
import com.patrykandpatrick.vico.compose.style.ChartStyle
import com.patrykandpatrick.vico.core.DefaultColors
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel

private val DarkColorPalette =
    darkColorScheme(
        primary = Purple200,
        secondary = Teal200,
        tertiary = Teal200,
        background = Color(red = 0, green = 0, blue = 0),
        surface = Color(red = 0, green = 0, blue = 0),
        surfaceVariant = Color(red = 29, green = 26, blue = 34),
    )

private val LightColorPalette =
    lightColorScheme(
        primary = Purple500,
        secondary = Teal200,
        tertiary = Teal200,
        surfaceVariant = Color(red = 250, green = 245, blue = 252),
    )

private val DarkNewItemBackground = DarkColorPalette.primary.copy(0.12f)
private val LightNewItemBackground = LightColorPalette.primary.copy(0.12f)

private val DarkSelectedNote = DarkNewItemBackground.compositeOver(DarkColorPalette.background)
private val LightSelectedNote = LightNewItemBackground.compositeOver(LightColorPalette.background)

private val DarkButtonBackground =
    DarkColorPalette.primary.copy(alpha = 0.32f).compositeOver(DarkColorPalette.background)
private val LightButtonBackground =
    LightColorPalette.primary.copy(alpha = 0.32f).compositeOver(LightColorPalette.background)

private val DarkLessImportantLink = DarkColorPalette.primary.copy(alpha = 0.52f)
private val LightLessImportantLink = LightColorPalette.primary.copy(alpha = 0.52f)

private val DarkMediumImportantLink = DarkColorPalette.primary.copy(alpha = 0.32f)
private val LightMediumImportantLink = LightColorPalette.primary.copy(alpha = 0.32f)

private val DarkGrayText = DarkColorPalette.onSurface.copy(alpha = 0.52f)
private val LightGrayText = LightColorPalette.onSurface.copy(alpha = 0.52f)

private val DarkPlaceholderText = DarkColorPalette.onSurface.copy(alpha = 0.32f)
private val LightPlaceholderText = LightColorPalette.onSurface.copy(alpha = 0.32f)

private val DarkOnBackgroundColorFilter = ColorFilter.tint(DarkColorPalette.onBackground)
private val LightOnBackgroundColorFilter = ColorFilter.tint(LightColorPalette.onBackground)

private val DarkSubtleButton = DarkColorPalette.onSurface.copy(alpha = 0.22f)
private val LightSubtleButton = LightColorPalette.onSurface.copy(alpha = 0.22f)

private val DarkSubtleBorder = DarkColorPalette.onSurface.copy(alpha = 0.12f)
private val LightSubtleBorder = LightColorPalette.onSurface.copy(alpha = 0.12f)

private val DarkOverPictureBackground = DarkColorPalette.background.copy(0.62f)
private val LightOverPictureBackground = LightColorPalette.background.copy(0.62f)

val DarkImageModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, DarkSubtleBorder, QuoteBorder)

val LightImageModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, LightSubtleBorder, QuoteBorder)

val DarkVideoModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = RectangleShape)
        .border(1.dp, DarkSubtleBorder, RectangleShape)

val LightVideoModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = RectangleShape)
        .border(1.dp, LightSubtleBorder, RectangleShape)

val DarkProfile35dpModifier =
    Modifier
        .size(Size35dp)
        .clip(shape = CircleShape)

val LightProfile35dpModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = CircleShape)

val DarkReplyBorderModifier =
    Modifier
        .padding(top = 5.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, DarkSubtleBorder, QuoteBorder)

val LightReplyBorderModifier =
    Modifier
        .padding(top = 2.dp, bottom = 0.dp, start = 0.dp, end = 0.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, LightSubtleBorder, QuoteBorder)

val DarkVideoBorderModifier =
    Modifier
        .padding(top = 5.dp)
        .fillMaxWidth()
        .clip(shape = RectangleShape)
        .border(1.dp, DarkSubtleBorder, RectangleShape)

val LightVideoBorderModifier =
    Modifier
        .padding(top = 2.dp, bottom = 0.dp, start = 0.dp, end = 0.dp)
        .fillMaxWidth()
        .clip(shape = RectangleShape)
        .border(1.dp, LightSubtleBorder, RectangleShape)

val DarkInnerPostBorderModifier =
    Modifier
        .padding(vertical = 5.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, DarkSubtleBorder, QuoteBorder)

val LightInnerPostBorderModifier =
    Modifier
        .padding(vertical = 5.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, LightSubtleBorder, QuoteBorder)

val DarkSelectedReactionBoxModifier =
    Modifier
        .padding(horizontal = 5.dp, vertical = 5.dp)
        .size(Size40dp)
        .clip(shape = SmallBorder)
        .background(DarkColorPalette.secondaryContainer)
        .padding(5.dp)

val LightSelectedReactionBoxModifier =
    Modifier
        .padding(horizontal = 5.dp, vertical = 5.dp)
        .size(Size40dp)
        .clip(shape = SmallBorder)
        .background(LightColorPalette.secondaryContainer)
        .padding(5.dp)

val DarkChannelNotePictureModifier =
    Modifier
        .size(30.dp)
        .clip(shape = CircleShape)
        .border(2.dp, DarkColorPalette.background, CircleShape)

val LightChannelNotePictureModifier =
    Modifier
        .size(30.dp)
        .clip(shape = CircleShape)
        .border(2.dp, LightColorPalette.background, CircleShape)

val LightProfilePictureBorder =
    Modifier.border(
        3.dp,
        DarkColorPalette.background,
        CircleShape,
    )

val DarkProfilePictureBorder =
    Modifier.border(
        3.dp,
        LightColorPalette.background,
        CircleShape,
    )

val LightRelayIconModifier =
    Modifier
        .size(Size13dp)
        .clip(shape = CircleShape)

val DarkRelayIconModifier =
    Modifier
        .size(Size13dp)
        .clip(shape = CircleShape)

val LightLargeRelayIconModifier =
    Modifier
        .size(Size55dp)
        .clip(shape = CircleShape)

val DarkLargeRelayIconModifier =
    Modifier
        .size(Size55dp)
        .clip(shape = CircleShape)

val RichTextDefaults = RichTextStyle().resolveDefaults()

val MarkDownStyleOnDark =
    RichTextDefaults.copy(
        paragraphSpacing = DefaultParagraphSpacing,
        headingStyle = DefaultHeadingStyle,
        listStyle =
            RichTextDefaults.listStyle?.copy(
                itemSpacing = 10.sp,
            ),
        codeBlockStyle =
            RichTextDefaults.codeBlockStyle?.copy(
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                    ),
                modifier =
                    Modifier
                        .padding(0.dp)
                        .fillMaxWidth()
                        .clip(shape = QuoteBorder)
                        .border(1.dp, DarkSubtleBorder, QuoteBorder)
                        .background(DarkColorPalette.onSurface.copy(alpha = 0.05f)),
            ),
        stringStyle =
            RichTextDefaults.stringStyle?.copy(
                linkStyle =
                    SpanStyle(
                        color = DarkColorPalette.primary,
                    ),
                codeStyle =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                        background = DarkColorPalette.onSurface.copy(alpha = 0.22f),
                    ),
            ),
    )

val MarkDownStyleOnLight =
    RichTextDefaults.copy(
        paragraphSpacing = DefaultParagraphSpacing,
        headingStyle = DefaultHeadingStyle,
        listStyle =
            RichTextDefaults.listStyle?.copy(
                itemSpacing = 10.sp,
            ),
        codeBlockStyle =
            RichTextDefaults.codeBlockStyle?.copy(
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                    ),
                modifier =
                    Modifier
                        .padding(0.dp)
                        .fillMaxWidth()
                        .clip(shape = QuoteBorder)
                        .border(1.dp, LightSubtleBorder, QuoteBorder)
                        .background(DarkColorPalette.onSurface.copy(alpha = 0.05f)),
            ),
        stringStyle =
            RichTextDefaults.stringStyle?.copy(
                linkStyle =
                    SpanStyle(
                        color = LightColorPalette.primary,
                    ),
                codeStyle =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                        background = LightColorPalette.onSurface.copy(alpha = 0.22f),
                    ),
            ),
    )

val ColorScheme.isLight: Boolean
    get() = primary == Purple500

val ColorScheme.newItemBackgroundColor: Color
    get() = if (isLight) LightNewItemBackground else DarkNewItemBackground

val ColorScheme.selectedNote: Color
    get() = if (isLight) LightSelectedNote else DarkSelectedNote

val ColorScheme.secondaryButtonBackground: Color
    get() = if (isLight) LightButtonBackground else DarkButtonBackground

val ColorScheme.lessImportantLink: Color
    get() = if (isLight) LightLessImportantLink else DarkLessImportantLink

val ColorScheme.mediumImportanceLink: Color
    get() = if (isLight) LightMediumImportantLink else DarkMediumImportantLink

val ColorScheme.placeholderText: Color
    get() = if (isLight) LightPlaceholderText else DarkPlaceholderText

val ColorScheme.nip05: Color
    get() = if (isLight) Nip05EmailColorLight else Nip05EmailColorDark

val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = if (isLight) LightOnBackgroundColorFilter else DarkOnBackgroundColorFilter

val ColorScheme.grayText: Color
    get() = if (isLight) LightGrayText else DarkGrayText

val ColorScheme.subtleBorder: Color
    get() = if (isLight) LightSubtleBorder else DarkSubtleBorder

val ColorScheme.subtleButton: Color
    get() = if (isLight) LightSubtleButton else DarkSubtleButton

val ColorScheme.overPictureBackground: Color
    get() = if (isLight) LightOverPictureBackground else DarkOverPictureBackground

val ColorScheme.bitcoinColor: Color
    get() = if (isLight) BitcoinLight else BitcoinDark

val ColorScheme.warningColor: Color
    get() = if (isLight) LightWarningColor else DarkWarningColor

val ColorScheme.allGoodColor: Color
    get() = if (isLight) LightAllGoodColor else DarkAllGoodColor

val ColorScheme.markdownStyle: RichTextStyle
    get() = if (isLight) MarkDownStyleOnLight else MarkDownStyleOnDark

val ColorScheme.imageModifier: Modifier
    get() = if (isLight) LightImageModifier else DarkImageModifier

val ColorScheme.videoGalleryModifier: Modifier
    get() = if (isLight) LightVideoModifier else DarkVideoModifier

val ColorScheme.profile35dpModifier: Modifier
    get() = if (isLight) LightProfile35dpModifier else DarkProfile35dpModifier

val ColorScheme.replyModifier: Modifier
    get() = if (isLight) LightReplyBorderModifier else DarkReplyBorderModifier

val ColorScheme.innerPostModifier: Modifier
    get() = if (isLight) LightInnerPostBorderModifier else DarkInnerPostBorderModifier

val ColorScheme.channelNotePictureModifier: Modifier
    get() = if (isLight) LightChannelNotePictureModifier else DarkChannelNotePictureModifier

val ColorScheme.userProfileBorderModifier: Modifier
    get() = if (isLight) LightProfilePictureBorder else DarkProfilePictureBorder

val ColorScheme.relayIconModifier: Modifier
    get() = if (isLight) LightRelayIconModifier else DarkRelayIconModifier

val ColorScheme.largeRelayIconModifier: Modifier
    get() = if (isLight) LightLargeRelayIconModifier else DarkLargeRelayIconModifier

val ColorScheme.selectedReactionBoxModifier: Modifier
    get() = if (isLight) LightSelectedReactionBoxModifier else DarkSelectedReactionBoxModifier

val ColorScheme.chartStyle: ChartStyle
    get() {
        val defaultColors = if (isLight) DefaultColors.Light else DefaultColors.Dark
        return ChartStyle.fromColors(
            axisLabelColor = Color(defaultColors.axisLabelColor),
            axisGuidelineColor = Color(defaultColors.axisGuidelineColor),
            axisLineColor = Color(defaultColors.axisLineColor),
            entityColors =
                listOf(
                    defaultColors.entity1Color,
                    defaultColors.entity2Color,
                    defaultColors.entity3Color,
                ).map(::Color),
            elevationOverlayColor = Color(defaultColors.elevationOverlayColor),
        )
    }

@Composable
fun AmethystTheme(
    sharedPrefsViewModel: SharedPreferencesViewModel,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (sharedPrefsViewModel.sharedPrefs.theme) {
            ThemeType.DARK -> true
            ThemeType.LIGHT -> false
            else -> isSystemInDarkTheme()
        }
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)

            insets.isAppearanceLightNavigationBars = !darkTheme
            insets.isAppearanceLightStatusBars = !darkTheme

            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
        }
    }
}

@Composable
fun ThemeComparisonColumn(toPreview: @Composable () -> Unit) {
    Column {
        Box {
            val darkTheme: SharedPreferencesViewModel = viewModel()
            darkTheme.updateTheme(ThemeType.DARK)
            AmethystTheme(darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) { toPreview() }
            }
        }

        Box {
            val lightTheme: SharedPreferencesViewModel = viewModel()
            lightTheme.updateTheme(ThemeType.LIGHT)
            AmethystTheme(lightTheme) {
                Surface(color = MaterialTheme.colorScheme.background) { toPreview() }
            }
        }
    }
}

@Composable
fun ThemeComparisonRow(toPreview: @Composable () -> Unit) {
    Row {
        Box(modifier = Modifier.weight(1f)) {
            val darkTheme: SharedPreferencesViewModel = viewModel()
            darkTheme.updateTheme(ThemeType.DARK)
            AmethystTheme(darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) { toPreview() }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            val lightTheme: SharedPreferencesViewModel = viewModel()
            lightTheme.updateTheme(ThemeType.LIGHT)
            AmethystTheme(lightTheme) {
                Surface(color = MaterialTheme.colorScheme.background) { toPreview() }
            }
        }
    }
}
