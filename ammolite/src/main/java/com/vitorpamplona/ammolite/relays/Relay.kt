/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.ammolite.relays

import com.vitorpamplona.ammolite.relays.relays.RelayState
import com.vitorpamplona.ammolite.relays.relays.SimpleRelay
import com.vitorpamplona.ammolite.relays.relays.Subscription
import com.vitorpamplona.ammolite.relays.relays.SubscriptionCollection
import com.vitorpamplona.ammolite.relays.relays.sockets.WebsocketBuilderFactory
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relays.Filter
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent

enum class FeedType {
    FOLLOWS,
    PUBLIC_CHATS,
    PRIVATE_DMS,
    GLOBAL,
    SEARCH,
    WALLET_CONNECT,
}

val ALL_FEED_TYPES =
    setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS, FeedType.GLOBAL, FeedType.SEARCH)

val COMMON_FEED_TYPES =
    setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS, FeedType.GLOBAL)

val EVENT_FINDER_TYPES =
    setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.GLOBAL)

class RelaySubFilter(
    val url: String,
    val activeTypes: Set<FeedType>,
    val subs: SubscriptionManager,
) : SubscriptionCollection {
    override fun allSubscriptions(): List<Subscription> =
        subs.allSubscriptions().mapNotNull { filter ->
            val filters = filter(filter.value)
            if (filters.isNotEmpty()) {
                Subscription(filter.key, filters)
            } else {
                null
            }
        }

    fun filter(filters: List<TypedFilter>): List<Filter> =
        filters.mapNotNull { filter ->
            if (activeTypes.any { it in filter.types } && filter.filter.isValidFor(url)) {
                filter.filter.toRelay(url)
            } else {
                null
            }
        }
}

class Relay(
    val url: String,
    val read: Boolean = true,
    val write: Boolean = true,
    val forceProxy: Boolean = false,
    val activeTypes: Set<FeedType>,
    socketBuilderFactory: WebsocketBuilderFactory,
    subs: SubscriptionManager,
) : SimpleRelay.Listener {
    private var listeners = setOf<Listener>()

    val relaySubFilter = RelaySubFilter(url, activeTypes, subs)
    val inner =
        SimpleRelay(url, socketBuilderFactory.build(forceProxy), relaySubFilter, RelayStats.get(url)).apply {
            register(this@Relay)
        }

    val brief = RelayBriefInfoCache.get(url)

    fun register(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun unregister(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    fun isConnected() = inner.isConnected()

    fun connect() = inner.connect()

    fun connectAndRun(onConnected: () -> Unit) {
        // BRB is crashing OkHttp Deflater object :(
        if (url.contains("brb.io")) return

        inner.connectAndRun(onConnected)
    }

    fun sendOutbox() = inner.sendOutbox()

    fun disconnect() = inner.disconnect()

    fun sendFilter(
        requestId: String,
        filters: List<TypedFilter>,
    ) {
        if (read) {
            inner.sendFilter(requestId, relaySubFilter.filter(filters))
        }
    }

    fun connectAndSendFiltersIfDisconnected() = inner.connectAndSendFiltersIfDisconnected()

    fun renewFilters() = inner.renewFilters()

    fun sendOverride(signedEvent: Event) = inner.send(signedEvent)

    fun send(signedEvent: Event) {
        if (signedEvent is RelayAuthEvent || write) {
            inner.send(signedEvent)
        }
    }

    fun close(subscriptionId: String) = inner.close(subscriptionId)

    fun isSameRelayConfig(other: RelaySetupInfoToConnect): Boolean =
        url == other.url &&
            forceProxy == other.forceProxy &&
            write == other.write &&
            read == other.read &&
            activeTypes == other.feedTypes

    override fun onEvent(
        relay: SimpleRelay,
        subscriptionId: String,
        event: Event,
        afterEOSE: Boolean,
    ) = listeners.forEach { it.onEvent(this, subscriptionId, event, afterEOSE) }

    override fun onError(
        relay: SimpleRelay,
        subscriptionId: String,
        error: Error,
    ) = listeners.forEach { it.onError(this, subscriptionId, error) }

    override fun onEOSE(
        relay: SimpleRelay,
        subscriptionId: String,
    ) = listeners.forEach { it.onEOSE(this, subscriptionId) }

    override fun onRelayStateChange(
        relay: SimpleRelay,
        type: RelayState,
    ) = listeners.forEach { it.onRelayStateChange(this, type) }

    override fun onSendResponse(
        relay: SimpleRelay,
        eventId: String,
        success: Boolean,
        message: String,
    ) = listeners.forEach { it.onSendResponse(this, eventId, success, message) }

    override fun onAuth(
        relay: SimpleRelay,
        challenge: String,
    ) = listeners.forEach { it.onAuth(this, challenge) }

    override fun onNotify(
        relay: SimpleRelay,
        description: String,
    ) = listeners.forEach { it.onNotify(this, description) }

    override fun onSend(
        relay: SimpleRelay,
        msg: String,
        success: Boolean,
    ) = listeners.forEach { it.onSend(this, msg, success) }

    override fun onBeforeSend(
        relay: SimpleRelay,
        event: Event,
    ) = listeners.forEach { it.onBeforeSend(this, event) }

    interface Listener {
        fun onEvent(
            relay: Relay,
            subscriptionId: String,
            event: Event,
            afterEOSE: Boolean,
        )

        fun onEOSE(
            relay: Relay,
            subscriptionId: String,
        )

        fun onError(
            relay: Relay,
            subscriptionId: String,
            error: Error,
        )

        fun onSendResponse(
            relay: Relay,
            eventId: String,
            success: Boolean,
            message: String,
        )

        fun onAuth(
            relay: Relay,
            challenge: String,
        )

        fun onRelayStateChange(
            relay: Relay,
            type: RelayState,
        )

        /** Relay sent a notification */
        fun onNotify(
            relay: Relay,
            description: String,
        )

        fun onBeforeSend(
            relay: Relay,
            event: Event,
        )

        fun onSend(
            relay: Relay,
            msg: String,
            success: Boolean,
        )
    }
}
