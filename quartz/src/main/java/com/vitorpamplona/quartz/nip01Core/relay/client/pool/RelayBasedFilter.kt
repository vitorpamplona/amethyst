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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Represents a filter that should only be sent to a given relay.
 */
class RelayBasedFilter(
    val relay: NormalizedRelayUrl,
    val filter: Filter,
)

fun List<RelayBasedFilter>.groupByRelay(): Map<NormalizedRelayUrl, List<Filter>> {
    val result = mutableMapOf<NormalizedRelayUrl, MutableList<Filter>>()
    for (relayBasedFilter in this) {
        if (relayBasedFilter.filter.isFilledFilter()) {
            result.getOrPut(relayBasedFilter.relay) { mutableListOf() }.add(relayBasedFilter.filter)
        } else {
            Log.e("FilterError", "Ignoring empty filter for ${relayBasedFilter.relay}")
        }
    }
    return result
}
