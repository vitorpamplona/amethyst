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
package com.vitorpamplona.amethyst.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.feed.FeedHeader
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ios.account.AccountManager
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.drafts.DraftManager
import com.vitorpamplona.amethyst.ios.feeds.IosCalendarEventsFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosFollowedHashtagsFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosFollowingFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosGlobalFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosPollsFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosProfileFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosThreadFilter
import com.vitorpamplona.amethyst.ios.feeds.IosTrendingFeedFilter
import com.vitorpamplona.amethyst.ios.model.IosIAccount
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.nwc.NwcSettings
import com.vitorpamplona.amethyst.ios.nwc.ZapAmountDialog
import com.vitorpamplona.amethyst.ios.nwc.ZapController
import com.vitorpamplona.amethyst.ios.settings.AmethystTheme
import com.vitorpamplona.amethyst.ios.settings.AppSettings
import com.vitorpamplona.amethyst.ios.settings.NoteDensity
import com.vitorpamplona.amethyst.ios.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.ios.ui.AccountSwitcherScreen
import com.vitorpamplona.amethyst.ios.ui.BookmarksScreen
import com.vitorpamplona.amethyst.ios.ui.ComposeNoteScreen
import com.vitorpamplona.amethyst.ios.ui.EditProfileScreen
import com.vitorpamplona.amethyst.ios.ui.HashtagFollowScreen
import com.vitorpamplona.amethyst.ios.ui.LoginScreen
import com.vitorpamplona.amethyst.ios.ui.MuteListScreen
import com.vitorpamplona.amethyst.ios.ui.SettingsScreen
import com.vitorpamplona.amethyst.ios.ui.badges.BadgeDisplayData
import com.vitorpamplona.amethyst.ios.ui.badges.BadgeGallery
import com.vitorpamplona.amethyst.ios.ui.calendar.CalendarEventCard
import com.vitorpamplona.amethyst.ios.ui.calendar.toCalendarEventDisplayData
import com.vitorpamplona.amethyst.ios.ui.chats.IosChatScreen
import com.vitorpamplona.amethyst.ios.ui.chats.IosChatroomListState
import com.vitorpamplona.amethyst.ios.ui.chats.IosConversationListScreen
import com.vitorpamplona.amethyst.ios.ui.codesnippets.CodeSnippetCard
import com.vitorpamplona.amethyst.ios.ui.codesnippets.toCodeSnippetDisplayData
import com.vitorpamplona.amethyst.ios.ui.communities.CommunityDetailScreen
import com.vitorpamplona.amethyst.ios.ui.communities.CommunityListScreen
import com.vitorpamplona.amethyst.ios.ui.groups.RelayGroupsScreen
import com.vitorpamplona.amethyst.ios.ui.highlights.HighlightCard
import com.vitorpamplona.amethyst.ios.ui.highlights.toHighlightDisplayData
import com.vitorpamplona.amethyst.ios.ui.labels.LabelRow
import com.vitorpamplona.amethyst.ios.ui.labels.toLabelDisplayData
import com.vitorpamplona.amethyst.ios.ui.lists.ListsManagementScreen
import com.vitorpamplona.amethyst.ios.ui.liveactivities.LiveActivityCard
import com.vitorpamplona.amethyst.ios.ui.liveactivities.LiveActivityDetailScreen
import com.vitorpamplona.amethyst.ios.ui.liveactivities.toLiveActivityDisplayData
import com.vitorpamplona.amethyst.ios.ui.marketplace.ClassifiedCard
import com.vitorpamplona.amethyst.ios.ui.marketplace.ClassifiedDetailScreen
import com.vitorpamplona.amethyst.ios.ui.marketplace.toClassifiedDisplayData
import com.vitorpamplona.amethyst.ios.ui.note.ArticleCard
import com.vitorpamplona.amethyst.ios.ui.note.ArticleDetailScreen
import com.vitorpamplona.amethyst.ios.ui.note.EditNoteScreen
import com.vitorpamplona.amethyst.ios.ui.note.NoteCard
import com.vitorpamplona.amethyst.ios.ui.note.ReportDialog
import com.vitorpamplona.amethyst.ios.ui.note.UserStatusBadge
import com.vitorpamplona.amethyst.ios.ui.note.UserStatusDot
import com.vitorpamplona.amethyst.ios.ui.notifications.IosNotificationScreen
import com.vitorpamplona.amethyst.ios.ui.polls.PollCard
import com.vitorpamplona.amethyst.ios.ui.polls.toPollDisplayData
import com.vitorpamplona.amethyst.ios.ui.reactions.EmojiReactionPickerDialog
import com.vitorpamplona.amethyst.ios.ui.search.IosSearchScreen
import com.vitorpamplona.amethyst.ios.ui.toArticleDisplayData
import com.vitorpamplona.amethyst.ios.ui.toNoteDisplayData
import com.vitorpamplona.amethyst.ios.ui.wiki.WikiCard
import com.vitorpamplona.amethyst.ios.ui.wiki.toWikiDisplayData
import com.vitorpamplona.amethyst.ios.viewmodels.IosFeedViewModel
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.tags.CommunityTag
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.UIKit.UIPasteboard

/** Feed item spacing based on current note density preference. */
@Composable
private fun feedItemSpacing(): androidx.compose.ui.unit.Dp {
    val density by AppSettings.noteDensity.collectAsState()
    return when (density) {
        NoteDensity.COMPACT -> 4.dp
        NoteDensity.COMFORTABLE -> 8.dp
    }
}

enum class Tab(
    val icon: ImageVector,
    val label: String,
) {
    FEED(Icons.Default.Home, "Feed"),
    SEARCH(Icons.Default.Search, "Search"),
    NOTIFICATIONS(Icons.Default.Notifications, "Notifications"),
    MESSAGES(Icons.Default.MailOutline, "Messages"),
    PROFILE(Icons.Default.Person, "Profile"),
}

sealed class Screen {
    data object Feed : Screen()

    data object Search : Screen()

    data object Notifications : Screen()

    data object Messages : Screen()

    data object MyProfile : Screen()

    data object Settings : Screen()

    data class Profile(
        val pubKeyHex: String,
    ) : Screen()

    data class Thread(
        val noteId: String,
    ) : Screen()

    data class ComposeNote(
        val replyToNoteId: String? = null,
    ) : Screen()

    data class Chat(
        val roomKey: ChatroomKey,
    ) : Screen()

    data object AccountSwitcher : Screen()

    data object AddAccount : Screen()

    data object EditProfile : Screen()

    data object Bookmarks : Screen()

    data object MuteList : Screen()

    data object HashtagFollow : Screen()

    data class Article(
        val noteId: String,
    ) : Screen()

    data object Communities : Screen()

    data class CommunityDetail(
        val communityAddressId: String,
    ) : Screen()

    data class CommunityPost(
        val communityAddressId: String,
    ) : Screen()

    data object PeopleLists : Screen()

    data class LiveActivity(
        val noteId: String,
    ) : Screen()

    data class ClassifiedDetail(
        val noteId: String,
    ) : Screen()

    data class EditNote(
        val noteId: String,
    ) : Screen()

    data object RelayGroups : Screen()
}

enum class FeedMode { GLOBAL, FOLLOWING, HASHTAGS, TRENDING, POLLS, CALENDAR, LIVE, MARKETPLACE }

