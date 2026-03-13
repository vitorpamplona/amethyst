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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Chat bubble shapes - standalone messages
val ChatBubbleShapeMe = RoundedCornerShape(15.dp, 15.dp, 3.dp, 15.dp)
val ChatBubbleShapeThem = RoundedCornerShape(3.dp, 15.dp, 15.dp, 15.dp)

// Chat bubble shapes - grouped messages (me = right side, them = left side)
// First in group: normal top corners, small bottom-right for me / small bottom-left for them
val ChatBubbleShapeMeFirst = RoundedCornerShape(15.dp, 15.dp, 3.dp, 15.dp)
val ChatBubbleShapeThemFirst = RoundedCornerShape(15.dp, 15.dp, 15.dp, 3.dp)

// Middle of group: small corners on the sender side
val ChatBubbleShapeMeMiddle = RoundedCornerShape(15.dp, 5.dp, 3.dp, 15.dp)
val ChatBubbleShapeThemMiddle = RoundedCornerShape(5.dp, 15.dp, 15.dp, 3.dp)

// Last in group: small top corner on sender side, normal bottom
val ChatBubbleShapeMeLast = RoundedCornerShape(15.dp, 5.dp, 15.dp, 15.dp)
val ChatBubbleShapeThemLast = RoundedCornerShape(5.dp, 15.dp, 15.dp, 15.dp)

// Grouping spacing
val ChatGroupedPadding =
    Modifier
        .fillMaxWidth(1f)
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 1.dp,
            bottom = 1.dp,
        )

// Chat bubble modifiers
val ChatBubbleMaxSizeModifier = Modifier.fillMaxWidth(0.85f)
val ChatPaddingInnerQuoteModifier = Modifier
val ChatPaddingModifier =
    Modifier
        .fillMaxWidth(1f)
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 3.dp,
            bottom = 3.dp,
        )

// Message bubble internal padding
val MessageBubbleLimits = Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 6.dp)

// Chat author area
val ChatAuthorBox = Modifier.size(20.dp)
val ChatAuthorImage = Modifier.size(20.dp).clip(shape = CircleShape)

// Spacing values reused across chat composables
val ChatRowColSpacing5dp = Arrangement.spacedBy(5.dp)
val ChatHalfHalfVertPadding = Modifier.padding(vertical = 3.dp)
val ChatStdHorzSpacer = Modifier.width(5.dp)
val ChatStdPadding = Modifier.padding(10.dp)
val ChatReactionRowHeight = Modifier.height(20.dp)

// Common size values
val ChatSize20dp = 20.dp
val ChatSize34dp = 34.dp

// Below-bubble timestamp padding
val ChatTimestampPadding = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)

// Reaction pill styling
val ChatReactionPillShape = RoundedCornerShape(12.dp)
val ChatReactionPillPadding = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
val ChatReactionPillSpacing = Arrangement.spacedBy(4.dp)
val ChatBelowBubblePadding = Modifier.padding(top = 2.dp)

// Reply preview bar
val ChatReplyBarWidth = 3.dp
val ChatReplyBarShape = RoundedCornerShape(2.dp)
val ChatReplyPreviewPadding = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp)

// Chat color extensions for ColorScheme
val ColorScheme.chatBubbleBackground: Color
    get() = if (isLight) onSurface.copy(alpha = 0.08f) else onSurface.copy(alpha = 0.12f)

val ColorScheme.chatBubbleDraftBackground: Color
    get() = onSurface.copy(alpha = 0.15f)

val ColorScheme.chatBubbleMeBackground: Color
    get() = primary.copy(alpha = 0.32f)

val ColorScheme.chatPlaceholderText: Color
    get() = onSurface.copy(alpha = 0.42f)
