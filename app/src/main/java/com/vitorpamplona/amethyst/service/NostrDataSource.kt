package com.vitorpamplona.amethyst.service

import android.util.Log
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.model.BadgeAwardEvent
import com.vitorpamplona.amethyst.service.model.BadgeDefinitionEvent
import com.vitorpamplona.amethyst.service.model.BadgeProfilesEvent
import com.vitorpamplona.amethyst.service.model.BookmarkListEvent
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelHideMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ChannelMuteUserEvent
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.DeletionEvent
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.MetadataEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RecommendRelayEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.Subscription
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import kotlin.Error

abstract class NostrDataSource(val debugName: String) {
    private var subscriptions = mapOf<String, Subscription>()
    data class Counter(var counter: Int)

    private var eventCounter = mapOf<String, Counter>()

    fun printCounter() {
        eventCounter.forEach {
            Log.d("STATE DUMP", "Received Events ${it.key}: ${it.value.counter}")
        }
    }

    private val clientListener = object : Client.Listener() {
        override fun onEvent(event: Event, subscriptionId: String, relay: Relay) {
            if (subscriptionId in subscriptions.keys) {
                if (!event.hasValidSignature()) return

                val key = "$debugName $subscriptionId ${event.kind}"
                val keyValue = eventCounter.get(key)
                if (keyValue != null) {
                    keyValue.counter++
                } else {
                    eventCounter = eventCounter + Pair(key, Counter(1))
                }

                try {
                    when (event) {
                        is BadgeAwardEvent -> LocalCache.consume(event)
                        is BadgeDefinitionEvent -> LocalCache.consume(event)
                        is BadgeProfilesEvent -> LocalCache.consume(event)
                        is BookmarkListEvent -> LocalCache.consume(event)
                        is ChannelCreateEvent -> LocalCache.consume(event)
                        is ChannelHideMessageEvent -> LocalCache.consume(event)
                        is ChannelMessageEvent -> LocalCache.consume(event, relay)
                        is ChannelMetadataEvent -> LocalCache.consume(event)
                        is ChannelMuteUserEvent -> LocalCache.consume(event)
                        is ContactListEvent -> LocalCache.consume(event)
                        is DeletionEvent -> LocalCache.consume(event)

                        is FileHeaderEvent -> LocalCache.consume(event)
                        is LnZapEvent -> {
                            event.zapRequest?.let { onEvent(it, subscriptionId, relay) }
                            LocalCache.consume(event)
                        }
                        is LnZapRequestEvent -> LocalCache.consume(event)
                        is LnZapPaymentRequestEvent -> LocalCache.consume(event)
                        is LnZapPaymentResponseEvent -> LocalCache.consume(event)
                        is LongTextNoteEvent -> LocalCache.consume(event, relay)
                        is MetadataEvent -> LocalCache.consume(event)
                        is PrivateDmEvent -> LocalCache.consume(event, relay)
                        is ReactionEvent -> LocalCache.consume(event)
                        is RecommendRelayEvent -> LocalCache.consume(event)
                        is ReportEvent -> LocalCache.consume(event, relay)
                        is RepostEvent -> {
                            event.containedPost()?.let { onEvent(it, subscriptionId, relay) }
                            LocalCache.consume(event)
                        }
                        is TextNoteEvent -> LocalCache.consume(event, relay)
                        is PollNoteEvent -> LocalCache.consume(event, relay)
                        else -> {
                            Log.w("Event Not Supported", event.toJson())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun onError(error: Error, subscriptionId: String, relay: Relay) {
            // Log.e("ERROR", "Relay ${relay.url}: ${error.message}")
        }

        override fun onRelayStateChange(type: Relay.Type, relay: Relay, channel: String?) {
            // Log.d("RELAY", "Relay ${relay.url} ${when (type) {
            //  Relay.Type.CONNECT -> "connected."
            //  Relay.Type.DISCONNECT -> "disconnected."
            //  Relay.Type.DISCONNECTING -> "disconnecting."
            //  Relay.Type.EOSE -> "sent all events it had stored."
            // }}")

            if (type == Relay.Type.EOSE && channel != null) {
                // updates a per subscripton since date
                subscriptions[channel]?.updateEOSE(Date().time / 1000, relay.url)
            }
        }

        override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {
        }
    }

    init {
        Client.subscribe(clientListener)
    }

    fun destroy() {
        stop()
        Client.unsubscribe(clientListener)
    }

    open fun start() {
        println("DataSource: ${this.javaClass.simpleName} Start")
        resetFilters()
    }

    open fun stop() {
        println("DataSource: ${this.javaClass.simpleName} Stop")
        subscriptions.values.forEach { channel ->
            Client.close(channel.id)
            channel.typedFilters = null
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
    private val bundler = BundledUpdate(250, Dispatchers.IO) {
        // println("DataSource: ${this.javaClass.simpleName} InvalidateFilters")

        // adds the time to perform the refresh into this delay
        // holding off new updates in case of heavy refresh routines.
        resetFiltersSuspend()
    }

    fun invalidateFilters() {
        bundler.invalidate()
    }

    fun resetFilters() {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            resetFiltersSuspend()
        }
    }

    fun resetFiltersSuspend() {
        // saves the channels that are currently active
        val activeSubscriptions = subscriptions.values.filter { it.typedFilters != null }
        // saves the current content to only update if it changes
        val currentFilters = activeSubscriptions.associate { it.id to it.toJson() }

        updateChannelFilters()

        // Makes sure to only send an updated filter when it actually changes.
        subscriptions.values.forEach { updatedSubscription ->
            val updatedSubscriotionNewFilters = updatedSubscription.typedFilters

            if (updatedSubscription.id in currentFilters.keys) {
                if (updatedSubscriotionNewFilters == null) {
                    // was active and is not active anymore, just close.
                    Client.close(updatedSubscription.id)
                } else {
                    // was active and is still active, check if it has changed.
                    if (updatedSubscription.toJson() != currentFilters[updatedSubscription.id]) {
                        Client.close(updatedSubscription.id)
                        Client.sendFilter(updatedSubscription.id, updatedSubscriotionNewFilters)
                    } else {
                        // hasn't changed, does nothing.
                        Client.sendFilterOnlyIfDisconnected(updatedSubscription.id, updatedSubscriotionNewFilters)
                    }
                }
            } else {
                if (updatedSubscriotionNewFilters == null) {
                    // was not active and is still not active, does nothing
                } else {
                    // was not active and becomes active, sends the filter.
                    if (updatedSubscription.toJson() != currentFilters[updatedSubscription.id]) {
                        Client.sendFilter(updatedSubscription.id, updatedSubscriotionNewFilters)
                    }
                }
            }
        }
    }

    abstract fun updateChannelFilters()
}
