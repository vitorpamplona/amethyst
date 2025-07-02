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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.simple

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RedirectRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.LargeCache

class OutboxProtector(
    listener: IRelayClientListener,
) : RedirectRelayClientListener(listener) {
    /**
     * Auth procedures require us to keep track of the outgoing events
     * to make sure the relay waits for the auth to finish and send them.
     */
    private val outboxCache = LargeCache<HexKey, Event>()

    override fun onRelayStateChange(
        relay: IRelayClient,
        type: RelayState,
    ) {
        if (type == RelayState.CONNECTED) {
            outboxCache.forEach { id, event ->
                relay.sendEvent(event)
            }
        }

        super.onRelayStateChange(relay, type)
    }

    override fun onAuthed(
        relay: IRelayClient,
        eventId: String,
        success: Boolean,
        message: String,
    ) {
        super.onAuthed(relay, eventId, success, message)
        outboxCache.forEach { id, event ->
            relay.sendEvent(event)
        }
    }

    override fun onBeforeSend(
        relay: IRelayClient,
        event: Event,
    ) {
        if (event !is RelayAuthEvent) {
            outboxCache.put(event.id, event)
        }
        super.onBeforeSend(relay, event)
    }

    override fun onSendResponse(
        relay: IRelayClient,
        eventId: String,
        success: Boolean,
        message: String,
    ) {
        // remove from cache for any error that is not an auth required error.
        // for auth required, we will do the auth and try to send again.
        if (outboxCache.containsKey(eventId) && !message.startsWith("auth-required")) {
            outboxCache.remove(eventId)
        }

        super.onSendResponse(relay, eventId, success, message)
    }
}
