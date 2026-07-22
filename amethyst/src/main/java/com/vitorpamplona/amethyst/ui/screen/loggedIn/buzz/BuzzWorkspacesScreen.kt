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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.relayGroupChannelHasUnreadFlow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * The **Workspaces** tab — a first-class navigation home for `block/buzz` workspaces,
 * giving Buzz a distinct identity instead of hiding inside the generic relay-group list.
 * A Buzz workspace is a NIP-29 group on a Buzz-dialect relay, so this filters the user's
 * joined groups to those relays ([BuzzRelayDialect]) and adds the Buzz-only surfaces (the
 * Agent Console) up top.
 *
 * Read-only aggregation: it reuses the always-on relay-group state subscription (mounted
 * at login), so no per-screen fetch is needed — it just projects the joined set.
 */
@Composable
fun BuzzWorkspacesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val joined by accountViewModel.account.relayGroupList.liveRelayGroupList
        .collectAsStateWithLifecycle()
    val buzzRelays by BuzzRelayDialect.flow.collectAsStateWithLifecycle()

    val workspaces =
        remember(joined, buzzRelays) {
            joined
                .mapNotNull { tag ->
                    val relay = RelayUrlNormalizer.normalizeOrNull(tag.relayUrl) ?: return@mapNotNull null
                    if (relay !in buzzRelays) return@mapNotNull null
                    GroupId(tag.groupId, relay)
                }.sortedBy { it.id }
        }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            UserDrawerSearchTopBar(accountViewModel, nav) {
                Text(
                    text = stringRes(R.string.buzz_workspaces_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        bottomBar = {
            AppBottomBar(Route.BuzzWorkspaces, nav, accountViewModel) { route ->
                nav.navBottomBar(route)
            }
        },
        accountViewModel = accountViewModel,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AgentConsoleHeroCard(onClick = { nav.nav(Route.AgentConsole) }) }

            if (workspaces.isEmpty()) {
                item { EmptyWorkspaces(onBrowse = { nav.nav(Route.RelayGroups) }) }
            } else {
                item {
                    Text(
                        text = stringRes(R.string.buzz_workspaces_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
                items(workspaces, key = { it.id + it.relayUrl.url }) { groupId ->
                    WorkspaceRow(groupId, accountViewModel, nav)
                }
            }
        }
    }
}

/** A bold accent card leading to the Buzz-only Agent Console — the tab's signature surface. */
@Composable
private fun AgentConsoleHeroCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringRes(R.string.buzz_console_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringRes(R.string.buzz_console_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** One workspace: a colored monogram avatar, live name + host, member count, unread dot. */
@Composable
private fun WorkspaceRow(
    groupId: GroupId,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseChannel = remember(groupId) { LocalCache.getOrCreateRelayGroupChannel(groupId) }
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val hasUnread by remember(groupId) {
        relayGroupChannelHasUnreadFlow(accountViewModel.account, groupId)
    }.collectAsStateWithLifecycle(initialValue = false)

    val name = channel.toBestDisplayName()
    val memberCount = channel.memberCount()

    Card(
        onClick = { nav.nav(routeFor(channel)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkspaceAvatar(name = name, seed = groupId.id)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = groupId.relayUrl.displayUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (memberCount > 0) {
                Icon(
                    symbol = MaterialSymbols.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "$memberCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasUnread) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** A round monogram whose color is derived deterministically from the workspace id. */
@Composable
private fun WorkspaceAvatar(
    name: String,
    seed: String,
) {
    val hue = remember(seed) { (seed.hashCode().toLong() and 0xFFFFFF).toFloat() % 360f }
    val color = remember(hue) { Color.hsl(hue, 0.55f, 0.5f) }
    val initial =
        remember(name) {
            name
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString() ?: "#"
        }

    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** Inviting empty state: what a Buzz workspace is + a way to go find one. */
@Composable
private fun EmptyWorkspaces(onBrowse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = stringRes(R.string.buzz_workspaces_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringRes(R.string.buzz_workspaces_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.size(2.dp))
            FilledTonalButton(onClick = onBrowse) {
                Text(stringRes(R.string.buzz_workspaces_browse))
            }
        }
    }
}
