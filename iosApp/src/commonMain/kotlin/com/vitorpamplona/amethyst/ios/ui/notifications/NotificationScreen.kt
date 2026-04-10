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
package com.vitorpamplona.amethyst.ios.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.feeds.IosNotificationFeedFilter
import com.vitorpamplona.amethyst.ios.util.toTimeAgo
import com.vitorpamplona.amethyst.ios.viewmodels.IosFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.tags.events.firstTaggedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent

/**
 * Describes the type of notification for display purposes.
 */
private enum class NotificationType(
    val icon: String,
    val actionText: String,
) {
    REACTION("❤️", "liked your note"),
    REPOST("🔁", "reposted your note"),
    MENTION("💬", "mentioned you"),
    REPLY("↩️", "replied to your note"),
    ZAP("⚡", "zapped your note"),
}

/**
 * Display data for a single notification row.
 */
private data class NotificationItem(
    val noteId: String,
    val authorPubKeyHex: String,
    val authorDisplayName: String,
    val authorPictureUrl: String?,
    val type: NotificationType,
    val snippet: String,
    val createdAt: Long,
    val referencedNoteId: String?,
)

/**
 * Notification screen showing reactions, reposts, mentions, replies, and zaps
 * targeting the logged-in user.
 */
@Composable
fun IosNotificationScreen(
    pubKeyHex: HexKey,
    localCache: IosLocalCache,
    onNavigateToThread: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        remember(pubKeyHex) {
            IosFeedViewModel(
                IosNotificationFeedFilter(pubKeyHex, localCache),
                localCache,
            )
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = feedState) {
            is FeedState.Loading -> {
                LoadingState("Loading notifications...")
            }

            is FeedState.Empty -> {
                EmptyState(
                    title = "No notifications yet",
                    description = "Reactions, reposts, mentions, and zaps will appear here",
                )
            }

            is FeedState.FeedError -> {
                EmptyState(
                    title = "Error loading notifications",
                    description = state.errorMessage,
                )
            }

            is FeedState.Loaded -> {
                val loadedState by state.feed.collectAsState()
                val items =
                    remember(loadedState) {
                        loadedState.list.mapNotNull { note ->
                            note.toNotificationItem(pubKeyHex, localCache)
                        }
                    }

                if (items.isEmpty()) {
                    EmptyState(
                        title = "No notifications yet",
                        description = "Reactions, reposts, mentions, and zaps will appear here",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.noteId }) { item ->
                            NotificationRow(
                                item = item,
                                onClick = {
                                    val target = item.referencedNoteId ?: item.noteId
                                    onNavigateToThread(target)
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Type icon
        Text(
            text = item.type.icon,
            modifier = Modifier.padding(top = 2.dp),
        )

        // Avatar
        UserAvatar(
            userHex = item.authorPubKeyHex,
            pictureUrl = item.authorPictureUrl,
            size = 40.dp,
            contentDescription = "Profile picture",
        )

        // Text content
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // "Author liked your note"
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(item.authorDisplayName)
                        }
                        append(" ")
                        append(item.type.actionText)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Note snippet preview
            if (item.snippet.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Timestamp
        Text(
            text = item.createdAt.toTimeAgo(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Convert a Note from the notification feed into a displayable NotificationItem.
 */
private fun Note.toNotificationItem(
    userPubKeyHex: HexKey,
    cache: IosLocalCache,
): NotificationItem? {
    val event = this.event ?: return null

    val authorUser = cache.getUserIfExists(event.pubKey)
    val authorName =
        authorUser?.toBestDisplayName()
            ?: try {
                event.pubKey
                    .hexToByteArrayOrNull()
                    ?.toNpub()
                    ?.take(20) ?: event.pubKey.take(16) + "..."
            } catch (_: Exception) {
                event.pubKey.take(16) + "..."
            }
    val authorPic = authorUser?.profilePicture()

    val referencedEventId = event.firstTaggedEvent()?.eventId
    val referencedNote = referencedEventId?.let { cache.getNoteIfExists(it) }

    val type: NotificationType
    val snippet: String

    when (event.kind) {
        ReactionEvent.KIND -> {
            type = NotificationType.REACTION
            val reactionContent = event.content.ifBlank { "+" }
            snippet =
                if (reactionContent == "+" || reactionContent == "❤️" || reactionContent == "🤙") {
                    referencedNote?.event?.content?.take(100) ?: ""
                } else {
                    "$reactionContent  ${referencedNote?.event?.content?.take(80) ?: ""}"
                }
        }

        RepostEvent.KIND -> {
            type = NotificationType.REPOST
            snippet = referencedNote?.event?.content?.take(100) ?: ""
        }

        LnZapEvent.KIND -> {
            type = NotificationType.ZAP
            snippet = referencedNote?.event?.content?.take(100) ?: ""
        }

        TextNoteEvent.KIND -> {
            // Distinguish reply vs mention
            val isReply =
                referencedEventId != null &&
                    referencedNote?.event?.pubKey == userPubKeyHex
            type = if (isReply) NotificationType.REPLY else NotificationType.MENTION
            snippet = event.content.take(120)
        }

        else -> {
            return null
        }
    }

    return NotificationItem(
        noteId = event.id,
        authorPubKeyHex = event.pubKey,
        authorDisplayName = authorName,
        authorPictureUrl = authorPic,
        type = type,
        snippet = snippet.replace("\n", " ").trim(),
        createdAt = event.createdAt,
        referencedNoteId = referencedEventId,
    )
}
