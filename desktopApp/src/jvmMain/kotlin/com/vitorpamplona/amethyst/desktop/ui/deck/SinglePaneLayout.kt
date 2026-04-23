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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.BunkerHeartbeatIndicator
import com.vitorpamplona.amethyst.desktop.DesktopScreen
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.network.Nip11Fetcher
import com.vitorpamplona.amethyst.desktop.service.highlights.DesktopHighlightStore
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.components.RelayHealthIndicator
import com.vitorpamplona.amethyst.desktop.ui.media.LocalIsImmersiveFullscreen
import com.vitorpamplona.amethyst.desktop.ui.tor.LocalTorState
import com.vitorpamplona.amethyst.desktop.ui.tor.TorStatusIndicator
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
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    signerConnectionState: SignerConnectionState,
    lastPingTimeSec: Long?,
    lastRelayEventAt: Long? = null,
    modifier: Modifier = Modifier,
) {
    val currentColumnType by singlePaneState.currentScreen.collectAsState()
    val navState = remember { ColumnNavigationState() }
    val navStack by navState.stack.collectAsState()
    val currentOverlay = navStack.lastOrNull()

    val isImmersive by LocalIsImmersiveFullscreen.current

    Row(modifier = modifier.fillMaxSize()) {
        if (!isImmersive) {
            NavigationRail(
                modifier = Modifier.width(80.dp).fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                val pinnedScreens by pinnedNavBarState.pinnedScreens.collectAsState()
                pinnedScreens.forEach { screenType ->
                    NavigationRailItem(
                        selected = currentColumnType == screenType && navStack.isEmpty(),
                        onClick = {
                            singlePaneState.navigate(screenType)
                            navState.clear()
                        },
                        icon = {
                            Icon(
                                screenType.icon(),
                                contentDescription = screenType.title(),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = {
                            Text(
                                screenType.title(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }

                NavigationRailItem(
                    selected = false,
                    onClick = onOpenAppDrawer,
                    icon = {
                        Icon(
                            MaterialSymbols.Apps,
                            contentDescription = "App Drawer",
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            "More",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                )

                Spacer(Modifier.weight(1f))

                // Relay health — shows elapsed time since last event (hidden when <30s)
                RelayHealthIndicator(
                    lastEventReceivedAt = lastRelayEventAt,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                BunkerHeartbeatIndicator(
                    signerConnectionState = signerConnectionState,
                    lastPingTimeSec = lastPingTimeSec,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                // Tor status — always last so it's never pushed off screen
                val torState = LocalTorState.current
                TorStatusIndicator(
                    status = torState.status,
                    onClick = {
                        singlePaneState.navigate(DeckColumnType.Settings)
                        navState.clear()
                    },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        if (!isImmersive) {
            VerticalDivider()
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(if (isImmersive) 0.dp else 12.dp),
            ) {
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
