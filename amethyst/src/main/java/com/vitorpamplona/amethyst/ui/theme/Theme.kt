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
import android.app.UiModeManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.ui.BlockQuoteGutter.BarGutter
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.resolveDefaults
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme.CandlestickCartesianLayerColors
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.ProvideMaterialSymbols
import com.vitorpamplona.amethyst.model.AccentColorType
import com.vitorpamplona.amethyst.model.FontFamilyType
import com.vitorpamplona.amethyst.model.FontSizeType
import com.vitorpamplona.amethyst.model.ThemeType

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

// Black or white — whichever has the higher WCAG contrast ratio against a solid accent fill
// (primary/secondary/tertiary). Runs once per scheme build (not on a render path), so the
// luminance() cost is irrelevant here. Picks black on the light pastel accents used in the dark
// theme and on the purple theme's bright teal secondary, white on the deep accents used in light.
private fun onAccent(color: Color): Color {
    val luminance = color.luminance()
    val contrastOnBlack = (luminance + 0.05f) / 0.05f
    val contrastOnWhite = 1.05f / (luminance + 0.05f)
    return if (contrastOnBlack >= contrastOnWhite) Color.Black else Color.White
}

// Faint accent-tinted fill for the Material3 "container" roles (primaryContainer, etc.), laid
// over the theme's base surface. Left unset, these roles fall back to Material's baseline violet,
// which is why container-backed UI (settings icon boxes, selected reaction chips, zap-amount
// chips, calendar selection, relay-group top bars) stayed purple under every accent.
private fun accentContainer(
    color: Color,
    surface: Color,
    alpha: Float,
): Color = color.copy(alpha = alpha).compositeOver(surface)

// Text/icon color drawn on an accentContainer. The container sits close to the base surface, so
// in the dark theme the light accent reads directly; in the light theme it is deepened toward
// black to stay legible on the pale tint.
private fun onAccentContainer(
    color: Color,
    dark: Boolean,
): Color = if (dark) color else lerp(color, Color.Black, 0.30f)

private fun darkColors(accent: AccentColorType): ColorScheme {
    val primary = accentPrimary(accent, dark = true)
    val secondary = accentSecondary(accent, dark = true)
    val surface = Color.Black
    return darkColorScheme(
        primary = primary,
        onPrimary = onAccent(primary),
        primaryContainer = accentContainer(primary, surface, 0.16f),
        onPrimaryContainer = onAccentContainer(primary, dark = true),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        secondaryContainer = accentContainer(secondary, surface, 0.16f),
        onSecondaryContainer = onAccentContainer(secondary, dark = true),
        tertiary = secondary,
        onTertiary = onAccent(secondary),
        tertiaryContainer = accentContainer(secondary, surface, 0.16f),
        onTertiaryContainer = onAccentContainer(secondary, dark = true),
        inversePrimary = accentPrimary(accent, dark = false),
        surfaceTint = primary,
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceVariant = Color(red = 29, green = 26, blue = 34),
    )
}

private fun lightColors(accent: AccentColorType): ColorScheme {
    val primary = accentPrimary(accent, dark = false)
    val secondary = accentSecondary(accent, dark = false)
    val surface = Color.White
    return lightColorScheme(
        primary = primary,
        onPrimary = onAccent(primary),
        primaryContainer = accentContainer(primary, surface, 0.12f),
        onPrimaryContainer = onAccentContainer(primary, dark = false),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        secondaryContainer = accentContainer(secondary, surface, 0.12f),
        onSecondaryContainer = onAccentContainer(secondary, dark = false),
        tertiary = secondary,
        onTertiary = onAccent(secondary),
        tertiaryContainer = accentContainer(secondary, surface, 0.12f),
        onTertiaryContainer = onAccentContainer(secondary, dark = false),
        inversePrimary = accentPrimary(accent, dark = true),
        surfaceTint = primary,
        surfaceContainerHighest = Color(red = 236, green = 230, blue = 240),
        surfaceVariant = Color(red = 250, green = 245, blue = 252),
    )
}

