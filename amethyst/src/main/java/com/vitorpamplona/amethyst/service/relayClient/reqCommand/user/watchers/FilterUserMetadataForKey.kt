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
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.quartz.experimental.relationshipStatus.ContactCardEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.mapOfSet

val UserMetadataForKeyKinds =
    listOf(
        MetadataEvent.KIND,
        StatusEvent.KIND,
        ContactCardEvent.KIND,
        AdvertisedRelayListEvent.KIND,
        ChatMessageRelayListEvent.KIND,
    )

fun filterUserMetadataForKey(
    authors: Set<User>,
    indexRelays: Set<NormalizedRelayUrl>,
    since: EOSEAccountFast<User>,
): List<RelayBasedFilter> {
    val perRelayUsers =
        mapOfSet {
            authors.forEach { key ->
                val relays =
                    key.outboxRelays()
                        ?: (key.relaysBeingUsed.keys + LocalCache.relayHints.hintsForKey(key.pubkeyHex) + indexRelays)

                relays.forEach {
                    add(it, key)
                }
            }
        }

    return perRelayUsers
        .map { (relay, users) ->
            val firstTimers = mutableSetOf<HexKey>()
            val updates = mutableSetOf<HexKey>()

            var minimumTime: Long = Long.MAX_VALUE

            users.forEach { user ->
                val time = since.since(user)?.get(relay)?.time
                if (time == null || user.latestMetadata == null) {
                    firstTimers.add(user.pubkeyHex)
                } else {
                    updates.add(user.pubkeyHex)
                    if (time < minimumTime) {
                        minimumTime = time
                    }
                }
            }

            listOfNotNull(
                if (firstTimers.isNotEmpty()) {
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            Filter(
                                kinds = UserMetadataForKeyKinds,
                                authors = firstTimers.sorted(),
                            ),
                    )
                } else {
                    null
                },
                if (updates.isNotEmpty()) {
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            Filter(
                                kinds = UserMetadataForKeyKinds,
                                authors = updates.sorted(),
                                since = minimumTime,
                            ),
                    )
                } else {
                    null
                },
            )
        }.flatten()
}
