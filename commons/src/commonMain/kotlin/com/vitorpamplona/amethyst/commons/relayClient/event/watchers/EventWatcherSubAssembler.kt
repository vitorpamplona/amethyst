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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.FilterShape
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

class EventWatcherSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<EventFinderQueryState>,
) : SingleSubEoseManager<EventFinderQueryState>(client, allKeys) {
    var lastNotesOnFilter = emptyList<Note>()

    /**
     * Each watched note keeps its own cursors ([Note.childCursors]), because it holds the replies,
     * reactions and zaps they vouch for. They used to live here, by note id, so a note LocalCache
     * had dropped and rebuilt (after an account switch away and back, with none of its children)
     * kept the old cursor and was only asked for what came after it: jack's top note showed 5
     * replies and 36 likes instead of 119 and 424, and stayed there.
     *
     * An answer reaches a note only for filters that name it, and only when the filter asked from
     * scratch or from no later than the note's own cursor: a REQ planned for the object it replaced
     * fetched only what that one lacked.
     */
    override fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        val live = filters?.filter { it.until == null }
        if (!live.isNullOrEmpty()) {
            val watched = lastNotesOnFilter.associateBy { it.idHex }
            val answered = mutableMapOf<Note, MutableList<Filter>>()
            val refused = mutableSetOf<Note>()
            live.forEach { filter ->
                filter.tags?.forEach { (tag, values) ->
                    if (tag !in WATCHED_TAGS) return@forEach
                    values.forEach { id ->
                        val note = watched[id] ?: return@forEach
                        val since = filter.since
                        val own = note.childCursorsOrNull()?.since(relay, cursorSource)
                        if (since == null || (own != null && own >= since)) {
                            answered.getOrPut(note) { mutableListOf() }.add(filter)
                        } else {
                            refused.add(note)
                        }
                    }
                }
            }
            answered.forEach { (note, asked) ->
                if (note !in refused) note.childCursors().newEose(relay, asked.map { FilterShape.of(it) }, time, cursorSource)
            }
        }
        super.newEose(relay, time, filters)
    }

    private fun sinceByRelay(note: Note) = note.childCursorsOrNull()?.sinceByRelay(cursorSource).orEmpty()

    override fun updateFilter(
        keys: List<EventFinderQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (keys.isEmpty()) {
            // Nothing is watched: let go of the last batch rather than holding it until the next.
            lastNotesOnFilter = emptyList()
            return null
        }

        lastNotesOnFilter = keys.map { it.note }

        // Attributed only when one account is watching. The notes here are whatever is rendered, not
        // anything an account owns, so with several accounts active no single one is the honest owner —
        // and splitting would re-request the same replies/zaps once per account.
        // Deduped by pubkey, not by `Account`: that class uses identity equality, so two objects for
        // the same logged-in user would look like two accounts and suppress attribution entirely.
        val soleAccountPubKey =
            keys
                .mapTo(mutableSetOf()) { it.account.userFinderPubkeyHex }
                .singleOrNull()

        return groupByRelayPresence(lastNotesOnFilter)
            .map { group ->
                if (group.isNotEmpty()) {
                    val addressables = group.filterIsInstance<AddressableNote>()
                    val events = group.mapNotNull { if (it !is AddressableNote) it else null }

                    listOfNotNull(
                        filterRepliesAndReactionsToNotes(events, findMinimumEOSEs(events), soleAccountPubKey),
                        filterRepliesAndReactionsToAddresses(addressables, findMinimumEOSEs(addressables), soleAccountPubKey),
                        filterNewVersionsOfAddresses(addressables),
                    ).flatten()
                } else {
                    emptyList()
                }
            }.flatten()
    }

    override fun distinct(key: EventFinderQueryState) = key.note

    /** Notes caught up on the same relays share filters; the rest are asked apart. */
    fun groupByRelayPresence(notes: Iterable<Note>): Collection<List<Note>> =
        notes
            .groupBy { sinceByRelay(it).keys }
            .values
            .map {
                // important to keep in order otherwise the Relay thinks the filter has changed and we REQ again
                it.sortedBy { it.idHex }
            }

    /** Per relay, the oldest cursor among [notes]: the window that misses nothing for any of them. */
    fun findMinimumEOSEs(notes: List<Note>): SincePerRelayMap {
        val minLatestEOSEs = mutableMapOf<NormalizedRelayUrl, MutableTime>()

        notes.forEach { note ->
            sinceByRelay(note).forEach { (relay, time) ->
                val minEose = minLatestEOSEs[relay]
                if (minEose == null) {
                    minLatestEOSEs[relay] = MutableTime(time)
                } else {
                    minEose.updateIfOlder(time)
                }
            }
        }

        return minLatestEOSEs
    }

    companion object {
        // The tags the reply, reaction and zap filters name a watched note by: its id or address.
        private val WATCHED_TAGS = setOf("e", "E", "q", "a", "A")
    }
}
