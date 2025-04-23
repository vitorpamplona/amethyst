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
package com.vitorpamplona.ammolite.relays.datasources

import android.util.Log
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.RelayState
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Semantically groups Nostr filters and subscriptions in data source objects that
 * maintain the desired active filter with the relay.
 *
 * This model also allows each datasource to observe any new events from the network,
 * regardless of the subscription
 */
abstract class NostrDataSource(
    val client: NostrClient,
) {
    private var subscriptions = SubscriptionSet()
    val stats = SubscriptionStats()

    var changingFilters = AtomicBoolean()

    private var active: Boolean = false

    private val clientListener =
        object : NostrClient.Listener {
            override fun onEvent(
                event: Event,
                subscriptionId: String,
                relay: Relay,
                afterEOSE: Boolean,
            ) {
                if (subscriptions.contains(subscriptionId)) {
                    stats.add(subscriptionId, event.kind)

                    consume(event, relay)

                    if (afterEOSE) {
                        markAsEOSE(subscriptionId, relay)
                    }
                }
            }

            override fun onEOSE(
                relay: Relay,
                subscriptionId: String,
            ) {
                if (subscriptions.contains(subscriptionId)) {
                    markAsEOSE(subscriptionId, relay)
                }
            }

            override fun onRelayStateChange(
                type: RelayState,
                relay: Relay,
            ) {}

            override fun onSendResponse(
                eventId: String,
                success: Boolean,
                message: String,
                relay: Relay,
            ) {
                if (success) {
                    markAsSeenOnRelay(eventId, relay)
                }
            }

            override fun onAuth(
                relay: Relay,
                challenge: String,
            ) {
                auth(relay, challenge)
            }

            override fun onNotify(
                relay: Relay,
                description: String,
            ) {
                notify(relay, description)
            }
        }

    init {
        Log.d("${this.javaClass.simpleName}", "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("${this.javaClass.simpleName}", "Destroy, Unsubscribe")
        stop()
        client.unsubscribe(clientListener)
        bundler.cancel()
    }

    open fun start() {
        Log.d("${this.javaClass.simpleName}", "Start")
        active = true
        invalidateFilters()
    }

    @OptIn(DelicateCoroutinesApi::class)
    open fun stop() {
        active = false
        Log.d("${this.javaClass.simpleName}", "Stop")

        subscriptions.forEach { subscription ->
            client.close(subscription.id)
            subscription.reset()
        }
    }

    fun getSub(subId: String) = subscriptions.get(subId)

    fun requestNewSubscription(onEOSE: ((Long, String) -> Unit)? = null): Subscription = subscriptions.newSub(onEOSE)

    fun dismissSubscription(subId: String) {
        getSub(subId)?.let { dismissSubscription(it) }
    }

    fun dismissSubscription(subscription: Subscription) {
        client.close(subscription.id)
        subscription.reset()
        subscriptions.remove(subscription)
    }

    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.Default)

    fun invalidateFilters() {
        bundler.invalidate {
            // println("DataSource: ${this.javaClass.simpleName} InvalidateFilters")

            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            resetFiltersSuspend()
        }
    }

    suspend fun resetFiltersSuspend() {
        // only runs one at a time. Ignores the others
        if (changingFilters.compareAndSet(false, true)) {
            Log.d("${this.javaClass.simpleName}", "resetFiltersSuspend $active")
            try {
                resetFiltersSuspendInner()
            } finally {
                changingFilters.getAndSet(false)
            }
        }
    }

    private fun resetFiltersSuspendInner() {
        // saves the channels that are currently active
        val activeSubscriptions = subscriptions.actives()
        // saves the current content to only update if it changes
        val currentFilters = activeSubscriptions.associate { it.id to it.typedFilters }

        // updates all filters
        updateSubscriptions()

        // Makes sure to only send an updated filter when it actually changes.
        subscriptions.forEach { newSubscriptionFilters ->
            val currentFilters = currentFilters[newSubscriptionFilters.id]
            updateRelaysIfNeeded(newSubscriptionFilters, currentFilters)
        }
    }

    fun updateRelaysIfNeeded(
        updatedSubscription: Subscription,
        currentFilters: List<TypedFilter>?,
    ) {
        val updatedSubscriptionNewFilters = updatedSubscription.typedFilters

        val isActive = client.isActive(updatedSubscription.id)

        if (!isActive && updatedSubscriptionNewFilters != null) {
            // Filter was removed from the active list
            // but it is supposed to be there. Send again.
            if (active) {
                client.sendFilter(updatedSubscription.id, updatedSubscriptionNewFilters)
            }
        } else {
            if (currentFilters != null) {
                if (updatedSubscriptionNewFilters == null) {
                    // was active and is not active anymore, just close.
                    client.close(updatedSubscription.id)
                } else {
                    // was active and is still active, check if it has changed.
                    if (updatedSubscription.hasChangedFiltersFrom(currentFilters)) {
                        client.close(updatedSubscription.id)
                        if (active) {
                            client.sendFilter(updatedSubscription.id, updatedSubscriptionNewFilters)
                        }
                    } else {
                        // hasn't changed, does nothing.
                        // unless the relay has disconnected, then reconnect.
                        if (active) {
                            client.sendFilterOnlyIfDisconnected(updatedSubscription.id, updatedSubscriptionNewFilters)
                        }
                    }
                }
            } else {
                if (updatedSubscriptionNewFilters == null) {
                    // was not active and is still not active, does nothing
                } else {
                    // was not active and becomes active, sends the filter.
                    if (active) {
                        client.sendFilter(updatedSubscription.id, updatedSubscriptionNewFilters)
                    }
                }
            }
        }
    }

    open fun consume(
        event: Event,
        relay: Relay,
    ) = Unit

    open fun markAsSeenOnRelay(
        eventId: String,
        relay: Relay,
    ) = Unit

    open fun markAsEOSE(
        subscriptionId: String,
        relay: Relay,
    ) {
        subscriptions[subscriptionId]?.callEose(
            // in case people's clock is slighly off.
            TimeUtils.oneMinuteAgo(),
            relay.url,
        )
    }

    open fun auth(
        relay: Relay,
        challenge: String,
    ) = Unit

    open fun notify(
        relay: Relay,
        description: String,
    ) = Unit

    abstract fun updateSubscriptions()
}
