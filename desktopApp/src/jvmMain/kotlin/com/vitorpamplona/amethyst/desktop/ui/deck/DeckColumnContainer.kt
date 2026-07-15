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
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.DesktopScreen
import com.vitorpamplona.amethyst.desktop.RelaySettingsScreen
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.chess.ChessScreen
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.network.Nip11Fetcher
import com.vitorpamplona.amethyst.desktop.service.drafts.DesktopDraftStore
import com.vitorpamplona.amethyst.desktop.service.highlights.DesktopHighlightStore
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinPreferences
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import com.vitorpamplona.amethyst.desktop.ui.ArticleEditorScreen
import com.vitorpamplona.amethyst.desktop.ui.ArticleReaderScreen
import com.vitorpamplona.amethyst.desktop.ui.BookmarksScreen
import com.vitorpamplona.amethyst.desktop.ui.CustomFeedScreen
import com.vitorpamplona.amethyst.desktop.ui.FeedScreen
import com.vitorpamplona.amethyst.desktop.ui.NotificationsScreen
import com.vitorpamplona.amethyst.desktop.ui.ReadsScreen
import com.vitorpamplona.amethyst.desktop.ui.SearchScreen
import com.vitorpamplona.amethyst.desktop.ui.ThreadScreen
import com.vitorpamplona.amethyst.desktop.ui.UserProfileScreen
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.chats.DesktopMessagesScreen
import com.vitorpamplona.amethyst.desktop.ui.relay.RelayDashboardScreen
import com.vitorpamplona.amethyst.desktop.ui.scheduledposts.DraftsAndScheduledScreen
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ColumnNavigationState {
    private val _stack = mutableStateListOf<DesktopScreen>()
    val stack: List<DesktopScreen> get() = _stack
    val current: DesktopScreen? get() = _stack.lastOrNull()
    val hasBackStack: Boolean get() = _stack.isNotEmpty()

    var navigatingForward by mutableStateOf(true)
        private set

    fun pushWithCap(
        screen: DesktopScreen,
        maxDepth: Int = 2,
    ) {
        navigatingForward = true
        if (_stack.size >= maxDepth) _stack.removeFirst()
        _stack.add(screen)
    }

    fun push(screen: DesktopScreen) {
        pushWithCap(screen)
    }

    fun pop(): Boolean {
        if (_stack.isEmpty()) return false
        navigatingForward = false
        _stack.removeLast()
        return true
    }

    fun clear() {
        _stack.clear()
    }
}

