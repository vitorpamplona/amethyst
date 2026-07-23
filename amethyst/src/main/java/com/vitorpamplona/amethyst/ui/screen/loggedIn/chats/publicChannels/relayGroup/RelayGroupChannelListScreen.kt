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
import androidx.compose.material3.IconButton
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
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.util.sortedBySnapshot
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip11RelayInfo.isRelaySignedRelayGroup
import com.vitorpamplona.amethyst.model.nip11RelayInfo.looksLikeNonNip29Relay
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzDmListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzImportRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzRelayImportViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsOnRelaySubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.warningColor
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_DM
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_FORUM
import com.vitorpamplona.quartz.buzz.workspace.buzzChannelType
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
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

    // This community's recent Direct Messages, shown inline below the channels (like Buzz's own
    // sidebar) instead of behind a separate drawer entry. Only mounted for Buzz relays.
    val dmVm: BuzzDmListViewModel = viewModel(key = "BuzzDmInline-${relay.url}")
    LaunchedEffect(isBuzz) { if (isBuzz) dmVm.bind(accountViewModel.account, relay.url) }
    val dmRows by dmVm.rows.collectAsStateWithLifecycle()

    // A Buzz workspace's channels come in three flavours, distinguished by the relay-signed 39000
    // `channel_type`: chat "stream" channels, "forum" channels (threaded posts), and "dm" channels
    // (private — they belong in the Direct Messages section, never the channel list). Split the
    // membership set accordingly; look each id up in the full per-relay channel set (which also
    // carries the directory 39000s) so the split reacts as metadata lands. Union in the directory
    // ids so nothing the old flat list showed disappears.
    val channelsById = remember(allChannels) { allChannels.associateBy { it.groupId.id } }
    val buzzGroupIds =
        remember(buzzChannels, channels) {
            val seen = LinkedHashSet<String>()
            (buzzChannels + channels.map { it.groupId }).filter { seen.add(it.id) }
        }

    fun buzzTypeOf(groupId: GroupId): String? = channelsById[groupId.id]?.event?.buzzChannelType()
    val buzzChatChannels =
        remember(buzzGroupIds, channelsById) {
            buzzGroupIds.filter { buzzTypeOf(it).let { t -> t != BUZZ_CHANNEL_TYPE_FORUM && t != BUZZ_CHANNEL_TYPE_DM } }
        }
    val buzzForumChannels =
        remember(buzzGroupIds, channelsById) {
            buzzGroupIds.filter { buzzTypeOf(it) == BUZZ_CHANNEL_TYPE_FORUM }
        }

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
        // A Buzz relay is a community, so always render its sectioned list (Channels, Forums, Direct
        // Messages, Agent Console) even before anything loads, rather than the generic "empty" text.
        if (channels.isEmpty() && !isBuzz) {
            // An empty directory on a relay whose NIP-11 says it doesn't run NIP-29 is almost
            // certainly the wrong relay, not a young one — say so instead of the generic empty text.
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
                        text = if (notNip29) stringRes(R.string.relay_group_channels_not_nip29) else stringRes(R.string.relay_group_channels_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notNip29) MaterialTheme.colorScheme.warningColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                if (showTorHint) {
                    item(key = "tor-hint") {
                        TorClearnetBanner(
                            relayName = relay.displayUrl(),
                            onUseClearnet = {
                                scope.launch { accountViewModel.account.saveTrustedRelayList((trustedRelays + relay).toList()) }
                            },
                        )
                    }
                }

                if (isBuzz) {
                    val noChannelsYet = buzzChatChannels.isEmpty() && buzzForumChannels.isEmpty()
                    if (noChannelsYet) {
                        item(key = "buzz-no-channels") {
                            Text(
                                text =
                                    if (buzzStatus is BuzzRelayImportViewModel.Status.Loading) {
                                        stringRes(R.string.buzz_import_loading)
                                    } else {
                                        stringRes(R.string.buzz_import_empty_body)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }

                    // -- CHANNELS --
                    if (buzzChatChannels.isNotEmpty()) {
                        item(key = "sec-channels") {
                            RelayGroupSectionHeader(title = stringRes(R.string.relay_group_section_channels)) {
                                if (buzzChatChannels.any { it.id !in buzzAdded }) {
                                    FilledTonalButton(onClick = { buzzVm.addAll() }) {
                                        Text(stringRes(R.string.buzz_import_add_all))
                                    }
                                }
                            }
                        }
                        items(buzzChatChannels, key = { "chat-${it.id}" }) { groupId ->
                            BuzzImportRow(
                                groupId = groupId,
                                isAdded = groupId.id in buzzAdded,
                                onAdd = { buzzVm.add(groupId) },
                                accountViewModel = accountViewModel,
                                onOpen = { nav.nav(Route.RelayGroup(groupId.id, relay.url)) },
                            )
                        }
                    }

                    // -- FORUMS --
                    if (buzzForumChannels.isNotEmpty()) {
                        item(key = "sec-forums") {
                            RelayGroupSectionHeader(title = stringRes(R.string.relay_group_section_forums))
                        }
                        items(buzzForumChannels, key = { "forum-${it.id}" }) { groupId ->
                            BuzzImportRow(
                                groupId = groupId,
                                isAdded = groupId.id in buzzAdded,
                                onAdd = { buzzVm.add(groupId) },
                                accountViewModel = accountViewModel,
                                // A forum channel's primary content is its threads (kind-45001 posts), not a
                                // kind-9 chat, so open the forum/threads view directly instead of the chat.
                                onOpen = { nav.nav(Route.RelayGroupThreads(groupId.id, relay.url)) },
                            )
                        }
                    }

                    // -- DIRECT MESSAGES -- (this community's private conversations, most recent first)
                    item(key = "sec-dms") {
                        RelayGroupSectionHeader(title = stringRes(R.string.buzz_dm_title)) {
                            IconButton(onClick = { nav.nav(Route.BuzzNewDm(relay.url)) }) {
                                Icon(
                                    symbol = MaterialSymbols.Add,
                                    contentDescription = stringRes(R.string.buzz_dm_new),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                    if (dmRows.isEmpty()) {
                        item(key = "dm-empty") {
                            Text(
                                text = stringRes(R.string.buzz_dm_section_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        val shown = dmRows.take(INLINE_DM_LIMIT)
                        items(shown, key = { "dm-${it.channelId}" }) { row ->
                            BuzzDmInlineRow(row, myPubkey, accountViewModel, nav) {
                                nav.nav(Route.RelayGroup(row.channelId, row.relayUrl.url))
                            }
                        }
                        if (dmRows.size > INLINE_DM_LIMIT) {
                            val extra = dmRows.size - INLINE_DM_LIMIT
                            item(key = "dm-see-all") {
                                SeeAllRow(pluralStringResource(R.plurals.buzz_dm_see_all_count, extra, extra)) {
                                    nav.nav(Route.BuzzDmList(relay.url))
                                }
                            }
                        }
                    }

                    // -- AGENT CONSOLE -- (owner's per-community fleet console, footer)
                    item(key = "sec-console") {
                        AgentConsoleFooter { nav.nav(Route.AgentConsole(relay.url)) }
                    }
                } else {
                    // Vanilla NIP-29 relay: flat channel directory (no forums/DMs/console).
                    itemsIndexed(channels, key = { _, channel -> channel.groupId.id }) { index, channel ->
                        if (index > 0) {
                            HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        RelayGroupChannelRow(channel, myPubkey, accountViewModel) { nav.nav(routeFor(channel)) }
                    }
                }
            }
        }
    }
}

/** A first screen's worth of a community's DMs shown inline; the rest live behind the See-all row. */
private const val INLINE_DM_LIMIT = 6

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
 * A section divider label ("Channels", "Forums", "Direct messages") with an optional trailing
 * action (an Add-all button, a New-message icon) — the modern equivalent of the old flat headers.
 */
@Composable
private fun RelayGroupSectionHeader(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * One inline Direct-Message conversation row inside the community view: the counterpart's avatar +
 * name (or a "+N" cluster label for a group DM) and a compact last-activity time. Tapping opens the
 * DM as its relay-group chat.
 */
@Composable
private fun BuzzDmInlineRow(
    row: BuzzDmListViewModel.DmRow,
    myPubkey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val others = row.others.ifEmpty { listOf(myPubkey) }
    val leadHex = others.first()
    val leadUser = remember(leadHex) { LocalCache.getOrCreateUser(leadHex) }
    val leadName by observeUserName(leadUser, accountViewModel)
    val label = if (others.size > 1) "$leadName +${others.size - 1}" else leadName

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(leadHex, 40.dp, accountViewModel = accountViewModel, nav = nav)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.lastActivity > 0) {
            Text(
                text = timeAgoShort(row.lastActivity, stringRes(R.string.now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The "See all N conversations" row that opens the full per-community DM inbox. */
@Composable
private fun SeeAllRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(symbol = MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** The owner's per-community Agent Console entry, pinned as the footer of a Buzz community view. */
@Composable
private fun AgentConsoleFooter(onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol = MaterialSymbols.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringRes(R.string.buzz_console_card_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringRes(R.string.buzz_console_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
