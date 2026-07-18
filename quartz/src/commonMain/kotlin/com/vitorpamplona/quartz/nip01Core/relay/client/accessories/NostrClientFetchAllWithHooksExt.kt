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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.SeenIds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Option-rich sibling of [fetchAll]: subscribe [filters] across their relays,
 * funnel every arriving event through the suspending [onEvent] hook (verify /
 * persist / filter — return `true` to keep it in the result), and return the
 * accepted `(relay, event)` pairs once every relay reached a terminal state
 * (EOSE, CLOSED, or cannot-connect) or [timeoutMs] elapsed.
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
 *    [timeoutMs] on them again.
 *  - **[pendingOnAuthRequired]** — a relay that refuses the REQ with an
 *    `auth-required:` CLOSED is kept pending rather than treated as terminal:
 *    the caller's NIP-42 responder answers the challenge and the client re-fires
 *    this same subscription, so the post-auth events are collected instead of
 *    returning empty. If auth never satisfies it, the relay simply falls through
 *    to the [timeoutMs].
 *  - **[onTimeout]** — diagnostic hook fired when the deadline elapsed with
 *    relays still pending: receives the stalled set, the terminal reasons seen so
 *    far (`"eose"` / `"closed:<msg>"` / `"cannot:<msg>"`), and what was collected.
 */
suspend fun INostrClient.fetchAllWithHooks(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    timeoutMs: Long = 8_000L,
    subscriptionId: String = newSubId(),
    pendingOnAuthRequired: Boolean = false,
    deadOut: MutableMap<NormalizedRelayUrl, DrainFailure>? = null,
    onTimeout: ((stalled: Set<NormalizedRelayUrl>, doneReasons: Map<NormalizedRelayUrl, String>, collected: List<Pair<NormalizedRelayUrl, Event>>) -> Unit)? = null,
    onEvent: suspend (relay: NormalizedRelayUrl, event: Event) -> Boolean,
): List<Pair<NormalizedRelayUrl, Event>> {
    if (filters.isEmpty()) return emptyList()
    val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(UNLIMITED)
    // Carries the terminal reason per relay so a timeout can distinguish a slow
    // relay (never terminal) from a connect failure / CLOSED.
    val doneChannel = Channel<Pair<NormalizedRelayUrl, String>>(UNLIMITED)
    val remaining = filters.keys.toMutableSet()
    val doneReasons = HashMap<NormalizedRelayUrl, String>()
    val listener =
        object : SubscriptionListener {
            override fun onEvent(
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
                // Keep the relay pending on an auth-required refusal: the authenticator answers the
                // challenge and re-fires this subscription, so the post-auth events still arrive.
                if (pendingOnAuthRequired && MachineReadablePrefix.parse(message) == MachineReadablePrefix.AUTH_REQUIRED) return
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
    val collected = mutableListOf<Pair<NormalizedRelayUrl, Event>>()
    try {
        subscribe(subscriptionId, filters, listener)
        val completed =
            withTimeoutOrNull(timeoutMs) {
                while (remaining.isNotEmpty()) {
                    select {
                        eventChannel.onReceive { pair ->
                            if (onEvent(pair.first, pair.second)) collected.add(pair)
                        }
                        doneChannel.onReceive { (relay, reason) ->
                            remaining.remove(relay)
                            doneReasons[relay] = reason
                        }
                    }
                }
                // Drain any events that landed after EOSE but before cancel
                while (true) {
                    val r = eventChannel.tryReceive()
                    if (!r.isSuccess) break
                    val pair = r.getOrThrow()
                    if (onEvent(pair.first, pair.second)) collected.add(pair)
                }
                true
            }
        if (completed == null && remaining.isNotEmpty()) {
            onTimeout?.invoke(remaining, doneReasons, collected)
        }
    } finally {
        unsubscribe(subscriptionId)
        eventChannel.close()
        doneChannel.close()
    }
    deadOut?.let { out ->
        for ((relay, reason) in doneReasons) {
            classifyDrainFailure(reason)?.let { out[relay] = it }
        }
    }
    return collected
}

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
    timeoutMs: Long = 30_000L,
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
                timeoutMs = timeoutMs,
                maxConcurrentRelays = maxConcurrentRelays,
            ) { event, relay -> eventChannel.trySend(relay to event) }
        } finally {
            eventChannel.close()
        }
        consumer.join()
    }
    return collected
}
