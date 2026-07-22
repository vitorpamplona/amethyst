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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.util.sortedBySnapshot
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip11RelayInfo.isRelaySignedRelayGroup
import com.vitorpamplona.amethyst.model.nip11RelayInfo.looksLikeNonNip29Relay
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzImportRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzRelayImportViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsOnRelaySubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.warningColor
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A first screen's worth of recent messages to prefetch per visible group card, ahead of a tap. */
private const val CHANNEL_LIST_WARMUP_LIMIT = 10

/** Grace period before offering the Tor→clearnet escape hatch, so a slow-but-working relay isn't nagged. */
private const val TOR_CLEARNET_HINT_DELAY_MS = 6_000L

/**
 * Lists every channel a relay hosts (its kind 39000-39003 directory), so the user
 * can browse and open channels on that relay. The relay's directory is streamed by
 * [RelayGroupsOnRelaySubscription] and consumed into per-group channels; this
 * screen reads them back for the relay and renders them. Each visible card also warms
 * its group's recent messages so opening a chat lands on cached content.
 */
@Composable
fun RelayGroupChannelListScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return

    RelayGroupsOnRelaySubscription(relay, accountViewModel.dataSources().relayGroupsOnRelay, accountViewModel)

    // Trust state drives the Tor→clearnet hint below AND a NIP-11 re-fetch: marking a relay Trusted
    // moves it off Tor onto clearnet, so a NIP-11 doc that failed over Tor must be re-fetched — its
    // cached error would otherwise keep the relay's `self` unknown for the whole TTL.
    val trustedRelays by accountViewModel.account.trustedRelayList.flow
        .collectAsStateWithLifecycle()
    val isTrusted = relay in trustedRelays

    // Warm the relay's NIP-11 so we can tell its genuine (relay-signed) groups from stray
    // user-published 39000s that a non-NIP-29 relay may also be storing. Re-keyed on trust so a move
    // to clearnet re-fetches over the new transport instead of serving the cached over-Tor failure.
    val nip11Cache = Amethyst.instance.nip11Cache
    val relayInfo by produceState(nip11Cache.getFromCache(relay), relay, isTrusted) {
        if (isTrusted) nip11Cache.invalidate(relay)
        nip11Cache.loadRelayInfo(relay, onInfo = { value = it }, onError = { _, _, _ -> })
    }

    // Re-read the relay's channels whenever a group-metadata (kind 39000) event lands in
    // the cache — driven by LocalCache.observeEvents rather than a timer, so the list
    // updates as directory events arrive with no polling. The initial value is sorted too
    // so the first frame doesn't reshuffle when the first emission arrives.
    val allChannels by produceState(
        initialValue = accountViewModel.getRelayGroupChannelsOnRelay(relay).sortedBySnapshot { it.toBestDisplayName().lowercase() },
        relay,
    ) {
        LocalCache
            .observeEvents<GroupMetadataEvent>(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
            .collect {
                value = accountViewModel.getRelayGroupChannelsOnRelay(relay).sortedBySnapshot { it.toBestDisplayName().lowercase() }
            }
    }

    // Prefer the relay's own genuine, relay-signed groups (39000 author == the NIP-11 `self`).
    // Recomputes as the NIP-11 doc resolves so real groups fill in and fakes stay hidden. But if
    // NIP-11 is unreachable (e.g. a Cloudflare-fronted relay that resets the plain HTTP GET while
    // still serving events over the socket), fall back to the dominant 39000 signer on this relay as
    // its de-facto signer — so its relay-signed groups still show while a stray user-published 39000
    // (a different author) stays filtered.
    val channels =
        remember(allChannels, relayInfo) {
            val nip11Known = relayInfo.self != null || relayInfo.supported_nips != null
            if (nip11Known) {
                allChannels.filter { isRelaySignedRelayGroup(it, relayInfo) }
            } else {
                val dominantSigner =
                    allChannels
                        .mapNotNull { it.event?.pubKey }
                        .groupingBy { it }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key
                if (dominantSigner != null) allChannels.filter { it.event?.pubKey == dominantSigner } else allChannels
            }
        }

    // Buzz relays expose no public group directory (membership is server-side), so `channels` above
    // stays empty for them. When this is a Buzz relay, fold in the membership-scoped channels
    // (kind-44100) so browsing the relay lists the channels you already belong to — each addable to
    // your kind-10009 list (so it then shows in Messages / Relay Groups). Same screen, one Browse.
    val isBuzz = BuzzRelayDialect.isBuzz(relay) || relayInfo.software?.contains("buzz", ignoreCase = true) == true
    val buzzVm: BuzzRelayImportViewModel = viewModel(key = "BuzzImport-${relay.url}")
    LaunchedEffect(isBuzz) { if (isBuzz) buzzVm.bind(accountViewModel.account, relay.url) }
    val buzzChannels by buzzVm.channels.collectAsStateWithLifecycle()
    val buzzAdded by buzzVm.added.collectAsStateWithLifecycle()
    val buzzStatus by buzzVm.status.collectAsStateWithLifecycle()

    // Tor-failure escape hatch: a Cloudflare-fronted (or otherwise Tor-hostile) relay times out over
    // Tor. When Tor is on, the relay isn't an onion, it isn't already trusted, and nothing has loaded
    // after a grace period, offer to reach it over clearnet — which adds it to the kind-10089 Trusted
    // Relay List (connected over clearnet even while Tor stays on for everything else).
    val torType by Amethyst.instance.torPrefs.torType
        .collectAsStateWithLifecycle(TorType.OFF)
    val isOnion = remember(relay) { relay.url.contains(".onion") }
    var connectTimedOut by remember(relay) { mutableStateOf(false) }
    LaunchedEffect(relay) {
        delay(TOR_CLEARNET_HINT_DELAY_MS)
        connectTimedOut = true
    }
    val scope = rememberCoroutineScope()
    val showTorHint = torType != TorType.OFF && !isOnion && relay !in trustedRelays && connectTimedOut

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
            FloatingActionButton(onClick = { nav.nav(Route.RelayGroupCreate(relay.url)) }, shape = CircleShape) {
                Icon(
                    symbol = MaterialSymbols.Add,
                    contentDescription = stringRes(R.string.relay_group_create_title),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) { padding ->
        val myPubkey = accountViewModel.userProfile().pubkeyHex
        val showBuzz = isBuzz && buzzChannels.isNotEmpty()
        // A Buzz relay is a community, so always render its list (with the per-community DM + Console
        // entries) even before channels load, rather than the generic "empty" text.
        if (channels.isEmpty() && !showBuzz && !isBuzz) {
            // An empty directory on a relay whose NIP-11 says it doesn't run NIP-29 is almost
            // certainly the wrong relay, not a young one — say so instead of the generic empty text.
            // For a Buzz relay still discovering, say so; if it settled empty, guide the user.
            val notNip29 = looksLikeNonNip29Relay(relayInfo)
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (showTorHint) {
                    TorClearnetBanner(
                        relayName = relay.displayUrl(),
                        onUseClearnet = {
                            scope.launch { accountViewModel.account.saveTrustedRelayList((trustedRelays + relay).toList()) }
                        },
                    )
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text =
                            when {
                                isBuzz && buzzStatus is BuzzRelayImportViewModel.Status.Loading -> stringRes(R.string.buzz_import_loading)
                                isBuzz -> stringRes(R.string.buzz_import_empty_body)
                                notNip29 -> stringRes(R.string.relay_group_channels_not_nip29)
                                else -> stringRes(R.string.relay_group_channels_empty)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notNip29 && !isBuzz) MaterialTheme.colorScheme.warningColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                // A Buzz relay is a community: its Direct Messages and (owner) Agent Console are
                // scoped to it, reached from here rather than as global drawer entries.
                if (isBuzz) {
                    item(key = "buzz-community-actions") {
                        BuzzCommunityActions(relayUrl = relay.url, nav = nav)
                    }
                }
                // Buzz membership section: the channels you already belong to on this workspace,
                // each addable to your kind-10009 list.
                if (showBuzz) {
                    item(key = "buzz-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringRes(R.string.buzz_import_your_channels),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (!buzzChannels.all { it.id in buzzAdded }) {
                                FilledTonalButton(onClick = { buzzVm.addAll() }) {
                                    Text(stringRes(R.string.buzz_import_add_all))
                                }
                            }
                        }
                    }
                    items(buzzChannels, key = { "buzz-${it.id}" }) { groupId ->
                        BuzzImportRow(
                            groupId = groupId,
                            isAdded = groupId.id in buzzAdded,
                            onAdd = { buzzVm.add(groupId) },
                            accountViewModel = accountViewModel,
                        )
                    }
                }
                itemsIndexed(channels, key = { _, channel -> channel.groupId.id }) { index, channel ->
                    if (index > 0 || showBuzz) {
                        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    RelayGroupChannelRow(channel, myPubkey, accountViewModel) { nav.nav(routeFor(channel)) }
                }
            }
        }
    }
}

