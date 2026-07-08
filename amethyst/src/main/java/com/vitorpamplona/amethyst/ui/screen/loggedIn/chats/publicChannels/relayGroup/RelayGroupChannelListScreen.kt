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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupDirectorySubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * Lists every channel a relay hosts (its kind 39000-39003 directory), so the user
 * can browse and open channels on that relay. The relay's directory is streamed by
 * [RelayGroupDirectorySubscription] and consumed into per-group channels; this
 * screen reads them back for the relay and renders them.
 */
@Composable
fun RelayGroupChannelListScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return

    var showCreate by remember { mutableStateOf(false) }

    RelayGroupDirectorySubscription(relay, accountViewModel.dataSources().relayGroupDirectory, accountViewModel)

    // Re-read the relay's channels whenever a group-metadata (kind 39000) event lands in
    // the cache — driven by LocalCache.observeEvents rather than a timer, so the list
    // updates as directory events arrive with no polling. The initial value is sorted too
    // so the first frame doesn't reshuffle when the first emission arrives.
    val channels by produceState(
        initialValue = accountViewModel.getRelayGroupChannelsOnRelay(relay).sortedBy { it.toBestDisplayName().lowercase() },
        relay,
    ) {
        LocalCache
            .observeEvents<GroupMetadataEvent>(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
            .collect {
                value = accountViewModel.getRelayGroupChannelsOnRelay(relay).sortedBy { it.toBestDisplayName().lowercase() }
            }
    }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text(
                        text = relay.displayUrl(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                popBack = nav::popBack,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(
                    symbol = MaterialSymbols.Add,
                    contentDescription = stringRes(R.string.relay_group_create_title),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) { padding ->
        val myPubkey = accountViewModel.userProfile().pubkeyHex
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringRes(R.string.relay_group_channels_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                itemsIndexed(channels, key = { _, channel -> channel.groupId.id }) { index, channel ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    RelayGroupChannelRow(channel, myPubkey, accountViewModel) { nav.nav(routeFor(channel)) }
                }
            }
        }
    }

    if (showCreate) {
        CreateRelayGroupDialog(relay, accountViewModel, nav) { showCreate = false }
    }
}

@Composable
private fun RelayGroupChannelRow(
    channel: RelayGroupChannel,
    myPubkey: String,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val joined = channel.membershipOf(myPubkey).isMember()
    val memberCount = channel.memberCount()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = channel.groupId.id,
            model = channel.profilePicture(),
            contentDescription = channel.toBestDisplayName(),
            modifier = Modifier.size(40.dp).clip(CircleShape),
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            autoPlayGif = autoPlayGif,
        )

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (channel.isPrivate()) {
                    Icon(
                        symbol = MaterialSymbols.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = channel.toBestDisplayName(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subtitle =
                channel.summary()?.takeIf { it.isNotBlank() }
                    ?: if (memberCount > 0) {
                        pluralStringResource(R.plurals.relay_group_member_count, memberCount, memberCount)
                    } else {
                        null
                    }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (joined) {
            Box(Modifier.size(20.dp).clip(CircleShape)) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = stringRes(R.string.relay_group_role_member),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
