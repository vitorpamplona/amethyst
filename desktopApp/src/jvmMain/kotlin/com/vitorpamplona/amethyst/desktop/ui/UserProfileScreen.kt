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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.FollowAction
import com.vitorpamplona.amethyst.commons.profile.ProfileBroadcastStatus
import com.vitorpamplona.amethyst.commons.profile.ui.ProfileBroadcastBanner
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.state.FollowState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.components.UserSearchCard
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.feeds.DesktopBookmarkFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopMutualFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopProfileFeedFilter
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.chats.DesktopDmRoute
import com.vitorpamplona.amethyst.desktop.ui.deck.LocalFollowPacksState
import com.vitorpamplona.amethyst.desktop.ui.media.LightboxOverlay
import com.vitorpamplona.amethyst.desktop.ui.note.DesktopRichText
import com.vitorpamplona.amethyst.desktop.ui.note.RichTextCallbacks
import com.vitorpamplona.amethyst.desktop.ui.note.WoTBadgedAvatar
import com.vitorpamplona.amethyst.desktop.ui.profile.EditProfileDialog
import com.vitorpamplona.amethyst.desktop.ui.profile.GalleryTab
import com.vitorpamplona.amethyst.desktop.ui.profile.RelayRowCard
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.GitHubIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.MastodonIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TwitterIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * User profile screen showing user info, follow button, and their posts.
 */
