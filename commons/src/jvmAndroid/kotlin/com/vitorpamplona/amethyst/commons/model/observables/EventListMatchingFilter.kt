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
 * Creates a list of events (regular and addressable)
 * that is updated every time a new event that matches
 * the filter is received, including addressables.
 *
 * Entries are keyed by [Note.idHex] rather than kept in a set ordered by
 * `created_at`. That ordering key is MUTABLE for addressables: a newer version
 * replaces the event on the same Note in place, so a set ordered by `created_at`
 * would navigate to the note's new position, miss the existing node at its old
 * position, and insert a duplicate for the same address — surfacing the same
 * event twice in the emitted list. Keying by the stable `idHex` keeps exactly
 * one entry per note (a replacement updates it), and the list is sorted fresh on
 * emit.
 */
class EventListMatchingFilter<T : Event>(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> SortedSet<Note>,
    private val update: (List<T>) -> Unit,
) : Observable {
    private val currentResults = ConcurrentHashMap<HexKey, Note>()

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) {
            // event update
            if (currentResults.containsKey(note.idHex)) {
                update(sortedResults())
            }
            return
        }

        if (filter.match(event)) {
            // Replaces on a matching idHex so an addressable's newer content is
            // reflected without duplicating the entry.
            currentResults[note.idHex] = note
            emitEnforcingLimit()
        }
    }

    override fun remove(note: Note) {
        if (currentResults.remove(note.idHex) != null) {
            update(sortedResults())
        }
    }

    fun init() {
        currentResults.clear()
        atOnce(filter).forEach { currentResults[it.idHex] = it }
        update(sortedResults())
    }

    @Suppress("UNCHECKED_CAST")
    private fun sortedResults(): List<T> =
        currentResults.values
            .sortedWith(CreatedAtIdHexComparator)
            .mapNotNull { it.event as? T }

    private fun emitEnforcingLimit() {
        val limit = filter.limit
        if (limit != null && currentResults.size > limit) {
            val sortedNotes = currentResults.values.sortedWith(CreatedAtIdHexComparator)
            sortedNotes.drop(limit).forEach { currentResults.remove(it.idHex) }
        }
        update(sortedResults())
    }
}
