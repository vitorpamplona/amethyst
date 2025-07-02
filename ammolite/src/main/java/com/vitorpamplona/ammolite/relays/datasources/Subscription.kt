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

import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.collections.forEachIndexed

data class Subscription(
    val id: String = newSubId(),
    val onEose: ((time: Long, relayUrl: NormalizedRelayUrl) -> Unit)? = null,
) {
    var relayBasedFilters: List<RelayBasedFilter>? = null // Inactive when null

    fun reset() {
        relayBasedFilters = null
    }

    fun callEose(
        time: Long,
        relay: NormalizedRelayUrl,
    ) {
        onEose?.let { it(time, relay) }
    }

    fun hasChangedFiltersFrom(otherFilters: List<RelayBasedFilter>?): Boolean {
        if (relayBasedFilters == null && otherFilters == null) return false
        if (relayBasedFilters?.size != otherFilters?.size) return true

        relayBasedFilters?.forEachIndexed { index, relaySetFilter ->
            val otherFilter = otherFilters?.getOrNull(index) ?: return true

            if (relaySetFilter.relay != otherFilter.relay) return true

            return isDifferent(relaySetFilter.filter, otherFilter.filter)
        }
        return false
    }

    fun isDifferent(
        filter1: Filter,
        filter2: Filter,
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
