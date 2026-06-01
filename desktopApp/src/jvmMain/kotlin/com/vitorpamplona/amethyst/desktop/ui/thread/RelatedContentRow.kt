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
package com.vitorpamplona.amethyst.desktop.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.feeds.related.CompactNoteData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHashes
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

/**
 * Horizontal scrollable row of compact related content cards.
 * Shows related posts by hashtag and author for the given note.
 * Only loads when the thread view is open.
 */
@Composable
fun RelatedContentSection(
    noteId: String,
    authorPubKey: String,
    noteHashtags: Set<String>,
    localCache: DesktopLocalCache,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var relatedItems by remember(noteId) { mutableStateOf<List<CompactNoteData>>(emptyList()) }

    DisposableEffect(noteId) {
        val results = mutableListOf<Note>()
        val lowercaseTags = noteHashtags.map { it.lowercase() }.toSet()
        val limit = 6

        // Scan cache for related content
        if (lowercaseTags.isNotEmpty()) {
            localCache.notes.forEach { key, note ->
                if (note.idHex != noteId &&
                    note.event is TextNoteEvent &&
                    note.event?.tags?.isTaggedHashes(lowercaseTags) == true
                ) {
                    results.add(note)
                }
            }
        }

        // Fallback: same author
        if (results.size < limit) {
            localCache.notes.forEach { key, note ->
                if (note.idHex != noteId &&
                    note.event is TextNoteEvent &&
                    note.event?.pubKey == authorPubKey &&
                    note !in results
                ) {
                    results.add(note)
                }
            }
        }

        relatedItems =
            results
                .sortedByDescending { it.createdAt() }
                .take(limit)
                .map { note ->
                    val event = note.event
                    val content = event?.content ?: ""
                    val firstLine =
                        content
                            .take(80)
                            .lineSequence()
                            .firstOrNull()
                            ?.take(60) ?: ""
                    val author = localCache.getUserIfExists(event?.pubKey ?: "")
                    val imageUrl =
                        UrlParser()
                            .parseValidUrls(content)
                            .withScheme
                            .firstOrNull { RichTextParser.isImageUrl(it) }
                    CompactNoteData(
                        id = note.idHex,
                        title = firstLine.ifBlank { "Note" },
                        authorName = author?.toBestDisplayName() ?: event?.pubKey?.take(8) ?: "",
                        thumbnailUrl = imageUrl,
                        zapCount = if (note.zapsAmount > java.math.BigDecimal.ZERO) "${note.zapsAmount.toLong()}" else "",
                    )
                }

        onDispose { }
    }

    if (relatedItems.isNotEmpty()) {
        val primaryHashtag = noteHashtags.firstOrNull()
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (primaryHashtag != null) {
                    Row {
                        Text(
                            text = "Related from ",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "#$primaryHashtag",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(
                        text = "More from this author",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "View all >",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { /* TODO: navigate to full related list */ },
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(relatedItems, key = { it.id }) { item ->
                    CompactRelatedCard(
                        item = item,
                        onClick = { onItemClick(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRelatedCard(
    item: CompactNoteData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier =
            modifier
                .width(200.dp)
                .height(140.dp)
                .clickable(onClick = onClick),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background: image or gradient placeholder
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(shape),
                )
            } else {
                Box(
                    modifier =
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surface,
                                    ),
                            ),
                        ),
                )
            }

            // Dark gradient overlay at bottom
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f),
                                    ),
                            ),
                        ),
            )

            // Text over the gradient
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                val subtitle =
                    buildString {
                        append(item.authorName)
                        if (item.zapCount.isNotBlank()) {
                            append(" · ")
                            append(item.zapCount)
                            append(" zaps")
                        }
                    }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
