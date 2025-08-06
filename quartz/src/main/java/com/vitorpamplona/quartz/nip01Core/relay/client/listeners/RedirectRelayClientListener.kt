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
package com.vitorpamplona.quartz.nip01Core.relay.client.listeners

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient

open class RedirectRelayClientListener(
    val listener: IRelayClientListener,
) : IRelayClientListener {
    override fun onEvent(
        relay: IRelayClient,
        subId: String,
        event: Event,
        arrivalTime: Long,
        afterEOSE: Boolean,
    ) = listener.onEvent(relay, subId, event, arrivalTime, afterEOSE)

    override fun onEOSE(
        relay: IRelayClient,
        subId: String,
        arrivalTime: Long,
    ) = listener.onEOSE(relay, subId, arrivalTime)

    override fun onError(
        relay: IRelayClient,
        subId: String,
        error: Error,
    ) = listener.onError(relay, subId, error)

    override fun onAuth(
        relay: IRelayClient,
        challenge: String,
    ) = listener.onAuth(relay, challenge)

    override fun onAuthed(
        relay: IRelayClient,
        eventId: String,
        success: Boolean,
        message: String,
    ) = listener.onAuthed(relay, eventId, success, message)

    override fun onRelayStateChange(
        relay: IRelayClient,
        type: RelayState,
    ) = listener.onRelayStateChange(relay, type)

    override fun onNotify(
        relay: IRelayClient,
        description: String,
    ) = listener.onNotify(relay, description)

    override fun onClosed(
        relay: IRelayClient,
        subId: String,
        message: String,
    ) = listener.onClosed(relay, subId, message)

    override fun onBeforeSend(
        relay: IRelayClient,
        event: Event,
    ) = listener.onBeforeSend(relay, event)

    override fun onSend(
        relay: IRelayClient,
        msg: String,
        success: Boolean,
    ) = listener.onSend(relay, msg, success)

    override fun onSendResponse(
        relay: IRelayClient,
        eventId: String,
        success: Boolean,
        message: String,
    ) = listener.onSendResponse(relay, eventId, success, message)
}
