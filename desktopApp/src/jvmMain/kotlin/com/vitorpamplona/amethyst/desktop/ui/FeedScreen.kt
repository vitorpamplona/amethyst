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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createBatchMetadataSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createFollowingFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createGlobalFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createReactionsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createRepliesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createRepostsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createZapsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.note.NoteCard
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent

/**
 * Note card with action buttons.
 */
@Composable
fun FeedNoteCard(
    event: Event,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    onReply: () -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    zapReceipts: List<ZapReceipt> = emptyList(),
    reactionCount: Int = 0,
    replyCount: Int = 0,
    repostCount: Int = 0,
    bookmarkList: BookmarkListEvent? = null,
    isBookmarked: Boolean = false,
    onBookmarkChanged: (BookmarkListEvent) -> Unit = {},
) {
    val zapAmountSats = zapReceipts.sumOf { it.amountSats }

    Column(
        modifier =
            Modifier.clickable {
                onNavigateToThread(event.id)
            },
    ) {
        NoteCard(
            note = event.toNoteDisplayData(localCache),
            onAuthorClick = onNavigateToProfile,
        )

        // Action buttons (only if logged in)
        if (account != null) {
            NoteActionsRow(
                event = event,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                onReplyClick = onReply,
                onZapFeedback = onZapFeedback,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                zapCount = zapReceipts.size,
                zapAmountSats = zapAmountSats,
                zapReceipts = zapReceipts,
                reactionCount = reactionCount,
                replyCount = replyCount,
                repostCount = repostCount,
                bookmarkList = bookmarkList,
                isBookmarked = isBookmarked,
                onBookmarkChanged = onBookmarkChanged,
            )
        }
    }
}

