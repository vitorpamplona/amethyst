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
 * persist / filter — return `true` to keep it in the result), and return the
 * accepted `(relay, event)` pairs once every relay reached a terminal state
 * (EOSE, CLOSED, or cannot-connect) or the line went quiet for [idleTimeoutMs].
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
 * events are discarded with the stack, [deadOut] / [doneOut] are never filled,
 * [onTimeout] does not fire, and a suspending [onEvent] can be cancelled
 * mid-write — the rule that keeps the hook out of the *internal* idle window's
 * timeout scope cannot protect it from the caller's. A caller that needs the
 * partial results, or needs the hook to finish what it started, should
 * accumulate into its own list from inside [onEvent] (it runs single-threaded)
 * rather than reading the return value.
 *
 * Extras over [fetchAll]:
 *  - **[onEvent] hook** — suspending per-event callback, invoked single-threaded
 *    in arrival order, so callers can serialize verify+store work. Only events it
 *    accepts (`true`) are collected. No cross-relay dedup is applied here — the
 *    hook sees every copy.
 *  - **[deadOut]** — when provided, every relay whose terminal reason classifies
 *    as a hard failure via [classifyDrainFailure] (connect refused / DNS / TLS /
 *    dead HTTP upgrade — NOT slow relays or 429s) is recorded, so callers can
 *    prune proven-dead relays from future routing instead of paying the full
 *    [idleTimeoutMs] on them again. An unsatisfied NIP-42 wall lands here too, as
 *    [DrainFailure.AUTH_REQUIRED] — recorded because the caller deserves to know
 *    why it got nothing, but flagged [DrainFailure.dropFromRouting] `false`, since
 *    the relay is alive and serves an identity it accepts. Test that property
 *    rather than the enum value before dropping anything.
 *  - **[pendingOnAuthRequired]** — a relay that refuses the REQ with an
 *    `auth-required:` CLOSED is kept pending rather than treated as terminal:
 *    the caller's NIP-42 responder answers the challenge and the client re-fires
 *    this same subscription, so the post-auth events are collected instead of
 *    returning empty. The wait is bounded by the AUTH's own outcome, not by the
 *    idle window — see [awaitAuthOutcome].
 *  - **[onTimeout]** — diagnostic hook fired when the idle window elapsed with
 *    relays still pending: receives the stalled set, the terminal reasons seen so
 *    far (`"eose"` / `"closed:<msg>"` / `"cannot:<msg>"` / `"auth-refused:<msg>"`),
 *    and what was collected.
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
    deadOut: MutableMap<NormalizedRelayUrl, DrainFailure>? = null,
    /**
     * Receives the terminal reason per relay ("eose", "closed:…", "cannot:…",
     * "auth-refused:…"), so a caller can tell "a relay served us and had nothing" from
     * "nobody served us". An empty result alone cannot: both look like zero events, and
     * treating the second as the first is how a read-merge-write on a replaceable event
     * destroys the entries it failed to read. See [anyRelayServed] and, for the NIP-42
     * wall specifically, [authRefusedRelays].
     *
     * A relay with NO entry here is still exactly one thing — nobody told us — and never
     * "auth-gated": that case now has a reason of its own.
     */
    doneOut: MutableMap<NormalizedRelayUrl, String>? = null,
    onTimeout: ((stalled: Set<NormalizedRelayUrl>, doneReasons: Map<NormalizedRelayUrl, String>, collected: List<Pair<NormalizedRelayUrl, Event>>) -> Unit)? = null,
    /** Stage-one grace handed to [awaitAuthOutcome] — how long a responder has to pick a challenge up. */
    authGraceMs: Long = DEFAULT_AUTH_GRACE_MS,
    onEvent: suspend (relay: NormalizedRelayUrl, event: Event) -> Boolean,
): List<Pair<NormalizedRelayUrl, Event>> {
    if (filters.isEmpty()) return emptyList()
    val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(UNLIMITED)
    // Carries the terminal reason per relay so a timeout can distinguish a slow
    // relay (never terminal) from a connect failure / CLOSED.
    val doneChannel = Channel<Pair<NormalizedRelayUrl, String>>(UNLIMITED)
    // Relays whose REQ came back `auth-required:`, handed to the resolver below. The
    // listener cannot wait on the AUTH itself — it runs on the relay's reader thread and
    // must not block it — so it only reports, and the resolver does the waiting.
    val authRefusalChannel = Channel<Pair<NormalizedRelayUrl, String>>(UNLIMITED)
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
                    // whether we chose to answer it, and a caller reading `doneOut` wants to know it
                    // gave up on an auth wall rather than on a policy refusal it can do nothing
                    // about. This is what [fetchAllPages] already does with End.AUTH_REQUIRED, and
                    // leaving it as a plain `closed:` here is what would make
                    // [authRefusedRelays] silently miss every no-responder client.
                    doneChannel.trySend(relay to "$DONE_REASON_AUTH_REFUSED:$message")
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
            var stalled = false
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
                    if (progressed == null) {
                        stalled = true
                        break
                    }
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
            if (stalled && remaining.isNotEmpty()) {
                onTimeout?.invoke(remaining, doneReasons, collected)
            }
            // Outlives the loop by design (it parks on an AUTH that may never settle), so
            // the scope only completes if we end it.
            authResolver?.cancel()
        }
    } finally {
        unsubscribe(subscriptionId)
        eventChannel.close()
        doneChannel.close()
        authRefusalChannel.close()
    }
    deadOut?.let { out ->
        for ((relay, reason) in doneReasons) {
            classifyDrainFailure(reason)?.let { out[relay] = it }
        }
    }
    doneOut?.putAll(doneReasons)
    return collected
}

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
 * Read against the map filled by `fetchAllWithHooks`'s `doneOut`. An empty event list means
 * "nothing matched" only when this is true; otherwise it means "nobody told us", and a caller
 * that overwrites a replaceable event on that basis deletes whatever it could not read.
 */
fun Map<NormalizedRelayUrl, String>.anyRelayServed(): Boolean = values.any { it == DONE_REASON_EOSE }

/**
 * The relays that turned us away at a NIP-42 wall — see [DONE_REASON_AUTH_REFUSED].
 *
 * Read against `doneOut`. These are emphatically *not* dead relays: they answered, and they
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
    onEvent: suspend (relay: NormalizedRelayUrl, event: Event) -> Boolean,
): List<Pair<NormalizedRelayUrl, Event>> {
    if (filters.isEmpty()) return emptyList()
    val collected = mutableListOf<Pair<NormalizedRelayUrl, Event>>()
    // fetchAllPagesFromPool's onEvent can't suspend, but the hook does — bridge
    // through a channel and run the hook single-threaded in one consumer so its
    // side effects (e.g. store writes) stay serialized.
    val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(UNLIMITED)
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
                        collected.add(relay to event)
                    }
                }
            }
        try {
            fetchAllPagesFromPool(
                filters = filters,
                idleTimeoutMs = idleTimeoutMs,
                maxConcurrentRelays = maxConcurrentRelays,
            ) { event, relay -> eventChannel.trySend(relay to event) }
        } finally {
            eventChannel.close()
        }
        consumer.join()
    }
    return collected
}
