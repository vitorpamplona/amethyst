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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.DesktopScreen
import com.vitorpamplona.amethyst.desktop.RelaySettingsScreen
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.chess.ChessScreen
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinPreferences
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import com.vitorpamplona.amethyst.desktop.ui.BookmarksScreen
import com.vitorpamplona.amethyst.desktop.ui.FeedScreen
import com.vitorpamplona.amethyst.desktop.ui.NotificationsScreen
import com.vitorpamplona.amethyst.desktop.ui.ReadsScreen
import com.vitorpamplona.amethyst.desktop.ui.SearchScreen
import com.vitorpamplona.amethyst.desktop.ui.ThreadScreen
import com.vitorpamplona.amethyst.desktop.ui.UserProfileScreen
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.chats.DesktopMessagesScreen
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ColumnNavigationState {
    private val _stack = MutableStateFlow<List<DesktopScreen>>(emptyList())
    val stack: kotlinx.coroutines.flow.StateFlow<List<DesktopScreen>> = _stack.asStateFlow()

    fun push(screen: DesktopScreen) {
        _stack.value = _stack.value + screen
    }

    fun pop(): Boolean {
        if (_stack.value.isEmpty()) return false
        _stack.value = _stack.value.dropLast(1)
        return true
    }

    fun clear() {
        _stack.value = emptyList()
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
    appScope: CoroutineScope,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navState = remember(column.id) { ColumnNavigationState() }
    val navStack by navState.stack.collectAsState()
    val currentOverlay = navStack.lastOrNull()

    Column(
        modifier =
            modifier
                .width(column.width.dp)
                .fillMaxHeight(),
    ) {
        ColumnHeader(
            column = column,
            canClose = canClose,
            hasBackStack = navStack.isNotEmpty(),
            onBack = { navState.pop() },
            onClose = onClose,
            onDoubleClick = onDoubleClickHeader,
        )

        HorizontalDivider()

        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            // Always keep RootContent composed so state (e.g. search results) survives navigation
            RootContent(
                columnType = column.type,
                relayManager = relayManager,
                localCache = localCache,
                accountManager = accountManager,
                account = account,
                iAccount = iAccount,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                appScope = appScope,
                compactMode = true,
                onShowComposeDialog = onShowComposeDialog,
                onShowReplyDialog = onShowReplyDialog,
                onZapFeedback = onZapFeedback,
                onNavigateToProfile = { navState.push(DesktopScreen.UserProfile(it)) },
                onNavigateToThread = { navState.push(DesktopScreen.Thread(it)) },
            )
            if (currentOverlay != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OverlayContent(
                        screen = currentOverlay,
                        relayManager = relayManager,
                        localCache = localCache,
                        account = account,
                        nwcConnection = nwcConnection,
                        subscriptionsCoordinator = subscriptionsCoordinator,
                        onShowComposeDialog = onShowComposeDialog,
                        onShowReplyDialog = onShowReplyDialog,
                        onZapFeedback = onZapFeedback,
                        onNavigateToProfile = { navState.push(DesktopScreen.UserProfile(it)) },
                        onNavigateToThread = { navState.push(DesktopScreen.Thread(it)) },
                        onBack = { navState.pop() },
                    )
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
    appScope: CoroutineScope,
    compactMode: Boolean = false,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    when (columnType) {
        DeckColumnType.HomeFeed -> {
            FeedScreen(
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                initialFeedMode = FeedMode.FOLLOWING,
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        DeckColumnType.Notifications -> {
            NotificationsScreen(relayManager, account, subscriptionsCoordinator)
        }

        DeckColumnType.Messages -> {
            DesktopMessagesScreen(
                account = iAccount,
                cacheProvider = localCache,
                relayManager = relayManager,
                localCache = localCache,
                compactMode = compactMode,
                onNavigateToProfile = onNavigateToProfile,
            )
        }

        DeckColumnType.Search -> {
            SearchScreen(
                localCache = localCache,
                relayManager = relayManager,
                subscriptionsCoordinator = subscriptionsCoordinator,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
            )
        }

        DeckColumnType.Reads -> {
            ReadsScreen(
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToArticle = onNavigateToThread,
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
                nwcConnection = nwcConnection,
                subscriptionsCoordinator = subscriptionsCoordinator,
                initialFeedMode = FeedMode.GLOBAL,
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
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
            RelaySettingsScreen(
                relayManager = relayManager,
                account = account,
                accountManager = accountManager,
                namecoinPreferences = LocalNamecoinPreferences.current,
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

        is DeckColumnType.Hashtag -> {
            SearchScreen(
                localCache = localCache,
                relayManager = relayManager,
                subscriptionsCoordinator = subscriptionsCoordinator,
                initialQuery = "#${columnType.tag}",
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
            )
        }
    }
}

@Composable
internal fun OverlayContent(
    screen: DesktopScreen,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn,
    nwcConnection: Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
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
                onCompose = onShowComposeDialog,
                onNavigateToProfile = onNavigateToProfile,
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

        else -> {
            androidx.compose.material3.Text(
                "Unsupported screen type",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
