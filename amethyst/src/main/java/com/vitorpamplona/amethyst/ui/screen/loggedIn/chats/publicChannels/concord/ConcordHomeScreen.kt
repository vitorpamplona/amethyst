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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The Concord Channels hub — a single-screen browser of every community the account
 * joined (kind-13302) and, expanded inline, that community's channels. A community
 * rail across the top jumps to (and expands) any server; each row in the list below
 * is a community you can expand to reveal its `#`/🔒/🎙 channels without leaving the
 * screen. Tapping a channel opens its chat; the community header opens the full
 * server view.
 *
 * Concord has no public directory — communities are E2E-encrypted and invite-gated —
 * so there's no browse feed: you arrive by creating one, redeeming an invite, or (for
 * a key already used on another Concord client) the on-open import from the stock
 * relays below. The live plane subscription is mounted here so channels fold in while
 * you browse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordHomeScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

    val account = accountViewModel.account
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()
    // Re-read folded metadata (name / icon / channels) whenever a Control Plane folds.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()

    // Concord clients (Armada/Vector) publish the kind-13302 joined list to the Concord
    // stock relays, not the user's outbox, so a community joined there never surfaces at
    // login. Pull it from those relays when the hub opens — scoped here so we only reach
    // the stock relays for users who actually use Concord.
    LaunchedEffect(Unit) { accountViewModel.importConcordCommunities() }

    // Communities expanded in the accordion (multi-open, so several can show channels at once).
    var expanded by remember { mutableStateOf(emptySet<String>()) }

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
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // Community rail: every joined community as an avatar; tap toggles its channels below.
            CommunityRail(
                communities = communities,
                revision = revision,
                expanded = expanded,
                accountViewModel = accountViewModel,
                onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id },
            )
            HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(Modifier.fillMaxSize()) {
                communities.forEach { entry ->
                    val state =
                        account.concordSessions
                            .sessionFor(entry.id)
                            ?.state
                            ?.value
                            .takeIf { revision >= 0 }
                    val isOpen = entry.id in expanded

                    item(key = entry.id) {
                        CommunityHeader(
                            communityId = entry.id,
                            name = state?.metadata?.name?.takeIf { it.isNotBlank() } ?: entry.name.ifBlank { stringRes(R.string.concord_home_title) },
                            iconPointer = state?.metadata?.icon,
                            channelCount = state?.channels?.size ?: 0,
                            expanded = isOpen,
                            accountViewModel = accountViewModel,
                            onToggle = { expanded = if (isOpen) expanded - entry.id else expanded + entry.id },
                            onOpen = { nav.nav(Route.ConcordServer(entry.id)) },
                        )
                    }

                    if (isOpen && state != null) {
                        val channels = state.channels.entries.toList()
                        items(channels, key = { "${entry.id}/${it.key}" }) { ch ->
                            val def = ch.value.definition
                            ChannelSubRow(
                                name = def?.name ?: ch.key,
                                icon =
                                    when {
                                        def?.voice == true -> MaterialSymbols.Mic
                                        def?.private == true -> MaterialSymbols.Lock
                                        else -> MaterialSymbols.Tag
                                    },
                                onClick = { nav.nav(Route.Concord(entry.id, ch.key)) },
                            )
                        }
                    }
                    item(key = "div-${entry.id}") {
                        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityRail(
    communities: List<ConcordCommunityListEntry>,
    revision: Int,
    expanded: Set<String>,
    accountViewModel: AccountViewModel,
    onToggle: (String) -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(communities, key = { it.id }) { entry ->
            val iconPointer =
                accountViewModel.account.concordSessions
                    .sessionFor(entry.id)
                    ?.state
                    ?.value
                    ?.metadata
                    ?.icon
                    .takeIf { revision >= 0 }
            val iconModel = rememberConcordImageModel(iconPointer, accountViewModel)
            val isOpen = entry.id in expanded
            val ring = if (isOpen) MaterialTheme.colorScheme.primary else Color.Transparent
            RobohashFallbackAsyncImage(
                robot = entry.id,
                model = iconModel,
                contentDescription = entry.name,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, ring, CircleShape)
                        .clickable { onToggle(entry.id) },
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                autoPlayGif = autoPlayGif,
            )
        }
    }
}

@Composable
private fun CommunityHeader(
    communityId: String,
    name: String,
    iconPointer: ImagePointer?,
    channelCount: Int,
    expanded: Boolean,
    accountViewModel: AccountViewModel,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val iconModel = rememberConcordImageModel(iconPointer, accountViewModel)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = communityId,
            model = iconModel,
            contentDescription = name,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onOpen),
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
        SymbolIcon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChannelSubRow(
    name: String,
    icon: MaterialSymbol,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 40.dp, end = 16.dp)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SymbolIcon(symbol = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
