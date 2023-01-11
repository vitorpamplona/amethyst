package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Relay
import java.util.Collections
import nostr.postr.events.ContactListEvent
import nostr.postr.events.DeletionEvent
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent
import nostr.postr.events.PrivateDmEvent
import nostr.postr.events.RecommendRelayEvent
import nostr.postr.events.TextNoteEvent

abstract class NostrDataSource(val debugName: String) {
  private val channels = Collections.synchronizedSet(mutableSetOf<Channel>())
  private val channelIds =  Collections.synchronizedSet(mutableSetOf<String>())

  private val clientListener = object : Client.Listener() {
    override fun onEvent(event: Event, subscriptionId: String, relay: Relay) {
      if (subscriptionId in channelIds) {
        when (event) {
          is MetadataEvent -> LocalCache.consume(event)
          is TextNoteEvent -> LocalCache.consume(event)
          is RecommendRelayEvent -> LocalCache.consume(event)
          is ContactListEvent -> LocalCache.consume(event)
          is PrivateDmEvent -> LocalCache.consume(event)
          is DeletionEvent -> LocalCache.consume(event)
          is RepostEvent -> LocalCache.consume(event)
          is ReactionEvent -> LocalCache.consume(event)
          else -> when (event.kind) {
            RepostEvent.kind -> LocalCache.consume(RepostEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
            ReactionEvent.kind -> LocalCache.consume(ReactionEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig))
          }
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

  fun loadTop(): List<Note> {
    return feed().take(100)
  }

  fun requestNewChannel(): Channel {
    val newChannel = Channel()
    channels.add(newChannel)
    channelIds.add(newChannel.id)
    return newChannel
  }

  fun dismissChannel(channel: Channel) {
    Client.close(channel.id)
    channels.remove(channel)
    channelIds.remove(channel.id)
  }

  fun resetFilters() {
    // saves the channels that are currently active
    val activeChannels = channels.filter { it.filter != null }
    // saves the current content to only update if it changes
    val currentFilter = activeChannels.associate { it.id to it.filter!!.toJson() }

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
          if (channelsNewFilter.toJson() != currentFilter[channel.id]) {
            Client.close(channel.id)
            Client.sendFilter(channel.id, mutableListOf(channelsNewFilter))
          } else {
            // hasn't changed, does nothing.
            Client.sendFilterOnlyIfDisconnected(channel.id, mutableListOf(channelsNewFilter))
          }
        }
      } else {
        if (channelsNewFilter == null) {
          // was not active and is still not active, does nothing
        } else {
          // was not active and becomes active, sends the filter.
          if (channelsNewFilter.toJson() != currentFilter[channel.id]) {
            Client.sendFilter(channel.id, mutableListOf(channelsNewFilter))
          }
        }
      }
    }
  }

  abstract fun updateChannelFilters()
  abstract fun feed(): List<Note>
}