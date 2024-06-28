/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.ammolite.relays

import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString().substring(0, 4),
    val onEOSE: ((Long, String) -> Unit)? = null,
) {
    var typedFilters: List<TypedFilter>? = null // Inactive when null

    fun updateEOSE(
        time: Long,
        relay: String,
    ) {
        onEOSE?.let { it(time, relay) }
    }

    fun hasChangedFiltersFrom(otherFilters: List<TypedFilter>?): Boolean {
        if (typedFilters == null && otherFilters == null) return false
        if (typedFilters?.size != otherFilters?.size) return true

        typedFilters?.forEachIndexed { index, typedFilter ->
            val otherFilter = otherFilters?.getOrNull(index) ?: return true

            // Does not check SINCE on purpose. Avoids replacing the filter if SINCE was all that changed.
            // fast check
            if (typedFilter.filter.authors?.size != otherFilter.filter.authors?.size ||
                typedFilter.filter.ids?.size != otherFilter.filter.ids?.size ||
                typedFilter.filter.tags?.size != otherFilter.filter.tags?.size ||
                typedFilter.filter.kinds?.size != otherFilter.filter.kinds?.size ||
                typedFilter.filter.limit != otherFilter.filter.limit ||
                typedFilter.filter.search?.length != otherFilter.filter.search?.length ||
                typedFilter.filter.until != otherFilter.filter.until
            ) {
                return true
            }

            // deep check
            if (typedFilter.filter.ids != otherFilter.filter.ids ||
                typedFilter.filter.authors != otherFilter.filter.authors ||
                typedFilter.filter.tags != otherFilter.filter.tags ||
                typedFilter.filter.kinds != otherFilter.filter.kinds ||
                typedFilter.filter.search != otherFilter.filter.search
            ) {
                return true
            }
        }
        return false
    }
}
