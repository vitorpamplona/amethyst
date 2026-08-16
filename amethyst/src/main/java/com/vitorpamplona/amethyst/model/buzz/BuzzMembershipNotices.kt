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
package com.vitorpamplona.amethyst.model.buzz

import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.amethyst.commons.model.buzz.ChannelClassification
import com.vitorpamplona.amethyst.commons.model.buzz.MembershipNotice
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.buzz.MembershipNotificationKinds
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberRemovedNotificationEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/*
 * The cache-side view of the Buzz membership stream: everything that reads kind-44100/44101 out of
 * LocalCache instead of asking a relay for its own copy.
 *
 * The relay subscription lives in BuzzMembershipEoseManager, mounted with the rest of the account
 * loaders. Every consumer of the stream — the Notifications feed's invite cards and Buzz DM discovery —
 * observes the cache through here, so there is exactly one `#p=me` REQ per workspace relay for all of
 * them.
 *
 * This is model code, not UI: the notifications DAL reads it from `acceptableEvent`, so it must not
 * live under `ui/`.
 */

/** Every membership verdict addressed to [me], as the cache observers want it. */
fun membershipNoticeFilter(me: HexKey) =
    Filter(
        kinds = MembershipNotificationKinds,
        tags = mapOf("p" to listOf(me)),
    )

/**
 * The workspace relay that vouched for this notice.
 *
 * A note records every relay it was seen on, and a Buzz membership notification is only meaningful on
 * the relay that issued it — the channel UUID it names is that relay's. So prefer a relay we joined as a
 * workspace; fall back to whatever else delivered it, which keeps a notice usable when the workspace set
 * hasn't been restored from disk yet.
 */
private fun Note.membershipRelay(): NormalizedRelayUrl? {
    val seen = relays
    if (seen.isEmpty()) return null
    val workspaces = BuzzWorkspaces.flow.value
    return seen.firstOrNull { it in workspaces } ?: seen.first()
}

/** Flattens a cached kind-44100/44101 into a [MembershipNotice], or null when it isn't usable. */
fun Note.toMembershipNotice(): MembershipNotice? {
    val relay = membershipRelay() ?: return null
    return when (val noteEvent = event) {
        is MemberAddedNotificationEvent ->
            noteEvent.channel()?.let {
                MembershipNotice(noteEvent.id, it, relay, noteEvent.actor(), noteEvent.createdAt, removed = false)
            }

        is MemberRemovedNotificationEvent ->
            noteEvent.channel()?.let {
                MembershipNotice(noteEvent.id, it, relay, noteEvent.actor(), noteEvent.createdAt, removed = true)
            }

        else -> null
    }
}

fun List<Note>.toMembershipNotices(): List<MembershipNotice> = mapNotNull { it.toMembershipNotice() }

/**
 * What [cache] currently knows about a channel's type, from its kind-39000.
 *
 * [ChannelClassification.UNKNOWN] until the directory lands — callers decide what to do with that, and
 * the invite projection deliberately withholds rather than guessing (see
 * [com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvites.pendingInvites]).
 */
fun classifyBuzzChannel(
    cache: LocalCache,
    channelId: String,
    relay: NormalizedRelayUrl,
): ChannelClassification {
    val metadata = cache.getRelayGroupChannelIfExists(GroupId(channelId, relay))?.event ?: return ChannelClassification.UNKNOWN
    return if (metadata.isBuzzDmChannel()) ChannelClassification.DM else ChannelClassification.NAMED
}
