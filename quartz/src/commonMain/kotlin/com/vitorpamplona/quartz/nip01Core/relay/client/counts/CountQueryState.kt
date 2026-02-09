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
package com.vitorpamplona.quartz.nip01Core.relay.client.counts

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

class CountQueryState<T> {
    // Logs the state of each channel to:
    // 1. inform when an event is received as live
    // 2. to block COUNTs being sent before finished (receiving an COUNT or Closed)
    //
    // If 2 happens, the relay might send multiple COUNTs in sequence
    // for the same sub and we won't know which COUNT was it for.
    private val queryStates = mutableMapOf<T, CountQueryStatus>()
    private val filterStates = mutableMapOf<T, List<Filter>>()

    fun currentFilters() = filterStates

    fun currentFilters(reference: T) = filterStates[reference]

    fun currentState(reference: T) = queryStates[reference]

    fun onCountReply(reference: T) {
        queryStates.put(reference, CountQueryStatus.RECEIVED)
    }

    fun onClosed(reference: T) {
        queryStates.put(reference, CountQueryStatus.CLOSED)
    }

    fun onQuery(
        reference: T,
        filters: List<Filter>,
    ) {
        queryStates.put(reference, CountQueryStatus.SENT)
        filterStates.put(reference, filters)
    }

    fun onCloseQuery(reference: T) {
        queryStates.put(reference, CountQueryStatus.CLOSED)
    }

    fun connecting(reference: T) {
        queryStates.remove(reference)
        filterStates.remove(reference)
    }

    fun disconnected(reference: T) {
        queryStates.remove(reference)
    }
}
