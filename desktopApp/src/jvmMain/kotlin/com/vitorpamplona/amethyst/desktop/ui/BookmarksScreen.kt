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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.note.NoteCard
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.launch

private enum class BookmarkTab { PUBLIC, PRIVATE }

/**
 * Screen displaying user's bookmarked notes (public and private).
 */
@Composable
fun BookmarksScreen(
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scope = rememberCoroutineScope()

    // Tab state
    var selectedTab by remember { mutableStateOf(BookmarkTab.PUBLIC) }

    // State for bookmark list
    var bookmarkList by remember { mutableStateOf<BookmarkListEvent?>(null) }
    var publicBookmarkIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var privateBookmarkIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasReceivedEose by remember { mutableStateOf(false) }

    // State for fetched bookmark events
    val publicEventState =
        remember(account.pubKeyHex) {
            EventCollectionState<Event>(
                getId = { it.id },
                maxSize = 100,
                scope = scope,
            )
        }
    val publicEvents by publicEventState.items.collectAsState()

    val privateEventState =
        remember(account.pubKeyHex) {
            EventCollectionState<Event>(
                getId = { it.id },
                maxSize = 100,
                scope = scope,
            )
        }
    val privateEvents by privateEventState.items.collectAsState()

    // Load metadata for bookmark authors via coordinator
    LaunchedEffect(publicEvents, privateEvents, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null) {
            val pubkeys = (publicEvents + privateEvents).map { it.pubKey }.distinct()
            if (pubkeys.isNotEmpty()) {
                subscriptionsCoordinator.loadMetadataForPubkeys(pubkeys)
            }
        }
    }

    // Subscribe to user's bookmark list (kind 30001)
    rememberSubscription(relayStatuses, account.pubKeyHex, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            SubscriptionConfig(
                subId = "bookmarks-list-${account.pubKeyHex.take(8)}",
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
                                .filterIsInstance<EventBookmark>()
                                .map { it.eventId }
                        publicBookmarkIds = pubIds
                    }
                },
                onEose = { _, _ ->
                    hasReceivedEose = true
                    isLoading = false
                },
            )
        } else {
            isLoading = false
            null
        }
    }

    // Decrypt private bookmarks when bookmark list changes
    LaunchedEffect(bookmarkList) {
        bookmarkList?.let { list ->
            scope.launch {
                try {
                    val privateBookmarks = list.privateBookmarks(account.signer)
                    val privIds =
                        privateBookmarks
                            ?.filterIsInstance<EventBookmark>()
                            ?.map { it.eventId }
                            ?: emptyList()
                    privateBookmarkIds = privIds
                } catch (e: Exception) {
                    println("Failed to decrypt private bookmarks: ${e.message}")
                    privateBookmarkIds = emptyList()
                }
            }
        }
    }

    // Subscribe to fetch the actual public bookmarked events
    rememberSubscription(relayStatuses, publicBookmarkIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && publicBookmarkIds.isNotEmpty()) {
            publicEventState.clear()
            SubscriptionConfig(
                subId = "public-bookmarked-events-${System.currentTimeMillis()}",
                filters =
                    listOf(
                        FilterBuilders.byIds(publicBookmarkIds),
                    ),
                relays = configuredRelays,
                onEvent = { event, _, _, _ ->
                    publicEventState.addItem(event)
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Subscribe to fetch the actual private bookmarked events
    rememberSubscription(relayStatuses, privateBookmarkIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && privateBookmarkIds.isNotEmpty()) {
            privateEventState.clear()
            SubscriptionConfig(
                subId = "private-bookmarked-events-${System.currentTimeMillis()}",
                filters =
                    listOf(
                        FilterBuilders.byIds(privateBookmarkIds),
                    ),
                relays = configuredRelays,
                onEvent = { event, _, _, _ ->
                    privateEventState.addItem(event)
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    val currentEvents = if (selectedTab == BookmarkTab.PUBLIC) publicEvents else privateEvents
    val currentBookmarkIds = if (selectedTab == BookmarkTab.PUBLIC) publicBookmarkIds else privateBookmarkIds

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with tabs
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Bookmarks",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.weight(1f))

            // Tab selector
            Row(
                horizontalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTab == BookmarkTab.PUBLIC,
                    onClick = { selectedTab = BookmarkTab.PUBLIC },
                    label = { Text("Public (${publicBookmarkIds.size})") },
                )
                FilterChip(
                    selected = selectedTab == BookmarkTab.PRIVATE,
                    onClick = { selectedTab = BookmarkTab.PRIVATE },
                    label = { Text("Private (${privateBookmarkIds.size})") },
                )
            }
        }

        // Content
        when {
            isLoading && !hasReceivedEose -> {
                LoadingState(message = "Loading bookmarks...")
            }

            currentBookmarkIds.isEmpty() && hasReceivedEose -> {
                EmptyState(
                    title = if (selectedTab == BookmarkTab.PUBLIC) "No public bookmarks" else "No private bookmarks",
                    description =
                        if (selectedTab == BookmarkTab.PUBLIC) {
                            "Bookmark notes publicly to save them here"
                        } else {
                            "Private bookmarks are encrypted and only visible to you"
                        },
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(currentEvents, key = { it.id }) { event ->
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
                            NoteActionsRow(
                                event = event,
                                relayManager = relayManager,
                                localCache = localCache,
                                account = account,
                                nwcConnection = nwcConnection,
                                onReplyClick = { onNavigateToThread(event.id) },
                                onZapFeedback = onZapFeedback,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                isBookmarked = true,
                                bookmarkList = bookmarkList,
                                onBookmarkChanged = { newList ->
                                    bookmarkList = newList
                                    // Update public bookmark IDs
                                    val pubIds =
                                        newList
                                            .publicBookmarks()
                                            .filterIsInstance<EventBookmark>()
                                            .map { it.eventId }
                                    publicBookmarkIds = pubIds

                                    // Decrypt and update private bookmark IDs
                                    scope.launch {
                                        try {
                                            val privateBookmarks = newList.privateBookmarks(account.signer)
                                            val privIds =
                                                privateBookmarks
                                                    ?.filterIsInstance<EventBookmark>()
                                                    ?.map { it.eventId }
                                                    ?: emptyList()
                                            privateBookmarkIds = privIds
                                        } catch (e: Exception) {
                                            // Keep existing private IDs if decryption fails
                                        }
                                    }

                                    // Remove unbookmarked event from appropriate list
                                    if (!pubIds.contains(event.id)) {
                                        publicEventState.removeItem(event.id)
                                    }
                                    if (!privateBookmarkIds.contains(event.id)) {
                                        privateEventState.removeItem(event.id)
                                    }
                                },
                            )
                        }
                        HorizontalDivider(thickness = 1.dp)
                    }
                }
            }
        }
    }
}
