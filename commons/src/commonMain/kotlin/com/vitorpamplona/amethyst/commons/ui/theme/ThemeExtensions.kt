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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance

/**
 * Determines if the color scheme is light mode.
 * Based on primary color luminance.
 */
val ColorScheme.isLight: Boolean
    get() = primary.luminance() < 0.5f

/**
 * Color filter for onBackground color (for tinting icons/images).
 */
val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = ColorFilter.tint(onBackground)

/**
 * De-emphasized text/icon color for secondary markers and hints. Matches the
 * Android app's placeholderText, which is the palette's onSurface at 42%.
 */
val ColorScheme.placeholderText: Color
    get() = onSurface.copy(alpha = 0.42f)

/**
 * Slightly stronger de-emphasized text color than [placeholderText]. Matches the
 * Android app's grayText, the palette's onSurface at 52%.
 */
val ColorScheme.grayText: Color
    get() = onSurface.copy(alpha = 0.52f)

/** Green "all good / healthy" status color. Mirrors the Android app's allGoodColor. */
val ColorScheme.allGoodColor: Color
    get() = if (isLight) Color(0xFF339900) else Color(0xFF99CC33)

/** Amber "warning / degraded" status color. Mirrors the Android app's warningColor. */
val ColorScheme.warningColor: Color
    get() = if (isLight) Color(0xFFFFCC00) else Color(0xFFF8DE22)
