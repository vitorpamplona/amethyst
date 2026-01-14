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
package com.vitorpamplona.amethyst.service.relayClient.speedLogger

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.bytesUsedInMemory

/**
 * Listens to NostrClient's onNotify messages from the relay
 */
class RelaySpeedLogger(
    val client: INostrClient,
) {
    companion object {
        val TAG: String = RelaySpeedLogger::class.java.simpleName
    }

    var current = FrameStat()

    private val clientListener =
        object : IRelayClientListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is EventMessage) {
                    current.increment(msg.event.kind, msg.subId, relay.url, msgStr.bytesUsedInMemory())
                }
            }
        }

    init {
        Log.d(TAG, "Init, Subscribe")
        client.subscribe(clientListener)
        // OkHttpDebugLogging.enableHttp2()
        // OkHttpDebugLogging.enableTaskRunner()
    }

    fun destroy() {
        // makes sure to run
        Log.d(TAG, "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
