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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The Concord Channels hub: lists the communities the account has joined (from the
 * kind-13302 list) and offers a Create action. Concord has no public directory —
 * communities are E2E-encrypted and invite-gated by design — so there is no browse
 * feed here; you arrive at a community by creating one or redeeming an invite link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordHomeScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()
    // Re-read folded metadata (icon / channel count) whenever a Control Plane folds.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.concord_home_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.nav(Route.ConcordCreate) }, shape = CircleShape) {
                SymbolIcon(
                    symbol = MaterialSymbols.Add,
                    contentDescription = stringRes(R.string.concord_create_title),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) { padding ->
        if (communities.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringRes(R.string.concord_home_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(communities, key = { it.id }) { entry ->
                    val state =
                        remember(entry.id, revision) {
                            account.concordSessions
                                .sessionFor(entry.id)
                                ?.state
                                ?.value
                        }
                    CommunityRow(
                        communityId = entry.id,
                        name = state?.metadata?.name?.takeIf { it.isNotBlank() } ?: entry.name.ifBlank { stringRes(R.string.concord_home_title) },
                        iconUrl = state?.metadata?.icon,
                        channelCount = state?.channels?.size ?: 0,
                        accountViewModel = accountViewModel,
                        onClick = { nav.nav(Route.ConcordServer(entry.id)) },
                    )
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CommunityRow(
    communityId: String,
    name: String,
    iconUrl: String?,
    channelCount: Int,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(12.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = communityId,
            model = iconUrl,
            contentDescription = name,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            autoPlayGif = autoPlayGif,
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channelCount > 0) {
                Text(
                    pluralStringResource(R.plurals.concord_channel_count, channelCount, channelCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
