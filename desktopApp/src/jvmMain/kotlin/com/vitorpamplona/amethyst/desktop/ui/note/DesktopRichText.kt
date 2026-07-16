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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.PdfSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.commons.ui.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextViewer as CommonsRichTextViewer

data class RichTextCallbacks(
    val onMentionClick: ((String) -> Unit)? = null,
    val onHashtagClick: ((String) -> Unit)? = null,
    val onNavigateToThread: ((String) -> Unit)? = null,
    val onImageClick: ((List<String>, Int) -> Unit)? = null,
    val onPayInvoice: ((String) -> Unit)? = null,
)

/**
 * Renders Nostr rich text on Desktop through the shared cross-platform
 * [CommonsRichTextViewer] core. Markdown stays on the native [RenderMarkdown]
 * path; plain rich text is dispatched by the shared core, with every
 * platform-divergent segment drawn mouse-first by [DesktopRichTextSegmentRenderer].
 *
 * Replaces the former hand-rolled `DesktopRichTextViewer` switchboard so Desktop
 * and Android render the same segment model from one place.
 */
@Composable
fun DesktopRichText(
    content: String,
    state: RichTextViewerState,
    localCache: DesktopLocalCache? = null,
    callbacks: RichTextCallbacks = RichTextCallbacks(),
    modifier: Modifier = Modifier,
) {
    if (CachedRichTextParser.isMarkdown(content)) {
        RenderMarkdown(
            content = content,
            onLinkClick = { url -> handleDesktopLinkClick(url, callbacks) },
            modifier = modifier,
        )
        return
    }

    val renderer = remember(localCache, callbacks) { DesktopRichTextSegmentRenderer(localCache, callbacks) }
    val interactions =
        remember(callbacks) {
            RichTextInteractions(
                onOpenUrl = {
                    runCatching {
                        java.awt.Desktop
                            .getDesktop()
                            .browse(URI(it))
                    }
                },
                onOpenEmail = {
                    runCatching {
                        java.awt.Desktop
                            .getDesktop()
                            .browse(URI("mailto:$it"))
                    }
                },
                onOpenPhone = { },
                onClickHashtag = { callbacks.onHashtagClick?.invoke(it) },
            )
        }

    CompositionLocalProvider(
        LocalRichTextSegmentRenderer provides renderer,
        LocalRichTextInteractions provides interactions,
    ) {
        CommonsRichTextViewer(
            state = state,
            canPreview = true,
            quotesLeft = 1,
            modifier = modifier,
        )
    }
}

private fun handleDesktopLinkClick(
    url: String,
    callbacks: RichTextCallbacks,
) {
    when {
        url.startsWith("nostr:") -> {
            val parsed = Nip19Parser.uriToRoute(url)
            when (val entity = parsed?.entity) {
                is NPub -> callbacks.onMentionClick?.invoke(entity.hex)
                is NProfile -> callbacks.onMentionClick?.invoke(entity.hex)
                is NNote -> callbacks.onNavigateToThread?.invoke(entity.hex)
                is NEvent -> callbacks.onNavigateToThread?.invoke(entity.hex)
                else -> {}
            }
        }

        else -> {
            runCatching {
                java.awt.Desktop
                    .getDesktop()
                    .browse(URI(url))
            }
        }
    }
}

/**
 * Desktop (mouse-first) implementation of the shared [RichTextSegmentRenderer].
 * Each method draws a segment with the existing Desktop leaf composables in this
 * file (and `NoteCard`), so Desktop keeps its own presentation and interaction
 * while sharing the parse + dispatch with Android.
 */
