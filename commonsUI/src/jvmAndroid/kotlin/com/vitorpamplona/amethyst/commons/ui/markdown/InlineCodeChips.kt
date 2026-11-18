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
package com.vitorpamplona.amethyst.commons.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.DefaultMediaRenderer
import com.halilibo.richtext.markdown.node.AstBlockNodeType
import com.halilibo.richtext.markdown.node.AstCode
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstHardLineBreak
import com.halilibo.richtext.markdown.node.AstHashtag
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstLinkReferenceDefinition
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstNostrUri
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstSoftLineBreak
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstText
import com.halilibo.richtext.ui.LocalOnUriCompose
import com.halilibo.richtext.ui.MediaRenderer
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.Text
import com.halilibo.richtext.ui.string.withFormat

/**
 * Draws a paragraph's inline `code` as a rounded chip, where the library can only paint a span
 * background: a square slab the full height of the line, flush against the letters around it.
 *
 * The library hands a paragraph to [AstBlockNodeComposer]s, so this one builds the paragraph's
 * string the way the library's own (internal) `MarkdownRichText` does, keeps where each code run
 * starts and ends, and renders it through the library's public `Text`, which reports the layout
 * the chips are drawn from. Room for the chip's sides comes from letter spacing on the character
 * before the run and on its last character, so the copied text gains nothing.
 *
 * Set the code [SpanStyle] without a background, or the slab still draws under the chip.
 * Headings and table cells keep the library's rendering: monospace, no chip.
 */
class InlineCodeChips(
    private val color: Color,
    private val sidePadding: Dp = 4.dp,
    private val verticalInset: Dp = 1.5.dp,
    private val cornerRadius: Dp = 4.dp,
) : AstBlockNodeComposer {
    override fun predicate(astBlockNodeType: AstBlockNodeType): Boolean = astBlockNodeType is AstParagraph

    @Composable
    override fun RichTextScope.Compose(
        astNode: AstNode,
        visitChildren: @Composable (AstNode) -> Unit,
    ) {
        val renderer = LocalOnUriCompose.current ?: DefaultMediaRenderer
        val density = LocalDensity.current
        val padding = with(density) { sidePadding.toSp() }
        val paragraph = remember(astNode, renderer, padding) { buildParagraph(astNode, renderer, padding) }
        if (paragraph.codeRuns.isEmpty()) {
            Text(text = paragraph.text)
            return
        }
        // Worked out once per layout, so drawing (on every frame that redraws) only paints.
        var chips by remember(paragraph) { mutableStateOf(emptyList<Rect>()) }
        val onLayout =
            remember(paragraph, density) {
                { layout: TextLayoutResult ->
                    chips = with(density) { chipRects(layout, paragraph.codeRuns, sidePadding.toPx(), verticalInset.toPx()) }
                }
            }
        val drawChips =
            remember(paragraph, density) {
                val radius = with(density) { CornerRadius(cornerRadius.toPx()) }
                Modifier.drawBehind { chips.forEach { drawRoundRect(color, it.topLeft, it.size, radius) } }
            }
        Text(text = paragraph.text, modifier = drawChips, onTextLayout = onLayout)
    }
}

/** One rect per line each code run covers, [pad] wider on the sides the letter spacing made room on. */
private fun chipRects(
    layout: TextLayoutResult,
    runs: List<CodeRun>,
    pad: Float,
    inset: Float,
): List<Rect> {
    val length = layout.layoutInput.text.length
    val rects = ArrayList<Rect>(runs.size)
    runs.forEach { code ->
        val run = code.range
        if (run.last >= length) return@forEach
        for (line in layout.getLineForOffset(run.first)..layout.getLineForOffset(run.last)) {
            val from = maxOf(run.first, layout.getLineStart(line))
            val to = minOf(run.last, layout.getLineEnd(line, visibleEnd = true) - 1)
            if (to < from) continue
            val first = layout.getBoundingBox(from)
            val last = layout.getBoundingBox(to)
            // min/max: in right-to-left text the run's first box is the rightmost. A side with no
            // room stays flush with the letters instead of sliding under the glyph next to it; the
            // run's last box already holds the closing side's room, when it has some.
            val left = minOf(first.left, last.left) - if (from == run.first && !code.roomBefore) 0f else pad
            val right = maxOf(first.right, last.right) + if (to == run.last) 0f else pad
            rects.add(Rect(left, layout.getLineTop(line) + inset, right, layout.getLineBottom(line) - inset))
        }
    }
    return rects
}

