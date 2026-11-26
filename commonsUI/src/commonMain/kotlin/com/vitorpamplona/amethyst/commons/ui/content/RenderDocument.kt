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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.BlockQuote
import com.halilibo.richtext.ui.FormattedList
import com.halilibo.richtext.ui.Heading
import com.halilibo.richtext.ui.HorizontalRule
import com.halilibo.richtext.ui.ListType
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.Table
import com.halilibo.richtext.ui.material3.RichText
import com.vitorpamplona.amethyst.commons.content.Block
import com.vitorpamplona.amethyst.commons.content.BlockContainer
import com.vitorpamplona.amethyst.commons.content.CachedDocuments
import com.vitorpamplona.amethyst.commons.content.Callout
import com.vitorpamplona.amethyst.commons.content.CodeBlock
import com.vitorpamplona.amethyst.commons.content.DefinitionList
import com.vitorpamplona.amethyst.commons.content.Div
import com.vitorpamplona.amethyst.commons.content.Document
import com.vitorpamplona.amethyst.commons.content.Footnotes
import com.vitorpamplona.amethyst.commons.content.Heading
import com.vitorpamplona.amethyst.commons.content.Image
import com.vitorpamplona.amethyst.commons.content.Inline
import com.vitorpamplona.amethyst.commons.content.ListBlock
import com.vitorpamplona.amethyst.commons.content.MathBlock
import com.vitorpamplona.amethyst.commons.content.NostrInline
import com.vitorpamplona.amethyst.commons.content.Paragraph
import com.vitorpamplona.amethyst.commons.content.Quote
import com.vitorpamplona.amethyst.commons.content.Table
import com.vitorpamplona.amethyst.commons.content.Text
import com.vitorpamplona.amethyst.commons.content.ThematicBreak
import com.vitorpamplona.amethyst.commons.content.markdown.MarkdownToDocument
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.callout_caution
import com.vitorpamplona.amethyst.commons.resources.callout_important
import com.vitorpamplona.amethyst.commons.resources.callout_note
import com.vitorpamplona.amethyst.commons.resources.callout_tip
import com.vitorpamplona.amethyst.commons.resources.callout_warning
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.ProvideZoomSourceGroup
import com.vitorpamplona.amethyst.commons.ui.markdown.LocalMarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.markdown.MarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.markdown.markdownAnchor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.MarkdownTextStyle
import com.vitorpamplona.amethyst.commons.ui.theme.markdownStyle
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlin.reflect.KClass

/**
 * What a document's renderers need from where it is shown. For Nostr content, [state] is the body
 * parsed the way a note is (the media a viewer pages through, the custom emoji), [segments] draws
 * its Nostr text as a note does, and [accountViewModel] and [nav] open what it links to. A document
 * that is not Nostr's ([RenderPlainMarkdown]) has none of them: its media are links.
 */
@Stable
class DocumentContext(
    val state: RichTextViewerState?,
    val canPreview: Boolean,
    val quotesLeft: Int,
    val callbackUri: String?,
    val accountViewModel: AccountViewModel?,
    val segments: RichTextSegmentRenderer?,
    val interactions: RichTextInteractions?,
    /** How far media on the document's top-level lines may run past its column. */
    val mediaBleed: Dp,
)

/** Draws a document's code blocks, where the screen showing it wants its own (a copy button). */
val LocalDocumentCodeBlock = staticCompositionLocalOf<(@Composable (CodeBlock) -> Unit)?> { null }

/** The color of inline code's chips, where the screen showing the document wants its own. */
val LocalDocumentChipColor = staticCompositionLocalOf<Color?> { null }

/**
 * Draws a block of a type [RenderBlock] does not know. Formats register theirs in
 * [DocumentRenderers]; a Djot `{.spreadsheet}` block, for one, arrives as a
 * [com.vitorpamplona.amethyst.commons.content.Div] that a renderer for its class can draw.
 */
