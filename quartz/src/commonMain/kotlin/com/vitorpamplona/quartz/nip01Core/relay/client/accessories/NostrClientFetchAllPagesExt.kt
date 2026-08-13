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
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.AuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.DEFAULT_AUTH_GRACE_MS
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.authSuccessMark
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.awaitAuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.hasAuthResponder
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Why ONE page stopped. The three terminal signals used to be indistinguishable —
 * all of them put a bare `Unit` on the page's channel — and [fetchAllPages] only
 * ever asked *whether* a page ended, never *how*. [PagedFetchResult] needs the
 * difference: an empty page is proof the relay has nothing older only when the
 * relay actually said so.
 */
private enum class PageSignal {
    /** The relay finished serving its stored events for this REQ. */
    EOSE,

    /** The relay ended the subscription itself — rate limited, policy, unsupported. */
    CLOSED,

    /** The relay ended the subscription with `auth-required:` — see [PagedFetchResult.End.AUTH_REQUIRED]. */
    AUTH_REQUIRED,

    /** Never got to ask. */
    CANNOT_CONNECT,
}

/**
 * What a [fetchAllPages] walk delivered, and why it stopped.
 *
 * The count alone cannot answer the question that matters to anything recording
 * coverage: is there nothing older, or did the relay simply stop giving us more?
 * Those look identical from `downloaded`, and a caller that guesses wrong either
 * re-walks a corpus forever or claims history it never read.
 */
data class PagedFetchResult(
    /** Total number of distinct events delivered across all pages. */
    val downloaded: Int,
    val end: End,
) {
    enum class End {
        /**
         * A page came back empty and the relay EOSEd it: there is nothing at or
         * below the last cursor. The only ending that proves ABSENCE, and so the
         * only one a coverage claim may be built on.
         */
        DRAINED,

        /**
         * A [Filter.limit] was fulfilled. The caller bounded the download itself,
         * so the walk stopped on its own instruction, not at the end of the
         * corpus — nothing below the last event was ever asked for.
         */
        LIMIT_REACHED,

        /**
         * The relay went quiet for `idleTimeoutMs` without ending the page.
         * Silence is not an answer; everything delivered so far is still good.
         */
        IDLE,

        /** The relay ended the subscription — rate limited, policy, unsupported filter. */
        CLOSED,

        /**
         * The relay refused the page with `auth-required:` and the NIP-42 challenge did
         * not satisfy it — no responder attached, one that declined, or an AUTH the relay
         * rejected.
         *
         * Split out of [CLOSED] because the three refusals CLOSED used to cover want
         * three different things from the caller: a rate limit wants a slower retry, a
         * policy refusal wants none, and this one wants a signer the relay accepts.
         * Lumped together, a walk that stopped at an auth wall was indistinguishable from
         * one the relay simply would not serve — and the wall is the only one of the three
         * the caller can actually take down.
         *
         * Proves nothing about what the relay holds, so no coverage claim may rest on it.
         */
        AUTH_REQUIRED,

        /** Never got to ask. */
        CANNOT_CONNECT,

        /**
         * The walk cannot advance its cursor: only `search` hits came back (NIP-50
         * results are relevance-ranked, so they never page), or a first page
         * delivered nothing any active filter matched.
         */
        UNPAGEABLE,
    }

    /**
     * Shorthand for the one ending that licenses skipping work later. Read this
     * rather than comparing to [End.DRAINED] by hand, so the meaning stays in one
     * place if the enum grows.
     */
    val drained: Boolean get() = end == End.DRAINED
}

