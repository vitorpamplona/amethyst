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
package com.vitorpamplona.amethyst.ui.note.elements

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
    nav: (String) -> Unit,
) {
    val unusedHashtags =
        remember(eventContent) { hashtags.filter { !eventContent.contains(it, true) } }

    if (unusedHashtags.isNotEmpty()) {
        FlowRow(
            modifier = HalfTopPadding,
        ) {
            unusedHashtags.forEach { hashtag ->
                ClickableText(
                    text = remember { AnnotatedString("#$hashtag ") },
                    onClick = { nav("Hashtag/$hashtag") },
                    style =
                        LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.lessImportantLink,
                        ),
                )
            }
        }
    }
}