fun interface BlockRenderer {
    @Composable
    fun RichTextScope.Render(
        block: Block,
        context: DocumentContext,
    )
}

/** The renderers formats add for their own blocks, by block type. */
object DocumentRenderers {
    val blocks: MutableMap<KClass<out Block>, BlockRenderer> = mutableMapOf()
}

/**
 * Markdown, parsed into the shared document model and drawn on every platform. The parse is cached
 * ([CachedDocuments]) and usually done ahead by the feed prefetcher.
 */
@Composable
fun RenderMarkdownDocument(
    content: String,
    tags: ImmutableListOfLists<String>?,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tagList = tags ?: EmptyTagList
    val document = remember(content, tagList, callbackUri) { CachedDocuments.markdown(content, tagList, callbackUri) }
    val state = remember(content, tagList, callbackUri) { CachedRichTextParser.parseText(content, tagList, callbackUri) }
    RenderDocument(document, state, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)
}

/** Draws [document]: blocks with compose-richtext's primitives, Nostr text with the note renderer's. */
@Composable
fun RenderDocument(
    document: Document,
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    callbackUri: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val platform = LocalRichTextPlatform.current
    val segments =
        remember(platform, accountViewModel, nav, backgroundColor, callbackUri, canPreview) {
            platform.segmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview)
        }
    val interactions =
        remember(nav) {
            RichTextInteractions(
                onClickHashtag = { nav.nav(Route.Hashtag(it.lowercase())) },
                onClickNip = { nav.nav(Route.Nip(it)) },
            )
        }
    val mediaBleed = LocalMediaBleed.current
    val context =
        remember(state, canPreview, quotesLeft, callbackUri, accountViewModel, segments, interactions, mediaBleed) {
            DocumentContext(state, canPreview, quotesLeft, callbackUri, accountViewModel, segments, interactions, mediaBleed)
        }

    val anchors = LocalMarkdownAnchors.current
    val uriHandler = LocalUriHandler.current
    val onClick = remember(uriHandler, accountViewModel, nav, anchors) { DocumentUriHandler(uriHandler, accountViewModel, nav, anchors) }

    CompositionLocalProvider(
        LocalUriHandler provides onClick,
        LocalRichTextSegmentRenderer provides segments,
        LocalRichTextInteractions provides interactions,
    ) {
        ProvideTextStyle(MarkdownTextStyle) {
            // One group for the document's media, so the viewer closes into whichever one it ends on.
            ProvideZoomSourceGroup {
                // Only media on the document's own top-level lines runs past the column (see TopLevel).
                ProvideMediaBleed(0.dp) {
                    RichText(modifier = modifier, style = MaterialTheme.colorScheme.markdownStyle) {
                        var headings = 0
                        document.blocks.forEach { block ->
                            if (block is Heading && anchors != null && block.anchor != null) {
                                Box(Modifier.markdownAnchor(anchors, block.anchor, headings++)) {
                                    RichText { RenderBlock(block, context, topLevel = true) }
                                }
                            } else {
                                RenderBlock(block, context, topLevel = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Markdown that is not Nostr content (an agent's reply, a README), drawn by the same renderer: no
 * Nostr pass, bare URLs as links, links handed to [onLinkClick]. [style] and [textStyle] replace the
 * article look; [fontScale] scales the text.
 */
@Composable
fun RenderPlainMarkdown(
    content: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f,
    style: RichTextStyle? = null,
    textStyle: TextStyle? = null,
) {
    val document = remember(content) { MarkdownToDocument(nostr = null).parse(content) }
    val context = remember { DocumentContext(null, false, 0, null, null, null, null, 0.dp) }
    val uriHandler = remember(onLinkClick) { PlainUriHandler(onLinkClick) }
    val density = LocalDensity.current
    val scaled = remember(fontScale, density) { if (fontScale == 1.0f) density else Density(density.density * fontScale, density.fontScale) }
    CompositionLocalProvider(LocalUriHandler provides uriHandler, LocalDensity provides scaled) {
        val render: @Composable () -> Unit = {
            RichText(modifier = modifier, style = style ?: RichTextStyle()) {
                document.blocks.forEach { RenderBlock(it, context, topLevel = true) }
            }
        }
        if (textStyle != null) ProvideTextStyle(textStyle, render) else render()
    }
}

/** Hands a link to the screen; only schemes that open something safe (web, mail). */
private class PlainUriHandler(
    val onLinkClick: (String) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (uri.substringBefore(':').lowercase() in PLAIN_SCHEMES) onLinkClick(uri)
    }
}

private val PLAIN_SCHEMES = setOf("http", "https", "mailto", "highlight")

/**
 * Opens a document's links: a section of the document being read when it is one (`#anchor`), a
 * screen of the app when the link names one (`nostr:`, a hashtag), the platform's handler otherwise.
 */
private class DocumentUriHandler(
    val platform: UriHandler,
    val accountViewModel: AccountViewModel,
    val nav: INav,
    val anchors: MarkdownAnchors?,
) : UriHandler {
    override fun openUri(uri: String) {
        val section = anchors?.anchorOfLink(uri)
        if (section != null && anchors.jumpTo(section)) return
        val route = uriToRoute(uri, accountViewModel.account)
        if (route != null) {
            nav.nav(route)
        } else {
            runCatching { platform.openUri(uri) }
        }
    }
}

@Composable
fun RichTextScope.RenderBlocks(
    blocks: List<Block>,
    context: DocumentContext,
) {
    blocks.forEach { RenderBlock(it, context, topLevel = false) }
}

/** One block. [topLevel] blocks are the document's own lines: only their media bleeds. */
@Composable
fun RichTextScope.RenderBlock(
    block: Block,
    context: DocumentContext,
    topLevel: Boolean = false,
) {
    when (block) {
        is Paragraph ->
            if (topLevel && context.mediaBleed > 0.dp && block.isMediaOnly()) {
                ProvideMediaBleed(context.mediaBleed) { RenderParagraph(block, context) }
            } else {
                RenderParagraph(block, context)
            }
        is Heading -> Heading(block.level) { RenderInlines(block.inlines, context) }
        is ListBlock -> RenderList(block, context)
        is Quote -> BlockQuote { RenderBlocks(block.blocks, context) }
        is CodeBlock -> LocalDocumentCodeBlock.current?.invoke(block) ?: RenderCodeBlock(block)
        is Table -> RenderTable(block, context)
        is ThematicBreak -> HorizontalRule()
        is MathBlock ->
            context.segments?.Equation(MathSegment("$$" + block.latex + "$$", block.latex, true), Modifier)
                ?: RenderCodeBlock(CodeBlock(block.latex, "latex"))
        is Callout -> RenderCallout(block, context)
        is DefinitionList -> RenderDefinitions(block, context)
        is Footnotes -> RenderFootnotes(block, context)
        else -> {
            val registered = DocumentRenderers.blocks[block::class]
            val calloutKind = (block as? Div)?.classes?.firstOrNull { it in CalloutKinds }
            if (registered != null) {
                with(registered) { Render(block, context) }
            } else if (block is Div && calloutKind != null) {
                // Djot's `::: warning`: a div named after a callout kind draws as one.
                RenderCallout(calloutKind, null, block.blocks, context)
            } else if (block is BlockContainer) {
                // A block this renderer does not know: its contents, so nothing is lost.
                RenderBlocks(block.blocks, context)
            }
        }
    }
}

/** A paragraph with nothing in it but media: a picture or a video on a line of its own. */
private fun Paragraph.isMediaOnly(): Boolean =
    inlines.isNotEmpty() &&
        inlines.all { inline ->
            inline is Image ||
                (inline is NostrInline && (inline.segment is ImageSegment || inline.segment is VideoSegment || inline.segment is LinkSegment)) ||
                (inline is Text && inline.text.isBlank())
        }

@Composable
private fun RichTextScope.RenderList(
    list: ListBlock,
    context: DocumentContext,
) {
    FormattedList(
        listType = if (list.ordered) ListType.Ordered else ListType.Unordered,
        items = list.items,
        startIndex = (list.start - 1).coerceAtLeast(0),
    ) { item ->
        val checked = item.checked
        if (checked != null) {
            Row {
                TaskBox(checked)
                Column { RenderBlocks(item.blocks, context) }
            }
        } else {
            RenderBlocks(item.blocks, context)
        }
    }
}

@Composable
private fun RichTextScope.RenderTable(
    table: Table,
    context: DocumentContext,
) {
    Table(
        headerRow =
            if (table.header.isEmpty()) {
                null
            } else {
                {
                    table.header.forEach { cell -> cell { RenderInlines(cell.inlines, context, bold = true) } }
                }
            },
    ) {
        table.rows.forEach { row ->
            row { row.forEach { cell -> cell { RenderInlines(cell.inlines, context) } } }
        }
    }
    table.caption?.let { caption -> RenderInlines(caption, context, italic = true) }
}

@Composable
private fun RichTextScope.RenderCallout(
    callout: Callout,
    context: DocumentContext,
) = RenderCallout(callout.kind, callout.title, callout.blocks, context)

/**
 * A callout drawn like GitHub's alerts, in the theme's own colors: a tinted panel with an accent
 * bar, the kind's name on top in the accent color, then the author's title and the blocks.
 */
@Composable
private fun RichTextScope.RenderCallout(
    kind: String,
    title: List<Inline>?,
    blocks: List<Block>,
    context: DocumentContext,
) {
    val accent = calloutAccent(kind)
    val label = calloutLabel(kind)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CalloutShape)
            .background(accent.copy(alpha = 0.10f))
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
            BasicRichText(Modifier.padding(top = 4.dp)) {
                title?.let { RenderInlines(it, context, bold = true) }
                RenderBlocks(blocks, context)
            }
        }
    }
}

private val CalloutShape = RoundedCornerShape(8.dp)

/** The kinds that GitHub, AsciiDoc and Djot authors use, all spelled in lower case. */
internal val CalloutKinds = setOf("note", "tip", "important", "warning", "caution")

@Composable
private fun calloutAccent(kind: String): Color =
    when (kind) {
        "tip" -> Color(0xFF3FB950)
        "important" -> Color(0xFFAB7DF8)
        "warning" -> Color(0xFFD29922)
        "caution", "danger", "error" -> MaterialTheme.colorScheme.error
        else -> Color(0xFF4493F8)
    }

@Composable
private fun calloutLabel(kind: String): String =
    when (kind) {
        "note" -> stringRes(Res.string.callout_note)
        "tip" -> stringRes(Res.string.callout_tip)
        "important" -> stringRes(Res.string.callout_important)
        "warning" -> stringRes(Res.string.callout_warning)
        "caution" -> stringRes(Res.string.callout_caution)
        else -> kind.replaceFirstChar { it.uppercaseChar() }
    }

@Composable
private fun RichTextScope.RenderDefinitions(
    list: DefinitionList,
    context: DocumentContext,
) {
    list.items.forEach { definition ->
        RenderInlines(definition.term, context, bold = true)
        Column(Modifier.padding(start = 16.dp)) {
            RichText { RenderBlocks(definition.definition, context) }
        }
    }
}

@Composable
private fun RichTextScope.RenderFootnotes(
    footnotes: Footnotes,
    context: DocumentContext,
) {
    HorizontalRule()
    FormattedList(listType = ListType.Ordered, items = footnotes.notes, startIndex = (footnotes.notes.firstOrNull()?.number ?: 1) - 1) { note ->
        RenderBlocks(note.blocks, context)
    }
}

internal val TaskWeight = FontWeight.Medium
