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
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayReqRefusals
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

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
    /**
     * The relay's own words when it refused the walk ([End.CLOSED], [End.AUTH_REQUIRED],
     * [End.CANNOT_CONNECT]): the CLOSED message (`"rate-limited: slow down"`) or the
     * transport's error. Null for every other ending. Often the only explanation for an
     * empty result, so a caller reporting one should show it.
     */
    val message: String? = null,
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
 * Whether a [fetchAllPages] page is over. Its subscription outlives it by one page (see
 * `openSubId` there), so its listener checks this before acting on anything the relay still
 * sends: written by the walk, read on the relay's reader thread.
 */
private class PageGate {
    @Volatile
    var over = false
}

/**
 * Opt-in diagnostics for [fetchAllPages]: when [enabled], each page's debug log line also says
 * how the relay ordered and repeated its events (out of order, newer than `until`, repeated on
 * the page, delivered on an earlier page). The last needs a set of every id the walk delivered,
 * so this is for investigating a relay (`amy --verbose`), never for normal use.
 */
object PagingDiagnostics {
    @Volatile
    var enabled: Boolean = false
}

/**
 * Reads a `CLOSED` reason as a refusal of the `REQ`'s `limit` — purplepag.es answers
 * `limit = 50000` with `blocked: limit too high: 50000 (max 500)` — and returns the largest
 * limit worth asking for instead: the relay's stated max when the message names one below
 * [sent], otherwise half of [sent]. Null when the message is not about the event limit or
 * [sent] cannot go any lower. Relays say "limit" about other things too, and halving the
 * event limit fixes none of them: a rate limit is throttling ([isThrottleMessage]), and a cap
 * on open subscriptions ("number of subscriptions exceeds limit") or on filters per REQ is
 * [AdaptiveRelayLimiter]'s and [RelayReqRefusals]'s to handle.
 *
 * Seen on relays (2026-10 surveys: 743 relays, then one or two per relay software from 5,728
 * NIP-11 documents): `blocked: limit too high: 5000 (max 500)` (purplepag.es),
 * `invalid: limitation.max_limit 1000` (relay.cxplay.org) and `restricted: limit must not
 * exceed 500` (relay.wavefunc.live).
 */
internal fun lowerLimitAfterRefusal(
    message: String?,
    sent: Int,
): Int? {
    if (message == null || sent <= 1) return null
    if (!message.contains("limit", ignoreCase = true)) return null
    if (isThrottleMessage(message)) return null
    if (AdaptiveRelayLimiter.isSubscriptionLimitMessage(message)) return null
    if (RelayReqRefusals.parseMaxFilters(message) != null || TOO_MANY_FILTERS.containsMatchIn(message)) return null
    val stated =
        (STATED_MAX.find(message) ?: STATED_CEILING.find(message))
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    return if (stated != null && stated in 1 until sent) stated else sent / 2
}

/**
 * True when a relay's message says it is throttling this client rather than refusing what was
 * asked: a `rate-limited:` prefix, the wording [AdaptiveRelayLimiter] treats as a rate limit,
 * or a spent read budget (relay.damus.io and the bostr proxies in front of it: `ERROR: read
 * bandwidth budget exhausted (1048570 bytes/min per IP) (retry in 30ms)`). Waiting helps;
 * asking for less does not.
 */
internal fun isThrottleMessage(message: String): Boolean =
    MachineReadablePrefix.parse(message) == MachineReadablePrefix.RATE_LIMITED ||
        AdaptiveRelayLimiter.isRateLimitMessage(message) ||
        BUDGET_EXHAUSTED.containsMatchIn(message)

private val BUDGET_EXHAUSTED = Regex("budget (?:is )?exhausted|retry in \\d", RegexOption.IGNORE_CASE)

private val TOO_MANY_FILTERS = Regex("(?:too many|number of|max(?:imum)?) filters", RegexOption.IGNORE_CASE)

// "limit must not exceed 500" (relay.wavefunc.live), "limit exceeds 200": the cap without "max".
private val STATED_CEILING = Regex("exceeds?\\D{0,12}?(\\d+)", RegexOption.IGNORE_CASE)

private val STATED_MAX = Regex("max(?:imum)?\\D{0,12}?(\\d+)", RegexOption.IGNORE_CASE)

