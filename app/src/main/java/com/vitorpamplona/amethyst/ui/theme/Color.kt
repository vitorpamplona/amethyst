package com.vitorpamplona.amethyst.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)
val BitcoinOrange = Color(0xFFF7931A)
val RoyalBlue = Color(0xFF4169E1)

val Following = Color(0xFF03DAC5)
val FollowsFollow = Color.Yellow
val NIP05Verified = Color.Blue

val Nip05EmailColor = Color(0xFFb198ec)
val Nip05EmailColorDark = Color(0xFF6e5490)
val Nip05EmailColorLight = Color(0xFFa770f3)

val DarkerGreen = Color.Green.copy(alpha = 0.32f)

val WarningColor = Color(0xFFC62828)

val RelayIconFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.5f) })
