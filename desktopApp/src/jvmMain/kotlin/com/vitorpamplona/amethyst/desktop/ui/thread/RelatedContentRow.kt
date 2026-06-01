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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.feeds.related.CompactNoteData
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
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
                    val content = event?.content?.take(80) ?: ""
                    val firstLine = content.lineSequence().firstOrNull()?.take(60) ?: ""
                    val author = localCache.getUserIfExists(event?.pubKey ?: "")
                    CompactNoteData(
                        id = note.idHex,
                        title = firstLine.ifBlank { "Note" },
                        authorName = author?.toBestDisplayName() ?: event?.pubKey?.take(8) ?: "",
                        thumbnailUrl = null,
                        zapCount = if (note.zapsAmount > java.math.BigDecimal.ZERO) "${note.zapsAmount.toLong()}" else "",
                    )
                }

        onDispose { }
    }

    if (relatedItems.isNotEmpty()) {
        val primaryHashtag = noteHashtags.firstOrNull()
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = if (primaryHashtag != null) "Related from #$primaryHashtag" else "More from this author",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

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
    Card(
        modifier =
            modifier
                .width(160.dp)
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.authorName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.zapCount.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row {
                    Icon(
                        MaterialSymbols.Bolt,
                        contentDescription = null,
                        modifier = Modifier.height(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${item.zapCount} sats",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
