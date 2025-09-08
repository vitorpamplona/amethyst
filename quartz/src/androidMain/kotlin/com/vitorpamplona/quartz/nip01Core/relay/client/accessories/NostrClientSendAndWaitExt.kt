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

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.DelicateCoroutinesApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(DelicateCoroutinesApi::class)
suspend fun INostrClient.sendAndWaitForResponse(
    event: Event,
    relayList: Set<NormalizedRelayUrl>,
    timeoutInSeconds: Long = 15,
): Boolean {
    val size = relayList.size
    val latch = CountDownLatch(size)
    val relayResults = mutableMapOf<NormalizedRelayUrl, Boolean>()
    var result = false

    Log.d("sendAndWaitForResponse", "Waiting for $size responses")

    val subscription =
        object : IRelayClientListener {
            override fun onError(
                relay: IRelayClient,
                subId: String,
                error: Error,
            ) {
                if (relay.url in relayList && relayResults[relay.url] == null) {
                    relayResults[relay.url] = false
                    latch.countDown()
                }
                Log.d("sendAndWaitForResponse", "onError Error from relay ${relay.url} count: ${latch.count} error: $error")
            }

            override fun onRelayStateChange(
                relay: IRelayClient,
                type: RelayState,
            ) {
                if (type == RelayState.DISCONNECTED) {
                    if (relay.url in relayList && relayResults[relay.url] == null) {
                        relayResults[relay.url] = false
                        latch.countDown()
                    }
                }
                Log.d("sendAndWaitForResponse", "onRelayStateChange ${type.name} from relay ${relay.url} count: ${latch.count}")
            }

            override fun onSendResponse(
                relay: IRelayClient,
                eventId: String,
                success: Boolean,
                message: String,
            ) {
                if (eventId == event.id) {
                    if (relayResults[relay.url] == null) {
                        latch.countDown()
                        relayResults[relay.url] = success
                    } else {
                        if (success && relayResults[relay.url] == false) {
                            relayResults[relay.url] = true
                        }
                    }

                    if (success) {
                        result = true
                    }

                    Log.d("sendAndWaitForResponse", "onSendResponse Received response for $eventId from relay ${relay.url} count: ${latch.count} message $message success $success")
                }
            }
        }

    subscribe(subscription)

    send(event, relayList)

    latch.await(timeoutInSeconds, TimeUnit.SECONDS)

    unsubscribe(subscription)

    Log.d("sendAndWaitForResponse", "countdown finished")

    return result
}
