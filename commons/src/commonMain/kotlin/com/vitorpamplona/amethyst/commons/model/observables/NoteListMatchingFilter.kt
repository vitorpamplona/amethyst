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
@file:OptIn(ExperimentalAtomicApi::class)

package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Creates a list of notes (regular and addressable), sorted by created_at like a
 * relay, that only grows when a new note appears.
 *
 * New versions of addressables do not update the list.
 *
 * There is exactly one [Note] instance per id/address (the cache owns their creation), so
 * uniqueness is a non-issue in principle — except a note's sort key is mutable: a newer
 * replaceable event swaps the event on the SAME [AddressableNote] instance, changing its
 * created_at in place. Anything ordered on that live value cannot survive it. So the sort key is
 * snapshotted into an immutable [Entry] when the note first enters and never read live again.
 *
 * **Lock-free, by copy-on-write.** Observer callbacks fire concurrently from several consume
 * threads (relay ingest + UI-side justConsume) and this observer is used everywhere, so it takes
 * no lock: the whole list lives in one immutable [State] behind an [AtomicReference], and every
 * mutation builds the next state and publishes it with a compare-and-set, retrying if another
 * thread won the race. Same shape quartz uses for its native `ConcurrentMap`.
 *
 * Two things fall out of that, both previously paid for by hand:
 *  - a reader never sees a torn list, so the emitted snapshot needs no de-duplication pass —
 *    one entry per idHex holds by construction, since [State.ids] is what gates insertion;
 *  - the emitted list IS the published state, so emitting costs one map rather than a walk of a
 *    concurrent structure plus a `HashSet` to strip the duplicates that walk could surface.
 *
 * The copy is O(n) per write, which is what the previous per-emit snapshot already cost.
 */
class NoteListMatchingFilter(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> List<Note>,
    private val update: (List<Note>) -> Unit,
) : Observable {
    /** A note plus the sort key captured at insertion time, so ordering never depends on mutable state. */
    private class Entry(
        val note: Note,
        val createdAt: Long,
        val id: HexKey,
    )

    /** One immutable version of the list. [ids] is the membership gate, keyed by the stable idHex. */
    private class State(
        val entries: List<Entry>,
        val ids: Set<HexKey>,
    ) {
        fun notes(): List<Note> = entries.map { it.note }

        companion object {
            val EMPTY = State(emptyList(), emptySet())
        }
    }

    // created_at descending, id ascending as a stable tiebreak. Both fields are
    // immutable snapshots, so an Entry never moves once inserted.
    private val order =
        Comparator<Entry> { a, b ->
            val byCreatedAt = b.createdAt.compareTo(a.createdAt)
            if (byCreatedAt != 0) byCreatedAt else a.id.compareTo(b.id)
        }

    private val state = AtomicReference(State.EMPTY)

    private fun entryFor(note: Note): Entry {
        // A null event (unresolved note) sorts last, matching CreatedAtIdHexComparator.
        val event = note.event
        return Entry(note, note.createdAt() ?: Long.MIN_VALUE, event?.id ?: note.idHex)
    }

    private fun State.plus(
        entry: Entry,
        limit: Int?,
    ): State {
        val found = entries.binarySearch(entry, order)
        val at = if (found < 0) -found - 1 else found

        val grown = ArrayList<Entry>(entries.size + 1)
        grown.addAll(entries.subList(0, at))
        grown.add(entry)
        grown.addAll(entries.subList(at, entries.size))

        if (limit == null || grown.size <= limit) return State(grown, ids + entry.note.idHex)

        // Over the limit: drop the oldest, which sorts last. That can be the entry just
        // inserted, and then it is simply not listed — as the previous pollLast() did.
        val dropped = grown.removeAt(grown.size - 1)
        return State(grown, ids + entry.note.idHex - dropped.note.idHex)
    }

    private fun State.minus(idHex: HexKey): State = State(entries.filterNot { it.note.idHex == idHex }, ids - idHex)

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) return

        if (!filter.match(event)) return

        val limit = filter.limit
        while (true) {
            val current = state.load()
            // Already listed: a newer version of an addressable keeps its entry, and its
            // position with it, so the list only ever grows.
            if (note.idHex in current.ids) return

            val next = current.plus(entryFor(note), limit)
            if (state.compareAndSet(current, next)) {
                update(next.notes())
                return
            }
        }
    }

    override fun remove(note: Note) {
        while (true) {
            val current = state.load()
            if (note.idHex !in current.ids) return

            val next = current.minus(note.idHex)
            if (state.compareAndSet(current, next)) {
                update(next.notes())
                return
            }
        }
    }

    fun init() {
        // The cache query behind [atOnce] has already applied the filter's limit, so this
        // inserts what it is given, exactly as the previous implementation did.
        var fresh = State.EMPTY
        atOnce(filter).forEach { note ->
            if (note.idHex !in fresh.ids) fresh = fresh.plus(entryFor(note), null)
        }
        state.store(fresh)
        update(fresh.notes())
    }
}
