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
): Map<NormalizedRelayUrl, PublishResult> {
    val resultChannel = Channel<DetailedResult>(UNLIMITED)

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

            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                super.onIncomingMessage(relay, msgStr, msg)

                when (msg) {
                    is OkMessage -> {
                        if (msg.eventId == event.id) {
                            resultChannel.trySend(DetailedResult(relay.url, msg.success, msg.message))
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
                            // The withTimeout block will cancel the coroutine if the loop takes too long
                            withTimeoutOrNull(timeoutInSeconds * 1000) {
                                while (receivedResults.size < relayList.size) {
                                    val result = resultChannel.receive()

                                    val currentResult = receivedResults[result.relay]
                                    // do not override a successful result.
                                    if (currentResult == null || !currentResult.accepted) {
                                        receivedResults[result.relay] = PublishResult(result.success, result.message)
                                    }
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
)
