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
package com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit

/*
 * PROTOTYPE ONLY. The two ways the prototypes draw a media marker inside the text field. Both are
 * OutputTransformations, the mechanism UrlUserTagOutputTransformation already uses to draw
 * `nostr:npub…` as `@Name`, so the raw text keeps the marker and the cursor treats the drawn
 * replacement as one unit (Backspace after it removes the whole marker).
 */

private const val NBSP = " "

/** Prototype A: the marker reads as an inline chip, `▣ Photo 1`, with its upload state. */
class MediaChipTransformation(
    private val composer: PrototypeComposer,
    private val chip: Color,
    private val onChip: Color,
    private val errorChip: Color,
    private val onErrorChip: Color,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val text = asCharSequence().toString()
        val matches = MediaTokens.REGEX.findAll(text).toList()
        if (matches.isEmpty()) return
        val drafts = matches.map { composer.draft(it.groupValues[1].toInt()) }
        val labels = matches.indices.map { i -> NBSP + chipLabel(i + 1, drafts[i]) + NBSP }

        for (i in matches.indices.reversed()) {
            replace(matches[i].range.first, matches[i].range.last + 1, labels[i])
        }
        var shift = 0
        matches.forEachIndexed { i, m ->
            val start = m.range.first + shift
            val failed = drafts[i]?.status is DraftStatus.Failed
            addStyle(
                SpanStyle(
                    background = if (failed) errorChip else chip,
                    color = if (failed) onErrorChip else onChip,
                    fontWeight = FontWeight.Medium,
                ),
                start,
                start + labels[i].length,
            )
            shift += labels[i].length - m.value.length
        }
    }

    private fun chipLabel(
        number: Int,
        draft: MediaDraft?,
    ): String {
        if (draft == null) return "▣ missing media"
        val name = if (draft.kind == DraftKind.VIDEO) "▶ Video $number" else "▣ Photo $number"
        val suffix =
            when (val s = draft.status) {
                is DraftStatus.Preparing -> " · preparing"
                is DraftStatus.Uploading -> " · ${(s.progress * 100).toInt()}%"
                is DraftStatus.Failed -> " · failed"
                is DraftStatus.Uploaded -> " ✓"
                DraftStatus.Local -> draft.trim?.let { " · " + formatDuration(it.lengthMs) } ?: ""
            }
        return name + suffix
    }
}

/**
 * Prototype B: the marker becomes one invisible character in a paragraph of its own whose line is
 * as tall as the media card, so the text flows around a hole the composer fills with the card
 * (see [blockTops]). The text keeps reading top to bottom exactly as the note will.
 */
class MediaBlockTransformation(
    private val blockHeight: TextUnit,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val text = asCharSequence().toString()
        val matches = MediaTokens.REGEX.findAll(text).toList()
        for (i in matches.indices.reversed()) {
            replace(matches[i].range.first, matches[i].range.last + 1, NBSP)
        }
        matches.forEachIndexed { i, m ->
            val start = visualOffset(matches, i)
            addStyle(
                ParagraphStyle(
                    lineHeight = blockHeight,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None),
                ),
                start,
                start + 1,
            )
            addStyle(SpanStyle(color = Color.Transparent), start, start + 1)
        }
    }
}

private fun visualOffset(
    matches: List<MatchResult>,
    index: Int,
): Int {
    var shift = 0
    for (i in 0 until index) shift += 1 - matches[i].value.length
    return matches[index].range.first + shift
}

/** Where each block's hole starts, in the text layout's coordinates, keyed by draft id. */
fun blockTops(
    rawText: CharSequence,
    layout: TextLayoutResult,
): List<Pair<Int, Float>> {
    val matches = MediaTokens.REGEX.findAll(rawText).toList()
    return matches.indices.mapNotNull { i ->
        val offset = visualOffset(matches, i)
        if (offset >= layout.layoutInput.text.length) return@mapNotNull null
        val line = layout.getLineForOffset(offset)
        matches[i].groupValues[1].toInt() to layout.getLineTop(line)
    }
}

/** The draft whose marker the collapsed cursor sits right before or after, if any. */
fun draftAtCursor(
    rawText: CharSequence,
    cursor: Int,
): Int? =
    MediaTokens.REGEX
        .findAll(rawText)
        .firstOrNull { cursor == it.range.first || cursor == it.range.last + 1 }
        ?.groupValues
        ?.get(1)
        ?.toInt()
