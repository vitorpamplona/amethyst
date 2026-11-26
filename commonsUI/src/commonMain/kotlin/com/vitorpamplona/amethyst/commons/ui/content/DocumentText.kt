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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.Text
import com.halilibo.richtext.ui.string.withFormat
import com.vitorpamplona.amethyst.commons.content.Code
import com.vitorpamplona.amethyst.commons.content.CodeBlock
import com.vitorpamplona.amethyst.commons.content.Emphasis
import com.vitorpamplona.amethyst.commons.content.FootnoteReference
import com.vitorpamplona.amethyst.commons.content.HardBreak
import com.vitorpamplona.amethyst.commons.content.Highlight
import com.vitorpamplona.amethyst.commons.content.Image
import com.vitorpamplona.amethyst.commons.content.Inline
import com.vitorpamplona.amethyst.commons.content.InlineContainer
import com.vitorpamplona.amethyst.commons.content.Insert
import com.vitorpamplona.amethyst.commons.content.Link
import com.vitorpamplona.amethyst.commons.content.Math
import com.vitorpamplona.amethyst.commons.content.NostrInline
import com.vitorpamplona.amethyst.commons.content.Paragraph
import com.vitorpamplona.amethyst.commons.content.SoftBreak
import com.vitorpamplona.amethyst.commons.content.Strikethrough
import com.vitorpamplona.amethyst.commons.content.Strong
import com.vitorpamplona.amethyst.commons.content.Subscript
import com.vitorpamplona.amethyst.commons.content.Superscript
import com.vitorpamplona.amethyst.commons.model.navigation.uri.fragmentHashtagOrNull
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.BuzzInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.ConcordInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.NipMentionSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.PdfSegment
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayGroupLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.TimestampSegment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WebxdcSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.commons.ui.components.BleedingMedia
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.note.WebxdcPostCard
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalTimestampText
import com.vitorpamplona.amethyst.commons.ui.richtext.RenderCustomEmoji
import com.vitorpamplona.amethyst.commons.ui.richtext.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.commons.ui.theme.Font17SP
import com.vitorpamplona.amethyst.commons.ui.theme.MarkdownTextStyle
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.markdownStyle
import com.vitorpamplona.quartz.nip92IMeta.imetasByUrl
import androidx.compose.material3.Text as MaterialText
import com.vitorpamplona.amethyst.commons.content.Text as TextInline

/** A paragraph: its text, with inline code drawn as rounded chips. */
@Composable
fun RichTextScope.RenderParagraph(
    paragraph: Paragraph,
    context: DocumentContext,
) {
    RenderInlines(paragraph.inlines, context)
}

/**
 * Inline text (a paragraph, a heading, a table cell, a term), with inline code drawn as rounded
 * chips: a translucent tint with a little room on each side, the same in an article and in the
 * coding-agent chat. [LocalDocumentChipColor] overrides the tint; a transparent one means no chips.
 */
@Composable
fun RichTextScope.RenderInlines(
    inlines: List<Inline>,
    context: DocumentContext,
    bold: Boolean = false,
    italic: Boolean = false,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val chip = LocalDocumentChipColor.current ?: colors.onSurface.copy(alpha = if (colors.isLight) 0.06f else 0.12f)
    val highlight = colors.primary.copy(alpha = 0.22f)
    // A transparent chip color (the chat's `chips = false`) means no chips, and so no room for them.
    val pad = if (chip.alpha > 0f) with(density) { ChipSide.toSp() } else null
    val built = remember(inlines, context, pad, highlight, bold, italic) { buildText(inlines, context, pad, highlight, bold, italic) }
    if (built.codeRuns.isEmpty()) {
        Text(text = built.text)
        return
    }
    // Worked out once per layout, so drawing (on every frame that redraws) only paints.
    var chips by remember(built) { mutableStateOf(emptyList<Rect>()) }
    val onLayout =
        remember(built, density) {
            { layout: TextLayoutResult ->
                chips = with(density) { chipRects(layout, built.codeRuns, ChipSide.toPx(), ChipInset.toPx()) }
            }
        }
    val drawChips =
        remember(built, density, chip) {
            val radius = with(density) { CornerRadius(ChipCorner.toPx()) }
            Modifier.drawBehind { chips.forEach { drawRoundRect(chip, it.topLeft, it.size, radius) } }
        }
    Text(text = built.text, modifier = drawChips, onTextLayout = onLayout)
}

