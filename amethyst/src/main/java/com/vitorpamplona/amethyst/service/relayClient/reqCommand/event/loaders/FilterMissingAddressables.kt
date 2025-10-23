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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun potentialRelaysToFindAddress(note: AddressableNote): Set<NormalizedRelayUrl> {
    val set = mutableSetOf<NormalizedRelayUrl>()

    LocalCache.getOrCreateUser(note.address.pubKeyHex).outboxRelays()?.let {
        set.addAll(it)
    }

    set.addAll(LocalCache.relayHints.hintsForAddress(note.idHex))

    LocalCache.getAnyChannel(note)?.relays()?.let { set.addAll(it) }

    note.replyTo?.map { parentNote ->
        set.addAll(parentNote.relays)

        LocalCache.getAnyChannel(parentNote)?.relays()?.let { set.addAll(it) }

        parentNote.author?.inboxRelays()?.let { set.addAll(it) }
    }

    note.replies.map { childNote ->
        set.addAll(childNote.relays)

        LocalCache.getAnyChannel(childNote)?.relays()?.let { set.addAll(it) }

        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    note.reactions.map { reactionType ->
        reactionType.value.forEach { childNote ->
            set.addAll(childNote.relays)
            childNote.author?.outboxRelays()?.let { set.addAll(it) }
        }
    }

    note.boosts.map { childNote ->
        set.addAll(childNote.relays)
        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    return set
}

fun filterMissingAddressables(keys: List<EventFinderQueryState>): List<RelayBasedFilter>? {
    val addressesPerRelay =
        mapOfSet {
            keys.forEach { key ->
                val default = key.account.followPlusAllMineWithSearch.flow.value
                if (key.note is AddressableNote && key.note.event == null) {
                    potentialRelaysToFindAddress(key.note).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, key.note.address)
                    }
                }

                // loads threading that is event-based
                key.note.replyTo?.forEach { note ->
                    if (note is AddressableNote && note.event == null) {
                        potentialRelaysToFindAddress(note).ifEmpty { default }.forEach { relayUrl ->
                            add(relayUrl, note.address)
                        }
                    }
                }
            }
        }

    return filterMissingAddressables(addressesPerRelay)
}

fun filterMissingAddressables(missingAddressables: Map<NormalizedRelayUrl, Set<Address>>): List<RelayBasedFilter> {
    if (missingAddressables.isEmpty()) return emptyList()

    return missingAddressables.flatMap { relayEntry ->
        relayEntry.value.map { address ->
            if (address.kind < 25000 && address.dTag.isBlank()) {
                RelayBasedFilter(
                    relay = relayEntry.key,
                    filter =
                        Filter(
                            kinds = listOf(address.kind),
                            authors = listOf(address.pubKeyHex),
                            limit = 1,
                        ),
                )
            } else {
                RelayBasedFilter(
                    relay = relayEntry.key,
                    filter =
                        Filter(
                            kinds = listOf(address.kind),
                            tags = mapOf("d" to listOf(address.dTag)),
                            authors = listOf(address.pubKeyHex),
                            limit = 1,
                        ),
                )
            }
        }
    }
}
