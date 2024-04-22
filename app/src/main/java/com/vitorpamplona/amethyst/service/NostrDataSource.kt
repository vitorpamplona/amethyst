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
package com.vitorpamplona.amethyst.service

import android.util.Log
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.Subscription
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

abstract class NostrDataSource(val debugName: String) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var subscriptions = mapOf<String, Subscription>()

    data class Counter(val subscriptionId: String, val eventKind: Int, var counter: Int)

    private var eventCounter = mapOf<Int, Counter>()
    var changingFilters = AtomicBoolean()

    private var active: Boolean = false

    fun printCounter() {
        eventCounter.forEach {
            Log.d(
                "STATE DUMP ${this.javaClass.simpleName}",
                "Received Events $debugName ${it.value.subscriptionId} ${it.value.eventKind}: ${it.value.counter}",
            )
        }
    }

    fun hashCodeFields(
        str1: String,
        str2: Int,
    ): Int {
        return 31 * str1.hashCode() + str2.hashCode()
    }

    private val clientListener =
        object : Client.Listener() {
            override fun onEvent(
                event: Event,
                subscriptionId: String,
                relay: Relay,
                afterEOSE: Boolean,
            ) {
                if (subscriptions.containsKey(subscriptionId)) {
                    val key = hashCodeFields(subscriptionId, event.kind)
                    val keyValue = eventCounter[key]
                    if (keyValue != null) {
                        keyValue.counter++
                    } else {
                        eventCounter = eventCounter + Pair(key, Counter(subscriptionId, event.kind, 1))
                    }

                    // Log.d(this@NostrDataSource.javaClass.simpleName, "Relay ${relay.url}: ${event.kind}")

                    consume(event, relay)
                    if (afterEOSE) {
                        markAsEOSE(subscriptionId, relay)
                    }
                }
            }

            override fun onError(
                error: Error,
                subscriptionId: String,
                relay: Relay,
            ) {
                // if (subscriptions.containsKey(subscriptionId)) {
                // Log.e(
                //    this@NostrDataSource.javaClass.simpleName,
                //    "Relay OnError ${relay.url}: ${error.message}"
                // )
                // }
            }

            override fun onRelayStateChange(
                type: Relay.StateType,
                relay: Relay,
                subscriptionId: String?,
            ) {
                // if (subscriptions.containsKey(subscriptionId)) {
                //    Log.d(this@NostrDataSource.javaClass.simpleName, "Relay ${relay.url} ${subscriptionId}
                // ${type.name}")
                // }

                if (
                    type == Relay.StateType.EOSE &&
                    subscriptionId != null &&
                    subscriptions.containsKey(subscriptionId)
                ) {
                    markAsEOSE(subscriptionId, relay)
                }
            }

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
        Log.d(this.javaClass.simpleName, "${this.javaClass.simpleName} Subscribe")
        Client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d(this.javaClass.simpleName, "${this.javaClass.simpleName} Unsubscribe")
        stop()
        Client.unsubscribe(clientListener)
        scope.cancel()
        bundler.cancel()
    }

    open fun start() {
        println("DataSource: ${this.javaClass.simpleName} Start")
        active = true
        resetFilters()
    }

    open fun stop() {
        active = false
        println("DataSource: ${this.javaClass.simpleName} Stop")

        GlobalScope.launch(Dispatchers.IO) {
            subscriptions.values.forEach { subscription ->
                Client.close(subscription.id)
                subscription.typedFilters = null
            }
        }
    }

    open fun stopSync() {
        active = false
        println("DataSource: ${this.javaClass.simpleName} Stop")

        subscriptions.values.forEach { subscription ->
            Client.close(subscription.id)
            subscription.typedFilters = null
        }
    }

    fun requestNewChannel(onEOSE: ((Long, String) -> Unit)? = null): Subscription {
        val newSubscription = Subscription(UUID.randomUUID().toString().substring(0, 4), onEOSE)
        subscriptions = subscriptions + Pair(newSubscription.id, newSubscription)
        return newSubscription
    }

    fun dismissChannel(subscription: Subscription) {
        Client.close(subscription.id)
        subscriptions = subscriptions.minus(subscription.id)
    }

    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.IO)

    fun invalidateFilters() {
        scope.launch(Dispatchers.IO) {
            bundler.invalidate {
                // println("DataSource: ${this.javaClass.simpleName} InvalidateFilters")

                // adds the time to perform the refresh into this delay
                // holding off new updates in case of heavy refresh routines.
                resetFiltersSuspend()
            }
        }
    }

    fun resetFilters() {
        scope.launch(Dispatchers.IO) { resetFiltersSuspend() }
    }

    fun resetFiltersSuspend() {
        println("DataSource: ${this.javaClass.simpleName} resetFiltersSuspend $active")
        checkNotInMainThread()

        // saves the channels that are currently active
        val activeSubscriptions = subscriptions.values.filter { it.typedFilters != null }
        // saves the current content to only update if it changes
        val currentFilters = activeSubscriptions.associate { it.id to it.typedFilters }

        changingFilters.getAndSet(true)

        updateChannelFilters()

        // Makes sure to only send an updated filter when it actually changes.
        subscriptions.values.forEach { updatedSubscription ->
            val updatedSubscriptionNewFilters = updatedSubscription.typedFilters

            val isActive = Client.isActive(updatedSubscription.id)

            if (!isActive && updatedSubscriptionNewFilters != null) {
                // Filter was removed from the active list
                if (active) {
                    Client.sendFilter(updatedSubscription.id, updatedSubscriptionNewFilters)
                }
            } else {
                if (currentFilters.containsKey(updatedSubscription.id)) {
                    if (updatedSubscriptionNewFilters == null) {
                        // was active and is not active anymore, just close.
                        Client.close(updatedSubscription.id)
                    } else {
                        // was active and is still active, check if it has changed.
                        if (updatedSubscription.hasChangedFiltersFrom(currentFilters[updatedSubscription.id])) {
                            Client.close(updatedSubscription.id)
                            if (active) {
                                Client.sendFilter(updatedSubscription.id, updatedSubscriptionNewFilters)
                            }
                        } else {
                            // hasn't changed, does nothing.
                            if (active) {
                                Client.sendFilterOnlyIfDisconnected(
                                    updatedSubscription.id,
                                    updatedSubscriptionNewFilters,
                                )
                            }
                        }
                    }
                } else {
                    if (updatedSubscriptionNewFilters == null) {
                        // was not active and is still not active, does nothing
                    } else {
                        // was not active and becomes active, sends the filter.
                        if (updatedSubscription.hasChangedFiltersFrom(currentFilters[updatedSubscription.id])) {
                            if (active) {
                                Log.d(
                                    this@NostrDataSource.javaClass.simpleName,
                                    "Update Filter 3 ${updatedSubscription.id} ${Client.isSubscribed(clientListener)}",
                                )
                                Client.sendFilter(updatedSubscription.id, updatedSubscriptionNewFilters)
                            }
                        }
                    }
                }
            }
        }

        changingFilters.getAndSet(false)
    }

    open fun consume(
        event: Event,
        relay: Relay,
    ) {
        LocalCache.verifyAndConsume(event, relay)
    }

    open fun markAsSeenOnRelay(
        eventId: String,
        relay: Relay,
    ) {
        val note = LocalCache.getNoteIfExists(eventId)
        val noteEvent = note?.event
        if (noteEvent is AddressableEvent) {
            LocalCache.getAddressableNoteIfExists(noteEvent.address().toTag())?.addRelay(relay)
        } else {
            note?.addRelay(relay)
        }
    }

    open fun markAsEOSE(
        subscriptionId: String,
        relay: Relay,
    ) {
        subscriptions[subscriptionId]?.updateEOSE(
            // in case people's clock is slighly off.
            TimeUtils.oneMinuteAgo(),
            relay.url,
        )
    }

    abstract fun updateChannelFilters()

    open fun auth(
        relay: Relay,
        challenge: String,
    ) = Unit

    open fun notify(
        relay: Relay,
        description: String,
    ) = Unit
}
