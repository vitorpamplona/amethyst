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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Asks the user to decide a capability the napplet does not yet have a standing grant for.
 * The implementation drives the consent UI (on Android, an Activity in the **main** process)
 * and suspends until the user answers. Returning [GrantState.DENY] (or any non-allowing
 * state) blocks the in-flight request.
 *
 * It receives the concrete [request] as well as the [capability] so the prompt can describe
 * exactly what the applet is asking to do (e.g. the kind of event it wants signed), not just
 * the broad capability class.
 */
fun interface NappletConsentPrompt {
    suspend fun request(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): GrantState
}

/**
 * Bridges the broker to the user's relays for the [NappletCapability.RELAY] capability.
 * Supplied by the host (an OkHttp/NostrClient-backed implementation in `amethyst`); kept as
 * an interface so the broker stays platform-agnostic and testable. A `null` gateway makes the
 * broker answer relay requests with [com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse.Unsupported].
 */
fun interface NappletRelayGateway {
    /** Publishes [event] and returns the relay URLs that accepted it (empty = nowhere reached). */
    suspend fun publish(event: Event): List<String>
}
