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
package com.vitorpamplona.quartz.nip65RelayList

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion
import com.vitorpamplona.quartz.utils.mapOfSet

class RelayListRecommendationProcessor {
    companion object {
        fun transpose(
            userList: Map<HexKey, Set<NormalizedRelayUrl>>,
            ignore: Set<NormalizedRelayUrl> = setOf(),
        ): Map<NormalizedRelayUrl, Set<HexKey>> =
            mapOfSet {
                userList.forEach { event ->
                    event.value.forEach { relay ->
                        if (relay !in ignore) {
                            add(relay, event.key)
                        }
                    }
                }
            }

        /**
         * filter onion and local host from write relays
         * for each user pubkey, a list of valid relays.
         */
        private fun filterValidRelays(
            userList: List<AdvertisedRelayListEvent>,
            hasOnionConnection: Boolean = false,
        ): Map<HexKey, Set<NormalizedRelayUrl>> =
            mapOfSet {
                userList.forEach { event ->
                    event.writeRelaysNorm()?.forEach { relay ->
                        if (!relay.isLocalHost() && (hasOnionConnection || !relay.isOnion())) {
                            add(event.pubKey, relay)
                        }
                    }
                }
            }

        fun reliableRelaySetFor(
            usersAndRelays: Map<HexKey, Set<NormalizedRelayUrl>>,
            relayUrlsToIgnore: Set<NormalizedRelayUrl> = emptySet(),
        ): Set<RelayRecommendation> {
            // ignores users that are already being served by the list.
            val usersToServeInTheFirstRound =
                usersAndRelays
                    .filter {
                        it.value.none {
                            it in relayUrlsToIgnore
                        }
                    }.toMutableMap()

            // returning this list.
            val selected = relayUrlsToIgnore.toMutableSet()
            val returningSet = mutableSetOf<RelayRecommendation>()

            do {
                // transposes the map so that we get a map of relays with users using them
                val popularity = transpose(usersToServeInTheFirstRound, selected)

                if (popularity.isEmpty()) {
                    // this happens when there are no relay options left for certain pubkeys
                    break
                }

                // chooses the most popular relay
                val mostPopularRelay = popularity.maxBy { it.value.size }
                selected.add(mostPopularRelay.key)
                returningSet.add(RelayRecommendation(mostPopularRelay.key, true, mostPopularRelay.value))

                // removes all users that we can now download posts from
                mostPopularRelay.value.forEach {
                    usersToServeInTheFirstRound.remove(it)
                }
            } while (usersToServeInTheFirstRound.isNotEmpty())

            // make a second pass to make sure each user is supported by at least 2 relays.
            val usersServedByOnlyOneRelay =
                usersAndRelays.filterTo(mutableMapOf()) { keyPair ->
                    keyPair.value.count {
                        it in selected
                    } < 2
                }

            do {
                // transposes the map so that we get a map of relays with users using them
                val popularity = transpose(usersServedByOnlyOneRelay, selected)

                if (popularity.isEmpty()) {
                    // this happens when there are no relay options left for certain pubkeys
                    break
                }

                // chooses the most popular relay
                val mostPopularRelay = popularity.maxBy { it.value.size }

                selected.add(mostPopularRelay.key)
                returningSet.add(RelayRecommendation(mostPopularRelay.key, false, mostPopularRelay.value))

                // removes all users that we can now download posts from
                mostPopularRelay.value.forEach {
                    usersServedByOnlyOneRelay.remove(it)
                }
            } while (usersServedByOnlyOneRelay.isNotEmpty())

            return returningSet
        }

        fun reliableRelaySetFor(
            userList: List<AdvertisedRelayListEvent>,
            relayUrlsToIgnore: Set<NormalizedRelayUrl> = emptySet(),
            hasOnionConnection: Boolean = false,
        ): Set<RelayRecommendation> =
            reliableRelaySetFor(
                filterValidRelays(userList, hasOnionConnection),
                relayUrlsToIgnore,
            )
    }

    class RelayRecommendation(
        val relay: NormalizedRelayUrl,
        val requiredToNotMissEvents: Boolean,
        val users: Set<HexKey>,
    )
}