/**
 * Downloads all pages of events matching [filters] from a single [relay] using
 * paginated `until` cursors.
 *
 * Each page after the first repeats the query with `until = oldest created_at of
 * the previous page` — **inclusive**, not `oldest - 1`. Advancing exclusively would
 * skip any event sharing that boundary second that didn't fit in the page, which
 * happens at *every* page boundary that lands inside a second (not just pathological
 * "dense" seconds), silently dropping events. Re-fetching the boundary second and
 * dropping the events already delivered from it (via [Event.id]) instead retrieves
 * the whole boundary. The dedup set is bounded to just the current boundary second —
 * `until` only ever decreases, so duplicates can only recur there — so memory stays
 * O(one second), never O(total events).
 *
 * Event counting is tracked per filter using [Filter.match]. A filter is considered
 * fulfilled when the number of matching events reaches its [Filter.limit]. Pagination
 * stops when all filters with limits are fulfilled or when a page returns no events.
 * Filters without a limit are considered unbounded and only stop on empty pages.
 *
 * The one unavoidable case: a single `created_at` second holding more events than the
 * relay returns in a page. The inclusive re-fetch then keeps returning the same page
 * and can never advance, so once a page yields nothing new we step strictly past that
 * second (`until = boundary - 1`) and continue. If the second was denser than the
 * relay's page cap its unreachable tail is lost — there is no client-side fix (raising
 * the request `limit` is futile: while paging we already send one above the relay's
 * cap, so a larger value is clamped to the same page). Stepping past at least keeps
 * the download progressing to older events instead of stalling forever.
 *
 * **Two guards keep that step from becoming a walk that never ends**, both learned
 * from a relay in production rather than from reasoning:
 *
 *  - **The cursor floors at zero.** `created_at` is an unsigned timestamp, so nothing
 *    can exist below epoch 0: a cursor that would step under it has reached the bottom
 *    of the time axis and the walk is [PagedFetchResult.End.DRAINED]. `until = 0`
 *    itself is still asked — it is a legal query, and the boundary re-fetch for events
 *    stamped at the epoch — it is only going *below* it that ends the walk. This also
 *    keeps a negative `until` off the wire, which relays disagree violently about:
 *    measured across five, one CLOSEs the subscription with a parse error, three
 *    answer a `NOTICE` and then never EOSE, and one drops the bound and serves its
 *    NEWEST events.
 *  - **A relay that ignores the cursor is [PagedFetchResult.End.UNPAGEABLE].** If a
 *    page delivered nothing and every event it received was NEWER than the `until` it
 *    asked for, the relay is not paging at all, and stepping one second lower just
 *    asks the same unanswered question again. That is exactly how the first guard's
 *    relay behaves — it treats `until <= 0` as no `until` — and without this the walk
 *    ran ~5.5 pages a second, 500 events fetched and discarded on each, EOSE on every
 *    one, for as long as the process lived. UNPAGEABLE is deliberate and conservative:
 *    it proves nothing about what the relay holds, so no coverage claim can be built
 *    on a page the relay never really answered.
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
 * @param idleTimeoutMs   Idle window per page — like every accessory timeout, it is measured
 *   from the relay's **most recent message**, not from the page's start: every arriving
 *   event resets it, so a slow relay actively streaming a large page is never cropped
 *   mid-delivery. A page only gives up after this much silence without an EOSE.
 *
 *   Deliberately no wall-clock ceiling here, unlike [fetchAll]'s `maxTotalMs`. A ceiling
 *   would bound one *page*, not this call: the loop below reacts to a page ending by
 *   advancing the cursor and issuing the next REQ, so a relay trickling events forever
 *   against an unbounded filter would just be re-paged forever — measurably so (see
 *   NostrClientFetchAllPagesIdleTimeoutTest). Worse, cutting a page mid-stream advances
 *   `until` to the oldest event received *so far*, which only preserves the set if the
 *   relay streams strictly newest-first (NIP-01 recommends but does not require it) —
 *   otherwise the not-yet-sent events above that cursor are skipped. What actually
 *   bounds this walk is a [Filter.limit] (the documented way to cap a download) or
 *   cancelling the caller, which the [ensureActive] at the top of each page honors.
 * @param onEvent     Called once for every distinct event delivered, in page order.
 * @return What was delivered and WHY the walk stopped — see [PagedFetchResult].
 *   The reason is part of the answer, not a detail: `downloaded` cannot tell
 *   "the relay has nothing older" from "the relay stopped answering", and a
 *   caller recording sync coverage may only treat the range below the oldest
 *   event it saw as verified-empty in the first case.
 */
