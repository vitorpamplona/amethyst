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
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.AuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.DEFAULT_AUTH_GRACE_MS
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.authSuccessMarks
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.awaitAuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.hasAuthResponder
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.SeenIds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Option-rich sibling of [fetchAll]: subscribe [filters] across their relays,
 * funnel every arriving event through the suspending [onEvent] hook (verify /
 * persist / filter — return `true` to keep it in the result), and return a
 * [FetchAllResult] once every relay reached a terminal state (EOSE, CLOSED, or
 * cannot-connect) or the line went quiet for [idleTimeoutMs].
 *
 * The result carries what each relay *did* alongside the events
 * ([FetchAllResult.doneReasons], [FetchAllResult.stalled], and the derived
 * [FetchAllResult.dead] / [FetchAllResult.anyRelayServed]), because an empty event
 * list is ambiguous on its own — "a relay answered and had nothing" and "nobody
 * answered" are the same zero events, and a read-merge-write that confuses them
 * deletes what it could not read.
 *
 * [idleTimeoutMs] is an **idle window, not a hard cap**: the clock only runs while
 * the relays are silent, and every arriving event or terminal signal resets
 * it. A slow relay actively streaming a large backlog is therefore never
 * cropped mid-delivery — the fetch ends when the work is done or when nothing
 * has arrived for [idleTimeoutMs] (a stall). The terminal conditions (EOSE /
 * CLOSED / cannot-connect per relay) are what bound the fetch; the timeout's
 * only job is detecting relays that will never reach one.
 *
 * There is no wall-clock ceiling parameter, in line with every other accessory:
 * a hard deadline composes at the call site — `withTimeoutOrNull(ms) { fetchAllWithHooks(…) }`
 * — and an internal one cannot tell a relay legitimately streaming a large
 * backlog from a misbehaving one, so it cuts both.
 *
 * **Cancellation is therefore the bound, and it is abrupt.** The subscription is
 * still closed and the channels released (that cleanup does not suspend, so it
 * survives cancellation), but a cancelled fetch returns nothing: the collected
 * events and the whole [FetchAllResult] are discarded with the stack, and a
 * suspending [onEvent] can be cancelled mid-write — the rule that keeps the hook
 * out of the *internal* idle window's timeout scope cannot protect it from the
 * caller's. A caller that needs the partial results, or needs the hook to finish
 * what it started, should accumulate into its own list from inside [onEvent] (it
 * runs single-threaded) rather than reading the return value.
 *
 * Extras over [fetchAll]:
 *  - **[onEvent] hook** — suspending per-event callback, invoked single-threaded
 *    in arrival order, so callers can serialize verify+store work. Only events it
 *    accepts (`true`) are collected. No cross-relay dedup is applied here — the
 *    hook sees every copy.
 *  - **[pendingOnAuthRequired]** — a relay that refuses the REQ with an
 *    `auth-required:` CLOSED is kept pending rather than treated as terminal:
 *    the caller's NIP-42 responder answers the challenge and the client re-fires
 *    this same subscription, so the post-auth events are collected instead of
 *    returning empty. The wait is bounded by the AUTH's own outcome, not by the
 *    idle window — see [awaitAuthOutcome].
 */
