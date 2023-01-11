package com.vitorpamplona.amethyst.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorPalette = darkColors(
  primary = Purple200,
  primaryVariant = Purple700,
  secondary = Teal200,
)

private val LightColorPalette = lightColors(
  primary = Purple500,
  primaryVariant = Purple700,
  secondary = Teal200,

  /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

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