@Composable
fun FeedScreen(
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn? = null,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onCompose: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scope = rememberCoroutineScope()
    val eventState =
        remember {
            EventCollectionState<Event>(
                getId = { it.id },
                sortComparator = compareByDescending { it.createdAt },
                maxSize = 200,
                scope = scope,
            )
        }
    val events by eventState.items.collectAsState()
    var replyToEvent by remember { mutableStateOf<Event?>(null) }
    var feedMode by remember { mutableStateOf(DesktopPreferences.feedMode) }
    var followedUsers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var zapsByEvent by remember { mutableStateOf<Map<String, List<ZapReceipt>>>(emptyMap()) }
    // Track reaction event IDs per target event to deduplicate
    var reactionIdsByEvent by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val reactionsByEvent = reactionIdsByEvent.mapValues { it.value.size }
    // Track reply/repost event IDs per target event to deduplicate
    var replyIdsByEvent by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val repliesByEvent = replyIdsByEvent.mapValues { it.value.size }
    var repostIdsByEvent by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val repostsByEvent = repostIdsByEvent.mapValues { it.value.size }
    var bookmarkList by remember { mutableStateOf<BookmarkListEvent?>(null) }
    var bookmarkedEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Track EOSE to know when initial load is complete
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

    // Load user's bookmark list
    rememberSubscription(relayStatuses, account, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && account != null) {
            SubscriptionConfig(
                subId = "bookmarks-${account.pubKeyHex.take(8)}",
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(account.pubKeyHex),
                            kinds = listOf(BookmarkListEvent.KIND),
                            limit = 1,
                        ),
                    ),
                relays = configuredRelays,
                onEvent = { event, _, _, _ ->
                    if (event is BookmarkListEvent) {
                        bookmarkList = event
                        // Extract public bookmarked event IDs
                        val pubIds =
                            event
                                .publicBookmarks()
                                .filterIsInstance<com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark>()
                                .map { it.eventId }
                                .toSet()
                        bookmarkedEventIds = pubIds
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Clear events and reset EOSE when feed mode changes
    remember(feedMode) {
        eventState.clear()
        eoseReceivedCount = 0
    }

    // Subscribe to feed based on mode
    rememberSubscription(relayStatuses, feedMode, followedUsers, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty()) {
            return@rememberSubscription null
        }

        when (feedMode) {
            FeedMode.GLOBAL -> {
                createGlobalFeedSubscription(
                    relays = configuredRelays,
                    onEvent = { event, _, _, _ ->
                        // Store metadata events in cache
                        if (event is MetadataEvent) {
                            localCache.consumeMetadata(event)
                        }
                        eventState.addItem(event)
                    },
                    onEose = { _, _ ->
                        eoseReceivedCount++
                    },
                )
            }

            FeedMode.FOLLOWING -> {
                if (followedUsers.isNotEmpty()) {
                    createFollowingFeedSubscription(
                        relays = configuredRelays,
                        followedUsers = followedUsers.toList(),
                        onEvent = { event, _, _, _ ->
                            // Store metadata events in cache
                            if (event is MetadataEvent) {
                                localCache.consumeMetadata(event)
                            }
                            eventState.addItem(event)
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

    // Subscribe to zaps for visible events
    val eventIds = events.map { it.id }
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createZapsSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                if (event is LnZapEvent) {
                    val receipt = event.toZapReceipt(localCache) ?: return@createZapsSubscription
                    val targetEventId = event.zappedPost().firstOrNull() ?: return@createZapsSubscription
                    zapsByEvent =
                        zapsByEvent.toMutableMap().apply {
                            val existing = this[targetEventId] ?: emptyList()
                            if (existing.none { it.createdAt == receipt.createdAt && it.senderPubKey == receipt.senderPubKey }) {
                                this[targetEventId] = existing + receipt
                            }
                        }
                }
            },
        )
    }

    // Subscribe to metadata for zap senders (to show display names)
    val zapSenderPubkeys =
        zapsByEvent.values
            .flatten()
            .map { it.senderPubKey }
            .distinct()
    rememberSubscription(relayStatuses, zapSenderPubkeys, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || zapSenderPubkeys.isEmpty()) {
            return@rememberSubscription null
        }

        // Only fetch metadata for users we don't have yet
        val missingPubkeys =
            zapSenderPubkeys.filter { pubkey ->
                localCache
                    .getUserIfExists(pubkey)
                    ?.metadataOrNull()
                    ?.flow
                    ?.value == null
            }
        if (missingPubkeys.isEmpty()) {
            return@rememberSubscription null
        }

        createBatchMetadataSubscription(
            relays = configuredRelays,
            pubKeyHexList = missingPubkeys,
            onEvent = { event, _, _, _ ->
                if (event is MetadataEvent) {
                    localCache.consumeMetadata(event)
                }
            },
        )
    }

    // Subscribe to reactions for visible events
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createReactionsSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                if (event is ReactionEvent) {
                    val targetEventId = event.originalPost().firstOrNull() ?: return@createReactionsSubscription
                    reactionIdsByEvent =
                        reactionIdsByEvent.toMutableMap().apply {
                            val existing = this[targetEventId] ?: emptySet()
                            this[targetEventId] = existing + event.id
                        }
                }
            },
        )
    }

    // Subscribe to replies for visible events
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createRepliesSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                // Find the event this is replying to
                val replyToId =
                    event.tags
                        .filter { it.size >= 2 && it[0] == "e" }
                        .lastOrNull()
                        ?.get(1) ?: return@createRepliesSubscription
                if (replyToId in eventIds) {
                    replyIdsByEvent =
                        replyIdsByEvent.toMutableMap().apply {
                            val existing = this[replyToId] ?: emptySet()
                            this[replyToId] = existing + event.id
                        }
                }
            },
        )
    }

    // Subscribe to reposts for visible events
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createRepostsSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                if (event is RepostEvent) {
                    val targetEventId = event.boostedEventId() ?: return@createRepostsSubscription
                    repostIdsByEvent =
                        repostIdsByEvent.toMutableMap().apply {
                            val existing = this[targetEventId] ?: emptySet()
                            this[targetEventId] = existing + event.id
                        }
                }
            },
        )
    }

    // Subscribe to metadata for note authors (to enable zaps and populate search cache)
    val authorPubkeys = events.map { it.pubKey }.distinct()

    // Use coordinator for rate-limited metadata loading (preferred)
    LaunchedEffect(authorPubkeys, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null && authorPubkeys.isNotEmpty()) {
            subscriptionsCoordinator.loadMetadataForPubkeys(authorPubkeys)
        }
    }

    // Fallback subscription if coordinator not available
    rememberSubscription(relayStatuses, authorPubkeys, subscriptionsCoordinator, relayManager = relayManager) {
        // Skip if using coordinator
        if (subscriptionsCoordinator != null) {
            return@rememberSubscription null
        }

        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || authorPubkeys.isEmpty()) {
            return@rememberSubscription null
        }

        // Only fetch metadata for users we don't have yet
        val missingPubkeys =
            authorPubkeys.filter { pubkey ->
                localCache
                    .getUserIfExists(pubkey)
                    ?.metadataOrNull()
                    ?.flow
                    ?.value == null
            }
        if (missingPubkeys.isEmpty()) {
            return@rememberSubscription null
        }

        createBatchMetadataSubscription(
            relays = configuredRelays,
            pubKeyHexList = missingPubkeys,
            onEvent = { event, _, _, _ ->
                if (event is MetadataEvent) {
                    localCache.consumeMetadata(event)
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with compose button
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
                        if (feedMode == FeedMode.GLOBAL) "Global Feed" else "Following Feed",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    // Feed mode selector
                    if (account != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = feedMode == FeedMode.GLOBAL,
                                onClick = {
                                    feedMode = FeedMode.GLOBAL
                                    DesktopPreferences.feedMode = FeedMode.GLOBAL
                                },
                                label = { Text("Global") },
                            )
                            FilterChip(
                                selected = feedMode == FeedMode.FOLLOWING,
                                onClick = {
                                    feedMode = FeedMode.FOLLOWING
                                    DesktopPreferences.feedMode = FeedMode.FOLLOWING
                                },
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
                    if (feedMode == FeedMode.FOLLOWING) {
                        Text(
                            " • ${followedUsers.size} followed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

            // New Post button (primary action)
            Button(
                onClick = onCompose,
                enabled = account != null && !account.isReadOnly,
            ) {
                Icon(Icons.Default.Add, "New Post", Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New Post")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (connectedRelays.isEmpty()) {
            LoadingState("Connecting to relays...")
        } else if (feedMode == FeedMode.FOLLOWING && followedUsers.isEmpty()) {
            LoadingState("Loading followed users...")
        } else if (events.isEmpty() && !initialLoadComplete) {
            LoadingState("Loading notes...")
        } else if (events.isEmpty() && initialLoadComplete) {
            EmptyState(
                title =
                    if (feedMode == FeedMode.FOLLOWING) {
                        "No notes from followed users"
                    } else {
                        "No notes found"
                    },
                description =
                    if (feedMode == FeedMode.FOLLOWING) {
                        "Notes from people you follow will appear here"
                    } else {
                        "Notes from the network will appear here"
                    },
                onRefresh = { relayManager.connect() },
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Use distinctBy to prevent duplicate key crashes from events with same ID
                items(events.distinctBy { it.id }, key = { it.id }) { event ->
                    FeedNoteCard(
                        event = event,
                        relayManager = relayManager,
                        localCache = localCache,
                        account = account,
                        nwcConnection = nwcConnection,
                        onReply = { replyToEvent = event },
                        onZapFeedback = onZapFeedback,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToThread = onNavigateToThread,
                        zapReceipts = zapsByEvent[event.id] ?: emptyList(),
                        reactionCount = reactionsByEvent[event.id] ?: 0,
                        replyCount = repliesByEvent[event.id] ?: 0,
                        repostCount = repostsByEvent[event.id] ?: 0,
                        bookmarkList = bookmarkList,
                        isBookmarked = bookmarkedEventIds.contains(event.id),
                        onBookmarkChanged = { newList ->
                            bookmarkList = newList
                            val pubIds =
                                newList
                                    .publicBookmarks()
                                    .filterIsInstance<com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark>()
                                    .map { it.eventId }
                                    .toSet()
                            bookmarkedEventIds = pubIds
                        },
                    )
                }
            }
        }

        // Reply dialog
        if (replyToEvent != null && account != null) {
            ComposeNoteDialog(
                onDismiss = { replyToEvent = null },
                relayManager = relayManager,
                account = account,
                replyTo = replyToEvent,
            )
        }
    }
}
