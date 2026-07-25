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

import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupChatContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * True when this NIP-29 group has at least one chat message newer than the timestamp this account
 * last read it ([relayGroupChannelLastReadRoute]). Reactive: it recombines both when a fresh message
 * folds in (the channel's notes flow ticks) and when the user opens the group (which advances the
 * last-read marker). Only actual group chat content counts (see [isGroupChatContent]) so a trailing
 * reaction/deletion can't stick the dot on; unacceptable (muted/blocked) authors are ignored too.
 */
fun relayGroupChannelHasUnreadFlow(
    account: Account,
    groupId: GroupId,
): Flow<Boolean> {
    val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
    return combine(
        account.loadLastReadFlow(relayGroupChannelLastReadRoute(groupId)),
        channel.flow().notes.stateFlow,
    ) { lastRead, _ ->
        channel.hasChatNewerThan(account, lastRead)
    }
}

/**
 * True when ANY of the account's joined groups on [relay] has unread chat — the unread signal for
 * the collapsed "grouped by relay" Messages row ([RelayGroupServerRoomNote]). It follows the joined
 * list ([RelayGroupListState.liveRelayGroupList]) so a group joined/left on that relay re-subscribes
 * the fan-in, and each group contributes its own [relayGroupChannelHasUnreadFlow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun relayGroupServerHasUnreadFlow(
    account: Account,
    relay: NormalizedRelayUrl,
): Flow<Boolean> =
    account.relayGroupList.liveRelayGroupList
        .flatMapLatest { tags ->
            val groupIds =
                tags.mapNotNull { tag ->
                    if (RelayUrlNormalizer.normalizeOrNull(tag.relayUrl) == relay) GroupId(tag.groupId, relay) else null
                }
            if (groupIds.isEmpty()) {
                flowOf(false)
            } else {
                combine(groupIds.map { relayGroupChannelHasUnreadFlow(account, it) }) { perGroup -> perGroup.any { it } }
            }
        }.distinctUntilChanged()

/** Whether this group's message store holds any acceptable chat content created after [sinceSecs]. */
private fun RelayGroupChannel.hasChatNewerThan(
    account: Account,
    sinceSecs: Long,
): Boolean = newMessagesSince(account, sinceSecs) > 0

/**
 * True for a note the group's chat *timeline* actually renders — an acceptable (not muted/blocked)
 * message whose event is real group-chat content ([isGroupChatContent], which also folds in the Buzz
 * dialect's stream/system/activity rows). Every list-row surface that summarizes a channel — the
 * unread badge ([relayGroupChannelUnreadCountFlow]), the last-message preview + time
 * ([newestTimelineNote]) and the recent-posters facepile ([recentAuthorHexes]) — reuses this so none
 * of them can disagree with the open channel's feed or with the unread dot.
 */
private fun isRelayGroupTimelineMessage(
    note: Note,
    account: Account,
): Boolean = note.event?.isGroupChatContent() == true && account.isAcceptable(note)

/**
 * The newest timeline message in this group (see [isRelayGroupTimelineMessage]), or null if none —
 * the note a list row shows as the channel's "last message". Skips reactions/metadata and hidden
 * authors so the preview matches what the channel feed renders and the unread badge counts.
 */
fun RelayGroupChannel.newestTimelineNote(account: Account): Note? =
    notes
        .filter { _, note -> isRelayGroupTimelineMessage(note, account) }
        .minWithOrNull(Channel.DefaultFeedOrder)

/**
 * The pubkeys of the [limit] most-recent distinct posters in this group, newest first — the facepile
 * shown on a channel row. One O(notes) pass keeps each author's latest post time, so a chatty author
 * counts once (at their newest message) rather than crowding out quieter voices.
 */
fun RelayGroupChannel.recentAuthorHexes(
    account: Account,
    limit: Int,
): List<HexKey> {
    val latestByAuthor = HashMap<HexKey, Long>()
    for (note in notes.values()) {
        if (!isRelayGroupTimelineMessage(note, account)) continue
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

/** The number of this group's timeline messages created strictly after [sinceSecs] (0 if none). */
private fun RelayGroupChannel.newMessagesSince(
    account: Account,
    sinceSecs: Long,
): Int =
    notes.count { _, note ->
        (note.createdAt() ?: 0L) > sinceSecs && isRelayGroupTimelineMessage(note, account)
    }

/**
 * The count of chat messages in [groupId] newer than the timestamp this account last read it — the
 * number the channel-row unread badge shows. Reactive: it recombines both when a fresh message folds
 * in (the channel's notes flow ticks) and when the user opens the group (which advances the last-read
 * marker), so opening a channel clears its badge. Mirrors [relayGroupChannelHasUnreadFlow].
 */
fun relayGroupChannelUnreadCountFlow(
    account: Account,
    groupId: GroupId,
): Flow<Int> {
    val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
    return combine(
        account.loadLastReadFlow(relayGroupChannelLastReadRoute(groupId)),
        channel.flow().notes.stateFlow,
    ) { lastRead, _ ->
        channel.newMessagesSince(account, lastRead)
    }
}