/**
 * Shown on the relay screen when a relay won't load over Tor (e.g. a Cloudflare-fronted relay that
 * blocks Tor exits). Offers to reach it over clearnet, which adds it to the Trusted Relay List.
 */
@Composable
private fun TorClearnetBanner(
    relayName: String,
    onUseClearnet: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.relay_tor_clearnet_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringRes(R.string.relay_tor_clearnet_body, relayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.size(10.dp))
            FilledTonalButton(onClick = onUseClearnet, modifier = Modifier.align(Alignment.End)) {
                Text(stringRes(R.string.relay_tor_clearnet_action))
            }
        }
    }
}

/**
 * Per-community entry points on a Buzz relay's screen: its Direct Messages inbox and (owner) Agent
 * Console, both scoped to this one relay — replacing the old global drawer entries.
 */
@Composable
private fun BuzzCommunityActions(
    relayUrl: String,
    nav: INav,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        BuzzActionCard(
            icon = MaterialSymbols.AutoMirrored.Send,
            label = stringRes(R.string.buzz_dm_title),
            onClick = { nav.nav(Route.BuzzDmList(relayUrl)) },
        )
        Spacer(Modifier.size(8.dp))
        BuzzActionCard(
            icon = MaterialSymbols.AutoAwesome,
            label = stringRes(R.string.buzz_console_card_title),
            onClick = { nav.nav(Route.AgentConsole(relayUrl)) },
        )
    }
}

@Composable
private fun BuzzActionCard(
    icon: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(symbol = MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
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

    // Anticipate a tap: while this row is on-screen, prefetch a first screen's worth of recent
    // messages for its group (content only — the directory subscription already streams metadata),
    // so opening the chat lands on cached content instead of a blank load. Bounded to visible rows
    // by the LazyColumn, and released as they scroll off.
    RelayGroupCardWarmupSubscription(
        channel,
        accountViewModel.dataSources().relayGroupCardWarmup,
        accountViewModel,
        contentOnly = true,
        contentLimit = CHANNEL_LIST_WARMUP_LIMIT,
    )

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
