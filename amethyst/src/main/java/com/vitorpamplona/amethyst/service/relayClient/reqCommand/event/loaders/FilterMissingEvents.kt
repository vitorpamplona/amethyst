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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.mapOfSet

fun potentialRelaysToFindEvent(note: Note): Set<NormalizedRelayUrl> {
    val set = mutableSetOf<NormalizedRelayUrl>()

    set.addAll(LocalCache.relayHints.hintsForEvent(note.idHex))

    val outboxFromNote = note.author?.outboxRelays()
    if (outboxFromNote != null) {
        set.addAll(outboxFromNote)
    } else {
        val hintAuthor = LocalCache.quotedAuthorHints.get(note.idHex)
        if (hintAuthor != null) {
            LocalCache.checkGetOrCreateUser(hintAuthor)?.outboxRelays()?.let { set.addAll(it) }
        }
    }

    LocalCache.getAnyChannel(note)?.relays()?.let { set.addAll(it) }

    note.replyTo?.forEach { parentNote ->
        set.addAll(parentNote.relays)

        LocalCache.getAnyChannel(parentNote)?.relays()?.let { set.addAll(it) }

        parentNote.author?.inboxRelays()?.let { set.addAll(it) }
    }

    note.replies.forEach { childNote ->
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

    note.boosts.forEach { childNote ->
        set.addAll(childNote.relays)
        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    return set
}

fun filterMissingEvents(keys: List<EventFinderQueryState>): List<RelayBasedFilter> {
    val eventsPerRelay =
        mapOfSet {
            keys.forEach { key ->
                val default = key.account.followPlusAllMineWithSearch.flow.value

                if (key.note !is AddressableNote && key.note.event == null) {
                    potentialRelaysToFindEvent(key.note).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }

                    key.account.searchRelayList.flow.value.forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }
                }

                // loads threading that is event-based
                key.note.replyTo?.forEach { note ->
                    if (note !is AddressableNote && note.event == null) {
                        potentialRelaysToFindEvent(note).ifEmpty { default }.forEach { relayUrl ->
                            add(relayUrl, note.idHex)
                        }
                    }
                }
            }
        }

    return filterMissingEvents(eventsPerRelay) + filterMissingQuotedAuthorNip65(keys.asSequence().map { it.note })
}

// When a missing-event note has a quoted-author hint but the hinted author's
// NIP-65 hasn't resolved to a usable outbox yet, emit a parallel kind-10002
// filter against the default indexer + search relays. The hint alone is
// useless until outboxRelays() can resolve — this is what closes the loop
// for Momostr-bridged quoted notes whose author writes only to
// relay.momostr.pink.
//
// The gate matches the consumer ([potentialRelaysToFindEvent] reads
// [outboxRelays] not [authorRelayList]): a read-only NIP-65 satisfies
// `authorRelayList() != null` but yields no write relays, so the gate has
// to track the same null-or-empty outbox condition the consumer sees.
//
// Retry suppression: NoteEventLoaderSubAssembler uses invalidateAfterEose,
// so updateFilter runs on every EOSE — without backoff the same kind-10002
// fan-out fires forever for bridged authors whose NIP-65 isn't on the
// default indexer/search relays. We record per-author last-attempt time
// and skip authors attempted within [NIP65_RETRY_BACKOFF_SECONDS].
private const val NIP65_RETRY_BACKOFF_SECONDS = 300L

fun filterMissingQuotedAuthorNip65(notes: Sequence<Note>): List<RelayBasedFilter> {
    val authors = mutableSetOf<HexKey>()
    notes.forEach { note ->
        if (note !is AddressableNote && note.event == null) {
            LocalCache.quotedAuthorHints.get(note.idHex)?.let { authors.add(it) }
        }
    }
    return emitNip65BackfillForUnresolvedAuthors(authors)
}

// The addressable counterpart: an unresolved AddressableNote already carries
// its author in the address (kind:pubkey:d), but the outbox lookup in
// [potentialRelaysToFindAddress] still depends on the author's NIP-65 being
// cached. When it isn't, the fetch falls back to the user's own write
// relays and misses bridged / long-tail authors. This emits the same
// kind-10002 backfill we use for event-id quotes, sharing the throttle
// (LocalCache.quotedAuthorNip65Attempts) so a single session can't fan out
// indefinitely against indexer/search relays.
fun filterMissingAddressableAuthorNip65(notes: Sequence<Note>): List<RelayBasedFilter> {
    val authors = mutableSetOf<HexKey>()
    notes.forEach { note ->
        if (note is AddressableNote && note.event == null) {
            authors.add(note.address.pubKeyHex)
        }
    }
    return emitNip65BackfillForUnresolvedAuthors(authors)
}

private fun emitNip65BackfillForUnresolvedAuthors(authors: Set<HexKey>): List<RelayBasedFilter> {
    if (authors.isEmpty()) return emptyList()
    val now = TimeUtils.now()
    val needed =
        authors.filterTo(mutableSetOf()) {
            LocalCache.getUserIfExists(it)?.outboxRelays().isNullOrEmpty() &&
                shouldRetryNip65Fetch(it, now)
        }
    if (needed.isEmpty()) return emptyList()

    needed.forEach { LocalCache.quotedAuthorNip65Attempts.put(it, now) }

    val sorted = needed.sorted()
    val relays = (DefaultIndexerRelayList + DefaultSearchRelayList)
    return relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter = Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = sorted),
        )
    }
}

private fun shouldRetryNip65Fetch(
    author: HexKey,
    nowSec: Long,
): Boolean {
    val last = LocalCache.quotedAuthorNip65Attempts.get(author) ?: return true
    return nowSec - last >= NIP65_RETRY_BACKOFF_SECONDS
}

fun filterMissingEvents(missingEventIds: Map<NormalizedRelayUrl, Set<String>>): List<RelayBasedFilter> {
    if (missingEventIds.isEmpty()) return emptyList()

    return missingEventIds.mapNotNull {
        if (it.value.isNotEmpty()) {
            RelayBasedFilter(
                relay = it.key,
                filter = Filter(ids = it.value.sorted()),
            )
        } else {
            null
        }
    }
}
