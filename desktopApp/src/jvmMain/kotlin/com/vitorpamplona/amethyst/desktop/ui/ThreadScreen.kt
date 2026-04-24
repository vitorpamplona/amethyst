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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.thread.drawReplyLevel
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.feeds.DesktopThreadFilter
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.createNoteSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createThreadRepliesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.media.LightboxOverlay
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Desktop Thread Screen - displays a note and all its replies in a thread view.
 *
 * Uses DesktopFeedViewModel + DesktopThreadFilter for cache-backed display.
 * Keeps relay subscriptions to populate cache with thread data.
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
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays = remember(relayStatuses) { relayStatuses.keys }

    // Lightbox state
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }

    // Track EOSE for root note subscription
    var rootNoteEoseReceived by remember(noteId) { mutableStateOf(false) }

    // DesktopFeedViewModel reads thread from cache (root + replies via graph walk)
    val threadViewModel =
        remember(noteId) {
            DesktopFeedViewModel(
                DesktopThreadFilter(noteId, localCache),
                localCache,
            )
        }
    DisposableEffect(threadViewModel) {
        onDispose { threadViewModel.destroy() }
    }
    val feedState by threadViewModel.feedState.feedContent.collectAsState()
    val threadNotes =
        if (feedState is FeedState.Loaded) {
            val loaded by (feedState as FeedState.Loaded).feed.collectAsState()
            loaded.list
        } else {
            kotlinx.collections.immutable.persistentListOf()
        }

    // Level cache for reply nesting
    val levelCache = remember(noteId) { mutableMapOf<String, Int>() }

    // Keep relay subscriptions to populate cache — root note
    rememberSubscription(connectedRelays, noteId, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            createNoteSubscription(
                relays = connectedRelays,
                noteId = noteId,
                onEvent = { event, _, relay, _ ->
                    subscriptionsCoordinator?.consumeEvent(event, relay)
                    levelCache[event.id] = 0
                },
                onEose = { _, _ ->
                    rootNoteEoseReceived = true
                },
            )
        } else {
            null
        }
    }

    // Keep relay subscription for replies
    rememberSubscription(connectedRelays, noteId, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            createThreadRepliesSubscription(
                relays = connectedRelays,
                noteId = noteId,
                onEvent = { event, _, relay, _ ->
                    subscriptionsCoordinator?.consumeEvent(event, relay)
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Request interaction data — keyed on noteId (stable), not threadNotes (changes on every bundle)
    DisposableEffect(noteId, subscriptionsCoordinator) {
        val coordinator = subscriptionsCoordinator ?: return@DisposableEffect onDispose {}
        val noteIds = threadNotes.mapNotNull { it.event?.id }
        val relays = relayManager.relayStatuses.value.keys
        val subId =
            if (noteIds.isNotEmpty()) {
                coordinator.requestInteractions(noteIds, relays)
            } else {
                null
            }
        onDispose { subId?.let { coordinator.releaseInteractions(it) } }
    }

    // Load metadata for thread authors via coordinator
    LaunchedEffect(threadNotes, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null && threadNotes.isNotEmpty()) {
            subscriptionsCoordinator.loadMetadataForNotes(threadNotes)
        }
    }

    // Calculate reply level for a note based on e-tags
    fun calculateLevel(note: Note): Int {
        val event = note.event ?: return 1
        levelCache[event.id]?.let { return it }

        val replyToId = findReplyToId(event)
        val level =
            if (replyToId == null || replyToId == noteId) {
                1
            } else {
                (levelCache[replyToId] ?: 0) + 1
            }
        levelCache[event.id] = level
        return level
    }

    val rootNote = threadNotes.firstOrNull { it.idHex == noteId }
    val replyNotes = threadNotes.filter { it.idHex != noteId }

    Box(modifier = Modifier.fillMaxSize()) {
        ReadingColumn {
            val sidePadding = readingHorizontalPadding()
            // Header — Messages-style: compact row with back + titleMedium
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        MaterialSymbols.AutoMirrored.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Thread",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            when {
                connectedRelays.isEmpty() -> {
                    LoadingState("Connecting to relays...")
                }

                feedState is FeedState.Loading && !rootNoteEoseReceived -> {
                    LoadingState("Loading thread...")
                }

                rootNote == null && rootNoteEoseReceived -> {
                    EmptyState(
                        title = "Note not found",
                        description = "This note may have been deleted or is not available from connected relays",
                        onRefresh = onBack,
                        refreshLabel = "Go back",
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = sidePadding),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        // Root note
                        if (rootNote != null) {
                            item(key = noteId) {
                                Column {
                                    FeedNoteCard(
                                        note = rootNote,
                                        relayManager = relayManager,
                                        localCache = localCache,
                                        account = account,
                                        nwcConnection = nwcConnection,
                                        onReply = { rootNote.event?.let { onReply(it) } },
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
                                HorizontalDivider(thickness = 1.dp)
                            }
                        }

                        // Reply notes with level indicators
                        items(replyNotes, key = { it.idHex }) { note ->
                            val level = calculateLevel(note)
                            Column(
                                modifier =
                                    Modifier
                                        .drawReplyLevel(
                                            level = level,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            selected = MaterialTheme.colorScheme.outlineVariant,
                                        ).clickable {
                                            note.event?.let { onNavigateToThread(it.id) }
                                        },
                            ) {
                                FeedNoteCard(
                                    note = note,
                                    relayManager = relayManager,
                                    localCache = localCache,
                                    account = account,
                                    nwcConnection = nwcConnection,
                                    onReply = { note.event?.let { onReply(it) } },
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
                            HorizontalDivider(thickness = 1.dp)
                        }

                        // Empty/loading state for replies
                        if (replyNotes.isEmpty()) {
                            item {
                                Spacer(Modifier.height(32.dp))
                                Text(
                                    "No replies yet",
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

        // Lightbox overlay
        val lb = lightboxState
        if (lb != null) {
            LightboxOverlay(
                urls = lb.urls,
                initialIndex = lb.index,
                initialSeekPosition = lb.seekPosition,
                initialFullscreen = lb.fullscreen,
                onDismiss = { lightboxState = null },
            )
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

    val replyTag = eTags.find { it.size >= 4 && it[3] == "reply" }
    if (replyTag != null) return replyTag[1]

    val rootTag = eTags.find { it.size >= 4 && it[3] == "root" }
    if (rootTag != null && eTags.size == 1) return rootTag[1]

    return eTags.lastOrNull()?.get(1)
}