suspend fun INostrClient.fetchAllWithHooks(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 8_000L,
    subscriptionId: String = newSubId(),
    /**
     * Whether an `auth-required:` CLOSED keeps the relay pending instead of ending it.
     *
     * Defaults to **whether this client has a NIP-42 responder attached** ([hasAuthResponder]),
     * because that is the fact the answer turns on: a challenge is worth waiting for when
     * something is going to answer it and is dead time when nothing is. The sibling accessories
     * take no such parameter at all — they simply do this — and neither should most callers.
     * It survives here only because this is the option-rich form and the flag predates the
     * derived default.
     *
     * There is almost nothing left to decide. With no responder, `true` and `false` produce the
     * same outcome, since [awaitAuthOutcome] returns [AuthOutcome.NO_RESPONDER] without waiting.
     * With one attached, waiting is what makes the relay readable at all, and it is bounded:
     * a challenge nobody picks up ends in [authGraceMs], one the relay rejects ends on its
     * `OK false`, and a signer prompt nobody answers is capped at [idleTimeoutMs] — so **an
     * auth-gated relay costs at most what a silent relay already cost**. Pass `false` only to
     * force the pre-existing give-up-immediately behaviour.
     *
     * Either way the refusal is REPORTED as [DONE_REASON_AUTH_REFUSED]; this only decides
     * whether we wait for the challenge before recording it.
     */
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
    /** Stage-one grace handed to [awaitAuthOutcome] — how long a responder has to pick a challenge up. */
    authGraceMs: Long = DEFAULT_AUTH_GRACE_MS,
    onEvent: suspend (relay: NormalizedRelayUrl, event: Event) -> Boolean,
): FetchAllResult {
    if (filters.isEmpty()) return FetchAllResult(emptyList(), emptyMap(), emptySet())
    val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(UNLIMITED)
    // Carries the terminal reason per relay so a timeout can distinguish a slow
    // relay (never terminal) from a connect failure / CLOSED.
    val doneChannel = Channel<Pair<NormalizedRelayUrl, String>>(UNLIMITED)
    // Relays whose REQ came back `auth-required:`, handed to the resolver below. The
    // listener cannot wait on the AUTH itself — it runs on the relay's reader thread and
    // must not block it — so it only reports, and the resolver does the waiting.
    val authRefusalChannel = Channel<Pair<NormalizedRelayUrl, String>>(UNLIMITED)
    // Relays that refused a filter's `limit` (`blocked: limit too high: 1000 (max 500)`), handed
    // to the resolver below to be re-asked at the limit they state.
    val limitRefusalChannel = Channel<Pair<NormalizedRelayUrl, String>>(UNLIMITED)
    // Subscriptions opened to re-ask a relay at a lower limit; closed with the main one.
    val retrySubIds = mutableListOf<String>()
    val remaining = filters.keys.toMutableSet()
    val doneReasons = HashMap<NormalizedRelayUrl, String>()
    val listener =
        object : SubscriptionListener {
            override suspend fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                eventChannel.trySend(relay to event)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                doneChannel.trySend(relay to "eose")
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (MachineReadablePrefix.parse(message) == MachineReadablePrefix.AUTH_REQUIRED) {
                    // Keep the relay pending: the authenticator answers the challenge and re-fires
                    // this subscription, so the post-auth events still arrive. The resolver ends it
                    // as auth-refused if the challenge does not work out — bounded by the AUTH, not
                    // by the timeout.
                    if (pendingOnAuthRequired) {
                        authRefusalChannel.trySend(relay to message)
                        return
                    }
                    // Not waiting — but still NAME the wall. What the relay said does not depend on
                    // whether we chose to answer it, and a caller reading the reasons wants to know it
                    // gave up on an auth wall rather than on a policy refusal it can do nothing
                    // about. This is what [fetchAllPages] already does with End.AUTH_REQUIRED, and
                    // leaving it as a plain `closed:` here is what would make
                    // [authRefusedRelays] silently miss every no-responder client.
                    doneChannel.trySend(relay to "$DONE_REASON_AUTH_REFUSED:$message")
                    return
                }
                // A refusal of a filter's `limit`, not of the query: re-ask that relay lower
                // rather than come back empty for what a relay clamping its pages would serve.
                val sent = filters[relay]
                if (sent != null && lowerLimitsAfterRefusal(message, sent.map { it.limit }) != null) {
                    limitRefusalChannel.trySend(relay to message)
                    return
                }
                doneChannel.trySend(relay to "closed:$message")
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                doneChannel.trySend(relay to "cannot:$message")
            }
        }
    // AUTH successes already on the books per relay, read BEFORE the REQ goes out. The
    // resolver compares against these: an AUTH that lands after this point is one that
    // re-sent our subscription, whereas a connection that was already authenticated and
    // still refused us is being gated for a reason no further waiting fixes.
    val authMarks = if (pendingOnAuthRequired) authSuccessMarks(filters.keys) else emptyMap()
    val collected = mutableListOf<Pair<NormalizedRelayUrl, Event>>()
    try {
        coroutineScope {
            subscribe(subscriptionId, filters, listener)
            // Turns each `auth-required:` refusal into a terminal reason as soon as the
            // AUTH resolves against us, so an auth wall costs a grace window instead of a
            // full idle window — and, unlike the timeout, says what it hit.
            //
            // A relay gets ONE resolver per fetch. A second refusal after a successful AUTH
            // (the relay wanting an identity we do not hold) falls through to [idleTimeoutMs],
            // which is the pre-existing behaviour; the alternative is letting a relay that
            // spams CLOSED spawn a coroutine per frame.
            val authResolver =
                if (pendingOnAuthRequired) {
                    launch {
                        val resolving = mutableSetOf<NormalizedRelayUrl>()
                        for ((relay, message) in authRefusalChannel) {
                            if (!resolving.add(relay)) continue
                            launch {
                                if (awaitAuthOutcome(relay, authMarks[relay] ?: 0, authGraceMs, idleTimeoutMs) != AuthOutcome.AUTHENTICATED) {
                                    doneChannel.trySend(relay to "$DONE_REASON_AUTH_REFUSED:$message")
                                }
                            }
                        }
                    }
                } else {
                    null
                }
            // Re-asks a relay that refused a filter's `limit`, on its own subscription, with the
            // filters above the limit its message states brought down to it (else the largest
            // halved) — see [lowerLimitsAfterRefusal]. Each refusal comes back
            // at once, and each re-ask lowers the limit, so it ends; a refusal that lowers nothing
            // more ends the relay as `closed:`, as any other refusal does.
            val limitResolver =
                launch {
                    val current = HashMap<NormalizedRelayUrl, List<Filter>>()
                    val attempts = HashMap<NormalizedRelayUrl, Int>()
                    val retryOf = HashMap<NormalizedRelayUrl, String>()
                    // Relays this resolver already ended: a refusal re-sent after that (an AUTH or
                    // a reconnect replays the refused subscription) must not open another re-ask.
                    val finished = HashSet<NormalizedRelayUrl>()
                    for ((relay, message) in limitRefusalChannel) {
                        if (relay in finished) continue
                        val base = current[relay] ?: filters[relay] ?: continue
                        val limits = lowerLimitsAfterRefusal(message, base.map { it.limit })
                        val attempt = (attempts[relay] ?: 0) + 1
                        attempts[relay] = attempt
                        if (limits == null || attempt > MAX_LIMIT_RETRIES) {
                            finished.add(relay)
                            doneChannel.trySend(relay to "closed:$message")
                            continue
                        }
                        val lowered = base.mapIndexed { i, f -> if (limits[i] == f.limit) f else f.copy(limit = limits[i]) }
                        current[relay] = lowered
                        // One re-ask open per relay: the one it refused is closed first, so a replay
                        // of it cannot serve the same events a second time.
                        retryOf[relay]?.let { unsubscribe(it) }
                        val retryId = newSubId()
                        retryOf[relay] = retryId
                        retrySubIds.add(retryId)
                        subscribe(retryId, mapOf(relay to lowered), listener)
                    }
                }
            // Idle-window wait. Two structural rules:
            //
            //  1. The suspending [onEvent] hook NEVER runs inside a timeout
            //     scope. Cancellation only lands at suspension points, so a
            //     hook stalled in verify/persist work would otherwise be
            //     cancelled mid-write by an expiring window and the
            //     already-received event silently lost. The select bodies
            //     below only stash/bookkeep (non-suspending — they cannot be
            //     cancelled mid-body); the hook runs after.
            //
            //  2. The timeout is only armed when both channels are DRY.
            //     Buffered messages drain through the tryReceive fast path
            //     with zero timeout-job churn — under burst arrival a
            //     per-message withTimeoutOrNull would pay one
            //     scheduled+cancelled cancellation task per event for nothing.
            while (remaining.isNotEmpty()) {
                // The caller's deadline is the only wall-clock bound on this fetch, so
                // the loop has to be able to observe it. Nothing on the fast path below
                // suspends — tryReceive never does, and a suspend hook that completes
                // without suspending performs no cancellation check — so a relay feeding
                // faster than we drain would spin here forever, deaf to an enclosing
                // withTimeout. This is the check that makes `cancel the caller` work, the
                // same role [fetchAllPages] gives its per-page ensureActive.
                ensureActive()
                var pending: Pair<NormalizedRelayUrl, Event>? = null

                // Fast path: consume whatever is already buffered.
                val bufferedDone = doneChannel.tryReceive().getOrNull()
                if (bufferedDone != null) {
                    remaining.remove(bufferedDone.first)
                    doneReasons[bufferedDone.first] = bufferedDone.second
                    continue
                }
                pending = eventChannel.tryReceive().getOrNull()

                // Slow path: both dry — arm one idle wait for the next signal.
                if (pending == null) {
                    val progressed =
                        withTimeoutOrNull(idleTimeoutMs) {
                            select<Unit> {
                                eventChannel.onReceive { pending = it }
                                doneChannel.onReceive { (relay, reason) ->
                                    remaining.remove(relay)
                                    doneReasons[relay] = reason
                                }
                            }
                        }
                    if (progressed == null) break
                }

                pending?.let { pair ->
                    if (onEvent(pair.first, pair.second)) collected.add(pair)
                }
            }
            // Drain any events that landed after the last terminal signal (or
            // during the final window) but before unsubscribe.
            while (true) {
                val r = eventChannel.tryReceive()
                if (!r.isSuccess) break
                val pair = r.getOrThrow()
                if (onEvent(pair.first, pair.second)) collected.add(pair)
            }
            // Outlives the loop by design (it parks on an AUTH that may never settle), so
            // the scope only completes if we end it.
            authResolver?.cancel()
            limitResolver.cancel()
        }
    } finally {
        unsubscribe(subscriptionId)
        retrySubIds.forEach { unsubscribe(it) }
        eventChannel.close()
        doneChannel.close()
        authRefusalChannel.close()
        limitRefusalChannel.close()
    }
    // `remaining` is empty unless the idle window elapsed with relays still pending, so
    // it IS the stalled set — the loop only leaves entries behind when it gives up on them.
    return FetchAllResult(collected, doneReasons, remaining.toSet())
}

