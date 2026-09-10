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
package com.vitorpamplona.amethyst.desktop.subscriptions

import com.vitorpamplona.amethyst.commons.search.RenderableKinds
import com.vitorpamplona.amethyst.commons.search.SearchPipeline
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

object SearchFilterFactory {
    /**
     * The filters a query sends, per the shared search language — see
     * [com.vitorpamplona.amethyst.commons.search.SearchFilterBuilder], which owns how each token
     * becomes a NIP-01 filter field and how a hashtag or a scope fans out into a union.
     *
     * The only thing this layer adds is the kind window, and it no longer keeps its own copy of
     * one: the list lived here and in Android's `searchPostsByText`, and the copy here had lost
     * the calendar slots and code snippets, so the same query returned different kinds on the two
     * platforms. Both now read [RenderableKinds].
     */
    fun createFilters(
        query: SearchQuery,
        limit: Int = 100,
    ): List<Filter> {
        if (query.isEmpty) return emptyList()
        // SearchPipeline.filters lets the query's own `kind:` win over the window passed here, so
        // a named kind collapses the fan-out to one group on its own.
        if (query.kinds.isNotEmpty()) return SearchPipeline.filters(query, limit = limit)
        return RenderableKinds.GROUPS.flatMap { SearchPipeline.filters(query, it, limit) }
    }
}
