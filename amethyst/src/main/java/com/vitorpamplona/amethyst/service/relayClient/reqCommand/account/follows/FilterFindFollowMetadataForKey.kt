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

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Constants
import com.vitorpamplona.amethyst.model.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.model.DefaultSearchRelayList
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlin.collections.ifEmpty

fun pickRelaysToLoadUsers(
    users: Set<User>,
    accounts: Collection<Account>,
    connected: Set<NormalizedRelayUrl>,
    cannotConnectRelays: Set<NormalizedRelayUrl>,
    hasTried: EOSEAccountFast<User>,
): Map<NormalizedRelayUrl, Set<HexKey>> {
    val indexRelays = mutableSetOf<NormalizedRelayUrl>()
    val homeRelays = mutableSetOf<NormalizedRelayUrl>()
    val searchRelays = mutableSetOf<NormalizedRelayUrl>()
    val commonRelays = mutableSetOf<NormalizedRelayUrl>()

    accounts.forEach { key ->
        indexRelays.addAll(
            key.indexerRelayList.flow.value
                .ifEmpty { DefaultIndexerRelayList },
        )

        homeRelays.addAll(key.nip65RelayList.allFlowNoDefaults.value)
        homeRelays.addAll(key.privateStorageRelayList.flow.value)
        homeRelays.addAll(key.localRelayList.flow.value)

        searchRelays.addAll(key.trustedRelayList.flow.value)
        searchRelays.addAll(
            key.searchRelayList.flow.value
                .ifEmpty { DefaultSearchRelayList },
        )

        // uses followShared to ignore personal relays when finding users.
        commonRelays.addAll(
            key.followSharedOutboxesOrProxy.flow.value
                .ifEmpty { Constants.eventFinderRelays },
        )
    }

    return pickRelaysToLoadUsers(
        users,
        indexRelays - cannotConnectRelays,
        homeRelays - cannotConnectRelays,
        searchRelays - cannotConnectRelays,
        connected,
        commonRelays - cannotConnectRelays,
        hasTried,
    )
}

fun pickRelaysToLoadUsers(
    users: Set<User>,
    indexRelays: Set<NormalizedRelayUrl>,
    homeRelays: Set<NormalizedRelayUrl>,
    searchRelays: Set<NormalizedRelayUrl>,
    connected: Set<NormalizedRelayUrl>,
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

                        val connectedRelaysLeftToTry =
                            (connected - tried)
                                .sortedBy { relay ->
                                    key.pubkeyHex.hashCode() xor relay.url.hashCode()
                                }.take(100)

                        // picks one at random to avoid overloading these relays
                        if (users.size > 300) {
                            connectedRelaysLeftToTry.take(20).forEach {
                                add(it, key.pubkeyHex)
                            }
                        } else {
                            connectedRelaysLeftToTry.forEach {
                                add(it, key.pubkeyHex)
                            }
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
