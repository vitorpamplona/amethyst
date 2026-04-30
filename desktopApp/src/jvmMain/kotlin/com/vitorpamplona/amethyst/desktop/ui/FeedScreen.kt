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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.compose.elements.BoostedMark
import com.vitorpamplona.amethyst.commons.compose.layouts.GenericRepostLayout
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.feeds.DesktopFollowingFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopGlobalFeedFilter
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createFollowingFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createGlobalFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.media.LightboxOverlay
import com.vitorpamplona.amethyst.desktop.ui.note.NoteCard
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

data class LightboxState(
    val urls: List<String>,
    val index: Int,
    val seekPosition: Float = 0f,
    val fullscreen: Boolean = false,
)

/**
 * Note card that reads counts from the Note model (cache-backed).
 * Event is extracted from Note for signing operations in NoteActionsRow.
 * Handles reposts (kind 6/16) by showing overlapping avatars + "Boosted" label
 * and rendering the original note content.
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
    val isRepost = event is RepostEvent || event is GenericRepostEvent

    if (isRepost) {
        val originalNote = note.replyTo?.lastOrNull()
        if (originalNote == null) {
            return
        }

        // Observe original note's flowSet — MUST happen before reading .event
        // so we recompose when the async fetch fills in the event
        val flowSet = remember(originalNote) { originalNote.flow() }
        val metadataState by flowSet.metadata.stateFlow.collectAsState()
        val reactionsState by flowSet.reactions.stateFlow.collectAsState()
        val repliesState by flowSet.replies.stateFlow.collectAsState()
        val zapsState by flowSet.zaps.stateFlow.collectAsState()

        DisposableEffect(originalNote) {
            onDispose { originalNote.clearFlow() }
        }

        // Now read event — recomposition will re-read this when metadata invalidates
        val originalEvent = originalNote.event
        if (originalEvent == null) {
            return
        }

        val reactionCount = originalNote.countReactions()
        val replyCount = originalNote.replies.size
        val repostCount = originalNote.boosts.size
        val zapAmount = originalNote.zapsAmount

        val reposterUser = localCache.getUserIfExists(event.pubKey)
        val originalUser = localCache.getUserIfExists(originalEvent.pubKey)

        Column {
            // Repost header: overlapping avatars + "Boosted" label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                GenericRepostLayout(
                    baseAuthorPicture = {
                        UserAvatar(
                            userHex = originalEvent.pubKey,
                            pictureUrl = originalUser?.profilePicture(),
                            size = 35.dp,
                        )
                    },
                    repostAuthorPicture = {
                        UserAvatar(
                            userHex = event.pubKey,
                            pictureUrl = reposterUser?.profilePicture(),
                            size = 35.dp,
                        )
                    },
                )
                BoostedMark()
            }

            // Original note content
            val displayData = remember(originalEvent, metadataState) { originalEvent.toNoteDisplayData(localCache) }
            NoteCard(
                note = displayData,
                localCache = localCache,
                onClick = { onNavigateToThread(originalEvent.id) },
                onAuthorClick = onNavigateToProfile,
                onMentionClick = onNavigateToProfile,
                onImageClick = onImageClick,
                onMediaClick = onMediaClick,
            )

            // Action buttons for original note
            if (account != null) {
                NoteActionsRow(
                    event = originalEvent,
                    relayManager = relayManager,
                    localCache = localCache,
                    account = account,
                    nwcConnection = nwcConnection,
                    onReplyClick = onReply,
                    onZapFeedback = onZapFeedback,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    zapCount = originalNote.zaps.size,
                    zapAmountSats = zapAmount.toLong(),
                    zapReceipts = emptyList(),
                    reactionCount = reactionCount,
                    replyCount = replyCount,
                    repostCount = repostCount,
                )
            }
        }
    } else {
        // Regular note rendering
        val flowSet = remember(note) { note.flow() }
        val metadataState by flowSet.metadata.stateFlow.collectAsState()
        val reactionsState by flowSet.reactions.stateFlow.collectAsState()
        val repliesState by flowSet.replies.stateFlow.collectAsState()
        val zapsState by flowSet.zaps.stateFlow.collectAsState()

        val reactionCount = note.countReactions()
        val replyCount = note.replies.size
        val repostCount = note.boosts.size
        val zapAmount = note.zapsAmount

        DisposableEffect(note) {
            onDispose { note.clearFlow() }
        }

        Column {
            val displayData = remember(event, metadataState) { event.toNoteDisplayData(localCache) }
            NoteCard(
                note = displayData,
                localCache = localCache,
                onClick = { onNavigateToThread(event.id) },
                onAuthorClick = onNavigateToProfile,
                onMentionClick = onNavigateToProfile,
                onImageClick = onImageClick,
                onMediaClick = onMediaClick,
            )

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
                    zapReceipts = emptyList(),
                    reactionCount = reactionCount,
                    replyCount = replyCount,
                    repostCount = repostCount,
                )
            }
        }
    }
}

@OptIn(FlowPreview::class)
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
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val followedUsers by localCache.followedUsers.collectAsState()

    // Available relay URLs — subscribe triggers connection on-demand
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    var replyToEvent by remember { mutableStateOf<Event?>(null) }
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }
    var feedMode by remember { mutableStateOf(initialFeedMode ?: DesktopPreferences.feedMode) }

    // Subscribe to contact list (kind 3) — populates localCache.followedUsers
    rememberSubscription(allRelayUrls, account, relayManager = relayManager) {
        if (allRelayUrls.isNotEmpty() && account != null) {
            createContactListSubscription(
                relays = allRelayUrls,
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
    rememberSubscription(allRelayUrls, feedMode, followedUsers, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null

        when (feedMode) {
            FeedMode.GLOBAL -> {
                createGlobalFeedSubscription(
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ ->
                        subscriptionsCoordinator?.consumeEvent(event, relay)
                    },
                )
            }

            FeedMode.FOLLOWING -> {
                val follows = followedUsers.toList()
                if (follows.isNotEmpty()) {
                    createFollowingFeedSubscription(
                        relays = allRelayUrls,
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

    // Viewport-aware metadata loading: only fetch for visible notes + buffer
    // Uses snapshotFlow to avoid per-frame recomposition from scroll observation
    LaunchedEffect(feedState, subscriptionsCoordinator) {
        if (subscriptionsCoordinator == null || feedState !is FeedState.Loaded) return@LaunchedEffect
        val loadedFeed = feedState as FeedState.Loaded

        // Initial load: batch metadata for first visible notes immediately
        val initialNotes = viewModel.feedState.visibleNotes().take(30)
        if (initialNotes.isNotEmpty()) {
            val authors = initialNotes.mapNotNull { it.author?.pubkeyHex }.distinct()
            subscriptionsCoordinator.loadMetadataBatched(authors)
            subscriptionsCoordinator.loadMetadataForNotes(initialNotes)
        }
    }

    // Fetch missing referenced notes (repost originals + quoted notes via e-tags)
    // Uses a direct relay subscription — bypasses the coordinator pipeline
    val missingNoteIds =
        remember(feedState) {
            if (feedState !is FeedState.Loaded) return@remember emptyList<String>()
            val notes = viewModel.feedState.visibleNotes()

            // Repost originals where event is null
            val repostOriginals =
                notes
                    .filter { it.event is RepostEvent || it.event is GenericRepostEvent }
                    .mapNotNull { it.replyTo?.lastOrNull() }
            val repostIds = repostOriginals.filter { it.event == null }.map { it.idHex }

            // Quoted note IDs from content bech32s (nostr:nevent/nostr:note references)
            val allEvents = (notes + repostOriginals.filter { it.event != null }).mapNotNull { it.event }
            val contentQuotedIds =
                allEvents
                    .flatMap { event ->
                        UrlParser().parseValidUrls(event.content).bech32s.mapNotNull { bech32 ->
                            when (val entity = Nip19Parser.uriToRoute(bech32)?.entity) {
                                is NNote -> entity.hex
                                is NEvent -> entity.hex
                                else -> null
                            }
                        }
                    }.filter { localCache.getNoteIfExists(it)?.event == null }

            (repostIds + contentQuotedIds).distinct()
        }

    rememberSubscription(allRelayUrls, missingNoteIds, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || missingNoteIds.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("fetch-referenced"),
            filters = listOf(FilterBuilders.byIds(missingNoteIds)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ ->
                subscriptionsCoordinator?.consumeEvent(event, relay)
            },
        )
    }

    // Fetch missing metadata (kind 0) for all note authors including referenced notes
    val missingAuthorPubkeys =
        remember(feedState) {
            if (feedState !is FeedState.Loaded) return@remember emptyList<String>()
            val notes = viewModel.feedState.visibleNotes()

            // Collect all referenced notes (repost originals + e-tag/content referenced)
            val repostOriginals =
                notes
                    .filter { it.event is RepostEvent || it.event is GenericRepostEvent }
                    .mapNotNull { it.replyTo?.lastOrNull() }

            // All notes in cache that are referenced by visible notes
            val allEvents = (notes + repostOriginals.filter { it.event != null }).mapNotNull { it.event }
            val quotedNotes =
                allEvents.flatMap { event ->
                    UrlParser().parseValidUrls(event.content).bech32s.mapNotNull { bech32 ->
                        when (val entity = Nip19Parser.uriToRoute(bech32)?.entity) {
                            is NNote -> localCache.getNoteIfExists(entity.hex)
                            is NEvent -> localCache.getNoteIfExists(entity.hex)
                            else -> null
                        }
                    }
                }

            // Authors from feed notes + repost originals + quoted notes
            (notes.mapNotNull { it.author } + repostOriginals.mapNotNull { it.author } + quotedNotes.mapNotNull { it.author })
                .filter { it.profilePicture() == null }
                .map { it.pubkeyHex }
                .distinct()
        }

    rememberSubscription(allRelayUrls, missingAuthorPubkeys, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || missingAuthorPubkeys.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("fetch-metadata"),
            filters = listOf(FilterBuilders.userMetadataMultiple(missingAuthorPubkeys)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ ->
                subscriptionsCoordinator?.consumeEvent(event, relay)
            },
        )
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
                    val lazyListState =
                        androidx.compose.foundation.lazy
                            .rememberLazyListState()

                    // Viewport-aware scroll observation: fetch metadata for newly visible notes
                    LaunchedEffect(lazyListState, loadedState) {
                        if (subscriptionsCoordinator == null) return@LaunchedEffect
                        val feedList = loadedState.list
                        if (feedList.isEmpty()) return@LaunchedEffect

                        snapshotFlow {
                            val info = lazyListState.layoutInfo
                            if (info.visibleItemsInfo.isEmpty()) return@snapshotFlow -1 to -1
                            info.visibleItemsInfo.first().index to info.visibleItemsInfo.last().index
                        }.distinctUntilChanged()
                            .debounce(500)
                            .collect { (first, last) ->
                                if (first < 0) return@collect
                                val from = (first - 10).coerceAtLeast(0)
                                val to = (last + 10).coerceAtMost(feedList.lastIndex)
                                val viewportNotes = feedList.subList(from, to + 1)
                                val authors = viewportNotes.mapNotNull { it.author?.pubkeyHex }.distinct()
                                if (authors.isNotEmpty()) {
                                    subscriptionsCoordinator.loadMetadataBatched(authors)
                                }
                            }
                    }

                    LazyColumn(
                        state = lazyListState,
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
