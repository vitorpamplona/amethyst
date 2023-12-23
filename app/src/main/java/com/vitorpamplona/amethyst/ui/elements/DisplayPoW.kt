package com.vitorpamplona.amethyst.ui.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.ThemeComparison
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink

@Composable
@Preview
fun DisplayPoWPreview() {
    ThemeComparison(
        onDark = {
            DisplayPoW(pow = 24)
        },
        onLight = {
            DisplayPoW(pow = 24)
        }
    )
}

@Composable
fun DisplayPoW(
    pow: Int
) {
    val powStr = remember(pow) {
        "PoW-$pow"
    }

    Text(
        powStr,
        color = MaterialTheme.colorScheme.lessImportantLink,
        fontSize = Font14SP,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}
