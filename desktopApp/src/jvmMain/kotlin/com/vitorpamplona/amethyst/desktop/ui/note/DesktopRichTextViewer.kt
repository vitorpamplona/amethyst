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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.vitorpamplona.amethyst.commons.compose.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.PdfSegment
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.service.DesktopCachedRichTextParser
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.collections.immutable.ImmutableMap
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

data class RichTextCallbacks(
    val onMentionClick: ((String) -> Unit)? = null,
    val onHashtagClick: ((String) -> Unit)? = null,
    val onNavigateToThread: ((String) -> Unit)? = null,
    val onImageClick: ((List<String>, Int) -> Unit)? = null,
    val onPayInvoice: ((String) -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopRichTextViewer(
    content: String,
    state: RichTextViewerState,
    localCache: DesktopLocalCache? = null,
    callbacks: RichTextCallbacks = RichTextCallbacks(),
    modifier: Modifier = Modifier,
) {
    if (DesktopCachedRichTextParser.isMarkdown(content)) {
        RenderMarkdown(
            content = content,
            onLinkClick = { url ->
                when {
                    url.startsWith("nostr:") -> {
                        val parsed = Nip19Parser.uriToRoute(url)
                        when (val entity = parsed?.entity) {
                            is NPub -> {
                                callbacks.onMentionClick?.invoke(entity.hex)
                            }

                            is NProfile -> {
                                callbacks.onMentionClick?.invoke(entity.hex)
                            }

                            is NNote -> {
                                callbacks.onNavigateToThread?.invoke(entity.hex)
                            }

                            is NEvent -> {
                                callbacks.onNavigateToThread?.invoke(entity.hex)
                            }

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
            },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier) {
        for (paragraph in state.paragraphs) {
            when (paragraph) {
                is ImageGalleryParagraph -> {
                    val urls = paragraph.words.map { it.segmentText }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        for ((index, segment) in paragraph.words.withIndex()) {
                            AsyncImage(
                                model = segment.segmentText,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(8.dp))
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

                else -> {
                    val hasOnlyText = paragraph.words.all { it is RegularTextSegment }
                    if (hasOnlyText) {
                        Text(
                            text = paragraph.words.joinToString("") { it.segmentText },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (paragraph.isRTL) Arrangement.End else Arrangement.Start,
                        ) {
                            for (word in paragraph.words) {
                                RenderSegment(word, state, localCache, callbacks)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderSegment(
    segment: Segment,
    state: RichTextViewerState,
    localCache: DesktopLocalCache?,
    callbacks: RichTextCallbacks,
) {
    when (segment) {
        is RegularTextSegment -> {
            Text(
                text = segment.segmentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is LinkSegment -> {
            ClickableLink(segment.segmentText, segment.segmentText)
        }

        is SchemelessUrlSegment -> {
            ClickableLink("https://${segment.segmentText}", segment.segmentText)
        }

        is BechSegment -> {
            RenderBechSegment(segment, localCache, callbacks)
        }

        is HashTagSegment -> {
            val display = "#${segment.hashtag}" + (segment.extras ?: "")
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { callbacks.onHashtagClick?.invoke(segment.hashtag) },
            )
        }

        is HashIndexUserSegment -> {
            val user = localCache?.getUserIfExists(segment.hex)
            val display = "@${user?.toBestDisplayName() ?: segment.hex.take(8) + "..."}"
            Text(
                text = display + (segment.extras ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { callbacks.onMentionClick?.invoke(segment.hex) },
            )
        }

        is HashIndexEventSegment -> {
            QuotedNoteEmbed(
                noteId = segment.hex,
                localCache = localCache,
                onMentionClick = callbacks.onMentionClick,
                onNavigateToThread = callbacks.onNavigateToThread,
            )
        }

        is EmailSegment -> {
            Text(
                text = segment.segmentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            runCatching {
                                java.awt.Desktop
                                    .getDesktop()
                                    .browse(URI("mailto:${segment.segmentText}"))
                            }
                        },
            )
        }

        is PhoneSegment -> {
            Text(
                text = segment.segmentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is RelayUrlSegment -> {
            Text(
                text = segment.segmentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { copyToClipboard(segment.segmentText) },
            )
        }

        is EmojiSegment -> {
            RenderCustomEmojiSegment(segment.segmentText, state.customEmoji)
        }

        is NowhereLinkSegment -> {
            RenderNowhereLinkCard(segment)
        }

        is InvoiceSegment -> {
            RenderInvoiceCard(segment.segmentText, callbacks)
        }

        is CashuSegment -> {
            RenderCashuCard(segment.segmentText)
        }

        is WithdrawSegment -> {
            ClickableLink(segment.segmentText, segment.segmentText)
        }

        is BlossomUriSegment -> {
            ClickableLink(segment.segmentText, segment.segmentText)
        }

        is PdfSegment -> {
            RenderPdfCard(segment.segmentText)
        }

        is Base64Segment -> {
            AsyncImage(
                model = segment.segmentText,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }

        is ImageSegment -> {
            AsyncImage(
                model = segment.segmentText,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }

        is VideoSegment -> {
            Text(
                text = segment.segmentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            runCatching {
                                java.awt.Desktop
                                    .getDesktop()
                                    .browse(URI(segment.segmentText))
                            }
                        },
            )
        }

        is SecretEmoji -> {
            RenderSecretEmoji(segment.segmentText)
        }

        else -> {
            Text(
                text = segment.segmentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
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

@Composable
private fun RenderCustomEmojiSegment(
    word: String,
    customEmoji: ImmutableMap<String, String>,
) {
    val matchedEmoji = remember(word, customEmoji) { customEmoji.entries.firstOrNull { word.contains(it.key) } }
    if (matchedEmoji != null) {
        val parts = word.split(matchedEmoji.key, limit = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (parts[0].isNotEmpty()) {
                Text(parts[0], style = MaterialTheme.typography.bodyMedium)
            }
            AsyncImage(
                model = matchedEmoji.value,
                contentDescription = matchedEmoji.key,
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit,
            )
            if (parts.size > 1 && parts[1].isNotEmpty()) {
                Text(parts[1], style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        Text(word, style = MaterialTheme.typography.bodyMedium)
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
