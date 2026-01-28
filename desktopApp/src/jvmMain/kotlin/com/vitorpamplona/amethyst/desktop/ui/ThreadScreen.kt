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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
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
import com.vitorpamplona.amethyst.commons.ui.thread.drawReplyLevel
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createNoteSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createReactionsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createRepliesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createRepostsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createThreadRepliesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createZapsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.note.NoteCard
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent

/**
 * Desktop Thread Screen - displays a note and all its replies in a thread view.
 *
 * Uses the shared drawReplyLevel modifier from commons to display reply nesting.
 */
@Composable
fun ThreadScreen(
    noteId: String,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
    onReply: (Event) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scope = rememberCoroutineScope()

    // State for the root note
    var rootNote by remember { mutableStateOf<Event?>(null) }

    // State for reply events
    val replyEventState =
        remember(noteId) {
            EventCollectionState<Event>(
                getId = { it.id },
                sortComparator = compareBy { it.createdAt },
                maxSize = 500,
                scope = scope,
            )
        }
    val replyEvents by replyEventState.items.collectAsState()

    // Cache for calculating reply levels
    val levelCache = remember(noteId) { mutableMapOf<String, Int>() }

    // Track EOSE to know when initial load is complete
    var rootNoteEoseReceived by remember(noteId) { mutableStateOf(false) }
    var repliesEoseReceived by remember(noteId) { mutableStateOf(false) }

    // Track zaps per event
    var zapsByEvent by remember(noteId) { mutableStateOf<Map<String, List<ZapReceipt>>>(emptyMap()) }
    // Track reaction event IDs per target event to deduplicate
    var reactionIdsByEvent by remember(noteId) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val reactionsByEvent = reactionIdsByEvent.mapValues { it.value.size }
    // Track reply/repost event IDs per target event to deduplicate
    var replyIdsByEvent by remember(noteId) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val repliesByEvent = replyIdsByEvent.mapValues { it.value.size }
    var repostIdsByEvent by remember(noteId) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val repostsByEvent = repostIdsByEvent.mapValues { it.value.size }

    // Bookmark state
    var bookmarkList by remember { mutableStateOf<BookmarkListEvent?>(null) }
    var bookmarkedEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load metadata for thread authors via coordinator
    LaunchedEffect(rootNote, replyEvents, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null) {
            val pubkeys = mutableListOf<String>()
            rootNote?.let { pubkeys.add(it.pubKey) }
            pubkeys.addAll(replyEvents.map { it.pubKey })
            if (pubkeys.isNotEmpty()) {
                subscriptionsCoordinator.loadMetadataForPubkeys(pubkeys.distinct())
            }
        }
    }

    // Subscribe to user's bookmark list
    rememberSubscription(relayStatuses, account, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && account != null) {
            SubscriptionConfig(
                subId = "thread-bookmarks-${account.pubKeyHex.take(8)}",
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
                        val pubIds =
                            event
                                .publicBookmarks()
                                .filterIsInstance<EventBookmark>()
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

    // Subscribe to the root note
    rememberSubscription(relayStatuses, noteId, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createNoteSubscription(
                relays = configuredRelays,
                noteId = noteId,
                onEvent = { event, _, _, _ ->
                    if (event.id == noteId) {
                        rootNote = event
                        levelCache[event.id] = 0
                    }
                },
                onEose = { _, _ ->
                    rootNoteEoseReceived = true
                },
            )
        } else {
            null
        }
    }

    // Subscribe to replies
    rememberSubscription(relayStatuses, noteId, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createThreadRepliesSubscription(
                relays = configuredRelays,
                noteId = noteId,
                onEvent = { event, _, _, _ ->
                    replyEventState.addItem(event)
                },
                onEose = { _, _ ->
                    repliesEoseReceived = true
                },
            )
        } else {
            null
        }
    }

    // Subscribe to zaps for thread events
    val allEventIds = listOf(noteId) + replyEvents.map { it.id }
    rememberSubscription(relayStatuses, allEventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || allEventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createZapsSubscription(
            relays = configuredRelays,
            eventIds = allEventIds,
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

    // Subscribe to reactions for thread events
    rememberSubscription(relayStatuses, allEventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || allEventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createReactionsSubscription(
            relays = configuredRelays,
            eventIds = allEventIds,
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

    // Subscribe to replies for thread events (for counts)
    rememberSubscription(relayStatuses, allEventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || allEventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createRepliesSubscription(
            relays = configuredRelays,
            eventIds = allEventIds,
            onEvent = { event, _, _, _ ->
                val replyToId =
                    event.tags
                        .filter { it.size >= 2 && it[0] == "e" }
                        .lastOrNull()
                        ?.get(1) ?: return@createRepliesSubscription
                if (replyToId in allEventIds) {
                    replyIdsByEvent =
                        replyIdsByEvent.toMutableMap().apply {
                            val existing = this[replyToId] ?: emptySet()
                            this[replyToId] = existing + event.id
                        }
                }
            },
        )
    }

    // Subscribe to reposts for thread events
    rememberSubscription(relayStatuses, allEventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || allEventIds.isEmpty()) {
            return@rememberSubscription null
        }

        createRepostsSubscription(
            relays = configuredRelays,
            eventIds = allEventIds,
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

    // Calculate reply level for an event based on e-tags
    fun calculateLevel(event: Event): Int {
        levelCache[event.id]?.let { return it }

        // Find the event this is replying to (last e-tag or marked reply/root)
        val replyToId = findReplyToId(event)
        val level =
            if (replyToId == null || replyToId == noteId) {
                1 // Direct reply to root
            } else {
                (levelCache[replyToId] ?: 0) + 1
            }
        levelCache[event.id] = level
        return level
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Thread",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (connectedRelays.isEmpty()) {
            LoadingState("Connecting to relays...")
        } else if (rootNote == null && !rootNoteEoseReceived) {
            LoadingState("Loading thread...")
        } else if (rootNote == null && rootNoteEoseReceived) {
            EmptyState(
                title = "Note not found",
                description = "This note may have been deleted or is not available from connected relays",
                onRefresh = onBack,
                refreshLabel = "Go back",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // Root note (no reply level indicator)
                item(key = noteId) {
                    Column(
                        modifier =
                            Modifier.clickable {
                                // Already viewing this thread, no-op
                            },
                    ) {
                        NoteCard(
                            note = rootNote!!.toNoteDisplayData(localCache),
                            onAuthorClick = onNavigateToProfile,
                        )
                        if (account != null) {
                            val rootZaps = zapsByEvent[noteId] ?: emptyList()
                            NoteActionsRow(
                                event = rootNote!!,
                                relayManager = relayManager,
                                localCache = localCache,
                                account = account,
                                nwcConnection = nwcConnection,
                                onReplyClick = { onReply(rootNote!!) },
                                onZapFeedback = onZapFeedback,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                zapCount = rootZaps.size,
                                zapAmountSats = rootZaps.sumOf { it.amountSats },
                                zapReceipts = rootZaps,
                                reactionCount = reactionsByEvent[noteId] ?: 0,
                                replyCount = repliesByEvent[noteId] ?: 0,
                                repostCount = repostsByEvent[noteId] ?: 0,
                                bookmarkList = bookmarkList,
                                isBookmarked = bookmarkedEventIds.contains(noteId),
                                onBookmarkChanged = { newList ->
                                    bookmarkList = newList
                                    val pubIds =
                                        newList
                                            .publicBookmarks()
                                            .filterIsInstance<EventBookmark>()
                                            .map { it.eventId }
                                            .toSet()
                                    bookmarkedEventIds = pubIds
                                },
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp)
                }

                // Reply notes with level indicators
                items(replyEvents.distinctBy { it.id }, key = { it.id }) { event ->
                    val level = calculateLevel(event)

                    Column(
                        modifier =
                            Modifier
                                .drawReplyLevel(
                                    level = level,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    selected =
                                        if (event.id == noteId) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                ).clickable {
                                    onNavigateToThread(event.id)
                                },
                    ) {
                        NoteCard(
                            note = event.toNoteDisplayData(localCache),
                            onAuthorClick = onNavigateToProfile,
                        )
                        if (account != null) {
                            val eventZaps = zapsByEvent[event.id] ?: emptyList()
                            NoteActionsRow(
                                event = event,
                                relayManager = relayManager,
                                localCache = localCache,
                                account = account,
                                nwcConnection = nwcConnection,
                                onReplyClick = { onReply(event) },
                                onZapFeedback = onZapFeedback,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                zapCount = eventZaps.size,
                                zapAmountSats = eventZaps.sumOf { it.amountSats },
                                zapReceipts = eventZaps,
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
                                            .filterIsInstance<EventBookmark>()
                                            .map { it.eventId }
                                            .toSet()
                                    bookmarkedEventIds = pubIds
                                },
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp)
                }

                // Empty state for no replies
                if (replyEvents.isEmpty() && repliesEoseReceived) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "No replies yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (replyEvents.isEmpty() && !repliesEoseReceived) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "Loading replies...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Finds the event ID this event is replying to.
 * Uses NIP-10 markers (reply/root) or falls back to last e-tag.
 */
private fun findReplyToId(event: Event): String? {
    val eTags = event.tags.filter { it.size >= 2 && it[0] == "e" }
    if (eTags.isEmpty()) return null

    // Check for NIP-10 marked tags first
    val replyTag = eTags.find { it.size >= 4 && it[3] == "reply" }
    if (replyTag != null) return replyTag[1]

    val rootTag = eTags.find { it.size >= 4 && it[3] == "root" }
    if (rootTag != null && eTags.size == 1) return rootTag[1]

    // Fall back to positional (last e-tag is the reply-to)
    return eTags.lastOrNull()?.get(1)
}
