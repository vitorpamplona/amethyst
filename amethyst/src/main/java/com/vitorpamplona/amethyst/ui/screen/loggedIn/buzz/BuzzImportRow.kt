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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.relayGroupChannelHasUnreadFlow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * One row in the "your channels on this workspace" section: a Buzz workspace channel the user is a
 * member of (via kind-44100). Tapping the card opens the channel ([onOpen]); the trailing Add
 * affordance appends it to the kind-10009 list so it surfaces in Messages / Relay Groups. Reused by
 * the relay group-list screen where Buzz membership discovery is folded in.
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
    val baseChannel = remember(groupId) { LocalCache.getOrCreateRelayGroupChannel(groupId) }
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val name = channel.toBestDisplayName()
    val memberCount = channel.memberCount()

    // An unread dot when this group has chat newer than the last time this account opened it.
    val hasUnread by remember(groupId) {
        relayGroupChannelHasUnreadFlow(accountViewModel.account, groupId)
    }.collectAsStateWithLifecycle(false)

    val content =
        @Composable {
            BuzzImportRowContent(name, groupId.id, memberCount, isAdded, hasUnread, isStarred, onToggleStar, onAdd)
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
    memberCount: Int,
    isAdded: Boolean,
    hasUnread: Boolean,
    isStarred: Boolean,
    onToggleStar: (() -> Unit)?,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BuzzImportAvatar(name = name, seed = seed)
            if (hasUnread) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (memberCount > 0) {
                Text(
                    text = "$memberCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onToggleStar != null) {
            IconButton(onClick = onToggleStar) {
                Icon(
                    symbol = if (isStarred) MaterialSymbols.Star else MaterialSymbols.StarBorder,
                    contentDescription = stringRes(if (isStarred) R.string.buzz_unstar else R.string.buzz_star),
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (isAdded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringRes(R.string.buzz_import_added),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            OutlinedButton(onClick = onAdd) {
                Text(stringRes(R.string.buzz_import_add))
            }
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
        modifier = Modifier.size(40.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
