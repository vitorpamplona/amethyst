/**
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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.ui.graphics.Color

// Primary brand colors
val Primary50 = Color(red = 127, green = 103, blue = 190)
val Primary60 = Color(red = 154, green = 130, blue = 219)
val Primary70 = Color(red = 182, green = 157, blue = 248)
val Primary80 = Color(red = 208, green = 188, blue = 255)
val DefaultPrimary = Color(red = 208, green = 188, blue = 255)
val LightPurple = Color(red = 187, green = 134, blue = 252)

// Material Design colors
val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)

// Bitcoin colors
val BitcoinOrange = Color(0xFFF7931A)
val BitcoinDark = Color(0xFFF7931A)
val BitcoinLight = Color(0xFFB66605)

// Status colors
val RoyalBlue = Color(0xFF4169E1)
val Following = Color(0xFF03DAC5)
val FollowsFollow = Color.Yellow
val Nip05Verified = Color.Blue

// NIP-05 email colors
val Nip05EmailColor = Color(0xFFb198ec)
val Nip05EmailColorDark = Color(0xFF6e5490)
val Nip05EmailColorLight = Color(0xFFa770f3)

// Feedback colors
val DarkerGreen = Color.Green.copy(alpha = 0.32f)
val LightRedColor = Color(0xFFC62828)
val LighterRedColor = Color(0xFFFF0E0E)

// Warning colors
val LightWarningColor = Color(0xFFffcc00)
val DarkWarningColor = Color(0xFFF8DE22)

// Surface variant colors
val LightRedColorOnSecondSurface = Color(0xFFC62828)
val DarkRedColorOnSecondSurface = Color(0xFFF34747)
val LightWarningColorOnSecondSurface = Color(0xFFC09B14)
val DarkWarningColorOnSecondSurface = Color(0xFFE1C419)

// Success colors
val LightAllGoodColor = Color(0xFF339900)
val DarkAllGoodColor = Color(0xFF99cc33)

// Fundraiser colors
val LightFundraiserProgressColor = Color(0xFF3DB601)
val DarkFundraiserProgressColor = Color(0xFF61A229)

// Relay status colors
object RelayStatusColors {
    val Connected = Color.Green
    val Connecting = Color.Yellow
    val Disconnected = Color.Red
    val Unknown = Color.Gray
}
