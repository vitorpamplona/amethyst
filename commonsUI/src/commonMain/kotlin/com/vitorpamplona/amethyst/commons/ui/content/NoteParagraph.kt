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
package com.vitorpamplona.amethyst.commons.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.halilibo.richtext.ui.material3.RichText
import com.vitorpamplona.amethyst.commons.content.Inline
import com.vitorpamplona.amethyst.commons.content.NostrInline
import com.vitorpamplona.amethyst.commons.content.Paragraph
import com.vitorpamplona.amethyst.commons.richtext.ParagraphState
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.theme.markdownStyle
import com.vitorpamplona.amethyst.commons.content.Text as TextInline

/**
 * How a note's plain paragraphs are laid out. With [useDocument], a paragraph is one text, its
 * mentions, emoji and media placed in it by the document renderer, instead of one composable per
 * word in a flow row. Set once at startup.
 */
object NoteTextLayout {
    var useDocument = false
}

/**
 * One paragraph of a note through the document renderer: its plain words as one run of text, and
 * what the parser found in it (mentions, hashtags, emoji, links, media) as the inlines a document
 * carries, drawn by the same segment renderer the word layout uses.
 */
@Composable
fun NoteParagraph(
    paragraph: ParagraphState,
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    mediaBleed: Dp,
    modifier: Modifier,
) {
    val segments = LocalRichTextSegmentRenderer.current
    val interactions = LocalRichTextInteractions.current
    val context =
        remember(state, canPreview, quotesLeft, segments, interactions, mediaBleed) {
            DocumentContext(state, canPreview, quotesLeft, null, null, segments, interactions, mediaBleed)
        }
    val document = remember(paragraph) { Paragraph(paragraph.toInlines()) }
    RichText(modifier = modifier, style = MaterialTheme.colorScheme.markdownStyle) {
        RenderParagraph(document, context)
    }
}

/** The paragraph's words as inlines: runs of plain words joined by the spaces between them. */
internal fun ParagraphState.toInlines(): List<Inline> {
    val out = ArrayList<Inline>(words.size)
    val run = StringBuilder()
    words.forEachIndexed { index, word ->
        if (index > 0) run.append(' ')
        if (word is RegularTextSegment) {
            run.append(word.segmentText)
        } else {
            if (run.isNotEmpty()) {
                out.add(TextInline(run.toString()))
                run.setLength(0)
            }
            out.add(NostrInline(word))
        }
    }
    if (run.isNotEmpty()) out.add(TextInline(run.toString()))
    return out
}
