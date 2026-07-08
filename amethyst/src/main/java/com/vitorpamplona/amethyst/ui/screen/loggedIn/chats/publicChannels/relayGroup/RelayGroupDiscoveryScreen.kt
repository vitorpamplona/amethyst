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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupPreviewSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsDiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * Discover NIP-29 groups across the relay set the top-bar filter resolves to. Built on the shared
 * feed stack exactly like the Git-repositories screen: a [FeedContentState]
 * ([com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountFeedContentStates.relayGroupsDiscoveryFeed])
 * fed by the per-type datasource, a [FeedFilterSpinner] persisting the selection to
 * `defaultRelayGroupsDiscoveryFollowList`, and a [RefresheableBox] + [RenderFeedContentState]. Rows
 * are the relay-signed 39000 metadata notes, rendered as joinable group cards.
 */
@Composable
fun RelayGroupDiscoveryScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayGroupDiscoveryScreen(
        feedContentState = accountViewModel.feedStates.relayGroupsDiscoveryFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun RelayGroupDiscoveryScreen(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedContentState)
    WatchAccountForRelayGroupDiscovery(feedContentState, accountViewModel)
    RelayGroupsDiscoveryFilterAssemblerSubscription(accountViewModel)

    Scaffold(
        topBar = {
            val selectedFilter by accountViewModel.account.settings.defaultRelayGroupsDiscoveryFollowList
                .collectAsStateWithLifecycle()
            val options by accountViewModel.feedStates.feedListOptions.relayGroupsDiscoveryRoutes
                .collectAsStateWithLifecycle()
            TopBarExtensibleWithBackButton(
                title = {
                    FeedFilterSpinner(
                        placeholderCode = selectedFilter,
                        explainer = stringRes(R.string.select_list_to_filter),
                        options = options,
                        onSelect = accountViewModel.account.settings::changeDefaultRelayGroupsDiscoveryFollowList,
                        accountViewModel = accountViewModel,
                    )
                },
                actions = {
                    IconButton(onClick = { nav.nav(Route.RelayGroupBrowse) }) {
                        Icon(
                            symbol = MaterialSymbols.Link,
                            contentDescription = stringRes(R.string.relay_group_browse_title),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                popBack = nav::popBack,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            RefresheableBox(feedContentState, true) {
                SaveableFeedContentState(feedContentState, scrollStateKey = ScrollStateKeys.RELAY_GROUPS_DISCOVERY_SCREEN) { listState ->
                    RenderFeedContentState(
                        feedContentState = feedContentState,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = null,
                        onLoaded = { loaded ->
                            val items by loaded.feed.collectAsStateWithLifecycle()
                            LazyColumn(Modifier.fillMaxWidth(), state = listState) {
                                itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
                                    RelayGroupDiscoveryRow(item, accountViewModel, nav)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchAccountForRelayGroupDiscovery(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listName by accountViewModel.account.settings.defaultRelayGroupsDiscoveryFollowList
        .collectAsStateWithLifecycle()
    val perRelay by accountViewModel.account.liveRelayGroupsDiscoveryFollowListsPerRelay
        .collectAsStateWithLifecycle()

    LaunchedEffect(listName, perRelay) {
        feedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

/**
 * Resolves the 39000 metadata note to its live [RelayGroupChannel] (keyed by host relay + group
 * id) and recomposes in place as the relay-signed metadata / roster changes, so member counts and
 * membership stay current without moving the row. While the row is on screen it also warms the
 * group — the newest handful of chat messages / threads — so tapping it opens an already-populated
 * screen. The warm-up is lifecycle-aware and tears down as the row scrolls off.
 */
@Composable
private fun RelayGroupDiscoveryRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? GroupMetadataEvent ?: return
    val relay = note.relays.firstOrNull() ?: return
    val baseChannel = remember(event.groupId(), relay) { LocalCache.getOrCreateRelayGroupChannel(GroupId(event.groupId(), relay)) }

    // Prefetch the group's recent content so opening the card lands on a populated screen.
    RelayGroupPreviewSubscription(baseChannel, accountViewModel.dataSources().relayGroupPreview, accountViewModel)

    val channelState by baseChannel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val channel = channelState.channel as? RelayGroupChannel ?: baseChannel

    val favoriteRelays by accountViewModel.account.relayFeedsList.flow
        .collectAsStateWithLifecycle()

    RelayGroupDiscoveryCard(
        channel = channel,
        myPubkey = accountViewModel.userProfile().pubkeyHex,
        isFavoriteRelay = channel.groupId.relayUrl in favoriteRelays,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun RelayGroupDiscoveryCard(
    channel: RelayGroupChannel,
    myPubkey: String,
    isFavoriteRelay: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val isMember = channel.membershipOf(myPubkey).isMember()
    val memberCount = channel.memberCount()
    val relay = channel.groupId.relayUrl

    ElevatedCard(
        onClick = { nav.nav(routeFor(channel)) },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RobohashFallbackAsyncImage(
                    robot = channel.groupId.id,
                    model = channel.profilePicture(),
                    contentDescription = channel.toBestDisplayName(),
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                    loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                    loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                    autoPlayGif = autoPlayGif,
                )

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = channel.toBestDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        DiscoveryStatusPill(channel)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (memberCount > 0) {
                            Icon(
                                symbol = MaterialSymbols.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = pluralStringResource(R.plurals.relay_group_member_count, memberCount, memberCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = relay.displayUrl(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }

                // Star the group's host relay so it (and its other groups) surface under the relay
                // chip in the filter — the favorite is the kind-10012 relay-feeds list.
                IconButton(onClick = {
                    if (isFavoriteRelay) accountViewModel.unfollowRelayFeed(relay) else accountViewModel.followRelayFeed(relay)
                }) {
                    Icon(
                        symbol = if (isFavoriteRelay) MaterialSymbols.Star else MaterialSymbols.StarBorder,
                        contentDescription = stringRes(R.string.relay_group_favorite_relay),
                        tint = if (isFavoriteRelay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                if (!isMember) {
                    FilledTonalButton(onClick = {
                        // Open groups join directly; closed ones open the group where the invite
                        // code can be entered.
                        if (channel.isClosed()) {
                            nav.nav(routeFor(channel))
                        } else {
                            accountViewModel.joinRelayGroup(channel)
                        }
                    }) {
                        Text(stringRes(R.string.join))
                    }
                }
            }

            channel.summary()?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

/** Small tonal pill: Invite-only wins over Private, matching the inline group card. */
@Composable
private fun DiscoveryStatusPill(channel: RelayGroupChannel) {
    val label =
        when {
            channel.isClosed() -> stringRes(R.string.relay_group_badge_invite_only)
            channel.isPrivate() -> stringRes(R.string.relay_group_badge_private)
            else -> return
        }
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
