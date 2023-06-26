package com.vitorpamplona.amethyst.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

val SmallBorder = RoundedCornerShape(7.dp)
val QuoteBorder = RoundedCornerShape(15.dp)
val ButtonBorder = RoundedCornerShape(20.dp)

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
val Size13dp = 13.dp
val Size15dp = 15.dp
val Size16dp = 16.dp
val Size17dp = 17.dp
val Size18dp = 18.dp
val Size19dp = 19.dp
val Size20dp = 20.dp
val Size24dp = 24.dp
val Size25dp = 25.dp
val Size30dp = 30.dp
val Size35dp = 35.dp
val Size55dp = 55.dp
val Size75dp = 75.dp

val HalfStartPadding = Modifier.padding(start = 5.dp)
val StdStartPadding = Modifier.padding(start = 10.dp)

val HalfPadding = Modifier.padding(5.dp)
val StdPadding = Modifier.padding(10.dp)

val Size15Modifier = Modifier.size(15.dp)
val Size20Modifier = Modifier.size(20.dp)
val Size22Modifier = Modifier.size(22.dp)
val Size24Modifier = Modifier.size(24.dp)
val Size30Modifier = Modifier.size(30.dp)
val Size55Modifier = Modifier.size(55.dp)

val TinyBorders = Modifier.padding(2.dp)
val NoSoTinyBorders = Modifier.padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)
val ReactionRowZapraiserSize = Modifier.defaultMinSize(minHeight = 4.dp).fillMaxWidth()
val ReactionRowExpandButton = Modifier.width(65.dp).padding(start = 31.dp)

val WidthAuthorPictureModifier = Modifier.width(55.dp)

val DiviserThickness = 0.25.dp

val ReactionRowHeight = Modifier.height(24.dp).padding(start = 10.dp)
val ReactionRowHeightChat = Modifier.height(25.dp)
val UserNameRowHeight = Modifier.height(22.dp).fillMaxWidth()
val UserNameMaxRowHeight = Modifier.heightIn(max = 22.dp).fillMaxWidth()

val Height4dpModifier = Modifier.height(4.dp)

val AccountPictureModifier = Modifier.width(55.dp).height(55.dp).clip(shape = CircleShape)

val ShowMoreRelaysButtonIconButtonModifier = Modifier.size(24.dp)
val ShowMoreRelaysButtonIconModifier = Modifier.size(15.dp)
val ShowMoreRelaysButtonBoxModifer = Modifier.fillMaxWidth().height(25.dp)

val ChatBubbleMaxSizeModifier = Modifier.fillMaxWidth(0.85f)
