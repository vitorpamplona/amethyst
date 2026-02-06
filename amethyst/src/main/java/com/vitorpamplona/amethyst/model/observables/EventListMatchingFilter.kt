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
package com.vitorpamplona.amethyst.model.observables

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.Observable
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import java.util.SortedSet

/**
 * Creates a list of events (regular and addressable)
 * that is updated every time a new event that matches
 * the filter is received, including addressables.
 */
class EventListMatchingFilter(
    private val filter: Filter,
    private val cache: LocalCache,
    private val update: (List<Event>) -> Unit,
) : Observable {
    // Keeping this here blocks it from being cleared from memory
    var currentResults: SortedSet<Note> = sortedSetOf<Note>(DefaultFeedOrder)

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (filter.match(event)) {
            currentResults.add(note)
            val limit = filter.limit
            if (limit != null && currentResults.size > limit) {
                currentResults.remove(currentResults.last())
            }
            update(currentResults.mapNotNull { it.event })
        }
    }

    override fun remove(note: Note) {
        if (currentResults.remove(note)) {
            update(currentResults.mapNotNull { it.event })
        }
    }

    fun init() {
        currentResults = cache.filter(filter)
        update(currentResults.mapNotNull { it.event })
    }
}
