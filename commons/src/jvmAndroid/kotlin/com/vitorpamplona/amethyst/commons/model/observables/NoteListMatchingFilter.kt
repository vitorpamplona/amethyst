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
 * Entries are keyed by [Note.idHex] rather than kept in a set ordered by
 * `created_at`. That ordering key is MUTABLE for addressables: when a newer
 * version of a kind-3xxxx replaceable/addressable arrives (a DVM re-announcing
 * its kind-31990 definition, for instance), LocalCache mutates the same Note in
 * place — bumping its `created_at` — and re-notifies here. A sorted set then
 * navigates to the note's *new* position, fails to find the existing node still
 * sitting at the *old* position, and inserts a second node for the same address.
 * The emitted list carried that address twice and a `LazyColumn` keyed by
 * `idHex` crashed with `Key "..." was already used`. Keying by the stable
 * `idHex` holds exactly one entry per note; the list is sorted fresh on emit.
 */
class NoteListMatchingFilter(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> SortedSet<Note>,
    private val update: (List<Note>) -> Unit,
) : Observable {
    private val currentResults = ConcurrentHashMap<HexKey, Note>()

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) return

        if (filter.match(event)) {
            // putIfAbsent returns non-null when this idHex is already tracked, so a
            // new version of an addressable neither re-adds nor reorders the list.
            if (currentResults.putIfAbsent(note.idHex, note) == null) {
                emitEnforcingLimit()
            }
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

    private fun sortedResults(): List<Note> = currentResults.values.sortedWith(CreatedAtIdHexComparator)

    private fun emitEnforcingLimit() {
        val limit = filter.limit
        val sorted = sortedResults()
        if (limit != null && sorted.size > limit) {
            sorted.drop(limit).forEach { currentResults.remove(it.idHex) }
            update(sorted.take(limit))
        } else {
            update(sorted)
        }
    }
}
