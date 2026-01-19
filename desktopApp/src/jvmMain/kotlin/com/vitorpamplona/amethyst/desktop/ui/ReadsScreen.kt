/**
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.subscriptions.FeedMode
import com.vitorpamplona.amethyst.commons.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.createFollowingLongFormFeedSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.createLongFormFeedSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

private fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp * 1000))

/**
 * Card displaying long-form content (NIP-23) with title, summary, and image.
 */
@Composable
fun LongFormCard(
    event: LongTextNoteEvent,
    localCache: DesktopLocalCache,
    onAuthorClick: (String) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val author = localCache.getUserIfExists(event.pubKey)
    val authorName = author?.info?.bestName() ?: event.pubKey.take(8)
    val publishedAt = event.publishedAt() ?: event.createdAt

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            event.title()?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Summary
            event.summary()?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
            }

            // Footer with author and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onAuthorClick(event.pubKey) },
                )

                Text(
                    text = formatDate(publishedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Topics/hashtags
            val topics = event.topics()
            if (topics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    topics.take(3).forEach { topic ->
                        Text(
                            text = "#$topic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReadsScreen(
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToArticle: (String) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scope = rememberCoroutineScope()

    val eventState =
        remember {
            EventCollectionState<LongTextNoteEvent>(
                getId = { it.id },
                sortComparator = compareByDescending { it.publishedAt() ?: it.createdAt },
                maxSize = 100,
                scope = scope,
            )
        }
    val events by eventState.items.collectAsState()

    var feedMode by remember { mutableStateOf(FeedMode.GLOBAL) }
    var followedUsers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var eoseReceivedCount by remember { mutableStateOf(0) }
    val initialLoadComplete = eoseReceivedCount > 0

    // Load followed users for Following feed mode
    rememberSubscription(relayStatuses, account, feedMode, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && account != null && feedMode == FeedMode.FOLLOWING) {
            createContactListSubscription(
                relays = configuredRelays,
                pubKeyHex = account.pubKeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is ContactListEvent) {
                        followedUsers = event.verifiedFollowKeySet()
                    }
                },
            )
        } else {
            null
        }
    }

    // Clear events when feed mode changes
    remember(feedMode) {
        eventState.clear()
        eoseReceivedCount = 0
    }

    // Subscribe to long-form content feed
    rememberSubscription(relayStatuses, feedMode, followedUsers, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty()) {
            return@rememberSubscription null
        }

        when (feedMode) {
            FeedMode.GLOBAL -> {
                createLongFormFeedSubscription(
                    relays = configuredRelays,
                    onEvent = { event, _, _, _ ->
                        if (event is LongTextNoteEvent) {
                            eventState.addItem(event)
                        }
                    },
                    onEose = { _, _ ->
                        eoseReceivedCount++
                    },
                )
            }
            FeedMode.FOLLOWING -> {
                if (followedUsers.isNotEmpty()) {
                    createFollowingLongFormFeedSubscription(
                        relays = configuredRelays,
                        followedUsers = followedUsers.toList(),
                        onEvent = { event, _, _, _ ->
                            if (event is LongTextNoteEvent) {
                                eventState.addItem(event)
                            }
                        },
                        onEose = { _, _ ->
                            eoseReceivedCount++
                        },
                    )
                } else {
                    null
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Reads",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    // Feed mode selector
                    if (account != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = feedMode == FeedMode.GLOBAL,
                                onClick = { feedMode = FeedMode.GLOBAL },
                                label = { Text("Global") },
                            )
                            FilterChip(
                                selected = feedMode == FeedMode.FOLLOWING,
                                onClick = { feedMode = FeedMode.FOLLOWING },
                                label = { Text("Following") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${connectedRelays.size} relays connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { relayManager.connect() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            connectedRelays.isEmpty() -> {
                LoadingState("Connecting to relays...")
            }
            feedMode == FeedMode.FOLLOWING && followedUsers.isEmpty() -> {
                LoadingState("Loading followed users...")
            }
            events.isEmpty() && !initialLoadComplete -> {
                LoadingState("Loading articles...")
            }
            events.isEmpty() && initialLoadComplete -> {
                EmptyState(
                    title =
                        if (feedMode == FeedMode.FOLLOWING) {
                            "No articles from followed users"
                        } else {
                            "No articles found"
                        },
                    description =
                        if (feedMode == FeedMode.FOLLOWING) {
                            "Long-form articles from people you follow will appear here"
                        } else {
                            "Long-form articles from the network will appear here"
                        },
                    onRefresh = { relayManager.connect() },
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(events, key = { it.id }) { event ->
                        LongFormCard(
                            event = event,
                            localCache = localCache,
                            onAuthorClick = onNavigateToProfile,
                            onClick = { onNavigateToArticle(event.id) },
                        )
                    }
                }
            }
        }
    }
}
