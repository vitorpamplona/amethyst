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
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

suspend fun INostrClient.fetchFirst(
    relay: String,
    filter: Filter,
) = fetchFirst(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to listOf(filter)))

suspend fun INostrClient.fetchFirst(
    relay: String,
    filters: List<Filter>,
) = fetchFirst(newSubId(), mapOf(RelayUrlNormalizer.normalize(relay) to filters))

suspend fun INostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    relay: String,
    filters: List<Filter>,
) = fetchFirst(subscriptionId, mapOf(RelayUrlNormalizer.normalize(relay) to filters))

suspend fun INostrClient.fetchFirst(
    relay: NormalizedRelayUrl,
    filter: Filter,
) = fetchFirst(newSubId(), mapOf(relay to listOf(filter)))

suspend fun INostrClient.fetchFirst(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
) = fetchFirst(newSubId(), mapOf(relay to filters))

suspend fun INostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
) = fetchFirst(subscriptionId, mapOf(relay to filters))

/**
 * Subscribe [filters], return the first event any relay delivers (or `null` when
 * every relay reached a terminal state — EOSE, CLOSED, or cannot-connect — with
 * nothing matching, or the line went quiet).
 *
 * [idleTimeoutMs] is an **idle window measured from the most recent progress**, not a
 * wall-clock deadline — the package-wide accessory convention. Progress means a
 * signal that actually advances the fetch: an event, or the first terminal state
 * from a relay still being waited on. Repeat chatter from a relay already
 * accounted for (a CLOSED/reconnect loop) is *not* progress and does not restart
 * the window — the same rule the negentropy watchdog applies to NOTICE/CLOSED
 * error chatter, and what keeps a flapping relay from holding this open forever.
 *
 * That makes the call self-bounding: at most one progress signal per relay, each
 * granting a fresh window. There is deliberately no ceiling parameter — a caller
 * who wants a hard wall-clock bound already has one in
 * `withTimeoutOrNull(ms) { fetchFirst(...) }`, which costs nothing here since a
 * timed-out fetch returns `null` either way.
 */
suspend fun INostrClient.fetchFirst(
    subscriptionId: String = newSubId(),
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 30_000L,
    /**
     * See [fetchAllWithHooks]. Defaults to whether this client has a NIP-42 responder
     * attached: an `auth-required:` CLOSED then keeps the relay in play until the AUTH
     * resolves, instead of counting as one more relay that had nothing.
     *
     * It matters more here than elsewhere. [fetchFirst] returns as soon as EVERY relay
     * is accounted for, so on a single auth-gated relay the refusal *is* the answer, and
     * the `null` it produced was indistinguishable from "no such event exists" — the
     * reading that makes a caller create a duplicate of something it already has.
     */
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
    /** Stage-one grace handed to [awaitAuthOutcome]. */
    authGraceMs: Long = DEFAULT_AUTH_GRACE_MS,
): Event? {
    val eventChannel = Channel<Event>(UNLIMITED)
    val doneChannel = Channel<NormalizedRelayUrl>(UNLIMITED)
    val authRefusalChannel = Channel<NormalizedRelayUrl>(UNLIMITED)
    val remaining = filters.keys.toMutableSet()

    val listener =
        object : SubscriptionListener {
            override suspend fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                eventChannel.trySend(event)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                doneChannel.trySend(relay)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                // An auth-required refusal is not this relay's answer yet: the responder is
                // about to sign, and the client re-fires the subscription on the AUTH's OK.
                // Hand it to the resolver, which returns the relay to the done channel if
                // and only if the challenge fails to satisfy.
                if (pendingOnAuthRequired && MachineReadablePrefix.parse(message) == MachineReadablePrefix.AUTH_REQUIRED) {
                    authRefusalChannel.trySend(relay)
                    return
                }
                doneChannel.trySend(relay)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                doneChannel.trySend(relay)
            }
        }

    // Read before the REQ goes out — see [awaitAuthOutcome]'s `since`.
    val authMarks = if (pendingOnAuthRequired) filters.keys.associateWith { authSuccessMark(it) } else emptyMap()

    var result: Event? = null
    try {
        coroutineScope {
            subscribe(subscriptionId, filters, listener)

            // Ends an auth-gated relay on the AUTH's verdict rather than on the idle
            // window: it rejoins [doneChannel] — counting as that relay's answer — only
            // once the challenge has demonstrably failed. One resolver per relay.
            val authResolver =
                if (pendingOnAuthRequired) {
                    launch {
                        val resolving = mutableSetOf<NormalizedRelayUrl>()
                        for (relay in authRefusalChannel) {
                            if (!resolving.add(relay)) continue
                            launch {
                                if (awaitAuthOutcome(relay, authMarks[relay] ?: 0, authGraceMs, idleTimeoutMs) != AuthOutcome.AUTHENTICATED) {
                                    doneChannel.trySend(relay)
                                }
                            }
                        }
                    }
                } else {
                    null
                }

            fetchFirstLoop(idleTimeoutMs, remaining, eventChannel, doneChannel) { result = it }

            // An event can land after the last terminal signal but before we
            // unsubscribe; without this drain it would be dropped and the fetch
            // would report "nothing found" while holding a match.
            if (result == null) result = eventChannel.tryReceive().getOrNull()

            // Parks on an AUTH that may never settle, so the scope only completes if we end it.
            authResolver?.cancel()
        }
    } finally {
        unsubscribe(subscriptionId)
        eventChannel.close()
        doneChannel.close()
        authRefusalChannel.close()
    }

    return result
}

/**
 * The idle-window wait, lifted out of [fetchFirst] so the auth resolver can share its
 * scope without burying the loop three levels deep. Fills [onFound] with the first event
 * any relay delivers, or returns once every relay in [remaining] is accounted for.
 */
private suspend fun fetchFirstLoop(
    idleTimeoutMs: Long,
    remaining: MutableSet<NormalizedRelayUrl>,
    eventChannel: Channel<Event>,
    doneChannel: Channel<NormalizedRelayUrl>,
    onFound: (Event) -> Unit,
) {
    // One idle window per unit of progress. The inner loop keeps consuming
    // non-progress signals INSIDE the same window, so repeat chatter from an
    // already-accounted-for relay cannot push the deadline out; only a real
    // advance escapes to the outer loop and earns a fresh window.
    while (remaining.isNotEmpty()) {
        val progressed =
            withTimeoutOrNull(idleTimeoutMs) {
                while (true) {
                    // Cancellation (this window expiring, or the caller giving up)
                    // only lands at a suspension point, and select() completes
                    // without suspending while either channel has something
                    // buffered — so check explicitly rather than draining a
                    // backlog of chatter uninterruptibly.
                    coroutineContext.ensureActive()
                    val advanced =
                        select<Boolean> {
                            eventChannel.onReceive { event ->
                                onFound(event)
                                remaining.clear()
                                true
                            }
                            doneChannel.onReceive { relay ->
                                // A relay sends its matching events before its EOSE, so an event may
                                // already be buffered when this completion fires. select() picks a ready
                                // clause at random, so without this drain we could treat the relay as done
                                // and exit while its event still sits unread in the channel.
                                val buffered = eventChannel.tryReceive().getOrNull()
                                if (buffered != null) {
                                    onFound(buffered)
                                    remaining.clear()
                                    true
                                } else {
                                    // Only the FIRST terminal signal from a relay we are still
                                    // waiting on advances the fetch; a repeat is chatter.
                                    remaining.remove(relay)
                                }
                            }
                        }
                    if (advanced) break
                }
                true
            }
        if (progressed == null) break
    }
}
