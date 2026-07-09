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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.dal.relayGroupDiscoveryChannelFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupPreviewSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupRosterSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsDiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

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
    // Keep the joined groups' metadata + rosters live so the "My Groups" filter can list them
    // (their host relays aren't fetched by the discovery filter set).
    RelayGroupRosterSubscription(accountViewModel.dataSources().relayGroupRoster, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = { RelayGroupsDiscoveryTopBar(accountViewModel, nav) },
        bottomBar = {
            AppBottomBar(Route.RelayGroups, nav, accountViewModel) { route ->
                if (route == Route.RelayGroups) feedContentState.sendToTop() else nav.navBottomBar(route)
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(
                    onClick = { nav.nav(Route.RelayGroupBrowse) },
                    shape = CircleShape,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Link,
                        contentDescription = stringRes(R.string.relay_group_browse_title),
                    )
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
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
                        // Collect the kind-3 follow set once for the whole list; each card highlights
                        // the members it contains ("people you follow who are in here").
                        val follows by accountViewModel.account.kind3FollowList.flow
                            .collectAsStateWithLifecycle()
                        LazyColumn(
                            contentPadding = rememberFeedContentPadding(FeedPadding),
                            modifier = Modifier.fillMaxWidth(),
                            state = listState,
                        ) {
                            itemsIndexed(
                                items.list,
                                key = { _, item -> item.idHex },
                                contentType = { _, item -> item.event?.kind ?: -1 },
                            ) { _, item ->
                                RelayGroupDiscoveryRow(item, Modifier.animateItem(), follows.authors, accountViewModel, nav)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Root top bar for the Relay Groups tab — the shared drawer/search bar every top-level feed uses,
 * with the feed-filter spinner as its title (mirrors GitRepositoriesTopBar). The browse-a-relay
 * action lives on the FAB, not here, so the bar stays consistent with the other screens.
 */
@Composable
private fun RelayGroupsDiscoveryTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UserDrawerSearchTopBar(accountViewModel, nav) {
        val selectedFilter by accountViewModel.account.settings.defaultRelayGroupsDiscoveryFollowList
            .collectAsStateWithLifecycle()
        val options by accountViewModel.feedStates.feedListOptions.relayGroupsDiscoveryRoutes
            .collectAsStateWithLifecycle()

        FeedFilterSpinner(
            placeholderCode = selectedFilter,
            explainer = stringRes(R.string.select_list_to_filter),
            options = options,
            onSelect = accountViewModel.account.settings::changeDefaultRelayGroupsDiscoveryFollowList,
            accountViewModel = accountViewModel,
        )
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
    // The "My Groups" filter reads the kind-10009 joined list, so re-scan when it changes
    // (join/leave) — otherwise a newly-joined group wouldn't appear until another refresh.
    val joinedGroups by accountViewModel.account.relayGroupList.liveRelayGroupList
        .collectAsStateWithLifecycle()

    LaunchedEffect(listName, perRelay, joinedGroups) {
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
    modifier: Modifier,
    follows: Set<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Resolve the note to its channel the SAME way the feed filter matched/sorted it, so the row
    // never binds a different relay's (empty) channel than the one the feed qualified.
    val baseChannel = remember(note) { relayGroupDiscoveryChannelFor(note) } ?: return

    // Prefetch the group's recent content so opening the card lands on a populated screen. The
    // metadata/rosters are already streaming from the directory subscription, so only ask for
    // content here (contentOnly) instead of re-requesting 39000-39003 per visible row.
    RelayGroupPreviewSubscription(baseChannel, accountViewModel.dataSources().relayGroupPreview, accountViewModel, contentOnly = true)

    val channelState by baseChannel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val channel = channelState.channel as? RelayGroupChannel ?: baseChannel

    // Reactive loaded-message count: the preview subscription streams kind-9 chats into the
    // channel's note cache, and each arrival re-emits this flow, so the "50+ messages" activity
    // signal grows live while the row is on screen.
    val notesState by baseChannel
        .flow()
        .notes.stateFlow
        .collectAsStateWithLifecycle()
    val messageCount = notesState.channel.notes.size()

    // People I follow who are in this group (relay-signed roster ∩ my kind-3 follows). Recomputed
    // when the roster changes (channelState) or my follow list does.
    val participatingFollows = remember(channelState, follows) { channel.participatingFollows(follows) }

    val favoriteRelays by accountViewModel.account.relayFeedsList.flow
        .collectAsStateWithLifecycle()

    RelayGroupDiscoveryCard(
        channel = channel,
        modifier = modifier,
        myPubkey = accountViewModel.userProfile().pubkeyHex,
        isFavoriteRelay = channel.groupId.relayUrl in favoriteRelays,
        messageCount = messageCount,
        participatingFollows = participatingFollows,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun RelayGroupDiscoveryCard(
    channel: RelayGroupChannel,
    modifier: Modifier,
    myPubkey: String,
    isFavoriteRelay: Boolean,
    messageCount: Int,
    participatingFollows: List<HexKey>,
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
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
                    if (memberCount > 0 || messageCount > 0) {
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
                            }
                            if (memberCount > 0 && messageCount > 0) {
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (messageCount > 0) {
                                // Loaded from the preview page; a full page reads as "50+", fewer as
                                // the exact count. DISCOVERY_MESSAGE_CAP tracks the preview limit.
                                Text(
                                    text =
                                        if (messageCount >= DISCOVERY_MESSAGE_CAP) {
                                            pluralStringResource(R.plurals.relay_group_message_count_capped, DISCOVERY_MESSAGE_CAP, DISCOVERY_MESSAGE_CAP)
                                        } else {
                                            pluralStringResource(R.plurals.relay_group_message_count, messageCount, messageCount)
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
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

            // Prominent social proof: the people I follow who are already in this group.
            if (participatingFollows.isNotEmpty()) {
                FollowsInGroupRow(
                    follows = participatingFollows,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            // The host relay as its own chip. Tapping the chip opens that relay's full group list;
            // the star INSIDE it is a separate tap target that only favorites the relay (surfacing
            // its groups under the relay filter). Joining the group is the separate button above.
            RelayChip(
                relay = relay,
                isFavorite = isFavoriteRelay,
                onToggleFavorite = {
                    if (isFavoriteRelay) accountViewModel.unfollowRelayFeed(relay) else accountViewModel.followRelayFeed(relay)
                },
                onOpenRelay = { nav.nav(Route.RelayGroupServer(relay.url)) },
                modifier = Modifier.padding(top = 8.dp),
            )

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

/**
 * The group's host relay as a tappable chip. The chip body opens that relay's full group list
 * ([Route.RelayGroupServer]); the star inside it is an independent tap target that only toggles the
 * relay in the kind-10012 relay-feeds list (a favorited relay, and every group it hosts, then
 * surfaces under the relay chip in the top-nav filter). Filled/primary when favorited, tonal outline
 * otherwise — visually distinct from the group's Join button so the scopes don't read as one action.
 */
@Composable
private fun RelayChip(
    relay: NormalizedRelayUrl,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenRelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = onOpenRelay,
            shape = RoundedCornerShape(8.dp),
            color =
                if (isFavorite) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Icon(
                    symbol = if (isFavorite) MaterialSymbols.Star else MaterialSymbols.StarBorder,
                    contentDescription = stringRes(R.string.relay_group_favorite_relay),
                    tint =
                        if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onToggleFavorite)
                            .padding(4.dp)
                            .size(16.dp),
                )
                Text(
                    text = relay.displayUrl(),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (isFavorite) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Overlapping avatars of the people I follow who are in the group, with a "%d people you follow"
 * caption — social proof that a group is worth joining. Shows up to five faces; the caption always
 * carries the true total.
 */
@Composable
private fun FollowsInGroupRow(
    follows: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
            follows.take(5).forEach { pubkey ->
                UserPicture(
                    userHex = pubkey,
                    size = Size25dp,
                    pictureModifier = Modifier.border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
        Text(
            text = pluralStringResource(R.plurals.relay_group_follows_participating, follows.size, follows.size),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Display cap for the loaded-message counter — kept in step with the discovery preview's fetch
 * limit (RELAY_GROUP_PREVIEW_LIMIT), so a chat that returns the full page reads as "50+ messages".
 */
private const val DISCOVERY_MESSAGE_CAP = 50

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
