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
package com.vitorpamplona.amethyst.service.relayClient

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.utils.Log

/**
 * Listens to NostrClient's onNotify messages from the relay
 */
class RelayLogger(
    val client: INostrClient,
) {
    companion object {
        val TAG = RelayLogger::class.java.simpleName
    }

    private val clientListener =
        object : IRelayClientListener {
            /** A new message was received */
            override fun onEvent(
                relay: IRelayClient,
                subId: String,
                event: Event,
                arrivalTime: Long,
                afterEOSE: Boolean,
            ) {
                Log.d(TAG, "Relay onEVENT ${relay.url} ($subId - $afterEOSE) ${event.toJson()}")
            }

            override fun onSend(
                relay: IRelayClient,
                msg: String,
                success: Boolean,
            ) {
                Log.d(TAG, "Relay send ${relay.url} (${msg.length} chars) $msg")
            }
        }

    init {
        Log.d(TAG, "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d(TAG, "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