suspend fun INostrClient.fetchAllPages(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    onNewPage: ((Long) -> Unit)? = null,
    onEvent: suspend (Event) -> Unit,
): PagedFetchResult {
    // Waiting out an `auth-required:` page refusal is worth something only when this client has
    // a NIP-42 responder to answer with. When it does, the AUTH's OK drives syncFilters, which
    // re-sends this very REQ (same subscription id, same filters — an `auth-required:` refusal
    // is deliberately not recorded as structural, so the pool keeps them) and the page answers
    // normally. Either way the refusal is REPORTED as End.AUTH_REQUIRED, never as CLOSED.
    val pendingOnAuthRequired = hasAuthResponder()
    var until: Long? = null
    var totalEvents = 0
    // At most one auth retry per walk. The AUTH is per-connection, so one success covers
    // every later page; a second refusal after it means the relay wants an identity we do
    // not have, and re-waiting on each page would multiply the grace by the page count.
    var authRetried = false
    // Overwritten by whichever break ends the loop. UNPAGEABLE is the honest
    // default: the two breaks that leave it alone are both "the cursor cannot
    // advance", and it is the reading that licenses the least.
    var end = PagedFetchResult.End.UNPAGEABLE

    // Track how many matching events each filter has received so far.
    val matchCountPerFilter = IntArray(filters.size)

    // Bounded dedup: ids already delivered at exactly the current boundary second
    // (`until`), which the next inclusive page re-fetches. `until` decreases
    // monotonically, so a duplicate can only ever be a boundary-second event —
    // hence no full-history seen-set, and memory is O(one second)'s worth of ids.
    var seenAtBoundary = HashSet<HexKey>()

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

        if (activeFilters.isEmpty()) {
            // Every filter either met its limit or is a search that has had its
            // one page. The first is the caller stopping the walk; the second
            // cannot page at all. Neither is the corpus ending.
            end =
                if (filters.any { it.limit != null }) {
                    PagedFetchResult.End.LIMIT_REACHED
                } else {
                    PagedFetchResult.End.UNPAGEABLE
                }
            break
        }

        // Announce the page only now that we know it will actually be fetched: a
        // search-only filter drops out of activeFilters above and breaks with no
        // REQ, so firing this earlier would report a page that never happens.
        if (until != null) onNewPage?.invoke(until)

        val doneChannel = Channel<PageSignal>(Channel.CONFLATED)

        // Read before this page's REQ goes out — see [awaitAuthOutcome]'s `since`.
        val authMark = if (pendingOnAuthRequired) authSuccessMark(relay) else 0

        // Idle watchdog for this page: every arriving event bumps it, so the page's
        // timeout measures silence since the relay's most recent message (the same
        // convention as fetchAll and the negentropy sync), never total page time.
        val clock = IdleClock()

        // Captured for the listener: the boundary second we re-fetch this page.
        val boundary = until
        var received = 0
        var delivered = 0

        /**
         * Events that came back NEWER than the `until` this page asked for — which an
         * honest relay never sends. Counted because it is the only way to tell a relay
         * that ignored the cursor apart from a boundary second too dense to page: both
         * deliver nothing, and only one of them can be fixed by stepping past.
         */
        var aboveBoundary = 0
        var pageMinTs = Long.MAX_VALUE
        val idsAtPageMin = HashSet<HexKey>()

        // How this page ended, read after the wait: null for an idle timeout, which
        // [receiveWithinIdle] reports by returning null. Only an EOSE can support a
        // drain claim below — silence is not an answer, and a CLOSED is the relay
        // declining to give one.
        var pageEnd: PageSignal? = null

        try {
            val listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // The bump is in a finally so it runs for EVERY event —
                        // including the duplicate that returns early below, which is
                        // still a sign of life — and, being a volatile write, runs
                        // AFTER the counters below. That ordering matters: these
                        // counters are written on the relay's reader thread and read
                        // by the driver coroutine once the wait ends. The EOSE path
                        // gets its happens-before from the channel, but the idle
                        // path has no such edge, so without the release write the
                        // driver could read a stale `pageMinTs` (ending the walk
                        // early) or an unsafely published `idsAtPageMin`.
                        try {
                            received++
                            // Before the dedup return, so it is counted for every event
                            // the page received, not just the ones that reach the match.
                            if (boundary != null && event.createdAt > boundary) aboveBoundary++
                            // Drop a boundary-second event we already delivered on an
                            // earlier page (the inclusive re-fetch returns it again).
                            if (boundary != null && event.createdAt == boundary && event.id in seenAtBoundary) return

                            // Count this event against every active filter it satisfies
                            // (one event can match more than one). Only a non-search filter
                            // may advance the `until` cursor: a search hit — possibly old,
                            // relevance-ranked — must not drag the cursor back and make the
                            // next page skip events a co-resident normal filter still needs.
                            var atLeastOne = false
                            var advancesCursor = false
                            // Indexed loop, not `for ((i, f) in activeFilters)`: this runs for
                            // EVERY event on the relay's reader thread (millions in a bulk
                            // download) and the destructuring form allocates an Iterator per
                            // event. Same reason quartz uses the `fast*` operators elsewhere
                            // in hot event paths — those only cover Array, so a List needs
                            // the index form.
                            for (i in activeFilters.indices) {
                                val active = activeFilters[i]
                                val index = active.index
                                val filter = active.value
                                if (matchCountPerFilter[index] < (filter.limit ?: Int.MAX_VALUE) && filter.match(event)) {
                                    matchCountPerFilter[index]++
                                    atLeastOne = true
                                    if (filter.search == null) advancesCursor = true
                                }
                            }
                            if (atLeastOne) {
                                onEvent(event)
                                delivered++
                                // Track the oldest advancing second and the ids delivered
                                // in it — that becomes the next boundary and its dedup set.
                                if (advancesCursor) {
                                    if (event.createdAt < pageMinTs) {
                                        pageMinTs = event.createdAt
                                        idsAtPageMin.clear()
                                        idsAtPageMin.add(event.id)
                                    } else if (event.createdAt == pageMinTs) {
                                        idsAtPageMin.add(event.id)
                                    }
                                }
                            }
                        } finally {
                            clock.bump()
                        }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        doneChannel.trySend(PageSignal.EOSE)
                    }

                    override fun onClosed(
                        message: String,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (MachineReadablePrefix.parse(message) == MachineReadablePrefix.AUTH_REQUIRED) {
                            doneChannel.trySend(PageSignal.AUTH_REQUIRED)
                        } else {
                            doneChannel.trySend(PageSignal.CLOSED)
                        }
                    }

                    override fun onCannotConnect(
                        relay: NormalizedRelayUrl,
                        message: String,
                        forFilters: List<Filter>?,
                    ) {
                        doneChannel.trySend(PageSignal.CANNOT_CONNECT)
                    }
                }

            subscribe(subId, mapOf(relay to activeFilters.map { it.value }), listener)

            // Wait for the page's terminal signal (EOSE / CLOSED / cannot-connect),
            // giving up only after [idleTimeoutMs] of silence — the wait resets on every
            // arriving event, so an actively streaming page is never cut mid-delivery.
            pageEnd = doneChannel.receiveWithinIdle(clock, idleTimeoutMs)

            // The relay wants NIP-42 before it answers this page. Deliberately do NOT
            // unsubscribe yet: the AUTH's OK re-sends this same subscription id, and the
            // page we are standing in is the one that gets answered. Tearing it down first
            // would leave the post-auth REQ with nothing to refill.
            if (pageEnd == PageSignal.AUTH_REQUIRED && pendingOnAuthRequired && !authRetried) {
                authRetried = true
                if (awaitAuthOutcome(relay, authMark, DEFAULT_AUTH_GRACE_MS, idleTimeoutMs) == AuthOutcome.AUTHENTICATED) {
                    // Silence so far was the AUTH round-trip, not the relay stalling, so the
                    // idle window starts over for the re-served page.
                    clock.bump()
                    pageEnd = doneChannel.receiveWithinIdle(clock, idleTimeoutMs)
                }
            }

            unsubscribe(subId)
            doneChannel.close()
        } finally {
            unsubscribe(subId)
            doneChannel.close()
        }

        totalEvents += delivered

        // The relay sent nothing at-or-below `until`. Whether that DRAINS the set
        // depends on why the page ended and on what was asked:
        //
        //  - only an EOSE proves absence. An idle timeout (`pageEnd == null`) is
        //    silence and a CLOSED is the relay declining to answer; reading either
        //    as "nothing older exists" would durably record coverage the relay
        //    never served, which is the one error a coverage claim must not make.
        //  - a filter that reached its [Filter.limit] stopped early on the caller's
        //    own instruction, so nothing below its last event was ever asked for.
        //  - a `search` filter runs on the first page only, so every page after it
        //    dropped out never carried it and cannot speak for it.
        if (received == 0) {
            val cappedByLimit =
                filters.indices.any { i ->
                    val limit = filters[i].limit
                    limit != null && matchCountPerFilter[i] >= limit
                }
            end =
                when {
                    pageEnd == PageSignal.AUTH_REQUIRED -> PagedFetchResult.End.AUTH_REQUIRED
                    pageEnd == PageSignal.CLOSED -> PagedFetchResult.End.CLOSED
                    pageEnd == PageSignal.CANNOT_CONNECT -> PagedFetchResult.End.CANNOT_CONNECT
                    pageEnd == null -> PagedFetchResult.End.IDLE
                    cappedByLimit -> PagedFetchResult.End.LIMIT_REACHED
                    filters.any { it.search != null } -> PagedFetchResult.End.UNPAGEABLE
                    else -> PagedFetchResult.End.DRAINED
                }
            break
        }

        if (delivered == 0) {
            // Every event this page was a boundary-second duplicate; nothing older
            // came back. Either the boundary second is exhausted (and there is
            // nothing older → the step's next page is empty and we stop) or it is
            // denser than the relay's page and keeps refilling it (stuck → the step
            // recovers progress, dropping only the second's unreachable tail). Both
            // are resolved by stepping strictly past it. `boundary` is null only on
            // the first page, which has no dedup and so can't be all-duplicate.
            val step = boundary ?: break // first page, all-duplicate: impossible, and `end` stays UNPAGEABLE

            // The relay is not honouring `until`: every event it sent was NEWER than
            // the cursor this page asked for. Stepping past cannot help — the next
            // page repeats the same ask one second lower and gets the same answer,
            // forever. Measured on a live relay (purplepag.es, which treats
            // `until <= 0` as no `until` and answers with its newest page): ~5.5
            // pages a second, 500 events fetched and discarded on each, `until`
            // marching one second further negative every time, an EOSE on every
            // single page, for as long as the process ran. This is the ONE reading
            // that ends it, and it is safely conservative — UNPAGEABLE proves
            // nothing about what the relay holds, so no coverage claim is built on
            // a page the relay never actually answered.
            if (aboveBoundary == received) {
                end = PagedFetchResult.End.UNPAGEABLE
                break
            }

            // Below the boundary there is nothing left to ask for: `created_at` is an
            // unsigned timestamp, so no event can exist under epoch 0 and a cursor
            // stepping past it has reached the bottom of the time axis. Ending here
            // rather than sending `until = -1` also keeps a value off the wire that
            // relays disagree violently about — measured across five: one CLOSEs the
            // subscription with a parse error, three answer a NOTICE and then never
            // EOSE (so every page burns a whole idle timeout), one drops the bound
            // and serves its newest events.
            if (step <= 0L) {
                end = PagedFetchResult.End.DRAINED
                break
            }
            until = step - 1
            seenAtBoundary = HashSet()
            continue
        }

        // Only search hits advanced nothing pageable → can't page further.
        if (pageMinTs == Long.MAX_VALUE) {
            end = PagedFetchResult.End.UNPAGEABLE
            break
        }

        // Advance inclusively to the oldest second seen, carrying its dedup set:
        // still the same boundary → accumulate; a genuinely older one → replace.
        // Clamp to `boundary` so a misbehaving relay that answers with an event past
        // the requested `until` can't push the cursor UPWARD — the boundary dedup and
        // termination both rely on `until` never increasing. Honest relays only
        // return events at-or-below `until`, so this is a no-op for them.
        val nextUntil = if (boundary != null) minOf(pageMinTs, boundary) else pageMinTs

        // The same floor as the step above, on the other way the cursor moves. It is
        // reachable here too, and not only through a bug: `pageMinTs` is an event's
        // own `created_at`, so one relay serving a negative timestamp is enough to
        // put the cursor under zero. Clamping to 0 instead of stopping would not
        // help — such an event never equals the boundary, so it dodges the dedup and
        // comes back on every page, pinning the walk there for good.
        if (nextUntil < 0L) {
            end = PagedFetchResult.End.DRAINED
            break
        }
        if (boundary != null && nextUntil == boundary) {
            seenAtBoundary.addAll(idsAtPageMin)
        } else {
            seenAtBoundary = idsAtPageMin
        }
        until = nextUntil
    }

    return PagedFetchResult(totalEvents, end)
}

suspend fun INostrClient.fetchAllPages(
    relay: String,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    onNewPage: ((Long) -> Unit)? = null,
    onEvent: suspend (Event) -> Unit,
): PagedFetchResult =
    fetchAllPages(
        relay = RelayUrlNormalizer.normalize(relay),
        filters = filters,
        idleTimeoutMs = idleTimeoutMs,
        onNewPage = onNewPage,
        onEvent = onEvent,
    )
