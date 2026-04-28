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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Material3 [Typography] using the host OS's preferred UI font with per-OS letter
 * spacing tuned to match native apps. Material's defaults are positively tracked
 * (Roboto-style); Apple's SF Pro and GNOME's Adwaita are tracked tighter at the
 * larger sizes, so we mirror that.
 *
 * The body sizes stay close to Material defaults so the existing layout code that
 * assumes 14–16sp body text doesn't reflow.
 */
object PlatformTypography {
    val current: Typography by lazy { build(PlatformFonts.ui) }

    private fun build(family: FontFamily): Typography {
        // Per-OS letter spacing offset applied to display/headline styles.
        val tightening: TextUnit =
            when (PlatformInfo.current) {
                Platform.MACOS -> (-0.4).sp

                // SF Pro is set tight at large sizes
                Platform.GNOME -> (-0.2).sp

                // Adwaita Sans is mildly tight
                Platform.KDE, Platform.WINDOWS -> 0.sp

                else -> 0.sp
            }

        fun ts(
            size: Int,
            line: Int,
            weight: FontWeight = FontWeight.Normal,
            tracking: TextUnit = 0.sp,
        ) = TextStyle(
            fontFamily = family,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = line.sp,
            letterSpacing = tracking,
        )

        return Typography(
            displayLarge = ts(57, 64, FontWeight.Normal, tightening),
            displayMedium = ts(45, 52, FontWeight.Normal, tightening),
            displaySmall = ts(36, 44, FontWeight.Normal, tightening),
            headlineLarge = ts(32, 40, FontWeight.SemiBold, tightening),
            headlineMedium = ts(28, 36, FontWeight.SemiBold, tightening),
            headlineSmall = ts(24, 32, FontWeight.SemiBold, tightening),
            titleLarge = ts(22, 28, FontWeight.SemiBold),
            titleMedium = ts(16, 24, FontWeight.Medium, 0.15.sp),
            titleSmall = ts(14, 20, FontWeight.Medium, 0.1.sp),
            bodyLarge = ts(16, 24, FontWeight.Normal, 0.15.sp),
            bodyMedium = ts(14, 20, FontWeight.Normal, 0.25.sp),
            bodySmall = ts(12, 16, FontWeight.Normal, 0.4.sp),
            labelLarge = ts(14, 20, FontWeight.Medium, 0.1.sp),
            labelMedium = ts(12, 16, FontWeight.Medium, 0.5.sp),
            labelSmall = ts(11, 16, FontWeight.Medium, 0.5.sp),
        )
    }
}
