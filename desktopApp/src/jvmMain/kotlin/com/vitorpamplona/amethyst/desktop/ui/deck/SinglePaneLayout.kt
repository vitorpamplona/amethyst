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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.amethyst.desktop.DesktopScreen
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.network.Nip11Fetcher
import com.vitorpamplona.amethyst.desktop.service.highlights.DesktopHighlightStore
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm
import kotlinx.coroutines.CoroutineScope

@Composable
fun SinglePaneLayout(
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    iAccount: com.vitorpamplona.amethyst.desktop.model.DesktopIAccount,
    nwcConnection: Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    highlightStore: DesktopHighlightStore,
    draftStore: com.vitorpamplona.amethyst.desktop.service.drafts.DesktopDraftStore,
    nip11Fetcher: Nip11Fetcher,
    appScope: CoroutineScope,
    singlePaneState: SinglePaneState,
    pinnedNavBarState: PinnedNavBarState,
    onOpenAppDrawer: () -> Unit,
    onOpenFeedsDrawer: () -> Unit = onOpenAppDrawer,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onShowImportFollowListDialog: () -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit,
    signerConnectionState: SignerConnectionState,
    lastPingTimeSec: Long?,
    lastRelayEventAt: Long? = null,
    modifier: Modifier = Modifier,
) {
    val currentColumnType by singlePaneState.currentScreen.collectAsState()
    val navState = remember { ColumnNavigationState() }
    val currentOverlay = navState.current

    // Sidebar is now provided by Main.kt (shared MainSidebar for both layout modes).
    // SinglePaneLayout only renders the content pane.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            // Content extends to the window edges; individual screens add their
            // own internal padding where appropriate (Messages uses full-bleed
            // panes to match native two-column chat apps).
            Box(modifier = Modifier.fillMaxSize()) {
                // Always keep RootContent composed so state (e.g. search results) survives navigation
                RootContent(
                    columnType = currentColumnType,
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
                    onShowComposeDialog = onShowComposeDialog,
                    onShowReplyDialog = onShowReplyDialog,
                    onZapFeedback = onZapFeedback,
                    onNavigateToProfile = { navState.push(DesktopScreen.UserProfile(it)) },
                    onNavigateToThread = { navState.push(DesktopScreen.Thread(it)) },
                    onNavigateToArticle = { navState.push(DesktopScreen.Article(it)) },
                    onNavigateToEditor = { navState.push(DesktopScreen.Editor(it)) },
                    onNavigateToRelays = { singlePaneState.navigate(DeckColumnType.Relays) },
                    onOpenFeedsDrawer = onOpenFeedsDrawer,
                )
                AnimatedContent(
                    targetState = currentOverlay,
                    transitionSpec = {
                        val duration = 200
                        if (navState.navigatingForward) {
                            (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)))
                                .togetherWith(slideOutHorizontally(tween(duration)) { -it } + fadeOut(tween(duration)))
                        } else {
                            (slideInHorizontally(tween(duration)) { -it } + fadeIn(tween(duration)))
                                .togetherWith(slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration)))
                        }
                    },
                    label = "SinglePaneNavAnimation",
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
                                onBack = { navState.pop() },
                            )
                        }
                    }
                }
            }
        }
    }
}
