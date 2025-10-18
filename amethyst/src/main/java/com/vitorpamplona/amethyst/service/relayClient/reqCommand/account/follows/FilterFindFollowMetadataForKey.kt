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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.follows

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.mapOfSet

val MetadataOnlyKinds = listOf(MetadataEvent.KIND)
val OutboxOnlyKinds = listOf(AdvertisedRelayListEvent.KIND)
val BothKinds = listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND)

fun filterFindFollowMetadataForKey(
    loadMetadata: Set<User>,
    loadOutbox: Set<User>,
    connectedRelays: Set<NormalizedRelayUrl>,
    indexRelays: Set<NormalizedRelayUrl>,
    searchRelays: Set<NormalizedRelayUrl>,
    allRelays: Set<NormalizedRelayUrl>,
    hasTried: EOSEAccountFast<User>,
): List<RelayBasedFilter> {
    val both = loadMetadata.intersect(loadOutbox)
    val onlyMetadata = loadMetadata - both
    val onlyOutbox = loadOutbox - both

    val perRelayKeysBoth = pickRelaysToLoadUsers(both, connectedRelays, indexRelays, searchRelays, allRelays, hasTried)
    val perRelayKeysOnlyMetadata = pickRelaysToLoadUsers(onlyMetadata, connectedRelays, indexRelays, searchRelays, allRelays, hasTried)
    val perRelayKeysOnlyOutbox = pickRelaysToLoadUsers(onlyOutbox, connectedRelays, indexRelays, searchRelays, allRelays, hasTried)

    return perRelayKeysBoth.mapNotNull {
        val sortedUsers = it.value.sorted()
        if (sortedUsers.isNotEmpty()) {
            RelayBasedFilter(
                relay = it.key,
                filter = Filter(kinds = BothKinds, authors = sortedUsers),
            )
        } else {
            null
        }
    } +
        perRelayKeysOnlyMetadata.mapNotNull {
            val sortedUsers = it.value.sorted()
            if (sortedUsers.isNotEmpty()) {
                RelayBasedFilter(
                    relay = it.key,
                    filter = Filter(kinds = MetadataOnlyKinds, authors = sortedUsers),
                )
            } else {
                null
            }
        } +
        perRelayKeysOnlyOutbox.mapNotNull {
            val sortedUsers = it.value.sorted()
            if (sortedUsers.isNotEmpty()) {
                RelayBasedFilter(
                    relay = it.key,
                    filter = Filter(kinds = OutboxOnlyKinds, authors = sortedUsers),
                )
            } else {
                null
            }
        }
}

fun pickRelaysToLoadUsers(
    users: Set<User>,
    indexRelays: Set<NormalizedRelayUrl>,
    homeRelays: Set<NormalizedRelayUrl>,
    searchRelays: Set<NormalizedRelayUrl>,
    commonRelays: Set<NormalizedRelayUrl>,
    hasTried: EOSEAccountFast<User>,
): Map<NormalizedRelayUrl, Set<HexKey>> =
    mapOfSet {
        users.forEachIndexed { idx, key ->
            val tried = hasTried.since(key)?.keys ?: emptySet()

            val outbox = key.authorRelayList()?.writeRelaysNorm()

            if (outbox != null && outbox.isNotEmpty()) {
                // If there is a home, get from it.

                // if it tried all outbox relays, stop.
                // the UserWatch will take over from here.
                val leftToTry = (outbox - tried)
                leftToTry.forEach {
                    add(it, key.pubkeyHex)
                }
            } else {
                // if not, tries hints first.
                val hints = key.relaysBeingUsed.keys + LocalCache.relayHints.hintsForKey(key.pubkeyHex)

                val leftToTryOnHints = hints - tried

                leftToTryOnHints.forEach {
                    add(it, key.pubkeyHex)
                }

                // if there are only a few hints, broadens the search
                if (leftToTryOnHints.size < 3) {
                    // This creates a pre-deterministic order of the array such that
                    // if this function is called twice, it returns the same arrays
                    // which gets ignored by the relay client if we send it twice
                    val indexRelaysLeftToTry =
                        (indexRelays - tried).sortedBy { relay ->
                            key.pubkeyHex.hashCode() xor relay.url.hashCode()
                        }
                    // This creates a pre-deterministic order of the array such that
                    // if this function is called twice, it returns the same arrays
                    // which gets ignored by the relay client if we send it twice
                    val homeRelaysLeftToTry =
                        (homeRelays - tried).sortedBy { relay ->
                            key.pubkeyHex.hashCode() xor relay.url.hashCode()
                        }

                    // picks one at random to avoid overloading these relays
                    if (users.size > 300) {
                        if (indexRelaysLeftToTry.size >= 2) {
                            add(indexRelaysLeftToTry[0], key.pubkeyHex)
                            add(indexRelaysLeftToTry[1], key.pubkeyHex)
                        } else if (indexRelaysLeftToTry.size == 1) {
                            add(indexRelaysLeftToTry.first(), key.pubkeyHex)
                        }

                        homeRelaysLeftToTry.forEach {
                            add(it, key.pubkeyHex)
                        }
                    } else {
                        indexRelaysLeftToTry.forEach {
                            add(it, key.pubkeyHex)
                        }

                        homeRelaysLeftToTry.forEach {
                            add(it, key.pubkeyHex)
                        }
                    }

                    if (indexRelaysLeftToTry.size < 2) {
                        val searchRelaysLeftToTry = searchRelays - tried

                        searchRelaysLeftToTry.forEach {
                            add(it, key.pubkeyHex)
                        }

                        if (searchRelaysLeftToTry.size < 2) {
                            // This creates a pre-deterministic order of the array such that
                            // if this function is called twice, it returns the same arrays
                            // which gets ignored by the relay client if we send it twice
                            val allRelaysLeftToTry =
                                (commonRelays - tried)
                                    .sortedBy { relay ->
                                        key.pubkeyHex.hashCode() xor relay.url.hashCode()
                                    }.take(100)

                            allRelaysLeftToTry.forEach {
                                add(it, key.pubkeyHex)
                            }
                        }
                    }
                }
            }
        }
    }
