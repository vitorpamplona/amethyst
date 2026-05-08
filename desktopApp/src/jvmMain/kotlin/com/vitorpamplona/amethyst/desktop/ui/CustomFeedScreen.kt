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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import com.vitorpamplona.amethyst.desktop.ui.deck.LocalFeedRepository
import kotlinx.collections.immutable.persistentListOf

@Composable
fun CustomFeedScreen(
    feedId: String,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
) {
    val feedRepository = LocalFeedRepository.current
    val feeds by feedRepository.feeds.collectAsState()
    val feedDef = feeds.firstOrNull { it.id == feedId }

    if (feedDef == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Feed not found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    when (val source = feedDef.source) {
        is FeedSource.Filter -> {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "${feedDef.emoji} ${feedDef.name}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
                )
                FeedScreen(
                    relayManager = relayManager,
                    localCache = localCache,
                    subscriptionsCoordinator = subscriptionsCoordinator,
                    customFeedId = feedId,
                    customFeedSource = source,
                    initialFeedMode = FeedMode.CUSTOM,
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToThread = onNavigateToThread,
                    onZapFeedback = onZapFeedback,
                )
            }
        }

        is FeedSource.Following -> {
            FeedScreen(
                relayManager = relayManager,
                localCache = localCache,
                subscriptionsCoordinator = subscriptionsCoordinator,
                initialFeedMode = FeedMode.FOLLOWING,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        is FeedSource.Global -> {
            FeedScreen(
                relayManager = relayManager,
                localCache = localCache,
                subscriptionsCoordinator = subscriptionsCoordinator,
                initialFeedMode = FeedMode.GLOBAL,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        is FeedSource.SingleRelay -> {
            FeedScreen(
                relayManager = relayManager,
                localCache = localCache,
                subscriptionsCoordinator = subscriptionsCoordinator,
                customFeedId = feedId,
                customFeedSource = FeedSource.Filter(relays = persistentListOf(source.url)),
                initialFeedMode = FeedMode.CUSTOM,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onZapFeedback = onZapFeedback,
            )
        }

        is FeedSource.DVM,
        is FeedSource.PeopleList,
        is FeedSource.InterestSet,
        -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("${feedDef.name} — coming soon", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
