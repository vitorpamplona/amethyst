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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.ConcordInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.ParagraphState
import com.vitorpamplona.amethyst.commons.richtext.PdfSegment
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayGroupLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment

/**
 * Cross-platform rich-text renderer. Owns everything identical on every front
 * end — paragraph splitting, RTL, word layout, plain text, custom emoji, and
 * hashtags — and delegates every platform-divergent segment to the
 * [LocalRichTextSegmentRenderer] and universal activations to
 * [LocalRichTextInteractions]. See [RichTextSegmentRenderer] for the rationale.
 *
 * Callers pass an already-parsed [RichTextViewerState] (the parser is pure and
 * lives in `commons/richtext`), so this composable takes no account, navigation,
 * or cache handle of its own — those enter through the two CompositionLocals the
 * host provides.
 */
@Composable
fun RichTextViewer(
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalRichTextSegmentRenderer.current
    val baseStyle = LocalTextStyle.current
    val paragraphStyle = remember(baseStyle) { baseStyle.copy(lineHeight = 1.3.em) }

    Column(modifier) {
        state.paragraphs.forEach { paragraph ->
            val align = if (paragraph.isRTL) Alignment.End else Alignment.Start
            if (paragraph is ImageGalleryParagraph) {
                renderer.Gallery(paragraph, state, Modifier.align(align))
            } else {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (paragraph.isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalTextStyle provides paragraphStyle,
                ) {
                    RenderParagraph(paragraph, state, canPreview, quotesLeft, Modifier.align(align))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderParagraph(
    paragraph: ParagraphState,
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
) {
    val spaceWidth = measureSpaceWidth(LocalTextStyle.current)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spaceWidth),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        paragraph.words.forEach { word ->
            RenderWord(word, state, canPreview, quotesLeft)
        }
    }
}

@Composable
private fun RenderWord(
    word: Segment,
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
) {
    val renderer = LocalRichTextSegmentRenderer.current
    val actions = LocalRichTextInteractions.current

    when (word) {
        is RegularTextSegment -> Text(word.segmentText)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state.customEmoji)
        is HashTagSegment -> HashTagText(word) { actions.onClickHashtag(word.hashtag) }
        is EmailSegment -> ClickableSpan(word.segmentText) { actions.onOpenEmail(word.segmentText) }
        is PhoneSegment -> ClickableSpan(word.segmentText) { actions.onOpenPhone(word.segmentText) }

        // Divergent media — presentation and CTA are platform-owned.
        is ImageSegment, is VideoSegment, is PdfSegment, is Base64Segment, is BlossomUriSegment ->
            renderer.Media(word, state, Modifier)

        is MathSegment -> renderer.Equation(word, Modifier)

        is LinkSegment ->
            if (canPreview) {
                renderer.LinkPreview(word.segmentText, Modifier)
            } else {
                ClickableSpan(word.segmentText) { actions.onOpenUrl(word.segmentText) }
            }

        is SchemelessUrlSegment ->
            ClickableSpan(word.segmentText) { actions.onOpenUrl("https://${word.segmentText}") }
        is NowhereLinkSegment -> renderer.NowhereLink(word, canPreview, Modifier)

        is RelayUrlSegment, is RelayGroupLinkSegment, is ConcordInviteLinkSegment ->
            renderer.RelayLink(word, Modifier)

        is InvoiceSegment, is WithdrawSegment, is CashuSegment, is ClinkOfferSegment ->
            renderer.Payment(word, Modifier)

        is HashIndexUserSegment -> renderer.UserMention(word.hex, word.extras, Modifier)
        is HashIndexEventSegment -> renderer.QuotedEvent(word.hex, word.extras, canPreview, quotesLeft, Modifier)
        is BechSegment -> renderer.NostrEntity(word.segmentText, canPreview, quotesLeft, Modifier)
        is SecretEmoji -> renderer.SecretMessage(word, state, canPreview, quotesLeft, Modifier)

        // Unknown/other segments fall back to their raw text.
        else -> Text(word.segmentText)
    }
}

@Composable
private fun ClickableSpan(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private val HashtagIconPlaceholder =
    Placeholder(width = 17.sp, height = 17.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)

/**
 * A `#hashtag` chip in the theme's primary color, with the shared inline icon for
 * well-known tags (see [checkForHashtagWithIcon]). Any trailing punctuation glued
 * to the tag ([HashTagSegment.extras]) renders in the normal text color.
 */
@Composable
private fun HashTagText(
    segment: HashTagSegment,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground
    val hashtagIcon = remember(segment.hashtag) { checkForHashtagWithIcon(segment.hashtag) }

    val annotated =
        remember(segment.segmentText, primary, background) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = primary)) { append("#${segment.hashtag}") }
                if (hashtagIcon != null) {
                    withStyle(SpanStyle(color = primary)) { appendInlineContent("inlineContent", "[icon]") }
                }
                segment.extras?.let { withStyle(SpanStyle(color = background)) { append(it) } }
            }
        }

    Text(
        text = annotated,
        modifier = Modifier.clickable(onClick = onClick),
        inlineContent =
            if (hashtagIcon != null) {
                mapOf(
                    "inlineContent" to
                        InlineTextContent(HashtagIconPlaceholder) {
                            Icon(
                                imageVector = hashtagIcon.icon,
                                contentDescription = hashtagIcon.description,
                                tint = Color.Unspecified,
                                modifier = hashtagIcon.modifier,
                            )
                        },
                )
            } else {
                emptyMap()
            },
    )
}

/** Width of a single space in [textStyle], used to space FlowRow words. */
@Composable
fun measureSpaceWidth(textStyle: TextStyle): Dp {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(fontFamilyResolver, density, layoutDirection, textStyle) {
        val widthPx =
            TextMeasurer(fontFamilyResolver, density, layoutDirection, 1)
                .measure(" ", textStyle)
                .size
                .width
        with(density) { widthPx.toDp() }
    }
}
