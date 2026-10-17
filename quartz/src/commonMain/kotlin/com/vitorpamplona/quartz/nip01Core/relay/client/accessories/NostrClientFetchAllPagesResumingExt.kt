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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log

/**
 * [fetchAllPages] that outlives its connection. A walk can end without the relay's word on
 * where it stopped: the socket dropped and the next page could not be asked
 * ([PagedFetchResult.End.CANNOT_CONNECT]), or a page went silent ([PagedFetchResult.End.IDLE]).
 * Each such ending is waited out on [resumeBackoff] and the walk asked again from where it
 * got to, so a relay that hangs up every few hundred thousand events (relay.ditto.pub does)
 * is read to its end in one call instead of one call per connection.
 *
 * [filters] are split into walks exactly as [fetchAllPages] splits them (the search filters
 * once, together, then each pageable filter alone, with its cross-walk dedup), and each
 * pageable walk resumes on its own cursor: the oldest `created_at` it was delivered, `until`
 * inclusive as in [fetchAllPages], with the events of that boundary second already delivered
 * not delivered again. A filter with a [Filter.limit] resumes with what is left of it.
 * Searches are relevance-ranked, one page, and never resumed.
 *
 * Only a walk that got somewhere is resumed through the whole backoff. One whose connection ended
 * before a single event is asked once more, after [resumeBackoff]'s first delay: a relay that took
 * the connection and closed it before answering (overloaded, restarting, still writing a page to
 * the socket it dropped) usually answers the next one, and one that is down costs that one delay.
 * A relay that stays silent before delivering anything ends the walk at once, as [fetchAllPages]
 * would: each re-ask of a silent relay costs a whole idle window.
 *
 * A walk that ends [PagedFetchResult.End.DRAINED] after at least [CONFIRM_AFTER_PAGES] pages
 * and something new is confirmed once, after [resumeBackoff]'s first delay, from its cursor: a
 * relay answering a deep page empty while it still holds events below it ended a walk of
 * relay.ditto.pub's kind 3s 1.58M events early, reporting the end of the corpus. On an honest
 * relay the confirmation costs three REQs (the bottom second, once more to see nothing new in
 * it, and the empty page below it), and a confirmation that finds nothing new, or fails to get
 * an answer, leaves the walk DRAINED. Shorter walks are not confirmed: a bounded fetch of a page
 * or two would pay the wait on every call.
 *
 * The backoff is per stall: any resumed walk that delivers something new resets it, and a
 * stall that outlasts it ends the walk with its last ending. A refusal (`CLOSED`,
 * `auth-required`), a limit met and an unpageable search are the relay's answers and end it
 * at once. [onResume] hears each re-ask with its attempt number within the stall.
 */
suspend fun INostrClient.fetchAllPagesResuming(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    onNewPage: ((Long) -> Unit)? = null,
    pageSize: Int? = null,
    throttleBackoff: PageRetryBackoff = PageRetryBackoff.DEFAULT,
    resumeBackoff: PageRetryBackoff = PageRetryBackoff.RESUME,
    onResume: ((attempt: Int, last: PagedFetchResult) -> Unit)? = null,
    onEvent: suspend (Event) -> Unit,
): PagedFetchResult {
    require(pageSize == null || pageSize > 0) { "pageSize must be positive: $pageSize" }
    return walkEachFilter(filters, onEvent) { walk, deliver ->
        val filter = walk.singleOrNull()
        if (filter == null || filter.isRankedSearch()) {
            walkPages(relay, walk, idleTimeoutMs, onNewPage, pageSize, throttleBackoff, deliver)
        } else {
            resumingWalk(relay, filter, idleTimeoutMs, onNewPage, pageSize, throttleBackoff, resumeBackoff, onResume, deliver)
        }
    }
}

/**
 * Pages a [DRAINED][PagedFetchResult.End.DRAINED] walk must have read before it is confirmed:
 * the empty-page-too-early failure was seen deep in a walk, and a shallow one confirming would
 * make every bounded fetch wait out a backoff delay.
 */
const val CONFIRM_AFTER_PAGES = 10

