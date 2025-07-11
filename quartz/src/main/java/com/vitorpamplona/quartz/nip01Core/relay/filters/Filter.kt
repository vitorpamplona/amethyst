/**
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
package com.vitorpamplona.quartz.nip01Core.relay.filters

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address

/**
 * A filter for Nostr events used in relay subscriptions. Supports various criteria
 * to match events based on IDs, authors, kinds, tags, time ranges, search terms, and limits.
 *
 * Parameters:
 * - ids: Optional list of event IDs to match (must be 64 characters).
 * - authors: Optional list of author public keys (must be 64 characters).
 * - kinds: Optional list of event kinds to include.
 * - tags: Optional map of tag names to values arrays (common tags like 'p', 'e', 'a' are validated).
 * - since: Optional timestamp for filtering events with publication time ≥ this value.
 * - until: Optional timestamp for filtering events with publication time ≤ this value.
 * - limit: Optional maximum number of events to request.
 * - search: Optional string to search within event content.
 *
 * This class performs validation on construction to ensure all string-based identifiers
 * follow Nostr requirements (64-char hex, onion addresses) and logs errors for invalid inputs.
 */
class Filter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null,
) {
    fun toJson() = FilterSerializer.toJson(ids, authors, kinds, tags, since, until, limit, search)

    fun match(event: Event) = FilterMatcher.match(event, ids, authors, kinds, tags, since, until)

    fun copy(
        ids: List<String>? = this.ids,
        authors: List<String>? = this.authors,
        kinds: List<Int>? = this.kinds,
        tags: Map<String, List<String>>? = this.tags,
        since: Long? = this.since,
        until: Long? = this.until,
        limit: Int? = this.limit,
        search: String? = this.search,
    ) = Filter(ids, authors, kinds, tags, since, until, limit, search)

    /**
     * Returns true if this filter contains any non-null and non-empty criteria.
     */
    fun isFilledFilter() =
        (ids != null && ids.isNotEmpty()) ||
            (authors != null && authors.isNotEmpty()) ||
            (kinds != null && kinds.isNotEmpty()) ||
            (tags != null && tags.isNotEmpty() && tags.values.all { it.isNotEmpty() }) ||
            (since != null) ||
            (until != null) ||
            (limit != null) ||
            (search != null && search.isNotEmpty())

    init {
        if (!isFilledFilter()) {
            Log.e("FilterError", "Filter is empty: ${toJson()}")
        }

        ids?.forEach {
            if (it.length != 64) Log.e("FilterError", "Invalid id length $it on ${toJson()}")
        }
        authors?.forEach {
            if (it.length != 64) Log.e("FilterError", "Invalid author length $it on ${toJson()}")
        }
        // tests common tags.
        tags?.get("p")?.forEach {
            if (it.length != 64) Log.e("FilterError", "Invalid p-tag length $it on ${toJson()}")
        }
        tags?.get("e")?.forEach {
            if (it.length != 64) Log.e("FilterError", "Invalid e-tag length $it on ${toJson()}")
        }
        tags?.get("a")?.forEach {
            if (Address.parse(it) == null) Log.e("FilterError", "Invalid a-tag $it on ${toJson()}")
        }
    }
}