@Composable
fun App() {
    val accountManager = remember { AccountManager() }
    val accountState by accountManager.accountState.collectAsState()

    LaunchedEffect(Unit) {
        accountManager.tryRestoreSession()
    }

    AmethystTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val state = accountState) {
                is AccountState.LoggedOut -> {
                    LoginScreen(
                        onLogin = { key -> accountManager.login(key) },
                        onCreateAccount = { accountManager.createAccount() },
                    )
                }

                is AccountState.LoggedIn -> {
                    MainScreen(
                        account = state,
                        accountManager = accountManager,
                        onLogout = { accountManager.logout() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    account: AccountState.LoggedIn,
    accountManager: AccountManager,
    onLogout: () -> Unit,
) {
    val relayManager = remember { IosRelayConnectionManager() }
    val localCache = remember { IosLocalCache() }
    val draftManager =
        remember(account.signer) {
            DraftManager(account.signer, relayManager)
        }
    val coordinator =
        remember {
            IosSubscriptionsCoordinator(
                CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob()),
                localCache,
            )
        }
    val appScope = rememberCoroutineScope()
    val iosAccount =
        remember(account) {
            IosIAccount(account, localCache, relayManager, appScope)
        }
    val chatroomListState =
        remember(iosAccount) {
            IosChatroomListState(iosAccount, localCache, relayManager, appScope)
        }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Feed) }
    var selectedTab by remember { mutableStateOf(Tab.FEED) }
    var composeDraft by remember { mutableStateOf("") }
    val navStack = remember { mutableListOf<Screen>() }

    fun navigateTo(screen: Screen) {
        navStack.add(currentScreen)
        currentScreen = screen
    }

    fun goBack() {
        if (navStack.isNotEmpty()) {
            currentScreen = navStack.removeAt(navStack.lastIndex)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val exceptionHandler =
        kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            platform.Foundation.NSLog(
                "CoroutineException: " + (throwable.message ?: "unknown") + " " + throwable::class.simpleName.orEmpty(),
            )
        }
    val scope = rememberCoroutineScope { exceptionHandler + kotlinx.coroutines.SupervisorJob() }

    val onLikeNote: (String) -> Unit = { noteId ->
        scope.launch {
            val note = localCache.getNoteIfExists(noteId)
            val event = note?.event
            if (event != null) {
                val template = ReactionEvent.like(EventHintBundle(event, null, null))
                val signed = account.signer.sign(template)
                relayManager.broadcastToAll(signed)
                snackbarHostState.showSnackbar("\uD83E\uDD19 Liked!")
            }
        }
    }

    val onBoostNote: (String) -> Unit = { noteId ->
        scope.launch {
            val note = localCache.getNoteIfExists(noteId)
            val event = note?.event
            if (event != null) {
                val template = RepostEvent.build(EventHintBundle(event, null, null))
                val signed = account.signer.sign(template)
                relayManager.broadcastToAll(signed)
                snackbarHostState.showSnackbar("\uD83D\uDD01 Reposted!")
            }
        }
    }

    var bookmarkListState by remember { mutableStateOf<BookmarkListEvent?>(null) }
    var bookmarkedNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load bookmark list from cache on start
    LaunchedEffect(account.pubKeyHex) {
        try {
            val address = BookmarkListEvent.createBookmarkAddress(account.pubKeyHex)
            val cachedNote = localCache.getOrCreateAddressableNote(address)
            val cachedEvent = cachedNote.event as? BookmarkListEvent
            if (cachedEvent != null) {
                bookmarkListState = cachedEvent
                bookmarkedNoteIds =
                    cachedEvent
                        .publicBookmarks()
                        .filterIsInstance<EventBookmark>()
                        .map { it.eventId }
                        .toSet()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (bookmarks): " + (e.message ?: "unknown"))
        }
    }

    val onBookmarkNote: (String) -> Unit = { noteId ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val isCurrentlyBookmarked = noteId in bookmarkedNoteIds
                    val bookmark = EventBookmark(noteId)
                    val newList =
                        if (isCurrentlyBookmarked) {
                            val existing = bookmarkListState
                            if (existing != null) {
                                BookmarkListEvent.remove(existing, bookmark, isPrivate = false, signer = account.signer)
                            } else {
                                return@launch
                            }
                        } else {
                            val existing = bookmarkListState
                            if (existing != null) {
                                BookmarkListEvent.add(existing, bookmark, isPrivate = false, signer = account.signer)
                            } else {
                                BookmarkListEvent.create(bookmark, isPrivate = false, signer = account.signer)
                            }
                        }
                    bookmarkListState = newList
                    bookmarkedNoteIds =
                        newList
                            .publicBookmarks()
                            .filterIsInstance<EventBookmark>()
                            .map { it.eventId }
                            .toSet()
                    localCache.consume(newList, null)
                    relayManager.broadcastToAll(newList)
                    if (isCurrentlyBookmarked) {
                        snackbarHostState.showSnackbar("Bookmark removed")
                    } else {
                        snackbarHostState.showSnackbar("\uD83D\uDD16 Bookmarked!")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to update bookmark")
                }
            }
        }
    }

    // ── Zap state ──
    var showZapDialog by remember { mutableStateOf<String?>(null) }
    var zapSending by remember { mutableStateOf(false) }
    var zapError by remember { mutableStateOf<String?>(null) }

    val onZapNote: (String) -> Unit = { noteId ->
        if (!NwcSettings.isConfigured()) {
            scope.launch {
                snackbarHostState.showSnackbar("⚡ Set up Wallet Connect in Settings to send zaps")
            }
        } else {
            showZapDialog = noteId
            zapError = null
        }
    }

    // ── Custom emoji reaction state ──
    var showEmojiPicker by remember { mutableStateOf<String?>(null) }

    val onLikeNoteLongPress: (String) -> Unit = { noteId ->
        showEmojiPicker = noteId
    }

    // ── Mute state ──
    var muteListState by remember { mutableStateOf<MuteListEvent?>(null) }
    var mutedUserPubKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load mute list from cache on start
    LaunchedEffect(account.pubKeyHex) {
        try {
            val address = MuteListEvent.createAddress(account.pubKeyHex)
            val cachedNote = localCache.getOrCreateAddressableNote(address)
            val cachedEvent = cachedNote.event as? MuteListEvent
            if (cachedEvent != null) {
                muteListState = cachedEvent
                mutedUserPubKeys =
                    cachedEvent
                        .publicMutes()
                        .filterIsInstance<UserTag>()
                        .map { it.pubKey }
                        .toSet()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (mute list): " + (e.message ?: "unknown"))
        }
    }

    val onMuteUser: (String) -> Unit = { targetPubKeyHex ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val mute = UserTag(targetPubKeyHex)
                    val newList =
                        if (muteListState != null) {
                            MuteListEvent.add(muteListState!!, mute, isPrivate = false, signer = account.signer)
                        } else {
                            MuteListEvent.create(mute, isPrivate = false, signer = account.signer)
                        }
                    muteListState = newList
                    mutedUserPubKeys =
                        newList
                            .publicMutes()
                            .filterIsInstance<UserTag>()
                            .map { it.pubKey }
                            .toSet()
                    localCache.consume(newList, null)
                    relayManager.broadcastToAll(newList)
                    snackbarHostState.showSnackbar("🔇 User muted")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to mute user")
                }
            }
        }
    }

    val onUnmuteUser: (String) -> Unit = { targetPubKeyHex ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val existing = muteListState ?: return@launch
                    val mute = UserTag(targetPubKeyHex)
                    val newList = MuteListEvent.remove(existing, mute, signer = account.signer)
                    muteListState = newList
                    mutedUserPubKeys =
                        newList
                            .publicMutes()
                            .filterIsInstance<UserTag>()
                            .map { it.pubKey }
                            .toSet()
                    localCache.consume(newList, null)
                    relayManager.broadcastToAll(newList)
                    snackbarHostState.showSnackbar("User unmuted")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to unmute user")
                }
            }
        }
    }

    // ── Hashtag follow state (NIP-51 kind 10015) ──
    var hashtagListState by remember { mutableStateOf<HashtagListEvent?>(null) }
    var followedHashtags by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load hashtag list from cache on start
    LaunchedEffect(account.pubKeyHex) {
        try {
            val address = HashtagListEvent.createAddress(account.pubKeyHex)
            val cachedNote = localCache.getOrCreateAddressableNote(address)
            val cachedEvent = cachedNote.event as? HashtagListEvent
            if (cachedEvent != null) {
                hashtagListState = cachedEvent
                followedHashtags = cachedEvent.publicHashtags().toSet()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (hashtags): " + (e.message ?: "unknown"))
        }
    }

    val onFollowHashtag: (String) -> Unit = { hashtag ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val newList =
                        if (hashtagListState != null) {
                            HashtagListEvent.add(
                                earlierVersion = hashtagListState!!,
                                hashtag = hashtag.lowercase(),
                                isPrivate = false,
                                signer = account.signer,
                            )
                        } else {
                            HashtagListEvent.create(
                                hashtag = hashtag.lowercase(),
                                isPrivate = false,
                                signer = account.signer,
                            )
                        }
                    hashtagListState = newList
                    followedHashtags = newList.publicHashtags().toSet()
                    localCache.consume(newList, null)
                    relayManager.broadcastToAll(newList)
                    snackbarHostState.showSnackbar("#$hashtag followed!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to follow hashtag")
                }
            }
        }
    }

    val onUnfollowHashtag: (String) -> Unit = { hashtag ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val existing = hashtagListState ?: return@launch
                    val newList =
                        HashtagListEvent.remove(
                            earlierVersion = existing,
                            hashtag = hashtag,
                            signer = account.signer,
                        )
                    hashtagListState = newList
                    followedHashtags = newList.publicHashtags().toSet()
                    localCache.consume(newList, null)
                    relayManager.broadcastToAll(newList)
                    snackbarHostState.showSnackbar("#$hashtag unfollowed")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to unfollow hashtag")
                }
            }
        }
    }

    // ── Community join state (NIP-72 kind 10004) ──
    var communityListState by remember { mutableStateOf<CommunityListEvent?>(null) }
    var joinedCommunityIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load community list from cache on start
    LaunchedEffect(account.pubKeyHex) {
        try {
            val address = CommunityListEvent.createAddress(account.pubKeyHex)
            val cachedNote = localCache.getOrCreateAddressableNote(address)
            val cachedEvent = cachedNote.event as? CommunityListEvent
            if (cachedEvent != null) {
                communityListState = cachedEvent
                joinedCommunityIds = cachedEvent.publicCommunityIds().toSet()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (communities): " + (e.message ?: "unknown"))
        }
    }

    val onJoinCommunity: (String) -> Unit = { communityAddressId ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val tag =
                        CommunityTag(
                            com.vitorpamplona.quartz.nip01Core.core.Address
                                .parse(communityAddressId) ?: return@launch,
                        )
                    val newList =
                        if (communityListState != null) {
                            CommunityListEvent.add(communityListState!!, tag, isPrivate = false, signer = account.signer)
                        } else {
                            CommunityListEvent.create(tag, isPrivate = false, signer = account.signer)
                        }
                    communityListState = newList
                    joinedCommunityIds = newList.publicCommunityIds().toSet()
                    localCache.consume(newList, null)
                    relayManager.broadcastToAll(newList)
                    snackbarHostState.showSnackbar("Joined community!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to join community")
                }
            }
        }
    }

    val onLeaveCommunity: (String) -> Unit = { communityAddressId ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val existing = communityListState ?: return@launch
                    val tag =
                        CommunityTag(
                            com.vitorpamplona.quartz.nip01Core.core.Address
                                .parse(communityAddressId) ?: return@launch,
                        )
                    val newList = CommunityListEvent.remove(existing, tag, signer = account.signer)
                    communityListState = newList
                    joinedCommunityIds = newList.publicCommunityIds().toSet()
                    localCache.consume(newList, null)
                    relayManager.broadcastToAll(newList)
                    snackbarHostState.showSnackbar("Left community")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to leave community")
                }
            }
        }
    }

    // ── Report state ──
    var showReportDialog by remember { mutableStateOf(false) }
    var reportTargetNoteId by remember { mutableStateOf<String?>(null) }
    var reportTargetPubKey by remember { mutableStateOf<String?>(null) }

    val onReportNote: (String, String) -> Unit = { noteId, authorPubKeyHex ->
        reportTargetNoteId = noteId
        reportTargetPubKey = authorPubKeyHex
        showReportDialog = true
    }

    val onSubmitReport: (ReportType) -> Unit = { reportType ->
        if (!account.isReadOnly) {
            scope.launch {
                try {
                    val noteId = reportTargetNoteId
                    val pubKey = reportTargetPubKey
                    if (noteId != null && pubKey != null) {
                        val note = localCache.getNoteIfExists(noteId)
                        val event = note?.event
                        val template =
                            if (event != null) {
                                ReportEvent.build(event, reportType)
                            } else {
                                ReportEvent.build(pubKey, reportType)
                            }
                        val signed = account.signer.sign(template)
                        relayManager.broadcastToAll(signed)
                        snackbarHostState.showSnackbar("🚩 Report sent")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to send report")
                } finally {
                    showReportDialog = false
                    reportTargetNoteId = null
                    reportTargetPubKey = null
                }
            }
        }
    }

    // ── Delete note state (NIP-09) ──
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteTargetNoteId by remember { mutableStateOf<String?>(null) }

    val onDeleteNote: (String) -> Unit = { noteId ->
        deleteTargetNoteId = noteId
        showDeleteConfirmDialog = true
    }

    val onConfirmDelete: () -> Unit = {
        if (!account.isReadOnly) {
            val noteId = deleteTargetNoteId
            if (noteId != null) {
                scope.launch {
                    try {
                        val note = localCache.getNoteIfExists(noteId)
                        val event = note?.event
                        if (event != null) {
                            val template = DeletionEvent.build(listOf(event))
                            val signed = account.signer.sign(template)
                            relayManager.broadcastToAll(signed)
                            localCache.markAsDeleted(noteId)
                            snackbarHostState.showSnackbar("🗑️ Note deleted")
                        } else {
                            snackbarHostState.showSnackbar("Note not found in cache")
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        platform.Foundation.NSLog("Delete error: " + (e.message ?: "unknown"))
                        snackbarHostState.showSnackbar("Failed to delete note")
                    } finally {
                        showDeleteConfirmDialog = false
                        deleteTargetNoteId = null
                    }
                }
            }
        }
    }

    // ── Clipboard helpers ──
    val onEditNote: (String) -> Unit = { noteId ->
        navigateTo(Screen.EditNote(noteId))
    }

    val onCopyNoteId: (String) -> Unit = { noteId ->
        UIPasteboard.generalPasteboard.string = noteId
        scope.launch { snackbarHostState.showSnackbar("Note ID copied") }
    }

    val onCopyNoteText: (String) -> Unit = { text ->
        UIPasteboard.generalPasteboard.string = text
        scope.launch { snackbarHostState.showSnackbar("Note text copied") }
    }

    val onCopyAuthorNpub: (String) -> Unit = { pubKeyHex ->
        val npub =
            try {
                pubKeyHex.hexToByteArrayOrNull()?.toNpub() ?: pubKeyHex
            } catch (e: Exception) {
                pubKeyHex
            }
        UIPasteboard.generalPasteboard.string = npub
        scope.launch { snackbarHostState.showSnackbar("Author npub copied") }
    }

    val onFollowUser: (String) -> Unit = { targetPubKeyHex ->
        if (!account.isReadOnly) {
            scope.launch {
                val current = localCache.latestContactList.value
                val newContactList =
                    if (current != null) {
                        ContactListEvent.followUser(current, targetPubKeyHex, account.signer)
                    } else {
                        ContactListEvent.createFromScratch(
                            followUsers =
                                listOf(
                                    com.vitorpamplona.quartz.nip02FollowList.tags
                                        .ContactTag(targetPubKeyHex, null, null),
                                ),
                            relayUse = null,
                            signer = account.signer,
                        )
                    }
                // Optimistic local update
                localCache.consume(newContactList, null)
                relayManager.broadcastToAll(newContactList)
                snackbarHostState.showSnackbar("\u2705 Followed!")
            }
        }
    }

    val onUnfollowUser: (String) -> Unit = { targetPubKeyHex ->
        if (!account.isReadOnly) {
            scope.launch {
                val current = localCache.latestContactList.value
                if (current != null) {
                    val newContactList =
                        ContactListEvent.unfollowUser(current, targetPubKeyHex, account.signer)
                    localCache.consume(newContactList, null)
                    relayManager.broadcastToAll(newContactList)
                    snackbarHostState.showSnackbar("Unfollowed")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            relayManager.addDefaultRelays()
            // Small delay to let the UI compose before relay sync starts.
            // Prevents ConcurrentModificationException in LargeCache.forEach
            // when syncFilters runs while subscriptions are being set up.
            kotlinx.coroutines.delay(500)
            relayManager.connect()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (relay connect): " + (e.message ?: "unknown"))
        }
    }

    // Subscribe to DMs once relays are connected
    LaunchedEffect(account) {
        try {
            relayManager.connectedRelays.first { it.isNotEmpty() }
            val relays = relayManager.connectedRelays.value

            // NIP-04 DMs TO the user
            relayManager.subscribe(
                "dm-inbox",
                listOf(FilterBuilders.nip04DmsToUser(account.pubKeyHex, limit = 200)),
                relays,
                object : SubscriptionListener {
                    override fun onEvent(
                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event is PrivateDmEvent) {
                            val note = localCache.getOrCreateNote(event.id)
                            val author = localCache.getOrCreateUser(event.pubKey)
                            if (note.event == null) {
                                note.loadEvent(event, author, emptyList())
                                note.addRelay(relay)
                            }
                            iosAccount.chatroomList.addMessage(
                                event.chatroomKey(iosAccount.pubKey),
                                note,
                            )
                        }
                        coordinator.consumeEvent(event, relay)
                    }
                },
            )

            // NIP-04 DMs FROM the user
            relayManager.subscribe(
                "dm-outbox",
                listOf(FilterBuilders.nip04DmsFromUser(account.pubKeyHex, limit = 200)),
                relays,
                object : SubscriptionListener {
                    override fun onEvent(
                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event is PrivateDmEvent) {
                            val note = localCache.getOrCreateNote(event.id)
                            val author = localCache.getOrCreateUser(event.pubKey)
                            if (note.event == null) {
                                note.loadEvent(event, author, emptyList())
                                note.addRelay(relay)
                            }
                            iosAccount.chatroomList.addMessage(
                                event.chatroomKey(iosAccount.pubKey),
                                note,
                            )
                        }
                        coordinator.consumeEvent(event, relay)
                    }
                },
            )

            // NIP-17 gift-wrapped DMs
            relayManager.subscribe(
                "dm-giftwrap",
                listOf(
                    Filter(
                        kinds = listOf(GiftWrapEvent.KIND),
                        tags = mapOf("p" to listOf(account.pubKeyHex)),
                    ),
                ),
                relays,
                object : SubscriptionListener {
                    override fun onEvent(
                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event is GiftWrapEvent) {
                            appScope.launch {
                                try {
                                    val seal =
                                        event.unwrapOrNull(iosAccount.signer)
                                            as? SealedRumorEvent ?: return@launch
                                    val innerEvent =
                                        seal.unsealOrNull(iosAccount.signer) ?: return@launch
                                    when (innerEvent) {
                                        is ChatMessageEvent -> {
                                            val innerNote =
                                                localCache.getOrCreateNote(innerEvent.id)
                                            val innerAuthor =
                                                localCache.getOrCreateUser(innerEvent.pubKey)
                                            if (innerNote.event == null) {
                                                innerNote.loadEvent(
                                                    innerEvent,
                                                    innerAuthor,
                                                    emptyList(),
                                                )
                                            }
                                            iosAccount.chatroomList.addMessage(
                                                innerEvent.chatroomKey(iosAccount.pubKey),
                                                innerNote,
                                            )
                                        }

                                        is ChatMessageEncryptedFileHeaderEvent -> {
                                            val innerNote =
                                                localCache.getOrCreateNote(innerEvent.id)
                                            val innerAuthor =
                                                localCache.getOrCreateUser(innerEvent.pubKey)
                                            if (innerNote.event == null) {
                                                innerNote.loadEvent(
                                                    innerEvent,
                                                    innerAuthor,
                                                    emptyList(),
                                                )
                                            }
                                            iosAccount.chatroomList.addMessage(
                                                innerEvent.chatroomKey(iosAccount.pubKey),
                                                innerNote,
                                            )
                                        }

                                        else -> {}
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    platform.Foundation.NSLog("GiftWrap processing error: " + (e.message ?: "unknown"))
                                }
                            }
                        }
                    }
                },
            )

            // Notifications: reactions, reposts, mentions, zaps targeting the user
            relayManager.subscribe(
                "notifications",
                listOf(FilterBuilders.notificationsForUser(account.pubKeyHex, limit = 300)),
                relays,
                object : SubscriptionListener {
                    override fun onEvent(
                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        coordinator.consumeEvent(event, relay)
                    }
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (DM subscriptions): " + (e.message ?: "unknown"))
        }
    }

    val showBottomBar =
        currentScreen is Screen.Feed || currentScreen is Screen.Search ||
            currentScreen is Screen.Notifications || currentScreen is Screen.Messages ||
            currentScreen is Screen.MyProfile

    val showTopBar = showBottomBar
    val showFab = currentScreen is Screen.Feed && !account.isReadOnly

    // Report dialog
    if (showReportDialog) {
        ReportDialog(
            onDismiss = {
                showReportDialog = false
                reportTargetNoteId = null
                reportTargetPubKey = null
            },
            onReport = onSubmitReport,
        )
    }

    // ── Delete Confirmation Dialog (NIP-09) ──
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                deleteTargetNoteId = null
            },
            title = { Text("Delete Note?") },
            text = {
                Text(
                    "This will broadcast a deletion request to relays. " +
                        "Relays may or may not honour it.",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    deleteTargetNoteId = null
                }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Zap Amount Dialog (NIP-47 NWC) ──
    showZapDialog?.let { noteId ->
        ZapAmountDialog(
            noteId = noteId,
            isSending = zapSending,
            errorMessage = zapError,
            onDismiss = {
                showZapDialog = null
                zapSending = false
                zapError = null
            },
            onZap = { id, amountSats ->
                zapSending = true
                zapError = null
                scope.launch {
                    val controller = ZapController(account.signer, relayManager, localCache)
                    val result = controller.zap(id, amountSats)
                    result.fold(
                        onSuccess = { msg ->
                            showZapDialog = null
                            zapSending = false
                            snackbarHostState.showSnackbar("⚡ $msg")
                        },
                        onFailure = { err ->
                            zapSending = false
                            zapError = err.message ?: "Zap failed"
                        },
                    )
                }
            },
        )
    }

    // ── Custom Emoji Reaction Picker (NIP-30) ──
    showEmojiPicker?.let { noteId ->
        EmojiReactionPickerDialog(
            customEmojis = emptyList(), // TODO: load from followed emoji packs
            onSelect = { content, customEmoji ->
                showEmojiPicker = null
                scope.launch {
                    val note = localCache.getNoteIfExists(noteId)
                    val event = note?.event
                    if (event != null) {
                        val template =
                            if (customEmoji != null) {
                                ReactionEvent.build(customEmoji, EventHintBundle(event, null, null))
                            } else {
                                ReactionEvent.build(content, EventHintBundle(event, null, null))
                            }
                        val signed = account.signer.sign(template)
                        relayManager.broadcastToAll(signed)
                        snackbarHostState.showSnackbar("❤️ Reacted!")
                    }
                }
            },
            onDismiss = { showEmojiPicker = null },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            when (selectedTab) {
                                Tab.FEED -> "Amethyst"
                                Tab.SEARCH -> "Search"
                                Tab.NOTIFICATIONS -> "Notifications"
                                Tab.MESSAGES -> "Messages"
                                Tab.PROFILE -> "Profile"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        IconButton(onClick = { navigateTo(Screen.Settings) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { navigateTo(Screen.ComposeNote()) },
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Compose Note",
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp)) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                navStack.clear()
                                currentScreen =
                                    when (tab) {
                                        Tab.FEED -> Screen.Feed
                                        Tab.SEARCH -> Screen.Search
                                        Tab.NOTIFICATIONS -> Screen.Notifications
                                        Tab.MESSAGES -> Screen.Messages
                                        Tab.PROFILE -> Screen.MyProfile
                                    }
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val screen = currentScreen) {
                is Screen.Feed -> {
                    IosFeedContent(
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        pubKeyHex = account.pubKeyHex,
                        followedHashtags = followedHashtags,
                        onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                        onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        onNavigateToArticle = { navigateTo(Screen.Article(it)) },
                        onNavigateToCommunities = { navigateTo(Screen.Communities) },
                        onNavigateToLiveActivity = { navigateTo(Screen.LiveActivity(it)) },
                        onNavigateToClassified = { navigateTo(Screen.ClassifiedDetail(it)) },
                        onBoost = onBoostNote,
                        onLike = onLikeNote,
                        onZap = onZapNote,
                        onBookmark = onBookmarkNote,
                        bookmarkedNoteIds = bookmarkedNoteIds,
                        onCopyNoteId = onCopyNoteId,
                        onCopyNoteText = onCopyNoteText,
                        onCopyAuthorNpub = onCopyAuthorNpub,
                        onMuteUser = onMuteUser,
                        onReport = onReportNote,
                        onDelete = if (account.isReadOnly) null else onDeleteNote,
                        mutedUserPubKeys = mutedUserPubKeys,
                    )
                }

                is Screen.Search -> {
                    IosSearchScreen(
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                        onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        onBoost = onBoostNote,
                        onLike = onLikeNote,
                        onZap = onZapNote,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                is Screen.Notifications -> {
                    IosNotificationScreen(
                        pubKeyHex = account.pubKeyHex,
                        localCache = localCache,
                        onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                is Screen.Messages -> {
                    IosConversationListScreen(
                        state = chatroomListState,
                        onConversationSelected = { roomKey ->
                            navigateTo(Screen.Chat(roomKey))
                        },
                    )
                }

                is Screen.Chat -> {
                    IosChatScreen(
                        roomKey = screen.roomKey,
                        account = iosAccount,
                        cacheProvider = localCache,
                        onBack = { goBack() },
                        onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                    )
                }

                is Screen.MyProfile -> {
                    IosProfileContent(
                        pubKeyHex = account.pubKeyHex,
                        loggedInPubKeyHex = account.pubKeyHex,
                        isReadOnly = account.isReadOnly,
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                        onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        onNavigateToSettings = { navigateTo(Screen.Settings) },
                        onEditProfile =
                            if (!account.isReadOnly) {
                                { navigateTo(Screen.EditProfile) }
                            } else {
                                null
                            },
                        onFollow = onFollowUser,
                        onUnfollow = onUnfollowUser,
                        onBoost = onBoostNote,
                        onLike = onLikeNote,
                        onZap = onZapNote,
                        onBookmark = onBookmarkNote,
                        bookmarkedNoteIds = bookmarkedNoteIds,
                        onCopyNoteId = onCopyNoteId,
                        onCopyNoteText = onCopyNoteText,
                        onCopyAuthorNpub = onCopyAuthorNpub,
                        onMuteUser = onMuteUser,
                        onReport = onReportNote,
                        onDelete = if (account.isReadOnly) null else onDeleteNote,
                    )
                }

                is Screen.EditProfile -> {
                    EditProfileScreen(
                        account = account,
                        localCache = localCache,
                        relayManager = relayManager,
                        onBack = { goBack() },
                        onSaved = { goBack() },
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        npub = account.npub,
                        pubKeyHex = account.pubKeyHex,
                        keyPair = account.keyPair,
                        relayManager = relayManager,
                        onBack = { goBack() },
                        onLogout = onLogout,
                        onAccountSwitcher = { navigateTo(Screen.AccountSwitcher) },
                        onEditProfile =
                            if (!account.isReadOnly) {
                                { navigateTo(Screen.EditProfile) }
                            } else {
                                null
                            },
                        onBookmarks = { navigateTo(Screen.Bookmarks) },
                        onMuteList = { navigateTo(Screen.MuteList) },
                        onHashtagFollow = { navigateTo(Screen.HashtagFollow) },
                        onCommunities = { navigateTo(Screen.Communities) },
                        onPeopleLists = { navigateTo(Screen.PeopleLists) },
                        onRelayGroups = { navigateTo(Screen.RelayGroups) },
                    )
                }

                is Screen.MuteList -> {
                    MuteListScreen(
                        mutedUserPubKeys = mutedUserPubKeys,
                        localCache = localCache,
                        onUnmute = onUnmuteUser,
                        onBack = { goBack() },
                    )
                }

                is Screen.HashtagFollow -> {
                    HashtagFollowScreen(
                        followedHashtags = followedHashtags,
                        onFollow = onFollowHashtag,
                        onUnfollow = onUnfollowHashtag,
                        onBack = { goBack() },
                    )
                }

                is Screen.Bookmarks -> {
                    BookmarksScreen(
                        account = account,
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        onBack = { goBack() },
                        onNavigateToProfile = { pubKey -> navigateTo(Screen.Profile(pubKey)) },
                        onNavigateToThread = { noteId -> navigateTo(Screen.Thread(noteId)) },
                        onBoost = onBoostNote,
                        onLike = onLikeNote,
                        onZap = onZapNote,
                        onBookmark = onBookmarkNote,
                    )
                }

                is Screen.AccountSwitcher -> {
                    AccountSwitcherScreen(
                        accountManager = accountManager,
                        localCache = localCache,
                        onBack = { goBack() },
                        onAddAccount = { navigateTo(Screen.AddAccount) },
                    )
                }

                is Screen.AddAccount -> {
                    LoginScreen(
                        onLogin = { key ->
                            val result = accountManager.login(key)
                            if (result.isSuccess) {
                                // Pop back to account switcher
                                goBack()
                            }
                            result
                        },
                        onCreateAccount = {
                            val created = accountManager.createAccount()
                            goBack()
                            created
                        },
                    )
                }

                is Screen.Profile -> {
                    Column {
                        TopAppBar(
                            title = { Text("Profile") },
                            navigationIcon = {
                                IconButton(onClick = { goBack() }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            },
                        )
                        IosProfileContent(
                            pubKeyHex = screen.pubKeyHex,
                            loggedInPubKeyHex = account.pubKeyHex,
                            isReadOnly = account.isReadOnly,
                            relayManager = relayManager,
                            localCache = localCache,
                            coordinator = coordinator,
                            onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                            onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                            onFollow = onFollowUser,
                            onUnfollow = onUnfollowUser,
                            onBoost = onBoostNote,
                            onLike = onLikeNote,
                            onZap = onZapNote,
                            onBookmark = onBookmarkNote,
                            bookmarkedNoteIds = bookmarkedNoteIds,
                            onCopyNoteId = onCopyNoteId,
                            onCopyNoteText = onCopyNoteText,
                            onCopyAuthorNpub = onCopyAuthorNpub,
                            onMuteUser = onMuteUser,
                            onReport = onReportNote,
                            onDelete = if (account.isReadOnly) null else onDeleteNote,
                        )
                    }
                }

                is Screen.Thread -> {
                    IosThreadContent(
                        noteId = screen.noteId,
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        onBack = { goBack() },
                        onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                        onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        onReply = { noteId -> navigateTo(Screen.ComposeNote(replyToNoteId = noteId)) },
                        onBoost = onBoostNote,
                        onLike = onLikeNote,
                        onZap = onZapNote,
                        onBookmark = onBookmarkNote,
                        bookmarkedNoteIds = bookmarkedNoteIds,
                        onCopyNoteId = onCopyNoteId,
                        onCopyNoteText = onCopyNoteText,
                        onCopyAuthorNpub = onCopyAuthorNpub,
                        onMuteUser = onMuteUser,
                        onReport = onReportNote,
                        onDelete = if (account.isReadOnly) null else onDeleteNote,
                    )
                }

                is Screen.ComposeNote -> {
                    ComposeNoteScreen(
                        account = account,
                        relayManager = relayManager,
                        localCache = localCache,
                        draftManager = draftManager,
                        replyToNoteId = screen.replyToNoteId,
                        initialDraft = composeDraft,
                        onDraftChanged = { composeDraft = it },
                        onBack = { goBack() },
                        onPublished = {
                            composeDraft = ""
                            goBack()
                        },
                    )
                }

                is Screen.Article -> {
                    val note = localCache.getNoteIfExists(screen.noteId)
                    val articleData = note?.toArticleDisplayData(localCache)
                    if (articleData != null) {
                        ArticleDetailScreen(
                            article = articleData,
                            onBack = { goBack() },
                            onAuthorClick = { navigateTo(Screen.Profile(it)) },
                        )
                    } else {
                        Column {
                            androidx.compose.material3.TopAppBar(
                                title = { Text("Article") },
                                navigationIcon = {
                                    IconButton(onClick = { goBack() }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                        )
                                    }
                                },
                            )
                            com.vitorpamplona.amethyst.commons.ui.components.EmptyState(
                                title = "Article not found",
                            )
                        }
                    }
                }

                is Screen.Communities -> {
                    CommunityListScreen(
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        joinedCommunityIds = joinedCommunityIds,
                        onNavigateToCommunity = { navigateTo(Screen.CommunityDetail(it)) },
                        onBack = { goBack() },
                    )
                }

                is Screen.CommunityDetail -> {
                    CommunityDetailScreen(
                        communityAddressId = screen.communityAddressId,
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        isJoined = screen.communityAddressId in joinedCommunityIds,
                        isReadOnly = account.isReadOnly,
                        onJoin = { onJoinCommunity(screen.communityAddressId) },
                        onLeave = { onLeaveCommunity(screen.communityAddressId) },
                        onPostToCommunity = { navigateTo(Screen.CommunityPost(screen.communityAddressId)) },
                        onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                        onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        onBack = { goBack() },
                        onBoost = onBoostNote,
                        onLike = onLikeNote,
                        onZap = onZapNote,
                    )
                }

                is Screen.CommunityPost -> {
                    ComposeNoteScreen(
                        account = account,
                        relayManager = relayManager,
                        localCache = localCache,
                        draftManager = draftManager,
                        replyToNoteId = null,
                        initialDraft = composeDraft,
                        onDraftChanged = { composeDraft = it },
                        onBack = { goBack() },
                        onPublished = {
                            composeDraft = ""
                            goBack()
                        },
                    )
                }

                is Screen.RelayGroups -> {
                    RelayGroupsScreen(
                        account = account,
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        onBack = { goBack() },
                    )
                }

                is Screen.EditNote -> {
                    EditNoteScreen(
                        noteId = screen.noteId,
                        account = account,
                        localCache = localCache,
                        relayManager = relayManager,
                        onBack = { goBack() },
                    )
                }

                is Screen.PeopleLists -> {
                    ListsManagementScreen(
                        account = account,
                        relayManager = relayManager,
                        localCache = localCache,
                        coordinator = coordinator,
                        onBack = { goBack() },
                        onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                    )
                }

                is Screen.LiveActivity -> {
                    val note = localCache.getNoteIfExists(screen.noteId)
                    val activityData = note?.toLiveActivityDisplayData(localCache)
                    if (activityData != null) {
                        LiveActivityDetailScreen(
                            activity = activityData,
                            onBack = { goBack() },
                            onHostClick = { navigateTo(Screen.Profile(it)) },
                        )
                    } else {
                        Column {
                            androidx.compose.material3.TopAppBar(
                                title = { Text("Live Activity") },
                                navigationIcon = {
                                    IconButton(onClick = { goBack() }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                        )
                                    }
                                },
                            )
                            com.vitorpamplona.amethyst.commons.ui.components.EmptyState(
                                title = "Live activity not found",
                            )
                        }
                    }
                }

                is Screen.ClassifiedDetail -> {
                    val note = localCache.getNoteIfExists(screen.noteId)
                    val classifiedData = note?.toClassifiedDisplayData(localCache)
                    if (classifiedData != null) {
                        ClassifiedDetailScreen(
                            classified = classifiedData,
                            onBack = { goBack() },
                            onAuthorClick = { navigateTo(Screen.Profile(it)) },
                        )
                    } else {
                        Column {
                            androidx.compose.material3.TopAppBar(
                                title = { Text("Listing") },
                                navigationIcon = {
                                    IconButton(onClick = { goBack() }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                        )
                                    }
                                },
                            )
                            com.vitorpamplona.amethyst.commons.ui.components.EmptyState(
                                title = "Listing not found",
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Feed content using commons FeedContentState + FeedViewModel ───

@Composable
private fun IosFeedContent(
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    pubKeyHex: String?,
    followedHashtags: Set<String> = emptySet(),
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToArticle: ((String) -> Unit)? = null,
    onNavigateToCommunities: (() -> Unit)? = null,
    onNavigateToLiveActivity: ((String) -> Unit)? = null,
    onNavigateToClassified: ((String) -> Unit)? = null,
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
    onBookmark: ((String) -> Unit)? = null,
    bookmarkedNoteIds: Set<String> = emptySet(),
    onCopyNoteId: ((String) -> Unit)? = null,
    onCopyNoteText: ((String) -> Unit)? = null,
    onCopyAuthorNpub: ((String) -> Unit)? = null,
    onMuteUser: ((String) -> Unit)? = null,
    onReport: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
    mutedUserPubKeys: Set<String> = emptySet(),
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val followedUsers by localCache.followedUsers.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    var feedMode by remember { mutableStateOf(FeedMode.GLOBAL) }

    // Subscribe to contact list
    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isNotEmpty() && pubKeyHex != null) {
            SubscriptionConfig(
                subId = generateSubId("contacts"),
                filters = listOf(FilterBuilders.contactList(pubKeyHex)),
                relays = allRelayUrls,
                onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
            )
        } else {
            null
        }
    }

    // Subscribe to feed events
    rememberSubscription(allRelayUrls, feedMode, followedUsers, followedHashtags, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        when (feedMode) {
            FeedMode.GLOBAL -> {
                SubscriptionConfig(
                    subId = generateSubId("global-feed"),
                    filters = listOf(FilterBuilders.textNotesWithArticlesGlobal(limit = 200)),
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                )
            }

            FeedMode.FOLLOWING -> {
                val follows = followedUsers.toList()
                if (follows.isNotEmpty()) {
                    SubscriptionConfig(
                        subId = generateSubId("following-feed"),
                        filters = listOf(FilterBuilders.textNotesFromAuthors(follows, limit = 200)),
                        relays = allRelayUrls,
                        onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                    )
                } else {
                    null
                }
            }

            FeedMode.HASHTAGS -> {
                val tags = followedHashtags.toList()
                if (tags.isNotEmpty()) {
                    SubscriptionConfig(
                        subId = generateSubId("hashtag-feed"),
                        filters = listOf(FilterBuilders.textNotesByHashtags(tags, limit = 200)),
                        relays = allRelayUrls,
                        onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                    )
                } else {
                    null
                }
            }

            FeedMode.TRENDING -> {
                // Trending uses the global feed subscription + local ranking
                SubscriptionConfig(
                    subId = generateSubId("trending-feed"),
                    filters = listOf(FilterBuilders.textNotesGlobal(limit = 500)),
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                )
            }

            FeedMode.POLLS -> {
                SubscriptionConfig(
                    subId = generateSubId("polls-feed"),
                    filters = listOf(FilterBuilders.polls(limit = 100)),
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                )
            }

            FeedMode.CALENDAR -> {
                SubscriptionConfig(
                    subId = generateSubId("calendar-feed"),
                    filters = listOf(FilterBuilders.calendarEvents(limit = 100)),
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                )
            }

            FeedMode.LIVE -> {
                SubscriptionConfig(
                    subId = generateSubId("live-activities"),
                    filters = listOf(FilterBuilders.liveActivities(limit = 100)),
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                )
            }

            FeedMode.MARKETPLACE -> {
                SubscriptionConfig(
                    subId = generateSubId("classifieds"),
                    filters = listOf(FilterBuilders.classifiedListings(limit = 100)),
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                )
            }
        }
    }

    // Commons FeedViewModel keyed on feedMode + followedHashtags
    val viewModel =
        remember(feedMode, followedHashtags) {
            val filter =
                when (feedMode) {
                    FeedMode.GLOBAL -> {
                        IosGlobalFeedFilter(localCache)
                    }

                    FeedMode.FOLLOWING -> {
                        IosFollowingFeedFilter(localCache) { localCache.followedUsers.value }
                    }

                    FeedMode.HASHTAGS -> {
                        IosFollowedHashtagsFeedFilter(localCache) { followedHashtags }
                    }

                    FeedMode.TRENDING -> {
                        IosTrendingFeedFilter(localCache)
                    }

                    FeedMode.POLLS -> {
                        IosPollsFeedFilter(localCache)
                    }

                    FeedMode.CALENDAR -> {
                        IosCalendarEventsFeedFilter(localCache)
                    }

                    FeedMode.LIVE -> {
                        com.vitorpamplona.amethyst.ios.feeds
                            .IosLiveActivitiesFeedFilter(localCache)
                    }

                    FeedMode.MARKETPLACE -> {
                        com.vitorpamplona.amethyst.ios.feeds
                            .IosClassifiedsFeedFilter(localCache)
                    }
                }
            IosFeedViewModel(filter, localCache)
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    // Fetch metadata for visible note authors
    val missingPubkeys =
        remember(feedState) {
            if (feedState !is FeedState.Loaded) return@remember emptyList<String>()
            viewModel.feedState
                .visibleNotes()
                .mapNotNull { it.author }
                .filter { it.profilePicture() == null }
                .map { it.pubkeyHex }
                .distinct()
                .take(50)
        }

    rememberSubscription(allRelayUrls, missingPubkeys, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || missingPubkeys.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("fetch-metadata"),
            filters = listOf(FilterBuilders.userMetadataMultiple(missingPubkeys)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Fetch zaps for visible notes to display zap amounts
    val visibleNoteIds =
        remember(feedState) {
            if (feedState !is FeedState.Loaded) return@remember emptyList<String>()
            viewModel.feedState
                .visibleNotes()
                .filter { it.zaps.isEmpty() }
                .map { it.idHex }
                .take(30)
        }

    rememberSubscription(allRelayUrls, visibleNoteIds, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || visibleNoteIds.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("fetch-zaps"),
            filters = listOf(FilterBuilders.zapsForEvents(visibleNoteIds, limit = 100)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FeedHeader(
            title =
                when (feedMode) {
                    FeedMode.GLOBAL -> "Global Feed"
                    FeedMode.FOLLOWING -> "Following"
                    FeedMode.HASHTAGS -> "Hashtags"
                    FeedMode.TRENDING -> "Trending"
                    FeedMode.POLLS -> "Polls"
                    FeedMode.CALENDAR -> "Calendar"
                    FeedMode.LIVE -> "Live"
                    FeedMode.MARKETPLACE -> "Marketplace"
                },
            connectedRelayCount = connectedRelays.size,
            onRefresh = { relayManager.connect() },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Feed mode tabs
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = feedMode.ordinal,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FeedMode.entries.forEach { mode ->
                androidx.compose.material3.Tab(
                    selected = feedMode == mode,
                    onClick = { feedMode = mode },
                    text = {
                        Text(
                            when (mode) {
                                FeedMode.GLOBAL -> "Global"
                                FeedMode.FOLLOWING -> "Following"
                                FeedMode.HASHTAGS -> "#Tags"
                                FeedMode.TRENDING -> "🔥 Trending"
                                FeedMode.POLLS -> "🗳️ Polls"
                                FeedMode.CALENDAR -> "📅 Calendar"
                                FeedMode.LIVE -> "🔴 Live"
                                FeedMode.MARKETPLACE -> "🛒 Market"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }

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
                        when (feedMode) {
                            FeedMode.FOLLOWING -> "No notes from followed users"
                            FeedMode.HASHTAGS -> if (followedHashtags.isEmpty()) "No followed hashtags" else "No notes with your hashtags yet"
                            FeedMode.TRENDING -> "No trending notes yet"
                            FeedMode.POLLS -> "No polls found"
                            FeedMode.CALENDAR -> "No calendar events found"
                            FeedMode.LIVE -> "No live activities found"
                            FeedMode.MARKETPLACE -> "No listings found"
                            else -> "No notes found"
                        },
                    description =
                        if (feedMode == FeedMode.HASHTAGS && followedHashtags.isEmpty()) {
                            "Follow hashtags in Settings to see them here"
                        } else {
                            null
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
                    verticalArrangement = Arrangement.spacedBy(feedItemSpacing()),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(loadedState.list, key = { it.idHex }) { note ->
                        val event = note.event ?: return@items
                        if (event.pubKey in mutedUserPubKeys) return@items

                        if (event is LiveActivitiesEvent) {
                            val activityData = note.toLiveActivityDisplayData(localCache)
                            if (activityData != null) {
                                LiveActivityCard(
                                    activity = activityData,
                                    onClick = { onNavigateToLiveActivity?.invoke(event.id) },
                                    onHostClick = onNavigateToProfile,
                                )
                            }
                        } else if (event is ZapPollEvent) {
                            val pollData = note.toPollDisplayData(localCache)
                            if (pollData != null) {
                                PollCard(
                                    poll = pollData,
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                    onVote = null, // TODO: implement zap-based voting
                                )
                            }
                        } else if (event is CalendarTimeSlotEvent || event is CalendarDateSlotEvent) {
                            val calendarData = note.toCalendarEventDisplayData(localCache)
                            if (calendarData != null) {
                                CalendarEventCard(
                                    event = calendarData,
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                    onRsvp = null, // TODO: implement RSVP
                                )
                            }
                        } else if (event is LongTextNoteEvent) {
                            val articleData = note.toArticleDisplayData(localCache)
                            if (articleData != null) {
                                ArticleCard(
                                    article = articleData,
                                    onClick = { onNavigateToArticle?.invoke(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                )
                            }
                        } else if (event is ClassifiedsEvent) {
                            val classifiedData = note.toClassifiedDisplayData(localCache)
                            if (classifiedData != null) {
                                ClassifiedCard(
                                    classified = classifiedData,
                                    onClick = { onNavigateToClassified?.invoke(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                )
                            }
                        } else if (event is WikiNoteEvent) {
                            val wikiData = note.toWikiDisplayData(localCache)
                            if (wikiData != null) {
                                WikiCard(
                                    wiki = wikiData,
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                )
                            }
                        } else if (event is CodeSnippetEvent) {
                            val snippetData = note.toCodeSnippetDisplayData(localCache)
                            if (snippetData != null) {
                                CodeSnippetCard(
                                    snippet = snippetData,
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                )
                            }
                        } else if (event is HighlightEvent) {
                            val highlightData = note.toHighlightDisplayData(localCache)
                            if (highlightData != null) {
                                HighlightCard(
                                    highlight = highlightData,
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                )
                            }
                        } else {
                            val noteDisplayData = note.toNoteDisplayData(localCache)
                            val labelData = event.toLabelDisplayData()
                            Column {
                                NoteCard(
                                    note = noteDisplayData,
                                    localCache = localCache,
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                    onBoost = onBoost,
                                    onLike = onLike,
                                    onZap = onZap,
                                    onBookmark = onBookmark,
                                    isBookmarked = event.id in bookmarkedNoteIds,
                                    onCopyNoteId = onCopyNoteId,
                                    onCopyNoteText = onCopyNoteText,
                                    onCopyAuthorNpub = onCopyAuthorNpub,
                                    onMuteUser = onMuteUser,
                                    onReport = onReport,
                                    onDelete = onDelete,
                                    onEdit = onEdit,
                                )
                                if (labelData != null) {
                                    LabelRow(
                                        labels = labelData,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Profile content using commons feed filter ───

@Composable
private fun IosProfileContent(
    pubKeyHex: String,
    loggedInPubKeyHex: String = "",
    isReadOnly: Boolean = true,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onEditProfile: (() -> Unit)? = null,
    onFollow: ((String) -> Unit)? = null,
    onUnfollow: ((String) -> Unit)? = null,
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
    onBookmark: ((String) -> Unit)? = null,
    bookmarkedNoteIds: Set<String> = emptySet(),
    onCopyNoteId: ((String) -> Unit)? = null,
    onCopyNoteText: ((String) -> Unit)? = null,
    onCopyAuthorNpub: ((String) -> Unit)? = null,
    onMuteUser: ((String) -> Unit)? = null,
    onReport: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }
    val connectedRelays by relayManager.connectedRelays.collectAsState()

    // Subscribe to profile metadata + notes
    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("profile-meta"),
            filters = listOf(FilterBuilders.userMetadata(pubKeyHex)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("profile-notes"),
            filters = listOf(FilterBuilders.textNotesFromAuthors(listOf(pubKeyHex), limit = 100)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    val viewModel =
        remember(pubKeyHex) {
            IosFeedViewModel(IosProfileFeedFilter(pubKeyHex, localCache), localCache)
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()
    val user = localCache.getUserIfExists(pubKeyHex)
    val displayName = user?.toBestDisplayName() ?: pubKeyHex.take(16) + "..."
    val about =
        user
            ?.metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.about
    val npubShort = pubKeyHex.take(12) + "..." + pubKeyHex.takeLast(8)
    val userStatusText = localCache.getUserStatusText(pubKeyHex)
    val userStatusType = localCache.getUserStatusType(pubKeyHex)

    // Subscribe to user status (NIP-38)
    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("user-status"),
            filters = listOf(FilterBuilders.userStatus(pubKeyHex)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Subscribe to profile badges (NIP-58)
    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("profile-badges"),
            filters = listOf(FilterBuilders.profileBadges(pubKeyHex)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Resolve badge definitions from the profile badges event
    val profileBadgesNote =
        remember(pubKeyHex) {
            val address = ProfileBadgesEvent.createAddress(pubKeyHex)
            localCache.getOrCreateAddressableNote(address)
        }
    val profileBadgesEvent = profileBadgesNote.event as? ProfileBadgesEvent
    val badgeDefinitionAddresses =
        remember(profileBadgesEvent) {
            profileBadgesEvent?.badgeAwardDefinitions() ?: emptyList()
        }

    // Subscribe to badge definitions referenced by the profile
    rememberSubscription(allRelayUrls, badgeDefinitionAddresses, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || badgeDefinitionAddresses.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("badge-defs"),
            filters = listOf(FilterBuilders.badgeDefinitionsGlobal(limit = 50)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Build badge display data
    val badgeDisplayList =
        remember(profileBadgesEvent) {
            if (profileBadgesEvent == null) return@remember emptyList<BadgeDisplayData>()
            val badges = profileBadgesEvent.acceptedBadges()
            badges.mapNotNull { accepted ->
                val defATag = accepted.badgeDefinition
                val defAddressId = defATag.toTag()
                val defAddress =
                    com.vitorpamplona.quartz.nip01Core.core.Address
                        .parse(defAddressId)
                val defEvent =
                    if (defAddress != null) {
                        localCache.getOrCreateAddressableNote(defAddress).event as? BadgeDefinitionEvent
                    } else {
                        null
                    }
                val issuerPubKey = defATag.pubKeyHex
                val issuerUser = localCache.getUserIfExists(issuerPubKey)
                BadgeDisplayData(
                    definitionAddressId = defAddressId,
                    awardEventId = accepted.badgeAward.eventId,
                    name = defEvent?.name() ?: defATag.dTag,
                    description = defEvent?.description(),
                    imageUrl = defEvent?.image(),
                    thumbUrl = defEvent?.thumb(),
                    issuerPubKeyHex = issuerPubKey,
                    issuerDisplayName = issuerUser?.toBestDisplayName() ?: issuerPubKey.take(16) + "...",
                    issuerProfilePicture = issuerUser?.profilePicture(),
                )
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        // Enhanced profile header
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            // Avatar with status dot overlay
            Box {
                UserAvatar(
                    userHex = pubKeyHex,
                    pictureUrl = user?.profilePicture(),
                    size = 80.dp,
                    contentDescription = "Profile",
                )
                UserStatusDot(
                    hasStatus = userStatusText != null,
                    isMusic = userStatusType == "music",
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                npubShort,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!about.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                )
            }

            // User status badge (NIP-38)
            if (userStatusText != null) {
                Spacer(Modifier.height(6.dp))
                UserStatusBadge(
                    statusText = userStatusText,
                    statusType = userStatusType,
                )
            }

            val isOwnProfile = pubKeyHex == loggedInPubKeyHex
            val followedUsers by localCache.followedUsers.collectAsState()
            val isFollowing = remember(followedUsers, pubKeyHex) { pubKeyHex in followedUsers }

            if (isOwnProfile && onNavigateToSettings != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (onEditProfile != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onEditProfile,
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Edit Profile")
                        }
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onNavigateToSettings,
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Settings")
                    }
                }
            } else if (!isOwnProfile && !isReadOnly) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isFollowing) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { onUnfollow?.invoke(pubKeyHex) },
                        ) {
                            Text("Unfollow")
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = { onFollow?.invoke(pubKeyHex) },
                        ) {
                            Text("Follow")
                        }
                    }
                }
            }
        }

        // Badge gallery (NIP-58)
        if (badgeDisplayList.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            BadgeGallery(
                badges = badgeDisplayList,
                onBadgeClick = null,
            )
            Spacer(Modifier.height(12.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Notes feed
        when (val state = feedState) {
            is FeedState.Loading -> {
                LoadingState("Loading profile...")
            }

            is FeedState.Empty -> {
                EmptyState(title = "No notes yet")
            }

            is FeedState.FeedError -> {
                EmptyState(title = "Error", description = state.errorMessage)
            }

            is FeedState.Loaded -> {
                val loadedState by state.feed.collectAsState()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(feedItemSpacing()),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(loadedState.list, key = { it.idHex }) { note ->
                        val event = note.event ?: return@items
                        NoteCard(
                            note = note.toNoteDisplayData(localCache),
                            localCache = localCache,
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                            onBoost = onBoost,
                            onLike = onLike,
                            onZap = onZap,
                            onBookmark = onBookmark,
                            isBookmarked = event.id in bookmarkedNoteIds,
                            onCopyNoteId = onCopyNoteId,
                            onCopyNoteText = onCopyNoteText,
                            onCopyAuthorNpub = onCopyAuthorNpub,
                            onMuteUser = onMuteUser,
                            onReport = onReport,
                            onDelete = onDelete,
                            onEdit = onEdit,
                        )
                    }
                }
            }
        }
    }
}

// ─── Thread content using commons feed filter ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosThreadContent(
    noteId: String,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onReply: ((String) -> Unit)? = null,
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
    onBookmark: ((String) -> Unit)? = null,
    bookmarkedNoteIds: Set<String> = emptySet(),
    onCopyNoteId: ((String) -> Unit)? = null,
    onCopyNoteText: ((String) -> Unit)? = null,
    onCopyAuthorNpub: ((String) -> Unit)? = null,
    onMuteUser: ((String) -> Unit)? = null,
    onReport: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    // Fetch the thread note + replies
    rememberSubscription(allRelayUrls, noteId, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("thread"),
            filters = listOf(FilterBuilders.byIds(listOf(noteId))),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    val viewModel =
        remember(noteId) {
            IosFeedViewModel(IosThreadFilter(noteId, localCache), localCache)
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Thread") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
        )

        when (val state = feedState) {
            is FeedState.Loading -> {
                LoadingState("Loading thread...")
            }

            is FeedState.Empty -> {
                EmptyState(title = "Note not found")
            }

            is FeedState.FeedError -> {
                EmptyState(title = "Error", description = state.errorMessage)
            }

            is FeedState.Loaded -> {
                val loadedState by state.feed.collectAsState()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(feedItemSpacing()),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(loadedState.list, key = { it.idHex }) { note ->
                        val event = note.event ?: return@items
                        NoteCard(
                            note = note.toNoteDisplayData(localCache),
                            localCache = localCache,
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                            onReply = onReply,
                            onBoost = onBoost,
                            onLike = onLike,
                            onZap = onZap,
                            onBookmark = onBookmark,
                            isBookmarked = event.id in bookmarkedNoteIds,
                            onCopyNoteId = onCopyNoteId,
                            onCopyNoteText = onCopyNoteText,
                            onCopyAuthorNpub = onCopyAuthorNpub,
                            onMuteUser = onMuteUser,
                            onReport = onReport,
                            onDelete = onDelete,
                            onEdit = onEdit,
                        )
                    }
                }
            }
        }
    }
}