@Composable
fun UserProfileScreen(
    pubKeyHex: String,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onBack: () -> Unit,
    canGoBack: Boolean = false,
    onCompose: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToArticle: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays = relayStatuses.keys

    // User metadata — seed from cache so returning to profile is instant
    val cachedUser = remember(pubKeyHex) { localCache.getUserIfExists(pubKeyHex) }
    val cachedMetadata = remember(pubKeyHex) { cachedUser?.metadataOrNull() }
    var displayName by remember { mutableStateOf(cachedMetadata?.bestName()) }
    var about by remember {
        mutableStateOf(
            cachedMetadata
                ?.flow
                ?.value
                ?.info
                ?.about,
        )
    }
    var picture by remember { mutableStateOf(cachedMetadata?.profilePicture()) }
    var banner by remember {
        mutableStateOf(
            cachedMetadata
                ?.flow
                ?.value
                ?.info
                ?.banner,
        )
    }
    var nip05 by remember {
        mutableStateOf(
            cachedMetadata
                ?.flow
                ?.value
                ?.info
                ?.nip05,
        )
    }
    var website by remember {
        mutableStateOf(
            cachedMetadata
                ?.flow
                ?.value
                ?.info
                ?.website,
        )
    }
    var lnAddress by remember { mutableStateOf(cachedMetadata?.lnAddress()) }
    var identities by remember {
        mutableStateOf(
            cachedMetadata?.flow?.value?.identities ?: emptyList(),
        )
    }
    var followersCount by remember { mutableStateOf(localCache.getCachedFollowerCount(pubKeyHex)) }
    var followingCount by remember { mutableStateOf(localCache.getCachedFollowingCount(pubKeyHex)) }

    // Profile editing state (only for own profile)
    val isOwnProfile = account != null && pubKeyHex == account.pubKeyHex
    var showEditProfile by remember { mutableStateOf(false) }
    var broadcastStatus by remember { mutableStateOf<ProfileBroadcastStatus>(ProfileBroadcastStatus.Idle) }
    var latestMetadataEvent by remember { mutableStateOf<MetadataEvent?>(null) }
    var latestIdentitiesEvent by remember { mutableStateOf<ExternalIdentitiesEvent?>(null) }

    val scope = rememberCoroutineScope()

    val iAccount = com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount.current
    val profileSnackbar = com.vitorpamplona.amethyst.desktop.ui.LocalSnackbarHost.current
    val hidden = {
        iAccount?.hiddenUsers?.value ?: com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers.EMPTY
    }
    var showProfileModMenu by remember(pubKeyHex) { mutableStateOf(false) }
    var showProfileReportDialog by remember(pubKeyHex) { mutableStateOf(false) }
    var showAddToListMenu by remember(pubKeyHex) { mutableStateOf(false) }
    val followPacksState = LocalFollowPacksState.current
    val followPacks by (followPacksState?.allPacks ?: MutableStateFlow(emptyList<FollowListEvent>())).collectAsState()
    val profileHidden by (iAccount?.hiddenUsers ?: kotlinx.coroutines.flow.MutableStateFlow(com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers.EMPTY)).collectAsState()
    val isUserMuted = profileHidden.isUserHidden(pubKeyHex)

    // User's posts — cache-backed via DesktopFeedViewModel
    val profileViewModel =
        remember(pubKeyHex, iAccount) {
            DesktopFeedViewModel(
                DesktopProfileFeedFilter(pubKeyHex, localCache, hidden = hidden),
                localCache,
                iAccount?.hiddenUsers,
            )
        }
    DisposableEffect(profileViewModel) {
        onDispose { profileViewModel.destroy() }
    }
    val profileFeedState by profileViewModel.feedState.feedContent.collectAsState()
    val profileLoadedNotes =
        if (profileFeedState is FeedState.Loaded) {
            val loaded by (profileFeedState as FeedState.Loaded).feed.collectAsState()
            loaded.list
        } else {
            kotlinx.collections.immutable.persistentListOf()
        }

    // User's replies — separate VM, same cache. Predicate inside the filter.
    val repliesViewModel =
        remember(pubKeyHex, iAccount) {
            DesktopFeedViewModel(
                DesktopProfileFeedFilter(pubKeyHex, localCache, repliesOnly = true, hidden = hidden),
                localCache,
                iAccount?.hiddenUsers,
            )
        }
    DisposableEffect(repliesViewModel) {
        onDispose { repliesViewModel.destroy() }
    }
    val repliesFeedState by repliesViewModel.feedState.feedContent.collectAsState()
    val repliesLoadedNotes =
        if (repliesFeedState is FeedState.Loaded) {
            val loaded by (repliesFeedState as FeedState.Loaded).feed.collectAsState()
            loaded.list
        } else {
            kotlinx.collections.immutable.persistentListOf()
        }

    // Mutual — notes the logged-in user authored that tag this profile.
    val mutualViewModel =
        remember(pubKeyHex, account, iAccount) {
            if (account != null) {
                DesktopFeedViewModel(
                    DesktopMutualFeedFilter(account.pubKeyHex, pubKeyHex, localCache, hidden = hidden),
                    localCache,
                    iAccount?.hiddenUsers,
                )
            } else {
                null
            }
        }
    DisposableEffect(mutualViewModel) { onDispose { mutualViewModel?.destroy() } }
    val mutualLoadedNotes =
        mutualViewModel?.let { vm ->
            val state by vm.feedState.feedContent.collectAsState()
            if (state is FeedState.Loaded) {
                val loaded by (state as FeedState.Loaded).feed.collectAsState()
                loaded.list
            } else {
                kotlinx.collections.immutable.persistentListOf()
            }
        } ?: kotlinx.collections.immutable.persistentListOf()

    // Bookmarks — public bookmarks (kind 10003) resolved to notes from cache.
    var bookmarkIds by remember(pubKeyHex) { mutableStateOf<Set<HexKey>>(emptySet()) }
    val bookmarkViewModel =
        remember(pubKeyHex) {
            DesktopFeedViewModel(
                DesktopBookmarkFeedFilter({ bookmarkIds }, localCache),
                localCache,
            )
        }
    DisposableEffect(bookmarkViewModel) { onDispose { bookmarkViewModel.destroy() } }
    LaunchedEffect(bookmarkIds) { bookmarkViewModel.invalidateData(false) }
    val bookmarkFeedState by bookmarkViewModel.feedState.feedContent.collectAsState()
    val bookmarkLoadedNotes =
        if (bookmarkFeedState is FeedState.Loaded) {
            val loaded by (bookmarkFeedState as FeedState.Loaded).feed.collectAsState()
            loaded.list
        } else {
            kotlinx.collections.immutable.persistentListOf()
        }

    var retryTrigger by remember { mutableStateOf(0) }

    // Subscribe to profile user's text notes (kind 1) — populates cache for DesktopFeedViewModel
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            SubscriptionConfig(
                subId = generateSubId("profile-notes-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        FilterBuilders.textNotesFromAuthors(
                            authors = listOf(pubKeyHex),
                            limit = 200,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, relay, _ ->
                    subscriptionsCoordinator?.consumeEvent(event, relay)
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Tab and gallery state
    var selectedTab by remember { mutableStateOf(0) }
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }
    val pictureEvents = remember { mutableStateListOf<PictureEvent>() }
    val articleEvents = remember { mutableStateListOf<LongTextNoteEvent>() }
    val highlightEvents = remember { mutableStateListOf<HighlightEvent>() }

    // Followers / Following user lists (pubkeys) and the profile's relay list.
    val followerList = remember(pubKeyHex) { mutableStateListOf<String>() }
    val followingList = remember(pubKeyHex) { mutableStateListOf<String>() }
    // Each relay entry: (url, canRead, canWrite)
    var relayList by remember(pubKeyHex) { mutableStateOf<List<Triple<String, Boolean, Boolean>>>(emptyList()) }

    // Follow state
    val followState =
        remember(account) {
            FollowState(myPubKeyHex = account?.pubKeyHex ?: "")
        }

    // Store the user's contact list separately for reliable access
    var myContactList by remember(account) { mutableStateOf<ContactListEvent?>(null) }
    var contactListLoaded by remember(account) { mutableStateOf(false) }
    var eoseReceivedCount by remember(account) { mutableStateOf(0) }

    // Load current user's contact list (for follow state)
    rememberSubscription(connectedRelays, account, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty() && account != null) {
            createContactListSubscription(
                relays = connectedRelays,
                pubKeyHex = account.pubKeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is ContactListEvent) {
                        // Store the most recent contact list (by createdAt timestamp)
                        if (myContactList == null || event.createdAt > myContactList!!.createdAt) {
                            myContactList = event
                        }

                        followState.updateContactList(event, pubKeyHex)
                        contactListLoaded = true
                    }
                },
                onEose = { _, _ ->
                    eoseReceivedCount++

                    // Wait for EOSE from at least 2 relays or all relays before enabling button
                    val minEoseCount = minOf(2, connectedRelays.size)
                    if (eoseReceivedCount >= minEoseCount && !contactListLoaded) {
                        contactListLoaded = true
                    }
                },
            )
        } else {
            null
        }
    }

    // Subscribe to user metadata (kind 0) + identities (kind 10011) for profile editing
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty() && pubKeyHex.length == 64) {
            SubscriptionConfig(
                subId = generateSubId("meta-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        FilterBuilders.userMetadata(pubKeyHex),
                        Filter(
                            kinds = listOf(ExternalIdentitiesEvent.KIND),
                            authors = listOf(pubKeyHex),
                            limit = 1,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    if (event is MetadataEvent) {
                        try {
                            val metadata = event.contactMetaData()
                            if (metadata != null) {
                                displayName = metadata.displayName ?: metadata.name
                                about = metadata.about
                                picture = metadata.picture
                                banner = metadata.banner
                                nip05 = metadata.nip05
                                website = metadata.website
                                lnAddress = metadata.lnAddress()
                                identities = event.identityClaims()
                            }

                            // Store MetadataEvent for editing (only for own profile)
                            if (isOwnProfile) {
                                val current = latestMetadataEvent
                                if (current == null || event.createdAt > current.createdAt) {
                                    latestMetadataEvent = event
                                }
                            }
                        } catch (_: Exception) {
                            // Ignore parse errors
                        }
                    }
                    // Capture ExternalIdentitiesEvent for profile editing
                    if (isOwnProfile && event is ExternalIdentitiesEvent) {
                        val current = latestIdentitiesEvent
                        if (current == null || event.createdAt > current.createdAt) {
                            latestIdentitiesEvent = event
                        }
                    }
                },
            )
        } else {
            null
        }
    }

    // Subscribe to profile user's contact list (for following count)
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            createContactListSubscription(
                relays = connectedRelays,
                pubKeyHex = pubKeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is ContactListEvent) {
                        val follows = event.verifiedFollowKeySet()
                        followingCount = follows.size
                        followingList.clear()
                        followingList.addAll(follows)
                        localCache.cacheFollowingCount(pubKeyHex, follows.size)
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Track unique followers (authors of contact lists that tag this pubkey)
    val followerAuthors = remember(pubKeyHex) { mutableSetOf<String>() }

    // Subscribe to followers (contact lists that tag this user)
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            // Clear dedup set but keep cached followersCount visible until new data arrives
            followerAuthors.clear()
            followerList.clear()

            SubscriptionConfig(
                subId = "followers-${pubKeyHex.take(8)}-${System.currentTimeMillis()}",
                filters =
                    listOf(
                        FilterBuilders.byPTags(
                            pubKeys = listOf(pubKeyHex),
                            kinds = listOf(3), // ContactListEvent
                            limit = 500,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    // Count unique authors who follow this user
                    if (followerAuthors.add(event.pubKey)) {
                        followerList.add(event.pubKey)
                        val count = followerAuthors.size
                        followersCount = count
                        localCache.cacheFollowerCount(pubKeyHex, count)
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Subscribe to picture events (kind 20) for gallery tab
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            pictureEvents.clear()
            SubscriptionConfig(
                subId = generateSubId("pics-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(pubKeyHex),
                            kinds = listOf(PictureEvent.KIND),
                            limit = 100,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    if (event is PictureEvent && pictureEvents.none { it.id == event.id }) {
                        pictureEvents.add(event)
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Subscribe to long-form articles (kind 30023) for reads tab
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            articleEvents.clear()
            SubscriptionConfig(
                subId = generateSubId("articles-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(pubKeyHex),
                            kinds = listOf(LongTextNoteEvent.KIND),
                            limit = 50,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    if (event is LongTextNoteEvent && articleEvents.none { it.id == event.id }) {
                        articleEvents.add(event)
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Subscribe to highlight events (kind 9802) for highlights tab
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            highlightEvents.clear()
            SubscriptionConfig(
                subId = generateSubId("hl-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(pubKeyHex),
                            kinds = listOf(HighlightEvent.KIND),
                            limit = 100,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    if (event is HighlightEvent && highlightEvents.none { it.id == event.id }) {
                        highlightEvents.add(event)
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Subscribe to the profile user's relay list (kind 10002) for the Relays tab
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            SubscriptionConfig(
                subId = generateSubId("relays-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(pubKeyHex),
                            kinds = listOf(AdvertisedRelayListEvent.KIND),
                            limit = 1,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    if (event is AdvertisedRelayListEvent) {
                        val writes = event.writeRelaysNorm()?.map { it.url }?.toSet() ?: emptySet()
                        val reads = event.readRelaysNorm()?.map { it.url }?.toSet() ?: emptySet()
                        relayList = (writes + reads).sorted().map { url -> Triple(url, url in reads, url in writes) }
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Subscribe to the profile user's public bookmarks list (kind 10003)
    rememberSubscription(connectedRelays, pubKeyHex, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            SubscriptionConfig(
                subId = generateSubId("bmlist-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(pubKeyHex),
                            kinds = listOf(BookmarkListEvent.KIND),
                            limit = 1,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    if (event is BookmarkListEvent) {
                        bookmarkIds =
                            event
                                .publicBookmarks()
                                .filterIsInstance<EventBookmark>()
                                .map { it.eventId }
                                .toSet()
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Fetch the bookmarked notes themselves once their ids are known
    rememberSubscription(connectedRelays, bookmarkIds, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty() && bookmarkIds.isNotEmpty()) {
            SubscriptionConfig(
                subId = generateSubId("bmnotes-${pubKeyHex.take(8)}"),
                filters = listOf(FilterBuilders.byIds(bookmarkIds.toList())),
                relays = connectedRelays,
                onEvent = { event, _, relay, _ -> subscriptionsCoordinator?.consumeEvent(event, relay) },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Fetch the logged-in user's notes that tag this profile (Mutual tab)
    rememberSubscription(connectedRelays, pubKeyHex, account, retryTrigger, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty() && account != null) {
            SubscriptionConfig(
                subId = generateSubId("mutual-${pubKeyHex.take(8)}"),
                filters =
                    listOf(
                        Filter(
                            kinds = listOf(TextNoteEvent.KIND),
                            authors = listOf(account.pubKeyHex),
                            tags = mapOf("p" to listOf(pubKeyHex)),
                            limit = 200,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, relay, _ -> subscriptionsCoordinator?.consumeEvent(event, relay) },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Scroll state for detecting scroll direction
    val listState = rememberLazyListState()
    var showFloatingHeader by remember { mutableStateOf(false) }
    var previousFirstVisibleItemIndex by remember { mutableStateOf(0) }
    var previousFirstVisibleItemScrollOffset by remember { mutableStateOf(0) }

    // Show floating header when scrolling up and header is scrolled out of view
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset
        val scrollingUp =
            currentIndex < previousFirstVisibleItemIndex ||
                (currentIndex == previousFirstVisibleItemIndex && currentOffset < previousFirstVisibleItemScrollOffset)

        // Header items are indices 0-3, so if first visible >= 3, header is out of view
        showFloatingHeader = scrollingUp && currentIndex >= 3
        if (!scrollingUp && currentIndex < 3) showFloatingHeader = false

        previousFirstVisibleItemIndex = currentIndex
        previousFirstVisibleItemScrollOffset = currentOffset
    }

    ReadingColumn {
        Box(modifier = Modifier.fillMaxSize()) {
            if (connectedRelays.isEmpty()) {
                LoadingState("Connecting to relays...")
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = readingHorizontalPadding()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Broadcast banner
                    item(key = "broadcast") {
                        ProfileBroadcastBanner(
                            status = broadcastStatus,
                            onTap = {
                                if (broadcastStatus is ProfileBroadcastStatus.Success ||
                                    broadcastStatus is ProfileBroadcastStatus.Failed
                                ) {
                                    broadcastStatus = ProfileBroadcastStatus.Idle
                                }
                            },
                        )
                    }

                    // Header — Messages-style: compact row, titleMedium title.
                    // Horizontal gutter already supplied by LazyColumn.contentPadding.
                    item(key = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Back is shown only when stacked onto a nav stack (e.g.
                                // clicked a user in the feed). Top-level "My Profile"
                                // from the nav rail sets canGoBack = false so no arrow.
                                if (canGoBack) {
                                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            MaterialSymbols.AutoMirrored.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    "Profile",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }

                            // Edit button for own profile — compact IconButton to match
                            // the action-icon pattern every other screen's header uses.
                            if (isOwnProfile && account.isReadOnly == false) {
                                IconButton(
                                    onClick = { showEditProfile = true },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        MaterialSymbols.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            // Moderation overflow (mute / report) for other profiles.
                            if (iAccount != null && iAccount.isWriteable() && pubKeyHex != iAccount.pubKey) {
                                Box {
                                    IconButton(
                                        onClick = { showProfileModMenu = true },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            MaterialSymbols.MoreVert,
                                            contentDescription = "Moderation actions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showProfileModMenu,
                                        onDismissRequest = { showProfileModMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Message") },
                                            onClick = {
                                                DesktopDmRoute.request(pubKeyHex)
                                                showProfileModMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to list…") },
                                            onClick = {
                                                showProfileModMenu = false
                                                showAddToListMenu = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share (copy link)") },
                                            onClick = {
                                                pubKeyHex.hexToByteArrayOrNull()?.toNpub()?.let { npub ->
                                                    Toolkit
                                                        .getDefaultToolkit()
                                                        .systemClipboard
                                                        .setContents(StringSelection("nostr:$npub"), null)
                                                    scope.launch { profileSnackbar?.showSnackbar("Profile link copied") }
                                                }
                                                showProfileModMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isUserMuted) "Unmute user" else "Mute user") },
                                            onClick = {
                                                scope.launch {
                                                    if (isUserMuted) {
                                                        iAccount.showUser(pubKeyHex)
                                                        profileSnackbar?.showSnackbar("Unmuted user")
                                                    } else {
                                                        iAccount.hideUser(pubKeyHex)
                                                        profileSnackbar?.showSnackbar("Muted user")
                                                    }
                                                }
                                                showProfileModMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Report…") },
                                            onClick = {
                                                showProfileReportDialog = true
                                                showProfileModMenu = false
                                            },
                                        )
                                    }
                                    // Add-to-list pack picker (opened from the menu above).
                                    DropdownMenu(
                                        expanded = showAddToListMenu,
                                        onDismissRequest = { showAddToListMenu = false },
                                    ) {
                                        if (followPacks.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No lists yet") },
                                                enabled = false,
                                                onClick = { showAddToListMenu = false },
                                            )
                                        } else {
                                            followPacks.forEach { pack ->
                                                DropdownMenuItem(
                                                    text = { Text(pack.title() ?: "Untitled list") },
                                                    onClick = {
                                                        val acct = account
                                                        if (acct != null) {
                                                            scope.launch {
                                                                try {
                                                                    val updated =
                                                                        FollowListEvent.add(
                                                                            pack,
                                                                            UserTag(pubKeyHex, null),
                                                                            acct.signer,
                                                                        )
                                                                    relayManager.broadcastToAll(updated)
                                                                    profileSnackbar?.showSnackbar("Added to list")
                                                                } catch (e: Exception) {
                                                                    profileSnackbar?.showSnackbar("Failed to add: ${e.message}")
                                                                }
                                                            }
                                                        }
                                                        showAddToListMenu = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                            }

                            // Follow/Unfollow button for other profiles — compact to
                            // match the header row height (32dp); primary-coloured
                            // text button so the affordance is still legible.
                            if (account != null && !account.isReadOnly && pubKeyHex != account.pubKeyHex) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val currentStatus = followState.currentStatusOrNull()

                                                followState.setFollowLoading()
                                                try {
                                                    val updatedEvent =
                                                        if (currentStatus?.isFollowing == true) {
                                                            unfollowUser(pubKeyHex, account, relayManager, myContactList)
                                                        } else {
                                                            followUser(pubKeyHex, account, relayManager, myContactList)
                                                        }

                                                    // Update both stored contact list and followState
                                                    myContactList = updatedEvent
                                                    followState.setFollowSuccess(updatedEvent, pubKeyHex)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    followState.setFollowError(e.message ?: "Failed to update follow status", e)
                                                }
                                            }
                                        },
                                        enabled = contactListLoaded && followState.state.value !is com.vitorpamplona.amethyst.commons.state.LoadingState.Loading,
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    ) {
                                        val state = followState.state.collectAsState().value
                                        val isFollowing = (state as? com.vitorpamplona.amethyst.commons.state.LoadingState.Success)?.data?.isFollowing ?: false
                                        val isLoading = state is com.vitorpamplona.amethyst.commons.state.LoadingState.Loading

                                        when {
                                            !contactListLoaded -> {
                                                androidx.compose.material3.CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("Loading...")
                                            }

                                            isLoading -> {
                                                androidx.compose.material3.CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(if (isFollowing) "Unfollowing..." else "Following...")
                                            }

                                            else -> {
                                                Icon(
                                                    if (isFollowing) MaterialSymbols.PersonRemove else MaterialSymbols.PersonAdd,
                                                    contentDescription = if (isFollowing) "Unfollow" else "Follow",
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(if (isFollowing) "Unfollow" else "Follow")
                                            }
                                        }
                                    }

                                    val errorMessage =
                                        followState.state
                                            .collectAsState()
                                            .value
                                            .errorOrNull()
                                    errorMessage?.let { error ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Profile card
                    item(key = "profile-card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                banner?.takeIf { it.isNotBlank() }?.let { bannerUrl ->
                                    AsyncImage(
                                        model = bannerUrl,
                                        contentDescription = "Profile banner",
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(MaterialTheme.shapes.medium),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    WoTBadgedAvatar(
                                        userHex = pubKeyHex,
                                        pictureUrl = picture,
                                        size = 56.dp,
                                        contentDescription = "Profile picture",
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            displayName ?: (pubKeyHex.hexToByteArrayOrNull()?.toNpub()?.take(20) ?: pubKeyHex.take(20)),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        val npub = pubKeyHex.hexToByteArrayOrNull()?.toNpub()
                                        var copied by remember { mutableStateOf(false) }

                                        LaunchedEffect(copied) {
                                            if (copied) {
                                                delay(2000)
                                                copied = false
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                (npub?.take(32) ?: pubKeyHex.take(32)) + "...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (npub != null) {
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                                        clipboard.setContents(StringSelection(npub), null)
                                                        copied = true
                                                    },
                                                    modifier = Modifier.size(20.dp),
                                                ) {
                                                    Icon(
                                                        if (copied) MaterialSymbols.Check else MaterialSymbols.ContentCopy,
                                                        contentDescription = if (copied) "Copied" else "Copy npub",
                                                        modifier = Modifier.size(14.dp),
                                                        tint =
                                                            if (copied) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                about?.takeIf { it.isNotBlank() }?.let { bio ->
                                    Spacer(Modifier.height(12.dp))
                                    val bioState = remember(bio) { CachedRichTextParser.parseText(bio, EmptyTagList) }
                                    DesktopRichText(
                                        content = bio,
                                        state = bioState,
                                        localCache = localCache,
                                        callbacks = RichTextCallbacks(onMentionClick = onNavigateToProfile),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // Profile metadata fields
                                nip05?.takeIf { it.isNotBlank() }?.let { addr ->
                                    Spacer(Modifier.height(8.dp))
                                    ProfileMetadataField(
                                        text = addr,
                                        icon = MaterialSymbols.CheckCircle,
                                        onClick = {
                                            runCatching {
                                                java.awt.Desktop.getDesktop().browse(
                                                    java.net.URI("https://${addr.substringAfter("@")}"),
                                                )
                                            }
                                        },
                                    )
                                }

                                website?.takeIf { it.isNotBlank() }?.let { site ->
                                    Spacer(Modifier.height(4.dp))
                                    ProfileMetadataField(
                                        text = site.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                                        copyValue = site,
                                        icon = MaterialSymbols.Language,
                                        onClick = {
                                            runCatching {
                                                val url = if (site.contains("://")) site else "https://$site"
                                                java.awt.Desktop
                                                    .getDesktop()
                                                    .browse(java.net.URI(url))
                                            }
                                        },
                                    )
                                }

                                lnAddress?.takeIf { it.isNotBlank() }?.let { addr ->
                                    Spacer(Modifier.height(4.dp))
                                    ProfileMetadataField(
                                        text = addr,
                                        icon = MaterialSymbols.Bolt,
                                    )
                                }

                                identities.forEach { identity ->
                                    Spacer(Modifier.height(4.dp))
                                    ProfileMetadataField(
                                        text =
                                            when (identity) {
                                                is TwitterIdentity -> "@${identity.identity}"
                                                is GitHubIdentity -> identity.identity
                                                is MastodonIdentity -> identity.identity
                                                else -> identity.identity
                                            },
                                        icon = MaterialSymbols.Language,
                                        onClick = {
                                            runCatching {
                                                java.awt.Desktop.getDesktop().browse(
                                                    java.net.URI(identity.toProofUrl()),
                                                )
                                            }
                                        },
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column {
                                        Text(
                                            "$followersCount",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Followers",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Column {
                                        Text(
                                            "$followingCount",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Following",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tabs
                    item(key = "tabs") {
                        PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                                Text("Notes", modifier = Modifier.padding(12.dp))
                            }
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                                Text("Replies", modifier = Modifier.padding(12.dp))
                            }
                            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                                Text(
                                    "Reads${if (articleEvents.isNotEmpty()) " (${articleEvents.size})" else ""}",
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                                Text("Gallery", modifier = Modifier.padding(12.dp))
                            }
                            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }) {
                                Text(
                                    "Highlights${if (highlightEvents.isNotEmpty()) " (${highlightEvents.size})" else ""}",
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }) {
                                Text(
                                    "Followers${if (followersCount > 0) " ($followersCount)" else ""}",
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 }) {
                                Text(
                                    "Following${if (followingCount > 0) " ($followingCount)" else ""}",
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Tab(selected = selectedTab == 7, onClick = { selectedTab = 7 }) {
                                Text(
                                    "Relays${if (relayList.isNotEmpty()) " (${relayList.size})" else ""}",
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Tab(selected = selectedTab == 8, onClick = { selectedTab = 8 }) {
                                Text("Bookmarks", modifier = Modifier.padding(12.dp))
                            }
                            Tab(selected = selectedTab == 9, onClick = { selectedTab = 9 }) {
                                Text("Mutual", modifier = Modifier.padding(12.dp))
                            }
                        }
                    }

                    // Tab content
                    when (selectedTab) {
                        0 -> {
                            when (profileFeedState) {
                                is FeedState.Loading -> {
                                    item(key = "loading") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                androidx.compose.material3.CircularProgressIndicator()
                                                Spacer(Modifier.height(16.dp))
                                                Text(
                                                    "Loading posts...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }

                                is FeedState.Empty -> {
                                    item(key = "empty") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "No posts yet",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }

                                is FeedState.FeedError -> {
                                    item(key = "error") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    "Failed to load posts",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    (profileFeedState as FeedState.FeedError).errorMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(16.dp))
                                                OutlinedButton(onClick = { retryTrigger++ }) {
                                                    Text("Retry")
                                                }
                                            }
                                        }
                                    }
                                }

                                is FeedState.Loaded -> {
                                    // loadedNotes collected outside LazyColumn in profileLoadedNotes
                                    items(profileLoadedNotes, key = { it.idHex }) { note ->
                                        FeedNoteCard(
                                            note = note,
                                            relayManager = relayManager,
                                            localCache = localCache,
                                            account = account,
                                            myPubKeyHex = account?.pubKeyHex,
                                            nwcConnection = nwcConnection,
                                            onReply = onCompose,
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

                        1 -> {
                            when (repliesFeedState) {
                                is FeedState.Loading -> {
                                    item(key = "replies-loading") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                androidx.compose.material3.CircularProgressIndicator()
                                                Spacer(Modifier.height(16.dp))
                                                Text(
                                                    "Loading replies...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }

                                is FeedState.Empty -> {
                                    item(key = "replies-empty") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "No replies yet",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }

                                is FeedState.FeedError -> {
                                    item(key = "replies-error") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    "Failed to load replies",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    (repliesFeedState as FeedState.FeedError).errorMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(16.dp))
                                                OutlinedButton(onClick = { retryTrigger++ }) {
                                                    Text("Retry")
                                                }
                                            }
                                        }
                                    }
                                }

                                is FeedState.Loaded -> {
                                    items(repliesLoadedNotes, key = { "reply-${it.idHex}" }) { note ->
                                        FeedNoteCard(
                                            note = note,
                                            relayManager = relayManager,
                                            localCache = localCache,
                                            account = account,
                                            myPubKeyHex = account?.pubKeyHex,
                                            nwcConnection = nwcConnection,
                                            onReply = onCompose,
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

                        2 -> {
                            if (articleEvents.isEmpty()) {
                                item(key = "no-articles") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "No long-form articles",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                items(
                                    articleEvents.sortedWith(compareByDescending<LongTextNoteEvent> { it.publishedAt() ?: it.createdAt }.thenBy { it.id }),
                                    key = { "art-${it.id}" },
                                ) { article ->
                                    LongFormCard(
                                        event = article,
                                        localCache = localCache,
                                        onAuthorClick = { onNavigateToProfile(article.pubKey) },
                                        onClick = {
                                            val addressTag = "${LongTextNoteEvent.KIND}:${article.pubKey}:${article.dTag()}"
                                            onNavigateToArticle(addressTag)
                                        },
                                    )
                                }
                            }
                        }

                        3 -> {
                            item(key = "gallery") {
                                GalleryTab(
                                    pictureEvents = pictureEvents,
                                    onImageClick = { urls, index -> lightboxState = LightboxState(urls, index) },
                                    modifier = Modifier.fillParentMaxHeight(),
                                )
                            }
                        }

                        4 -> {
                            if (highlightEvents.isEmpty()) {
                                item(key = "no-highlights") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "No published highlights",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                items(
                                    highlightEvents.sortedWith(compareByDescending<HighlightEvent> { it.createdAt }.thenBy { it.id }),
                                    key = { "hl-${it.id}" },
                                ) { highlight ->
                                    PublishedHighlightCard(
                                        highlight = highlight,
                                        localCache = localCache,
                                    )
                                }
                            }
                        }

                        5 -> {
                            if (followerList.isEmpty()) {
                                item(key = "no-followers") { ProfileTabMessage("No followers found yet") }
                            } else {
                                items(followerList.toList(), key = { "follower-$it" }) { pk ->
                                    val user = remember(pk) { localCache.getOrCreateUser(pk) }
                                    UserSearchCard(user = user, onClick = { onNavigateToProfile(pk) })
                                }
                            }
                        }

                        6 -> {
                            if (followingList.isEmpty()) {
                                item(key = "no-following") { ProfileTabMessage("No following found yet") }
                            } else {
                                items(followingList.toList(), key = { "following-$it" }) { pk ->
                                    val user = remember(pk) { localCache.getOrCreateUser(pk) }
                                    UserSearchCard(user = user, onClick = { onNavigateToProfile(pk) })
                                }
                            }
                        }

                        7 -> {
                            if (relayList.isEmpty()) {
                                item(key = "no-relays") { ProfileTabMessage("No relay list published") }
                            } else {
                                items(relayList, key = { "relay-${it.first}" }) { (url, read, write) ->
                                    RelayRowCard(url = url, canRead = read, canWrite = write)
                                }
                            }
                        }

                        8 -> {
                            if (bookmarkLoadedNotes.isEmpty()) {
                                item(key = "no-bookmarks") { ProfileTabMessage("No public bookmarks") }
                            } else {
                                items(bookmarkLoadedNotes, key = { "bm-${it.idHex}" }) { note ->
                                    FeedNoteCard(
                                        note = note,
                                        relayManager = relayManager,
                                        localCache = localCache,
                                        account = account,
                                        myPubKeyHex = account?.pubKeyHex,
                                        nwcConnection = nwcConnection,
                                        onReply = onCompose,
                                        onZapFeedback = onZapFeedback,
                                        onNavigateToProfile = onNavigateToProfile,
                                        onNavigateToThread = onNavigateToThread,
                                        onImageClick = { urls, index -> lightboxState = LightboxState(urls, index) },
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

                        9 -> {
                            if (account == null) {
                                item(key = "mutual-login") { ProfileTabMessage("Log in to see mutual posts") }
                            } else if (mutualLoadedNotes.isEmpty()) {
                                item(key = "no-mutual") { ProfileTabMessage("You haven't posted about this user") }
                            } else {
                                items(mutualLoadedNotes, key = { "mutual-${it.idHex}" }) { note ->
                                    FeedNoteCard(
                                        note = note,
                                        relayManager = relayManager,
                                        localCache = localCache,
                                        account = account,
                                        myPubKeyHex = account.pubKeyHex,
                                        nwcConnection = nwcConnection,
                                        onReply = onCompose,
                                        onZapFeedback = onZapFeedback,
                                        onNavigateToProfile = onNavigateToProfile,
                                        onNavigateToThread = onNavigateToThread,
                                        onImageClick = { urls, index -> lightboxState = LightboxState(urls, index) },
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
            }

            // Floating header — appears on scroll up when profile header is out of view.
            // Fully-qualified call to force the non-scoped overload; ReadingColumn
            // provides a ColumnScope in the outer lambda which would otherwise win
            // overload resolution and break the BoxScope Modifier.align call below.
            androidx.compose.animation.AnimatedVisibility(
                visible = showFloatingHeader,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canGoBack) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(
                                MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    WoTBadgedAvatar(
                        userHex = pubKeyHex,
                        pictureUrl = picture,
                        size = 28.dp,
                        contentDescription = "Profile picture",
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        displayName ?: pubKeyHex.take(12) + "...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
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

    // Edit Profile Dialog — full form with all 13 fields
    if (showEditProfile && account != null) {
        EditProfileDialog(
            account = account,
            relayManager = relayManager,
            latestMetadata = latestMetadataEvent,
            latestIdentities = latestIdentitiesEvent,
            onDismiss = { showEditProfile = false },
        )
    }

    if (showProfileReportDialog && iAccount != null) {
        com.vitorpamplona.amethyst.desktop.ui.note.ReportNoteDialog(
            onDismiss = { showProfileReportDialog = false },
            onReport = { type, comment ->
                scope.launch {
                    try {
                        iAccount.report(pubKeyHex, type, comment)
                        profileSnackbar?.showSnackbar("Report sent")
                    } catch (e: Exception) {
                        profileSnackbar?.showSnackbar("Report failed: ${e.message}")
                    }
                }
            },
            onBlockAndReport = { type, comment ->
                scope.launch {
                    try {
                        iAccount.report(pubKeyHex, type, comment)
                        iAccount.hideUser(pubKeyHex)
                        profileSnackbar?.showSnackbar("Reported & muted")
                    } catch (e: Exception) {
                        profileSnackbar?.showSnackbar("Report failed: ${e.message}")
                    }
                }
            },
        )
    }
}

/**
 * Follows a user by publishing an updated contact list event.
 */
private suspend fun followUser(
    pubKeyHex: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    currentContactList: ContactListEvent?,
): ContactListEvent =
    withContext(Dispatchers.IO) {
        // Use shared FollowAction from commons
        val updatedEvent = FollowAction.follow(pubKeyHex, account.signer, currentContactList)

        val connectedRelays = relayManager.connectedRelays.value
        if (connectedRelays.isEmpty()) {
            throw IllegalStateException("Cannot follow: No connected relays")
        }

        relayManager.broadcastToAll(updatedEvent)

        updatedEvent
    }

/**
 * Unfollows a user by publishing an updated contact list event without them.
 */
private suspend fun unfollowUser(
    pubKeyHex: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    currentContactList: ContactListEvent?,
): ContactListEvent =
    withContext(Dispatchers.IO) {
        if (currentContactList != null) {
            // Use shared FollowAction from commons
            val updatedEvent = FollowAction.unfollow(pubKeyHex, account.signer, currentContactList)

            val connectedRelays = relayManager.connectedRelays.value
            if (connectedRelays.isEmpty()) {
                throw IllegalStateException("Cannot unfollow: No connected relays")
            }

            relayManager.broadcastToAll(updatedEvent)

            updatedEvent
        } else {
            throw IllegalStateException("Cannot unfollow: No contact list available")
        }
    }

@Composable
private fun ProfileTabMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PublishedHighlightCard(
    highlight: HighlightEvent,
    localCache: DesktopLocalCache,
) {
    val articleAddress = highlight.inPostAddress()
    val articleTitle = articleAddress?.let { "Article" } ?: "Unknown source"

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Quoted highlight text
            Text(
                text = "\u201C${highlight.quote()}\u201D",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Note/comment
            val comment = highlight.comment()
            if (!comment.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Context (surrounding paragraph)
            val context = highlight.context()
            if (!context.isNullOrBlank() && context != highlight.quote()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = context.take(200) + if (context.length > 200) "\u2026" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Source article reference
            if (articleAddress != null) {
                Text(
                    text = "from ${articleAddress.dTag.ifBlank { "article" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ProfileMetadataField(
    text: String,
    copyValue: String = text,
    icon: com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol? = null,
    onClick: () -> Unit = {},
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (
                                event.buttons.isSecondaryPressed &&
                                event.changes.any { it.pressed && !it.previousPressed }
                            ) {
                                showContextMenu = true
                            }
                        }
                    }
                }.clickable { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
            DropdownMenuItem(
                text = { Text("Copy") },
                onClick = {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(copyValue), null)
                    showContextMenu = false
                },
            )
        }
    }
}
