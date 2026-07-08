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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupDirectorySubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * Discover NIP-29 groups across the relay set the top-bar filter resolves to. Mirrors the
 * Git/Pictures feeds: a [FeedFilterSpinner] persists the selection to
 * `defaultRelayGroupsDiscoveryFollowList` (Global / Follows / a followed hashtag or geohash /
 * a specific relay — favorite relays show up as relay chips). The group directory query is
 * fanned out to every relay in that set and each group is listed as a joinable card.
 */
@Composable
fun RelayGroupDiscoveryScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: RelayGroupDiscoveryViewModel = viewModel()
    viewModel.init(accountViewModel.account)

    val relays by viewModel.relays.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedFilter by accountViewModel.account.settings.defaultRelayGroupsDiscoveryFollowList
        .collectAsStateWithLifecycle()
    val favoriteRelays by accountViewModel.account.relayFeedsList.flow
        .collectAsStateWithLifecycle()

    // Fan the directory subscription out to each relay in the current set while the screen
    // is visible; each is a lifecycle-aware per-relay REQ that EOSEs and dedupes by relay.
    relays.forEach { relay ->
        key(relay) {
            RelayGroupDirectorySubscription(relay, accountViewModel.dataSources().relayGroupDirectory, accountViewModel)
        }
    }

    Scaffold(
        topBar = {
            val options by accountViewModel.feedStates.feedListOptions.kind3GlobalPeopleRoutes
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
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text =
                        if (selectedFilter == TopFilter.Global) {
                            stringRes(R.string.relay_group_discovery_empty)
                        } else {
                            stringRes(R.string.relay_group_discovery_empty_filtered)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            val myPubkey = accountViewModel.userProfile().pubkeyHex
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(groups, key = { it.groupId.toKey() }) { channel ->
                    RelayGroupDiscoveryCard(
                        channel = channel,
                        myPubkey = myPubkey,
                        isFavoriteRelay = channel.groupId.relayUrl in favoriteRelays,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
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

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = channel.groupId.id,
            model = channel.profilePicture(),
            contentDescription = channel.toBestDisplayName(),
            modifier = Modifier.size(48.dp).clip(CircleShape),
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            autoPlayGif = autoPlayGif,
        )

        Column(
            Modifier
                .weight(1f)
                .clickable { nav.nav(routeFor(channel)) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = channel.toBestDisplayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                DiscoveryStatusPill(channel)
            }
            val subtitle =
                if (memberCount > 0) {
                    "${relay.displayUrl()} · ${pluralStringResource(R.plurals.relay_group_member_count, memberCount, memberCount)}"
                } else {
                    relay.displayUrl()
                }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel.summary()?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
