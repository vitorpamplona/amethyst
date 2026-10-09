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

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.AuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.DEFAULT_AUTH_GRACE_MS
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.authSuccessMarks
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.awaitAuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.hasAuthResponder
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip45Count.mergeCountResults
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * NIP-45 COUNT against one [relay]: the fan-out [count] over a single relay, so it behaves the
 * same way. An `auth-required:` refusal waits for this client's NIP-42 responder, and the
 * AUTH's `OK` re-fires the COUNT. A relay that refuses the COUNT (a `CLOSED`, a NOTICE refusing
 * the verb, see [isCountRejectionNotice]) or cannot be reached returns `null` at once; one that
 * stays silent, after [idleTimeoutMs].
 *
 * @return The [CountResult], or `null` when the relay did not answer one.
 */
suspend fun INostrClient.count(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long = 15_000,
): CountResult? = count(mapOf(relay to listOf(filter)), idleTimeoutMs)[relay]

/**
 * Sends NIP-45 COUNT queries to multiple relays in parallel
 * (one filter per relay) and suspends until all results arrive
 * or the timeout expires.
 *
 * [idleTimeoutMs] is an **idle window measured from the most recent progress**, not a
 * wall-clock deadline for the whole batch — the package-wide accessory
 * convention: each *new* relay's COUNT result restarts it, so a large fan-out
 * where results keep trickling in is never cut short. A relay re-sending a result
 * it already gave is not progress and does not restart the window, which makes
 * the call self-bounding (at most one window per relay). A caller wanting a hard
 * wall-clock bound has `withTimeoutOrNull(ms) { count(...) }` — at the cost of
 * discarding the partial map, which is why this returns whatever arrived instead.
 *
 * A relay that will not answer stops being waited for at once: a `CLOSED` for its COUNT (an
 * `auth-required:` one only once this client's NIP-42 responder has failed to satisfy it), a
 * NOTICE refusing the verb ([isCountRejectionNotice]), or a failed connection. Its key is
 * absent from the result; an answer it sends later anyway (a reconnect re-fires the COUNT) is
 * still kept.
 *
 * @param filters Map of relay -> filter to count.
 * @param idleTimeoutMs Idle window between new responses (default 15 s).
 * @return Map of relay -> [CountResult] for every relay that responded in time.
 */
