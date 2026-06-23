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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.BaseEoseManager
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.follows.pickRelaysToLoadUsers
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * When an AddressableNote stub (event == null) references an author whose NIP-65 relay list has
 * not been loaded yet, [potentialRelaysToFindAddress] returns an empty set and the event is never
 * found. This assembler detects those cases and fetches kind 0 + kind 10002 for the missing
 * authors from indexer/search relays. Once the relay list arrives, [invalidateFilters] re-triggers
 * [NoteEventLoaderSubAssembler], which can now query the author's actual outbox relays.
 */
class AddressableAuthorRelayLoaderSubAssembler(
    client: INostrClient,
    val cache: LocalCache,
    val failureTracker: RelayOfflineTracker,
    allKeys: () -> Set<EventFinderQueryState>,
) : BaseEoseManager<EventFinderQueryState>(client, allKeys) {
    val relayListKinds = listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND)

    var hasTried: EOSEAccountFast<User> = EOSEAccountFast(200)

    val sub =
        requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    forFilters?.forEach { filter ->
                        filter.authors?.forEach { pubKey ->
                            cache.getUserIfExists(pubKey)?.let { user ->
                                hasTried.newEose(user, relay, TimeUtils.now())
                            }
                        }
                    }
                    invalidateFilters()
                }
            },
        )

    override fun updateSubscriptions(keys: Set<EventFinderQueryState>) {
        val newFilters = updateFilter(keys.toList())?.ifEmpty { null }
        sub.updateFilters(newFilters?.groupByRelay())
    }

    fun updateFilter(keys: List<EventFinderQueryState>): List<RelayBasedFilter>? {
        val unknownAuthors = mutableSetOf<User>()
        keys.forEach { key ->
            val note = key.note
            if (note is AddressableNote && note.event == null) {
                val author = cache.getOrCreateUser(note.address.pubKeyHex)
                if (author.authorRelayList() == null) {
                    unknownAuthors.add(author)
                }
            }
        }

        if (unknownAuthors.isEmpty()) return null

        val accounts = keys.mapTo(mutableSetOf()) { it.account }
        val connectedRelays = client.connectedRelaysFlow().value

        val perRelayKeys =
            pickRelaysToLoadUsers(
                unknownAuthors,
                accounts,
                connectedRelays,
                failureTracker.cannotConnectRelays,
                hasTried,
            )

        val activeFilters =
            perRelayKeys.mapNotNull { (relay, pubkeys) ->
                val sorted = pubkeys.sorted()
                if (sorted.isNotEmpty()) {
                    RelayBasedFilter(
                        relay = relay,
                        filter = Filter(kinds = relayListKinds, authors = sorted),
                    )
                } else {
                    null
                }
            }

        val placedPubkeys = perRelayKeys.values.asSequence().flatten().toSet()
        val abandonedPubkeys =
            unknownAuthors.mapNotNullTo(mutableSetOf<HexKey>()) { user ->
                user.pubkeyHex.takeIf { it !in placedPubkeys }
            }

        if (abandonedPubkeys.isEmpty()) return activeFilters

        val sortedAbandoned = abandonedPubkeys.sorted()
        val fallbackRelays = (DefaultIndexerRelayList + DefaultSearchRelayList) - failureTracker.cannotConnectRelays
        val fallbackFilters =
            fallbackRelays.map { relay ->
                RelayBasedFilter(
                    relay = relay,
                    filter = Filter(kinds = relayListKinds, authors = sortedAbandoned),
                )
            }

        return activeFilters + fallbackFilters
    }
}
