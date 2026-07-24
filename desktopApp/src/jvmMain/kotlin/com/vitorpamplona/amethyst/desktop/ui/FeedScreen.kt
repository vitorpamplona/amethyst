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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.FollowAction
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.commons.nip64Chess.RelaySyncStatus
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.search.AdvancedSearchBarState
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.commons.search.SearchResultFilter
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.feeds.NewPostsChip
import com.vitorpamplona.amethyst.commons.ui.feeds.StickToTopOnPrepend
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberNewPostsChipState
import com.vitorpamplona.amethyst.commons.ui.layouts.GenericRepostLayout
import com.vitorpamplona.amethyst.commons.ui.note.ReplyContext
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.SearchHistoryStore
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.cache.dispatch
import com.vitorpamplona.amethyst.desktop.feeds.DesktopCustomFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopFollowingFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopGlobalFeedFilter
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinPreferences
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinService
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SearchFilterFactory
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createCustomFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createFollowingFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createGlobalFeedSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createSearchPeopleSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createThreadRepliesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.media.LightboxOverlay
import com.vitorpamplona.amethyst.desktop.ui.note.DesktopPollCard
import com.vitorpamplona.amethyst.desktop.ui.note.NoteCard
import com.vitorpamplona.amethyst.desktop.ui.note.SpamCheckedNoteRender
import com.vitorpamplona.amethyst.desktop.ui.note.WoTBadgedAvatar
import com.vitorpamplona.amethyst.desktop.ui.relay.LocalRelayCategories
import com.vitorpamplona.amethyst.desktop.ui.relay.Nip65RelayEditor
import com.vitorpamplona.amethyst.desktop.ui.search.SearchResultsList
import com.vitorpamplona.amethyst.desktop.ui.thread.CommentItem
import com.vitorpamplona.amethyst.desktop.ui.thread.CommentsCard
import com.vitorpamplona.amethyst.desktop.ui.thread.InlineReplyInput
import com.vitorpamplona.amethyst.desktop.ui.thread.RelatedContentSection
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    onHashtagClick: ((String) -> Unit)? = null,
    followedUsers: Set<String> = emptySet(),
    myPubKeyHex: String? = null,
    onFollow: ((String) -> Unit)? = null,
    forceReveal: Boolean = false,
) {
    val event = note.event ?: return
    var showReportDialog by remember(note.idHex) { mutableStateOf(false) }
    // Same action list the ⋮ overflow uses, so right-click and ⋮ are identical.
    val menuActions =
        com.vitorpamplona.amethyst.desktop.ui.note.rememberNoteMenuActions(event, relayManager) {
            showReportDialog = true
        }

    SpamCheckedNoteRender(
        note = note,
        localCache = localCache,
        forceReveal = forceReveal,
    ) {
        ContextMenuArea(items = {
            menuActions.map { action -> ContextMenuItem(action.label) { action.onClick() } }
        }) {
            FeedNoteCardBody(
                note = note,
                event = event,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                onReply = onReply,
                onZapFeedback = onZapFeedback,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onImageClick = onImageClick,
                onMediaClick = onMediaClick,
                onHashtagClick = onHashtagClick,
                followedUsers = followedUsers,
                myPubKeyHex = myPubKeyHex,
                onFollow = onFollow,
            )
        }
    }

    if (showReportDialog) {
        com.vitorpamplona.amethyst.desktop.ui.note
            .NoteReportDialog(event) { showReportDialog = false }
    }
}

