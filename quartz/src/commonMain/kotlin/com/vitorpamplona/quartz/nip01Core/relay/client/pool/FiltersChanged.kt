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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

object FiltersChanged {
    fun needsToResendRequest(
        oldFilters: List<Filter>,
        newFilters: List<Filter>,
    ): Boolean {
        if (oldFilters.size != newFilters.size) return true

        oldFilters.forEachIndexed { index, oldFilter ->
            val newFilter = newFilters.getOrNull(index) ?: return true

            return needsToResendRequest(oldFilter, newFilter)
        }
        return false
    }

    /**
     * Checks if the filter has changed, with a special case for when the since changes due to new
     * EOSE times.
     */
    fun needsToResendRequest(
        oldFilter: Filter,
        newFilter: Filter,
    ): Boolean {
        // Does not check SINCE on purpose. Avoids replacing the filter if SINCE was all that changed.
        // fast check
        if (oldFilter.authors?.size != newFilter.authors?.size ||
            oldFilter.ids?.size != newFilter.ids?.size ||
            oldFilter.tags?.size != newFilter.tags?.size ||
            oldFilter.kinds?.size != newFilter.kinds?.size ||
            oldFilter.limit != newFilter.limit ||
            oldFilter.search?.length != newFilter.search?.length ||
            oldFilter.until != newFilter.until
        ) {
            return true
        }

        // deep check
        if (oldFilter.ids != newFilter.ids ||
            oldFilter.authors != newFilter.authors ||
            oldFilter.tags != newFilter.tags ||
            oldFilter.kinds != newFilter.kinds ||
            oldFilter.search != newFilter.search
        ) {
            return true
        }

        if (oldFilter.since != null) {
            if (newFilter.since == null) {
                // went was checking the future only and now wants everything
                return true
            } else if (oldFilter.since > newFilter.since) {
                // went backwards in time, forces update
                return true
            }
        }

        return false
    }
}
