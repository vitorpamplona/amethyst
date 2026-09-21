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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * A relay-like list of the events matching a filter, newest first, re-emitting on addressable updates.
 *
 * The declaration is shared so the event cache can live in `commonMain`; the implementation is
 * not. One entry per idHex under concurrent ingest is kept by doing every write to the sorted
 * set inside that key's `ConcurrentHashMap.compute` critical section — per-key striping, no
 * lock — over a `ConcurrentSkipListSet` whose weakly-consistent iterator the snapshot
 * de-duplicates. Kotlin/Native has neither primitive, and the argument for why this is correct
 * (spelled out on the jvmAndroid actual) does not survive being reassembled out of weaker ones:
 * a copy-on-write `compute` may re-run its lambda, and this lambda has side effects.
 *
 * So iOS has no implementation yet and every member throws — see `EventListMatchingFilter.ios.kt`.
 */
expect class EventListMatchingFilter<T : Event>(
    filter: Filter,
    atOnce: (filter: Filter) -> List<Note>,
    update: (List<T>) -> Unit,
) : Observable {
    override fun new(
        event: Event,
        note: Note,
    )

    override fun remove(note: Note)

    fun init()
}
