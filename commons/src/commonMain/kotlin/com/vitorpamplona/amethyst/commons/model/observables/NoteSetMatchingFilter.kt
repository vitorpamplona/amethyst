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
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.concurrent.Volatile

/**
 * Every cached note that passes [filter], kept current as notes arrive and are deleted: a
 * materialized index of what one feed could show. A feed then rebuilds by scanning this set, not
 * the whole cache, which in a long session holds far more notes than any one feed cares about.
 *
 * Registered unindexed (see `EventCache.registerNoteSet`), so each new note costs one
 * [IFilter.matches] call.
 *
 * [weak] holds the notes the way `LocalCache` does, so the set never keeps alive a note nothing
 * else wants. Use it when the rule takes in a large part of the cache (Home takes every note); a
 * small set (notifications) can hold its notes strongly.
 *
 * [dependents] names the notes whose answer may change when a given note arrives: a reaction
 * that came before the note it reacts to only matches once that note is known.
 */
class NoteSetMatchingFilter(
    private val filter: IFilter,
    private val weak: Boolean = false,
    private val dependents: (Note) -> Collection<Note> = { emptyList() },
) : Observable {
    private val lock = KmpLock()
    private val strong = HashSet<Note>()
    private val weakRefs = HashMap<HexKey, WeakReference<Note>>()

    /** False until the first walk of the cache has finished; [snapshot] is incomplete before. */
    @Volatile
    var ready: Boolean = false
        private set

    val size: Int get() = lock.withLock { if (weak) weakRefs.size else strong.size }

    /** A copy of the current set, or null while the first walk is still running. */
    fun snapshot(): Set<Note>? {
        if (!ready) return null
        return lock.withLock {
            if (weak) {
                val live = HashSet<Note>(weakRefs.size)
                val iterator = weakRefs.values.iterator()
                while (iterator.hasNext()) {
                    val note = iterator.next().get()
                    if (note == null) iterator.remove() else live.add(note)
                }
                live
            } else {
                strong.toHashSet()
            }
        }
    }

    /** For the first walk: [note] as it is in the cache now. */
    fun consider(note: Note) {
        val event = note.event ?: return
        if (event is AddressableEvent && note !is AddressableNote) return
        if (filter.matches(note)) add(note)
    }

    fun markReady() {
        ready = true
    }

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (!(event is AddressableEvent && note !is AddressableNote) && filter.matches(note)) add(note)
        dependents(note).forEach { dependent ->
            if (filter.matches(dependent)) add(dependent)
        }
    }

    override fun remove(note: Note) {
        lock.withLock {
            if (weak) {
                if (weakRefs[note.idHex]?.get() === note) weakRefs.remove(note.idHex)
            } else {
                strong.remove(note)
            }
        }
    }

    private var addsSinceSweep = 0

    private fun add(note: Note) {
        lock.withLock {
            if (weak) {
                weakRefs[note.idHex] = WeakReference(note)
                // Dead entries are dropped by snapshot(); a set nobody reads would only grow.
                if (++addsSinceSweep >= SWEEP_EVERY) {
                    addsSinceSweep = 0
                    weakRefs.values.removeAll { it.get() == null }
                }
            } else {
                strong.add(note)
            }
        }
    }

    private companion object {
        const val SWEEP_EVERY = 1_000
    }
}
