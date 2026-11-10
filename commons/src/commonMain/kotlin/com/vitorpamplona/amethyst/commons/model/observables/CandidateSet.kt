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
import kotlinx.coroutines.awaitCancellation
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
     * The candidates, or null until the first walk of the cache has finished. Starts the set on
     * first use, so a family of feeds nobody opens never walks the cache or watches new notes.
     */
    fun snapshot(): Set<Note>? {
        start()
        return observer.snapshot()
    }

    val size: Int get() = observer.size

    /** Walks the cache once, off the main thread, then follows it until the scope ends. Idempotent. */
    @OptIn(ExperimentalAtomicApi::class)
    fun start(): CandidateSet {
        if (!started.compareAndSet(expectedValue = false, newValue = true)) return this
        scope.launch(Dispatchers.IO) {
            try {
                cache.registerNoteSet(observer)
                awaitCancellation()
            } finally {
                cache.unregisterNoteSet(observer)
            }
        }
        return this
    }
}
