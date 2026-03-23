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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.feeds.DesktopFollowingFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopGlobalFeedFilter
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import com.vitorpamplona.amethyst.desktop.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createFollowingFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createGlobalFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.media.LightboxOverlay
import com.vitorpamplona.amethyst.desktop.ui.note.NoteCard
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event

data class LightboxState(
    val urls: List<String>,
    val index: Int,
    val seekPosition: Float = 0f,
    val fullscreen: Boolean = false,
)

/**
 * Note card that reads counts from the Note model (cache-backed).
 * Event is extracted from Note for signing operations in NoteActionsRow.
 */
@Composable
fun FeedNoteCard(
    note: Note,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    onReply: () -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onMediaClick: ((List<String>, Int, Float) -> Unit)? = null,
) {
    val event = note.event ?: return

    // Observe Note.flowSet for live count updates
    val flowSet = remember(note) { note.flow() }
    val reactionsState by flowSet.reactions.stateFlow.collectAsState()
    val repliesState by flowSet.replies.stateFlow.collectAsState()
    val zapsState by flowSet.zaps.stateFlow.collectAsState()

    // Read counts from Note model (re-read on each stateFlow emission)
    val reactionCount = note.countReactions()
    val replyCount = note.replies.size
    val repostCount = note.boosts.size
    val zapAmount = note.zapsAmount

    // Clean up flowSet when card leaves composition
    DisposableEffect(note) {
        onDispose { note.clearFlow() }
    }

    Column {
        NoteCard(
            note = event.toNoteDisplayData(localCache),
            localCache = localCache,
            onClick = { onNavigateToThread(event.id) },
            onAuthorClick = onNavigateToProfile,
            onMentionClick = onNavigateToProfile,
            onImageClick = onImageClick,
            onMediaClick = onMediaClick,
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
                zapCount = note.zaps.size,
                zapAmountSats = zapAmount.toLong(),
                zapReceipts = emptyList(), // TODO: extract ZapReceipts from Note.zaps
                reactionCount = reactionCount,
                replyCount = replyCount,
                repostCount = repostCount,
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
    initialFeedMode: FeedMode? = null,
    onCompose: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val followedUsers by localCache.followedUsers.collectAsState()

    var replyToEvent by remember { mutableStateOf<Event?>(null) }
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }
    var feedMode by remember { mutableStateOf(initialFeedMode ?: DesktopPreferences.feedMode) }

    // Subscribe to contact list (kind 3) — populates localCache.followedUsers
    rememberSubscription(connectedRelays, account, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty() && account != null) {
            createContactListSubscription(
                relays = connectedRelays,
                pubKeyHex = account.pubKeyHex,
                onEvent = { event, _, relay, _ ->
                    subscriptionsCoordinator?.consumeEvent(event, relay)
                },
            )
        } else {
            null
        }
    }

    // Subscribe to feed events (kind 1) — populates cache via coordinator
    rememberSubscription(connectedRelays, feedMode, followedUsers, relayManager = relayManager) {
        if (connectedRelays.isEmpty()) return@rememberSubscription null

        when (feedMode) {
            FeedMode.GLOBAL -> {
                createGlobalFeedSubscription(
                    relays = connectedRelays,
                    onEvent = { event, _, relay, _ ->
                        subscriptionsCoordinator?.consumeEvent(event, relay)
                    },
                )
            }

            FeedMode.FOLLOWING -> {
                val follows = followedUsers.toList()
                if (follows.isNotEmpty()) {
                    createFollowingFeedSubscription(
                        relays = connectedRelays,
                        followedUsers = follows,
                        onEvent = { event, _, relay, _ ->
                            subscriptionsCoordinator?.consumeEvent(event, relay)
                        },
                    )
                } else {
                    null
                }
            }
        }
    }

    // DesktopFeedViewModel keyed on feedMode — recreated on mode switch
    val viewModel =
        remember(feedMode) {
            val filter =
                when (feedMode) {
                    FeedMode.GLOBAL -> {
                        DesktopGlobalFeedFilter(localCache)
                    }

                    FeedMode.FOLLOWING -> {
                        DesktopFollowingFeedFilter(localCache) {
                            localCache.followedUsers.value
                        }
                    }
                }
            DesktopFeedViewModel(filter, localCache)
        }

    // Cancel old ViewModel's viewModelScope on recreation
    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    // Load metadata for visible notes via Coordinator (rate-limited)
    LaunchedEffect(feedState, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null && feedState is FeedState.Loaded) {
            val notes = viewModel.feedState.visibleNotes()
            if (notes.isNotEmpty()) {
                subscriptionsCoordinator.loadMetadataForNotes(notes)
            }
        }
    }

    // Request interaction subscriptions — keyed on feedMode (stable), not feedState (changes every 250ms)
    DisposableEffect(feedMode, subscriptionsCoordinator) {
        val coordinator = subscriptionsCoordinator ?: return@DisposableEffect onDispose {}
        val relays = relayManager.relayStatuses.value.keys
        // Initial subscription with whatever notes are visible now
        val noteIds = viewModel.feedState.visibleNotes().mapNotNull { it.event?.id }
        val subId =
            if (noteIds.isNotEmpty()) {
                coordinator.requestInteractions(noteIds, relays)
            } else {
                null
            }
        onDispose { subId?.let { coordinator.releaseInteractions(it) } }
    }

    @OptIn(ExperimentalLayoutApi::class)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with compose button
            FeedHeader(
                feedMode = feedMode,
                account = account,
                connectedRelays = connectedRelays,
                followedUsersCount = followedUsers.size,
                onFeedModeChange = { mode ->
                    feedMode = mode
                    DesktopPreferences.feedMode = mode
                },
                onRefresh = { relayManager.connect() },
                onCompose = onCompose,
            )

            Spacer(Modifier.height(8.dp))

            // Feed content based on FeedState
            when (val state = feedState) {
                is FeedState.Loading -> {
                    if (connectedRelays.isEmpty()) {
                        LoadingState("Connecting to relays...")
                    } else {
                        LoadingState("Loading notes...")
                    }
                }

                is FeedState.Empty -> {
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
                }

                is FeedState.FeedError -> {
                    EmptyState(
                        title = "Error loading feed",
                        description = state.errorMessage,
                        onRefresh = { relayManager.connect() },
                    )
                }

                is FeedState.Loaded -> {
                    val loadedState by state.feed.collectAsState()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(loadedState.list, key = { it.idHex }) { note ->
                            FeedNoteCard(
                                note = note,
                                relayManager = relayManager,
                                localCache = localCache,
                                account = account,
                                nwcConnection = nwcConnection,
                                onReply = { replyToEvent = note.event },
                                onZapFeedback = onZapFeedback,
                                onNavigateToProfile = onNavigateToProfile,
                                onNavigateToThread = onNavigateToThread,
                                onImageClick = { urls, index ->
                                    lightboxState = LightboxState(urls, index)
                                },
                                onMediaClick = { urls, index, seekPos ->
                                    com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
                                        .playVideo(urls[index], seekPos)
                                    com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
                                        .toggleFullscreen()
                                },
                            )
                        }
                    }
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

        // Lightbox overlay
        lightboxState?.let { state ->
            LightboxOverlay(
                urls = state.urls,
                initialIndex = state.index,
                initialSeekPosition = state.seekPosition,
                initialFullscreen = state.fullscreen,
                onDismiss = { lightboxState = null },
            )
        }
    }
}

/**
 * Feed header with title, mode selector, relay count, and compose button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedHeader(
    feedMode: FeedMode,
    account: AccountState.LoggedIn?,
    connectedRelays: Set<Any>,
    followedUsersCount: Int,
    onFeedModeChange: (FeedMode) -> Unit,
    onRefresh: () -> Unit,
    onCompose: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (feedMode == FeedMode.GLOBAL) "Global Feed" else "Following Feed",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (account != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = feedMode == FeedMode.GLOBAL,
                            onClick = { onFeedModeChange(FeedMode.GLOBAL) },
                            label = { Text("Global") },
                        )
                        FilterChip(
                            selected = feedMode == FeedMode.FOLLOWING,
                            onClick = { onFeedModeChange(FeedMode.FOLLOWING) },
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
                        " \u2022 $followedUsersCount followed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onRefresh,
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

        Button(
            onClick = onCompose,
            enabled = account != null && !account.isReadOnly,
        ) {
            Icon(Icons.Default.Add, "New Post", Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Post")
        }
    }
}