suspend fun INostrClient.count(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 15_000,
): Map<NormalizedRelayUrl, CountResult> {
    if (filters.isEmpty()) return emptyMap()
    val pendingOnAuthRequired = hasAuthResponder()

    // Filled before the listener is registered: it reads this map on every CLOSED and COUNT
    // the shared client receives, from the relays' reader threads.
    val subIdToRelay = filters.keys.associateBy { newSubId() }
    val resultChannel = Channel<Pair<NormalizedRelayUrl, CountResult>>(UNLIMITED)
    val authRefusalChannel = Channel<NormalizedRelayUrl>(UNLIMITED)
    // Relays that met a NIP-42 wall we could not get over. They are counted as
    // answered-for-loop-purposes so the fan-out isn't held open on them, but they
    // contribute no [CountResult] — the caller sees an absent key, not a zero.
    val gaveUp = mutableSetOf<NormalizedRelayUrl>()
    val gaveUpChannel = Channel<NormalizedRelayUrl>(UNLIMITED)

    val listener =
        object : RelayConnectionListener {
            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is CountMessage) {
                    val relayUrl = subIdToRelay[msg.queryId] ?: return
                    resultChannel.trySend(relayUrl to msg.result)
                }
                if (msg is ClosedMessage) {
                    val relayUrl = subIdToRelay[msg.subId] ?: return
                    if (pendingOnAuthRequired && MachineReadablePrefix.parse(msg.message) == MachineReadablePrefix.AUTH_REQUIRED) {
                        authRefusalChannel.trySend(relayUrl)
                    } else {
                        gaveUpChannel.trySend(relayUrl)
                    }
                }
                if (msg is NoticeMessage && relay.url in filters && isCountRejectionNotice(msg.message)) {
                    gaveUpChannel.trySend(relay.url)
                }
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                if (relay.url in filters) gaveUpChannel.trySend(relay.url)
            }
        }

    val results = mutableMapOf<NormalizedRelayUrl, CountResult>()

    try {
        addConnectionListener(listener)

        val authMarks = if (pendingOnAuthRequired) authSuccessMarks(filters.keys) else emptyMap()
        subIdToRelay.forEach { (subId, relay) ->
            count(subId = subId, filters = mapOf(relay to filters.getValue(relay)))
        }

        coroutineScope {
            val authResolver =
                launch {
                    val resolving = mutableSetOf<NormalizedRelayUrl>()
                    for (relay in authRefusalChannel) {
                        if (!resolving.add(relay)) continue
                        launch {
                            if (awaitAuthOutcome(relay, authMarks[relay] ?: 0, DEFAULT_AUTH_GRACE_MS, idleTimeoutMs) != AuthOutcome.AUTHENTICATED) {
                                gaveUpChannel.trySend(relay)
                            }
                        }
                    }
                }

            // One idle window per new relay result. The inner loop absorbs repeats
            // (a relay answering twice) inside the SAME window, so only genuinely
            // new information pushes the deadline out — bounding the call at one
            // window per relay without needing a wall-clock ceiling. A relay giving
            // up at an auth wall counts as progress for the same reason an answer
            // does: it is one fewer relay we are still waiting on.
            while (results.size + gaveUp.size < filters.size) {
                val progressed =
                    withTimeoutOrNull(idleTimeoutMs) {
                        while (true) {
                            // Cancellation (this window expiring, or the caller giving up)
                            // only lands at a suspension point, and receive() does not
                            // suspend while the channel has buffered results — so check
                            // explicitly rather than draining a backlog uninterruptibly.
                            coroutineContext.ensureActive()
                            val advanced =
                                select<Boolean> {
                                    resultChannel.onReceive { (relay, result) ->
                                        // put() returns the previous value: null means this relay
                                        // had not answered yet, i.e. real progress — unless it
                                        // already counted as given up (a CLOSED, a NOTICE or a
                                        // failed connection, then the answer after a reconnect):
                                        // keep the answer, but count the relay once, or the loop
                                        // ends before the last relay answers.
                                        results.put(relay, result) == null && !gaveUp.remove(relay)
                                    }
                                    gaveUpChannel.onReceive { relay ->
                                        relay !in results && gaveUp.add(relay)
                                    }
                                }
                            if (advanced) break
                        }
                        true
                    }
                if (progressed == null) break
            }
            authResolver.cancel()
        }

        // A result can land after the last window closed but before we unsubscribe;
        // it costs nothing to keep, and dropping it would understate the count.
        while (true) {
            val (relay, result) = resultChannel.tryReceive().getOrNull() ?: break
            results[relay] = result
        }
    } finally {
        subIdToRelay.keys.forEach { unsubscribe(it) }
        removeConnectionListener(listener)
        resultChannel.close()
        authRefusalChannel.close()
        gaveUpChannel.close()
    }

    return results
}

/**
 * Queries multiple relays for a COUNT and combines the answers into one figure.
 *
 * The combination rules — and why summing is never one of them — live in [mergeCountResults].
 *
 * @param relays List of relays to query.
 * @param filter The filter to count against.
 * @param idleTimeoutMs Idle window between responses (default 15 s) — see [count].
 * @return A merged [CountResult], or `null` if no relay responded.
 */
suspend fun INostrClient.countMerged(
    relays: List<NormalizedRelayUrl>,
    filter: Filter,
    idleTimeoutMs: Long = 15_000,
): CountResult? {
    if (relays.isEmpty()) return null

    val results =
        count(
            filters = relays.associateWith { listOf(filter) },
            idleTimeoutMs = idleTimeoutMs,
        )

    return mergeCountResults(results.values)
}
