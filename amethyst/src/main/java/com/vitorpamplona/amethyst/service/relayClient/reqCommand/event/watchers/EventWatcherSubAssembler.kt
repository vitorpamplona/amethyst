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
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime

class EventWatcherSubAssembler(
    client: NostrClient,
    allKeys: () -> Set<EventFinderQueryState>,
) : SingleSubEoseManager<EventFinderQueryState>(client, allKeys) {
    var lastNotesOnFilter = emptyList<Note>()

    override fun newEose(
        relayUrl: String,
        time: Long,
    ) {
        lastNotesOnFilter.forEach {
            it.lastReactionsDownloadTime.newEose(relayUrl, time)
        }
        super.newEose(relayUrl, time)
    }

    override fun updateFilter(
        keys: List<EventFinderQueryState>,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? {
        if (keys.isEmpty()) {
            return null
        }

        lastNotesOnFilter = keys.map { it.note }
        val addressables = lastNotesOnFilter.filterIsInstance<AddressableNote>()
        val events = keys.mapNotNull { if (it.note !is AddressableNote) it.note else null }

        return groupByEOSEPresence(lastNotesOnFilter)
            .map {
                listOfNotNull(
                    filterRepliesAndReactionsToNotes(events),
                    filterRepliesAndReactionsToAddresses(addressables),
                    filterQuotesToNotes(it),
                ).flatten()
            }.flatten()
    }

    override fun distinct(key: EventFinderQueryState) = key.note
}