/**
 * How long [fetchAllPages] waits before re-asking a page a relay answered empty (or CLOSED)
 * when its own earlier answers say it is throttling, not done — see [fetchAllPages]:
 * one entry per re-ask, so the list's size is the bound. Once it is used up the page's
 * answer is believed and the walk ends as it always would have. The count resets whenever a
 * page delivers something, so it bounds each stall, never the whole walk.
 *
 * [sleep] is the wait itself, injectable so a test can record the delays instead of
 * spending them.
 */
class PageRetryBackoff(
    val delaysMs: List<Long>,
    val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        /** Three re-asks, 2 s, 5 s and 10 s apart: at most 17 s spent on one stall. */
        val DEFAULT = PageRetryBackoff(listOf(2_000L, 5_000L, 10_000L))

        /** Never re-ask: an empty EOSEd page always ends the walk. */
        val NONE = PageRetryBackoff(emptyList())
    }
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
 * the request `limit` is futile: the relay clamps it to the same page, or refuses it).
 * Stepping past at least keeps the download progressing to older events instead of
 * stalling forever.
 *
 * **A filter's [Filter.limit] caps the walk, not the page.** Each `REQ` asks for what the
 * filter still needs (plus the boundary second's repeats), so a relay that serves 50000 in
 * one `REQ` is walked in one page, and one that clamps to its own max (strfry and most
 * others) is walked at that max. A relay that refuses an oversized limit instead —
 * purplepag.es answers `limit = 50000` with `CLOSED blocked: limit too high: 50000 (max
 * 500)` — has the same page re-asked lower, at the max its message states or else at half
 * the refused limit, and the walk keeps that cap from then on. That is done for a lone
 * pageable filter only: several filters share one cursor, so cutting one short would skip
 * the gap down to another filter's oldest event; against a relay that refuses large limits,
 * walk them one at a time. A filter without a limit is sent without one: the relay's
 * default page.
 *
 * **A throttled relay is re-asked before it is believed.** relay.damus.io, paged quickly
 * on one connection, shrinks its pages to a handful of events and then EOSEs an EMPTY page
 * for a range that holds hundreds; a fresh subscription id does not help, time does. So an
 * empty EOSEd page that the relay's own earlier answers contradict — after a page under half
 * the size of the largest it served, either re-asking the boundary second it just served
 * events in, or after two such short pages in a row — is re-asked at the same cursor after
 * each of [throttleBackoff]'s delays. Only once those run out is it taken as
 * [PagedFetchResult.End.DRAINED]. An honestly paging relay never produces either reading
 * (its walk ends on one short tail page, the boundary repeat and an empty page below the
 * step), so it never waits. The same relay sometimes ends the shrinking run with a CLOSED
 * (`read bandwidth budget exhausted`) instead; a CLOSED right after a short page gets the
 * same re-asks and, if it outlasts them, still ends the walk as [PagedFetchResult.End.CLOSED].
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
 * **NIP-67 completeness hints.** A relay may append hints to a page's `EOSE`:
 *
 *  - `"finish"` — every stored match was sent, so the walk stops right there instead of
 *    spending one more REQ just to observe an empty page. It ends
 *    [PagedFetchResult.End.DRAINED] (or LIMIT_REACHED / UNPAGEABLE when a filter had
 *    already dropped out of the page, since `finish` can only speak for what was asked).
 *  - `"more"` — the relay holds more; paging continues, which is what the walk does
 *    anyway until it sees an empty page, so this needs no special handling.
 *  - `"auth"` — more may be available after NIP-42. Handled like an `auth-required:`
 *    CLOSED: when a responder is attached the walk waits (once) for the AUTH verdict and,
 *    on success, reads the page the relay re-serves after the AUTH's re-REQ, dropping the
 *    events it already delivered. If the AUTH does not happen, the page's events still
 *    count but the walk can no longer claim DRAINED: it ends
 *    [PagedFetchResult.End.AUTH_REQUIRED] wherever it would have ended DRAINED.
 *
 * Hints are only ever a shortcut; their absence changes nothing (the heuristic above).
 *
 * @param relay       The relay to query.
 * @param filters Filters to apply on every page (the `until` field is overwritten per page).
 * @param idleTimeoutMs   Idle window per page — like every accessory timeout, it is measured
 *   from the relay's **most recent message**, not from the page's start: every arriving
 *   event resets it, so a slow relay actively streaming a large page is never cropped
 *   mid-delivery. A page only gives up after this much silence without an EOSE.
 *
 *   Deliberately no wall-clock ceiling here — no accessory has one. A ceiling
 *   would bound one *page*, not this call: the loop below reacts to a page ending by
 *   advancing the cursor and issuing the next REQ, so a relay trickling events forever
 *   against an unbounded filter would just be re-paged forever — measurably so (see
 *   NostrClientFetchAllPagesIdleTimeoutTest). Worse, cutting a page mid-stream advances
 *   `until` to the oldest event received *so far*, which only preserves the set if the
 *   relay streams strictly newest-first (NIP-01 recommends but does not require it) —
 *   otherwise the not-yet-sent events above that cursor are skipped. What actually
 *   bounds this walk is a [Filter.limit] (the documented way to cap a download) or
 *   cancelling the caller, which the [ensureActive] at the top of each page honors.
 * @param pageSize    A cap the caller already knows the relay enforces (its NIP-11
 *   `limitation.max_limit`), so a lone bounded filter never asks above it and no refusal has
 *   to teach it. Null (the default) asks for the whole remainder and learns a refusing
 *   relay's cap from its `CLOSED`. It never changes what is downloaded, only how many `REQ`s
 *   it takes.
 * @param throttleBackoff How an empty page the relay's earlier answers contradict is re-asked
 *   (see above). [PageRetryBackoff.NONE] takes every empty EOSEd page at its word.
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
    pageSize: Int? = null,
    throttleBackoff: PageRetryBackoff = PageRetryBackoff.DEFAULT,
    onEvent: suspend (Event) -> Unit,
): PagedFetchResult {
    require(pageSize == null || pageSize > 0) { "pageSize must be positive: $pageSize" }
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

    // Each page has its own subscription, opened BEFORE the previous page's is closed
    // (make-before-break), so the relay is never without one of this walk's subscriptions.
    // [NostrClient] samples the relays its subscriptions want every 300 ms and disconnects the
    // rest; closing a page before opening the next let a sample land in the gap (cursor
    // bookkeeping, a pacing pause, a backoff), which dropped the relay mid-walk. Measured on
    // production relays: the next page paid a new WebSocket + TLS handshake (~0.9 s on yabu.me),
    // and the old connection's teardown racing the new one delivered whole pages twice
    // (relay.nostrcheck.me: 1840 events for a 920-event page) or left a REQ unanswered until the
    // idle timeout. At most two of the walk's subscriptions are open at once, and only for the
    // moment between the next REQ and the previous CLOSE. A page's listener ignores anything
    // that arrives after its page is over (see [PageGate]). The walk closes whichever one is
    // still open on every exit, cancellation included.
    var openSubId: String? = null

    // The text of the last refusal (CLOSED message or connection error), written on the
    // relay's reader thread before its signal is sent; the channel orders it, as with the
    // EOSE hints below.
    var refusal: String? = null

    // Throttle evidence for the empty-page re-ask (see [PageRetryBackoff]):
    //  - the filter indices active on the page that set the current boundary. Only while
    //    the SAME filters are asked again does an empty inclusive re-fetch contradict what
    //    the relay just served (a filter that dropped out may have been the one matching).
    //  - the largest page the relay has answered so far, and how many delivering pages in
    //    a row came back shorter than it.
    //  - re-asks spent on the current cursor; reset by any page that delivers something.
    var boundaryFilters: List<Int>? = null
    var largestPage = 0
    var shortPagesInARow = 0
    var lastPageable: List<Int>? = null
    // The filters that delivered on the last page that delivered anything. One of them coming
    // back empty is a filter running dry, which shrinks every later page honestly.
    var lastProductive: List<Int>? = null
    var reAsks = 0
    // Pacing for a relay that keeps answering with short pages (see below): the wait to take
    // before the next REQ, and how many paced pages in a row it has been.
    var paceMs = 0L
    var pacedPages = 0
    // The last cursor reported to onNewPage: a re-asked cursor is one page, however many REQs.
    var announcedUntil: Long? = null

    // The most a lone bounded filter asks for in one REQ: what the caller knows the relay
    // enforces, else unknown (ask for everything) until a refusal of the limit sets it.
    var limitCap: Int? = pageSize
    // The relay refused a boundary-second top-up above [limitCap]: that second's tail is out
    // of reach, so stop asking past the cap (each ask would be refused again).
    var topUpRefused = false

    // Numbers the REQs of this walk in the debug log, retries included.
    var reqCount = 0
    // Only with [PagingDiagnostics.enabled] (`amy --verbose`): every id handed to [onEvent], to
    // report a relay that re-serves events the cursor already passed. O(walk) memory, so it is
    // an explicit opt-in, not tied to the log level (the desktop app always logs DEBUG).
    val walkIds: HashSet<HexKey>? = if (PagingDiagnostics.enabled) HashSet() else null

    try {
        while (true) {
            coroutineContext.ensureActive()

            // The filters actually queried this page, each kept with its index into
            // matchCountPerFilter (and so into [filters], whose `limit` stays the TOTAL
            // the caller asked for). A filter drops out once it has its limit's worth of
            // events; a `search` filter additionally runs on the FIRST page only
            // (until == null), because relevance-ranked results can't be paged by a
            // created_at cursor. The listener below iterates this SAME list, so what we
            // count always matches what we subscribed for.
            //
            // What goes on the wire is never more than a filter still needs: its remainder,
            // topped up by the boundary second's already-delivered events, which the inclusive
            // re-fetch sends again (without that, a walk one event short would be answered with
            // nothing but a duplicate, step past the boundary, and lose the rest of that second).
            //
            // A lone bounded filter asks for at most [limitCap] once one is known (passed in, or
            // learned from a relay that refused a larger limit). That is safe only for ONE
            // filter: the cursor is shared, so with several, a filter cut short whose page ends
            // newer than another's would have the gap between the two skipped when the cursor
            // moves to the oldest event of the page. So a multi-filter page keeps asking for each
            // filter's full remainder. An unbounded filter is sent without a `limit`: the relay's
            // own default page.
            val pageable =
                filters.indices.filter { index ->
                    val limit = filters[index].limit
                    val stillNeedsMore = limit == null || matchCountPerFilter[index] < limit
                    val pageableThisPage = until == null || filters[index].search == null
                    stillNeedsMore && pageableThisPage
                }
            val capToPageSize = pageable.size == 1
            // Pages shrink honestly when a filter drops out (its limit met, a search after page 1):
            // that is not throttling, so the short-page evidence starts over with the new set.
            if (pageable != lastPageable) {
                largestPage = 0
                shortPagesInARow = 0
                lastPageable = pageable
                lastProductive = null
            }
            val activeFilters =
                pageable.map { index ->
                    val filter = filters[index]
                    val pageLimit =
                        filter.limit?.let {
                            val remainder = it - matchCountPerFilter[index]
                            val seen = seenAtBoundary.size
                            val cap = limitCap
                            when {
                                !capToPageSize || cap == null -> remainder + seen
                                // A normal page: at most the cap, so a relay that refuses anything
                                // above its max (purplepag.es) is never asked for more again.
                                seen < cap || topUpRefused -> minOf(remainder + seen, cap)
                                // The boundary second alone fills a page: capping here would bring
                                // back only duplicates, step past the second and drop its tail. Ask
                                // past the cap; a relay that can serve it will, and one that refuses
                                // sets [topUpRefused] and loses that tail, as it had to.
                                else -> minOf(remainder, cap) + seen
                            }
                        }
                    IndexedValue(index, filter.copy(until = until ?: filter.until, limit = pageLimit))
                }
            // To tell, after the page, which filters it delivered for.
            val matchesBeforePage = matchCountPerFilter.copyOf()

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

            // A relay trickling short pages gets a pause before the next REQ (see below).
            if (paceMs > 0) {
                Log.d("fetchAllPages") { "${relay.url} pages keep coming back short; pausing ${paceMs}ms before the next REQ" }
                throttleBackoff.sleep(paceMs)
                paceMs = 0
            }

            // Announce the page only now that we know it will actually be fetched: a
            // search-only filter drops out of activeFilters above and breaks with no
            // REQ, so firing this earlier would report a page that never happens.
            // Once per cursor: a refused, throttled or paced re-ask is the same page again.
            if (until != null && until != announcedUntil) {
                announcedUntil = until
                onNewPage?.invoke(until)
            }

            val doneChannel = Channel<PageSignal>(Channel.CONFLATED)
            val pageSubId = newSubId()
            val gate = PageGate()
            var pageStart = TimeSource.Monotonic.markNow()

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

            // The ids of the second the page is currently in, to drop an event the relay repeats
            // on the same page (see the listener). O(one second), like the boundary dedup.
            var runSecond = Long.MIN_VALUE
            val runIds = HashSet<HexKey>()
            // The open-ended first page also carries events stored while the query ran, out of
            // `created_at` order (eden.nostr.land, measured: one of them twice, seconds apart), so
            // the per-second ids above can miss a repeat there. The first page alone keeps every id:
            // O(that page), and only once per walk.
            val firstPageIds: HashSet<HexKey>? = if (until == null) HashSet() else null
            // Repeats dropped by the two checks above. A page's size, as the throttle evidence
            // reads it, counts each event once: relay.jmoose.rocks, measured, sent its first
            // page twice (1002 for a 500-event page), and against that "largest page" every
            // honest 500 after it looked short, so the walk paused seven times for nothing.
            var repeatsDropped = 0

            // Diagnostics only: how the relay ordered and repeated this page's events.
            val pageIdsSeen: HashSet<HexKey>? = if (walkIds != null) HashSet() else null
            var repeatedOnPage = 0
            var deliveredBefore = 0
            var outOfOrder = 0
            var lastCreatedAt = Long.MAX_VALUE
            var pageMinTs = Long.MAX_VALUE
            val idsAtPageMin = HashSet<HexKey>()

            // How this page ended, read after the wait: null for an idle timeout, which
            // [receiveWithinIdle] reports by returning null. Only an EOSE can support a
            // drain claim below — silence is not an answer, and a CLOSED is the relay
            // declining to give one.
            var pageEnd: PageSignal? = null

            // NIP-67 hints of the EOSE that ended this page (null: none sent). Written on the
            // relay's reader thread before the EOSE signal is sent; the channel orders it.
            var eoseHints: List<String>? = null

            // Ids delivered on this page, kept only while an EOSE `"auth"` hint could still make
            // the relay re-serve the page after AUTH (at most once per walk), so the re-served
            // copies of events already handed to [onEvent] are dropped. The hint is rare, so the
            // page only appends to a plain list (no hashing, no per-entry node); the lookup set
            // is built from it once, on the first re-served event. Both are reader-thread only.
            val pageIds: ArrayList<HexKey>? = if (pendingOnAuthRequired && !authRetried) ArrayList() else null
            var reServedIds: HashSet<HexKey>? = null
            var reServing = false

            try {
                val listener =
                    object : SubscriptionListener {
                        override suspend fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            // This page's subscription stays open until the next page's is (see
                            // openSubId); a live event reaching it meanwhile belongs to no page.
                            if (gate.over) return
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
                                if (pageIdsSeen != null) {
                                    if (!pageIdsSeen.add(event.id)) repeatedOnPage++
                                    if (event.createdAt > lastCreatedAt) outOfOrder++
                                    lastCreatedAt = event.createdAt
                                }
                                // Drop a boundary-second event we already delivered on an
                                // earlier page (the inclusive re-fetch returns it again).
                                if (boundary != null && event.createdAt == boundary && event.id in seenAtBoundary) return
                                // The relay re-serving this page after an EOSE "auth" hint: skip what
                                // this page already delivered.
                                if (reServing && pageIds != null) {
                                    val seenOnPage = reServedIds ?: HashSet(pageIds).also { reServedIds = it }
                                    if (event.id in seenOnPage) return
                                }
                                // Drop an event the relay sends twice on one page: purplepag.es,
                                // measured, repeats 59-84 events per 500-event page, and each repeat
                                // counted toward the limit (a walk for 3000 stopped at 2716 distinct).
                                // Pages come newest-first, so a repeat lands in the same second as the
                                // original and that second's ids are enough to catch it.
                                if (event.createdAt != runSecond) {
                                    runSecond = event.createdAt
                                    runIds.clear()
                                }
                                if (!runIds.add(event.id) || (firstPageIds != null && !firstPageIds.add(event.id))) {
                                    repeatsDropped++
                                    return
                                }

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
                                    // The caller's total, not this page's wire limit.
                                    if (matchCountPerFilter[index] < (filters[index].limit ?: Int.MAX_VALUE) && filter.match(event)) {
                                        matchCountPerFilter[index]++
                                        atLeastOne = true
                                        if (filter.search == null) advancesCursor = true
                                    }
                                }
                                if (atLeastOne) {
                                    if (walkIds != null && !walkIds.add(event.id)) deliveredBefore++
                                    onEvent(event)
                                    delivered++
                                    if (pageIds != null) {
                                        val seenOnPage = reServedIds
                                        if (seenOnPage != null) seenOnPage.add(event.id) else pageIds.add(event.id)
                                    }
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
                            if (gate.over) return
                            doneChannel.trySend(PageSignal.EOSE)
                        }

                        override fun onEose(
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                            hints: List<String>?,
                        ) {
                            if (gate.over) return
                            eoseHints = hints
                            doneChannel.trySend(PageSignal.EOSE)
                        }

                        override fun onClosed(
                            message: String,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            // A refusal of a finished page must not overwrite the current one's.
                            if (gate.over) return
                            refusal = message
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
                            if (gate.over) return
                            refusal = message
                            doneChannel.trySend(PageSignal.CANNOT_CONNECT)
                        }
                    }

                reqCount++
                Log.d("fetchAllPages") { "${relay.url} REQ #$reqCount until=${until ?: "-"} limit=${activeFilters.joinToString(",") { it.value.limit?.toString() ?: "none" }}" }
                pageStart = TimeSource.Monotonic.markNow()
                subscribe(pageSubId, mapOf(relay to activeFilters.map { it.value }), listener)
                // Only now that this page's subscription keeps the relay wanted.
                openSubId?.let { unsubscribe(it) }
                openSubId = pageSubId

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
                } else if (pageEnd == PageSignal.EOSE && eoseHints.hasHint(EoseMessage.HINT_AUTH) && pendingOnAuthRequired && !authRetried) {
                    // NIP-67 "auth": the page was answered, but the relay says it held some back.
                    // Same wait as the CLOSED case — the relay sent its challenge before this EOSE,
                    // and the AUTH's OK re-sends this very REQ — except the page already delivered
                    // events, so the re-served copies are dropped via [pageIds].
                    authRetried = true
                    reServing = true
                    if (awaitAuthOutcome(relay, authMark, DEFAULT_AUTH_GRACE_MS, idleTimeoutMs) == AuthOutcome.AUTHENTICATED) {
                        eoseHints = null
                        clock.bump()
                        pageEnd = doneChannel.receiveWithinIdle(clock, idleTimeoutMs)
                    }
                }
            } finally {
                // The subscription itself stays open until the next page's is (or the walk ends).
                gate.over = true
                doneChannel.close()
            }

            totalEvents += delivered

            // The page ended on an EOSE saying more is visible only after AUTH, and no AUTH
            // took the wall down: whatever it did deliver stands, but it cannot prove absence.
            val authBlocked = pageEnd == PageSignal.EOSE && eoseHints.hasHint(EoseMessage.HINT_AUTH)

            Log.d("fetchAllPages") {
                val reason = if (pageEnd == PageSignal.CLOSED || pageEnd == PageSignal.AUTH_REQUIRED || pageEnd == PageSignal.CANNOT_CONNECT) " ($refusal)" else ""
                val shape = if (pageIdsSeen != null) " outOfOrder=$outOfOrder aboveUntil=$aboveBoundary repeatedOnPage=$repeatedOnPage deliveredBefore=$deliveredBefore" else ""
                "${relay.url} REQ #$reqCount -> received=$received delivered=$delivered end=${pageEnd ?: "IDLE"}$reason$shape hints=$eoseHints in ${pageStart.elapsedNow().inWholeMilliseconds}ms"
            }

            // An EOSEd empty page that the relay's own earlier answers say cannot be empty:
            // throttling, not the end of the corpus. relay.damus.io, paged fast on one
            // connection, shrinks its pages to a handful of events and then answers EMPTY for
            // a range that holds hundreds — measured: ~700 of ~250k events, then a false
            // DRAINED. A fresh subscription id does not help; time does. So wait and re-ask
            // the SAME cursor, a bounded number of times, before believing it.
            //
            // It takes a short page — one smaller than a page the relay already served — and
            // then one of two readings an honestly paging relay never produces, so its walk
            // never waits:
            //  - the empty page re-asked a boundary second INCLUSIVELY, with the same filters,
            //    right after the relay served matching events in that very second. An honest
            //    relay cannot answer that with nothing — at the least it re-sends the boundary.
            //  - the delivering pages before it were short twice in a row. A normal walk has
            //    exactly one short page — the tail of the corpus — before it ends.
            //
            // The same relay also ends that shrinking run with a CLOSED instead (measured:
            // pages of 500, 500, 131, 3, then `ERROR: read bandwidth budget exhausted (1048570
            // bytes/min per IP)`), so a CLOSED right after a short page is re-asked the same way.
            // A CLOSED with no shrinking before it — a policy refusal, a first-page rejection —
            // never waits, and one that outlasts the backoff still ends the walk as CLOSED.
            // The relay refused this page's `limit` (purplepag.es: `blocked: limit too high: 50000
            // (max 500)`). Re-ask the same page lower and keep that cap for the rest of the walk.
            // Only for a lone filter (see the cap above); each refusal lowers the ask, so this ends.
            if (received == 0 && pageEnd == PageSignal.CLOSED && activeFilters.size == 1) {
                val sent = activeFilters[0].value.limit
                val cap = limitCap
                if (sent != null && cap != null && sent > cap && lowerLimitAfterRefusal(refusal, sent) != null) {
                    // A boundary-second top-up past a known cap, refused for its limit: the relay
                    // holds to its cap. Any other CLOSED here (a throttle, a hiccup) is not that,
                    // and must not give up every later dense second's tail; it goes to the
                    // throttle check below like any other CLOSED.
                    topUpRefused = true
                    Log.d("fetchAllPages") { "${relay.url} refused a top-up of $sent past its cap $cap ($refusal); asking at the cap" }
                    continue
                }
                val lower = if (sent != null) lowerLimitAfterRefusal(refusal, sent) else null
                if (lower != null) {
                    limitCap = lower
                    Log.d("fetchAllPages") { "${relay.url} refused limit=$sent ($refusal); re-asking at $lower" }
                    continue
                }
            }

            if (received == 0 && reAsks < throttleBackoff.delaysMs.size) {
                val throttled =
                    when (pageEnd) {
                        PageSignal.EOSE -> {
                            // Not at the walk's `since` floor, though: the last page of a walk
                            // bounded by `since` asks `until == since`, which some relays answer
                            // with nothing (wheat.happytavern.co, grain, measured: three re-asks,
                            // 17 s, on every such walk). That page is the end, not a contradiction.
                            val contradictsBoundary =
                                boundary != null &&
                                    seenAtBoundary.isNotEmpty() &&
                                    boundaryFilters == activeFilters.map { it.index } &&
                                    activeFilters.none { it.value.since.let { since -> since != null && boundary <= since } }
                            !authBlocked &&
                                !eoseHints.hasHint(EoseMessage.HINT_FINISH) &&
                                (shortPagesInARow >= 2 || (shortPagesInARow >= 1 && contradictsBoundary))
                        }
                        // After shrinking pages, or when the relay says outright that it is
                        // throttling (damus.bostr.online, measured: a CLOSED `read bandwidth budget
                        // exhausted ... (retry in 30ms)` that the walk took as its end).
                        PageSignal.CLOSED -> shortPagesInARow >= 1 || refusal?.let(::isThrottleMessage) == true
                        // A page with no answer at all (no event, EOSE or CLOSED) is not re-asked:
                        // each ask costs a whole idle window, and a dead relay is silent too. The
                        // silent pages measured mid-walk (REQ #2 on yabu.me, yestr.me, no.str.cr,
                        // ...) were the walk's own doing, a connection the pool dropped between
                        // pages, and went away with that (0 in 300+ walks since).
                        else -> false
                    }
                if (throttled) {
                    val waitMs = throttleBackoff.delaysMs[reAsks++]
                    Log.d("fetchAllPages") { "${relay.url} ${if (pageEnd == PageSignal.CLOSED) "CLOSED ($refusal)" else "empty page"} at until=$until looks throttled; re-asking in ${waitMs}ms (#$reAsks)" }
                    throttleBackoff.sleep(waitMs)
                    continue
                }
            }

            // What the relay served on this page, each event once.
            val served = received - repeatsDropped
            if (pageEnd == PageSignal.EOSE && served > 0) {
                // A filter that delivered before and nothing now has run out of older events (EventSync's
                // `#p=me` beside a longer `authors=me`): the pages after it are honestly smaller, so the
                // short-page evidence starts over from this page, as when a filter drops out. Only the
                // filters still delivering count; a trickling relay keeps the same ones and is caught.
                if (delivered > 0) {
                    val productive = activeFilters.map { it.index }.filter { matchCountPerFilter[it] > matchesBeforePage[it] }
                    val previous = lastProductive
                    if (previous != null && !productive.containsAll(previous)) {
                        largestPage = 0
                        shortPagesInARow = 0
                    }
                    lastProductive = productive
                }
                if (served > largestPage) largestPage = served
                // "Short" is at most half the largest page, or of what this page asked for when
                // that was less (the walk's last, topped-up page asks for just what is missing): a
                // relay whose pages wobble by a few events (post-limit filtering of deleted or
                // expired events) never counts.
                if (delivered > 0) {
                    val asked = activeFilters.sumOf { it.value.limit ?: largestPage }
                    shortPagesInARow = if (served * 2 <= minOf(largestPage, asked)) shortPagesInARow + 1 else 0
                }
            }
            if (delivered > 0) reAsks = 0
            // Two short pages in a row that still delivered: the relay is trickling, not ending
            // (relay.damus.io, measured: two full pages, then 1-4 events a page every ~120 ms for
            // as long as it was asked, and a full 500 again after a 2 s pause). An honest walk has
            // one short page, its tail, so it never pauses. Pause before the next REQ, longer each
            // paced page in a row, holding at the last step; a full page ends the pacing.
            if (shortPagesInARow >= 2 && delivered > 0 && throttleBackoff.delaysMs.isNotEmpty()) {
                paceMs = throttleBackoff.delaysMs[minOf(pacedPages, throttleBackoff.delaysMs.size - 1)]
                pacedPages++
            } else if (shortPagesInARow == 0) {
                pacedPages = 0
            }

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
                        authBlocked -> PagedFetchResult.End.AUTH_REQUIRED
                        else -> PagedFetchResult.End.DRAINED
                    }
                break
            }

            // NIP-67 "finish": the relay says it sent every stored match for this page's
            // filters, so there is nothing below the cursor to ask for — stop now rather than
            // spend a REQ to watch an empty page come back. It only speaks for the filters this
            // page actually carried; one that already dropped out (limit met, or a search after
            // its single page) keeps the reading it would have had.
            if (pageEnd == PageSignal.EOSE && eoseHints.hasHint(EoseMessage.HINT_FINISH)) {
                end =
                    when {
                        authBlocked -> PagedFetchResult.End.AUTH_REQUIRED
                        filters.indices.any { i -> filters[i].limit.let { it != null && matchCountPerFilter[i] >= it } } -> PagedFetchResult.End.LIMIT_REACHED
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

                // Unless the relay is trickling (two short pages in a row): then a page of only
                // the boundary's repeats is the throttle cutting the page short, not the second
                // running out (relay.damus.io, measured: pages of 1-4 events, some of them all
                // repeats). Stepping past would drop the rest of that second, so the same cursor is
                // re-asked after the backoff instead; an honest walk has one short page, its tail,
                // and never gets here. Not at the `since` floor, where stepping past ends the walk.
                if (shortPagesInARow >= 2 &&
                    pageEnd == PageSignal.EOSE &&
                    reAsks < throttleBackoff.delaysMs.size &&
                    activeFilters.none { it.value.since.let { since -> since != null && step <= since } }
                ) {
                    val waitMs = throttleBackoff.delaysMs[reAsks++]
                    Log.d("fetchAllPages") { "${relay.url} only repeats at until=$until while trickling; re-asking in ${waitMs}ms (#$reAsks)" }
                    throttleBackoff.sleep(waitMs)
                    continue
                }

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
            boundaryFilters = activeFilters.map { it.index }
            until = nextUntil
        }
    } finally {
        openSubId?.let { unsubscribe(it) }
    }

    val refused = end == PagedFetchResult.End.CLOSED || end == PagedFetchResult.End.AUTH_REQUIRED || end == PagedFetchResult.End.CANNOT_CONNECT
    Log.d("fetchAllPages") { "${relay.url} walk ended $end: $totalEvents events in $reqCount REQs${limitCap?.let { ", cap $it" } ?: ""}" }
    return PagedFetchResult(totalEvents, end, refusal.takeIf { refused })
}

suspend fun INostrClient.fetchAllPages(
    relay: String,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    onNewPage: ((Long) -> Unit)? = null,
    pageSize: Int? = null,
    throttleBackoff: PageRetryBackoff = PageRetryBackoff.DEFAULT,
    onEvent: suspend (Event) -> Unit,
): PagedFetchResult =
    fetchAllPages(
        relay = RelayUrlNormalizer.normalize(relay),
        filters = filters,
        idleTimeoutMs = idleTimeoutMs,
        onNewPage = onNewPage,
        pageSize = pageSize,
        throttleBackoff = throttleBackoff,
        onEvent = onEvent,
    )

private fun List<String>?.hasHint(hint: String) = this != null && contains(hint)
