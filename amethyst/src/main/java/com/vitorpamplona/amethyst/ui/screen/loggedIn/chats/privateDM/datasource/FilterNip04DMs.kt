/**
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
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

fun filterNip04DMs(
    group: Set<HexKey>?,
    account: Account?,
    since: SincePerRelayMap?,
): List<RelayBasedFilter>? {
    if (group == null || group.isEmpty() || account == null) return null

    val userOutboxRelays = account.homeRelays.flow.value
    val userInboxRelays = account.dmRelays.flow.value

    val groupOutboxRelays = mutableSetOf<NormalizedRelayUrl>()
    val groupInboxRelays = mutableSetOf<NormalizedRelayUrl>()

    group.forEach {
        val authorHomeRelayEventAddress = AdvertisedRelayListEvent.createAddressTag(it)
        val authorHomeRelayEvent = (LocalCache.getAddressableNoteIfExists(authorHomeRelayEventAddress)?.event as? AdvertisedRelayListEvent)

        val outbox =
            authorHomeRelayEvent?.writeRelaysNorm()
                ?: LocalCache.relayHints.hintsForKey(it).ifEmpty { null }
                ?: listOfNotNull(
                    LocalCache.getUserIfExists(it)?.metadataOrNull()?.relay,
                )

        groupOutboxRelays.addAll(outbox)

        val inbox =
            authorHomeRelayEvent?.readRelaysNorm()?.ifEmpty { null }
                ?: LocalCache.relayHints.hintsForKey(it).ifEmpty { null }
                ?: listOfNotNull(
                    LocalCache.getUserIfExists(it)?.metadataOrNull()?.relay,
                )

        groupInboxRelays.addAll(inbox)
    }

    val toMeRelays = (userInboxRelays + groupOutboxRelays)
    val fromMeRelays = (userOutboxRelays + groupInboxRelays)

    return toMeRelays.map {
        RelayBasedFilter(
            relay = it,
            filter =
                Filter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    authors = group.toList(),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                    since = since?.get(it)?.time,
                ),
        )
    } +
        fromMeRelays.map {
            RelayBasedFilter(
                relay = it,
                filter =
                    Filter(
                        kinds = listOf(PrivateDmEvent.KIND),
                        authors = listOf(account.userProfile().pubkeyHex),
                        tags = mapOf("p" to group.toList()),
                        since = since?.get(it)?.time,
                    ),
            )
        }
}