private val DarkColorPalette = darkColors(AccentColorType.PURPLE)
private val LightColorPalette = lightColors(AccentColorType.PURPLE)

private val DarkTransparentBackground = DarkColorPalette.background.copy(0.32f)
private val LightTransparentBackground = LightColorPalette.background.copy(0.32f)

private val DarkGrayText = DarkColorPalette.onSurface.copy(alpha = 0.52f)
private val LightGrayText = LightColorPalette.onSurface.copy(alpha = 0.52f)

private val DarkOnSurface65 = DarkColorPalette.onSurface.copy(alpha = 0.65f).compositeOver(DarkColorPalette.surface)
private val LightOnSurface65 = LightColorPalette.onSurface.copy(alpha = 0.65f).compositeOver(LightColorPalette.surface)

private val DarkPlaceholderText = DarkColorPalette.onSurface.copy(alpha = 0.42f)
private val LightPlaceholderText = LightColorPalette.onSurface.copy(alpha = 0.42f)

private val DarkOnBackgroundColorFilter = ColorFilter.tint(DarkColorPalette.onBackground)
private val LightOnBackgroundColorFilter = ColorFilter.tint(LightColorPalette.onBackground)

private val DarkSubtleButton = DarkColorPalette.onSurface.copy(alpha = 0.22f)
private val LightSubtleButton = LightColorPalette.onSurface.copy(alpha = 0.22f)

private val DarkSubtleBorder = DarkColorPalette.onSurface.copy(alpha = 0.12f)
private val LightSubtleBorder = LightColorPalette.onSurface.copy(alpha = 0.05f)

private val DarkChatBackground = DarkColorPalette.onSurface.copy(alpha = 0.12f)
private val LightChatBackground = LightColorPalette.onSurface.copy(alpha = 0.08f)

// Bubble fill for other users' chat messages. Stronger than chatBackground so the
// bubble clearly separates from the screen background in both themes.
private val DarkChatBubbleThem = DarkColorPalette.onSurface.copy(alpha = 0.18f)
private val LightChatBubbleThem = LightColorPalette.onSurface.copy(alpha = 0.11f)

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
        .padding(top = 5.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, LightSubtleBorder, QuoteBorder)

val DarkInnerPostBorderModifier =
    Modifier
        .padding(vertical = 4.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, DarkSubtleBorder, QuoteBorder)

val LightInnerPostBorderModifier =
    Modifier
        .padding(vertical = 4.dp)
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

// Geometry only — no color. The fill is applied live from the scheme's secondaryContainer in
// the selectedReactionBoxModifier getter so the selected-reaction highlight follows the accent
// instead of freezing to the purple palette captured at class load.
val SelectedReactionBoxOuterModifier =
    Modifier
        .padding(horizontal = 5.dp, vertical = 5.dp)
        .size(Size40dp)
        .clip(shape = SmallBorder)

val DarkChannelNotePictureModifier =
    Modifier
        .size(20.dp)
        .clip(shape = CircleShape)
        .border(2.dp, DarkColorPalette.background, CircleShape)

val LightChannelNotePictureModifier =
    Modifier
        .size(20.dp)
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

val darkLargeProfilePictureModifier =
    Modifier
        .size(120.dp)
        .clip(shape = CircleShape)
        .border(3.dp, DarkColorPalette.onBackground, CircleShape)

val lightLargeProfilePictureModifier =
    Modifier
        .size(120.dp)
        .clip(shape = CircleShape)
        .border(3.dp, LightColorPalette.onBackground, CircleShape)

// Geometry only — no color. The fill is applied live from the scheme's primary in the
// newItemBubbleModifier getter so the unread dot follows the accent (matching its sibling
// newItemBackgroundColor) instead of freezing to the purple palette captured at class load.
val NewItemBubbleShapeModifier =
    Modifier
        .size(10.dp)
        .clip(shape = CircleShape)

