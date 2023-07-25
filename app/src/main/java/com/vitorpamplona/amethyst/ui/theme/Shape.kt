package com.vitorpamplona.amethyst.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp)
)

val RippleRadius45dp = 45.dp // Ripple should be +10.dp over the component size

val BottomTopHeight = Modifier.height(50.dp)
val TabRowHeight = Modifier.height(40.dp)

val SmallBorder = RoundedCornerShape(7.dp)
val QuoteBorder = RoundedCornerShape(15.dp)
val ButtonBorder = RoundedCornerShape(20.dp)
val EditFieldBorder = RoundedCornerShape(25.dp)

val ChatBubbleShapeMe = RoundedCornerShape(15.dp, 15.dp, 3.dp, 15.dp)
val ChatBubbleShapeThem = RoundedCornerShape(3.dp, 15.dp, 15.dp, 15.dp)

val StdButtonSizeModifier = Modifier.size(20.dp)

val HalfVertSpacer = Modifier.height(2.dp)

val StdHorzSpacer = Modifier.width(5.dp)
val StdVertSpacer = Modifier.height(5.dp)

val DoubleHorzSpacer = Modifier.width(10.dp)
val DoubleVertSpacer = Modifier.height(10.dp)

val HalfDoubleVertSpacer = Modifier.height(7.dp)

val Size0dp = 0.dp
val Size5dp = 5.dp
val Size10dp = 10.dp
val Size13dp = 13.dp
val Size15dp = 15.dp
val Size16dp = 16.dp
val Size17dp = 17.dp
val Size18dp = 18.dp
val Size19dp = 19.dp
val Size20dp = 20.dp
val Size22dp = 22.dp
val Size24dp = 24.dp
val Size25dp = 25.dp
val Size30dp = 30.dp
val Size35dp = 35.dp
val Size55dp = 55.dp
val Size75dp = 75.dp

val HalfStartPadding = Modifier.padding(start = 5.dp)
val StdStartPadding = Modifier.padding(start = 10.dp)
val StdTopPadding = Modifier.padding(top = 10.dp)

val HalfPadding = Modifier.padding(5.dp)
val StdPadding = Modifier.padding(10.dp)

val HalfHorzPadding = Modifier.padding(horizontal = 5.dp)
val HalfVertPadding = Modifier.padding(vertical = 5.dp)

val Size6Modifier = Modifier.size(6.dp)
val Size10Modifier = Modifier.size(10.dp)
val Size15Modifier = Modifier.size(15.dp)
val Size16Modifier = Modifier.size(16.dp)
val Size18Modifier = Modifier.size(18.dp)
val Size20Modifier = Modifier.size(20.dp)
val Size22Modifier = Modifier.size(22.dp)
val Size24Modifier = Modifier.size(24.dp)
val Size26Modifier = Modifier.size(26.dp)
val Size30Modifier = Modifier.size(30.dp)
val Size35Modifier = Modifier.size(35.dp)
val Size50Modifier = Modifier.size(50.dp)
val Size55Modifier = Modifier.size(55.dp)

val TinyBorders = Modifier.padding(2.dp)
val NoSoTinyBorders = Modifier.padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)
val ReactionRowZapraiserSize = Modifier.defaultMinSize(minHeight = 4.dp).fillMaxWidth()
val ReactionRowExpandButton = Modifier.width(65.dp).padding(start = 31.dp)

val WidthAuthorPictureModifier = Modifier.width(55.dp)
val WidthAuthorPictureModifierWithPadding = Modifier.width(65.dp)

val DividerThickness = 0.25.dp

val ReactionRowHeight = Modifier.height(24.dp).padding(start = 10.dp)
val ReactionRowHeightChat = Modifier.height(25.dp)
val UserNameRowHeight = Modifier.fillMaxWidth()
val UserNameMaxRowHeight = Modifier.fillMaxWidth()

val Height4dpModifier = Modifier.height(4.dp)

val AccountPictureModifier = Modifier.width(55.dp).height(55.dp).clip(shape = CircleShape)

val ShowMoreRelaysButtonIconButtonModifier = Modifier.size(24.dp)
val ShowMoreRelaysButtonIconModifier = Modifier.size(15.dp)
val ShowMoreRelaysButtonBoxModifer = Modifier.fillMaxWidth().height(25.dp)

val ChatBubbleMaxSizeModifier = Modifier.fillMaxWidth(0.85f)

val ModifierWidth3dp = Modifier.width(3.dp)

val NotificationIconModifier = Modifier.width(55.dp).padding(end = 5.dp)
val NotificationIconModifierSmaller = Modifier.width(55.dp).padding(end = 4.dp)

val ZapPictureCommentModifier = Modifier.height(35.dp).widthIn(min = 35.dp)
val ChatHeadlineBorders = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)

val VolumeBottomIconSize = Modifier.size(70.dp).padding(10.dp)
val PinBottomIconSize = Modifier.size(70.dp).padding(10.dp)
val NIP05IconSize = Modifier.size(14.dp).padding(top = 1.dp, start = 1.dp, end = 1.dp)

val EditFieldModifier = Modifier
    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 5.dp)
    .fillMaxWidth()
val EditFieldTrailingIconModifier = Modifier
    .height(32.dp)
    .padding(start = 5.dp, end = 10.dp)
val EditFieldLeadingIconModifier = Modifier
    .height(32.dp)
    .padding(start = 2.dp)
