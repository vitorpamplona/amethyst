package com.vitorpamplona.amethyst.service.relays

import com.vitorpamplona.amethyst.service.Constants
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import nostr.postr.JsonFilter
import nostr.postr.events.Event

/**
 * The Nostr Client manages multiple personae the user may switch between. Events are received and
 * published through multiple relays.
 * Events are stored with their respective persona.
 */
object Client: RelayPool.Listener {
    /**
     * Lenient mode:
     *
     * true: For maximum compatibility. If you want to play ball with sloppy counterparts, use
     *       this.
     * false: For developers who want to make protocol compliant counterparts. If your software
     *        produces events that fail to deserialize in strict mode, you should probably fix
     *        something.
     **/
    var lenient: Boolean = false
    private val listeners = Collections.synchronizedSet(HashSet<Listener>())
    internal var relays = Constants.defaultRelays
    internal val subscriptions = ConcurrentHashMap<String, MutableList<JsonFilter>>()

    fun connect(
        relays: Array<Relay> = Constants.defaultRelays
    ) {
        RelayPool.register(this)
        RelayPool.loadRelays(relays.toList())
        this.relays = relays
    }

    fun requestAndWatch(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: MutableList<JsonFilter> = mutableListOf(JsonFilter())
    ) {
        subscriptions[subscriptionId] = filters
        RelayPool.requestAndWatch()
    }

    fun sendFilter(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: MutableList<JsonFilter> = mutableListOf(JsonFilter())
    ) {
        subscriptions[subscriptionId] = filters
        RelayPool.sendFilter(subscriptionId)
    }

    fun sendFilterOnlyIfDisconnected(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: MutableList<JsonFilter> = mutableListOf(JsonFilter())
    ) {
        subscriptions[subscriptionId] = filters
        RelayPool.sendFilterOnlyIfDisconnected(subscriptionId)
    }

    fun send(signedEvent: Event) {
        RelayPool.send(signedEvent)
    }

    fun close(subscriptionId: String){
        RelayPool.close(subscriptionId)
    }

    fun disconnect() {
        RelayPool.unregister(this)
        RelayPool.disconnect()
        RelayPool.unloadRelays()
    }

    override fun onEvent(event: Event, subscriptionId: String, relay: Relay) {
        listeners.forEach { it.onEvent(event, subscriptionId, relay) }
    }

    override fun onError(error: Error, subscriptionId: String, relay: Relay) {
        listeners.forEach { it.onError(error, subscriptionId, relay) }
    }

    override fun onRelayStateChange(type: Relay.Type, relay: Relay) {
        listeners.forEach { it.onRelayStateChange(type, relay) }
    }

    fun subscribe(listener: Listener) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: Listener): Boolean {
        return listeners.remove(listener)
    }


    abstract class Listener {
        /**
         * A new message was received
         */
        open fun onEvent(event: Event, subscriptionId: String, relay: Relay) = Unit

        /**
         * A new or repeat message was received
         */
        open fun onError(error: Error, subscriptionId: String, relay: Relay) = Unit

        /**
         * Connected to or disconnected from a relay
         */
        open fun onRelayStateChange(type: Relay.Type, relay: Relay) = Unit
    }
}