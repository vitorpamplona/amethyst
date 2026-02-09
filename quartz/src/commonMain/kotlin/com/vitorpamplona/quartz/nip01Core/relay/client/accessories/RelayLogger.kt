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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.Log

/**
 * Listens to NostrClient's onNotify messages from the relay
 */
class RelayLogger(
    val client: INostrClient,
    val debugSending: Boolean = false,
    val debugReceiving: Boolean = false,
) {
    fun logTag(url: NormalizedRelayUrl) = "Relay ${url.displayUrl()}"

    private val clientListener =
        object : IRelayClientListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                val logTag = logTag(relay.url)

                when (msg) {
                    is EventMessage -> if (debugReceiving) Log.d(logTag, "Received: $msgStr")
                    is EoseMessage -> if (debugReceiving) Log.d(logTag, "EOSE: ${msg.subId}")
                    is NoticeMessage -> Log.w(logTag, "Notice: ${msg.message}")
                    is OkMessage -> if (debugReceiving) Log.d(logTag, "OK: ${msg.eventId} ${msg.success} ${msg.message}")
                    is AuthMessage -> if (debugReceiving) Log.d(logTag, "Auth: ${msg.challenge}")
                    is NotifyMessage -> if (debugReceiving) Log.d(logTag, "Notify: ${msg.message}")
                    is ClosedMessage -> Log.w(logTag, "Closed: ${msg.subId} ${msg.message}")
                }
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                if (success) {
                    if (debugSending) {
                        Log.d(logTag(relay.url), "Sent (${cmdStr.length} chars): $cmdStr")
                    }
                } else {
                    Log.e(logTag(relay.url), "Failure sending (${cmdStr.length} chars): $cmdStr")
                }
            }

            override fun onConnecting(relay: IRelayClient) {
                Log.d(logTag(relay.url), "Connecting...")
            }

            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                Log.d(logTag(relay.url), "OnOpen (ping: ${pingMillis}ms${if (compressed) ", using compression" else ""})")
            }

            override fun onDisconnected(relay: IRelayClient) {
                Log.d(logTag(relay.url), "Disconnected")
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                super.onCannotConnect(relay, errorMessage)
                Log.e(logTag(relay.url), errorMessage)
            }
        }

    init {
        Log.d("RelayLogger", "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("RelayLogger", "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
