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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolEventOutboxRepository
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolSubscriptionRepository
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayPool
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * The Nostr Client manages a relay pool, keeps active subscriptions and manages sending of events.
 */
class NostrClient(
    private val websocketBuilder: WebsocketBuilder,
    private val scope: CoroutineScope,
) : IRelayClientListener {
    private val relayPool: RelayPool = RelayPool(this, ::buildRelay)
    private val activeSubscriptions: PoolSubscriptionRepository = PoolSubscriptionRepository()
    private val eventOutbox: PoolEventOutboxRepository = PoolEventOutboxRepository()

    private var listeners = setOf<IRelayClientListener>()

    /**
     * Whatches for any changes in the relay list from subscriptions or outbox
     * and updates the relayPool as needed.
     */
    private val allRelays =
        combine(
            activeSubscriptions.relays,
            eventOutbox.relays,
        ) { subs, outbox ->
            subs + outbox
        }.onStart {
            activeSubscriptions.relays.value + eventOutbox.relays.value
        }.onEach {
            relayPool.updatePool(it)
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Companion.Eagerly,
                activeSubscriptions.relays.value + eventOutbox.relays.value,
            )

    fun buildRelay(relay: NormalizedRelayUrl): IRelayClient =
        BasicRelayClient(
            url = relay,
            socketBuilder = websocketBuilder,
            listener = relayPool,
            stats = RelayStats.get(relay),
        ) { liveRelay ->
            activeSubscriptions.forEachSub(relay, liveRelay::sendRequest)
            eventOutbox.forEachUnsentEvent(relay, liveRelay::send)
        }

    fun allAvailableRelays() = relayPool.getAll()

    // Reconnects all relays that may have disconnected
    fun connect() = relayPool.connect()

    // Reconnects all relays that may have disconnected
    fun disconnect() = relayPool.disconnect()

    @Synchronized
    fun reconnect(onlyIfChanged: Boolean = false) {
        if (onlyIfChanged) {
            relayPool.getAllNeedsToReconnect().forEach {
                it.disconnect()
            }
            relayPool.connect()
        } else {
            relayPool.disconnect()
            relayPool.connect()
        }
    }

    fun sendFilter(
        subscriptionId: String = newSubId(),
        filters: List<RelayBasedFilter> = listOf(),
    ) {
        activeSubscriptions.addOrUpdate(subscriptionId, filters)
        relayPool.sendRequest(subscriptionId, filters)
    }

    fun sendFilterOnlyIfDisconnected(
        subscriptionId: String = newSubId(),
        filters: List<RelayBasedFilter> = listOf(),
    ) {
        activeSubscriptions.addOrUpdate(subscriptionId, filters)
        relayPool.connectIfDisconnected()
    }

    fun sendIfExists(
        signedEvent: Event,
        connectedRelay: NormalizedRelayUrl,
    ) {
        relayPool.getRelay(connectedRelay)?.send(signedEvent)
    }

    fun send(
        signedEvent: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        eventOutbox.markAsSending(signedEvent, relayList)
        relayPool.send(signedEvent, relayList)
    }

    fun close(subscriptionId: String) {
        relayPool.close(subscriptionId)
        activeSubscriptions.remove(subscriptionId)
    }

    fun isActive(subscriptionId: String): Boolean = activeSubscriptions.isActive(subscriptionId)

    override fun onEvent(
        relay: IRelayClient,
        subId: String,
        event: Event,
        arrivalTime: Long,
        afterEOSE: Boolean,
    ) {
        listeners.forEach { it.onEvent(relay, subId, event, arrivalTime, afterEOSE) }
    }

    override fun onEOSE(
        relay: IRelayClient,
        subId: String,
        arrivalTime: Long,
    ) {
        listeners.forEach { it.onEOSE(relay, subId, arrivalTime) }
    }

    override fun onRelayStateChange(
        relay: IRelayClient,
        type: RelayState,
    ) {
        listeners.forEach { it.onRelayStateChange(relay, type) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onBeforeSend(
        relay: IRelayClient,
        event: Event,
    ) {
        eventOutbox.newTry(event.id, relay.url)
        listeners.forEach { it.onBeforeSend(relay, event) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onSendResponse(
        relay: IRelayClient,
        eventId: String,
        success: Boolean,
        message: String,
    ) {
        eventOutbox.newResponse(eventId, relay.url, success, message)
        listeners.forEach { it.onSendResponse(relay, eventId, success, message) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onAuth(
        relay: IRelayClient,
        challenge: String,
    ) {
        listeners.forEach { it.onAuth(relay, challenge) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onNotify(
        relay: IRelayClient,
        description: String,
    ) {
        listeners.forEach { it.onNotify(relay, description) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onSend(
        relay: IRelayClient,
        msg: String,
        success: Boolean,
    ) {
        listeners.forEach { it.onSend(relay, msg, success) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onError(
        relay: IRelayClient,
        subId: String,
        error: Error,
    ) {
        listeners.forEach { it.onError(relay, subId, error) }
    }

    fun subscribe(listener: IRelayClientListener) {
        listeners = listeners.plus(listener)
    }

    fun isSubscribed(listener: IRelayClientListener): Boolean = listeners.contains(listener)

    fun unsubscribe(listener: IRelayClientListener) {
        listeners = listeners.minus(listener)
    }

    fun getSubscriptionFiltersOrNull(subId: String): List<RelayBasedFilter>? = activeSubscriptions.getSubscriptionFiltersOrNull(subId)

    fun relayStatusFlow() = relayPool.statusFlow
}
