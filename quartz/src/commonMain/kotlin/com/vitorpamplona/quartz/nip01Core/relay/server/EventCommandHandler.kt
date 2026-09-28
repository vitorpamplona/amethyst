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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext

/**
 * Answers EVENT commands that are requests *to the relay* rather than content
 * *for* it — e.g. a NIP-43 join (kind 28934) or leave (kind 28936) request.
 *
 * [RelaySession] offers every inbound EVENT to the handler **before** the
 * connection's policy chain and the store. Returning `null` means "not mine":
 * the event continues down the normal path (policies, then the store).
 * Returning an [OkMessage] consumes the event — the session sends that `OK`
 * and the event is neither stored nor fanned out to subscribers.
 *
 * Because it runs ahead of the policy chain, a handler owns the whole
 * validation of the events it consumes: signature (with parallel verify on,
 * nothing upstream of the store has checked it), timestamps, and any
 * allow/deny decision. A throw (other than cancellation) is answered with
 * `OK false "error: …"`.
 *
 * Install one on a server with [RelayServerBase.eventCommandHandler].
 */
fun interface EventCommandHandler {
    suspend fun handle(
        event: Event,
        ctx: RequestContext,
    ): OkMessage?
}
