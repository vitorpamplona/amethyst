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
 * Listens to a relay's `OK` frames for every event this client publishes.
 *
 * [onRelayReceived] fires on `OK true` — the relay stored the event. [onRelayRejected], when
 * supplied, fires on `OK false` with the relay's verbatim reason (NIP-01 suggests a
 * machine-readable prefix; parse it with `MachineReadablePrefix.parse`). A refusal is the only
 * signal a relay ever gives that a published event did not land, so a caller that ignores it
 * cannot distinguish "refused" from "still in flight".
 *
 * The reason is passed through untouched. In particular `duplicate:` is reported as a rejection
 * here because that is what the frame said, even though it means the relay already holds the
 * event — callers that care (delivery ticks) treat it as acceptance themselves, since relays
 * disagree on which OK flag to pair it with.
 */
class RelayInsertConfirmationCollector(
    val client: INostrClient,
    val onRelayRejected: ((eventId: HexKey, relay: IRelayClient, reason: String) -> Unit)? = null,
    val onRelayReceived: (eventId: HexKey, relay: IRelayClient) -> Unit,
) {
    private val clientListener =
        object : RelayConnectionListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg !is OkMessage) return
                if (msg.success) {
                    onRelayReceived(msg.eventId, relay)
                } else {
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
