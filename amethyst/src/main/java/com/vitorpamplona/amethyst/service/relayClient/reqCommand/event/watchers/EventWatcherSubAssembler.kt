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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.watchers

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.ammolite.relays.filters.MutableTime
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

class EventWatcherSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<EventFinderQueryState>,
) : SingleSubEoseManager<EventFinderQueryState>(client, allKeys) {
    var lastNotesOnFilter = emptyList<Note>()
    var latestEOSEs: EOSEAccountFast<Note> = EOSEAccountFast<Note>(10000)

    override fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
    ) {
        lastNotesOnFilter.forEach {
            latestEOSEs.newEose(it, relay, time)
        }
        super.newEose(relay, time)
    }

    override fun updateFilter(
        key: List<EventFinderQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (key.isEmpty()) {
            return null
        }

        lastNotesOnFilter = key.map { it.note }

        return groupByRelayPresence(lastNotesOnFilter, latestEOSEs)
            .map { group ->
                if (group.isNotEmpty()) {
                    val addressables = group.filterIsInstance<AddressableNote>()
                    val events = group.mapNotNull { if (it !is AddressableNote) it else null }

                    listOfNotNull(
                        filterRepliesAndReactionsToNotes(events, findMinimumEOSEs(events, latestEOSEs)),
                        filterRepliesAndReactionsToAddresses(addressables, findMinimumEOSEs(addressables, latestEOSEs)),
                    ).flatten()
                } else {
                    emptyList()
                }
            }.flatten()
    }

    override fun distinct(key: EventFinderQueryState) = key.note

    fun groupByRelayPresence(
        notes: Iterable<Note>,
        eoseCache: EOSEAccountFast<Note>,
    ): Collection<List<Note>> =
        notes
            .groupBy { eoseCache.since(it)?.keys?.hashCode() }
            .values
            .map {
                // important to keep in order otherwise the Relay thinks the filter has changed and we REQ again
                it.sortedBy { it.idHex }
            }

    fun findMinimumEOSEs(
        notes: List<Note>,
        eoseCache: EOSEAccountFast<Note>,
    ): SincePerRelayMap {
        val minLatestEOSEs = mutableMapOf<NormalizedRelayUrl, MutableTime>()

        notes.forEach { note ->
            eoseCache.since(note)?.forEach {
                val minEose = minLatestEOSEs[it.key]
                if (minEose == null) {
                    minLatestEOSEs.put(it.key, it.value.copy())
                } else {
                    minEose.updateIfOlder(it.value.time)
                }
            }
        }

        return minLatestEOSEs
    }
}
