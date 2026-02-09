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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log

class RelayActiveRequestStates(
    val client: INostrClient,
) {
    private var subStates = mutableMapOf<NormalizedRelayUrl, RequestSubscriptionState<String>>()

    fun subGetOrCreate(relay: NormalizedRelayUrl): RequestSubscriptionState<String> = subStates[relay] ?: RequestSubscriptionState<String>().also { subStates.put(relay, it) }

    private val clientListener =
        object : IRelayClientListener {
            override fun onConnecting(relay: IRelayClient) {
                subStates[relay.url] = RequestSubscriptionState()
            }

            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                when (msg) {
                    is EventMessage -> subGetOrCreate(relay.url).onNewEvent(msg.subId)
                    is EoseMessage -> subGetOrCreate(relay.url).onEose(msg.subId)
                    is ClosedMessage -> subGetOrCreate(relay.url).onClosed(msg.subId)
                }
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                when (cmd) {
                    is ReqCmd -> subGetOrCreate(relay.url).onOpenReq(cmd.subId, cmd.filters)
                    is CloseCmd -> subGetOrCreate(relay.url).onCloseReq(cmd.subId)
                }
            }

            override fun onDisconnected(relay: IRelayClient) {
                subStates.remove(relay.url)
            }
        }

    init {
        Log.d("RelaySubStateMachine", "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("RelaySubStateMachine", "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
