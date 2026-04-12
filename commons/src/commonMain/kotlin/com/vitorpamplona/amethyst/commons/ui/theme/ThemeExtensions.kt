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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

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
 * Subtle border color for cards and containers.
 * Uses different alpha for light vs dark to maintain visual contrast.
 */
val ColorScheme.subtleBorder: Color
    get() = if (isLight) onSurface.copy(alpha = 0.05f) else onSurface.copy(alpha = 0.12f)

/**
 * Reply/quote border modifier for embedded note content.
 */
@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.replyModifier: Modifier
    get() {
        val borderColor = subtleBorder
        return Modifier
            .padding(top = 5.dp)
            .fillMaxWidth()
            .clip(shape = QuoteBorder)
            .border(1.dp, borderColor, QuoteBorder)
    }
