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
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Downloads all pages of events matching [filters] from a single [relay] using
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
 * A `search` ([Filter.search]) filter is the exception: NIP-50 results are ranked by
 * relevance, not `created_at`, so paging one by a `until` cursor is meaningless — it
 * would silently turn a top-N search into a time-walk, and never terminate against a
 * relay that runs FTS over its whole corpus regardless of `until`. So a search filter
 * is queried on the FIRST page only; it is then dropped from every later page and its
 * hits never advance (nor drag back) the `until` cursor other filters page with. Give
 * it a `limit` to bound that single page; without one you get the relay's default page
 * of top hits.
 *
 * @param relay       The relay to query.
 * @param filters Filters to apply on every page (the `until` field is overwritten per page).
 * @param timeoutMs   Maximum time to wait for a single page's EOSE before giving up.
 * @param onEvent     Called for every event received (in page order, after each EOSE).
 * @return Total number of events received across all pages.
 */
suspend fun INostrClient.fetchAllPages(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    timeoutMs: Long = 30_000L,
    onNewPage: ((Long) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): Int {
    var until: Long? = null
    var totalEvents = 0

    // Track how many matching events each filter has received so far.
    val matchCountPerFilter = IntArray(filters.size)

    // One subscription id reused for every page. Each page opens it (with the
    // page's `until`), waits for EOSE, then closes it before the next page opens
    // it again — so at most one subscription is ever live and the whole download
    // occupies a single subscription slot on the connection (relays cap the
    // number of concurrent subscriptions per connection, so churning through a
    // fresh id per page is wasteful).
    //
    // Reusing the id is safe because the pool serializes the "send a REQ"
    // decision: after each page's EOSE, the pool's auto-resend and this loop's
    // unsubscribe+resubscribe can no longer both fire a REQ for the same id (see
    // PoolRequests.decideCommandLocked / PoolRequestsConcurrencyTest). Without
    // that fix the two raced and produced a duplicate REQ — two EOSEs, or an
    // empty page that silently truncated large results.
    val subId = newSubId()

    while (true) {
        coroutineContext.ensureActive()

        val pagedFilters =
            if (until == null) {
                filters
            } else {
                filters.map {
                    it.copy(until = until)
                }
            }

        // The filters actually queried this page, each kept with its index into
        // matchCountPerFilter. A filter drops out once it has its limit's worth of
        // events; a `search` filter additionally runs on the FIRST page only
        // (until == null), because relevance-ranked results can't be paged by a
        // created_at cursor. The listener below iterates this SAME list, so what we
        // count always matches what we subscribed for.
        val activeFilters =
            pagedFilters.withIndex().filter { (index, filter) ->
                val stillNeedsMore = filter.limit == null || matchCountPerFilter[index] < filter.limit
                val pageableThisPage = until == null || filter.search == null
                stillNeedsMore && pageableThisPage
            }

        if (activeFilters.isEmpty()) break

        // Announce the page only now that we know it will actually be fetched: a
        // search-only filter drops out of activeFilters above and breaks with no
        // REQ, so firing this earlier would report a page that never happens.
        if (until != null) onNewPage?.invoke(until)

        val doneChannel = Channel<Unit>(Channel.CONFLATED)

        var pageCount = 0
        var pageMinTs = Long.MAX_VALUE

        try {
            val listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // Count this event against every active filter it satisfies
                        // (one event can match more than one). Only a non-search filter
                        // may advance the `until` cursor: a search hit — possibly old,
                        // relevance-ranked — must not drag the cursor back and make the
                        // next page skip events a co-resident normal filter still needs.
                        var atLeastOne = false
                        var advancesCursor = false
                        for ((index, filter) in activeFilters) {
                            if (matchCountPerFilter[index] < (filter.limit ?: Int.MAX_VALUE) && filter.match(event)) {
                                matchCountPerFilter[index]++
                                atLeastOne = true
                                if (filter.search == null) advancesCursor = true
                            }
                        }
                        if (atLeastOne) {
                            onEvent(event)
                            pageCount++
                            if (advancesCursor && event.createdAt < pageMinTs) {
                                pageMinTs = event.createdAt
                            }
                        }
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

            subscribe(subId, mapOf(relay to activeFilters.map { it.value }), listener)

            withTimeoutOrNull(timeoutMs) {
                doneChannel.receive()
            }

            unsubscribe(subId)
            doneChannel.close()
        } finally {
            unsubscribe(subId)
            doneChannel.close()
        }

        if (pageCount == 0) break

        totalEvents += pageCount

        // Advance cursor: next page starts just before the oldest event seen.
        until = min((until ?: Long.MAX_VALUE) - 1, pageMinTs - 1)
    }

    return totalEvents
}

suspend fun INostrClient.fetchAllPages(
    relay: String,
    filters: List<Filter>,
    timeoutMs: Long = 30_000L,
    onNewPage: ((Long) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): Int =
    fetchAllPages(
        relay = RelayUrlNormalizer.normalize(relay),
        filters = filters,
        timeoutMs = timeoutMs,
        onNewPage = onNewPage,
        onEvent = onEvent,
    )
