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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * One relay's verdict on a published event: [accepted] plus the reason the
 * relay (or the transport) gave. For an accepted event [message] is whatever
 * the relay put in the OK (usually empty); for a rejection it is the NIP-01
 * machine-readable reason (`blocked: …`, `rate-limited: …`, `pow: …`), a
 * connection error, or `"no response within timeout"`.
 */
class PublishResult(
    val accepted: Boolean,
    val message: String,
    /**
     * Milliseconds from the publish to this relay's OK (true or false — a rejection
     * is still a measured round trip), or -1 when the relay never answered with an
     * OK. On an already-open socket this is an honest NIP-66 `rtt-write`.
     */
    val elapsedMs: Long = -1,
) {
    /**
     * True when this failure came from the transport (never connected,
     * dropped, or silent past the timeout) rather than from the relay
     * actually answering `OK false`. Callers use this to tell "the relay
     * refused the event" apart from "the relay never weighed in".
     */
    val isTransportFailure: Boolean
        get() = !accepted && (message == NO_RESPONSE || message == DISCONNECTED || message.startsWith(CANNOT_CONNECT_PREFIX))

    companion object {
        const val NO_RESPONSE = "no response within timeout"
        const val DISCONNECTED = "disconnected before OK"
        const val CANNOT_CONNECT_PREFIX = "cannot connect: "
    }
}

/**
 * How many times a relay that answered with a transport failure rather than an
 * OK is re-sent to before the failure is reported. One retry covers the common
 * case — a socket that dropped between our EVENT frame and the relay's OK —
 * without turning a genuinely unreachable relay into a long stall, because the
 * retries share the caller's existing publish timeout.
 */
const val DEFAULT_TRANSPORT_RETRIES = 1

/**
 * Internal channel marker for "this relay is back up", so the wait loop — the
 * one coroutine that owns the retry bookkeeping — can re-issue a send that a
 * disconnected relay would have dropped. The NUL prefix keeps it out of reach
 * of any real relay message, and it never surfaces in a [PublishResult].
 */
private const val RECONNECTED = "\u0000publish-retry-reconnected"

@OptIn(DelicateCoroutinesApi::class)
suspend fun INostrClient.publishAndConfirm(
    event: Event,
    relayList: Set<NormalizedRelayUrl>,
    timeoutInSeconds: Long = 15,
): Boolean = publishAndCollectResults(event, relayList, timeoutInSeconds).any { it.value.accepted }

