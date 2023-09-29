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

val DefaultParagraphSpacing: TextUnit = 12.sp

internal val DefaultHeadingStyle: HeadingStyle = { level, textStyle ->
    when (level) {
        0 -> textStyle.copy(
            fontSize = 30.sp,
            fontWeight = FontWeight.Light
        )
        1 -> textStyle.copy(
            fontSize = 26.sp,
            fontWeight = FontWeight.Light
        )
        2 -> textStyle.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Light
        )
        3 -> textStyle.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        4 -> textStyle.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        5 -> textStyle.copy(
            fontWeight = FontWeight.Bold
        )
        else -> textStyle
    }
}
