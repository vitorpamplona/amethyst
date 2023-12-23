package com.vitorpamplona.amethyst.ui.elements

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedHashtags(
    hashtags: ImmutableList<String>,
    eventContent: String,
    nav: (String) -> Unit
) {
    val unusedHashtags = remember(eventContent) {
        hashtags.filter { !eventContent.contains(it, true) }
    }

    if (unusedHashtags.isNotEmpty()) {
        FlowRow(
            modifier = HalfTopPadding
        ) {
            unusedHashtags.forEach { hashtag ->
                ClickableText(
                    text = remember { AnnotatedString("#$hashtag ") },
                    onClick = { nav("Hashtag/$hashtag") },
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.lessImportantLink
                    )
                )
            }
        }
    }
}
