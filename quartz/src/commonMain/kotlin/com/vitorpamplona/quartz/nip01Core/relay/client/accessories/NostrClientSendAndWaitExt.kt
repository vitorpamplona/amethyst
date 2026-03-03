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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
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

class Result(
    val relay: NormalizedRelayUrl,
    val success: Boolean,
)

@OptIn(DelicateCoroutinesApi::class)
suspend fun INostrClient.sendAndWaitForResponse(
    event: Event,
    relayList: Set<NormalizedRelayUrl>,
    timeoutInSeconds: Long = 15,
): Boolean = sendAndWaitForResponseDetailed(event, relayList, timeoutInSeconds).any { it.value }

/**
 * Sends an event to the given relays and waits for OK responses.
 * Returns per-relay results: relay URL -> accepted (true/false).
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun INostrClient.sendAndWaitForResponseDetailed(
    event: Event,
    relayList: Set<NormalizedRelayUrl>,
    timeoutInSeconds: Long = 15,
): Map<NormalizedRelayUrl, Boolean> {
    val resultChannel = Channel<Result>(UNLIMITED)

    Log.d("sendAndWaitForResponse", "Waiting for ${relayList.size} responses")

    val subscription =
        object : IRelayClientListener {
            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                if (relay.url in relayList) {
                    resultChannel.trySend(Result(relay.url, false))
                    Log.d("sendAndWaitForResponse", "Error from relay ${relay.url}: $errorMessage")
                }
            }

            override fun onDisconnected(relay: IRelayClient) {
                if (relay.url in relayList) {
                    resultChannel.trySend(Result(relay.url, false))
                    Log.d("sendAndWaitForResponse", "Disconnected from relay ${relay.url}")
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
                            resultChannel.trySend(Result(relay.url, msg.success))
                            Log.d("sendAndWaitForResponse", "onSendResponse Received response for ${msg.eventId} from relay ${relay.url} message ${msg.message} success ${msg.success}")
                        }
                    }
                }
            }
        }

    subscribe(subscription)

    // subscribe before sending the result.
    val resultSubscription =
        coroutineScope {
            val result =
                async {
                    val receivedResults = mutableMapOf<NormalizedRelayUrl, Boolean>()
                    // The withTimeout block will cancel the coroutine if the loop takes too long
                    withTimeoutOrNull(timeoutInSeconds * 1000) {
                        while (receivedResults.size < relayList.size) {
                            val result = resultChannel.receive()

                            val currentResult = receivedResults[result.relay]
                            // do not override a successful result.
                            if (currentResult == null || !currentResult) {
                                receivedResults[result.relay] = result.success
                            }
                        }
                    }
                    receivedResults
                }

            send(event, relayList)

            result
        }

    val receivedResults = resultSubscription.await()

    unsubscribe(subscription)

    // Clean up the channel
    resultChannel.close()

    Log.d("sendAndWaitForResponse", "Finished with ${receivedResults.size} results")

    return receivedResults
}
