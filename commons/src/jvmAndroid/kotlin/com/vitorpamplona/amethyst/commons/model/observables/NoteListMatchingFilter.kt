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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import java.util.SortedSet
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates a list of notes (regular and addressable)
 * that only gets updated when a new note appears.
 *
 * New versions of addressables do not update the list.
 *
 * Membership is keyed by the immutable [Note.idHex] rather than kept in a
 * sorted set ordered by createdAt. AddressableNotes are mutable: when a newer
 * version of a replaceable event arrives, LocalCache swaps the event on the
 * SAME note instance, changing its createdAt in place. A
 * ConcurrentSkipListSet ordered on that createdAt cannot survive the change —
 * the moved node is no longer found by add()/remove(), so the note ends up
 * inserted twice and the emitted list carries a duplicate idHex, crashing any
 * LazyColumn keyed on it. Deduping by idHex keeps membership correct
 * regardless of createdAt changes; the display order is computed fresh on each
 * emission.
 */
class NoteListMatchingFilter(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> SortedSet<Note>,
    private val update: (List<Note>) -> Unit,
) : Observable {
    val currentResults: ConcurrentHashMap<HexKey, Note> = ConcurrentHashMap()

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) return

        // New versions of addressables do not update the list.
        if (currentResults.containsKey(note.idHex)) return

        if (filter.match(event)) {
            currentResults[note.idHex] = note

            val limit = filter.limit
            if (limit != null && currentResults.size > limit) {
                // Drop the oldest (sorts last under CreatedAtIdHexComparator).
                currentResults.values.maxWithOrNull(CreatedAtIdHexComparator)?.let {
                    currentResults.remove(it.idHex)
                }
            }

            update(snapshot())
        }
    }

    override fun remove(note: Note) {
        if (currentResults.remove(note.idHex) != null) {
            update(snapshot())
        }
    }

    fun init() {
        currentResults.clear()
        atOnce(filter).forEach { currentResults[it.idHex] = it }
        update(snapshot())
    }

    private fun snapshot(): List<Note> = currentResults.values.sortedWith(CreatedAtIdHexComparator)
}