@Composable
private fun FeedNoteCardBody(
    note: Note,
    event: com.vitorpamplona.quartz.nip01Core.core.Event,
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
    onHashtagClick: ((String) -> Unit)? = null,
    followedUsers: Set<String> = emptySet(),
    myPubKeyHex: String? = null,
    onFollow: ((String) -> Unit)? = null,
) {
    if (event is PollEvent) {
        DesktopPollCard(
            note = note,
            event = event,
            relayManager = relayManager,
            localCache = localCache,
            account = account,
            myPubKeyHex = myPubKeyHex,
            onNavigateToThread = onNavigateToThread,
            onNavigateToProfile = onNavigateToProfile,
            onHashtagClick = onHashtagClick,
        )
        return
    }

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

        // A boosted poll must still render as an interactive poll card, not a plain note.
        if (originalEvent is PollEvent) {
            DesktopPollCard(
                note = originalNote,
                event = originalEvent,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                myPubKeyHex = myPubKeyHex,
                onNavigateToThread = onNavigateToThread,
                onNavigateToProfile = onNavigateToProfile,
                onHashtagClick = onHashtagClick,
            )
            return
        }

        val reactionCount = remember(reactionsState) { originalNote.countReactions() }
        val replyCount = remember(repliesState) { originalNote.replies.size }
        val repostCount = remember(metadataState) { originalNote.boosts.size }
        val zapAmount = remember(zapsState) { originalNote.zapsAmount }

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
                        WoTBadgedAvatar(
                            userHex = event.pubKey,
                            pictureUrl = reposterUser?.profilePicture(),
                            size = 35.dp,
                        )
                    },
                    repostAuthorPicture = {
                        WoTBadgedAvatar(
                            userHex = originalEvent.pubKey,
                            pictureUrl = originalUser?.profilePicture(),
                            size = 35.dp,
                        )
                    },
                )
            }

            // Original note content with actions inside card
            val displayData = remember(originalEvent, metadataState) { originalEvent.toNoteDisplayData(localCache) }
            val showRepostFollowPill =
                account != null &&
                    onFollow != null &&
                    originalEvent.pubKey != myPubKeyHex &&
                    originalEvent.pubKey !in followedUsers
            val innerReplyContext = rememberReplyContext(originalEvent, localCache)
            NoteCard(
                note = displayData,
                modifier = Modifier.fillMaxWidth(),
                localCache = localCache,
                onClick = { onNavigateToThread(originalEvent.id) },
                onAuthorClick = onNavigateToProfile,
                onMentionClick = onNavigateToProfile,
                onHashtagClick = onHashtagClick,
                onImageClick = onImageClick,
                onMediaClick = onMediaClick,
                headerTrailingContent =
                    if (showRepostFollowPill) {
                        {
                            FollowPill(onClick = { onFollow.invoke(originalEvent.pubKey) })
                        }
                    } else {
                        null
                    },
                bottomContent =
                    if (account != null) {
                        {
                            NoteActionsRow(
                                event = originalEvent,
                                relayManager = relayManager,
                                localCache = localCache,
                                account = account,
                                nwcConnection = nwcConnection,
                                onReplyClick = onReply,
                                onZapFeedback = onZapFeedback,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                note = originalNote,
                                zapCount = originalNote.zaps.size,
                                zapAmountSats = zapAmount.toLong(),
                                zapReceipts = emptyList(),
                                reactionCount = reactionCount,
                                replyCount = replyCount,
                                repostCount = repostCount,
                                onNavigateToThread = onNavigateToThread,
                                onNavigateToProfile = onNavigateToProfile,
                            )
                        }
                    } else {
                        null
                    },
                replyContext = innerReplyContext,
                onNavigateToThread = onNavigateToThread,
            )
        }
    } else {
        // Regular note rendering
        val flowSet = remember(note) { note.flow() }
        val metadataState by flowSet.metadata.stateFlow.collectAsState()
        val reactionsState by flowSet.reactions.stateFlow.collectAsState()
        val repliesState by flowSet.replies.stateFlow.collectAsState()
        val zapsState by flowSet.zaps.stateFlow.collectAsState()

        val reactionCount = remember(reactionsState) { note.countReactions() }
        val replyCount = remember(repliesState) { note.replies.size }
        val repostCount = remember(metadataState) { note.boosts.size }
        val zapAmount = remember(zapsState) { note.zapsAmount }

        DisposableEffect(note) {
            onDispose { note.clearFlow() }
        }

        val displayData = remember(event, metadataState) { event.toNoteDisplayData(localCache) }
        val showFollowPill =
            account != null &&
                onFollow != null &&
                event.pubKey != myPubKeyHex &&
                event.pubKey !in followedUsers
        val replyContext = rememberReplyContext(event, localCache)
        NoteCard(
            note = displayData,
            modifier = Modifier.fillMaxWidth(),
            localCache = localCache,
            onClick = { onNavigateToThread(event.id) },
            onAuthorClick = onNavigateToProfile,
            onMentionClick = onNavigateToProfile,
            onHashtagClick = onHashtagClick,
            onImageClick = onImageClick,
            onMediaClick = onMediaClick,
            headerTrailingContent =
                if (showFollowPill) {
                    {
                        FollowPill(onClick = { onFollow.invoke(event.pubKey) })
                    }
                } else {
                    null
                },
            bottomContent =
                if (account != null) {
                    {
                        NoteActionsRow(
                            event = event,
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            nwcConnection = nwcConnection,
                            onReplyClick = onReply,
                            onZapFeedback = onZapFeedback,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            note = note,
                            zapCount = note.zaps.size,
                            zapAmountSats = zapAmount.toLong(),
                            zapReceipts = emptyList(),
                            reactionCount = reactionCount,
                            replyCount = replyCount,
                            repostCount = repostCount,
                            onNavigateToThread = onNavigateToThread,
                            onNavigateToProfile = onNavigateToProfile,
                        )
                    }
                } else {
                    null
                },
            replyContext = replyContext,
            onNavigateToThread = onNavigateToThread,
        )
    }
}

/**
 * Detects whether [event] is a reply (NIP-10 or NIP-22), observes the
 * parent's metadata flow so the embed and label pop in once it arrives,
 * and returns a [ReplyContext] (or null for non-replies).
 *
 * For addressable parents (`a` coord), the embed is skipped and only the
 * "Replying to @X" label renders — see ReplyContext.from semantics.
 */
@Composable
private fun rememberReplyContext(
    event: Event,
    localCache: DesktopLocalCache,
): ReplyContext? {
    val threaded = event as? BaseThreadedEvent ?: return null

    val replyTargetEventId =
        remember(event) {
            threaded
                .replyingToAddressOrEvent()
                ?.takeUnless { it.contains(":") }
        }

    // Observe parent NOTE metadata so recomposition picks up the parent event /
    // author when it arrives via relay subscription.
    val parentNote =
        replyTargetEventId?.let {
            remember(it) { localCache.getOrCreateNote(it) }
        }
    val parentFlow = parentNote?.let { remember(it) { it.flow() } }
    val parentMetaState = parentFlow?.metadata?.stateFlow?.collectAsState()
    val parentMetaValue = parentMetaState?.value

    DisposableEffect(parentNote) {
        onDispose { parentNote?.clearFlow() }
    }

    // Also observe the parent AUTHOR's user metadata (kind 0) flow so the
    // "Replying to @X" label upgrades from truncated-hex to display name once
    // the author metadata arrives. parentAuthor can be null until the parent
    // event lands, so produceState (single unconditional composable call) is
    // used to avoid the "conditional composable call" slot-structure trap.
    val parentAuthor = parentNote?.author
    val parentAuthorMetaValue by produceState<Any?>(initialValue = null, key1 = parentAuthor) {
        val author = parentAuthor
        if (author == null) {
            value = null
        } else {
            author.metadata().flow.collect { value = it }
        }
    }

    return remember(event, parentMetaValue, parentAuthorMetaValue) {
        ReplyContext.from(threaded, localCache)
    }
}

