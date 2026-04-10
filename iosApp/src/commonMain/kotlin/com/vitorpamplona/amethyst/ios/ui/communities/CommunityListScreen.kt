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
package com.vitorpamplona.amethyst.ios.ui.communities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityListScreen(
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    joinedCommunityIds: Set<String>,
    onNavigateToCommunity: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    // Subscribe to community definitions
    rememberSubscription(allRelayUrls, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("discover-communities"),
            filters = listOf(FilterBuilders.communityDefinitions(limit = 200)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Recompute community list when relay connections change (proxy for new events)
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    var communityRefreshKey by remember { mutableStateOf(0) }

    // Force refresh periodically as events arrive
    androidx.compose.runtime.LaunchedEffect(connectedRelays) {
        try {
            kotlinx.coroutines.delay(2000)
            communityRefreshKey++
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("LaunchedEffect error (community refresh): " + (e.message ?: "unknown"))
        }
    }

    val communities =
        remember(communityRefreshKey) {
            localCache
                .allNotes()
                .filter { it.event is CommunityDefinitionEvent }
                .mapNotNull { it.toCommunityDisplayData() }
                .sortedByDescending { it.createdAt }
        }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Communities",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
        )

        when {
            allRelayUrls.isEmpty() -> {
                LoadingState("Connecting to relays...")
            }

            communities.isEmpty() -> {
                EmptyState(
                    title = "No communities found",
                    description = "Communities will appear here as they are discovered from relays",
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                ) {
                    items(communities, key = { it.addressId }) { community ->
                        CommunityCard(
                            community = community,
                            isJoined = community.addressId in joinedCommunityIds,
                            onClick = { onNavigateToCommunity(community.addressId) },
                        )
                    }
                }
            }
        }
    }
}
