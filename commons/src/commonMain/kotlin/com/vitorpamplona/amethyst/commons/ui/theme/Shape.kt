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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Font sizes ──────────────────────────────────────────────────────────────

val Font14SP = 14.sp
val Font17SP = 17.sp

// ── Border shapes ───────────────────────────────────────────────────────────

val SmallestBorder = RoundedCornerShape(5.dp)
val SmallBorder = RoundedCornerShape(7.dp)
val QuoteBorder = RoundedCornerShape(15.dp)
val ButtonBorder = RoundedCornerShape(20.dp)
val EditFieldBorder = RoundedCornerShape(25.dp)

// ── Dp size constants ───────────────────────────────────────────────────────

val Size0dp = 0.dp
val Size2dp = 2.dp
val Size3dp = 3.dp
val Size5dp = 5.dp
val Size6dp = 6.dp
val Size8dp = 8.dp
val Size10dp = 10.dp
val Size12dp = 12.dp
val Size13dp = 13.dp
val Size14dp = 14.dp
val Size15dp = 15.dp
val Size16dp = 16.dp
val Size17dp = 17.dp
val Size18dp = 18.dp
val Size19dp = 19.dp
val Size20dp = 20.dp
val Size22dp = 22.dp
val Size23dp = 23.dp
val Size24dp = 24.dp
val Size25dp = 25.dp
val Size30dp = 30.dp
val Size34dp = 34.dp
val Size35dp = 35.dp
val Size40dp = 40.dp
val Size50dp = 50.dp
val Size55dp = 55.dp
val Size75dp = 75.dp
val Size100dp = 100.dp
val Size110dp = 110.dp
val Size165dp = 165.dp

// ── Spacers ─────────────────────────────────────────────────────────────────

val HalfVertSpacer = Modifier.height(2.dp)
val HalfHorzSpacer = Modifier.width(3.dp)
val StdHorzSpacer = Modifier.width(5.dp)
val StdVertSpacer = Modifier.height(5.dp)
val DoubleHorzSpacer = Modifier.width(10.dp)
val DoubleVertSpacer = Modifier.height(10.dp)

// ── Padding modifiers ───────────────────────────────────────────────────────

val HalfStartPadding = Modifier.padding(start = 5.dp)
val StdStartPadding = Modifier.padding(start = 10.dp)
val StdEndPadding = Modifier.padding(end = 10.dp)
val HalfEndPadding = Modifier.padding(end = 5.dp)
val StdTopPadding = Modifier.padding(top = 10.dp)
val HalfTopPadding = Modifier.padding(top = 5.dp)

val HalfPadding = Modifier.padding(5.dp)
val StdPadding = Modifier.padding(10.dp)
val BigPadding = Modifier.padding(15.dp)

val HalfHorzPadding = Modifier.padding(horizontal = 5.dp)
val HalfVertPadding = Modifier.padding(vertical = 5.dp)
val HorzPadding = Modifier.padding(horizontal = 10.dp)
val VertPadding = Modifier.padding(vertical = 10.dp)
val HorzHalfVertPadding = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)

val MaxWidthWithHorzPadding = Modifier.fillMaxWidth().padding(horizontal = 10.dp)

// ── Padding values ──────────────────────────────────────────────────────────

val ZeroPadding = PaddingValues(0.dp)
val HalfFeedPadding = PaddingValues(5.dp)
val FeedPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
val ButtonPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)

// ── Size modifiers ──────────────────────────────────────────────────────────

val Size5Modifier = Modifier.size(5.dp)
val Size10Modifier = Modifier.size(10.dp)
val Size14Modifier = Modifier.size(14.dp)
val Size15Modifier = Modifier.size(15.dp)
val Size16Modifier = Modifier.size(16.dp)
val Size17Modifier = Modifier.size(17.dp)
val Size18Modifier = Modifier.size(18.dp)
val Size19Modifier = Modifier.size(19.dp)
val Size20Modifier = Modifier.size(20.dp)
val Size22Modifier = Modifier.size(22.dp)
val Size24Modifier = Modifier.size(24.dp)
val Size25Modifier = Modifier.size(25.dp)
val Size26Modifier = Modifier.size(26.dp)
val Size28Modifier = Modifier.size(28.dp)
val Size30Modifier = Modifier.size(30.dp)
val Size35Modifier = Modifier.size(35.dp)
val Size39Modifier = Modifier.size(39.dp)
val Size40Modifier = Modifier.size(40.dp)
val Size50Modifier = Modifier.size(50.dp)
val Size55Modifier = Modifier.size(55.dp)
val Size75Modifier = Modifier.size(75.dp)

// ── Arrangement ─────────────────────────────────────────────────────────────

val RowColSpacing = Arrangement.spacedBy(3.dp)
val RowColSpacing5dp = Arrangement.spacedBy(5.dp)
val RowColSpacing10dp = Arrangement.spacedBy(10.dp)
val SpacedBy2dp = Arrangement.spacedBy(Size2dp)
val SpacedBy3dp = Arrangement.spacedBy(Size3dp)
val SpacedBy5dp = Arrangement.spacedBy(Size5dp)
val SpacedBy10dp = Arrangement.spacedBy(Size10dp)
val SpacedBy55dp = Arrangement.spacedBy(Size55dp)

// ── Misc ────────────────────────────────────────────────────────────────────

val DividerThickness = 0.25.dp

val ripple24dp = ripple(bounded = false, radius = Size24dp)
