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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Downloads all pages of events matching [baseFilters] from a single [relay] using
 * paginated `until` cursors.
 *
 * After EOSE the oldest [Event.createdAt] seen in that page minus one becomes the
 * next `until`, and the query repeats until the relay returns no new events.
 *
 * Event counting is tracked per filter using [Filter.match]. A filter is considered
 * fulfilled when the number of matching events reaches its [Filter.limit]. Pagination
 * stops when all filters with limits are fulfilled or when a page returns no events.
 * Filters without a limit are considered unbounded and only stop on empty pages.
 *
 * @param relay       The relay to query.
 * @param baseFilters Filters to apply on every page (the `until` field is overwritten per page).
 * @param timeoutMs   Maximum time to wait for a single page's EOSE before giving up.
 * @param onEvent     Called for every event received (in page order, after each EOSE).
 * @return Total number of events received across all pages.
 */
suspend fun INostrClient.downloadFromRelay(
    relay: NormalizedRelayUrl,
    baseFilters: List<Filter>,
    timeoutMs: Long = 30_000L,
    onEvent: (Event) -> Unit,
): Int {
    var until: Long? = null
    var totalEvents = 0

    // Track how many matching events each filter has received so far.
    val matchCountPerFilter = IntArray(baseFilters.size)

    while (true) {
        coroutineContext.ensureActive()

        // Only include filters that still need more events.
        val activeFilterIndices =
            baseFilters.indices.filter { i ->
                val limit = baseFilters[i].limit
                limit == null || matchCountPerFilter[i] < limit
            }

        if (activeFilterIndices.isEmpty()) break

        val activeBaseFilters = activeFilterIndices.map { baseFilters[it] }

        val eventChannel = Channel<Event>(UNLIMITED)
        val doneChannel = Channel<Unit>(Channel.CONFLATED)
        val subId = newSubId()

        val filters =
            if (until == null) {
                activeBaseFilters
            } else {
                activeBaseFilters.map { it.copy(until = until) }
            }

        val listener =
            object : IRequestListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    eventChannel.trySend(event)
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(Unit)
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(Unit)
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(Unit)
                }
            }

        openReqSubscription(subId, mapOf(relay to filters), listener)
        withTimeoutOrNull(timeoutMs) { doneChannel.receive() }
        close(subId)
        eventChannel.close()
        doneChannel.close()

        var pageCount = 0
        var pageMinTs = Long.MAX_VALUE
        for (event in eventChannel) {
            onEvent(event)
            pageCount++
            if (event.createdAt < pageMinTs) pageMinTs = event.createdAt

            // Count this event against every base filter it matches.
            for (i in baseFilters.indices) {
                if (baseFilters[i].match(event)) {
                    matchCountPerFilter[i]++
                }
            }
        }

        if (pageCount == 0) break

        totalEvents += pageCount

        // Advance cursor: next page starts just before the oldest event seen.
        until = pageMinTs - 1
    }

    return totalEvents
}

suspend fun INostrClient.downloadFromRelay(
    relay: String,
    baseFilters: List<Filter>,
    timeoutMs: Long = 30_000L,
    onEvent: (Event) -> Unit,
): Int =
    downloadFromRelay(
        relay = RelayUrlNormalizer.normalize(relay),
        baseFilters = baseFilters,
        timeoutMs = timeoutMs,
        onEvent = onEvent,
    )
