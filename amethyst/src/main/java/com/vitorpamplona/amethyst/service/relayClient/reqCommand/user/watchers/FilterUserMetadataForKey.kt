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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.watchers

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.relationshipStatus.RelationshipStatusEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

val UserMetadataForKeyKinds =
    listOf(
        MetadataEvent.KIND,
        StatusEvent.KIND,
        RelationshipStatusEvent.KIND,
        AdvertisedRelayListEvent.KIND,
        ChatMessageRelayListEvent.KIND,
    )

fun filterUserMetadataForKey(
    authors: Set<HexKey>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays =
        authors
            .map {
                val authorHomeRelayEventAddress = AdvertisedRelayListEvent.createAddressTag(it)
                val authorHomeRelayEvent = (LocalCache.getAddressableNoteIfExists(authorHomeRelayEventAddress)?.event as? AdvertisedRelayListEvent)

                authorHomeRelayEvent?.writeRelaysNorm()
                    ?: LocalCache.relayHints.hintsForKey(it).ifEmpty { null }
                    ?: listOfNotNull(LocalCache.getUserIfExists(it)?.latestMetadataRelay)
            }.flatten()
            .toSet()

    return relays.map {
        RelayBasedFilter(
            relay = it,
            filter =
                Filter(
                    kinds = UserMetadataForKeyKinds,
                    authors = authors.toList(),
                    since = since?.get(it)?.time,
                ),
        )
    }
}
