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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Shared border geometry and spacing for the event-kind renderers that are being
// pulled down from `amethyst/ui/note` into commons. These mirror the values in the
// Android app's `ui/theme/Shape.kt` so the two front ends can't drift; the Android
// copies become dead code as each renderer moves and are deleted at the end of the
// migration.

/** Rounded corner used for quoted/embedded note bodies (15dp). */
val QuoteBorder = RoundedCornerShape(15.dp)

/** Rounded corner used for small inline pills/chips (7dp). */
val SmallBorder = RoundedCornerShape(7.dp)

/** 5dp horizontal spacer used between inline elements. */
val StdHorzSpacer = Modifier.width(5.dp)

/** 5dp vertical spacer used between stacked elements. */
val StdVertSpacer = Modifier.height(5.dp)

/**
 * Faint hairline border color for cards and quoted bodies. Matches the Android app's
 * `subtleBorder`: onSurface at 5% (light) / 12% (dark) alpha.
 */
val ColorScheme.subtleBorder: Color
    get() = onSurface.copy(alpha = if (isLight) 0.05f else 0.12f)

/**
 * The wrapping modifier for a quoted/reply body: top padding, full width, clipped to
 * [QuoteBorder] with a [subtleBorder] hairline. Mirrors the Android `replyModifier`.
 */
@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.replyModifier: Modifier
    get() =
        Modifier
            .padding(top = 5.dp)
            .fillMaxWidth()
            .clip(QuoteBorder)
            .border(1.dp, subtleBorder, QuoteBorder)
