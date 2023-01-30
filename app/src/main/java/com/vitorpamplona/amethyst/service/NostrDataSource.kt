package com.vitorpamplona.amethyst.service

import android.os.Handler
import android.os.Looper
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelHideMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ChannelMuteUserEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Relay
import java.util.Collections
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
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

abstract class NostrDataSource<T>(val debugName: String) {
  private val channels = Collections.synchronizedSet(mutableSetOf<Channel>())
  private val channelIds =  Collections.synchronizedSet(mutableSetOf<String>())

  private val eventCounter = mutableMapOf<String, Int>()

  fun printCounter() {
    eventCounter.forEach {
      println("AAA Count ${it.key}: ${it.value}")
    }
  }

  private val clientListener = object : Client.Listener() {
    override fun onEvent(event: Event, subscriptionId: String, relay: Relay) {
      if (subscriptionId in channelIds) {
        val key = "${debugName} ${subscriptionId} ${event.kind}"
        if (eventCounter.contains(key)) {
          eventCounter.put(key, eventCounter.get(key)!! + 1)
        } else {
          eventCounter.put(key, 1)
        }

        try {
          when (event) {
            is MetadataEvent -> LocalCache.consume(event)
            is TextNoteEvent -> LocalCache.consume(event, relay)
            is RecommendRelayEvent -> LocalCache.consume(event)
            is ContactListEvent -> LocalCache.consume(event)
            is PrivateDmEvent -> LocalCache.consume(event)
            is DeletionEvent -> LocalCache.consume(event)
            is RepostEvent -> LocalCache.consume(event)
            is ReactionEvent -> LocalCache.consume(event)
            else -> when (event.kind) {
              RepostEvent.kind -> LocalCache.consume(RepostEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ReactionEvent.kind -> LocalCache.consume(ReactionEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ReportEvent.kind -> LocalCache.consume(ReportEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))

              ChannelCreateEvent.kind -> LocalCache.consume(ChannelCreateEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ChannelMetadataEvent.kind -> LocalCache.consume(ChannelMetadataEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
              ChannelMessageEvent.kind -> LocalCache.consume(ChannelMessageEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
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

    override fun onRelayStateChange(type: Relay.Type, relay: Relay) {
      //Log.d("RELAY", "Relay ${relay.url} ${when (type) {
      //  Relay.Type.CONNECT -> "connected."
      //  Relay.Type.DISCONNECT -> "disconnected."
      //  Relay.Type.DISCONNECTING -> "disconnecting."
      //  Relay.Type.EOSE -> "sent all events it had stored."
      //}}")

      /*
      if (type == Relay.Type.EOSE) {
        // One everything is loaded, if new users are found, update filters
        resetFilters()
      }*/
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
    channels.forEach { channel ->
      if (channel.filter != null) // if it is active, close
        Client.close(channel.id)
    }
  }

  fun loadTop(): List<T> {
    val returningList = feed().take(100)

    // prepare previews
    val scope = CoroutineScope(Job() + Dispatchers.IO)
    scope.launch {
      loadPreviews(returningList)
    }

    return returningList
  }

  fun loadPreviews(list: List<T>) {
    list.forEach {
      if (it is Note) {
        UrlCachedPreviewer.preloadPreviewsFor(it)
      }
    }
  }

  fun requestNewChannel(): Channel {
    val newChannel = Channel(debugName+UUID.randomUUID().toString().substring(0,4))
    channels.add(newChannel)
    channelIds.add(newChannel.id)
    return newChannel
  }

  fun dismissChannel(channel: Channel) {
    Client.close(channel.id)
    channels.remove(channel)
    channelIds.remove(channel.id)
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
    val activeChannels = channels.filter { it.filter != null }
    // saves the current content to only update if it changes
    val currentFilter = activeChannels.associate { it.id to it.filter!!.joinToString("|") { it.toJson() }  }

    updateChannelFilters()

    // Makes sure to only send an updated filter when it actually changes.
    channels.forEach { channel ->
      val channelsNewFilter = channel.filter

      if (channel in activeChannels) {
        if (channelsNewFilter == null) {
          // was active and is not active anymore, just close.
          Client.close(channel.id)
        } else {
          // was active and is still active, check if it has changed.
          if (channelsNewFilter.joinToString("|") { it.toJson() } != currentFilter[channel.id]) {
            Client.close(channel.id)
            Client.sendFilter(channel.id, channelsNewFilter)
          } else {
            // hasn't changed, does nothing.
            Client.sendFilterOnlyIfDisconnected(channel.id, channelsNewFilter)
          }
        }
      } else {
        if (channelsNewFilter == null) {
          // was not active and is still not active, does nothing
        } else {
          // was not active and becomes active, sends the filter.
          if (channelsNewFilter.joinToString("|") { it.toJson() } != currentFilter[channel.id]) {
            Client.sendFilter(channel.id, channelsNewFilter)
          }
        }
      }
    }
  }

  abstract fun updateChannelFilters()
  abstract fun feed(): List<T>
}