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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The notes in [cache] a family of feeds could show, by a stable [filter], kept current for as
 * long as a scope runs. The feeds rebuild by scanning [snapshot] and applying their own viewer
 * policy (mutes, the selected list, settings), which would make the set stale if it were in
 * [filter]. See [NoteSetMatchingFilter] for [weak] and [dependents].
 */
class CandidateSet(
    private val cache: EventCache,
    private val scope: CoroutineScope,
    filter: IFilter,
    weak: Boolean = false,
    dependents: (Note) -> Collection<Note> = { emptyList() },
) {
    private val observer = NoteSetMatchingFilter(filter, weak, dependents)

    @OptIn(ExperimentalAtomicApi::class)
    private val started = AtomicBoolean(false)

    /**
     * The candidates, or null until the first walk of the cache has finished. The first call
     * starts the set on the calling thread (a rebuild, already off the main thread), so that
     * rebuild uses the set's own first walk instead of walking the cache a second time. A family
     * of feeds nobody opens never walks the cache or watches new notes.
     */
    fun snapshot(): Set<Note>? {
        startHere()
        return observer.snapshot()
    }

    val size: Int get() = observer.size

    /** Starts the set in the background, for a family whose feeds are always on. Idempotent. */
    fun start(): CandidateSet {
        scope.launch(Dispatchers.IO) { startHere() }
        return this
    }

    /** Registers and walks the cache on this thread; follows the cache until the scope ends. */
    @OptIn(ExperimentalAtomicApi::class)
    private fun startHere() {
        if (!started.compareAndSet(expectedValue = false, newValue = true)) return
        cache.registerNoteSet(observer)
        // Runs at once when the scope has already ended, so the observer never outlives it.
        scope.coroutineContext[Job]?.invokeOnCompletion { cache.unregisterNoteSet(observer) }
    }
}
