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
 * Creates a list of notes (regular and addressable), sorted by created_at like a
 * relay, that only grows when a new note appears.
 *
 * New versions of addressables do not update the list.
 *
 * There is exactly one [Note] instance per id/address (LocalCache owns their
 * creation), so uniqueness is a non-issue in principle — except a note's sort
 * key is mutable: a newer replaceable event swaps the event on the SAME
 * [AddressableNote] instance, changing its created_at in place. A sorted set
 * ordered on that live value cannot survive it — the moved node is no longer on
 * the search path of add()/remove(), so the same instance gets inserted twice
 * and the emitted list carries a duplicate idHex, crashing any LazyColumn keyed
 * on it.
 *
 * So the sort key is snapshotted into an immutable [Entry] when the note first
 * enters and never read live again; [sorted] is ordered on that snapshot
 * (stable) and [byId] is the membership source of truth, keyed by the stable
 * idHex.
 *
 * Observer callbacks fire concurrently from several consume threads (relay
 * ingest + UI-side justConsume), and this observer is used everywhere, so it
 * stays lock-free: [byId] is a ConcurrentHashMap and every write to [sorted] for
 * a given idHex happens INSIDE that key's `compute` critical section.
 * ConcurrentHashMap stripes per key, so same-idHex ops serialize while different
 * keys run fully in parallel. An entry is added to [sorted] only while its key
 * is absent from [byId], and every path that makes a key absent removes its
 * entry from [sorted] first, keeping [sorted] converged to one entry per idHex.
 *
 * That convergence isn't enough on its own: a ConcurrentSkipListSet iterator is
 * only weakly consistent, so under concurrent add/remove churn a single
 * traversal can momentarily surface a key twice (a lazy-deleted node not yet
 * unlinked while its replacement is inserted). The emitted list must never carry
 * a duplicate idHex — the LazyColumn keyed on it would crash — so [snapshot]
 * deduplicates by idHex as it materializes.
 */
class NoteListMatchingFilter(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> SortedSet<Note>,
    private val update: (List<Note>) -> Unit,
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
        // A null event (unresolved note) sorts last, matching CreatedAtIdHexComparator.
        val event = note.event
        return Entry(note, note.createdAt() ?: Long.MIN_VALUE, event?.id ?: note.idHex)
    }

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) return

        if (!filter.match(event)) return

        // Add to [sorted] atomically with claiming the idHex slot. New versions
        // of an already listed note return the existing entry untouched.
        var added = false
        byId.compute(note.idHex) { _, existing ->
            existing ?: entryFor(note).also {
                sorted.add(it)
                added = true
            }
        }
        if (!added) return

        val limit = filter.limit
        if (limit != null && sorted.size > limit) {
            // Drop the oldest (sorts last under [order]).
            sorted.pollLast()?.let { byId.remove(it.note.idHex, it) }
        }

        update(snapshot())
    }

    override fun remove(note: Note) {
        // Remove from [sorted] atomically with releasing the idHex slot.
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

    fun init() {
        sorted.clear()
        byId.clear()
        atOnce(filter).forEach { note ->
            byId.computeIfAbsent(note.idHex) { entryFor(note).also { sorted.add(it) } }
        }
        update(snapshot())
    }

    private fun snapshot(): List<Note> {
        // Dedup by idHex: the weakly-consistent iterator can transiently surface a
        // key twice under concurrent churn. Keeping the first (newest position) is
        // correct — both nodes point at the same note.
        val seen = HashSet<HexKey>()
        return sorted.mapNotNull { e -> e.note.takeIf { seen.add(it.idHex) } }
    }
}