private class ChipParagraph(
    val text: RichTextString,
    val codeRuns: List<CodeRun>,
)

/** A code run's characters, and whether letter spacing made room for the chip before it. */
private class CodeRun(
    val range: IntRange,
    val roomBefore: Boolean,
)

/** The paragraph's string, as `MarkdownRichText` builds it, plus where each code run sits. */
private fun buildParagraph(
    paragraph: AstNode,
    renderer: MediaRenderer,
    padding: TextUnit,
): ChipParagraph {
    val builder = RichTextString.Builder()
    val runs = mutableListOf<CodeRun>()
    val room = SpanStyle(letterSpacing = padding)

    // The last character appended, while it is known: the media renderer appends its own.
    var last: Char? = null

    fun appendText(text: String) {
        if (text.isEmpty()) return
        builder.append(text)
        last = text.last()
    }

    fun visit(node: AstNode) {
        var format: Int? = null
        when (val type = node.type) {
            is AstCode -> {
                val literal = type.literal
                if (literal.isEmpty()) return
                val start = builder.withAnnotatedString { length }
                val roomBefore = last?.takesRoom() == true
                if (roomBefore) builder.withAnnotatedString { addStyle(room, start - 1, start) }
                builder.withFormat(RichTextString.Format.Code) { append(literal) }
                val end = start + literal.length
                if (literal.last().takesRoom()) builder.withAnnotatedString { addStyle(room, end - 1, end) }
                last = literal.last()
                runs.add(CodeRun(start until end, roomBefore))
                return
            }
            is AstText -> {
                val label = if (renderer.shouldSanitizeUriLabel() && node.links.parent?.type is AstLink) renderer.sanitizeUriLabel(type.literal) else type.literal
                appendText(label)
                return
            }
            is AstImage -> {
                last = null
                return renderer.renderImage(type.title, type.destination, builder)
            }
            is AstNostrUri -> {
                last = null
                return renderer.renderNostrUri(type.destination, builder)
            }
            is AstHashtag -> {
                last = null
                return renderer.renderHashtag(type.tag, builder)
            }
            AstSoftLineBreak -> return appendText(" ")
            AstHardLineBreak -> return appendText("\n")
            is AstEmphasis -> format = builder.pushFormat(RichTextString.Format.Italic)
            is AstStrikethrough -> format = builder.pushFormat(RichTextString.Format.Strikethrough)
            is AstStrongEmphasis -> format = builder.pushFormat(RichTextString.Format.Bold)
            is AstLinkReferenceDefinition -> format = builder.pushFormat(RichTextString.Format.Link(destination = type.destination))
            is AstLink -> {
                val title = type.title ?: node.children().joinToString { (it.type as? AstText)?.literal ?: "" }
                if (renderer.shouldRenderLinkPreview(title, type.destination)) {
                    last = null
                    return renderer.renderLinkPreview(title, type.destination, builder)
                }
                format = builder.pushFormat(RichTextString.Format.Link(destination = type.destination))
            }
            else -> {}
        }
        node.children().forEach(::visit)
        format?.let(builder::pop)
    }

    paragraph.children().forEach(::visit)
    return ChipParagraph(builder.toRichTextString(), runs)
}

private fun AstNode.children(): Sequence<AstNode> = generateSequence(links.firstChild) { it.links.next }

/**
 * Whether letter spacing can go on this character alone. A span on half of a surrogate pair, or
 * on a combining mark, ZWJ or variation selector, would cut a cluster (an emoji, an accented
 * letter) in two, so those get no room; a letter, digit, ideograph, space or punctuation does.
 */
private fun Char.takesRoom(): Boolean = !isSurrogate() && category !in CLUSTER_PARTS

private val CLUSTER_PARTS = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.FORMAT)