@OptIn(FlowPreview::class)
@Composable
fun FeedScreen(
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn? = null,
    iAccount: com.vitorpamplona.amethyst.desktop.model.DesktopIAccount? = null,
    customFeedId: String? = null,
    customFeedSource: com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Filter? = null,
    onOpenFeedsDrawer: () -> Unit = {},
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    initialFeedMode: FeedMode? = null,
    onCompose: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
    onNavigateToRelays: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    val feedSearchActiveState = com.vitorpamplona.amethyst.desktop.ui.theme.LocalFeedSearchActive.current
    var searchActive by feedSearchActiveState
    val onSearchActiveChange: (Boolean) -> Unit = { searchActive = it }
    val openFullSearch = com.vitorpamplona.amethyst.desktop.ui.theme.LocalOpenFullSearch.current
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val followedUsers by localCache.followedUsers.collectAsState()

    // Available relay URLs — subscribe triggers connection on-demand
    val allRelayUrls = relayStatuses.keys

    // Feed relays from relay categories (NIP-65 outbox, minus blocked, with fallback)
    val relayCategories = LocalRelayCategories.current
    val feedRelays by relayCategories.feedRelays.collectAsState()

    var replyToEvent by remember { mutableStateOf<Event?>(null) }
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }

    // Inline expansion state — which note is expanded to show comments + related
    var expandedNoteId by remember { mutableStateOf<String?>(null) }

    // Follow pill state
    val scope = rememberCoroutineScope()
    val followMutex = remember { Mutex() }
    val onFollowFromFeed: (String) -> Unit = { pubKeyHex ->
        if (account != null) {
            scope.launch(Dispatchers.IO) {
                followMutex.withLock {
                    val currentList = localCache.lastContactListEvent
                    val updatedEvent = FollowAction.follow(pubKeyHex, account.signer, currentList)
                    // consume updates followedUsers StateFlow + stores the event before broadcast
                    dispatch(updatedEvent, localCache, relayManager)
                }
            }
        }
    }
    var showRelayPicker by remember { mutableStateOf(false) }

    // Default tab on launch = the first PINNED feed (Following/Global/Custom),
    // not whatever DesktopPreferences happened to save last. If a caller
    // explicitly passes customFeedId/initialFeedMode, that always wins.
    val feedRepo = com.vitorpamplona.amethyst.desktop.ui.deck.LocalFeedRepository.current
    // Read feeds.value (the source StateFlow that's loaded synchronously by
    // FeedDefinitionRepository on construction) rather than pinnedFeeds.value —
    // the latter is a stateIn-derived flow whose initial value is an empty list
    // until the first flow emission propagates, which is too late for `remember`.
    val firstPinned =
        remember {
            feedRepo.feeds.value
                .filter { it.pinned }
                .minByOrNull { it.pinOrder }
        }
    val firstPinnedCustomSource = firstPinned?.source as? com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Filter

    var activeFeedId by remember {
        mutableStateOf(
            customFeedId
                ?: firstPinned?.takeIf { firstPinnedCustomSource != null }?.id,
        )
    }
    var activeFeedSource by remember {
        mutableStateOf(
            customFeedSource ?: firstPinnedCustomSource,
        )
    }
    var feedMode by remember {
        mutableStateOf(
            when {
                customFeedSource != null -> FeedMode.CUSTOM
                initialFeedMode != null -> initialFeedMode
                firstPinned != null ->
                    when (firstPinned.source) {
                        is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Following -> FeedMode.FOLLOWING
                        is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Global -> FeedMode.GLOBAL
                        is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Filter -> FeedMode.CUSTOM
                        else -> DesktopPreferences.feedMode
                    }
                else -> DesktopPreferences.feedMode
            },
        )
    }

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
    rememberSubscription(feedRelays, feedMode, followedUsers, activeFeedSource, relayManager = relayManager) {
        if (feedRelays.isEmpty()) return@rememberSubscription null

        when (feedMode) {
            FeedMode.GLOBAL -> {
                createGlobalFeedSubscription(
                    relays = feedRelays,
                    onEvent = { event, _, relay, _ ->
                        subscriptionsCoordinator?.consumeEvent(event, relay)
                    },
                )
            }

            FeedMode.FOLLOWING -> {
                val follows = followedUsers.toList()
                if (follows.isNotEmpty()) {
                    createFollowingFeedSubscription(
                        relays = feedRelays,
                        followedUsers = follows,
                        onEvent = { event, _, relay, _ ->
                            subscriptionsCoordinator?.consumeEvent(event, relay)
                        },
                    )
                } else {
                    null
                }
            }

            FeedMode.CUSTOM -> {
                val src = activeFeedSource
                if (src != null) {
                    createCustomFeedSubscription(
                        source = src,
                        relays = feedRelays,
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
        remember(feedMode, activeFeedId, iAccount) {
            val hidden = { iAccount?.hiddenUsers?.value ?: com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers.EMPTY }
            val filter =
                when (feedMode) {
                    FeedMode.GLOBAL -> {
                        DesktopGlobalFeedFilter(localCache, hidden)
                    }

                    FeedMode.FOLLOWING -> {
                        DesktopFollowingFeedFilter(localCache, hidden) {
                            localCache.followedUsers.value
                        }
                    }

                    FeedMode.CUSTOM -> {
                        DesktopCustomFeedFilter(
                            localCache,
                            activeFeedId ?: "custom",
                            activeFeedSource ?: com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
                                .Filter(),
                            hidden,
                        )
                    }
                }
            DesktopFeedViewModel(filter, localCache, iAccount?.hiddenUsers)
        }

    // Cancel old ViewModel's viewModelScope on recreation
    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    // Force refresh when followedUsers arrives and feed is still empty
    LaunchedEffect(followedUsers, feedState) {
        if (followedUsers.isNotEmpty() && feedState is FeedState.Empty) {
            withContext(Dispatchers.IO) {
                viewModel.feedState.refreshSuspended()
            }
        }
    }

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

            // Reply parents — when the visible note is a reply, fetch the immediate parent
            // so QuotedNoteEmbed can render it. Skip addressable-coord parents (kind 30023 etc).
            val replyParentIds =
                notes
                    .mapNotNull { note ->
                        val evt = note.event as? BaseThreadedEvent ?: return@mapNotNull null
                        evt.replyingToAddressOrEvent()?.takeUnless { it.contains(":") }
                    }.filter { localCache.getNoteIfExists(it)?.event == null }

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

            (repostIds + replyParentIds + contentQuotedIds).distinct()
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

            // Reply-parent authors — extract DIRECTLY from each visible reply's tags
            // (NIP-22 ReplyAuthorTag for CommentEvent; NIP-10 last p-tag convention for
            // TextNote / other threaded events). Doesn't require the parent event itself
            // to be in cache, so the metadata fetch races in parallel with the parent-event
            // fetch above and lands as soon as either relay returns kind 0 for the author.
            val replyParentAuthorHexes =
                notes.mapNotNull { note ->
                    when (val evt = note.event) {
                        is CommentEvent -> evt.replyAuthor()?.pubKey
                        is BaseThreadedEvent -> evt.taggedUsers().lastOrNull()?.pubKey
                        else -> null
                    }
                }

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

            // Authors from feed notes + repost originals + quoted notes (User-based —
            // filter by missing profile picture). Reply-parent authors are hex-only
            // (the parent User may not yet exist in cache) so they're concat'd as hexes
            // after the User → hex projection.
            val knownAuthorHexes =
                (
                    notes.mapNotNull { it.author } +
                        repostOriginals.mapNotNull { it.author } +
                        quotedNotes.mapNotNull { it.author }
                ).filter { it.profilePicture() == null }
                    .map { it.pubkeyHex }

            val replyParentAuthorMissing =
                replyParentAuthorHexes.filter {
                    localCache
                        .getOrCreateUser(it)
                        .metadataOrNull()
                        ?.flow
                        ?.value == null
                }

            (knownAuthorHexes + replyParentAuthorMissing).distinct()
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

    // Interaction subscriptions (reactions, zaps, reposts, replies) — same pattern as metadata
    val interactionNoteIds =
        remember(feedState) {
            if (feedState !is FeedState.Loaded) return@remember emptyList<String>()
            viewModel.feedState.visibleNotes().mapNotNull { it.event?.id }
        }

    rememberSubscription(allRelayUrls, interactionNoteIds, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || interactionNoteIds.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("fetch-interactions"),
            filters =
                listOf(
                    FilterBuilders.reactionsForEvents(interactionNoteIds),
                    FilterBuilders.zapsForEvents(interactionNoteIds),
                    FilterBuilders.repostsForEvents(interactionNoteIds),
                    Filter(
                        kinds = listOf(com.vitorpamplona.quartz.nip10Notes.TextNoteEvent.KIND),
                        tags = mapOf("e" to interactionNoteIds),
                    ),
                    // Poll responses (kind 1018) referencing these notes (NIP-88, lowercase `e`).
                    Filter(
                        kinds = listOf(PollResponseEvent.KIND),
                        tags = mapOf("e" to interactionNoteIds),
                    ),
                ),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ ->
                subscriptionsCoordinator?.consumeEvent(event, relay)
            },
        )
    }

    // Hoisted so the floating "New posts" chip can share the same scroll
    // state as the LazyColumn and follow the animated header height.
    val homeFeedLazyListState = rememberLazyListState()
    val headerSpacerHeight by animateDpAsState(
        targetValue = if (searchActive) 300.dp else 60.dp,
        animationSpec = tween(200),
    )

    // Auto-snap to position 0 when fresh events prepend AND the user was
    // already at the top. Without this, Compose's stable-key diff keeps
    // the previously-visible top item anchored, pushing the new items
    // silently above the viewport — the root cause of the "stale feed on
    // launch" perception. Mutually exclusive with the NewPostsChip below,
    // which handles the case where the user is scrolled away from top.
    StickToTopOnPrepend(viewModel.feedState, homeFeedLazyListState)

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Feed content (scrollable, behind scrim)
        ReadingColumn {
            // Reserve space for the header card that floats above the feed.
            Spacer(Modifier.height(headerSpacerHeight))

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
                    val lazyListState = homeFeedLazyListState

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

                    val sidePadding = LocalReadingSidePadding.current
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(start = sidePadding + 12.dp, end = sidePadding + 12.dp, top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(loadedState.list, key = { it.idHex }) { note ->
                            val isExpanded = note.idHex == expandedNoteId

                            FeedNoteCard(
                                note = note,
                                relayManager = relayManager,
                                localCache = localCache,
                                account = account,
                                nwcConnection = nwcConnection,
                                onReply = { replyToEvent = note.event },
                                onZapFeedback = onZapFeedback,
                                onNavigateToProfile = onNavigateToProfile,
                                onNavigateToThread = { noteId ->
                                    // Toggle inline expansion instead of navigating
                                    expandedNoteId = if (expandedNoteId == noteId) null else noteId
                                },
                                onImageClick = { urls, index ->
                                    lightboxState = LightboxState(urls, index)
                                },
                                onMediaClick = { urls, index, seekPos ->
                                    com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
                                        .playVideo(urls[index], seekPos)
                                    com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
                                        .toggleFullscreen()
                                },
                                followedUsers = followedUsers,
                                myPubKeyHex = account?.pubKeyHex,
                                onFollow = onFollowFromFeed,
                            )

                            // Inline expanded content: CommentsCard + Related
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                ExpandedNoteContent(
                                    note = note,
                                    relayManager = relayManager,
                                    localCache = localCache,
                                    account = account,
                                    nwcConnection = nwcConnection,
                                    subscriptionsCoordinator = subscriptionsCoordinator,
                                    onNavigateToProfile = onNavigateToProfile,
                                    onNavigateToThread = { noteId ->
                                        expandedNoteId = if (expandedNoteId == noteId) null else noteId
                                    },
                                    onNavigateToThreadOverlay = onNavigateToThread,
                                    onReply = { replyToEvent = it },
                                    onZapFeedback = onZapFeedback,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Layer 1.5: "New posts" chip — floats below the header card,
        // appears when fresh events prepend while the user is scrolled
        // away from the top of the feed. Below the scrim in z-order so
        // it dims along with the feed when search is expanded.
        if (feedState is FeedState.Loaded) {
            val chipState =
                rememberNewPostsChipState(
                    feedContentState = viewModel.feedState,
                    listState = homeFeedLazyListState,
                )
            NewPostsChip(
                state = chipState,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = headerSpacerHeight + 16.dp),
            )
        }

        // Reply dialog
        if (replyToEvent != null && account != null) {
            ComposeNoteDialog(
                onDismiss = { replyToEvent = null },
                relayManager = relayManager,
                account = account,
                localCache = localCache,
                replyTo = replyToEvent,
            )
        }

        // Feed relay picker dialog
        if (showRelayPicker && account != null && iAccount != null) {
            AlertDialog(
                onDismissRequest = { showRelayPicker = false },
                title = { Text("Feed Relays (NIP-65)") },
                text = {
                    Nip65RelayEditor(
                        nip65State = iAccount.nip65RelayList,
                        signer = account.signer,
                        onPublish = { event ->
                            relayManager.broadcastToAll(event)
                            // Update local NIP-65 state immediately via addressable note cache
                            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                            GlobalScope.launch(Dispatchers.IO) {
                                localCache.justConsumeMyOwnEvent(event)
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showRelayPicker = false }) {
                        Text("Close")
                    }
                },
            )
        }

        // Layer 2: Search scrim — dims feed content when search is expanded
        if (searchActive) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSearchActiveChange(false)
                        },
            )
        }

        // Layer 3: Header card — rendered AFTER scrim so it floats above it
        FeedTabsHeader(
            feedMode = feedMode,
            activeFeedId = activeFeedId,
            searchExpanded = searchActive,
            onSearchExpandedChange = onSearchActiveChange,
            onFeedModeChange = { mode ->
                feedMode = mode
                activeFeedId = null
                activeFeedSource = null
                if (mode != FeedMode.CUSTOM) {
                    DesktopPreferences.feedMode = mode
                }
            },
            onNavigateToFeed = { feed ->
                val source = feed.source
                if (source is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Filter) {
                    activeFeedId = feed.id
                    activeFeedSource = source
                    feedMode = FeedMode.CUSTOM
                }
            },
            onOpenFeedsDrawer = onOpenFeedsDrawer,
            onCompose = onCompose,
            onSearchClick = openFullSearch,
            relayManager = relayManager,
            localCache = localCache,
            account = account,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToThread = onNavigateToThread,
        )

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
 * Feed header \u2014 Messages-style single row: Following/Global tabs on the left,
 * compact icon buttons on the right (relays, refresh, compose). The selected
 * tab IS the title; no redundant "Global Feed" / "Following Feed" label.
 *
 * Relay count and followed-users count are surfaced via a hover tooltip on
 * the relays icon (desktop convention \u2014 the at-a-glance info is preserved
 * without stealing header real estate).
 */
@Composable
private fun FeedTabsHeader(
    feedMode: FeedMode,
    activeFeedId: String? = null,
    searchExpanded: Boolean = false,
    onSearchExpandedChange: (Boolean) -> Unit = {},
    onFeedModeChange: (FeedMode) -> Unit,
    onNavigateToFeed: (com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition) -> Unit = {},
    onOpenFeedsDrawer: () -> Unit,
    onCompose: () -> Unit,
    onSearchClick: () -> Unit = {},
    relayManager: DesktopRelayConnectionManager? = null,
    localCache: DesktopLocalCache? = null,
    account: AccountState.LoggedIn? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
) {
    val feedRepo = com.vitorpamplona.amethyst.desktop.ui.deck.LocalFeedRepository.current
    val pinnedFeeds by feedRepo.pinnedFeeds.collectAsState()
    val sidePadding = LocalReadingSidePadding.current
    val scope = rememberCoroutineScope()
    val searchState = remember { AdvancedSearchBarState(scope) }
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val debouncedQuery by searchState.debouncedQuery.collectAsState()
    val relayCategories = LocalRelayCategories.current
    val searchRelays by relayCategories.searchRelays.collectAsState()

    // Namecoin resolution wiring (matches SearchScreen.kt pattern).
    // The home tab search bar must resolve <name>.bit / <name>.namecoin
    // identifiers in the same way as the full Search screen so users get a
    // consistent experience regardless of where they type the query.
    val namecoinService = LocalNamecoinService.current
    val namecoinPrefs = LocalNamecoinPreferences.current
    val namecoinEnabled =
        namecoinPrefs
            ?.settings
            ?.collectAsState()
            ?.value
            ?.enabled ?: false
    val isNamecoinQuery =
        remember(searchText.text) {
            searchText.text.isNotBlank() &&
                NamecoinNameResolver.isNamecoinIdentifier(searchText.text.trim())
        }
    var namecoinState by remember { mutableStateOf<NamecoinResolveState?>(null) }

    // Resolve Namecoin identifiers, cancelling stale lookups when the user
    // keeps typing. Uses resolveDetailed() so we get typed failure outcomes
    // (NotFound / Expired / ServersUnreachable) instead of swallowed exceptions.
    LaunchedEffect(searchText.text, namecoinEnabled) {
        if (!namecoinEnabled || !isNamecoinQuery || namecoinService == null) {
            namecoinState = null
            return@LaunchedEffect
        }
        namecoinState = NamecoinResolveState.Loading
        val outcome = namecoinService.resolveDetailed(searchText.text.trim())
        namecoinState =
            when (outcome) {
                is NamecoinResolveOutcome.Success ->
                    NamecoinResolveState.Resolved(outcome.result)
                is NamecoinResolveOutcome.NameNotFound -> NamecoinResolveState.NotFound
                is NamecoinResolveOutcome.NoNostrField ->
                    NamecoinResolveState.Error("Name exists but has no Nostr pubkey")
                is NamecoinResolveOutcome.MalformedRecord ->
                    NamecoinResolveState.Error(
                        "Namecoin record JSON is malformed: ${outcome.error}",
                    )
                is NamecoinResolveOutcome.ServersUnreachable ->
                    NamecoinResolveState.Error(
                        "ElectrumX servers unreachable — check your connection or try again",
                    )
                is NamecoinResolveOutcome.InvalidIdentifier ->
                    NamecoinResolveState.Error("Invalid Namecoin identifier")
                is NamecoinResolveOutcome.Timeout ->
                    NamecoinResolveState.Error(
                        "Resolution timed out — servers may be slow, try again",
                    )
            }
    }

    // Sync search text to AdvancedSearchBarState
    LaunchedEffect(searchText.text) {
        searchState.updateFromText(searchText.text)
    }

    // Auto-save to history after 1s of no typing (separate from relay debounce)
    LaunchedEffect(searchText.text) {
        if (searchText.text.isNotBlank()) {
            kotlinx.coroutines.delay(1000L)
            val query = searchState.query.value
            if (!query.isEmpty) {
                SearchHistoryStore.addToHistory(query)
            }
        }
    }

    // Clear on collapse
    LaunchedEffect(searchExpanded) {
        if (!searchExpanded) {
            searchText = TextFieldValue("")
            searchState.clearSearch()
        }
    }

    // Auto-focus when expanded
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
        }
    }

    // Start/stop relay search when debounced query changes
    LaunchedEffect(debouncedQuery) {
        if (!debouncedQuery.isEmpty) {
            searchState.clearResults()
            searchState.initRelayStates(searchRelays)
            searchState.startSearching("people-search")
            searchState.startSearching("adv-search")
            kotlinx.coroutines.delay(10_000L)
            searchState.timeoutWaitingRelays()
        }
    }

    // NIP-50 people search subscription
    if (relayManager != null) {
        rememberSubscription(searchRelays, debouncedQuery, relayManager = relayManager) {
            if (searchRelays.isEmpty() || debouncedQuery.isEmpty) {
                return@rememberSubscription null
            }
            createSearchPeopleSubscription(
                relays = searchRelays,
                searchQuery = debouncedQuery.text.ifBlank { QuerySerializer.serialize(debouncedQuery) },
                limit = 10,
                onEvent = { event, _, relay, _ ->
                    if (searchState.trackRelayEvent(relay.url, event.id)) {
                        if (event is MetadataEvent) {
                            localCache?.consumeMetadata(event)
                            val user = localCache?.getUserIfExists(event.pubKey)
                            if (user != null) {
                                searchState.addPeopleResult(user)
                            }
                        }
                    }
                },
                onEose = { relay, _ ->
                    searchState.updateRelayState(relay.url, RelaySyncStatus.EOSE_RECEIVED)
                    searchState.stopSearching("people-search")
                },
                onClosed = { relay, _, _ ->
                    searchState.updateRelayState(relay.url, RelaySyncStatus.FAILED)
                    searchState.stopSearching("people-search")
                },
            )
        }

        // NIP-50 note search subscription
        rememberSubscription(searchRelays, debouncedQuery, relayManager = relayManager) {
            if (searchRelays.isEmpty() || debouncedQuery.isEmpty) {
                return@rememberSubscription null
            }
            val filters = SearchFilterFactory.createFilters(debouncedQuery)
            if (filters.isEmpty()) return@rememberSubscription null

            SubscriptionConfig(
                subId = generateSubId("inline-search"),
                filters = filters,
                relays = searchRelays,
                onEvent = { event, _, relay, _ ->
                    if (event.kind == MetadataEvent.KIND) return@SubscriptionConfig
                    if (searchState.trackRelayEvent(relay.url, event.id)) {
                        val filtered = SearchResultFilter.filter(listOf(event), debouncedQuery)
                        if (filtered.isNotEmpty()) {
                            searchState.addNoteResults(filtered)
                        }
                    }
                },
                onEose = { relay, _ ->
                    searchState.updateRelayState(relay.url, RelaySyncStatus.EOSE_RECEIVED)
                    searchState.stopSearching("adv-search")
                },
                onClosed = { relay, _, _ ->
                    searchState.updateRelayState(relay.url, RelaySyncStatus.FAILED)
                    searchState.stopSearching("adv-search")
                },
            )
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePadding + 8.dp, vertical = 8.dp),
    ) {
        Column {
            // Always-visible header row: tabs + search + compose
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Feed tabs — always visible. Clicking collapses search.
                pinnedFeeds.forEach { feed ->
                    val isSelected =
                        when (feed.source) {
                            is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Following ->
                                feedMode == FeedMode.FOLLOWING
                            is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Global ->
                                feedMode == FeedMode.GLOBAL
                            else -> activeFeedId == feed.id
                        }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSearchExpandedChange(false)
                            when (feed.source) {
                                is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Following ->
                                    onFeedModeChange(FeedMode.FOLLOWING)
                                is com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource.Global ->
                                    onFeedModeChange(FeedMode.GLOBAL)
                                else -> onNavigateToFeed(feed)
                            }
                        },
                        label = {
                            Text(
                                "${feed.emoji} ${feed.name}",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }

                // Search: pill when collapsed, active input when expanded
                if (searchExpanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            MaterialSymbols.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                            onSearchExpandedChange(false)
                                            focusManager.clearFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                            textStyle =
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchText.text.isEmpty()) {
                                        Text(
                                            "Search notes, profiles, hashtags...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (PlatformInfo.isMacOS) "\u2318F" else "Ctrl+F",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    com.vitorpamplona.amethyst.desktop.ui.search.SearchPill(
                        onClick = { onSearchExpandedChange(true) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Compose button — always visible
                IconButton(onClick = onCompose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        MaterialSymbols.Edit,
                        contentDescription = "Compose",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Animated expanding section: history or search results
            AnimatedVisibility(
                visible = searchExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(tween(100)),
            ) {
                Column {
                    val hasQuery = searchText.text.isNotBlank()
                    val isSearching by searchState.isSearching.collectAsState()
                    val people by searchState.peopleResults.collectAsState()
                    val notes by searchState.noteResults.collectAsState()
                    val hasResults = people.isNotEmpty() || notes.isNotEmpty()

                    // Linear progress bar at the top — visible while searching
                    AnimatedVisibility(
                        visible = isSearching || (hasQuery && !hasResults),
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Namecoin lookup row — rendered above the regular search
                    // results when the query looks like a Namecoin identifier
                    // (matches the pattern used in SearchScreen.kt).
                    if (isNamecoinQuery && namecoinState != null) {
                        InlineNamecoinResultRow(
                            state = namecoinState!!,
                            query = searchText.text.trim(),
                            onClick = { pubkeyHex ->
                                onSearchExpandedChange(false)
                                onNavigateToProfile(pubkeyHex)
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    if (hasQuery) {
                        // Results stream in as they arrive
                        if (hasResults) {
                            SearchResultsList(
                                state = searchState,
                                onNavigateToProfile = { pubkey ->
                                    onSearchExpandedChange(false)
                                    onNavigateToProfile(pubkey)
                                },
                                onNavigateToThread = { noteId ->
                                    onSearchExpandedChange(false)
                                    onNavigateToThread(noteId)
                                },
                                localCache = localCache,
                                relayManager = relayManager,
                                account = account,
                                myPubKeyHex = account?.pubKeyHex,
                                modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                            )
                        } else if (isSearching) {
                            // Loading — centered in results area
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    MaterialSymbols.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Searching ${searchRelays.size} relays...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // Search complete, no results
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    MaterialSymbols.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (searchRelays.isEmpty()) "No search relays configured" else "No results found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // "Open full search" — always visible when there's a query
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSearchExpandedChange(false)
                                        onSearchClick()
                                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                MaterialSymbols.AutoMirrored.OpenInNew,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Open full search", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Show search history when empty
                        SearchHistorySection(
                            onOpenFullSearch = {
                                onSearchExpandedChange(false)
                                onSearchClick()
                            },
                            onHistoryItemClick = { text ->
                                searchText = TextFieldValue(text, TextRange(text.length))
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Namecoin lookup result row shown above the regular search
 * results in the home tab inline search bar. Mirrors the resolve / loading
 * / error UX of [SearchScreen]'s full Namecoin panel so users see the same
 * outcome regardless of where they typed the identifier.
 */
@Composable
private fun InlineNamecoinResultRow(
    state: NamecoinResolveState,
    query: String,
    onClick: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Namecoin lookup",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            is NamecoinResolveState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(modifier = Modifier.width(80.dp))
                    Text(
                        "Resolving $query…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is NamecoinResolveState.Resolved -> {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onClick(state.result.pubkey) }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        MaterialSymbols.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${state.result.namecoinName} → ${state.result.pubkey.take(12)}…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.result.relays.isNotEmpty()) {
                            Text(
                                "Relays: ${state.result.relays.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        MaterialSymbols.AutoMirrored.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is NamecoinResolveState.NotFound -> {
                Text(
                    "Name not found on Namecoin blockchain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is NamecoinResolveState.Error -> {
                Text(
                    "Resolution error: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SearchHistorySection(
    onOpenFullSearch: () -> Unit,
    onHistoryItemClick: (String) -> Unit = { },
) {
    val history by SearchHistoryStore.history.collectAsState()
    val savedSearches by SearchHistoryStore.savedSearches.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        if (history.isNotEmpty()) {
            Text(
                "Recent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            history.take(5).forEach { query ->
                val text =
                    com.vitorpamplona.amethyst.commons.search.QuerySerializer
                        .serialize(query)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onHistoryItemClick(text) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(MaterialSymbols.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }

        if (savedSearches.isNotEmpty()) {
            if (history.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Text("Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            savedSearches.take(5).forEach { saved ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenFullSearch() }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(MaterialSymbols.Bookmark, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(saved.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onOpenFullSearch() }.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(MaterialSymbols.AutoMirrored.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Open full search", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedHeader(
    feedMode: FeedMode,
    account: AccountState.LoggedIn?,
    feedRelays: Set<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl>,
    followedUsersCount: Int,
    onFeedModeChange: (FeedMode) -> Unit,
    onRefresh: () -> Unit,
    onCompose: () -> Unit,
    onNavigateToRelays: () -> Unit = {},
    onOpenRelayPicker: () -> Unit = {},
) {
    val sidePadding = LocalReadingSidePadding.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePadding + 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tabs \u2014 the selected one is the screen title.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (account != null) {
                FilterChip(
                    selected = feedMode == FeedMode.FOLLOWING,
                    onClick = { onFeedModeChange(FeedMode.FOLLOWING) },
                    label = { Text("Following") },
                )
            }
            FilterChip(
                selected = feedMode == FeedMode.GLOBAL,
                onClick = { onFeedModeChange(FeedMode.GLOBAL) },
                label = { Text("Global") },
            )
        }

        // Actions \u2014 compact icon buttons at the same scale as the Messages header.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val relaysTooltip =
                buildString {
                    append("${feedRelays.size} relays")
                    if (feedMode == FeedMode.FOLLOWING) {
                        append(" \u2022 $followedUsersCount followed")
                    }
                }
            TooltipArea(
                tooltip = {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            relaysTooltip,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            ) {
                IconButton(
                    onClick = {
                        if (account != null && !account.isReadOnly) {
                            onOpenRelayPicker()
                        } else {
                            onNavigateToRelays()
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        MaterialSymbols.Dns,
                        contentDescription = relaysTooltip,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    MaterialSymbols.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (account != null && !account.isReadOnly) {
                IconButton(
                    onClick = onCompose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        MaterialSymbols.Add,
                        contentDescription = "New Post",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedNoteContent(
    note: com.vitorpamplona.amethyst.commons.model.Note,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToThreadOverlay: (String) -> Unit = {},
    onReply: (Event) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
) {
    val event = note.event ?: return
    val noteId = event.id
    val expandedScope = rememberCoroutineScope()
    val connectedRelays =
        relayManager.relayStatuses
            .collectAsState()
            .value.keys

    // Subscribe for replies when expanded
    rememberSubscription(connectedRelays, noteId, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            createThreadRepliesSubscription(
                relays = connectedRelays,
                noteId = noteId,
                onEvent = { ev, _, relay, _ ->
                    subscriptionsCoordinator?.consumeEvent(ev, relay)
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Observe replies flow so we recompose when new replies arrive
    val noteFlowSet = remember(note) { note.flow() }
    val repliesState by noteFlowSet.replies.stateFlow.collectAsState()

    DisposableEffect(note) { onDispose { note.clearFlow() } }

    // Get reply notes from cache — recompute when replies change
    val replyNotes = remember(repliesState) { note.replies.sortedByDescending { it.createdAt() } }

    // Load metadata for reply authors
    LaunchedEffect(replyNotes, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null && replyNotes.isNotEmpty()) {
            val authors = replyNotes.mapNotNull { it.event?.pubKey }.distinct()
            if (authors.isNotEmpty()) {
                subscriptionsCoordinator.loadMetadataBatched(authors)
            }
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        // Comments card
        CommentsCard(
            commentCount = replyNotes.size,
            replyContent = {
                if (account != null) {
                    val myUser = remember(account.pubKeyHex) { localCache.getUserIfExists(account.pubKeyHex) }
                    val myAvatarUrl = remember(myUser) { myUser?.profilePicture() }

                    InlineReplyInput(
                        myAvatarUrl = myAvatarUrl,
                        onSend = { content ->
                            withContext(Dispatchers.IO) {
                                val parentText = event as? TextNoteEvent ?: return@withContext
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
                replyNotes.take(5).forEachIndexed { index, replyNote ->
                    val replyEvent = replyNote.event
                    val flowSet = remember(replyNote) { replyNote.flow() }
                    val metadataState by flowSet.metadata.stateFlow.collectAsState()
                    val reactionsState by flowSet.reactions.stateFlow.collectAsState()
                    val zapsState by flowSet.zaps.stateFlow.collectAsState()

                    DisposableEffect(replyNote) { onDispose { replyNote.clearFlow() } }

                    val author =
                        remember(replyEvent?.pubKey, metadataState) {
                            replyEvent?.pubKey?.let { localCache.getUserIfExists(it) }
                        }
                    val reactionCount = remember(reactionsState) { replyNote.countReactions() }
                    val zapAmount = remember(zapsState) { replyNote.zapsAmount }

                    CommentItem(
                        authorName = author?.toBestDisplayName() ?: replyEvent?.pubKey?.take(8) ?: "",
                        authorHandle = author?.pubkeyNpub()?.take(16)?.let { "@$it..." } ?: "",
                        authorAvatarUrl = author?.profilePicture(),
                        authorPubKeyHex = replyEvent?.pubKey ?: "",
                        content = replyEvent?.content ?: "",
                        timeAgo = (replyEvent?.createdAt ?: 0L).toTimeAgo(),
                        reactionCount = reactionCount,
                        zapAmount = zapAmount.toLong(),
                        onReply = { replyNote.event?.let { onReply(it) } },
                        onLike = {
                            val ev = replyNote.event
                            if (account != null && ev != null) {
                                expandedScope.launch(Dispatchers.IO) {
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
                            val ev = replyNote.event
                            if (account != null && ev != null && nwcConnection != null) {
                                expandedScope.launch {
                                    val feedback =
                                        zapNote(
                                            event = ev,
                                            account = account,
                                            relayManager = relayManager,
                                            localCache = localCache,
                                            amountSats = 21,
                                            nwcConnection = nwcConnection,
                                        )
                                    onZapFeedback(feedback)
                                }
                            }
                        },
                        onAuthorClick = { replyNote.event?.pubKey?.let { onNavigateToProfile(it) } },
                    )
                    if (index < replyNotes.take(5).lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        // Related content
        val noteHashtags =
            remember(event) {
                event.tags.mapNotNull(HashtagTag::parse).toSet()
            }
        RelatedContentSection(
            noteId = noteId,
            authorPubKey = event.pubKey,
            noteHashtags = noteHashtags,
            localCache = localCache,
            onItemClick = onNavigateToThreadOverlay,
            onViewAll = { onNavigateToProfile(event.pubKey) },
        )
    }
}

@Composable
private fun FollowPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                "Follow",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        leadingIcon = {
            Icon(
                MaterialSymbols.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
        modifier = modifier.height(28.dp),
    )
}
