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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.cache.dispatch
import com.vitorpamplona.amethyst.desktop.feeds.DesktopThreadFilter
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createNoteSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createThreadRepliesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.media.LightboxOverlay
import com.vitorpamplona.amethyst.desktop.ui.thread.CommentItem
import com.vitorpamplona.amethyst.desktop.ui.thread.CommentsCard
import com.vitorpamplona.amethyst.desktop.ui.thread.InlineReplyInput
import com.vitorpamplona.amethyst.desktop.ui.thread.RelatedContentSection
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val connectedRelays = relayStatuses.keys
    val threadScope = rememberCoroutineScope()

    // Lightbox state
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }

    // Track EOSE for root note subscription
    var rootNoteEoseReceived by remember(noteId) { mutableStateOf(false) }

    // DesktopFeedViewModel reads thread from cache (root + replies via graph walk)
    val iAccount = com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount.current
    val threadViewModel =
        remember(noteId, iAccount) {
            DesktopFeedViewModel(
                DesktopThreadFilter(noteId, localCache) {
                    iAccount?.hiddenUsers?.value ?: com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers.EMPTY
                },
                localCache,
                iAccount?.hiddenUsers,
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

    // Fetch quoted notes referenced in thread content
    val quotedNoteIds =
        remember(threadNotes) {
            threadNotes
                .mapNotNull { it.event }
                .flatMap { event ->
                    UrlParser()
                        .parseValidUrls(event.content)
                        .bech32s
                        .mapNotNull { bech32 ->
                            when (val entity = Nip19Parser.uriToRoute(bech32)?.entity) {
                                is NNote -> entity.hex
                                is NEvent -> entity.hex
                                else -> null
                            }
                        }
                }.filter { localCache.getNoteIfExists(it)?.event == null }
                .distinct()
        }

    rememberSubscription(connectedRelays, quotedNoteIds, relayManager = relayManager) {
        if (connectedRelays.isEmpty() || quotedNoteIds.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("thread-quoted"),
            filters = listOf(FilterBuilders.byIds(quotedNoteIds)),
            relays = connectedRelays,
            onEvent = { event, _, relay, _ ->
                subscriptionsCoordinator?.consumeEvent(event, relay)
            },
        )
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
                                        myPubKeyHex = account?.pubKeyHex,
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
                                        // Root of an explicitly-opened thread — user opted in, skip spam collapse.
                                        forceReveal = true,
                                    )
                                }
                                HorizontalDivider(thickness = 1.dp)
                            }
                        }

                        // Comments card (replies + inline reply input)
                        item(key = "comments-card") {
                            Spacer(Modifier.height(12.dp))
                            CommentsCard(
                                commentCount = replyNotes.size,
                                replyContent = {
                                    if (account != null && rootNote != null) {
                                        val myPubKey = account.pubKeyHex
                                        val myUser =
                                            remember(myPubKey) { localCache.getUserIfExists(myPubKey) }
                                        val myAvatarUrl = remember(myUser) { myUser?.profilePicture() }

                                        InlineReplyInput(
                                            myAvatarUrl = myAvatarUrl,
                                            onSend = { content ->
                                                withContext(Dispatchers.IO) {
                                                    val parentText =
                                                        rootNote.event as? TextNoteEvent
                                                            ?: return@withContext
                                                    val signedEvent =
                                                        ReplyActions.replyTo(
                                                            EventHintBundle(parentText, null),
                                                            content,
                                                            account.signer,
                                                        )
                                                    dispatch(signedEvent, localCache, relayManager)
                                                }
                                            },
                                        )
                                    }
                                },
                            ) {
                                if (replyNotes.isEmpty()) {
                                    Text(
                                        "No replies yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp),
                                    )
                                } else {
                                    replyNotes.forEachIndexed { index, note ->
                                        val event = note.event

                                        // Observe metadata + reactions so we recompose
                                        // when author info arrives from relays
                                        val flowSet = remember(note) { note.flow() }
                                        val metadataState by flowSet.metadata.stateFlow.collectAsState()
                                        val reactionsState by flowSet.reactions.stateFlow.collectAsState()
                                        val zapsState by flowSet.zaps.stateFlow.collectAsState()

                                        DisposableEffect(note) { onDispose { note.clearFlow() } }

                                        val author =
                                            remember(event?.pubKey, metadataState) {
                                                event?.pubKey?.let { localCache.getUserIfExists(it) }
                                            }

                                        val reactionCount =
                                            remember(reactionsState) { note.countReactions() }
                                        val zapAmount = remember(zapsState) { note.zapsAmount }

                                        CommentItem(
                                            authorName =
                                                author?.toBestDisplayName()
                                                    ?: event?.pubKey?.take(8)
                                                    ?: "",
                                            authorHandle =
                                                author?.pubkeyNpub()?.take(16)?.let { "@$it..." }
                                                    ?: "",
                                            authorAvatarUrl = author?.profilePicture(),
                                            authorPubKeyHex = event?.pubKey ?: "",
                                            content = event?.content ?: "",
                                            timeAgo = (event?.createdAt ?: 0L).toTimeAgo(),
                                            reactionCount = reactionCount,
                                            zapAmount = zapAmount.toLong(),
                                            onReply = { note.event?.let { onReply(it) } },
                                            onLike = {
                                                val ev = note.event
                                                if (account != null && ev != null) {
                                                    threadScope.launch(Dispatchers.IO) {
                                                        val signed =
                                                            ReactionAction.reactTo(
                                                                EventHintBundle(ev, null),
                                                                "+",
                                                                account.signer,
                                                            )
                                                        dispatch(signed, localCache, relayManager)
                                                    }
                                                }
                                            },
                                            onZap = {
                                                val ev = note.event
                                                if (account != null && ev != null && nwcConnection != null) {
                                                    threadScope.launch {
                                                        zapNote(
                                                            event = ev,
                                                            account = account,
                                                            relayManager = relayManager,
                                                            localCache = localCache,
                                                            amountSats = 21,
                                                            nwcConnection = nwcConnection,
                                                        )
                                                    }
                                                }
                                            },
                                            onAuthorClick = {
                                                note.event?.pubKey?.let { onNavigateToProfile(it) }
                                            },
                                        )
                                        if (index < replyNotes.lastIndex) {
                                            Spacer(Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Related content section
                        if (rootNote != null) {
                            item(key = "related-content") {
                                val rootEvent = rootNote.event
                                if (rootEvent != null) {
                                    val noteHashtags =
                                        remember(rootEvent) {
                                            rootEvent.tags.hashtags().toSet()
                                        }
                                    RelatedContentSection(
                                        noteId = noteId,
                                        authorPubKey = rootEvent.pubKey,
                                        noteHashtags = noteHashtags,
                                        localCache = localCache,
                                        onItemClick = onNavigateToThread,
                                        onViewAll = { onNavigateToProfile(rootEvent.pubKey) },
                                    )
                                }
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
