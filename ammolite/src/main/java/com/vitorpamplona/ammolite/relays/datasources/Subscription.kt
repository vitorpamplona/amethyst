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
package com.vitorpamplona.ammolite.relays.datasources

import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.NormalFilter
import com.vitorpamplona.ammolite.relays.filters.SinceAuthorPerRelayFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString().substring(0, 4),
    val onEose: ((time: Long, relayUrl: String) -> Unit)? = null,
) {
    var typedFilters: List<TypedFilter>? = null // Inactive when null

    fun reset() {
        typedFilters = null
    }

    fun callEose(
        time: Long,
        relay: String,
    ) {
        onEose?.let { it(time, relay) }
    }

    fun hasChangedFiltersFrom(otherFilters: List<TypedFilter>?): Boolean {
        if (typedFilters == null && otherFilters == null) return false
        if (typedFilters?.size != otherFilters?.size) return true

        typedFilters?.forEachIndexed { index, typedFilter ->
            val otherFilter = otherFilters?.getOrNull(index) ?: return true

            if (typedFilter.filter is SincePerRelayFilter && otherFilter.filter is SincePerRelayFilter) {
                return isDifferent(typedFilter.filter, otherFilter.filter)
            }

            if (typedFilter.filter is SinceAuthorPerRelayFilter && otherFilter.filter is SinceAuthorPerRelayFilter) {
                return isDifferent(typedFilter.filter, otherFilter.filter)
            }

            if (typedFilter.filter is NormalFilter && otherFilter.filter is NormalFilter) {
                return isDifferent(typedFilter.filter, otherFilter.filter)
            }

            return true
        }
        return false
    }

    fun isDifferent(
        filter1: SincePerRelayFilter,
        filter2: SincePerRelayFilter,
    ): Boolean {
        // Does not check SINCE on purpose. Avoids replacing the filter if SINCE was all that changed.
        // fast check
        if (filter1.authors?.size != filter2.authors?.size ||
            filter1.ids?.size != filter2.ids?.size ||
            filter1.tags?.size != filter2.tags?.size ||
            filter1.kinds?.size != filter2.kinds?.size ||
            filter1.limit != filter2.limit ||
            filter1.search?.length != filter2.search?.length ||
            filter1.until != filter2.until
        ) {
            return true
        }

        // deep check
        if (filter1.ids != filter2.ids ||
            filter1.authors != filter2.authors ||
            filter1.tags != filter2.tags ||
            filter1.kinds != filter2.kinds ||
            filter1.search != filter2.search
        ) {
            return true
        }

        return false
    }

    fun isDifferent(
        filter1: SinceAuthorPerRelayFilter,
        filter2: SinceAuthorPerRelayFilter,
    ): Boolean {
        // Does not check SINCE on purpose. Avoids replacing the filter if SINCE was all that changed.
        // fast check
        if (filter1.authors?.size != filter2.authors?.size ||
            filter1.ids?.size != filter2.ids?.size ||
            filter1.tags?.size != filter2.tags?.size ||
            filter1.kinds?.size != filter2.kinds?.size ||
            filter1.limit != filter2.limit ||
            filter1.search?.length != filter2.search?.length ||
            filter1.until != filter2.until
        ) {
            return true
        }

        // deep check
        if (filter1.ids != filter2.ids ||
            filter1.authors != filter2.authors ||
            filter1.tags != filter2.tags ||
            filter1.kinds != filter2.kinds ||
            filter1.search != filter2.search
        ) {
            return true
        }

        return false
    }

    fun isDifferent(
        filter1: NormalFilter,
        filter2: NormalFilter,
    ): Boolean {
        // Does not check SINCE on purpose. Avoids replacing the filter if SINCE was all that changed.
        // fast check
        if (filter1.authors?.size != filter2.authors?.size ||
            filter1.ids?.size != filter2.ids?.size ||
            filter1.tags?.size != filter2.tags?.size ||
            filter1.kinds?.size != filter2.kinds?.size ||
            filter1.limit != filter2.limit ||
            filter1.search?.length != filter2.search?.length ||
            filter1.until != filter2.until
        ) {
            return true
        }

        // deep check
        if (filter1.ids != filter2.ids ||
            filter1.authors != filter2.authors ||
            filter1.tags != filter2.tags ||
            filter1.kinds != filter2.kinds ||
            filter1.search != filter2.search
        ) {
            return true
        }

        return false
    }
}
