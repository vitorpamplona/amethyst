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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Teal200 = Color(0xFF03DAC5)

// Accent palette options selected through Settings -> Accent Color.
// Each accent ships a brighter variant for the dark theme and a deeper variant for the light theme.
val AccentBlueDark = Color(0xFF82B1FF)
val AccentBlueLight = Color(0xFF1565C0)
val AccentGreenDark = Color(0xFF80CBC4)
val AccentGreenLight = Color(0xFF2E7D32)
val AccentOrangeDark = Color(0xFFFFB74D)
val AccentOrangeLight = Color(0xFFE65100)
val AccentRedDark = Color(0xFFEF9A9A)
val AccentRedLight = Color(0xFFC62828)
val AccentPinkDark = Color(0xFFF48FB1)
val AccentPinkLight = Color(0xFFAD1457)
val BitcoinOrange = Color(0xFFF7931A)
val RoyalBlue = Color(0xFF4169E1)

val BitcoinDark = Color(0xFFF7931A)
val BitcoinLight = Color(0xFFB66605)

val Following = Color(0xFF03DAC5)

val LightRedColor = Color(0xFFC62828)

val RelayIconFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.5f) })

val LightWarningColor = Color(0xFFffcc00)
val DarkWarningColor = Color(0xFFF8DE22)

val LightRedColorOnSecondSurface = Color(0xFFC62828)
val DarkRedColorOnSecondSurface = Color(0xFFF34747)

val LightWarningColorOnSecondSurface = Color(0xFFC09B14)
val DarkWarningColorOnSecondSurface = Color(0xFFE1C419)

val LightAllGoodColor = Color(0xFF339900)
val DarkAllGoodColor = Color(0xFF99cc33)

val LightFundraiserProgressColor = Color(0xFF3DB601)
val DarkFundraiserProgressColor = Color(0xFF61A229)
