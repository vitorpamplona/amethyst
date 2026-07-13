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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The Concord Channels hub — a single-screen browser of every community the account
 * joined (kind-13302) and, expanded inline, that community's channels. Each row is a
 * community you can expand to reveal its `#`/🔒/🎙 channels without leaving the screen.
 * Tapping a channel opens its chat; the community header opens the full server view.
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
                    // Back arrow only when this is a pushed screen (from the drawer / a deep link);
                    // as a bottom-nav root there is nothing to pop and the bar takes its place.
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Renders only when this is a bottom-nav root (AppBottomBar hides itself when canPop),
            // so the same screen works both as a pushed destination and a bottom-nav tab.
            AppBottomBar(Route.Concords, nav, accountViewModel) { route ->
                if (route != Route.Concords) nav.navBottomBar(route)
            }
        },
        floatingActionButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(onClick = { nav.nav(Route.ConcordCreate) }, shape = CircleShape) {
                    SymbolIcon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(R.string.concord_create_title),
                        modifier = Modifier.size(24.dp),
                    )
                }
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

        // Busiest communities first: sort by the most-recent message across their channels so the
        // one you'd actually open floats to the top (recomputed as messages fold in on `revision`).
        val sorted =
            remember(communities, revision) {
                communities.sortedByDescending { entry ->
                    val keys =
                        account.concordSessions
                            .sessionFor(entry.id)
                            ?.state
                            ?.value
                            ?.channels
                            ?.keys
                            .orEmpty()
                    communityActivity(entry.id, keys)
                }
            }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            sorted.forEach { entry ->
                val state =
                    account.concordSessions
                        .sessionFor(entry.id)
                        ?.state
                        ?.value
                        .takeIf { revision >= 0 }
                val isOpen = entry.id in expanded
                val channelKeys = state?.channels?.keys.orEmpty()

                item(key = entry.id) {
                    CommunityHeader(
                        communityId = entry.id,
                        name = state?.metadata?.name?.takeIf { it.isNotBlank() } ?: entry.name.ifBlank { stringRes(R.string.concord_home_title) },
                        iconPointer = state?.metadata?.icon,
                        channelKeys = channelKeys,
                        revision = revision,
                        expanded = isOpen,
                        accountViewModel = accountViewModel,
                        onToggle = { expanded = if (isOpen) expanded - entry.id else expanded + entry.id },
                        onOpen = { nav.nav(Route.ConcordServer(entry.id)) },
                    )
                }

                if (isOpen && state != null) {
                    // A banner hero (CORD-02 §6), when the community set one — pops in once decrypted.
                    state.metadata?.banner?.let { banner ->
                        item(key = "banner-${entry.id}") { CommunityBanner(banner, accountViewModel) }
                    }
                    // Channels, most-recently-active first, each with its last message + unread state.
                    val channels =
                        state.channels.entries.sortedByDescending {
                            LocalCache.getConcordChannelIfExists(ConcordChannelId(entry.id, it.key))?.lastNote?.createdAt() ?: 0L
                        }
                    items(channels, key = { "${entry.id}/${it.key}" }) { ch ->
                        val def = ch.value.definition
                        ConcordChannelRow(
                            communityId = entry.id,
                            channelKey = ch.key,
                            channelName = def?.name ?: ch.key,
                            icon =
                                when {
                                    def?.voice == true -> MaterialSymbols.Mic
                                    def?.private == true -> MaterialSymbols.Lock
                                    else -> MaterialSymbols.Tag
                                },
                            revision = revision,
                            accountViewModel = accountViewModel,
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

/** Most-recent message time across a community's [channelKeys] (0 if none), for activity sorting. */
private fun communityActivity(
    communityId: String,
    channelKeys: Set<String>,
): Long =
    channelKeys.maxOfOrNull { key ->
        LocalCache.getConcordChannelIfExists(ConcordChannelId(communityId, key))?.lastNote?.createdAt() ?: 0L
    } ?: 0L

/**
 * The number of a community's channels with a message newer than this account last read there —
 * combines each channel's persisted last-read ([concordChannelLastReadRoute]) against its
 * [com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel.lastNote]. Recomputed on
 * [revision] so a freshly-folded message flips the badge.
 */
@Composable
private fun communityUnreadCount(
    account: Account,
    communityId: String,
    channelKeys: Set<String>,
    revision: Int,
): Int {
    if (channelKeys.isEmpty()) return 0
    val flow =
        remember(communityId, channelKeys, revision) {
            combine(
                channelKeys.map { key ->
                    account.loadLastReadFlow(concordChannelLastReadRoute(communityId, key)).map { lastRead ->
                        val last = LocalCache.getConcordChannelIfExists(ConcordChannelId(communityId, key))?.lastNote?.createdAt() ?: 0L
                        if (last > lastRead) 1 else 0
                    }
                },
            ) { flags -> flags.sum() }
        }
    return flow.collectAsStateWithLifecycle(0).value
}

@Composable
private fun CommunityHeader(
    communityId: String,
    name: String,
    iconPointer: ImagePointer?,
    channelKeys: Set<String>,
    revision: Int,
    expanded: Boolean,
    accountViewModel: AccountViewModel,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val iconModel = rememberConcordImageModel(iconPointer, accountViewModel)
    val unread = communityUnreadCount(accountViewModel.account, communityId, channelKeys, revision)
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
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channelKeys.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.concord_channel_count, channelKeys.size, channelKeys.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (unread > 0) UnreadBadge(unread)
        SymbolIcon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The community's decrypted CORD-02 §6 banner as a hero strip; renders nothing until it resolves. */
@Composable
private fun CommunityBanner(
    banner: ImagePointer,
    accountViewModel: AccountViewModel,
) {
    val model = rememberConcordImageModel(banner, accountViewModel) ?: return
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    RobohashFallbackAsyncImage(
        robot = "",
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(110.dp),
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = false,
        autoPlayGif = autoPlayGif,
    )
}

/** A small pill showing the unread-channel count next to a community. */
@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * One channel row with its last message (author + snippet), relative time, and an unread marker —
 * bold + a dot when there's a message newer than this account last read the channel.
 */
@Composable
private fun ConcordChannelRow(
    communityId: String,
    channelKey: String,
    channelName: String,
    icon: MaterialSymbol,
    revision: Int,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val account = accountViewModel.account
    val channel = remember(communityId, channelKey) { LocalCache.getConcordChannelIfExists(ConcordChannelId(communityId, channelKey)) }
    val lastNote = remember(revision, channel) { channel?.lastNote }
    val lastReadTime by account.loadLastReadFlow(concordChannelLastReadRoute(communityId, channelKey)).collectAsStateWithLifecycle()
    val unread = (lastNote?.createdAt() ?: Long.MIN_VALUE) > lastReadTime

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 40.dp, end = 16.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SymbolIcon(
            symbol = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                channelName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val note = lastNote
            val event = note?.event
            val author = note?.author
            val preview: String? =
                if (author != null && event != null) {
                    val authorName by observeUserName(author, accountViewModel)
                    "$authorName: ${event.content.take(80)}"
                } else {
                    event?.content?.take(80)
                }
            preview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        lastNote?.createdAt()?.let { ts ->
            Text(
                timeAgo(ts, LocalContext.current, prefix = ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unread) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
