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

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * The host-agnostic orchestration brain: turns one raw applet envelope into an [Outcome] a platform
 * host acts on. It owns the decode → broker → encode flow, the fire-and-forget edge ops
 * (`relay.close`, `resource.cancel`), and the subscribe-vs-reply decision — everything *except* the
 * transport (Messenger / IPC / in-process) and the live relay subscription, which are the host's job.
 *
 * Both the Android `:napplet` service and a future desktop host route through here, so their wire
 * behavior can't drift. (The `shell.ready` handshake is handled at the WebView edge before the
 * boundary, so the router never sees it.)
 */
object NappletRequestRouter {
    sealed interface Outcome {
        /** Nothing to do (e.g. a malformed fire-and-forget message). */
        data object Ignore : Outcome

        /** Send this `.result` payload back, correlated to the request's id. */
        data class Reply(
            val payload: String,
        ) : Outcome

        /** Open a live relay subscription; the host streams `relay.event`/`relay.eose`/`relay.closed` by [subId]. */
        data class OpenSubscription(
            val subId: String,
            val filters: List<Filter>,
        ) : Outcome

        /** Stop the live subscription [subId] (fire-and-forget; no reply). */
        data class CloseSubscription(
            val subId: String,
        ) : Outcome

        /** Start streaming `identity.changed` pushes when the active user's key changes (fire-and-forget). */
        data object WatchIdentity : Outcome

        /** Stop the `identity.changed` stream (fire-and-forget; no reply). */
        data object UnwatchIdentity : Outcome

        /** Push these envelope(s) to the applet immediately, unkeyed (e.g. an EOSE closing a refused sub). */
        data class Push(
            val payloads: List<String>,
        ) : Outcome
    }

    suspend fun route(
        broker: NappletBroker,
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
        payload: String,
    ): Outcome {
        val requestType = runCatching { NappletProtocolJson.readType(payload) }.getOrNull() ?: "napplet"

        // Fire-and-forget edge ops that never reach the broker.
        when (requestType) {
            "relay.close" -> {
                val subId = runCatching { NappletProtocolJson.readSubId(payload) }.getOrNull()
                return if (subId != null) Outcome.CloseSubscription(subId) else Outcome.Ignore
            }
            "resource.cancel" ->
                return Outcome.Reply(NappletProtocolJson.encodeResponse(requestType, NappletResponse.Done))
            // identity.watch/unwatch are a push subscription (like relay.subscribe), gated on the
            // IDENTITY declaration directly — the actual pubkey stream is the host's job.
            "identity.watch" ->
                return if (NappletCapability.IDENTITY in declared) Outcome.WatchIdentity else Outcome.Ignore
            "identity.unwatch" ->
                return Outcome.UnwatchIdentity
        }

        val request =
            runCatching { NappletProtocolJson.decodeRequest(payload) }.getOrNull()
                ?: return Outcome.Reply(NappletProtocolJson.encodeResponse(requestType, NappletResponse.Failed("Malformed or unsupported request.")))

        val response = broker.handle(identity, request, declared)

        // A subscription streams pushes keyed by subId instead of sending a .result.
        if (requestType == "relay.subscribe") {
            val subId =
                runCatching { NappletProtocolJson.readSubId(payload) }.getOrNull()
                    ?: return Outcome.Ignore
            return if (response is NappletResponse.Subscribed) {
                Outcome.OpenSubscription(subId, runCatching { NappletProtocolJson.decodeFilterList(payload) }.getOrDefault(emptyList()))
            } else {
                // Not authorized → close it immediately with an empty EOSE.
                Outcome.Push(listOf(NappletProtocolJson.encodeRelayEose(subId)))
            }
        }

        return Outcome.Reply(NappletProtocolJson.encodeResponse(requestType, response))
    }
}
