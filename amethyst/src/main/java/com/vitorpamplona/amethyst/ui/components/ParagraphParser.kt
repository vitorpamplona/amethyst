/**
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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.ParagraphState
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class RenderContext(
    val state: RichTextViewerState,
    val backgroundColor: MutableState<Color>,
    val quotesLeft: Int,
    val callbackUri: String?,
    val accountViewModel: AccountViewModel,
    val nav: INav,
)

data class ParagraphImageAnalysis(
    val imageCount: Int,
    val isImageOnly: Boolean,
    val hasMultipleImages: Boolean,
)

class ParagraphParser {
    fun analyzeParagraphImages(paragraph: ParagraphState): ParagraphImageAnalysis {
        var imageCount = 0
        var hasNonWhitespaceNonImageContent = false

        paragraph.words.forEach { word ->
            when (word) {
                is ImageSegment, is Base64Segment -> imageCount++
                is RegularTextSegment -> {
                    if (word.segmentText.isNotBlank()) {
                        hasNonWhitespaceNonImageContent = true
                    }
                }
                else -> hasNonWhitespaceNonImageContent = true // Links, emojis, etc.
            }
        }

        val isImageOnly = imageCount > 0 && !hasNonWhitespaceNonImageContent
        val hasMultipleImages = imageCount > 1

        return ParagraphImageAnalysis(
            imageCount = imageCount,
            isImageOnly = isImageOnly,
            hasMultipleImages = hasMultipleImages,
        )
    }

    fun collectConsecutiveImageParagraphs(
        paragraphs: ImmutableList<ParagraphState>,
        startIndex: Int,
    ): Pair<List<ParagraphState>, Int> {
        val imageParagraphs = mutableListOf<ParagraphState>()
        var j = startIndex

        while (j < paragraphs.size) {
            val currentParagraph = paragraphs[j]
            val words = currentParagraph.words

            // Fast path for empty check
            if (words.isEmpty()) {
                j++
                continue
            }

            // Check for single whitespace word
            if (words.size == 1) {
                val firstWord = words.first()
                if (firstWord is RegularTextSegment && firstWord.segmentText.isBlank()) {
                    j++
                    continue
                }
            }

            // Check if it's an image-only paragraph using unified analysis
            val analysis = analyzeParagraphImages(currentParagraph)
            if (analysis.isImageOnly) {
                imageParagraphs.add(currentParagraph)
                j++
            } else {
                break
            }
        }

        return imageParagraphs to j
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun processParagraph(
        paragraphs: ImmutableList<ParagraphState>,
        paragraphIndex: Int,
        spaceWidth: Dp,
        context: RenderContext,
        renderSingleParagraph: @Composable (ParagraphState, ImmutableList<Segment>, Dp, RenderContext) -> Unit,
        renderImageGallery: @Composable (ImmutableList<Segment>, RenderContext) -> Unit,
    ): Int {
        val paragraph = paragraphs[paragraphIndex]

        if (paragraph.words.isEmpty()) {
            // Empty paragraph - render normally with FlowRow (will render nothing)
            renderSingleParagraph(paragraph, paragraph.words.toImmutableList(), spaceWidth, context)
            return paragraphIndex + 1
        }

        val analysis = analyzeParagraphImages(paragraph)

        if (analysis.isImageOnly) {
            // Collect consecutive image-only paragraphs for gallery
            val (imageParagraphs, endIndex) = collectConsecutiveImageParagraphs(paragraphs, paragraphIndex)
            val allImageWords = imageParagraphs.flatMap { it.words }.toImmutableList()

            if (allImageWords.size > 1) {
                // Multiple images - render as gallery (no FlowRow wrapper needed)
                renderImageGallery(allImageWords, context)
            } else {
                // Single image - render with FlowRow wrapper
                renderSingleParagraph(paragraph, paragraph.words.toImmutableList(), spaceWidth, context)
            }

            return endIndex // Return next index to process
        } else if (analysis.hasMultipleImages) {
            // Mixed paragraph with multiple images - use renderImageGallery for smart grouping
            renderImageGallery(paragraph.words.toImmutableList(), context)
            return paragraphIndex + 1
        } else {
            // Regular paragraph (no images or single image) - render normally with FlowRow
            renderSingleParagraph(paragraph, paragraph.words.toImmutableList(), spaceWidth, context)
            return paragraphIndex + 1
        }
    }

    @Composable
    fun ProcessWordsWithImageGrouping(
        words: ImmutableList<Segment>,
        context: RenderContext,
        renderSingleWord: @Composable (Segment, RenderContext) -> Unit,
        renderGallery: @Composable (ImmutableList<MediaUrlImage>, AccountViewModel) -> Unit,
    ) {
        var i = 0
        val n = words.size

        while (i < n) {
            val word = words[i]

            if (word is ImageSegment || word is Base64Segment) {
                // Collect consecutive image/whitespace segments without extra list allocations
                val imageSegments = mutableListOf<Segment>()
                var j = i

                while (j < n) {
                    val seg = words[j]
                    when {
                        seg is ImageSegment || seg is Base64Segment -> imageSegments.add(seg)
                        seg is RegularTextSegment && seg.segmentText.isBlank() -> { /* skip whitespace */ }
                        else -> break
                    }
                    j++
                }

                if (imageSegments.size > 1) {
                    val imageContents =
                        imageSegments
                            .mapNotNull { segment ->
                                val imageUrl = segment.segmentText
                                context.state.imagesForPager[imageUrl] as? MediaUrlImage
                            }.toImmutableList()

                    if (imageContents.isNotEmpty()) {
                        renderGallery(imageContents, context.accountViewModel)
                    }
                } else {
                    renderSingleWord(imageSegments.firstOrNull() ?: word, context)
                }

                i = j // jump past processed run
            } else {
                renderSingleWord(word, context)
                i++
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun RenderSingleParagraphWithFlowRow(
        paragraph: ParagraphState,
        words: ImmutableList<Segment>,
        spaceWidth: Dp,
        context: RenderContext,
        renderWord: @Composable (Segment, RenderContext) -> Unit,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides
                if (paragraph.isRTL) {
                    LayoutDirection.Rtl
                } else {
                    LayoutDirection.Ltr
                },
            LocalTextStyle provides LocalTextStyle.current,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spaceWidth),
            ) {
                words.forEach { word ->
                    renderWord(word, context)
                }
            }
        }
    }

    @Composable
    fun ProcessAllParagraphs(
        paragraphs: ImmutableList<ParagraphState>,
        spaceWidth: Dp,
        context: RenderContext,
        renderSingleParagraph: @Composable (ParagraphState, ImmutableList<Segment>, Dp, RenderContext) -> Unit,
        renderImageGallery: @Composable (ImmutableList<Segment>, RenderContext) -> Unit,
    ) {
        var i = 0
        while (i < paragraphs.size) {
            i =
                processParagraph(
                    paragraphs = paragraphs,
                    paragraphIndex = i,
                    spaceWidth = spaceWidth,
                    context = context,
                    renderSingleParagraph = renderSingleParagraph,
                    renderImageGallery = renderImageGallery,
                )
        }
    }
}
