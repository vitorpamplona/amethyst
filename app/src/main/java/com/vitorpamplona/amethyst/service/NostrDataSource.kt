package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelHideMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ChannelMuteUserEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.Subscription
import com.vitorpamplona.amethyst.service.relays.hasValidSignature
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nostr.postr.events.ContactListEvent
import nostr.postr.events.DeletionEvent
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent
import nostr.postr.events.PrivateDmEvent
import nostr.postr.events.RecommendRelayEvent
import nostr.postr.events.TextNoteEvent

abstract class NostrDataSource(val debugName: String) {
  private var subscriptions = mapOf<String, Subscription>()
  data class Counter(var counter:Int)

  private var eventCounter = mapOf<String, Counter>()

  fun printCounter() {
    eventCounter.forEach {
      println("AAA Count ${it.key}: ${it.value.counter}")
    }
  }

  private val clientListener = object : Client.Listener() {
    override fun onEvent(event: Event, subscriptionId: String, relay: Relay) {
      if (subscriptionId in subscriptions.keys) {
        if (!event.hasValidSignature()) return

        val key = "${debugName} ${subscriptionId} ${event.kind}"
        val keyValue = eventCounter.get(key)
        if (keyValue != null) {
          keyValue.counter++
        } else {
          eventCounter = eventCounter + Pair(key, Counter(1))
        }

        try {
          when (event) {
            is MetadataEvent -> LocalCache.consume(event)
            is TextNoteEvent -> LocalCache.consume(event, relay)
            is RecommendRelayEvent -> LocalCache.consume(event)
            is ContactListEvent -> LocalCache.consume(event)
            is PrivateDmEvent -> LocalCache.consume(event, relay)
            is DeletionEvent -> LocalCache.consume(event)
            else -> when (event.kind) {
              RepostEvent.kind -> {
                val repostEvent = RepostEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig)

                repostEvent.containedPost?.let { onEvent(it, subscriptionId, relay) }
                LocalCache.consume(repostEvent)
              }
              ReactionEvent.kind -> LocalCache.consume(ReactionEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ReportEvent.kind -> LocalCache.consume(ReportEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig), relay)

              LnZapEvent.kind -> {
                val zapEvent = LnZapEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig)

                zapEvent.containedPost?.let { onEvent(it, subscriptionId, relay) }
                LocalCache.consume(zapEvent)
              }
              LnZapRequestEvent.kind -> LocalCache.consume(LnZapRequestEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))

              ChannelCreateEvent.kind -> LocalCache.consume(ChannelCreateEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ChannelMetadataEvent.kind -> LocalCache.consume(ChannelMetadataEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ChannelMessageEvent.kind -> LocalCache.consume(ChannelMessageEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig), relay)
              ChannelHideMessageEvent.kind -> LocalCache.consume(ChannelHideMessageEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ChannelMuteUserEvent.kind -> LocalCache.consume(ChannelMuteUserEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }

      }
    }

    override fun onError(error: Error, subscriptionId: String, relay: Relay) {
      //Log.e("ERROR", "Relay ${relay.url}: ${error.message}")
    }

    override fun onRelayStateChange(type: Relay.Type, relay: Relay, channel: String?) {
      //Log.d("RELAY", "Relay ${relay.url} ${when (type) {
      //  Relay.Type.CONNECT -> "connected."
      //  Relay.Type.DISCONNECT -> "disconnected."
      //  Relay.Type.DISCONNECTING -> "disconnecting."
      //  Relay.Type.EOSE -> "sent all events it had stored."
      //}}")

      if (type == Relay.Type.EOSE && channel != null) {
        // updates a per subscripton since date
        subscriptions[channel]?.updateEOSE(Date().time / 1000)
      }
    }

    override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {

    }
  }

  init {
    Client.subscribe(clientListener)
  }

  open fun start() {
    resetFilters()
  }

  open fun stop() {
    subscriptions.values.forEach { channel ->
      Client.close(channel.id)
      channel.typedFilters = null
    }
  }

  fun requestNewChannel(onEOSE: ((Long) -> Unit)? = null): Subscription {
    val newSubscription = Subscription(UUID.randomUUID().toString().substring(0,4), onEOSE)
    subscriptions = subscriptions + Pair(newSubscription.id, newSubscription)
    return newSubscription
  }

  fun dismissChannel(subscription: Subscription) {
    Client.close(subscription.id)
    subscriptions = subscriptions.minus(subscription.id)
  }

  var handlerWaiting = false
  @Synchronized
  fun invalidateFilters() {
    if (handlerWaiting) return

    handlerWaiting = true
    val scope = CoroutineScope(Job() + Dispatchers.IO)
    scope.launch {
      delay(200)
      resetFiltersSuspend()
      handlerWaiting = false
    }
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
    val currentFilters = activeSubscriptions.associate { it.id to it.toJson()  }

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