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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms

import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.mutedChannelIdOf
import com.vitorpamplona.amethyst.model.unreadPrivateChatRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.concordChannelLastReadRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.concordCommunityHasUnreadFlow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.relayGroupChannelLastReadRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.relayGroupServerHasUnreadFlow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.ConcordServerRoomNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.RelayGroupServerRoomNote
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Whether one Messages row is showing a blue dot — the SAME question each row composable answers for
 * itself in [ChatroomHeaderCompose], hoisted so the bottom-bar badge can ask it about every row.
 *
 * The badge used to run on `unreadPrivateChatRoute` alone, which opens with
 * `if (newestMessage !is ChatroomKeyable) return null`. Only NIP-17/NIP-04 DM events implement that
 * interface, so eight of the nine row types — public chats, ephemeral rooms, geohash cells, Marmot
 * groups, NIP-29/Buzz channels and Concord channels, plus both collapsed "grouped" rows — were
 * silently skipped: their row could show a dot while the envelope stayed clean.
 *
 * Returns null when the row cannot be unread at all (no event, my own newest message in a DM, everyone
 * hidden), so callers can skip it rather than subscribe to a flow that is always false. A muted public
 * chat is deliberately NOT one of these cases: mute is a runtime-toggleable setting, not a structural
 * fact about the row, so it is folded into the emitted `Flow<Boolean>` (via [mutedChannelIdOf] combined
 * with the mute set) instead of being resolved as a one-shot snapshot at construction time. Early-return
 * on a snapshot of the mute set would freeze the dot's mute state as of whenever the flow was built —
 * do not "simplify" this back into an early return.
 *
 * The two collapsed rows are why this returns a `Flow<Boolean>` rather than a `(route, createdAt)`
 * pair: their dot is a fan-in over every child channel, not one timestamp against one marker, and
 * approximating them by the newest child would miss an older channel that is still unread.
 */
fun rowHasUnreadFlow(
    row: Note,
    account: Account,
): Flow<Boolean>? {
    // Collapsed rows own a fan-in flow across their children — reuse the row's own signal verbatim.
    if (row is RelayGroupServerRoomNote) return relayGroupServerHasUnreadFlow(account, row.relay)
    if (row is ConcordServerRoomNote) return concordCommunityHasUnreadFlow(account, row.communityId)

    val route = rowLastReadRoute(row, account) ?: return null
    val createdAt = row.createdAt() ?: return null

    val unread = account.settings.getLastReadFlow(route).map { lastReadAt -> createdAt > lastReadAt }

    // Public chats can be silenced at runtime, so the mute set has to be part of the
    // emitted signal rather than a snapshot taken when this flow was built — otherwise
    // toggling mute would not move the dot until something else re-keyed the caller.
    val mutedChannelId = mutedChannelIdOf(row.event) ?: return unread

    return combine(unread, account.settings.mutedPublicChats) { hasUnread, muted ->
        hasUnread && mutedChannelId !in muted
    }
}

/**
 * The last-read marker route behind a row's dot, mirroring what each row composable loads. Channel-type
 * rows are identified by their gatherer (the channel the note was filed into) rather than by event kind,
 * because a Buzz channel and a Concord channel can both carry a kind-9 message.
 */
private fun rowLastReadRoute(
    row: Note,
    account: Account,
): String? {
    row.inGatherers?.forEach { gatherer ->
        when (gatherer) {
            is RelayGroupChannel -> return relayGroupChannelLastReadRoute(gatherer.groupId)
            is ConcordChannel -> return concordChannelLastReadRoute(gatherer.channelId.communityId, gatherer.channelId.channelId)
            is MarmotGroupChatroom -> return marmotGroupLastReadRoute(gatherer.nostrGroupId)
            is GeohashChatChannel -> return "Geohash/${gatherer.geohash}"
            else -> Unit
        }
    }

    return when (val event = row.event) {
        // Same route strings the row composables use — see ChatroomHeaderCompose.
        is ChannelMessageEvent -> event.channelId()?.let { "Channel/$it" }
        is EphemeralChatEvent -> event.roomId()?.let { "Channel/${it.toKey()}" }
        is GeohashChatEvent -> event.geohash()?.let { "Geohash/$it" }
        // DMs keep their own rule: a room whose newest message is mine counts as read.
        else -> unreadPrivateChatRoute(row.event, account.signer.pubKey, account::isAllHidden)?.first
    }
}
