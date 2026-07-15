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

import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * A reactive flow of how many messages in [communityId]/[channelKey] are newer than the
 * timestamp this account last read that channel. It combines the persisted last-read marker
 * ([concordChannelLastReadRoute]) with the channel's own notes flow, so the count updates both
 * when a fresh message folds in and when the user opens the channel (which advances last-read).
 *
 * Uses `getOrCreate` (not `getIfExists`): a channel folded on the Control Plane may have no
 * message-buffer note yet, and the flow must still exist so the badge appears the moment its
 * first message lands. Counting straight off [ConcordChannel.notes] ignores the synthetic
 * placeholder note (it is never added to that cache) and any note with no timestamp.
 */
fun concordChannelUnreadCountFlow(
    account: Account,
    communityId: String,
    channelKey: String,
): Flow<Int> {
    val channel = LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelKey))
    // The notes flow drives recomputation (each new message re-emits its channel state); the count
    // itself reads the channel's own note store, so it survives the base-typed ChannelState.channel.
    return combine(
        account.loadLastReadFlow(concordChannelLastReadRoute(communityId, channelKey)),
        channel.flow().notes.stateFlow,
    ) { lastRead, _ ->
        channel.newMessagesSince(lastRead)
    }
}

/** The number of this channel's messages created strictly after [sinceSecs] (0 if none). */
private fun ConcordChannel.newMessagesSince(sinceSecs: Long): Int = notes.count { _, note -> (note.createdAt() ?: 0L) > sinceSecs }

/**
 * The pubkeys of the [limit] most-recent distinct posters in this channel, newest first — the
 * facepile shown on a channel row. One O(notes) pass keeps each author's latest post time, so a
 * chatty author counts once (at their newest message) rather than crowding out quieter voices.
 */
fun ConcordChannel.recentAuthorHexes(limit: Int): List<HexKey> {
    val latestByAuthor = HashMap<HexKey, Long>()
    for (note in notes.values()) {
        val author = note.author?.pubkeyHex ?: continue
        val at = note.createdAt() ?: continue
        val prev = latestByAuthor[author]
        if (prev == null || at > prev) latestByAuthor[author] = at
    }
    return latestByAuthor.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
}
