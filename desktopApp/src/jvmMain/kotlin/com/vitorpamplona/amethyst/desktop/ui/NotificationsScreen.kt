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
package com.vitorpamplona.amethyst.desktop.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Repost
import com.vitorpamplona.amethyst.commons.icons.Zap
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feed.FeedHeader
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.createNotificationsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent

/**
 * Notification types for display.
 */
sealed class NotificationItem(
    open val event: Event,
    open val timestamp: Long,
) {
    data class Mention(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp)

    data class Reply(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp)

    data class Reaction(
        override val event: Event,
        override val timestamp: Long,
        val content: String,
    ) : NotificationItem(event, timestamp)

    data class Repost(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp)

    data class Zap(
        override val event: Event,
        override val timestamp: Long,
        val amount: Long?,
    ) : NotificationItem(event, timestamp)
}

@Composable
fun NotificationsScreen(
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scope = rememberCoroutineScope()
    val notificationState =
        remember {
            EventCollectionState<NotificationItem>(
                getId = { it.event.id },
                sortComparator = null, // Prepend new items (no sorting)
                maxSize = 200,
                scope = scope,
            )
        }
    val notifications by notificationState.items.collectAsState()

    // Load metadata for notification authors via coordinator
    LaunchedEffect(notifications, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null && notifications.isNotEmpty()) {
            val pubkeys = notifications.map { it.event.pubKey }.distinct()
            subscriptionsCoordinator.loadMetadataForPubkeys(pubkeys)
        }
    }

    // Track EOSE to know when initial load is complete
    var eoseReceivedCount by remember { mutableStateOf(0) }
    val initialLoadComplete = eoseReceivedCount > 0

    // Subscribe to notifications
    rememberSubscription(relayStatuses, account.pubKeyHex, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createNotificationsSubscription(
                relays = configuredRelays,
                pubKeyHex = account.pubKeyHex,
                onEvent = { event, _, _, _ ->
                    // Skip events from the user themselves (except zaps)
                    if (event.pubKey == account.pubKeyHex && event !is LnZapEvent) {
                        return@createNotificationsSubscription
                    }

                    val notification =
                        when (event) {
                            is ReactionEvent -> {
                                NotificationItem.Reaction(
                                    event = event,
                                    timestamp = event.createdAt,
                                    content = event.content,
                                )
                            }

                            is RepostEvent, is GenericRepostEvent -> {
                                NotificationItem.Repost(
                                    event = event,
                                    timestamp = event.createdAt,
                                )
                            }

                            is LnZapEvent -> {
                                val amount = event.amount?.toLong()
                                NotificationItem.Zap(
                                    event = event,
                                    timestamp = event.createdAt,
                                    amount = amount,
                                )
                            }

                            is TextNoteEvent -> {
                                val eTags = event.tags.filter { it.size > 1 && it[0] == "e" }
                                val isReply = eTags.isNotEmpty()
                                if (isReply) {
                                    NotificationItem.Reply(event, event.createdAt)
                                } else {
                                    NotificationItem.Mention(event, event.createdAt)
                                }
                            }

                            else -> {
                                NotificationItem.Mention(event, event.createdAt)
                            }
                        }

                    notificationState.addItem(notification)
                },
                onEose = { _, _ ->
                    eoseReceivedCount++
                },
            )
        } else {
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FeedHeader(
            title = "Notifications",
            connectedRelayCount = connectedRelays.size,
            onRefresh = { relayManager.connect() },
        )

        Spacer(Modifier.height(16.dp))

        if (connectedRelays.isEmpty()) {
            LoadingState("Connecting to relays...")
        } else if (notifications.isEmpty() && !initialLoadComplete) {
            LoadingState("Loading notifications...")
        } else if (notifications.isEmpty() && initialLoadComplete) {
            EmptyState(
                title = "No notifications yet",
                description = "When someone interacts with your posts, you'll see it here",
                onRefresh = { relayManager.connect() },
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notifications.distinctBy { it.event.id }, key = { it.event.id }) { notification ->
                    NotificationCard(notification)
                }
            }
        }
    }
}

@Composable
fun NotificationCard(notification: NotificationItem) {
    val (icon, label, color) =
        when (notification) {
            is NotificationItem.Mention -> {
                Triple(Icons.Default.Favorite, "mentioned you", MaterialTheme.colorScheme.primary)
            }

            is NotificationItem.Reply -> {
                Triple(Reply, "replied", MaterialTheme.colorScheme.secondary)
            }

            is NotificationItem.Reaction -> {
                Triple(
                    Icons.Default.Favorite,
                    "reacted ${notification.content}",
                    MaterialTheme.colorScheme.tertiary,
                )
            }

            is NotificationItem.Repost -> {
                Triple(Repost, "reposted", MaterialTheme.colorScheme.primary)
            }

            is NotificationItem.Zap -> {
                val amountText = notification.amount?.let { " ${it / 1000} sats" } ?: ""
                Triple(Zap, "zapped$amountText", MaterialTheme.colorScheme.primary)
            }
        }

    val authorDisplay =
        try {
            notification.event.pubKey
                .hexToByteArrayOrNull()
                ?.toNpub()
                ?.take(20) ?: notification.event.pubKey.take(20)
        } catch (e: Exception) {
            notification.event.pubKey.take(20)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: icon + label + author + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "$authorDisplay $label",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = notification.timestamp.toTimeAgo(withDot = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            // Content (for text-based notifications)
            if (notification is NotificationItem.Mention ||
                notification is NotificationItem.Reply
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notification.event.content.take(200),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                )
            }
        }
    }
}
