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
package com.vitorpamplona.amethyst.commons.relayClient.event.loaders

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun potentialRelaysToFindEvent(
    cache: ICacheProvider,
    note: Note,
): Set<NormalizedRelayUrl> {
    val set = mutableSetOf<NormalizedRelayUrl>()

    set.addAll(cache.relayHints.hintsForEvent(note.idHex))

    note.author?.outboxRelays()?.let { set.addAll(it) }

    cache.getAnyChannel(note)?.relays()?.let { set.addAll(it) }

    note.replyTo?.forEach { parentNote ->
        set.addAll(parentNote.relays)

        cache.getAnyChannel(parentNote)?.relays()?.let { set.addAll(it) }

        parentNote.author?.inboxRelays()?.let { set.addAll(it) }
    }

    note.replies.forEach { childNote ->
        set.addAll(childNote.relays)

        cache.getAnyChannel(childNote)?.relays()?.let { set.addAll(it) }

        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    note.reactions.map { reactionType ->
        reactionType.value.forEach { childNote ->
            set.addAll(childNote.relays)
            childNote.author?.outboxRelays()?.let { set.addAll(it) }
        }
    }

    note.boosts.forEach { childNote ->
        set.addAll(childNote.relays)
        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    note.inGatherers?.forEach { parent ->
        // loads from parent's relays, parent's authors relays and cited authors in the parent note.
        // as well as relays from the channel and other fixed places.
        when (parent) {
            is Note -> {
                set.addAll(parent.relays)
                parent.author?.outboxRelays()?.let { set.addAll(it) }
                parent.author?.inboxRelays()?.let { set.addAll(it) }

                val noteEvent = parent.event
                if (noteEvent is PubKeyHintProvider) {
                    noteEvent.linkedPubKeys().forEach { potentialAuthor ->
                        cache.checkGetOrCreateUser(potentialAuthor)?.let { potentialAuthor ->
                            potentialAuthor.outboxRelays()?.let { set.addAll(it) }
                            potentialAuthor.inboxRelays()?.let { set.addAll(it) }
                        }
                    }
                }
            }

            is Channel -> {
                set.addAll(parent.relays())
            }
        }
    }

    return set
}

fun filterMissingEvents(
    cache: ICacheProvider,
    keys: List<EventFinderQueryState>,
): List<RelayBasedFilter> {
    val eventsPerRelay =
        mapOfSet {
            keys.forEach { key ->
                val default = key.account.followPlusAllMineWithSearchRelays()

                if (key.note !is AddressableNote && key.note.event == null) {
                    potentialRelaysToFindEvent(cache, key.note).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }

                    key.account.searchOnlyRelays().forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }
                }

                // loads threading that is event-based
                key.note.replyTo?.forEach { note ->
                    if (note !is AddressableNote && note.event == null) {
                        potentialRelaysToFindEvent(cache, note).ifEmpty { default }.forEach { relayUrl ->
                            add(relayUrl, note.idHex)
                        }
                    }
                }
            }
        }

    return filterMissingEvents(eventsPerRelay)
}

fun filterMissingEvents(missingEventIds: Map<NormalizedRelayUrl, Set<String>>): List<RelayBasedFilter> {
    if (missingEventIds.isEmpty()) return emptyList()

    return missingEventIds.mapNotNull {
        if (it.value.isNotEmpty()) {
            RelayBasedFilter(
                relay = it.key,
                filter = ExplainedFilter(purpose = SubPurpose.REFERENCED_EVENTS, ids = it.value.sorted()),
            )
        } else {
            null
        }
    }
}
