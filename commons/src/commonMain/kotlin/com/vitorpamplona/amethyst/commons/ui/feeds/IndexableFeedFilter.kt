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
package com.vitorpamplona.amethyst.commons.ui.feeds

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Opt-in marker for a [FeedFilter] that can source its incremental updates from
 * the `LocalCache` observer registry (the inverted [com.vitorpamplona.quartz.nip01Core.relay.filters.FilterIndex])
 * instead of the global new-event fan-out.
 *
 * [indexFilters] returns the **coarse** Nostr [Filter](s) used only to register
 * the observer in the index. They must be a *superset* of what [FeedFilter.feed]
 * returns — the index uses them solely to decide which observers to wake for a
 * newly inserted event; the filter's own `applyFilter`/`feed` predicates still
 * run and remain the source of truth for inclusion. A filter that narrows too
 * tightly here (e.g. a `since`/author constraint the real feed doesn't enforce)
 * would silently drop posts, so prefer the broadest reliable narrowing field
 * (usually `kinds`).
 *
 * See `amethyst/plans/2026-06-18-dal-filter-to-localcache-observer.md`.
 */
interface IndexableFeedFilter {
    fun indexFilters(): List<Filter>
}
