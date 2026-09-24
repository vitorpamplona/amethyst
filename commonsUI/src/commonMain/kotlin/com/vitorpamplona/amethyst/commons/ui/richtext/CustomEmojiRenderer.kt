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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
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
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) {
    val placeholderSize =
        remember(fontSize) {
            if (fontSize == TextUnit.Unspecified) 22.sp else fontSize.times(1.1f)
        }

    // Remembered like annotatedText below: rebuilding this map handed Text a
    // fresh unstable Map (plus a Placeholder and lambda per emoji) on every
    // recomposition of every name/note with a custom emoji.
    val inlineContent =
        remember(wordsInOrder, placeholderSize) {
            wordsInOrder.buildInlineContent(placeholderSize)
        }

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
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

private fun ImmutableList<CustomEmoji.Renderable>.buildInlineContent(placeholderSize: TextUnit): Map<String, InlineTextContent> =
    mapIndexedNotNull { idx, value ->
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

@Composable
fun CustomEmojiChecker(
    text: String,
    tags: ImmutableListOfLists<String>?,
    onRegularText: @Composable (String) -> Unit,
    onEmojiText: @Composable (ImmutableList<CustomEmoji.Renderable>) -> Unit,
) {
    val mayContainEmoji by remember(text, tags) {
        mutableStateOf(CustomEmoji.fastMightContainEmoji(text, tags?.lists))
    }

    if (mayContainEmoji) {
        var emojiList by
            remember(text, tags) {
                mutableStateOf<ImmutableList<CustomEmoji.Renderable>?>(null)
            }

        LaunchedEffect(text, tags) {
            val newEmojiList = CustomEmoji.assembleAnnotatedList(text, tags?.lists)
            if (newEmojiList != null) {
                emojiList = newEmojiList
            }
        }

        emojiList?.let {
            onEmojiText(it)
        } ?: run {
            onRegularText(text)
        }
    } else {
        onRegularText(text)
    }
}

@Composable
fun CustomEmojiChecker(
    text: String,
    emojis: ImmutableMap<String, String>,
    onRegularText: @Composable (String) -> Unit,
    onEmojiText: @Composable (ImmutableList<CustomEmoji.Renderable>) -> Unit,
) {
    val mayContainEmoji by remember(text, emojis) {
        mutableStateOf(CustomEmoji.fastMightContainEmoji(text, emojis))
    }

    if (mayContainEmoji) {
        var emojiList by
            remember(text, emojis) {
                mutableStateOf<ImmutableList<CustomEmoji.Renderable>?>(null)
            }

        LaunchedEffect(text, emojis) {
            val newEmojiList = CustomEmoji.assembleAnnotatedList(text, emojis)
            if (newEmojiList != null) {
                emojiList = newEmojiList
            }
        }

        emojiList?.let {
            onEmojiText(it)
        } ?: run {
            onRegularText(text)
        }
    } else {
        onRegularText(text)
    }
}

@Composable
fun CreateTextWithEmoji(
    text: String,
    tags: ImmutableListOfLists<String>?,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) {
    CustomEmojiChecker(
        text,
        tags,
        onEmojiText = {
            val textColor =
                color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }
            val style =
                LocalTextStyle.current
                    .merge(
                        TextStyle(
                            color = textColor,
                            textAlign = TextAlign.Unspecified,
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                        ),
                    ).toSpanStyle()

            InLineIconRenderer(it, style, fontSize, maxLines, overflow, modifier)
        },
        onRegularText = {
            val textColor =
                color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }
            Text(
                text = it,
                color = textColor,
                textAlign = textAlign,
                fontWeight = fontWeight,
                fontSize = fontSize,
                maxLines = maxLines,
                overflow = overflow,
                modifier = modifier,
            )
        },
    )
}

@Composable
fun CreateTextWithEmoji(
    text: String,
    emojis: ImmutableMap<String, String>,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) {
    val textColor =
        color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }

    CustomEmojiChecker(
        text,
        emojis,
        onEmojiText = {
            val currentStyle = LocalTextStyle.current
            val style =
                remember(currentStyle) {
                    currentStyle
                        .merge(
                            TextStyle(
                                color = textColor,
                                textAlign = TextAlign.Unspecified,
                                fontWeight = fontWeight,
                                fontSize = fontSize,
                            ),
                        ).toSpanStyle()
                }

            InLineIconRenderer(it, style, fontSize, maxLines, overflow, modifier)
        },
        onRegularText = {
            Text(
                text = it,
                color = textColor,
                textAlign = textAlign,
                fontWeight = fontWeight,
                fontSize = fontSize,
                maxLines = maxLines,
                overflow = overflow,
                modifier = modifier,
            )
        },
    )
}

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    maxLines: Int = Int.MAX_VALUE,
    tags: ImmutableListOfLists<String>?,
    style: TextStyle,
    onClick: () -> Unit,
) {
    CustomEmojiChecker(
        text = clickablePart,
        tags = tags,
        onRegularText = {
            Text(
                text =
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable(
                                "me",
                                TextLinkStyles(style = style.toSpanStyle()),
                            ) {
                                onClick()
                            },
                        ) {
                            append(clickablePart)
                        }
                    },
                style = style,
                maxLines = maxLines,
            )
        },
        onEmojiText = {
            ClickableInLineIconRenderer(it, maxLines, style.toSpanStyle(), onClick = onClick)
        },
    )
}

@Composable
fun ClickableInLineIconRenderer(
    wordsInOrder: ImmutableList<CustomEmoji.Renderable>,
    maxLines: Int = Int.MAX_VALUE,
    style: SpanStyle,
    suffix: String? = null,
    nonClickableStype: SpanStyle? = null,
    onClick: () -> Unit,
) {
    val placeholderSize =
        remember(style) {
            if (style.fontSize == TextUnit.Unspecified) 22.sp else style.fontSize.times(1.1f)
        }

    val inlineContent =
        wordsInOrder
            .mapIndexedNotNull { idx, value ->
                if (value is CustomEmoji.ImageUrlType) {
                    Pair(
                        "inlineContent$idx",
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
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(1.dp),
                            )
                        },
                    )
                } else {
                    null
                }
            }.associate { it.first to it.second }

    val annotatedText =
        buildAnnotatedString {
            wordsInOrder.forEachIndexed { idx, value ->
                withLink(
                    LinkAnnotation.Clickable("link", TextLinkStyles(style)) {
                        onClick()
                    },
                ) {
                    if (value is CustomEmoji.TextType) {
                        append(value.text)
                    } else if (value is CustomEmoji.ImageUrlType) {
                        appendInlineContent("inlineContent$idx", "[icon]")
                    }
                }
            }

            if (suffix != null && nonClickableStype != null) {
                withStyle(nonClickableStype) {
                    append(suffix)
                }
            }
        }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        maxLines = maxLines,
    )
}
