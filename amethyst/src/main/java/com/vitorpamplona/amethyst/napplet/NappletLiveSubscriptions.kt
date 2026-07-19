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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The registry of live relay subscriptions an applet has open, keyed by its `subId`. Each entry
 * holds the exact [INostrClient] that opened it, so teardown unsubscribes from the right account
 * even after an account switch, plus an EOSE latch so a multi-relay subscription emits a single
 * `relay.eose`. Encodes the `relay.event`/`relay.eose`/`relay.closed` pushes and hands them to the
 * caller-supplied sink — it never touches the transport itself.
 *
 * The account is supplied per [open] by the caller, which resolves it from the requesting surface's
 * LAUNCH account — not from whoever is signed in at the time. A full-screen surface survives an
 * account switch, and reading live would have pointed its REQs at the new account's relays while its
 * signatures still came from the old one. [open] is reached only after the broker authorized the
 * subscription (RELAY consent).
 */
class NappletLiveSubscriptions {
    private val liveSubs = ConcurrentHashMap<String, LiveSub>()
    private val liveSeq = AtomicInteger(0)

    private class LiveSub(
        val clientSubId: String,
        val client: INostrClient,
    ) {
        val eoseSent = AtomicBoolean(false)
    }

    /**
     * Opens a live relay subscription for [nappletSubId], streaming `relay.event`/`relay.eose`/
     * `relay.closed` envelopes to [push] as events arrive. Replaces any existing subscription for
     * the same id. With no account/relays/filters it pushes a single empty EOSE to close it.
     */
    fun open(
        nappletSubId: String,
        filters: List<Filter>,
        account: Account?,
        push: (String) -> Unit,
    ) {
        val relays = account?.homeRelays?.flow?.value ?: emptySet()
        if (account == null || filters.isEmpty() || relays.isEmpty()) {
            push(NappletProtocolJson.encodeRelayEose(nappletSubId))
            return
        }

        close(nappletSubId)
        // liveSeq guarantees a unique client subId, so a rapid re-open of the same applet subId
        // can't collide with the subscription it's replacing.
        val sub = LiveSub("napplet-$nappletSubId-${liveSeq.incrementAndGet()}", account.client)
        liveSubs[nappletSubId] = sub

        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) = push(NappletProtocolJson.encodeRelayEvent(nappletSubId, event))

                // A subscription fans out to several relays; collapse their EOSEs into the single
                // relay.eose the SDK expects (fired when the first relay finishes its stored events).
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (sub.eoseSent.compareAndSet(false, true)) push(NappletProtocolJson.encodeRelayEose(nappletSubId))
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) = push(NappletProtocolJson.encodeRelayClosed(nappletSubId, message))
            }

        runCatching { sub.client.subscribe(sub.clientSubId, relays.associateWith { filters }, listener) }
    }

    /** Stops the live subscription for [nappletSubId], unsubscribing from the client that opened it. */
    fun close(nappletSubId: String) {
        val sub = liveSubs.remove(nappletSubId) ?: return
        runCatching { sub.client.unsubscribe(sub.clientSubId) }
    }

    /** Tears down every open subscription (service teardown). */
    fun closeAll() {
        liveSubs.values.forEach { sub -> runCatching { sub.client.unsubscribe(sub.clientSubId) } }
        liveSubs.clear()
    }
}
