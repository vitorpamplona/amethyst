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
package com.vitorpamplona.amethyst.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.ios.ui.note.NoteCard
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.launch

private enum class BookmarkTab { PUBLIC, PRIVATE }

/**
 * Screen displaying user's bookmarked notes (public and private).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    account: AccountState.LoggedIn,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
    onBookmark: ((String) -> Unit)? = null,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays = remember(relayStatuses) { relayStatuses.keys }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(BookmarkTab.PUBLIC) }
    var bookmarkList by remember { mutableStateOf<BookmarkListEvent?>(null) }
    var publicBookmarkIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var privateBookmarkIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasReceivedEose by remember { mutableStateOf(false) }

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

    // Seed from cache
    LaunchedEffect(account.pubKeyHex) {
        try {
            val address = BookmarkListEvent.createBookmarkAddress(account.pubKeyHex)
            val cachedNote = localCache.getOrCreateAddressableNote(address)
            val cachedEvent = cachedNote.event as? BookmarkListEvent
            if (cachedEvent != null) {
                bookmarkList = cachedEvent
                publicBookmarkIds =
                    cachedEvent
                        .publicBookmarks()
                        .filterIsInstance<EventBookmark>()
                        .map { it.eventId }
                publicBookmarkIds.forEach { id ->
                    val note = localCache.getNoteIfExists(id)
                    val event = note?.event
                    if (event != null) publicEventState.addItem(event)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (bookmark seed): " + (e.message ?: "unknown"))
        }
    }

    // Subscribe to user's bookmark list (kind 10003)
    rememberSubscription(connectedRelays, account.pubKeyHex, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            SubscriptionConfig(
                subId = generateSubId("bookmarks-list"),
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(account.pubKeyHex),
                            kinds = listOf(BookmarkListEvent.KIND),
                            limit = 1,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, relay, _ ->
                    coordinator.consumeEvent(event, relay)
                    if (event is BookmarkListEvent) {
                        bookmarkList = event
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

    // Decrypt private bookmarks
    LaunchedEffect(bookmarkList) {
        try {
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
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        privateBookmarkIds = emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (bookmark decrypt): " + (e.message ?: "unknown"))
        }
    }

    // Fetch public bookmarked events
    rememberSubscription(connectedRelays, publicBookmarkIds, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty() && publicBookmarkIds.isNotEmpty()) {
            publicEventState.clear()
            SubscriptionConfig(
                subId = generateSubId("public-bookmarks"),
                filters = listOf(FilterBuilders.byIds(publicBookmarkIds)),
                relays = connectedRelays,
                onEvent = { event, _, relay, _ ->
                    coordinator.consumeEvent(event, relay)
                    publicEventState.addItem(event)
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Fetch private bookmarked events
    rememberSubscription(connectedRelays, privateBookmarkIds, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty() && privateBookmarkIds.isNotEmpty()) {
            privateEventState.clear()
            SubscriptionConfig(
                subId = generateSubId("private-bookmarks"),
                filters = listOf(FilterBuilders.byIds(privateBookmarkIds)),
                relays = connectedRelays,
                onEvent = { event, _, relay, _ ->
                    coordinator.consumeEvent(event, relay)
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
        TopAppBar(
            title = { Text("Bookmarks") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )

        // Tab selector
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
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

        // Content
        when {
            isLoading && !hasReceivedEose -> {
                LoadingState(message = "Loading bookmarks...")
            }

            currentBookmarkIds.isEmpty() && hasReceivedEose -> {
                EmptyState(
                    title =
                        if (selectedTab == BookmarkTab.PUBLIC) {
                            "No public bookmarks"
                        } else {
                            "No private bookmarks"
                        },
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(currentEvents, key = { it.id }) { event ->
                        NoteCard(
                            note = event.toNoteDisplayData(localCache),
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                            onBoost = onBoost,
                            onLike = onLike,
                            onZap = onZap,
                            onBookmark = onBookmark,
                            isBookmarked = true,
                        )
                    }
                }
            }
        }
    }
}
