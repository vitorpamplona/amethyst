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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.amethyst.commons.ui.components.BunkerHeartbeatIndicator
import com.vitorpamplona.amethyst.desktop.DesktopScreen
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm
import kotlinx.coroutines.CoroutineScope

private data class NavItem(
    val type: DeckColumnType,
    val icon: ImageVector,
    val label: String,
)

private val navItems =
    listOf(
        NavItem(DeckColumnType.HomeFeed, Icons.Default.Home, "Home"),
        NavItem(DeckColumnType.Reads, Icons.AutoMirrored.Filled.Article, "Reads"),
        NavItem(DeckColumnType.Search, Icons.Default.Search, "Search"),
        NavItem(DeckColumnType.Bookmarks, Icons.Default.Bookmark, "Bookmarks"),
        NavItem(DeckColumnType.Messages, Icons.Default.Email, "Messages"),
        NavItem(DeckColumnType.Notifications, Icons.Default.Notifications, "Notifications"),
        NavItem(DeckColumnType.MyProfile, Icons.Default.Person, "Profile"),
        NavItem(DeckColumnType.Chess, Icons.Default.Extension, "Chess"),
        NavItem(DeckColumnType.Settings, Icons.Default.Settings, "Settings"),
    )

@Composable
fun SinglePaneLayout(
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    nwcConnection: Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    appScope: CoroutineScope,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    signerConnectionState: SignerConnectionState,
    lastPingTimeSec: Long?,
    modifier: Modifier = Modifier,
) {
    var currentColumnType by remember { mutableStateOf<DeckColumnType>(DeckColumnType.HomeFeed) }
    val navState = remember { ColumnNavigationState() }
    val navStack by navState.stack.collectAsState()
    val currentOverlay = navStack.lastOrNull()

    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.width(80.dp).fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            navItems.forEach { item ->
                NavigationRailItem(
                    selected = currentColumnType == item.type && navStack.isEmpty(),
                    onClick = {
                        currentColumnType = item.type
                        navState.clear()
                    },
                    icon = {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            BunkerHeartbeatIndicator(
                signerConnectionState = signerConnectionState,
                lastPingTimeSec = lastPingTimeSec,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        VerticalDivider()

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Show header with back button when navigated into overlay
            if (navStack.isNotEmpty()) {
                SinglePaneHeader(
                    title =
                        when (currentOverlay) {
                            is DesktopScreen.UserProfile -> "Profile"
                            is DesktopScreen.Thread -> "Thread"
                            else -> currentColumnType.title()
                        },
                    onBack = { navState.pop() },
                )
                HorizontalDivider()
            }

            Box(
                modifier = Modifier.fillMaxSize().padding(12.dp),
            ) {
                if (currentOverlay != null) {
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
                } else {
                    RootContent(
                        columnType = currentColumnType,
                        relayManager = relayManager,
                        localCache = localCache,
                        accountManager = accountManager,
                        account = account,
                        nwcConnection = nwcConnection,
                        subscriptionsCoordinator = subscriptionsCoordinator,
                        appScope = appScope,
                        onShowComposeDialog = onShowComposeDialog,
                        onShowReplyDialog = onShowReplyDialog,
                        onZapFeedback = onZapFeedback,
                        onNavigateToProfile = { navState.push(DesktopScreen.UserProfile(it)) },
                        onNavigateToThread = { navState.push(DesktopScreen.Thread(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SinglePaneHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
