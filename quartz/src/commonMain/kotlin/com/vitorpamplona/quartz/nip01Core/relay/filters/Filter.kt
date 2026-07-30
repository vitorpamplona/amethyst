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
package com.vitorpamplona.quartz.nip01Core.relay.filters

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.utils.Log

/**
 * A filter for Nostr events used in relay subscriptions. Supports various criteria
 * to match events based on IDs, authors, kinds, tags, time ranges, search terms, and limits.
 *
 * Parameters:
 * - ids: Optional list of event IDs to match (must be 64 characters).
 * - authors: Optional list of author public keys (must be 64 characters).
 * - kinds: Optional list of event kinds to include.
 * - tags: Optional map of tag names to values arrays (common tags like 'p', 'e', 'a' are validated).
 * - tagsAll: Optional map of tag names to values arrays that must all match (common tags like 'p', 'e', 'a' are validated).
 * - since: Optional timestamp for filtering events with publication time ≥ this value.
 * - until: Optional timestamp for filtering events with publication time ≤ this value.
 * - limit: Optional maximum number of events to request.
 * - search: Optional string to search within event content.
 *
 * This class performs validation on construction to ensure all string-based identifiers
 * follow Nostr requirements (64-char hex, onion addresses) and logs errors for invalid inputs.
 *
 * ## Subclassing
 *
 * `open` so a consumer can carry its own client-side metadata alongside a filter — e.g. Amethyst's
 * `ExplainedFilter`, which records *why* a subscription exists so the UI can explain which relays
 * serve which purpose.
 *
 * Subclass fields never reach a relay: `FilterSerializer` is registered for this type and writes an
 * explicit protocol field list, so any extra state is dropped at the wire boundary regardless of the
 * runtime type. That is deliberate — the purpose of a REQ is exactly the sort of thing a client
 * should not hand to relays.
 *
 * A subclass MUST override [copy]. Filters are copied on the live path (`copy(since = …)` after each
 * EOSE), so a subclass that inherits the base implementation is silently downgraded to a plain
 * [Filter] on the first window refresh.
 */
@Stable
open class Filter(
    val ids: List<HexKey>? = null,
    val authors: List<HexKey>? = null,
    val kinds: List<Kind>? = null,
    val tags: Map<String, List<String>>? = null,
    val tagsAll: Map<String, List<String>>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null,
) : OptimizedSerializable {
    fun toJson() = OptimizedJsonMapper.toJson(this)

    fun match(event: Event) = FilterMatcher.match(event, ids, authors, kinds, tags, tagsAll, since, until)

    open fun copy(
        ids: List<String>? = this.ids,
        authors: List<String>? = this.authors,
        kinds: List<Int>? = this.kinds,
        tags: Map<String, List<String>>? = this.tags,
        tagsAll: Map<String, List<String>>? = this.tagsAll,
        since: Long? = this.since,
        until: Long? = this.until,
        limit: Int? = this.limit,
        search: String? = this.search,
    ) = Filter(ids, authors, kinds, tags, tagsAll, since, until, limit, search)

    /**
     * Returns true if this filter doesn't filter for anything.
     */
    fun isEmpty() =
        (ids == null || ids.isEmpty()) &&
            (authors == null || authors.isEmpty()) &&
            (kinds == null || kinds.isEmpty()) &&
            (tags == null || tags.isEmpty() || tags.values.all { it.isEmpty() }) &&
            (tagsAll == null || tagsAll.isEmpty() || tagsAll.values.all { it.isEmpty() }) &&
            (since == null) &&
            (until == null) &&
            (limit == null) &&
            (search == null || search.isEmpty())

    init {
        ids?.forEach {
            if (it.length != 64) Log.e("FilterError") { "Invalid id length $it on ${toJson()}" }
        }
        authors?.forEach {
            if (it.length != 64) Log.e("FilterError") { "Invalid author length $it on ${toJson()}" }
        }
        // tests common tags.
        if (tags != null) {
            tags["p"]?.forEach {
                if (it.length != 64) Log.e("FilterError") { "Invalid p-tag length $it on ${toJson()}" }
            }
            tags["e"]?.forEach {
                if (it.length != 64) Log.e("FilterError") { "Invalid e-tag length $it on ${toJson()}" }
            }
            tags["a"]?.forEach {
                if (Address.parse(it) == null) Log.e("FilterError") { "Invalid a-tag $it on ${toJson()}" }
            }
        }
        if (tagsAll != null) {
            tagsAll["p"]?.forEach {
                if (it.length != 64) Log.e("FilterError") { "Invalid p-tag length $it on ${toJson()}" }
            }
            tagsAll["e"]?.forEach {
                if (it.length != 64) Log.e("FilterError") { "Invalid e-tag length $it on ${toJson()}" }
            }
            tagsAll["a"]?.forEach {
                if (Address.parse(it) == null) Log.e("FilterError") { "Invalid a-tag $it on ${toJson()}" }
            }
        }
    }
}
