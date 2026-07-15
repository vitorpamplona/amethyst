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
package com.vitorpamplona.amethyst.desktop.ui.scheduledposts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.util.timeAbsolute
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.LocalScheduledPostStore
import com.vitorpamplona.amethyst.desktop.ui.readingHorizontalPadding
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

/**
 * Management list for scheduled posts belonging to [accountPubkeyHex]. Reads
 * reactively off the shared [ScheduledPostStore.flow] provided via
 * [LocalScheduledPostStore], so cancel / publish-now update the list live.
 *
 * Rows are grouped by lifecycle: PENDING/PUBLISHING (upcoming) first, then the
 * terminal SENT/FAILED/CANCELLED rows; within each group they are ordered by the
 * scheduled publish time ascending.
 */
@Composable
fun ScheduledPostsList(
    accountPubkeyHex: String,
    onEditInComposer: (content: String, draftDTag: String?, scheduledForSec: Long?) -> Unit = { _, _, _ -> },
) {
    val store = LocalScheduledPostStore.current
    val allPosts by store.flow.collectAsState()
    val scope = rememberCoroutineScope()

    val myPosts =
        remember(allPosts, accountPubkeyHex) {
            allPosts.filter { it.accountPubkey == accountPubkeyHex }
        }

    val ordered =
        remember(myPosts) {
            myPosts.sortedWith(
                compareBy<ScheduledPost> { if (it.status.isTerminal()) 1 else 0 }
                    .thenBy { it.publishAtSec },
            )
        }

    if (ordered.isEmpty()) {
        EmptyState(
            title = "No scheduled posts",
            description = "Posts you schedule from the composer will appear here.",
        )
        return
    }

    val sidePadding = readingHorizontalPadding()
    LazyColumn(
        contentPadding = PaddingValues(horizontal = sidePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ordered, key = { it.id }) { post ->
            ScheduledPostCard(
                post = post,
                onPublishNow = { scope.launch { store.publishNow(post.id, TimeUtils.now()) } },
                onCancel = { scope.launch { store.cancel(post.id) } },
                onEdit = {
                    // Reopen the composer prefilled with this post's content and its original
                    // time (so the primary button reads "Schedule"), then cancel the old row.
                    // The user tweaks and re-queues from the composer.
                    val content =
                        Event
                            .fromJsonOrNull(post.signedEventJson)
                            ?.content
                            .orEmpty()
                    onEditInComposer(content, null, post.publishAtSec)
                    scope.launch { store.cancel(post.id) }
                },
            )
        }
    }
}

@Composable
private fun ScheduledPostCard(
    post: ScheduledPost,
    onPublishNow: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    val fullContent =
        remember(post.signedEventJson) {
            Event
                .fromJsonOrNull(post.signedEventJson)
                ?.content
                ?.trim()
                .orEmpty()
        }
    // Pull out image URLs so we can show a thumbnail instead of a raw Blossom link,
    // and strip them from the text preview.
    val imageUrls =
        remember(fullContent) {
            UrlParser().parseValidUrls(fullContent).withScheme.filter { RichTextParser.isImageUrl(it) }
        }
    val textPreview =
        remember(fullContent, imageUrls) {
            imageUrls.fold(fullContent) { acc, url -> acc.replace(url, "") }.trim()
        }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(post.status)
                RowActions(
                    post = post,
                    onPublishNow = onPublishNow,
                    onCancel = onCancel,
                    onEdit = onEdit,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (textPreview.isNotBlank() || imageUrls.isEmpty()) {
                Text(
                    text = textPreview.ifBlank { "(no text)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (imageUrls.isNotEmpty()) {
                if (textPreview.isNotBlank()) Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    imageUrls.take(3).forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    MaterialSymbols.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = scheduleLabel(post.publishAtSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (post.status == ScheduledPostStatus.FAILED) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Failed after ${post.attemptCount} attempt(s): ${post.lastError ?: "unknown error"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RowActions(
    post: ScheduledPost,
    onPublishNow: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    // Actions only apply to rows the user can still act on.
    val canPublishNow = post.status == ScheduledPostStatus.PENDING || post.status == ScheduledPostStatus.FAILED
    val canCancel = canPublishNow
    val canEdit = post.status == ScheduledPostStatus.PENDING
    if (!canPublishNow && !canCancel && !canEdit) return

    var menuOpen by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                MaterialSymbols.MoreVert,
                contentDescription = "Actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (canPublishNow) {
                DropdownMenuItem(
                    text = { Text("Publish now") },
                    onClick = {
                        menuOpen = false
                        onPublishNow()
                    },
                    leadingIcon = {
                        Icon(
                            MaterialSymbols.AutoMirrored.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text("Edit (cancels schedule)") },
                    onClick = {
                        menuOpen = false
                        // Reopens the composer prefilled with this post's content + time and
                        // cancels the old row; the user tweaks and re-queues from the composer.
                        onEdit()
                    },
                    leadingIcon = {
                        Icon(
                            MaterialSymbols.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
            if (canCancel) {
                DropdownMenuItem(
                    text = { Text("Cancel", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onCancel()
                    },
                    leadingIcon = {
                        Icon(
                            MaterialSymbols.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: ScheduledPostStatus) {
    val (label, bg, fg) =
        when (status) {
            ScheduledPostStatus.PENDING ->
                Triple("Scheduled", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            ScheduledPostStatus.PUBLISHING ->
                Triple("Publishing…", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            ScheduledPostStatus.SENT ->
                Triple("Sent", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            ScheduledPostStatus.FAILED ->
                Triple("Failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            ScheduledPostStatus.CANCELLED ->
                Triple(
                    "Cancelled",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }

    Box(
        modifier =
            Modifier
                .background(bg, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

private fun ScheduledPostStatus.isTerminal(): Boolean =
    this == ScheduledPostStatus.SENT ||
        this == ScheduledPostStatus.FAILED ||
        this == ScheduledPostStatus.CANCELLED

/**
 * Absolute time (e.g. "May 28, 2:32 PM") plus a coarse relative hint. Unlike the
 * shared past-oriented `timeAgo`, this renders future offsets like "in 3h".
 */
private fun scheduleLabel(publishAtSec: Long): String {
    val absolute = timeAbsolute(publishAtSec, withDot = false)
    val relative = relativeFuture(publishAtSec)
    return if (relative != null) "$absolute ($relative)" else absolute
}

private fun relativeFuture(publishAtSec: Long): String? {
    val diff = publishAtSec - TimeUtils.now()
    if (diff <= 0L) return "due"
    return when {
        diff < TimeUtils.ONE_HOUR -> "in ${(diff / TimeUtils.ONE_MINUTE).coerceAtLeast(1L)}m"
        diff < TimeUtils.ONE_DAY -> "in ${diff / TimeUtils.ONE_HOUR}h"
        else -> "in ${diff / TimeUtils.ONE_DAY}d"
    }
}
