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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.buzzTimelinePreviewSummary
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordAuthorFacepile
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordUnreadBadge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.newestTimelineNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.recentAuthorHexes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.relayGroupChannelUnreadCountFlow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/** How many recent-poster avatars a channel row's facepile shows at most. */
private const val FACEPILE_MAX = 4

/** A first screen's worth of recent messages to prefetch per visible card, so previews fill in. */
private const val CARD_WARMUP_LIMIT = 10

/**
 * One channel row in a Buzz workspace's community view: a channel the user is a member of (via
 * kind-44100), rendered like the Concord server view — a colored monogram, the channel name with a
 * recent-posters facepile, a preview of the last message (author + snippet, or the Buzz activity
 * summary for system/diff/job rows), the relative time of that message, and an unread-count badge.
 * Tapping the card opens the channel ([onOpen]); the trailing overflow (3-dot) menu holds the
 * per-channel actions — Pin/Unpin and Add-to-my-list — so the row stays clean.
 *
 * Reused by the relay group-list screen where Buzz membership discovery is folded in.
 */
@Composable
fun BuzzImportRow(
    groupId: GroupId,
    isAdded: Boolean,
    onAdd: () -> Unit,
    accountViewModel: AccountViewModel,
    onOpen: (() -> Unit)? = null,
    isStarred: Boolean = false,
    onToggleStar: (() -> Unit)? = null,
) {
    val account = accountViewModel.account
    val baseChannel = remember(groupId) { LocalCache.getOrCreateRelayGroupChannel(groupId) }

    // Warm a first screen's worth of recent messages while this card is visible (content only — the
    // directory subscription already streams metadata), so the preview + facepile fill in ahead of a
    // tap instead of staying blank until the channel is opened. Bounded to visible rows by the
    // LazyColumn and released as they scroll off.
    RelayGroupCardWarmupSubscription(
        baseChannel,
        accountViewModel.dataSources().relayGroupCardWarmup,
        accountViewModel,
        contentOnly = true,
        contentLimit = CARD_WARMUP_LIMIT,
    )

    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel
    // The channel's own notes flow drives the preview/facepile so they update the moment a message
    // folds in, independent of the metadata-scoped [observeChannel] above.
    val notesState by channel
        .flow()
        .notes.stateFlow
        .collectAsStateWithLifecycle()

    val name = channel.toBestDisplayName()
    val memberCount = channel.memberCount()
    val isPrivate = channel.isPrivate()
    val lastNote = remember(notesState) { channel.newestTimelineNote(account) }
    val faceAuthors = remember(notesState) { channel.recentAuthorHexes(account, FACEPILE_MAX) }
    val unread by
        remember(groupId) { relayGroupChannelUnreadCountFlow(account, groupId) }
            .collectAsStateWithLifecycle(0)
    val hasUnread = unread > 0

    val content =
        @Composable {
            BuzzImportRowContent(
                name = name,
                seed = groupId.id,
                isPrivate = isPrivate,
                memberCount = memberCount,
                lastNote = lastNote,
                faceAuthors = faceAuthors,
                unread = unread,
                hasUnread = hasUnread,
                isAdded = isAdded,
                isStarred = isStarred,
                onToggleStar = onToggleStar,
                onAdd = onAdd,
                accountViewModel = accountViewModel,
            )
        }
    if (onOpen != null) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun BuzzImportRowContent(
    name: String,
    seed: String,
    isPrivate: Boolean,
    memberCount: Int,
    lastNote: Note?,
    faceAuthors: List<String>,
    unread: Int,
    hasUnread: Boolean,
    isAdded: Boolean,
    isStarred: Boolean,
    onToggleStar: (() -> Unit)?,
    onAdd: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuzzImportAvatar(name = name, seed = seed)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Line 1: a lock (private), the channel name, a pin marker (starred), and the recent-
            // posters facepile pushed to the right.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isPrivate) {
                    Icon(
                        symbol = MaterialSymbols.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isStarred) {
                    Icon(
                        symbol = MaterialSymbols.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                ConcordAuthorFacepile(faceAuthors, accountViewModel)
            }
            // Line 2: the last-message preview, then the time + unread badge.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    BuzzChannelPreviewLine(lastNote, memberCount, accountViewModel)
                }
                lastNote?.createdAt()?.let { ts ->
                    Text(
                        timeAgo(ts, LocalContext.current, prefix = ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                ConcordUnreadBadge(unread)
            }
        }
        BuzzChannelRowMenu(
            isAdded = isAdded,
            onAdd = onAdd,
            isStarred = isStarred,
            onToggleStar = onToggleStar,
        )
    }
}

/**
 * The line under a channel name: the last message's author + a snippet ("author: hello"), the Buzz
 * activity summary for a system/diff/job row, or — before anything has folded in — the member count
 * (or a muted "No messages yet"). Author names resolve reactively (hex → profile name).
 */
@Composable
private fun BuzzChannelPreviewLine(
    lastNote: Note?,
    memberCount: Int,
    accountViewModel: AccountViewModel,
) {
    val event = lastNote?.event
    val author = lastNote?.author
    // A Buzz timeline row (system line, huddle/job activity, diff) carries JSON/diff in its content,
    // so show its human-readable summary — the same text the in-chat row renders — rather than
    // "author: {json}". A plain chat message falls through to the usual "author: message" framing.
    val summary = remember(event) { event?.let { buzzTimelinePreviewSummary(it) } }
    val preview: String =
        when {
            summary != null -> summary
            event != null && author != null -> {
                val authorName by observeUserName(author, accountViewModel)
                val body = event.content.take(80)
                if (body.isBlank()) authorName else "$authorName: $body"
            }
            event != null -> event.content.take(80)
            memberCount > 0 -> pluralStringResource(R.plurals.relay_group_member_count, memberCount, memberCount)
            else -> stringRes(R.string.relay_group_no_messages_yet)
        }
    Text(
        preview,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The per-channel overflow (3-dot) menu: Pin/Unpin and Add-to-my-list. Moved off the row itself so a
 * channel card reads as a clean Concord-style row, with its actions one tap behind the kebab.
 */
@Composable
private fun BuzzChannelRowMenu(
    isAdded: Boolean,
    onAdd: () -> Unit,
    isStarred: Boolean,
    onToggleStar: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                symbol = MaterialSymbols.MoreVert,
                contentDescription = stringRes(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onToggleStar != null) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            symbol = MaterialSymbols.PushPin,
                            contentDescription = null,
                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    text = { Text(stringRes(if (isStarred) R.string.buzz_unpin else R.string.buzz_pin)) },
                    onClick = {
                        expanded = false
                        onToggleStar()
                    },
                )
            }
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        symbol = if (isAdded) MaterialSymbols.Check else MaterialSymbols.Add,
                        contentDescription = null,
                        tint = if (isAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                text = { Text(stringRes(if (isAdded) R.string.buzz_import_added else R.string.buzz_import_add)) },
                enabled = !isAdded,
                onClick = {
                    expanded = false
                    onAdd()
                },
            )
        }
    }
}

/** A round monogram whose color is derived deterministically from the channel id. */
@Composable
private fun BuzzImportAvatar(
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
        modifier = Modifier.size(44.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