val darkBlackTagModifier =
    Modifier
        .clip(SmallestBorder)
        .background(DarkColorPalette.onBackground)
        .padding(horizontal = 5.dp)

val lightBlackTagModifier =
    Modifier
        .clip(SmallestBorder)
        .background(LightColorPalette.onBackground)
        .padding(horizontal = 5.dp)

val RichTextDefaults = RichTextStyle().resolveDefaults()

val MarkDownStyleOnDark =
    RichTextDefaults.copy(
        paragraphSpacing = DefaultParagraphSpacing,
        headingStyle = DefaultHeadingStyle,
        listStyle =
            RichTextDefaults.listStyle?.copy(
                itemSpacing = 10.sp,
            ),
        blockQuoteGutter =
            BarGutter(
                startMargin = 4.sp,
                barWidth = 3.sp,
                endMargin = 8.sp,
                color = { DarkColorPalette.primary.copy(alpha = 0.45f) },
            ),
        codeBlockStyle =
            RichTextDefaults.codeBlockStyle?.copy(
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                        lineHeight = 1.45.em,
                    ),
                modifier =
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(shape = QuoteBorder)
                        .border(1.dp, DarkSubtleBorder, QuoteBorder)
                        .background(DarkColorPalette.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ),
        tableStyle =
            RichTextDefaults.tableStyle?.copy(
                borderColor = DarkSubtleBorder,
                borderStrokeWidth = 1f,
                cellPadding = 10.sp,
            ),
        stringStyle =
            RichTextDefaults.stringStyle?.copy(
                linkStyle =
                    TextLinkStyles(
                        style =
                            SpanStyle(
                                color = DarkColorPalette.primary,
                            ),
                    ),
                codeStyle =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                        background = DarkColorPalette.onSurface.copy(alpha = 0.22f),
                        letterSpacing = 0.3.sp,
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
        blockQuoteGutter =
            BarGutter(
                startMargin = 4.sp,
                barWidth = 3.sp,
                endMargin = 8.sp,
                color = { LightColorPalette.primary.copy(alpha = 0.45f) },
            ),
        codeBlockStyle =
            RichTextDefaults.codeBlockStyle?.copy(
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                        lineHeight = 1.45.em,
                    ),
                modifier =
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(shape = QuoteBorder)
                        .border(1.dp, LightSubtleBorder, QuoteBorder)
                        .background(LightColorPalette.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ),
        tableStyle =
            RichTextDefaults.tableStyle?.copy(
                borderColor = LightSubtleBorder,
                borderStrokeWidth = 1f,
                cellPadding = 10.sp,
            ),
        stringStyle =
            RichTextDefaults.stringStyle?.copy(
                linkStyle =
                    TextLinkStyles(
                        style =
                            SpanStyle(
                                color = LightColorPalette.primary,
                            ),
                    ),
                codeStyle =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                        background = LightColorPalette.onSurface.copy(alpha = 0.12f),
                        letterSpacing = 0.3.sp,
                    ),
            ),
    )

// Compared against the dark palette's background instead of a fixed primary so the check keeps
// working when the user picks a non-purple accent (accent only changes primary/secondary, never
// background). Kept as a single reference comparison because this getter fans out to hundreds of
// themed-color call sites on hot rendering paths — luminance()/etc. would add real per-frame cost.
val ColorScheme.isLight: Boolean
    get() = background != Color.Black

// The accent-derived tints below are computed from the live scheme's primary so they follow
// the selected accent color. Color is an inline value class, so these copies don't allocate.
val ColorScheme.newItemBackgroundColor: Color
    get() = primary.copy(alpha = 0.12f)

val ColorScheme.transparentBackground: Color
    get() = if (isLight) LightTransparentBackground else DarkTransparentBackground

val ColorScheme.selectedNote: Color
    get() = primary.copy(alpha = 0.12f).compositeOver(background)

