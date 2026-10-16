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
 * "Where it got to" is per filter: each filter resumes at the oldest `created_at` it was
 * delivered, `until` inclusive as in [fetchAllPages], and the events of that boundary second
 * already delivered are not delivered again. A filter with a [Filter.limit] resumes with what
 * is left of it, and drops out once that is met.
 *
 * A walk that ends [PagedFetchResult.End.DRAINED] after delivering something is confirmed
 * once, after [resumeBackoff]'s first delay, from its cursor: a relay answering a deep page
 * empty while it still holds events below it ended a walk of relay.ditto.pub's kind 3s 1.58M
 * events early, reporting the end of the corpus. On an honest relay the confirmation costs three
 * REQs: the bottom second, once more to see nothing new in it, and the empty page below it.
 *
 * The backoff is per stall: any resumed walk that delivers something new resets it, and a
 * stall that outlasts it ends the call with the last walk's own ending. A refusal (`CLOSED`,
 * `auth-required`), a limit met and an unpageable search are the relay's answers and end it
 * at once. [onResume] hears each re-ask with its attempt number within the stall.
 *
 * An event two of [filters] both match may be delivered twice across a resume; the pool walks
 * ([fetchAllPagesFromPool] and its stream) deduplicate by id over the whole call.
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
    val cursors = filters.map { FilterCursor(it) }
    var downloaded = 0
    var attempt = 0
    var confirmed = false
    var asking = filters
    while (true) {
        var deliveredNew = 0
        val result =
            fetchAllPages(relay, asking, idleTimeoutMs, onNewPage, pageSize, throttleBackoff) { event ->
                if (cursors.any { it.alreadyDelivered(event) }) return@fetchAllPages
                cursors.forEach { it.record(event) }
                deliveredNew++
                onEvent(event)
            }
        downloaded += deliveredNew
        if (deliveredNew > 0) {
            attempt = 0
            confirmed = false
        }

        val stalled = result.end == PagedFetchResult.End.CANNOT_CONNECT || result.end == PagedFetchResult.End.IDLE
        // One re-ask past a DRAINED that delivered: it must find nothing new to be believed.
        val toConfirm = result.end == PagedFetchResult.End.DRAINED && deliveredNew > 0 && !confirmed
        val next = cursors.mapNotNull { it.resumeFilter() }
        if (!(stalled || toConfirm) || next.isEmpty() || next.any { it.isRankedSearch() } || attempt >= resumeBackoff.delaysMs.size) {
            return result.copy(downloaded = downloaded)
        }
        if (toConfirm) confirmed = true
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
