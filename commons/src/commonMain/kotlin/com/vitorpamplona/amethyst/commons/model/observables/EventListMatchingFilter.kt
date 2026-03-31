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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import java.util.SortedSet
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates a list of events (regular and addressable)
 * that is updated every time a new event that matches
 * the filter is received, including addressables.
 *
 * Uses a ConcurrentHashMap keyed by note.idHex for identity tracking.
 * For AddressableNotes, idHex is the stable address (kind:pubkey:dTag),
 * so event replacements are correctly detected as updates, not new entries.
 */
class EventListMatchingFilter<T : Event>(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> SortedSet<Note>,
    private val update: (List<T>) -> Unit,
) : Observable {
    // Keeping this here blocks notes from being cleared from memory
    private val trackedNotes = ConcurrentHashMap<String, Note>()

    @Suppress("UNCHECKED_CAST")
    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) {
            // Addressable event arrived through a regular Note path.
            // Re-emit if we're tracking this note.
            if (trackedNotes.containsKey(note.idHex)) {
                emitCurrentResults()
            }
            return
        }

        if (filter.match(event)) {
            val isNew = trackedNotes.put(note.idHex, note) == null
            if (isNew) {
                val limit = filter.limit
                if (limit != null && trackedNotes.size > limit) {
                    val sorted = trackedNotes.values.sortedWith(CreatedAtIdHexComparator)
                    val toRemove = sorted.last()
                    trackedNotes.remove(toRemove.idHex)
                }
            }

            // Always emit: new notes need emission, and addressable
            // updates (isNew=false) need re-emission with new event content.
            emitCurrentResults()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun remove(note: Note) {
        if (trackedNotes.remove(note.idHex) != null) {
            emitCurrentResults()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun emitCurrentResults() {
        update(
            trackedNotes.values
                .sortedWith(CreatedAtIdHexComparator)
                .mapNotNull { it.event as? T },
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun init() {
        trackedNotes.clear()
        atOnce(filter).associateByTo(trackedNotes) { it.idHex }
        emitCurrentResults()
    }
}
