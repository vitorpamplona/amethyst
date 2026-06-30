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
package com.vitorpamplona.amethyst.desktop.followpacks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.followpacks.subscribeMetadataFor
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

/**
 * Horizontal row of up to 3 recent notes from authors in [memberHexes].
 * Each card shows avatar + name + handle + content + first image (if any).
 */
@Composable
fun FromThePackFeed(
    memberHexes: Set<HexKey>,
    cache: DesktopLocalCache,
    relayManager: RelayConnectionManager,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxNotes: Int = 3,
) {
    @Suppress("UNUSED_VARIABLE")
    val metadataVersion by cache.metadataVersion.collectAsState()

    DisposableEffect(memberHexes.hashCode()) {
        if (memberHexes.isEmpty()) {
            onDispose { }
        } else {
            val subId = "pack-notes-${memberHexes.hashCode()}"
            val filter =
                Filter(
                    kinds = listOf(TextNoteEvent.KIND),
                    authors = memberHexes.toList().take(50),
                    limit = 50,
                )
            relayManager.subscribe(
                subId,
                listOf(filter),
                listener =
                    object : SubscriptionListener {
                        override fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            cache.consume(event, relay)
                        }
                    },
            )
            val metaHandle = relayManager.subscribeMetadataFor(memberHexes.toList().take(50), cache)
            onDispose {
                relayManager.unsubscribe(subId)
                metaHandle()
            }
        }
    }

    val notes =
        remember(memberHexes, metadataVersion) {
            val out = mutableListOf<NotePreview>()
            cache.notes.forEach { _, note ->
                val ev = note.event
                if (ev is TextNoteEvent && ev.pubKey in memberHexes) {
                    val author = cache.getUserIfExists(ev.pubKey)
                    out.add(
                        NotePreview(
                            id = ev.id,
                            pubKey = ev.pubKey,
                            createdAt = ev.createdAt,
                            content = ev.content,
                            imageUrl = firstImageUrl(ev.content),
                            authorName = author?.toBestDisplayName(),
                            authorNip05 = author?.metadataOrNull()?.nip05(),
                            authorAvatarUrl = author?.profilePicture(),
                        ),
                    )
                }
            }
            out.sortByDescending { it.createdAt }
            out.take(maxNotes)
        }

    if (notes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading recent notes from this pack…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        notes.forEach { note ->
            NoteCardPretty(
                note = note,
                onOpenThread = { onNavigateToThread(note.id) },
                onOpenProfile = { onNavigateToProfile(note.pubKey) },
                modifier = Modifier.weight(1f),
            )
        }
        // If fewer than maxNotes results, pad with empty Spacers so cards keep their proportional width.
        repeat(maxNotes - notes.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private data class NotePreview(
    val id: String,
    val pubKey: HexKey,
    val createdAt: Long,
    val content: String,
    val imageUrl: String?,
    val authorName: String?,
    val authorNip05: String?,
    val authorAvatarUrl: String?,
)

@Composable
private fun NoteCardPretty(
    note: NotePreview,
    onOpenThread: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = note.authorName ?: (note.pubKey.take(12) + "…")
    val handle = note.authorNip05?.removePrefix("_@")
    val relative = relativeTime(note.createdAt)
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpenThread)
                .padding(12.dp),
    ) {
        // Author row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.clickable(onClick = onOpenProfile),
        ) {
            UserAvatar(
                userHex = note.pubKey,
                pictureUrl = note.authorAvatarUrl,
                size = 36.dp,
                contentDescription = name,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (handle != null) {
                    Text(
                        text = "@$handle • $relative",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = relative,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = note.content.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (note.imageUrl != null) 3 else 6,
            overflow = TextOverflow.Ellipsis,
        )
        if (note.imageUrl != null) {
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = note.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

// --- helpers ---

private val IMAGE_URL_REGEX =
    Regex(
        "https?://\\S+\\.(jpg|jpeg|png|webp|gif|bmp)(\\?[^\\s]*)?",
        RegexOption.IGNORE_CASE,
    )

private fun firstImageUrl(content: String): String? = IMAGE_URL_REGEX.find(content)?.value

private fun relativeTime(createdAtSec: Long): String {
    val now = System.currentTimeMillis() / 1000L
    val delta = (now - createdAtSec).coerceAtLeast(0)
    return when {
        delta < 60 -> "now"
        delta < 3600 -> "${delta / 60}m"
        delta < 86_400 -> "${delta / 3600}h"
        delta < 30L * 86_400 -> "${delta / 86_400}d"
        delta < 365L * 86_400 -> "${delta / (30L * 86_400)}mo"
        else -> "${delta / (365L * 86_400)}y"
    }
}
