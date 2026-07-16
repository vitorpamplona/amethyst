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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.LocalChatActingIdentities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.LocalChatDisplayNameResolver
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.LocalChatReactOverride
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * A Bitchat-interoperable public geohash location chat: everyone physically (or
 * "teleported") in the same geohash cell shares an ephemeral, relay-broadcast
 * room. Messages are signed with a per-geohash throwaway identity, so posting
 * here does not reveal the account's npub.
 *
 * The room runs the **same** rendering as every other chat — the cell's
 * [GeohashChatChannel] feeds [ChannelFeedViewModel] and renders through the shared
 * [RefreshingChatroomFeedView] (grouping, delivery ticks, reply previews, rich
 * content, reactions, zaps, long-press, swipe, colors, highlighting). The only
 * geohash-specific bits are injected without a second account: the composer
 * (throwaway-identity signing, PoW, teleport / post-as-self) and two
 * composition-locals — [LocalChatActingIdentities] so own messages align/highlight
 * under the per-cell key, and [LocalChatReactOverride] so reactions stay anonymous.
 */
@Composable
fun GeohashChatScreen(
    geohash: String,
    teleported: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadGeohashChannel(geohash, accountViewModel) { channel ->
        GeohashChatRoom(channel, teleported, accountViewModel, nav)
    }
}

@Composable
private fun GeohashChatRoom(
    channel: GeohashChatChannel,
    teleported: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val geohash = channel.geohash
    val composer: GeohashChatViewModel = viewModel(key = "GeohashChat/$geohash")
    composer.init(geohash, accountViewModel, teleported)

    val feedViewModel: ChannelFeedViewModel =
        viewModel(
            key = "geohash:${geohash}ChannelFeedViewModel",
            factory = ChannelFeedViewModel.Factory(channel, accountViewModel.account),
        )

    WatchLifecycleAndUpdateModel(feedViewModel)
    ChannelFilterAssemblerSubscription(channel, accountViewModel.dataSources().channel, accountViewModel)

    val relays by composer.relays.collectAsStateWithLifecycle()
    val myPubKeys by composer.myPubKeys.collectAsStateWithLifecycle()
    val teleporting by composer.teleported.collectAsStateWithLifecycle()
    val postingAsSelf by composer.postAsSelf.collectAsStateWithLifecycle()
    val replyingTo by composer.replyTo.collectAsStateWithLifecycle()

    var nickname by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var showPostAsSelfWarning by remember { mutableStateOf(false) }

    if (showPostAsSelfWarning) {
        AlertDialog(
            onDismissRequest = { showPostAsSelfWarning = false },
            title = { Text("Post as your real account?") },
            text = {
                Text(
                    "Location chat is anonymous by default. If you post as yourself, messages here are " +
                        "signed with your Nostr identity and publicly reveal that you were at this location.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    composer.setPostAsSelf(true)
                    showPostAsSelfWarning = false
                }) { Text("Post as me") }
            },
            dismissButton = {
                TextButton(onClick = { showPostAsSelfWarning = false }) { Text("Cancel") }
            },
        )
    }

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = { GeohashChatTopBar(geohash, relays.size, nav) },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(
                    LocalChatActingIdentities provides myPubKeys,
                    LocalChatReactOverride provides { note, reaction -> composer.react(note, reaction) },
                    LocalChatDisplayNameResolver provides { note ->
                        // Author line = nickname (throwaway keys have no profile) + a ✈ marker for
                        // teleported senders (not physically in the cell). Folded into the name so it
                        // needs no extra renderer seam.
                        val event = note.event as? GeohashChatEvent
                        val nick = event?.nickname()?.takeIf { it.isNotBlank() }
                        val teleported = event?.isTeleported() == true
                        when {
                            nick != null && teleported -> "$nick ✈"
                            nick != null -> nick
                            teleported -> "${note.author?.pubkeyDisplayHex().orEmpty()} ✈"
                            else -> null
                        }
                    },
                ) {
                    RefreshingChatroomFeedView(
                        feedContentState = feedViewModel.feedState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        routeForLastRead = "Channel/geohash:$geohash",
                        onWantsToReply = composer::setReplyTo,
                        onWantsToEditDraft = {},
                    )
                }
            }

            HorizontalDivider()
            replyingTo?.let { parent ->
                GeohashReplyBar(parent, onCancel = { composer.setReplyTo(null) })
            }
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                singleLine = true,
                label = { Text("Nickname (optional)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = teleporting,
                    onClick = { composer.setTeleported(!teleporting) },
                    label = { Text("✈ Teleport") },
                )
                FilterChip(
                    selected = postingAsSelf,
                    onClick = {
                        if (postingAsSelf) composer.setPostAsSelf(false) else showPostAsSelfWarning = true
                    },
                    label = { Text("Post as me") },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message #$geohash") },
                )
                IconButton(
                    enabled = draft.isNotBlank() && relays.isNotEmpty(),
                    onClick = {
                        composer.sendMessage(draft, nickname)
                        draft = ""
                    },
                ) {
                    SymbolIcon(symbol = MaterialSymbols.AutoMirrored.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun GeohashChatTopBar(
    geohash: String,
    relayCount: Int,
    nav: INav,
) {
    TopBarExtensibleWithBackButton(
        title = {
            Column {
                Text("#$geohash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LoadCityName(geohashStr = geohash) { cityName ->
                    Text(
                        "$cityName · $relayCount relays",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        popBack = nav::popBack,
    )
}

@Composable
private fun GeohashReplyBar(
    parent: Note,
    onCancel: () -> Unit,
) {
    val snippet = (parent.event as? GeohashChatEvent)?.content?.take(80).orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Replying: $snippet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
