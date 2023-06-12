package com.vitorpamplona.amethyst.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorPalette = darkColors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200,
    secondaryVariant = Color(0xFFF7931A)
)

private val LightColorPalette = lightColors(
    primary = Purple500,
    primaryVariant = Purple700,
    secondary = Teal200,
    secondaryVariant = Color(0xFFB66605)
)

private val DarkNewItemBackground = DarkColorPalette.primary.copy(0.12f)
private val LightNewItemBackground = LightColorPalette.primary.copy(0.12f)

private val DarkReplyItemBackground = DarkColorPalette.onSurface.copy(alpha = 0.05f)
private val LightReplyItemBackground = LightColorPalette.onSurface.copy(alpha = 0.05f)

private val DarkSelectedNote = DarkNewItemBackground.compositeOver(DarkColorPalette.background)
private val LightSelectedNote = LightNewItemBackground.compositeOver(LightColorPalette.background)

private val DarkButtonBackground = DarkColorPalette.primary.copy(alpha = 0.32f).compositeOver(DarkColorPalette.background)
private val LightButtonBackground = LightColorPalette.primary.copy(alpha = 0.32f).compositeOver(LightColorPalette.background)

private val DarkLessImportantLink = DarkColorPalette.primary.copy(alpha = 0.52f)
private val LightLessImportantLink = LightColorPalette.primary.copy(alpha = 0.52f)

private val DarkMediumImportantLink = DarkColorPalette.primary.copy(alpha = 0.32f)
private val LightMediumImportantLink = LightColorPalette.primary.copy(alpha = 0.32f)

private val DarkVeryImportantLink = DarkColorPalette.primary.copy(alpha = 0.12f)
private val LightVeryImportantLink = LightColorPalette.primary.copy(alpha = 0.12f)

private val DarkPlaceholderText = DarkColorPalette.onSurface.copy(alpha = 0.32f)
private val LightPlaceholderText = LightColorPalette.onSurface.copy(alpha = 0.32f)

private val DarkSubtleBorder = DarkColorPalette.onSurface.copy(alpha = 0.12f)
private val LightSubtleBorder = LightColorPalette.onSurface.copy(alpha = 0.12f)

private val DarkImageVerifier = Nip05.copy(0.52f).compositeOver(DarkColorPalette.background)
private val LightImageVerifier = Nip05.copy(0.52f).compositeOver(LightColorPalette.background)

private val DarkOverPictureBackground = DarkColorPalette.background.copy(0.62f)
private val LightOverPictureBackground = LightColorPalette.background.copy(0.62f)

val Colors.newItemBackgroundColor: Color
    get() = if (isLight) LightNewItemBackground else DarkNewItemBackground

val Colors.replyBackground: Color
    get() = if (isLight) LightReplyItemBackground else DarkReplyItemBackground

val Colors.selectedNote: Color
    get() = if (isLight) LightSelectedNote else DarkSelectedNote

val Colors.secondaryButtonBackground: Color
    get() = if (isLight) LightButtonBackground else DarkButtonBackground

val Colors.lessImportantLink: Color
    get() = if (isLight) LightLessImportantLink else DarkLessImportantLink

val Colors.mediumImportanceLink: Color
    get() = if (isLight) LightMediumImportantLink else DarkMediumImportantLink
val Colors.veryImportantLink: Color
    get() = if (isLight) LightVeryImportantLink else DarkVeryImportantLink

val Colors.placeholderText: Color
    get() = if (isLight) LightPlaceholderText else DarkPlaceholderText

val Colors.subtleBorder: Color
    get() = if (isLight) LightSubtleBorder else DarkSubtleBorder

val Colors.hashVerified: Color
    get() = if (isLight) LightImageVerifier else DarkImageVerifier

val Colors.overPictureBackground: Color
    get() = if (isLight) LightOverPictureBackground else DarkOverPictureBackground

@Composable
fun AmethystTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )

    val view = LocalView.current
    if (!view.isInEditMode && darkTheme) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
        }
    }
}