/**
 * Sends an event to the given relays and waits for OK responses.
 * Returns per-relay results: relay URL -> accepted (true/false).
 * Keeps the historical contract: only relays that RESPONDED (an OK, a
 * connect failure, or a disconnect) appear — a relay that stayed silent
 * past the timeout is absent, not reported as `false`, so long-standing
 * callers that render the false entries as "rejected by" don't start
 * blaming relays that merely never answered. Prefer
 * [publishAndCollectResults] when the caller can surface the reasons.
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun INostrClient.publishAndConfirmDetailed(
    event: Event,
    relayList: Set<NormalizedRelayUrl>,
    timeoutInSeconds: Long = 15,
): Map<NormalizedRelayUrl, Boolean> =
    publishAndCollectResults(event, relayList, timeoutInSeconds)
        .filterValues { it.message != PublishResult.NO_RESPONSE }
        .mapValues { it.value.accepted }

/**
 * Sends an event to the given relays and waits for OK responses, keeping the
 * per-relay reason alongside the verdict. Relays that never answered inside
 * the timeout are present with `accepted = false, message = "no response
 * within timeout"`, so the result always covers the full [relayList].
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun INostrClient.publishAndCollectResults(
    event: Event,
    relayList: Set<NormalizedRelayUrl>,
    timeoutInSeconds: Long = 15,
    transportRetries: Int = DEFAULT_TRANSPORT_RETRIES,
): Map<NormalizedRelayUrl, PublishResult> {
    val resultChannel = Channel<DetailedResult>(UNLIMITED)
    val mark = TimeSource.Monotonic.markNow()

    Log.d("publishAndConfirm") { "Waiting for ${relayList.size} responses" }

    val subscription =
        object : RelayConnectionListener {
            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                if (relay.url in relayList) {
                    resultChannel.trySend(DetailedResult(relay.url, false, PublishResult.CANNOT_CONNECT_PREFIX + errorMessage))
                    Log.d("publishAndConfirm") { "Error from relay ${relay.url}: $errorMessage" }
                }
            }

            override fun onDisconnected(relay: IRelayClient) {
                if (relay.url in relayList) {
                    resultChannel.trySend(DetailedResult(relay.url, false, PublishResult.DISCONNECTED))
                    Log.d("publishAndConfirm") { "Disconnected from relay ${relay.url}" }
                }
            }

            /**
             * A relay is only sendable once it is back up: publishing to a
             * disconnected relay dials and drops the command, so a retry has to
             * be re-issued from here rather than at the moment we noticed the
             * hang-up.
             */
            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                if (relay.url in relayList) {
                    resultChannel.trySend(DetailedResult(relay.url, false, RECONNECTED))
                }
            }

            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                super.onIncomingMessage(relay, msgStr, msg)

                when (msg) {
                    is OkMessage -> {
                        // The relayList guard matters, not just the id: the same event may
                        // have been published to OTHER relays by an earlier call (probe
                        // waves, republish), and counting their late OKs here would inflate
                        // receivedResults and end the wait loop before every listed relay
                        // answered — misreporting the missing ones as NO_RESPONSE.
                        if (msg.eventId == event.id && relay.url in relayList) {
                            resultChannel.trySend(DetailedResult(relay.url, msg.success, msg.message, mark.elapsedNow().inWholeMilliseconds))
                            Log.d("publishAndConfirm") { "onSendResponse Received response for ${msg.eventId} from relay ${relay.url} message ${msg.message} success ${msg.success}" }
                        }
                    }
                }
            }
        }

    val receivedResults =
        try {
            addConnectionListener(subscription)

            // subscribe before sending the result.
            val resultSubscription =
                coroutineScope {
                    val result =
                        async {
                            val receivedResults = mutableMapOf<NormalizedRelayUrl, PublishResult>()
                            // A relay that hung up or never connected gave no verdict on the
                            // event — it may have stored it, it may not. Re-send to that relay
                            // once (a Nostr event is idempotent under its own id, so the worst
                            // case is a duplicate the relay collapses) and keep waiting for the
                            // OK we were owed, instead of reporting a failed publish for a relay
                            // that is healthy a moment later. The retries live inside the
                            // caller's existing timeout, so nothing waits longer than before.
                            val retriesLeft = relayList.associateWith { transportRetries }.toMutableMap()
                            // The withTimeout block will cancel the coroutine if the loop takes too long
                            withTimeoutOrNull(timeoutInSeconds * 1000) {
                                val awaitingReconnect = mutableSetOf<NormalizedRelayUrl>()
                                while (receivedResults.size < relayList.size) {
                                    val result = resultChannel.receive()

                                    if (result.message == RECONNECTED) {
                                        // The pool flushes what it still owes a relay as part of
                                        // coming back up, so there is nothing to re-send here —
                                        // this only reopens the relay to a fresh verdict.
                                        awaitingReconnect.remove(result.relay)
                                        continue
                                    }

                                    // One dropped socket can report itself more than once
                                    // (the pool's disconnect and the relay client's both land
                                    // here). While a relay is waiting to come back those are
                                    // echoes of the drop we already answered, not new verdicts.
                                    if (result.relay in awaitingReconnect) continue

                                    val currentResult = receivedResults[result.relay]
                                    // do not override a successful result.
                                    if (currentResult == null || !currentResult.accepted) {
                                        receivedResults[result.relay] = PublishResult(result.success, result.message, result.elapsedMs)
                                    }

                                    val recorded = receivedResults[result.relay]
                                    if (recorded != null && recorded.isTransportFailure && (retriesLeft[result.relay] ?: 0) > 0) {
                                        retriesLeft[result.relay] = retriesLeft.getValue(result.relay) - 1
                                        // Drop the provisional verdict so the loop keeps waiting
                                        // for this relay rather than treating the hang-up as its
                                        // answer. If the retry also fails we record it again and
                                        // report the transport failure as before.
                                        receivedResults.remove(result.relay)
                                        awaitingReconnect.add(result.relay)
                                        Log.d("publishAndConfirm") {
                                            "Retrying ${event.id} on ${result.relay} after ${recorded.message}"
                                        }
                                        // The event is still in the pool's outbox for this relay,
                                        // so the dial is the whole job: the pool flushes what it
                                        // owes the relay once the socket is back. Ignore the
                                        // accumulated backoff — this is a user-visible publish
                                        // waiting on it, not a background refresh.
                                        resetBackoff()
                                        reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
                                    }
                                }
                            }
                            // A relay whose last word was a transport failure and whose retry
                            // never came back inside the timeout still has to be reported: the
                            // caller promised a verdict for every listed relay, and "we retried"
                            // is not one.
                            for (relay in relayList) {
                                if (relay !in receivedResults && retriesLeft.getValue(relay) < transportRetries) {
                                    receivedResults[relay] = PublishResult(false, PublishResult.DISCONNECTED)
                                }
                            }

                            receivedResults
                        }

                    publish(event, relayList)

                    result
                }

            resultSubscription.await()
        } finally {
            removeConnectionListener(subscription)
        }

    // Clean up the channel
    resultChannel.close()

    Log.d("publishAndConfirm") { "Finished with ${receivedResults.size} results" }

    // Pure construction of the promised invariant: the result covers the
    // full relayList, with never-answered relays reported as NO_RESPONSE.
    return relayList.associateWith { receivedResults[it] ?: PublishResult(false, PublishResult.NO_RESPONSE) }
}

private class DetailedResult(
    val relay: NormalizedRelayUrl,
    val success: Boolean,
    val message: String,
    val elapsedMs: Long = -1,
)