/** One pageable [filter]'s walk, resumed from its cursor after each stall (see [fetchAllPagesResuming]). */
private suspend fun INostrClient.resumingWalk(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long,
    onNewPage: ((Long) -> Unit)?,
    pageSize: Int?,
    throttleBackoff: PageRetryBackoff,
    resumeBackoff: PageRetryBackoff,
    onResume: ((attempt: Int, last: PagedFetchResult) -> Unit)?,
    onEvent: suspend (Event) -> Unit,
): PagedFetchResult {
    val cursor = FilterCursor(filter)
    var downloaded = 0
    var pages = 0
    var attempt = 0
    // The DRAINED ending being confirmed: what the walk reports if the re-ask finds nothing new.
    var confirming: PagedFetchResult? = null
    var asking = filter
    while (true) {
        var deliveredNew = 0
        pages++
        val result =
            walkPages(
                relay,
                listOf(asking),
                idleTimeoutMs,
                { until ->
                    pages++
                    onNewPage?.invoke(until)
                },
                pageSize,
                throttleBackoff,
            ) { event ->
                if (cursor.alreadyDelivered(event)) return@walkPages
                onEvent(event)
                // Recorded only once handed on: an [onEvent] cancelled where it suspends (the socket
                // ending under it) delivered nothing, and its next copy must not look like a repeat.
                cursor.record(event)
                deliveredNew++
            }
        downloaded += deliveredNew
        if (deliveredNew > 0) {
            attempt = 0
            confirming = null
        } else if (confirming != null) {
            // Nothing below the bottom the walk reported, or no answer to the question: either
            // way nothing contradicts the DRAINED it already had.
            return confirming.copy(downloaded = downloaded)
        }

        val stalled = result.end == PagedFetchResult.End.CANNOT_CONNECT || result.end == PagedFetchResult.End.IDLE
        val toConfirm = result.end == PagedFetchResult.End.DRAINED && deliveredNew > 0 && pages >= CONFIRM_AFTER_PAGES
        val next = cursor.resumeFilter()
        val unanswered = downloaded == 0 && (result.end != PagedFetchResult.End.CANNOT_CONNECT || attempt >= 1)
        if (!(stalled || toConfirm) || unanswered || next == null || attempt >= resumeBackoff.delaysMs.size) {
            return result.copy(downloaded = downloaded)
        }
        if (toConfirm) confirming = result
        val waitMs = resumeBackoff.delaysMs[attempt++]
        Log.d("fetchAllPagesResuming") { "${relay.url} walk ended ${result.end} (${result.message.orEmpty()}); asking again in ${waitMs}ms (#$attempt)" }
        onResume?.invoke(attempt, result)
        resumeBackoff.sleep(waitMs)
        asking = next
    }
}

/**
 * Where one filter's walk got to: the oldest `created_at` it was delivered, the ids delivered at
 * that second (the only ones a walk resumed at it can repeat), and how much of its limit is used.
 * One cursor per walk, fed only that walk's events: a cursor fed another filter's events could
 * pass a second its own walk never read.
 */
private class FilterCursor(
    val filter: Filter,
) {
    private var oldest: Long? = null
    private val atOldest = HashSet<HexKey>()
    private var matched = 0

    fun alreadyDelivered(event: Event): Boolean = event.createdAt == oldest && event.id in atOldest

    fun record(event: Event) {
        if (!filter.match(event)) return
        matched++
        val at = oldest
        when {
            at == null || event.createdAt < at -> {
                oldest = event.createdAt
                atOldest.clear()
                atOldest.add(event.id)
            }

            event.createdAt == at -> {
                atOldest.add(event.id)
            }
        }
    }

    /**
     * The filter asked from where it got to, or null once its limit is met. The limit left is
     * widened by the boundary second's delivered events, which the resumed walk repeats and
     * [alreadyDelivered] drops: counted against it, they would end the walk short.
     */
    fun resumeFilter(): Filter? {
        val left = filter.limit?.let { it - matched }
        if (left != null && left <= 0) return null
        val until = oldest?.let { minOf(it, filter.until ?: it) } ?: filter.until
        return filter.copy(until = until, limit = left?.let { it + atOldest.size })
    }
}
