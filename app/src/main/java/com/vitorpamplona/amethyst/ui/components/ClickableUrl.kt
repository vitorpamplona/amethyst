package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString

@Composable
fun ClickableUrl(urlText: String, url: String) {
    val uri = LocalUriHandler.current

    val text = remember(urlText) {
        AnnotatedString(urlText)
    }

    ClickableText(
        text = text,
        onClick = {
            runCatching {
                val doubleCheckedUrl = if (url.contains("://")) url else "https://$url"
                uri.openUri(doubleCheckedUrl)
            }
        },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
    )
}
