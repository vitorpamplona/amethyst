/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.commons.ui.richtext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * Inline custom emoji: renders [text], replacing each `:shortcode:` present in
 * [emojis] with its image. Universal to every front end, so it lives in the
 * shared core rather than the platform seam. Falls back to plain [Text] when the
 * text carries no known emoji.
 *
 * Cross-platform equivalent of Amethyst's `CreateTextWithEmoji`, built on quartz's
 * [CustomEmoji.assembleAnnotatedList] + [InLineIconRenderer].
 */
@Composable
fun RenderCustomEmoji(
    text: String,
    emojis: ImmutableMap<String, String>,
    modifier: Modifier = Modifier,
) {
    val renderable = remember(text, emojis) { CustomEmoji.assembleAnnotatedList(text, emojis) }

    if (renderable.isNullOrEmpty()) {
        Text(text, modifier)
    } else {
        InLineIconRenderer(renderable, LocalTextStyle.current.toSpanStyle(), modifier = modifier)
    }
}

/**
 * Renders an already-assembled list of [CustomEmoji.Renderable]s: text spans as
 * text, image spans as inline [AsyncImage]s sized to ~1.1× the current font.
 */
@Composable
fun InLineIconRenderer(
    wordsInOrder: ImmutableList<CustomEmoji.Renderable>,
    style: SpanStyle,
    fontSize: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
) {
    val placeholderSize =
        remember(fontSize) {
            if (fontSize == TextUnit.Unspecified) 22.sp else fontSize.times(1.1f)
        }

    val inlineContent =
        wordsInOrder
            .mapIndexedNotNull { idx, value ->
                if (value is CustomEmoji.ImageUrlType) {
                    "inlineContent$idx" to
                        InlineTextContent(
                            Placeholder(
                                width = placeholderSize,
                                height = placeholderSize,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            ),
                        ) {
                            AsyncImage(
                                model = value.url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp),
                            )
                        }
                } else {
                    null
                }
            }.associate { it.first to it.second }

    val annotatedText =
        remember(wordsInOrder, style) {
            buildAnnotatedString {
                wordsInOrder.forEachIndexed { idx, value ->
                    withStyle(style) {
                        when (value) {
                            is CustomEmoji.TextType -> append(value.text)
                            is CustomEmoji.ImageUrlType -> appendInlineContent("inlineContent$idx", "[icon]")
                            else -> {}
                        }
                    }
                }
            }
        }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        fontSize = fontSize,
        modifier = modifier,
    )
}