@Composable
fun DeckColumnContainer(
    column: DeckColumn,
    canClose: Boolean,
    onClose: () -> Unit,
    onDoubleClickHeader: () -> Unit = {},
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    iAccount: DesktopIAccount,
    nwcConnection: Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    highlightStore: DesktopHighlightStore,
    draftStore: DesktopDraftStore,
    nip11Fetcher: Nip11Fetcher,
    appScope: CoroutineScope,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onEditInComposer: (content: String, draftDTag: String?, scheduledForSec: Long?) -> Unit = { _, _, _ -> },
    onZapFeedback: (ZapFeedback) -> Unit,
    onNavigateToRelays: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
    clearOverlaySignal: SharedFlow<String>? = null,
    modifier: Modifier = Modifier,
) {
    val navState = remember(column.id) { ColumnNavigationState() }
    val currentOverlay = navState.current
    val focusRequester = remember { FocusRequester() }

    // Sidebar-driven column focus drains this column's overlay stack so the
    // tapped destination isn't hidden behind a leftover detail screen. Filter
    // by column.id so unrelated columns' overlays stay intact.
    if (clearOverlaySignal != null) {
        LaunchedEffect(column.id, clearOverlaySignal) {
            clearOverlaySignal.collect { id ->
                if (id == column.id) navState.clear()
            }
        }
    }

    // Request focus once when the column is created. Re-keying on
    // `currentOverlay` would steal focus from sibling columns whenever any
    // deck column mutates its overlay state (e.g. typing in column A's reply
    // box loses focus when column B opens a profile). Esc continues to work
    // because the column still owns focus when the user hits the key.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            modifier
                .width(column.width.dp)
                .fillMaxHeight()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyUp && navState.hasBackStack) {
                        navState.pop()
                        true
                    } else {
                        false
                    }
                },
    ) {
        ColumnHeader(
            column = column,
            canClose = canClose,
            hasBackStack = navState.hasBackStack,
            onBack = { navState.pop() },
            onClose = onClose,
            onDoubleClick = onDoubleClickHeader,
        )

        HorizontalDivider()

        // Offline banner — shows when no remote relays connected
        val connectedRelays by relayManager.connectedRelays.collectAsState()
        val localRelay = LocalLocalRelayStore.current
        val hasLocalData =
            if (localRelay != null) {
                val count by localRelay.eventCount.collectAsState()
                count > 0
            } else {
                false
            }
        com.vitorpamplona.amethyst.desktop.ui.components.OfflineBanner(
            connectedRelayCount = connectedRelays.size,
            hasLocalData = hasLocalData,
        )

        // Unhealthy relays banner — flags relays unresponsive >7d, opens review popup
        com.vitorpamplona.amethyst.desktop.ui.relay.health.UnhealthyRelayBannerHost(
            onOpenDashboard = onNavigateToRelays,
        )

        // Content runs edge-to-edge; each screen adds its own header padding
        Box(modifier = Modifier.fillMaxSize()) {
            // Always keep RootContent composed so state survives navigation
            RootContent(
                columnType = column.type,
                relayManager = relayManager,
                localCache = localCache,
                accountManager = accountManager,
                account = account,
                iAccount = iAccount,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                highlightStore = highlightStore,
                draftStore = draftStore,
                nip11Fetcher = nip11Fetcher,
                appScope = appScope,
                compactMode = true,
                onShowComposeDialog = onShowComposeDialog,
                onShowReplyDialog = onShowReplyDialog,
                onEditInComposer = onEditInComposer,
                onZapFeedback = onZapFeedback,
                onNavigateToProfile = { navState.push(DesktopScreen.UserProfile(it)) },
                onNavigateToThread = { navState.push(DesktopScreen.Thread(it)) },
                onNavigateToArticle = { navState.push(DesktopScreen.Article(it)) },
                onNavigateToEditor = { navState.push(DesktopScreen.Editor(it)) },
                onNavigateToRelays = onNavigateToRelays,
                onNavigateToPack = { navState.push(DesktopScreen.FollowPackDetail(it)) },
                onNavigateToPackBrowseAll = { navState.push(DesktopScreen.FollowPackBrowseAll) },
                onOpenNotificationSettings = { navState.push(DesktopScreen.NotificationSettings) },
                onOpenMessages = onOpenMessages,
            )

            // Overlay with slide animation
            AnimatedContent(
                targetState = currentOverlay,
                transitionSpec = {
                    val duration = 200
                    if (navState.navigatingForward) {
                        (
                            slideInHorizontally(
                                tween(duration),
                            ) { it } +
                                fadeIn(
                                    androidx.compose.animation.core
                                        .tween(duration),
                                )
                        ).togetherWith(
                            slideOutHorizontally(
                                tween(duration),
                            ) { -it } +
                                fadeOut(
                                    androidx.compose.animation.core
                                        .tween(duration),
                                ),
                        )
                    } else {
                        (
                            slideInHorizontally(
                                tween(duration),
                            ) { -it } +
                                fadeIn(
                                    androidx.compose.animation.core
                                        .tween(duration),
                                )
                        ).togetherWith(
                            slideOutHorizontally(
                                tween(duration),
                            ) { it } +
                                fadeOut(
                                    androidx.compose.animation.core
                                        .tween(duration),
                                ),
                        )
                    }
                },
                label = "ColumnNavAnimation",
            ) { overlayScreen ->
                if (overlayScreen != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        OverlayContent(
                            screen = overlayScreen,
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            iAccount = iAccount,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            highlightStore = highlightStore,
                            draftStore = draftStore,
                            onShowComposeDialog = onShowComposeDialog,
                            onShowReplyDialog = onShowReplyDialog,
                            onZapFeedback = onZapFeedback,
                            onNavigateToProfile = { navState.push(DesktopScreen.UserProfile(it)) },
                            onNavigateToThread = { navState.push(DesktopScreen.Thread(it)) },
                            onNavigateToArticle = { navState.push(DesktopScreen.Article(it)) },
                            onNavigateToPack = { navState.push(DesktopScreen.FollowPackDetail(it)) },
                            onBack = { navState.pop() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RootContent(
    columnType: DeckColumnType,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    iAccount: DesktopIAccount,
    nwcConnection: Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    highlightStore: DesktopHighlightStore? = null,
    draftStore: DesktopDraftStore? = null,
    nip11Fetcher: Nip11Fetcher,
    appScope: CoroutineScope,
    compactMode: Boolean = false,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onEditInComposer: (content: String, draftDTag: String?, scheduledForSec: Long?) -> Unit = { _, _, _ -> },
    onZapFeedback: (ZapFeedback) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToArticle: (String) -> Unit = {},
    onNavigateToEditor: (String?) -> Unit = {},
    onNavigateToRelays: () -> Unit = {},
    onOpenFeedsDrawer: () -> Unit = {},
    onNavigateToPack: (String) -> Unit = {},
    onNavigateToPackBrowseAll: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    when (columnType) {
        DeckColumnType.HomeFeed -> {
            // Don't hardcode initialFeedMode — let FeedScreen pick the first
            // pinned feed (Following/Global/Custom) as the default tab.
            FeedScreen(
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                iAccount = iAccount,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
                onNavigateToRelays = onNavigateToRelays,
                onOpenFeedsDrawer = onOpenFeedsDrawer,
            )
        }

        DeckColumnType.Notifications -> {
            NotificationsScreen(
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onOpenSettings = onOpenNotificationSettings,
                onNavigateToThread = onNavigateToThread,
                onNavigateToProfile = onNavigateToProfile,
                onOpenMessages = onOpenMessages,
            )
        }

        DeckColumnType.NotificationSettings -> {
            com.vitorpamplona.amethyst.desktop.ui.settings
                .NotificationSettingsScreen()
        }

        DeckColumnType.Messages -> {
            com.vitorpamplona.amethyst.desktop.security.DesktopMessagesLockGate(
                onOpenSettings = onNavigateToRelays,
            ) {
                DesktopMessagesScreen(
                    account = iAccount,
                    cacheProvider = localCache,
                    relayManager = relayManager,
                    localCache = localCache,
                    compactMode = compactMode,
                    onNavigateToProfile = onNavigateToProfile,
                )
            }
        }

        DeckColumnType.Search -> {
            SearchScreen(
                localCache = localCache,
                relayManager = relayManager,
                subscriptionsCoordinator = subscriptionsCoordinator,
                account = account,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
            )
        }

        DeckColumnType.Reads -> {
            ReadsScreen(
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToArticle = onNavigateToArticle,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        DeckColumnType.Bookmarks -> {
            BookmarksScreen(
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        DeckColumnType.GlobalFeed -> {
            FeedScreen(
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                iAccount = iAccount,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                initialFeedMode = FeedMode.GLOBAL,
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
                onNavigateToRelays = onNavigateToRelays,
            )
        }

        DeckColumnType.MyProfile -> {
            UserProfileScreen(
                pubKeyHex = account.pubKeyHex,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onBack = {},
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onNavigateToArticle = onNavigateToArticle,
                onZapFeedback = onZapFeedback,
            )
        }

        DeckColumnType.Chess -> {
            ChessScreen(
                relayManager = relayManager,
                account = account,
                onBack = {},
                compactMode = true,
            )
        }

        DeckColumnType.Settings -> {
            val torState = com.vitorpamplona.amethyst.desktop.ui.tor.LocalTorState.current
            RelaySettingsScreen(
                relayManager = relayManager,
                account = account,
                accountManager = accountManager,
                torStatus = torState.status,
                torSettings = torState.settings,
                onTorSettingsChanged = torState.onSettingsChanged,
                namecoinPreferences = LocalNamecoinPreferences.current,
                blossomServers = iAccount.blossomServerList.flow,
                onBlossomServersChanged = { servers ->
                    // Publish a kind-10063 event so the list syncs to every
                    // Amethyst client, then consume it locally so state updates
                    // immediately (mirrors the NIP-65 save flow above). Read-only
                    // accounts can't sign, so this is a no-op there.
                    if (iAccount.isWriteable()) {
                        appScope.launch {
                            val event = iAccount.blossomServerList.saveBlossomServersList(servers)
                            relayManager.broadcastToAll(event)
                            localCache.justConsumeMyOwnEvent(event)
                        }
                    }
                },
            )
        }

        DeckColumnType.Wallet -> {
            com.vitorpamplona.amethyst.desktop.security.DesktopWalletLockGate(
                onOpenSettings = onNavigateToRelays,
            ) {
                com.vitorpamplona.amethyst.desktop.ui.wallet.WalletColumnScreen(
                    account = account,
                    accountManager = accountManager,
                    relayManager = relayManager,
                    localCache = localCache,
                    nwcConnection = nwcConnection,
                    appScope = appScope,
                    onZapFeedback = onZapFeedback,
                )
            }
        }

        DeckColumnType.Relays -> {
            val accountRelays = com.vitorpamplona.amethyst.desktop.ui.relay.LocalAccountRelays.current
            RelayDashboardScreen(
                relayManager = relayManager,
                nip11Fetcher = nip11Fetcher,
                nip65State = iAccount.nip65RelayList,
                accountRelays = accountRelays ?: return,
                signer = iAccount.signer,
                onPublish = { event -> relayManager.broadcastToAll(event) },
            )
        }

        is DeckColumnType.Profile -> {
            UserProfileScreen(
                pubKeyHex = columnType.pubKeyHex,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onBack = {},
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        is DeckColumnType.Thread -> {
            ThreadScreen(
                noteId = columnType.noteId,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onBack = {},
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
                onReply = onShowReplyDialog,
            )
        }

        is DeckColumnType.Article -> {
            ArticleReaderScreen(
                addressTag = columnType.addressTag,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                subscriptionsCoordinator = subscriptionsCoordinator,
                highlightStore = highlightStore,
                onBack = {},
                onNavigateToProfile = onNavigateToProfile,
            )
        }

        is DeckColumnType.Editor -> {
            ArticleEditorScreen(
                draftSlug = columnType.draftSlug,
                draftStore = draftStore ?: remember { DesktopDraftStore(scope) },
                account = account,
                relayManager = relayManager,
                onBack = {},
                onPublished = {},
            )
        }

        DeckColumnType.Drafts -> {
            DraftsAndScheduledScreen(
                draftStore = draftStore ?: remember { DesktopDraftStore(scope) },
                accountPubkeyHex = account.pubKeyHex,
                relayManager = relayManager,
                account = account,
                onOpenEditor = { slug -> onNavigateToEditor(slug) },
                onEditInComposer = onEditInComposer,
            )
        }

        DeckColumnType.MyHighlights -> {
            com.vitorpamplona.amethyst.desktop.ui.highlights.MyHighlightsScreen(
                highlightStore = highlightStore ?: remember { DesktopHighlightStore(scope) },
                onNavigateToArticle = onNavigateToArticle,
            )
        }

        is DeckColumnType.Hashtag -> {
            SearchScreen(
                localCache = localCache,
                relayManager = relayManager,
                subscriptionsCoordinator = subscriptionsCoordinator,
                account = account,
                initialQuery = "#${columnType.tag}",
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
            )
        }

        is DeckColumnType.CustomFeed -> {
            CustomFeedScreen(
                feedId = columnType.feedId,
                relayManager = relayManager,
                localCache = localCache,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        DeckColumnType.Discover -> {
            val followPacks = LocalFollowPacksState.current
            if (followPacks != null) {
                com.vitorpamplona.amethyst.desktop.followpacks.ui
                    .DiscoverScreen(
                        state = followPacks,
                        cache = localCache,
                        iAccount = iAccount,
                        relayManager = relayManager,
                        onOpenPack = { aTag -> onNavigateToPack(aTag) },
                        onOpenBrowseAll = { onNavigateToPackBrowseAll() },
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToThread = onNavigateToThread,
                        onZapFeedback = onZapFeedback,
                    )
            }
        }

        DeckColumnType.FollowPacks -> {
            val followPacks = LocalFollowPacksState.current
            if (followPacks != null) {
                com.vitorpamplona.amethyst.desktop.followpacks.ui
                    .FollowPackBrowseAllScreen(
                        state = followPacks,
                        cache = localCache,
                        onOpenPack = { aTag -> onNavigateToPack(aTag) },
                        onBack = null,
                    )
            }
        }
    }
}

@Composable
internal fun OverlayContent(
    screen: DesktopScreen,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn,
    iAccount: DesktopIAccount? = null,
    nwcConnection: Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    highlightStore: DesktopHighlightStore? = null,
    draftStore: DesktopDraftStore? = null,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToArticle: (String) -> Unit = {},
    onNavigateToPack: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    when (screen) {
        is DesktopScreen.UserProfile -> {
            UserProfileScreen(
                pubKeyHex = screen.pubKeyHex,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onBack = onBack,
                canGoBack = true,
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onNavigateToArticle = onNavigateToArticle,
                onZapFeedback = onZapFeedback,
            )
        }

        is DesktopScreen.Thread -> {
            ThreadScreen(
                noteId = screen.noteId,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onBack = onBack,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
                onReply = onShowReplyDialog,
            )
        }

        is DesktopScreen.Article -> {
            ArticleReaderScreen(
                addressTag = screen.addressTag,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                subscriptionsCoordinator = subscriptionsCoordinator,
                highlightStore = highlightStore,
                onBack = onBack,
                onNavigateToProfile = onNavigateToProfile,
            )
        }

        is DesktopScreen.Editor -> {
            val overlayScope = androidx.compose.runtime.rememberCoroutineScope()
            ArticleEditorScreen(
                draftSlug = screen.draftSlug,
                draftStore = draftStore ?: remember { DesktopDraftStore(overlayScope) },
                account = account,
                relayManager = relayManager,
                onBack = onBack,
                onPublished = onBack,
            )
        }

        is DesktopScreen.FollowPackDetail -> {
            val followPacks = LocalFollowPacksState.current
            if (followPacks != null && iAccount != null) {
                com.vitorpamplona.amethyst.desktop.followpacks.ui
                    .FollowPackDetailScreen(
                        addressTag = screen.addressTag,
                        state = followPacks,
                        cache = localCache,
                        iAccount = iAccount,
                        relayManager = relayManager,
                        onBack = onBack,
                        onNavigateToProfile = onNavigateToProfile,
                    )
            }
        }

        is DesktopScreen.FollowPackBrowseAll -> {
            val followPacks = LocalFollowPacksState.current
            if (followPacks != null) {
                com.vitorpamplona.amethyst.desktop.followpacks.ui
                    .FollowPackBrowseAllScreen(
                        state = followPacks,
                        cache = localCache,
                        onOpenPack = { aTag -> onNavigateToPack(aTag) },
                        onBack = onBack,
                    )
            }
        }

        is DesktopScreen.NotificationSettings -> {
            com.vitorpamplona.amethyst.desktop.ui.settings.NotificationSettingsScreen(
                onBack = onBack,
            )
        }

        else -> {
            androidx.compose.material3.Text(
                "Unsupported screen type",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
