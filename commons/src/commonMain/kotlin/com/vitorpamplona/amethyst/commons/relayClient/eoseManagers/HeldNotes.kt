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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.observables.CandidateSet
import com.vitorpamplona.amethyst.commons.model.observables.IFilter
import kotlinx.coroutines.CoroutineScope

/**
 * A holder for what a subscription loads when nothing else keeps it (`LocalCache` keeps notes
 * weakly): every cached note [filter] matches, held strongly for as long as [scope] runs, with the
 * relay cursors that vouch for them. When the scope ends, the notes and the cursors go together.
 *
 * For small sets only (an account's drafts, its chess games, a community's rules): it walks the
 * cache once when made and checks every note that arrives after.
 */
class HeldNotes(
    cache: EventCache,
    scope: CoroutineScope,
    filter: IFilter,
) : RelayCursorHolder {
    private val notes = CandidateSet(cache, scope, filter, weak = false).start()

    override val relayCursors = RelayCursors()

    /** The notes held, or null while the first walk of the cache is still running. */
    fun snapshot(): Set<Note>? = notes.snapshot()
}