/** A code block. JSON-like code gets the highlighter's colors; all code keeps its lines and scrolls sideways. */
@Composable
fun RenderCodeBlock(block: CodeBlock) {
    val style = MaterialTheme.colorScheme.markdownStyle.codeBlockStyle
    CodeBlockBody(
        code = block.code,
        language = block.language,
        textStyle = style?.textStyle ?: MarkdownTextStyle,
        wrap = false,
        modifier = style?.modifier ?: Modifier,
    )
}

/** The box in front of a task list's item. */
@Composable
fun TaskBox(checked: Boolean) {
    MaterialText(
        text = if (checked) "☑" else "☐",
        fontWeight = TaskWeight,
        modifier = Modifier.padding(end = 6.dp),
    )
}

// ---- Building the text --------------------------------------------------------------------------

private val ChipSide = 4.dp
private val ChipInset = 1.5.dp
private val ChipCorner = 4.dp

private class BuiltText(
    val text: RichTextString,
    val codeRuns: List<CodeRun>,
)

/** A code run's characters, and whether letter spacing made room for the chip before it. */
private class CodeRun(
    val range: IntRange,
    val roomBefore: Boolean,
)

private fun buildText(
    inlines: List<Inline>,
    context: DocumentContext,
    chipPadding: TextUnit?,
    highlight: Color,
    bold: Boolean = false,
    italic: Boolean = false,
): BuiltText {
    val builder = RichTextString.Builder()
    val bolded = if (bold) builder.pushFormat(RichTextString.Format.Bold) else null
    val italicized = if (italic) builder.pushFormat(RichTextString.Format.Italic) else null
    val text = TextBuilder(builder, context, chipPadding, highlight)
    text.inlines(inlines)
    italicized?.let { builder.pop(it) }
    bolded?.let { builder.pop(it) }
    return BuiltText(builder.toRichTextString(), text.codeRuns)
}

/**
 * Appends inlines to a [RichTextString]. With a [chipPadding], inline code gets room for a chip
 * on its sides (letter spacing on the character before it and on its last character, so copied
 * text gains nothing) and its runs are recorded; without one it is plain monospace.
 */