/** How many times [fetchAllWithHooks] re-asks one relay at a lower limit before taking the refusal. */
private const val MAX_LIMIT_RETRIES = 6

/** The terminal reason recorded when a relay finished serving a subscription normally. */
const val DONE_REASON_EOSE = "eose"

/**
 * Terminal-reason prefix for a relay that refused the REQ with `auth-required:` and whose
 * NIP-42 challenge then failed to satisfy — no responder attached, the responder declined,
 * the signer never answered, or the relay rejected the AUTH with `OK false`. The relay's
 * own message follows the colon.
 *
 * Its own reason, not a `closed:`, because it is the one refusal the caller can *act* on: a
 * rate limit wants a slower retry and a policy refusal wants no retry at all, while this one
 * wants a signer this relay accepts. Folding it into `closed:` is what left an auth wall
 * indistinguishable from a relay that simply does not want us.
 */
const val DONE_REASON_AUTH_REFUSED = "auth-refused"

/**
 * True when at least one relay completed the fetch normally, i.e. answered and reached EOSE.
 *
 * Read against [FetchAllResult.doneReasons] (or via [FetchAllResult.anyRelayServed], which is
 * exactly this). An empty event list means
 * "nothing matched" only when this is true; otherwise it means "nobody told us", and a caller
 * that overwrites a replaceable event on that basis deletes whatever it could not read.
 */