val ColorScheme.secondaryButtonBackground: Color
    get() = primary.copy(alpha = 0.32f).compositeOver(background)

val ColorScheme.lessImportantLink: Color
    get() = primary.copy(alpha = 0.52f)

val ColorScheme.mediumImportanceLink: Color
    get() = primary.copy(alpha = 0.32f)

val ColorScheme.placeholderText: Color
    get() = if (isLight) LightPlaceholderText else DarkPlaceholderText

val ColorScheme.nip05: Color
    get() = if (isLight) Nip05EmailColorLight else Nip05EmailColorDark

val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = if (isLight) LightOnBackgroundColorFilter else DarkOnBackgroundColorFilter

val ColorScheme.grayText: Color
    get() = if (isLight) LightGrayText else DarkGrayText

val ColorScheme.onSurface65: Color
    get() = if (isLight) LightOnSurface65 else DarkOnSurface65

val ColorScheme.subtleBorder: Color
    get() = if (isLight) LightSubtleBorder else DarkSubtleBorder

val ColorScheme.chatBackground: Color
    get() = if (isLight) LightChatBackground else DarkChatBackground

// Accent-following bubble fill for the logged-in user's own chat messages. Stronger
// than mediumImportanceLink so "mine" vs "theirs" vs background read at a glance
// while the default onBackground text stays readable on top of it.
val ColorScheme.chatBubbleMe: Color
    get() = primary.copy(alpha = if (isLight) 0.36f else 0.45f)

val ColorScheme.chatBubbleThem: Color
    get() = if (isLight) LightChatBubbleThem else DarkChatBubbleThem

val ColorScheme.chatDraftBackground: Color
    get() = if (isLight) LightChatDraftBackground else DarkChatDraftBackground

val ColorScheme.subtleButton: Color
    get() = if (isLight) LightSubtleButton else DarkSubtleButton

val ColorScheme.overPictureBackground: Color
    get() = if (isLight) LightOverPictureBackground else DarkOverPictureBackground

val ColorScheme.bitcoinColor: Color
    get() = if (isLight) BitcoinLight else BitcoinDark

val ColorScheme.redColorOnSecondSurface: Color
    get() = if (isLight) LightRedColorOnSecondSurface else DarkRedColorOnSecondSurface

val ColorScheme.warningColor: Color
    get() = if (isLight) LightWarningColor else DarkWarningColor

val ColorScheme.warningColorOnSecondSurface: Color
    get() = if (isLight) LightWarningColorOnSecondSurface else DarkWarningColorOnSecondSurface

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
val ColorScheme.selectedReactionBoxModifier: Modifier
    get() = SelectedReactionBoxOuterModifier.background(secondaryContainer).padding(5.dp)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.largeProfilePictureModifier: Modifier
    get() = if (isLight) lightLargeProfilePictureModifier else darkLargeProfilePictureModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.newItemBubbleModifier: Modifier
    get() = NewItemBubbleShapeModifier.background(primary)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.blackTagModifier: Modifier
    get() = if (isLight) lightBlackTagModifier else darkBlackTagModifier

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

    AmethystTheme(theme, accentColor, fontFamily, fontSize, content)
}

@Composable
fun AmethystTheme(
    prefTheme: ThemeType,
    accentColor: AccentColorType = AccentColorType.PURPLE,
    fontFamily: FontFamilyType = FontFamilyType.SYSTEM,
    fontSize: FontSizeType = FontSizeType.NORMAL,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme =
        when (prefTheme) {
            ThemeType.DARK -> {
                val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                uiManager.nightMode = UiModeManager.MODE_NIGHT_YES
                true
            }

            ThemeType.LIGHT -> {
                val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                uiManager.nightMode = UiModeManager.MODE_NIGHT_NO
                false
            }

            else -> {
                isSystemInDarkTheme()
            }
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
            ProvideMaterialSymbols {
                CompositionLocalProvider(
                    LocalDensity provides scaledDensity,
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
