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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/** The two relay sets a conversation's NIP-04 DMs flow over, resolved via the outbox model. */
class Nip04DmRelays(
    val toMeRelays: Set<NormalizedRelayUrl>,
    val fromMeRelays: Set<NormalizedRelayUrl>,
) {
    val all: Set<NormalizedRelayUrl> get() = toMeRelays + fromMeRelays
}

/**
 * Resolves where a conversation's NIP-04 DMs flow: messages **to me** arrive on my inbox + the
 * group's outbox relays; messages **from me** arrive on my outbox + the group's inbox relays.
 */
fun nip04DMRelays(
    group: Set<HexKey>?,
    account: Account?,
): Nip04DmRelays? {
    if (group.isNullOrEmpty() || account == null) return null

    val userOutboxRelays = account.homeRelays.flow.value
    val userInboxRelays = account.dmRelays.flow.value

    val groupOutboxRelays = mutableSetOf<NormalizedRelayUrl>()
    val groupInboxRelays = mutableSetOf<NormalizedRelayUrl>()

    group.forEach {
        val authorHomeRelayEventAddress = AdvertisedRelayListEvent.createAddressTag(it)
        val authorHomeRelayEvent = (LocalCache.getAddressableNoteIfExists(authorHomeRelayEventAddress)?.event as? AdvertisedRelayListEvent)

        val outbox =
            authorHomeRelayEvent?.writeRelaysNorm()
                ?: LocalCache.getUserIfExists(it)?.allUsedRelaysOrNull()
                ?: LocalCache.relayHints.hintsForKey(it).ifEmpty { null }
                ?: emptyList()

        groupOutboxRelays.addAll(outbox)

        val inbox =
            authorHomeRelayEvent?.readRelaysNorm()?.ifEmpty { null }
                ?: LocalCache.getUserIfExists(it)?.allUsedRelaysOrNull()
                ?: LocalCache.relayHints.hintsForKey(it).ifEmpty { null }
                ?: emptyList()

        groupInboxRelays.addAll(inbox)
    }

    return Nip04DmRelays(
        toMeRelays = userInboxRelays + groupOutboxRelays,
        fromMeRelays = userOutboxRelays + groupInboxRelays,
    )
}

private fun toMeFilter(
    relay: NormalizedRelayUrl,
    group: Set<HexKey>,
    account: Account,
    since: Long?,
    until: Long?,
    limit: Int?,
) = RelayBasedFilter(
    relay = relay,
    filter =
        Filter(
            kinds = listOf(PrivateDmEvent.KIND),
            authors = group.toList(),
            tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
            since = since,
            until = until,
            limit = limit,
        ),
)

private fun fromMeFilter(
    relay: NormalizedRelayUrl,
    group: Set<HexKey>,
    account: Account,
    since: Long?,
    until: Long?,
    limit: Int?,
) = RelayBasedFilter(
    relay = relay,
    filter =
        Filter(
            kinds = listOf(PrivateDmEvent.KIND),
            authors = listOf(account.userProfile().pubkeyHex),
            tags = mapOf("p" to group.toList()),
            since = since,
            until = until,
            limit = limit,
        ),
)

/** Live-tail filters: everything since [windowStart], open-ended at the top (new messages keep arriving). */
fun filterNip04DMs(
    group: Set<HexKey>?,
    account: Account?,
    windowStart: Long,
): List<RelayBasedFilter>? {
    if (group.isNullOrEmpty() || account == null) return null
    val relays = nip04DMRelays(group, account) ?: return null
    return relays.toMeRelays.map { toMeFilter(it, group, account, since = windowStart, until = null, limit = null) } +
        relays.fromMeRelays.map { fromMeFilter(it, group, account, since = windowStart, until = null, limit = null) }
}

/**
 * History filters: a bounded backward page per relay. Each relay is asked for [limit] events older
 * than [untilFor]`(relay)` (no `since`), so it can be paged down to empty independently.
 */
fun filterNip04DMsHistory(
    group: Set<HexKey>,
    account: Account,
    relays: Nip04DmRelays,
    limit: Int,
    untilFor: (NormalizedRelayUrl) -> Long?,
): List<RelayBasedFilter> =
    relays.toMeRelays.map { toMeFilter(it, group, account, since = null, until = untilFor(it), limit = limit) } +
        relays.fromMeRelays.map { fromMeFilter(it, group, account, since = null, until = untilFor(it), limit = limit) }
