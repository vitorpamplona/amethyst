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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Manages the State of Subscriptions by logging states as the
 * subscription progresses.
 */
class RequestSubscriptionState<T> {
    // Logs the state of each channel to:
    // 1. inform when an event is received as live
    // 2. to block REQs being sent before finished (receiving an EOSE or Closed)
    //
    // If 2 happens, the relay might send multiple EOSEs in sequence
    // for the same sub and we won't know which REQ was it for.
    private val subStates = mutableMapOf<T, ReqSubStatus>()
    private val filterStates = mutableMapOf<T, List<Filter>>()

    /**
     * This cache is used to make sure we know what the relay was processing
     * before a close or disconnect so that if new events still arrive
     * we can link them with the appropriate filters.
     */
    private val lastKnownFilterStates = mutableMapOf<T, List<Filter>>()

    fun currentFilters() = filterStates

    fun currentFilters(reference: T) = filterStates[reference]

    fun lastKnownFilterStates(reference: T) = lastKnownFilterStates[reference]

    fun currentState(reference: T) = subStates[reference]

    fun onNewEvent(reference: T) {
        if (subStates[reference] == ReqSubStatus.SENT) {
            subStates[reference] = ReqSubStatus.QUERYING_PAST
        }
    }

    fun onEose(reference: T) {
        subStates[reference] = ReqSubStatus.LIVE
    }

    fun onClosed(reference: T) {
        subStates[reference] = ReqSubStatus.CLOSED
        // Closed messages are usually relays refusing to process a REQ
        // This message keeps the state of filterStates intact to
        // avoid sending the same filter, and getting immediately closed,
        // over and over again.

        // filterStates.remove(reference)
    }

    fun onOpenReq(
        reference: T,
        filters: List<Filter>,
    ) {
        subStates[reference] = ReqSubStatus.SENT
        filterStates[reference] = filters
        lastKnownFilterStates[reference] = filters
    }

    fun onCloseReq(reference: T) {
        subStates[reference] = ReqSubStatus.CLOSED
        filterStates.remove(reference)
    }

    fun connecting(reference: T) {
        subStates.remove(reference)
        filterStates.remove(reference)
    }

    fun disconnected(reference: T) {
        subStates.remove(reference)
        filterStates.remove(reference)
    }
}
