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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.feeds.IosCommunityPostsFeedFilter
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.ios.ui.note.NoteCard
import com.vitorpamplona.amethyst.ios.ui.toNoteDisplayData
import com.vitorpamplona.amethyst.ios.viewmodels.IosFeedViewModel
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    communityAddressId: String,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    isJoined: Boolean,
    isReadOnly: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onPostToCommunity: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onBack: () -> Unit,
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    // Subscribe to approved posts in this community
    rememberSubscription(allRelayUrls, communityAddressId, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("community-posts"),
            filters = listOf(FilterBuilders.communityApprovedPosts(communityAddressId, limit = 200)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Also fetch posts that tag this community
    rememberSubscription(allRelayUrls, communityAddressId, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("community-tagged"),
            filters = listOf(FilterBuilders.postsTaggingCommunity(communityAddressId, limit = 200)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Resolve community definition
    val communityNote =
        remember(communityAddressId) {
            localCache
                .allNotes()
                .firstOrNull {
                    val ev = it.event
                    ev is CommunityDefinitionEvent && ev.addressTag() == communityAddressId
                }
        }
    val communityData = communityNote?.toCommunityDisplayData()

    val viewModel =
        remember(communityAddressId) {
            IosFeedViewModel(IosCommunityPostsFeedFilter(communityAddressId, localCache), localCache)
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        communityData?.name ?: "Community",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isReadOnly) {
                FloatingActionButton(onClick = onPostToCommunity) {
                    Icon(Icons.Default.Edit, contentDescription = "Post to community")
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = modifier.fillMaxSize().padding(paddingValues),
        ) {
            // Community header
            if (communityData != null) {
                CommunityHeader(
                    community = communityData,
                    isJoined = isJoined,
                    isReadOnly = isReadOnly,
                    onJoin = onJoin,
                    onLeave = onLeave,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Posts feed
            when (val state = feedState) {
                is FeedState.Loading -> {
                    LoadingState("Loading community posts...")
                }

                is FeedState.Empty -> {
                    EmptyState(
                        title = "No posts yet",
                        description = "Be the first to post in this community!",
                    )
                }

                is FeedState.FeedError -> {
                    EmptyState(title = "Error", description = state.errorMessage)
                }

                is FeedState.Loaded -> {
                    val loadedState by state.feed.collectAsState()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    ) {
                        items(loadedState.list, key = { it.idHex }) { note ->
                            val event = note.event ?: return@items
                            NoteCard(
                                note = note.toNoteDisplayData(localCache),
                                onClick = { onNavigateToThread(event.id) },
                                onAuthorClick = onNavigateToProfile,
                                onBoost = onBoost,
                                onLike = onLike,
                                onZap = onZap,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityHeader(
    community: CommunityDisplayData,
    isJoined: Boolean,
    isReadOnly: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (community.image != null) {
                AsyncImage(
                    model = community.image,
                    contentDescription = community.name,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = community.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${community.moderatorCount} moderator${if (community.moderatorCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!community.description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = community.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (!community.rules.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Rules",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = community.rules,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!isReadOnly) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                if (isJoined) {
                    OutlinedButton(onClick = onLeave) {
                        Text("Leave Community")
                    }
                } else {
                    Button(onClick = onJoin) {
                        Text("Join Community")
                    }
                }
            }
        }
    }
}
