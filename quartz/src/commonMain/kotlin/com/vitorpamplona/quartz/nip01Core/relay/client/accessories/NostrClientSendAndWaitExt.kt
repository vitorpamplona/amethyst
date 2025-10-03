/**
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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
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
): Boolean {
    val resultChannel = Channel<Result>()

    Log.d("sendAndWaitForResponse", "Waiting for ${relayList.size} responses")

    val subscription =
        object : IRelayClientListener {
            override fun onError(
                relay: IRelayClient,
                subId: String,
                error: Error,
            ) {
                if (relay.url in relayList) {
                    resultChannel.trySend(Result(relay.url, false))
                    Log.d("sendAndWaitForResponse", "onError Error from relay ${relay.url} error: $error")
                }
            }

            override fun onRelayStateChange(
                relay: IRelayClient,
                type: RelayState,
            ) {
                if (type == RelayState.DISCONNECTED && relay.url in relayList) {
                    resultChannel.trySend(Result(relay.url, false))
                    Log.d("sendAndWaitForResponse", "onRelayStateChange ${type.name} from relay ${relay.url}")
                }
            }

            override fun onSendResponse(
                relay: IRelayClient,
                eventId: String,
                success: Boolean,
                message: String,
            ) {
                if (eventId == event.id) {
                    resultChannel.trySend(Result(relay.url, success))
                    Log.d("sendAndWaitForResponse", "onSendResponse Received response for $eventId from relay ${relay.url} message $message success $success")
                }
            }
        }

    subscribe(subscription)

    // subscribe before sending the result.
    val resultSubscription =
        coroutineScope {
            async(Dispatchers.IO) {
                val receivedResults = mutableMapOf<NormalizedRelayUrl, Boolean>()
                // The withTimeout block will cancel the coroutine if the loop takes too long
                withTimeoutOrNull(timeoutInSeconds * 1000) {
                    send(event, relayList)
                    while (receivedResults.size < relayList.size) {
                        val result = resultChannel.receive()

                        val currentResult = receivedResults[result.relay]
                        // do not override a successful result.
                        if (currentResult == null || !currentResult) {
                            receivedResults.put(result.relay, result.success)
                        }
                    }
                }
                receivedResults
            }
        }

    val receivedResults = resultSubscription.await()

    unsubscribe(subscription)

    // Clean up the channel
    resultChannel.close()

    Log.d("sendAndWaitForResponse", "Finished with ${receivedResults.size} results")

    return receivedResults.any { it.value }
}
