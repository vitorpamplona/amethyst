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
package com.vitorpamplona.ammolite.relays.datasources

import android.util.Log
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Semantically groups Nostr filters and subscriptions in data source objects that
 * maintain the desired active filter with the relay.
 */
class SubscriptionController(
    val client: NostrClient,
    val updateSubscriptions: () -> Unit,
) : SubscriptionControllerService {
    private val subscriptions = SubscriptionSet()
    private var active: Boolean = false
    private val changingFilters = AtomicBoolean()

    val stats = SubscriptionStats()

    private val clientListener =
        object : NostrClient.Listener {
            override fun onEvent(
                event: Event,
                subscriptionId: String,
                relay: Relay,
                arrivalTime: Long,
                afterEOSE: Boolean,
            ) {
                if (subscriptions.contains(subscriptionId)) {
                    stats.add(subscriptionId, event.kind)
                    if (afterEOSE) {
                        runAfterEOSE(subscriptionId, relay, arrivalTime)
                    }
                }
            }

            override fun onEOSE(
                relay: Relay,
                subscriptionId: String,
                arrivalTime: Long,
            ) {
                if (subscriptions.contains(subscriptionId)) {
                    runAfterEOSE(subscriptionId, relay, arrivalTime)
                }
            }
        }

    private fun runAfterEOSE(
        subscriptionId: String,
        relay: Relay,
        arrivalTime: Long,
    ) {
        subscriptions[subscriptionId]?.callEose(arrivalTime, relay.url)
    }

    init {
        Log.d("${this.javaClass.simpleName}", "Init, Subscribe")
        client.subscribe(clientListener)
    }

    override fun destroy() {
        // makes sure to run
        Log.d("${this.javaClass.simpleName}", "Destroy, Unsubscribe")
        stop()
        client.unsubscribe(clientListener)
        bundler.cancel()
    }

    override fun start() {
        Log.d("${this.javaClass.simpleName}", "Start")
        active = true
        invalidateFilters()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun stop() {
        active = false
        Log.d("${this.javaClass.simpleName}", "Stop")

        subscriptions.forEach { subscription ->
            client.close(subscription.id)
            subscription.reset()
        }
    }

    override fun printStats(tag: String) = stats.printCounter(tag)

    fun getSub(subId: String) = subscriptions.get(subId)

    fun requestNewSubscription(onEOSE: ((Long, String) -> Unit)? = null): Subscription = subscriptions.newSub(onEOSE)

    fun dismissSubscription(subId: String) = getSub(subId)?.let { dismissSubscription(it) }

    fun dismissSubscription(subscription: Subscription) {
        client.close(subscription.id)
        subscription.reset()
        subscriptions.remove(subscription)
    }

    fun isUpdatingFilters() = changingFilters.get()

    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.Default)

    override fun invalidateFilters() {
        bundler.invalidate {
            // println("DataSource: ${this.javaClass.simpleName} InvalidateFilters")

            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            resetFiltersSuspend()
        }
    }

    private fun resetFiltersSuspend() {
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
        val currentFilters = activeSubscriptions.associate { it.id to client.getSubscriptionFiltersOrNull(it.id) }

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
}