private class TextBuilder(
    val builder: RichTextString.Builder,
    val context: DocumentContext,
    val chipPadding: TextUnit?,
    val highlight: Color,
) {
    val codeRuns = mutableListOf<CodeRun>()

    // The last character appended, while it is known: inline content has none.
    private var last: Char? = null

    private fun append(text: String) {
        if (text.isEmpty()) return
        builder.append(text)
        last = text.last()
    }

    private fun length() = builder.withAnnotatedString { length }

    fun inlines(list: List<Inline>) = list.forEach { inline(it) }

    private fun inline(inline: Inline) {
        when (inline) {
            is TextInline -> append(inline.text)
            is SoftBreak -> append(" ")
            is HardBreak -> append("\n")
            is Emphasis -> builder.withFormat(RichTextString.Format.Italic) { inlines(inline.children) }
            is Strong -> builder.withFormat(RichTextString.Format.Bold) { inlines(inline.children) }
            is Strikethrough -> builder.withFormat(RichTextString.Format.Strikethrough) { inlines(inline.children) }
            is Superscript -> builder.withFormat(RichTextString.Format.Superscript) { inlines(inline.children) }
            is Subscript -> builder.withFormat(RichTextString.Format.Subscript) { inlines(inline.children) }
            is Insert -> builder.withFormat(RichTextString.Format.Underline) { inlines(inline.children) }
            is Highlight -> {
                val style = builder.withAnnotatedString { pushStyle(SpanStyle(background = highlight)) }
                inlines(inline.children)
                builder.withAnnotatedString { pop(style) }
            }
            is Code -> code(inline.code)
            is Link -> link(inline)
            is Image -> image(inline)
            is Math ->
                if (inline.display) {
                    equation(MathSegment("$$" + inline.latex + "$$", inline.latex, true))
                } else {
                    equation(MathSegment("$" + inline.latex + "$", inline.latex, false))
                }
            is FootnoteReference -> builder.withFormat(RichTextString.Format.Superscript) { append(inline.number.toString()) }
            is NostrInline -> nostr(inline.segment)
            // An inline this renderer does not know: its children, so no text is lost.
            is InlineContainer -> inlines(inline.children)
            else -> {}
        }
    }

    private fun code(code: String) {
        if (code.isEmpty()) return
        val padding = chipPadding
        if (padding == null) {
            builder.withFormat(RichTextString.Format.Code) { append(code) }
            return
        }
        val room = SpanStyle(letterSpacing = padding)
        val start = length()
        val roomBefore = last?.takesRoom() == true
        if (roomBefore) builder.withAnnotatedString { addStyle(room, start - 1, start) }
        builder.withFormat(RichTextString.Format.Code) { append(code) }
        val end = start + code.length
        if (code.last().takesRoom()) builder.withAnnotatedString { addStyle(room, end - 1, end) }
        last = code.last()
        codeRuns.add(CodeRun(start until end, roomBefore))
    }

    private fun link(link: Link) {
        val label = (link.children.singleOrNull() as? TextInline)?.text
        // `[#tag](#tag)`: the hashtag's feed.
        val hashtag = fragmentHashtagOrNull(link.destination)
        when {
            hashtag != null -> linked("nostr:hashtag?id=$hashtag") { inlines(link.children) }
            // A link whose text is its own address previews like a bare one.
            label == link.destination && context.canPreview && link.destination.startsWith("http") -> linkPreview(link.destination)
            else -> linked(link.destination) { inlines(link.children) }
        }
    }

    private inline fun linked(
        destination: String,
        content: () -> Unit,
    ) {
        val index = builder.pushFormat(RichTextString.Format.Link(destination = destination))
        content()
        builder.pop(index)
    }

    private fun image(image: Image) {
        val state = context.state
        val accountViewModel = context.accountViewModel
        if (!context.canPreview || state == null || accountViewModel == null) {
            linked(image.url) { append(image.alt.ifBlank { image.url }) }
            return
        }
        val description = image.alt.ifBlank { image.title } ?: image.title
        // The note's own parse already knows most images; only one it missed reads the tags.
        val media =
            state.mediaForPager[image.url]
                ?: RichTextParser().createMediaContent(fullUrl = image.url, eventTags = state.tags.lists.imetasByUrl(), description = description, callbackUri = context.callbackUri)
                ?: MediaUrlImage(url = image.url, description = description)
        fullWidth {
            val bleed = LocalMediaBleed.current
            ProvideMediaBleed(bleed) {
                BleedingMedia(enabled = true) { roundedCorner ->
                    ZoomableContentView(
                        content = media,
                        images = state.mediaList,
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.FillWidth,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }

    private fun linkPreview(url: String) {
        val segments = context.segments ?: return linked(url) { append(url) }
        fullWidth { segments.LinkPreview(url, Modifier) }
    }

    private fun equation(segment: MathSegment) {
        val segments = context.segments ?: return append(segment.segmentText)
        if (segment.displayMode) {
            fullWidth { segments.Equation(segment, Modifier) }
        } else {
            inlineSized { segments.Equation(segment, Modifier) }
        }
    }

    /** What Nostr text carries, drawn as a note draws it (see RichTextViewer's word renderer). */
    private fun nostr(segment: Segment) {
        val segments = context.segments ?: return append(segment.segmentText)
        val state = context.state ?: return append(segment.segmentText)
        val interactions = context.interactions ?: return append(segment.segmentText)
        when (segment) {
            is HashTagSegment -> {
                linkedWithListener(segment.hashtag.let { "#$it" }) { interactions.onClickHashtag(segment.hashtag) }
                checkForHashtagWithIcon(segment.hashtag)?.let { icon ->
                    inlineSized {
                        Box(Modifier.size(17.dp)) {
                            Icon(imageVector = icon.icon, contentDescription = icon.description, tint = Color.Unspecified, modifier = icon.modifier)
                        }
                    }
                }
                segment.extras?.let { append(it) }
            }
            is NipMentionSegment -> {
                val extras = segment.extras
                val mention = if (extras != null) segment.segmentText.removeSuffix(extras) else segment.segmentText
                linkedWithListener(mention) { interactions.onClickNip(segment.number) }
                extras?.let { append(it) }
            }
            is EmailSegment -> linked("mailto:" + segment.segmentText) { append(segment.segmentText) }
            is PhoneSegment -> linked("tel:" + segment.segmentText) { append(segment.segmentText) }
            is SchemelessUrlSegment -> linked("https://" + segment.segmentText) { append(segment.segmentText) }
            is LinkSegment ->
                if (context.canPreview) {
                    linkPreview(segment.segmentText)
                } else {
                    linked(segment.segmentText) { append(segment.segmentText) }
                }
            is ImageSegment, is VideoSegment, is PdfSegment, is Base64Segment, is BlossomUriSegment ->
                if (context.canPreview) {
                    fullWidth {
                        ProvideMediaBleed(LocalMediaBleed.current) { segments.Media(segment, state, Modifier) }
                    }
                } else {
                    linked(segment.segmentText) { append(segment.segmentText) }
                }
            is EmojiSegment -> inlineSized { RenderCustomEmoji(segment.segmentText, state.customEmoji) }
            is MathSegment -> equation(segment)
            is BechSegment ->
                if (segment.isUser() || !context.canPreview || context.quotesLeft <= 0) {
                    inlineFit { segments.NostrEntity(segment.segmentText, context.canPreview, context.quotesLeft, Modifier) }
                } else {
                    fullWidth { segments.NostrEntity(segment.segmentText, context.canPreview, context.quotesLeft, Modifier) }
                }
            is HashIndexUserSegment -> inlineFit { segments.UserMention(segment.hex, segment.extras, Modifier) }
            is HashIndexEventSegment -> fullWidth { segments.QuotedEvent(segment.hex, segment.extras, context.canPreview, context.quotesLeft, Modifier) }
            is InvoiceSegment, is CashuSegment, is ClinkOfferSegment, is WithdrawSegment ->
                fullWidth { segments.Payment(segment, Modifier) }
            is RelayUrlSegment, is RelayGroupLinkSegment, is ConcordInviteLinkSegment, is BuzzInviteLinkSegment ->
                inlineFit { segments.RelayLink(segment, Modifier) }
            is NowhereLinkSegment -> fullWidth { segments.NowhereLink(segment, context.canPreview, Modifier) }
            is WebxdcSegment -> fullWidth { WebxdcPostCard(segment.app, segment.postUri, null) }
            is TimestampSegment -> inlineFit { LocalTimestampText(segment) }
            is SecretEmoji -> fullWidth { segments.SecretMessage(segment, state, context.canPreview, context.quotesLeft, Modifier) }
            else -> append(segment.segmentText)
        }
    }

    private fun linkedWithListener(
        text: String,
        onClick: () -> Unit,
    ) {
        val listener = LinkInteractionListener { onClick() }
        val index = builder.pushFormat(RichTextString.Format.Link(destination = text, linkInteractionListener = listener))
        append(text)
        builder.pop(index)
    }

    /** A card on its own line, as wide as the text. */
    private fun fullWidth(content: @Composable () -> Unit) {
        last = null
        builder.appendInlineContentFullWidth(
            content =
                InlineContent(
                    initialSize = { IntSize(Font17SP.roundToPx(), Font17SP.roundToPx()) },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ) { content() },
        )
    }

    /** Something the size of a letter: an emoji, an icon. */
    private fun inlineSized(content: @Composable () -> Unit) {
        last = null
        builder.appendInlineContent(
            content =
                InlineContent(
                    initialSize = { IntSize(Font17SP.roundToPx(), Font17SP.roundToPx()) },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ) { content() },
        )
    }

    /** Something in the line that measures itself: a user's name, a relay chip. */
    private fun inlineFit(content: @Composable () -> Unit) {
        last = null
        builder.appendInlineContent(
            content =
                InlineContent(
                    initialSize = { IntSize(Font17SP.roundToPx() * 4, Font17SP.roundToPx()) },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ) { content() },
        )
    }
}

private fun BechSegment.isUser(): Boolean {
    val bech = segmentText.removePrefix("nostr:").removePrefix("@").lowercase()
    return bech.startsWith("npub1") || bech.startsWith("nprofile1")
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
            val lastBox = layout.getBoundingBox(to)
            // min/max: in right-to-left text the run's first box is the rightmost. A side with no
            // room stays flush with the letters instead of sliding under the glyph next to it.
            val left = minOf(first.left, lastBox.left) - if (from == run.first && !code.roomBefore) 0f else pad
            val right = maxOf(first.right, lastBox.right) + if (to == run.last) 0f else pad
            rects.add(Rect(left, layout.getLineTop(line) + inset, right, layout.getLineBottom(line) - inset))
        }
    }
    return rects
}

/**
 * Whether letter spacing can go on this character alone. A span on half of a surrogate pair, or
 * on a combining mark, ZWJ or variation selector, would cut a cluster (an emoji, an accented
 * letter) in two, so those get no room.
 */
private fun Char.takesRoom(): Boolean = !isSurrogate() && category !in CLUSTER_PARTS

private val CLUSTER_PARTS = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.FORMAT)
