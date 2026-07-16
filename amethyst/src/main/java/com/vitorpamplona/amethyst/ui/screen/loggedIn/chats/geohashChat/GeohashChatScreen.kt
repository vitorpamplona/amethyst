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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatMessageActionSheet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatReactionChips
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatBubbleLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatGroupPosition
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * A Bitchat-interoperable public geohash location chat: everyone physically (or
 * "teleported") in the same geohash cell shares an ephemeral, relay-broadcast
 * room. Messages are signed with a per-geohash throwaway identity, so posting
 * here does not reveal the account's npub.
 *
 * The message feed is loaded through the **same data path as every other chat**:
 * the cell's [GeohashChatChannel] is populated in LocalCache by the kind-20000
 * subscription that [ChannelFilterAssemblerSubscription] assembles (routed to the
 * geographically-nearest relays), and surfaced by the shared [ChannelFeedViewModel].
 * Only the composer and per-message rendering are geohash-specific — the throwaway
 * identity, the nickname (`n` tag), the teleport marker, and anonymous own-message
 * alignment that the profile-based shared renderer can't express.
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
    val feedState by feedViewModel.feedState.feedContent.collectAsStateWithLifecycle()

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

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("#$geohash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LoadCityName(geohashStr = geohash) { cityName ->
                Text(
                    "$cityName · ${relays.size} relays",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val state = feedState) {
                is FeedState.Loaded ->
                    GeohashMessageList(
                        loaded = state,
                        myPubKeys = myPubKeys,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onReply = composer::setReplyTo,
                    )

                else -> Unit
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

/** A loaded geohash message: the LocalCache [Note] (target of reactions/zaps/replies) plus its parsed event. */
private class GeoMsg(
    val note: Note,
    val event: GeohashChatEvent,
)

@Composable
private fun GeohashMessageList(
    loaded: FeedState.Loaded,
    myPubKeys: Set<String>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onReply: (Note) -> Unit,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val messages =
        remember(items.list) {
            items.list
                .mapNotNull { note -> (note.event as? GeohashChatEvent)?.let { GeoMsg(note, it) } }
                .sortedBy { it.event.createdAt }
        }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
    ) {
        itemsIndexed(messages.asReversed(), key = { _, it -> it.event.id }) { revIndex, msg ->
            val index = messages.size - 1 - revIndex
            GeohashBubble(
                msg = msg,
                position = groupPositionFor(messages, index),
                isMine = msg.event.pubKey in myPubKeys,
                accountViewModel = accountViewModel,
                nav = nav,
                onReply = onReply,
            )
        }
    }
}

@Composable
private fun GeohashBubble(
    msg: GeoMsg,
    position: ChatGroupPosition,
    isMine: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onReply: (Note) -> Unit,
) {
    val message = msg.event
    ChatBubbleLayout(
        isLoggedInUser = isMine,
        isDraft = false,
        innerQuote = false,
        drawAuthorInfo = position.isFirstOfGroup && !isMine,
        groupPosition = position,
        onClick = { false },
        onDoubleTap = {
            // Quick default reaction (same gesture as every other chat surface).
            if (accountViewModel.isWriteable() && accountViewModel.reactionChoices().isNotEmpty()) {
                accountViewModel.reactToOrDelete(msg.note)
            }
        },
        onSwipeReply = { onReply(msg.note) },
        onAuthorClick = {},
        actionMenu = { onDismiss ->
            // The shared sheet: react, zap (Lightning + on-chain + nutzap), reply, copy, report, share.
            // Its own isLoggedUser/isDraft gating hides own-only actions for anonymous authors.
            ChatMessageActionSheet(
                note = msg.note,
                onWantsToReply = onReply,
                onWantsToEditDraft = {},
                onDismiss = onDismiss,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        reactionsRow = { ChatReactionChips(msg.note, accountViewModel, nav) },
        footerRow =
            if (position.isLastOfGroup) {
                { GeohashBubbleFooter(message) }
            } else {
                null
            },
        drawAuthorLine = { GeohashAuthorLine(message) },
    ) { _ ->
        Text(message.content, style = MaterialTheme.typography.bodyLarge)
    }
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

@Composable
private fun GeohashAuthorLine(message: GeohashChatEvent) {
    val name = message.nickname()?.takeIf { it.isNotBlank() } ?: message.pubKey.take(8)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (message.isTeleported()) {
            Text(
                "  ✈",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun GeohashBubbleFooter(message: GeohashChatEvent) {
    val time = remember(message.createdAt) { timeFormat.format(Date(message.createdAt * 1000)) }
    Text(
        time,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private const val GROUP_WINDOW_SECONDS = 10 * 60L

/** Same author within the grouping window continues the bubble run. */
private fun groups(
    newer: GeoMsg,
    older: GeoMsg,
): Boolean = newer.event.pubKey == older.event.pubKey && abs(newer.event.createdAt - older.event.createdAt) <= GROUP_WINDOW_SECONDS

/** Bubble position from time-ordered neighbors (older = earlier, newer = later). */
private fun groupPositionFor(
    messages: List<GeoMsg>,
    index: Int,
): ChatGroupPosition {
    val note = messages[index]
    val older = messages.getOrNull(index - 1)
    val newer = messages.getOrNull(index + 1)
    val connectedAbove = older != null && groups(note, older)
    val connectedBelow = newer != null && groups(newer, note)
    return when {
        connectedAbove && connectedBelow -> ChatGroupPosition.MIDDLE
        connectedAbove -> ChatGroupPosition.BOTTOM
        connectedBelow -> ChatGroupPosition.TOP
        else -> ChatGroupPosition.SINGLE
    }
}