fun Map<NormalizedRelayUrl, String>.anyRelayServed(): Boolean = values.any { it == DONE_REASON_EOSE }

/**
 * The relays that turned us away at a NIP-42 wall — see [DONE_REASON_AUTH_REFUSED].
 *
 * Read against [FetchAllResult.doneReasons] (or via [FetchAllResult.authRefused], which is
 * exactly this). These are emphatically *not* dead relays: they answered, and they
 * will serve the same query on a connection carrying an identity they accept. A caller
 * recording coverage should mark them unmeasured-and-fixable rather than empty, and one
 * routing reads should keep them for a session that holds the right signer.
 */
fun Map<NormalizedRelayUrl, String>.authRefusedRelays(): Set<NormalizedRelayUrl> = filterValues { it.startsWith(DONE_REASON_AUTH_REFUSED) }.keys

/**
 * [fetchAllPagesFromPool] with a suspending per-event hook: paginates every relay
 * to completion (each on its own `until` cursor, up to [maxConcurrentRelays] at
 * once) and funnels every event through [onEvent] — invoked single-threaded in
 * one consumer coroutine, so suspending verify/persist work stays serialized.
 * Returns the accepted `(relay, event)` pairs, tagged by the relay that first
 * delivered each.
 *
 * Unlike [fetchAllWithHooks], results ARE deduped across relays: the same
 * widely-mirrored event arrives once per relay, and the repeats are dropped by a
 * [SeenIds] filter BEFORE the (potentially expensive) [onEvent] — an id is marked
 * seen only after the hook accepts it, so a forged copy (valid id, bad signature)
 * delivered first can't suppress the genuine one from another relay.
 */
