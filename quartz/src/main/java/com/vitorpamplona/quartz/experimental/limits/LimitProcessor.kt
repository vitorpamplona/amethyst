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
package com.vitorpamplona.quartz.experimental.limits

import com.vitorpamplona.ammolite.relays.filters.Filter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip13Pow.getPoWRank
import com.vitorpamplona.quartz.utils.TimeUtils

class LimitProcessor {
    fun wrapFilterToLimits(
        filter: Filter,
        sendingStr: String,
        limits: Limits,
    ): Filter? {
        var newFilter: Filter? = filter

        if (limits.canRead != null && !limits.canRead) {
            newFilter = null
        }

        if (limits.maxLimit != null && filter.limit != null && filter.limit > limits.maxLimit) {
            newFilter = filter.copy(limit = limits.maxLimit)
        }

        if (!limits.acceptedEventKinds.isNullOrEmpty() && !filter.kinds.isNullOrEmpty()) {
            val intersect = filter.kinds.filter { it in limits.acceptedEventKinds }
            if (intersect.isNotEmpty()) {
                newFilter = filter.copy(kinds = intersect)
            } else {
                newFilter = null
            }
        }

        if (!limits.blockedEventKinds.isNullOrEmpty() && !filter.kinds.isNullOrEmpty()) {
            val intersect = filter.kinds.filter { it !in limits.blockedEventKinds }
            if (intersect.isNotEmpty()) {
                newFilter = filter.copy(kinds = intersect)
            } else {
                newFilter = null
            }
        }

        if (limits.maxMessageLength != null && sendingStr.length > limits.maxMessageLength) {
            // TODO: figure out how to dynamically reduce filter size
            newFilter = null
        }

        return newFilter
    }

    fun canSendEvent(
        ev: Event,
        sendingStr: String,
        limits: Limits,
    ): Boolean {
        if (limits.canWrite != null && !limits.canWrite) return false

        if (!limits.acceptedEventKinds.isNullOrEmpty() && ev.kind !in limits.acceptedEventKinds) return false
        if (!limits.blockedEventKinds.isNullOrEmpty() && ev.kind in limits.blockedEventKinds) return false

        if (limits.minPoW != null && ev.getPoWRank() < limits.minPoW) return false
        if (limits.maxEventTags != null && ev.tags.size > limits.maxEventTags) return false
        if (limits.maxContentLength != null && ev.content.length > limits.maxContentLength) return false

        if (limits.createdAtMillisecsAgo != null && ev.createdAt < TimeUtils.now() - limits.createdAtMillisecsAgo) return false
        if (limits.createdAtMillisecsAhead != null && ev.createdAt > TimeUtils.now() + limits.createdAtMillisecsAhead) return false

        if (limits.requiredTags != null && !matchAll(ev, limits.requiredTags)) return false

        if (limits.maxMessageLength != null && sendingStr.length > limits.maxMessageLength) return false

        return true
    }

    private fun matchAll(
        ev: Event,
        requiredTags: Array<Array<String>>,
    ): Boolean =
        requiredTags.all { requiredTag ->
            if (requiredTag.isNotEmpty()) {
                if (requiredTag.getOrNull(1) == null) {
                    ev.tags.any { eventTag ->
                        eventTag.getOrNull(0) == requiredTag[0]
                    }
                } else {
                    ev.tags.any { eventTag ->
                        eventTag.getOrNull(0) == requiredTag[0] && eventTag.getOrNull(1) == requiredTag[1]
                    }
                }
            } else {
                true
            }
        }
}
