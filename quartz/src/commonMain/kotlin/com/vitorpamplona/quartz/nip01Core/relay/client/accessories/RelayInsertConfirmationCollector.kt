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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.utils.Log

/**
 * Listens to INostrClient's relay messages and reports `OK`s for published events: [onRelayReceived] for an acceptance and, when given,
 * [onRelayRejected] for a definitive refusal with the relay's reason. An `auth-required:` refusal
 * is NOT reported — the client authenticates and resends, so it is not the relay's final answer —
 * and a `duplicate:` refusal counts as an acceptance.
 */
class RelayInsertConfirmationCollector(
    val client: INostrClient,
    val onRelayRejected: ((eventId: HexKey, relay: IRelayClient, reason: String) -> Unit)? = null,
    val onRelayReceived: (eventId: HexKey, relay: IRelayClient) -> Unit,
) {
    private val clientListener =
        object : RelayConnectionListener {
            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg !is OkMessage) return
                // NIP-01: "duplicate:" means the relay already has the event. Most relays send it
                // with `true`, some with `false`; either way the event is there.
                if (msg.success || msg.message.startsWith(DUPLICATE_PREFIX)) {
                    onRelayReceived(msg.eventId, relay)
                } else if (!msg.message.startsWith(AUTH_REQUIRED_PREFIX)) {
                    onRelayRejected?.invoke(msg.eventId, relay, msg.message)
                }
            }
        }

    init {
        Log.d("RelayInsertConfirmationCollector", "Init, Subscribe")
        client.addConnectionListener(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("RelayInsertConfirmationCollector", "Destroy, Unsubscribe")
        client.removeConnectionListener(clientListener)
    }
}

private const val AUTH_REQUIRED_PREFIX = "auth-required:"

private const val DUPLICATE_PREFIX = "duplicate:"
