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
 * Gray text color for less important UI elements.
 * Uses onSurface with 52% opacity.
 */
val ColorScheme.grayText: Color
    get() = onSurface.copy(alpha = 0.52f)

/**
 * Placeholder text color.
 * Uses onSurface with 42% opacity.
 */
val ColorScheme.placeholderText: Color
    get() = onSurface.copy(alpha = 0.42f)

/**
 * Subtle button tint.
 * Uses onSurface with 22% opacity.
 */
val ColorScheme.subtleButton: Color
    get() = onSurface.copy(alpha = 0.22f)
