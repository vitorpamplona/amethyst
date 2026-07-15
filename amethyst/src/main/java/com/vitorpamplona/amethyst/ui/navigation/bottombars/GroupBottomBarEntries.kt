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
package com.vitorpamplona.amethyst.ui.navigation.bottombars

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.rememberConcordImageModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * The resolved presentation of a pinned chat/group [BottomBarEntry] — enough to render its avatar in
 * the bottom bar and its row in the settings picker, and to navigate when tapped. Resolved live from
 * the local cache, so the name/avatar fill in as the group's metadata arrives.
 */
@Immutable
data class GroupEntryDisplay(
    val label: String,
    val robotSeed: String,
    val model: String?,
    val route: Route,
)

@Composable
fun rememberPublicChatEntryDisplay(
    entry: BottomBarEntry.PublicChat,
    accountViewModel: AccountViewModel,
): GroupEntryDisplay {
    val channel = remember(entry.channelId) { LocalCache.getOrCreatePublicChatChannel(entry.channelId) }
    val state by observeChannel(channel, accountViewModel)
    val current = (state?.channel as? PublicChatChannel) ?: channel
    return GroupEntryDisplay(
        label = current.toBestDisplayName(),
        robotSeed = entry.channelId,
        model = current.profilePicture(),
        route = Route.PublicChatChannel(entry.channelId),
    )
}

@Composable
fun rememberRelayGroupEntryDisplay(
    entry: BottomBarEntry.RelayGroup,
    accountViewModel: AccountViewModel,
): GroupEntryDisplay {
    val relay = remember(entry.relayUrl) { RelayUrlNormalizer.normalizeOrNull(entry.relayUrl) }
    val route = Route.RelayGroup(entry.groupId, entry.relayUrl)

    if (relay == null) {
        return GroupEntryDisplay(entry.groupId, entry.groupId, null, route)
    }

    val channel = remember(entry.groupId, relay) { LocalCache.getOrCreateRelayGroupChannel(GroupId(entry.groupId, relay)) }
    val state by observeChannel(channel, accountViewModel)
    val current = (state?.channel as? RelayGroupChannel) ?: channel
    return GroupEntryDisplay(
        label = current.toBestDisplayName(),
        robotSeed = entry.groupId,
        model = current.profilePicture(),
        route = route,
    )
}

@Composable
fun rememberConcordEntryDisplay(
    entry: BottomBarEntry.Concord,
    accountViewModel: AccountViewModel,
): GroupEntryDisplay {
    val account = accountViewModel.account
    // Recompute the folded metadata (name / icon) whenever a Control Plane folds.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()

    val session = remember(entry.communityId, revision) { account.concordSessions.sessionFor(entry.communityId) }
    val metadata =
        session
            ?.state
            ?.value
            .takeIf { revision >= 0 }
            ?.metadata

    val fallbackName = remember(communities, entry.communityId) { communities.firstOrNull { it.id == entry.communityId }?.name?.ifBlank { null } }
    val label = metadata?.name?.takeIf { it.isNotBlank() } ?: fallbackName ?: stringRes(R.string.concord_home_title)
    val model = rememberConcordImageModel(metadata?.icon, accountViewModel)

    return GroupEntryDisplay(
        label = label,
        robotSeed = entry.communityId,
        model = model,
        route = Route.ConcordServer(entry.communityId),
    )
}

/** Resolves any chat/group entry to its live display, or null for a non-group entry. */
@Composable
fun rememberGroupEntryDisplay(
    entry: BottomBarEntry,
    accountViewModel: AccountViewModel,
): GroupEntryDisplay? =
    when (entry) {
        is BottomBarEntry.PublicChat -> rememberPublicChatEntryDisplay(entry, accountViewModel)
        is BottomBarEntry.RelayGroup -> rememberRelayGroupEntryDisplay(entry, accountViewModel)
        is BottomBarEntry.Concord -> rememberConcordEntryDisplay(entry, accountViewModel)
        is BottomBarEntry.BuiltIn -> null
        is BottomBarEntry.Favorite -> null
    }

/** The circular avatar for a pinned chat/group, shared by the bottom bar and the settings picker. */
@Composable
fun GroupEntryAvatar(
    display: GroupEntryDisplay,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    RobohashFallbackAsyncImage(
        robot = display.robotSeed,
        model = display.model,
        contentDescription = display.label,
        modifier = Modifier.size(size).clip(CircleShape),
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif = false,
    )
}