class DesktopRichTextSegmentRenderer(
    private val localCache: DesktopLocalCache?,
    private val callbacks: RichTextCallbacks,
) : RichTextSegmentRenderer {
    @Composable
    override fun Media(
        segment: Segment,
        state: RichTextViewerState,
        modifier: Modifier,
    ) {
        when (segment) {
            is ImageSegment, is Base64Segment ->
                AsyncImage(
                    model = segment.segmentText,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (callbacks.onImageClick != null) {
                                    Modifier.clickable { callbacks.onImageClick.invoke(listOf(segment.segmentText), 0) }
                                } else {
                                    Modifier
                                },
                            ),
                    contentScale = ContentScale.Fit,
                )
            is PdfSegment -> RenderPdfCard(segment.segmentText)
            else -> ClickableLink(segment.segmentText, segment.segmentText)
        }
    }

    @Composable
    override fun Gallery(
        paragraph: ImageGalleryParagraph,
        state: RichTextViewerState,
        modifier: Modifier,
    ) {
        val urls = remember(paragraph) { paragraph.words.map { it.segmentText } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
            paragraph.words.forEachIndexed { index, segment ->
                AsyncImage(
                    model = segment.segmentText,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(max = 300.dp)
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (callbacks.onImageClick != null) {
                                    Modifier.clickable { callbacks.onImageClick.invoke(urls, index) }
                                } else {
                                    Modifier
                                },
                            ),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }

    @Composable
    override fun Equation(
        segment: MathSegment,
        modifier: Modifier,
    ) = Text(segment.segmentText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

    @Composable
    override fun NostrEntity(
        bech: String,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) {
        val segment = remember(bech) { BechSegment(bech) }
        RenderBechSegment(segment, localCache, callbacks)
    }

    @Composable
    override fun QuotedEvent(
        eventHex: String,
        addedChars: String?,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = QuotedNoteEmbed(
        noteId = eventHex,
        localCache = localCache,
        onMentionClick = callbacks.onMentionClick,
        onNavigateToThread = callbacks.onNavigateToThread,
    )

    @Composable
    override fun UserMention(
        userHex: String,
        addedChars: String?,
        modifier: Modifier,
    ) {
        val user = localCache?.getUserIfExists(userHex)
        val display = "@${user?.toBestDisplayName() ?: (userHex.take(8) + "...")}"
        Text(
            text = display + (addedChars ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { callbacks.onMentionClick?.invoke(userHex) },
        )
    }

    @Composable
    override fun Payment(
        segment: Segment,
        modifier: Modifier,
    ) {
        when (segment) {
            is InvoiceSegment -> RenderInvoiceCard(segment.segmentText, callbacks)
            is CashuSegment -> RenderCashuCard(segment.segmentText)
            is WithdrawSegment -> ClickableLink(segment.segmentText, segment.segmentText)
            else -> Text(segment.segmentText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    @Composable
    override fun LinkPreview(
        url: String,
        modifier: Modifier,
    ) = ClickableLink(url, url)

    @Composable
    override fun RelayLink(
        segment: Segment,
        modifier: Modifier,
    ) {
        if (segment is RelayUrlSegment) {
            Text(
                text = segment.segmentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { copyToClipboard(segment.segmentText) },
            )
        } else {
            Text(segment.segmentText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    @Composable
    override fun NowhereLink(
        segment: NowhereLinkSegment,
        canPreview: Boolean,
        modifier: Modifier,
    ) = RenderNowhereLinkCard(segment)

    @Composable
    override fun SecretMessage(
        segment: SecretEmoji,
        state: RichTextViewerState,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = RenderSecretEmoji(segment.segmentText)
}

@Composable
private fun ClickableLink(
    url: String,
    displayText: String,
) {
    Text(
        text = displayText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable {
                    runCatching {
                        java.awt.Desktop
                            .getDesktop()
                            .browse(URI(url))
                    }
                },
    )
}

@Composable
private fun RenderBechSegment(
    segment: BechSegment,
    localCache: DesktopLocalCache?,
    callbacks: RichTextCallbacks,
) {
    // Try NAddress first — kind 39089 (Follow Pack) renders as a rich card.
    val naddr =
        remember(segment.segmentText) {
            com.vitorpamplona.quartz.nip19Bech32.entities
                .NAddress
                .parse(segment.segmentText)
        }
    if (naddr != null && naddr.kind == com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent.KIND) {
        val followPacks = com.vitorpamplona.amethyst.desktop.ui.deck.LocalFollowPacksState.current
        val relayManager = com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayManager.current
        if (localCache != null && relayManager != null && followPacks != null) {
            com.vitorpamplona.amethyst.desktop.followpacks.ui
                .RenderFollowPackCard(
                    address = naddr,
                    cache = localCache,
                    iAccount = null,
                    relayManager = relayManager,
                    onOpenPack = { /* no nav callback available in rich-text context */ },
                )
            return
        }
    }

    val resolved =
        remember(segment.segmentText, localCache) {
            resolveBech32(segment.segmentText, localCache)
        }
    when {
        resolved.noteIdHex != null -> {
            QuotedNoteEmbed(
                noteId = resolved.noteIdHex,
                localCache = localCache,
                onMentionClick = callbacks.onMentionClick,
                onNavigateToThread = callbacks.onNavigateToThread,
            )
        }

        resolved.pubKeyHex != null -> {
            Text(
                text = resolved.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { callbacks.onMentionClick?.invoke(resolved.pubKeyHex) },
            )
        }

        else -> {
            Text(
                text = resolved.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RenderInvoiceCard(
    invoice: String,
    callbacks: RichTextCallbacks,
) {
    val amount =
        remember(invoice) {
            runCatching { LnInvoiceUtil.getAmountInSats(invoice) }.getOrNull()
        }
    val description =
        remember(invoice) {
            LnInvoiceUtil.getDescription(invoice)
        }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    symbol = MaterialSymbols.Bolt,
                    contentDescription = "Lightning Invoice",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (amount != null) "$amount sats" else "Lightning Invoice",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { copyToClipboard(invoice) }) {
                    Icon(
                        symbol = MaterialSymbols.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                if (callbacks.onPayInvoice != null) {
                    TextButton(onClick = { callbacks.onPayInvoice.invoke(invoice) }) {
                        Icon(
                            symbol = MaterialSymbols.Bolt,
                            contentDescription = "Pay",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Pay")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderCashuCard(token: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Cashu Token",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = token.take(40) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { copyToClipboard(token) }) {
                Icon(
                    symbol = MaterialSymbols.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Copy")
            }
        }
    }
}

@Composable
private fun RenderPdfCard(url: String) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable {
                    runCatching {
                        java.awt.Desktop
                            .getDesktop()
                            .browse(URI(url))
                    }
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.PictureAsPdf,
                contentDescription = "PDF",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RenderSecretEmoji(text: String) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        val decoded = remember(text) { runCatching { EmojiCoder.decode(text) }.getOrDefault(text) }
        Text(
            text = decoded,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable { expanded = false },
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { expanded = true },
        )
    }
}

private val nowhereToolLabels =
    mapOf(
        "e" to "Nowhere Event",
        "f" to "Nowhere Fundraiser",
        "s" to "Nowhere Store",
        "p" to "Nowhere Petition",
        "m" to "Nowhere Message",
        "d" to "Nowhere Drop",
        "a" to "Nowhere Art",
        "fo" to "Nowhere Forum",
    )

@Composable
private fun RenderNowhereLinkCard(segment: NowhereLinkSegment) {
    val label = nowhereToolLabels[segment.tool] ?: "Nowhere Site"
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable {
                    runCatching {
                        java.awt.Desktop
                            .getDesktop()
                            .browse(URI(segment.segmentText))
                    }
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = segment.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}
