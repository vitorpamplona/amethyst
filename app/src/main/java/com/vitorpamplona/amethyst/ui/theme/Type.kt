package com.vitorpamplona.amethyst.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.HeadingStyle

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
  /* Other default text styles to override
    button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = Font14SP
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
    */
)

val Font12SP = 12.sp
val Font14SP = 14.sp
val Font17SP = 17.sp

val MarkdownTextStyle = TextStyle(lineHeight = 1.30.em)

val DefaultParagraphSpacing: TextUnit = 16.sp

internal val DefaultHeadingStyle: HeadingStyle = { level, textStyle ->
    when (level) {
        0 -> Typography.displayLarge.copy(
            fontSize = 32.sp,
            lineHeight = 40.sp
        )
        1 -> Typography.displayMedium.copy(
            fontSize = 28.sp,
            lineHeight = 36.sp
        )
        2 -> Typography.displaySmall.copy(
            fontSize = 24.sp,
            lineHeight = 32.sp
        )
        3 -> Typography.headlineLarge.copy(
            fontSize = 22.sp,
            lineHeight = 26.sp
        )
        4 -> Typography.headlineMedium.copy(
            fontSize = 20.sp,
            lineHeight = 24.sp
        )
        5 -> Typography.headlineSmall
        6 -> Typography.titleLarge
        else -> textStyle
    }
}
