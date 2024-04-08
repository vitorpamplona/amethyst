/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp

val Shapes =
    Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(0.dp),
    )

val RippleRadius45dp = 45.dp // Ripple should be +10.dp over the component size

val BottomTopHeight = Modifier.height(50.dp)
val TabRowHeight = Modifier

val SmallBorder = RoundedCornerShape(7.dp)
val SmallishBorder = RoundedCornerShape(9.dp)
val QuoteBorder = RoundedCornerShape(15.dp)
val ButtonBorder = RoundedCornerShape(20.dp)
val EditFieldBorder = RoundedCornerShape(25.dp)

val ChatBubbleShapeMe = RoundedCornerShape(15.dp, 15.dp, 3.dp, 15.dp)
val ChatBubbleShapeThem = RoundedCornerShape(3.dp, 15.dp, 15.dp, 15.dp)

val StdButtonSizeModifier = Modifier.size(19.dp)

val HalfVertSpacer = Modifier.height(2.dp)

val StdHorzSpacer = Modifier.width(5.dp)
val StdVertSpacer = Modifier.height(5.dp)

val DoubleHorzSpacer = Modifier.width(10.dp)
val DoubleVertSpacer = Modifier.height(10.dp)

val HalfDoubleVertSpacer = Modifier.height(7.dp)

val Size0dp = 0.dp
val Size5dp = 5.dp
val Size10dp = 10.dp
val Size12dp = 12.dp
val Size13dp = 13.dp
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
val Size55dp = 55.dp
val Size75dp = 75.dp

val HalfEndPadding = Modifier.padding(end = 5.dp)
val HalfStartPadding = Modifier.padding(start = 5.dp)
val StdStartPadding = Modifier.padding(start = 10.dp)
val StdTopPadding = Modifier.padding(top = 10.dp)
val HalfTopPadding = Modifier.padding(top = 5.dp)

val HalfPadding = Modifier.padding(5.dp)
val StdPadding = Modifier.padding(10.dp)
val BigPadding = Modifier.padding(15.dp)

val RowColSpacing = Arrangement.spacedBy(3.dp)

val HalfHorzPadding = Modifier.padding(horizontal = 5.dp)
val HalfVertPadding = Modifier.padding(vertical = 5.dp)

val HorzPadding = Modifier.padding(horizontal = 10.dp)
val VertPadding = Modifier.padding(vertical = 10.dp)

val MaxWidthWithHorzPadding = Modifier.fillMaxWidth().padding(horizontal = 10.dp)

val Size6Modifier = Modifier.size(6.dp)
val Size10Modifier = Modifier.size(10.dp)
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
val Size30Modifier = Modifier.size(30.dp)
val Size35Modifier = Modifier.size(35.dp)
val Size39Modifier = Modifier.size(39.dp)
val Size40Modifier = Modifier.size(40.dp)
val Size50Modifier = Modifier.size(50.dp)
val Size55Modifier = Modifier.size(55.dp)

val TinyBorders = Modifier.padding(2.dp)
val NoSoTinyBorders = Modifier.padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)
val ReactionRowZapraiserSize = Modifier.defaultMinSize(minHeight = 4.dp).fillMaxWidth()
val ReactionRowExpandButton = Modifier.width(65.dp).padding(start = 31.dp)

val WidthAuthorPictureModifier = Modifier.width(55.dp)
val WidthAuthorPictureModifierWithPadding = Modifier.width(65.dp)

val VideoReactionColumnPadding = Modifier.padding(bottom = 75.dp)

val DividerThickness = 0.25.dp

val ReactionRowHeight = Modifier.height(24.dp).padding(start = 10.dp)
val ReactionRowHeightChat = Modifier.height(25.dp)
val UserNameRowHeight = Modifier.fillMaxWidth()
val UserNameMaxRowHeight = Modifier.fillMaxWidth()

val Height24dpModifier = Modifier.height(24.dp)
val Height4dpModifier = Modifier.height(4.dp)

val AccountPictureModifier = Modifier.size(55.dp).clip(shape = CircleShape)
val HeaderPictureModifier = Modifier.size(34.dp).clip(shape = CircleShape)

val ShowMoreRelaysButtonIconButtonModifier = Modifier.size(15.dp)
val ShowMoreRelaysButtonIconModifier = Modifier.size(20.dp)
val ShowMoreRelaysButtonBoxModifer = Modifier.fillMaxWidth().height(17.dp)

val ChatBubbleMaxSizeModifier = Modifier.fillMaxWidth(0.85f)

val ModifierWidth3dp = Modifier.width(3.dp)

val NotificationIconModifier = Modifier.width(55.dp).padding(end = 5.dp)
val NotificationIconModifierSmaller = Modifier.width(55.dp).padding(end = 4.dp)

val ZapPictureCommentModifier = Modifier.height(35.dp).widthIn(min = 35.dp)
val ChatHeadlineBorders = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)

val VolumeBottomIconSize = Modifier.size(70.dp).padding(10.dp)
val PinBottomIconSize = Modifier.size(70.dp).padding(10.dp)
val NIP05IconSize = Modifier.size(13.dp).padding(top = 1.dp, start = 1.dp, end = 1.dp)

val EditFieldModifier =
    Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 5.dp).fillMaxWidth()
val EditFieldTrailingIconModifier = Modifier.height(26.dp).padding(start = 5.dp, end = 0.dp)
val EditFieldLeadingIconModifier = Modifier.height(32.dp).padding(start = 2.dp)

val ZeroPadding = PaddingValues(0.dp)
val FeedPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
val ButtonPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)

val ChatPaddingInnerQuoteModifier = Modifier.padding(top = 10.dp)
val ChatPaddingModifier =
    Modifier.fillMaxWidth(1f)
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 3.dp,
            bottom = 3.dp,
        )

val profileContentHeaderModifier =
    Modifier.fillMaxWidth().padding(top = 70.dp, start = Size25dp, end = Size25dp)
val bannerModifier = Modifier.fillMaxWidth().height(120.dp)
val drawerSpacing = Modifier.padding(top = Size10dp, start = Size25dp, end = Size25dp)

val IconRowTextModifier = Modifier.padding(start = 16.dp)
val IconRowModifier = Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 25.dp)

val emptyLineItemModifier = Modifier.height(Size75dp).fillMaxWidth()

val imageHeaderBannerSize = Modifier.fillMaxWidth().height(150.dp)

val authorNotePictureForImageHeader = Modifier.size(75.dp).padding(10.dp)

val normalWithTopMarginNoteModifier =
    Modifier.fillMaxWidth()
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 10.dp,
        )

val boostedNoteModifier =
    Modifier.fillMaxWidth()
        .padding(
            start = 0.dp,
            end = 0.dp,
            top = 0.dp,
        )

val liveStreamTag =
    Modifier
        .clip(SmallBorder)
        .background(Color.Black)
        .padding(horizontal = Size5dp)

val chatAuthorBox = Modifier.size(20.dp)
val chatAuthorImage = Modifier.size(20.dp).clip(shape = CircleShape)
val AuthorInfoVideoFeed = Modifier.width(75.dp).padding(end = 15.dp)

val inlinePlaceholder =
    Placeholder(
        width = Font17SP,
        height = Font17SP,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
    )
