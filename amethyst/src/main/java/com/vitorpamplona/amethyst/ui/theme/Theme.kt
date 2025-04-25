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
import android.app.UiModeManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.resolveDefaults
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme.CandlestickCartesianLayerColors
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel

private val DarkColorPalette =
    darkColorScheme(
        primary = Purple200,
        secondary = Teal200,
        tertiary = Teal200,
        background = Color.Black, // full black theme
        surface = Color.Black, // full black theme
        surfaceDim = Color.Black, // full black theme
        surfaceVariant = Color(red = 29, green = 26, blue = 34),
    )

private val LightColorPalette =
    lightColorScheme(
        primary = Purple500,
        secondary = Teal200,
        tertiary = Teal200,
        surfaceContainerHighest = Color(red = 236, green = 230, blue = 240),
        surfaceVariant = Color(red = 250, green = 245, blue = 252),
    )

private val DarkNewItemBackground = DarkColorPalette.primary.copy(0.12f)
private val LightNewItemBackground = LightColorPalette.primary.copy(0.12f)

private val DarkTransparentBackground = DarkColorPalette.background.copy(0.32f)
private val LightTransparentBackground = LightColorPalette.background.copy(0.32f)

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
private val LightSubtleBorder = LightColorPalette.onSurface.copy(alpha = 0.05f)

private val DarkChatBackground = DarkColorPalette.onSurface.copy(alpha = 0.12f)
private val LightChatBackground = LightColorPalette.onSurface.copy(alpha = 0.08f)

private val DarkChatDraftBackground = DarkColorPalette.onSurface.copy(alpha = 0.15f)
private val LightChatDraftBackground = LightColorPalette.onSurface.copy(alpha = 0.15f)

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

val DarkMaxWidthWithBackground =
    Modifier
        .fillMaxWidth()
        .background(DarkColorPalette.background)

val LightMaxWidthWithBackground =
    Modifier
        .fillMaxWidth()
        .background(LightColorPalette.background)

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

val darkLargeProfilePictureModifier =
    Modifier
        .width(120.dp)
        .height(120.dp)
        .clip(shape = CircleShape)
        .border(3.dp, DarkColorPalette.onBackground, CircleShape)

val lightLargeProfilePictureModifier =
    Modifier
        .width(120.dp)
        .height(120.dp)
        .clip(shape = CircleShape)
        .border(3.dp, LightColorPalette.onBackground, CircleShape)

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
                        background = LightColorPalette.onSurface.copy(alpha = 0.12f),
                    ),
            ),
    )

val ColorScheme.isLight: Boolean
    get() = primary == Purple500

val ColorScheme.newItemBackgroundColor: Color
    get() = if (isLight) LightNewItemBackground else DarkNewItemBackground

val ColorScheme.transparentBackground: Color
    get() = if (isLight) LightTransparentBackground else DarkTransparentBackground

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

val ColorScheme.chatBackground: Color
    get() = if (isLight) LightChatBackground else DarkChatBackground

val ColorScheme.chatDraftBackground: Color
    get() = if (isLight) LightChatDraftBackground else DarkChatDraftBackground

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

val ColorScheme.fundraiserProgressColor: Color
    get() = if (isLight) LightFundraiserProgressColor else DarkFundraiserProgressColor

val ColorScheme.markdownStyle: RichTextStyle
    get() = if (isLight) MarkDownStyleOnLight else MarkDownStyleOnDark

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.imageModifier: Modifier
    get() = if (isLight) LightImageModifier else DarkImageModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.videoGalleryModifier: Modifier
    get() = if (isLight) LightVideoModifier else DarkVideoModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.profile35dpModifier: Modifier
    get() = if (isLight) LightProfile35dpModifier else DarkProfile35dpModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.replyModifier: Modifier
    get() = if (isLight) LightReplyBorderModifier else DarkReplyBorderModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.innerPostModifier: Modifier
    get() = if (isLight) LightInnerPostBorderModifier else DarkInnerPostBorderModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.maxWidthWithBackground: Modifier
    get() = if (isLight) LightMaxWidthWithBackground else DarkMaxWidthWithBackground

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.channelNotePictureModifier: Modifier
    get() = if (isLight) LightChannelNotePictureModifier else DarkChannelNotePictureModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.userProfileBorderModifier: Modifier
    get() = if (isLight) LightProfilePictureBorder else DarkProfilePictureBorder

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.relayIconModifier: Modifier
    get() = if (isLight) LightRelayIconModifier else DarkRelayIconModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.largeRelayIconModifier: Modifier
    get() = if (isLight) LightLargeRelayIconModifier else DarkLargeRelayIconModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.selectedReactionBoxModifier: Modifier
    get() = if (isLight) LightSelectedReactionBoxModifier else DarkSelectedReactionBoxModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.largeProfilePictureModifier: Modifier
    get() = if (isLight) lightLargeProfilePictureModifier else darkLargeProfilePictureModifier

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
fun AmethystTheme(
    sharedPrefsViewModel: SharedPreferencesViewModel,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme =
        when (sharedPrefsViewModel.sharedPrefs.theme) {
            ThemeType.DARK -> {
                val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
                uiManager!!.nightMode = UiModeManager.MODE_NIGHT_YES
                true
            }
            ThemeType.LIGHT -> {
                val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
                uiManager!!.nightMode = UiModeManager.MODE_NIGHT_NO
                false
            }
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

            @Suppress("DEPRECATION")
            window.statusBarColor = colors.transparentBackground.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.transparentBackground.toArgb()

            view.setBackgroundColor(colors.background.toArgb())
        }
    }
}
