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
 * Emits each new event that matches the given Nostr filter, one at a time.
 *
 * Unlike [EventListMatchingFilter] / [NoteListMatchingFilter], this does not
 * accumulate a list — it simply calls [onNew] per matching event as it is
 * inserted into the cache. Useful for reactive event-triggered pipelines
 * (e.g. notifications) that need per-event delivery without list overhead.
 *
 * The optional [predicate] runs after the protocol [filter] matches; use it
 * for checks the Nostr [Filter] grammar can't express (e.g. rolling age
 * cutoffs, arbitrary tag shapes, derived content fields). The predicate has
 * to be fast — it runs on every new cache insertion.
 */
class NewEventMatchingFilter<T : Event>(
    private val filter: Filter,
    private val predicate: (Event) -> Boolean = { true },
    private val onNew: (T) -> Unit,
) : Observable {
    @Suppress("UNCHECKED_CAST")
    override fun new(
        event: Event,
        note: Note,
    ) {
        if (filter.match(event) && predicate(event)) {
            onNew(event as T)
        }
    }

    override fun remove(note: Note) {}
}
