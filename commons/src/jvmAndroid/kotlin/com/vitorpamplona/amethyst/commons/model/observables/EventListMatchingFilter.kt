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
import java.util.concurrent.ConcurrentSkipListSet

/**
 * Creates a list of events (regular and addressable), sorted by created_at, that
 * is updated every time a new matching event is received — INCLUDING newer
 * versions of addressables, whose refreshed content re-emits and re-sorts.
 *
 * Like [NoteListMatchingFilter], this cannot store mutable [Note]s in a set
 * ordered on their live created_at: a newer replaceable event mutates the SAME
 * [AddressableNote] instance in place, which strands its node and lets the same
 * instance be inserted twice — the emitted list would then carry the same event
 * twice. So the sort key is snapshotted into an immutable [Entry], [sorted] is
 * ordered on that snapshot, and [byId] is the membership source of truth keyed
 * by the stable idHex (the address for addressables, the event id otherwise).
 *
 * Unlike [NoteListMatchingFilter], an addressable update is NOT ignored: the
 * list re-emits so consumers pick up the refreshed [Event] (read live off the
 * note). The entry's captured position is kept — re-sorting an updated entry
 * would mean a remove+add on [sorted], and two entries with different captured
 * keys for the same note would then transiently coexist and both read the same
 * live event, duplicating it. Keeping the position matches the original's only
 * non-corrupting behavior (it never reliably re-sorted either); consumers that
 * care about order re-sort downstream.
 *
 * Observer callbacks fire concurrently from several consume threads (relay
 * ingest + UI-side justConsume), so it stays lock-free: [sorted] is only ever
 * written for a key INSIDE that key's `compute` critical section, and only while
 * the key is absent. ConcurrentHashMap stripes per key, so same-idHex ops
 * serialize while different keys run in parallel. An entry is added only while
 * its key is absent from [byId], and every path that frees a key removes its
 * entry from [sorted] first.
 *
 * That keeps [sorted] converged to one entry per idHex, but a
 * ConcurrentSkipListSet iterator is only weakly consistent: under concurrent
 * add/remove churn a single traversal can momentarily surface a key twice
 * (lazy-deleted node not yet unlinked while its replacement is inserted). The
 * emitted list must never carry a duplicate id — a LazyColumn keyed on it would
 * crash — so [snapshot] deduplicates by the stable idHex as it materializes.
 */
class EventListMatchingFilter<T : Event>(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> SortedSet<Note>,
    private val update: (List<T>) -> Unit,
) : Observable {
    /** A note plus the sort key captured at insertion time, so ordering never depends on mutable state. */
    private class Entry(
        val note: Note,
        val createdAt: Long,
        val id: HexKey,
    )

    // created_at descending, id ascending as a stable tiebreak. Both fields are
    // immutable snapshots, so an Entry never moves once inserted.
    private val order =
        Comparator<Entry> { a, b ->
            val byCreatedAt = b.createdAt.compareTo(a.createdAt)
            if (byCreatedAt != 0) byCreatedAt else a.id.compareTo(b.id)
        }

    private val sorted = ConcurrentSkipListSet(order)
    private val byId = ConcurrentHashMap<HexKey, Entry>()

    private fun entryFor(note: Note): Entry {
        val event = note.event
        return Entry(note, note.createdAt() ?: Long.MIN_VALUE, event?.id ?: note.idHex)
    }

    @Suppress("UNCHECKED_CAST")
    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) {
            // The "version" note (a regular note holding an addressable event) is
            // never stored — the AddressableNote is. Re-emit if that addressable
            // is already listed so consumers pick up the refreshed content.
            if (byId.containsKey(event.address().toValue())) update(snapshot())
            return
        }

        if (!filter.match(event)) return

        // Add to [sorted] atomically with claiming the idHex slot, only when the
        // key is absent. An update keeps its entry (and position) — the re-emit
        // below reflects the refreshed event read live off the note.
        var added = false
        byId.compute(note.idHex) { _, existing ->
            existing ?: entryFor(note).also {
                sorted.add(it)
                added = true
            }
        }

        if (added) {
            val limit = filter.limit
            if (limit != null && sorted.size > limit) {
                // Drop the oldest (sorts last under [order]).
                sorted.pollLast()?.let { byId.remove(it.note.idHex, it) }
            }
        }

        // Always re-emit on a match: a first insert grows the list, an update
        // refreshes the event content the snapshot reads off the note.
        update(snapshot())
    }

    @Suppress("UNCHECKED_CAST")
    override fun remove(note: Note) {
        var removed = false
        byId.compute(note.idHex) { _, existing ->
            if (existing != null) {
                sorted.remove(existing)
                removed = true
            }
            null
        }
        if (removed) update(snapshot())
    }

    @Suppress("UNCHECKED_CAST")
    fun init() {
        sorted.clear()
        byId.clear()
        atOnce(filter).forEach { note ->
            byId.computeIfAbsent(note.idHex) { entryFor(note).also { sorted.add(it) } }
        }
        update(snapshot())
    }

    @Suppress("UNCHECKED_CAST")
    private fun snapshot(): List<T> {
        // Dedup by the stable idHex: the weakly-consistent iterator can transiently
        // surface a key twice under concurrent churn. Both would read the same live
        // event off the same note, so keeping the first (newest position) is correct.
        val seen = HashSet<HexKey>()
        return sorted.mapNotNull { e -> if (seen.add(e.note.idHex)) e.note.event as? T else null }
    }
}