suspend fun INostrClient.fetchAllPagesFromPoolWithHooks(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 30_000L,
    maxConcurrentRelays: Int = 8,
    onRelayResult: ((relay: NormalizedRelayUrl, result: PagedFetchResult) -> Unit)? = null,
    pageSize: Int? = null,
    throttleBackoff: PageRetryBackoff = PageRetryBackoff.DEFAULT,
    onEvent: suspend (relay: NormalizedRelayUrl, event: Event) -> Boolean,
): List<Pair<NormalizedRelayUrl, Event>> {
    val collected = mutableListOf<Pair<NormalizedRelayUrl, Event>>()
    streamAllPagesFromPoolWithHooks(filters, idleTimeoutMs, maxConcurrentRelays, onRelayResult, pageSize, throttleBackoff) { relay, event ->
        onEvent(relay, event).also { accepted -> if (accepted) collected.add(relay to event) }
    }
    return collected
}

/**
 * [fetchAllPagesFromPoolWithHooks] without the result list: the same paging, the same
 * single-consumer hook and the same cross-relay dedup, but nothing is kept beyond the
 * dedup set's ids — an accepted event is the hook's to keep, write out or drop. Use it
 * for a walk too large to hold (a relay's whole history for a broad filter): memory is
 * O(distinct ids), not O(events). Events reach [onEvent] in arrival order, NOT sorted:
 * up to [maxConcurrentRelays] relays page at once, so their events interleave.
 *
 * A slow [onEvent] holds the walk back: events reach it through a small bounded buffer, and a
 * relay whose events are waiting does not take its next page. Without that, a consumer slower
 * than the network (signature checks, store writes, a slow stdout pipe) let every page of every
 * relay pile up on the heap, which is the out-of-memory a streamed walk exists to avoid.
 *
 * @return how many distinct events [onEvent] accepted.
 */
suspend fun INostrClient.streamAllPagesFromPoolWithHooks(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 30_000L,
    maxConcurrentRelays: Int = 8,
    onRelayResult: ((relay: NormalizedRelayUrl, result: PagedFetchResult) -> Unit)? = null,
    pageSize: Int? = null,
    throttleBackoff: PageRetryBackoff = PageRetryBackoff.DEFAULT,
    onEvent: suspend (relay: NormalizedRelayUrl, event: Event) -> Boolean,
): Int {
    if (filters.isEmpty()) return 0
    var accepted = 0
    // Bridge through a channel and run the hook single-threaded in one consumer so its side
    // effects (e.g. store writes) stay serialized. Bounded, and sent to with a suspending send,
    // so a consumer that falls behind stalls the walks feeding it (see above).
    val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(STREAM_BUFFER)
    coroutineScope {
        val consumer =
            launch {
                // One writer → SeenIds' single-writer contract holds. Skip a
                // cross-relay duplicate before running the hook on it; mark it seen
                // only once the hook accepts it so a bad-sig copy can't pre-empt a
                // good one. Start small (one-shot fetches are typically hundreds of
                // events); it grows if an unbounded drain needs it, rather than
                // eagerly taking the large-walk default table.
                val seen = SeenIds(initialSlotsPow2 = 12)
                for ((relay, event) in eventChannel) {
                    if (seen.contains(event.id)) continue
                    if (onEvent(relay, event)) {
                        seen.add(event.id)
                        accepted++
                    }
                }
            }
        try {
            fetchAllPagesFromPool(
                filters = filters,
                idleTimeoutMs = idleTimeoutMs,
                maxConcurrentRelays = maxConcurrentRelays,
                onRelayResult = onRelayResult,
                pageSize = pageSize,
                throttleBackoff = throttleBackoff,
            ) { event, relay -> eventChannel.send(relay to event) }
        } finally {
            eventChannel.close()
        }
        consumer.join()
    }
    return accepted
}

/**
 * How many accepted-but-unconsumed events [streamAllPagesFromPoolWithHooks] holds before the
 * walks feeding it wait for its consumer: enough to smooth over a burst, a page's worth at most.
 */
private const val STREAM_BUFFER = 256
