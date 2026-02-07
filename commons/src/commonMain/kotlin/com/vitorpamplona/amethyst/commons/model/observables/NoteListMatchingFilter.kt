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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import java.util.SortedSet
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.collections.toList

/**
 * Creates a list of notes (regular and addressable)
 * that only gets updated when a new note appears.
 *
 * New versions of addressables do not update the list
 */
class NoteListMatchingFilter(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> SortedSet<Note>,
    private val update: (List<Note>) -> Unit,
) : Observable {
    var currentResults: ConcurrentSkipListSet<Note> = ConcurrentSkipListSet(CreatedAtIdHexComparator)

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (filter.match(event)) {
            if (currentResults.add(note)) {
                val limit = filter.limit
                if (limit != null && currentResults.size > limit) {
                    currentResults.remove(currentResults.last())
                }

                update(currentResults.toList())
            }
        }
    }

    override fun remove(note: Note) {
        if (currentResults.remove(note)) {
            update(currentResults.toList())
        }
    }

    fun init() {
        currentResults = ConcurrentSkipListSet(atOnce(filter))
        update(currentResults.toList())
    }
}
