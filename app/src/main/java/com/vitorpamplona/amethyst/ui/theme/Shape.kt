package com.vitorpamplona.amethyst.ui.theme

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import androidx.compose.ui.Modifier
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
val StdHorzSpacer = Modifier.width(5.dp)
val StdVertSpacer = Modifier.height(5.dp)
val DoubleHorzSpacer = Modifier.width(10.dp)
val DoubleVertSpacer = Modifier.height(10.dp)

val Size13dp = 13.dp
val Size16dp = 16.dp
val Size25dp = 25.dp
val Size35dp = 35.dp
val Size55dp = 55.dp

val StdPadding = Modifier.padding(10.dp)

val Size15Modifier = Modifier.size(15.dp)
