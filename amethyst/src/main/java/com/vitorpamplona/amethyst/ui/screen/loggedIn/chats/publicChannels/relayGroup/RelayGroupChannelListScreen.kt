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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupDirectorySubscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.delay

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

    // The channel set grows as directory events arrive; poll the cache while the
    // screen is on top (a directory is small and rarely changes).
    val channels by produceState(
        initialValue = accountViewModel.getRelayGroupChannelsOnRelay(relay),
        relay,
    ) {
        while (true) {
            value = accountViewModel.getRelayGroupChannelsOnRelay(relay).sortedBy { it.toBestDisplayName().lowercase() }
            delay(1500)
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
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(channels, key = { it.groupId.id }) { channel ->
                RelayGroupChannelRow(channel) { nav.nav(routeFor(channel)) }
                HorizontalDivider(thickness = 0.25.dp)
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
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "# " + channel.toBestDisplayName(),
            fontWeight = FontWeight.SemiBold,
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
}
