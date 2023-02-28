package com.vitorpamplona.amethyst.service.relays

import android.view.SearchEvent
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nostr.postr.events.Event
import nostr.postr.events.TextNoteEvent

/**
 * RelayPool manages the connection to multiple Relays and lets consumers deal with simple events.
 */
object RelayPool: Relay.Listener {

    val scope = CoroutineScope(Job() + Dispatchers.IO)

    private var relays = listOf<Relay>()
    private var listeners = setOf<Listener>()
    private var searchRelays = listOf<Relay>()

    fun availableRelays(): Int {
        return relays.size
    }

    fun connectedRelays(): Int {
        return relays.filter { it.isConnected() }.size
    }

    fun getRelay(url: String): Relay? {
        return relays.firstOrNull() { it.url == url }
    }

    fun loadRelays(relayList: List<Relay>){
        if (!relayList.isNullOrEmpty()) {
            relayList.forEach { addRelay(it) }
        } else {
            Constants.convertDefaultRelays().forEach { addRelay(it) }
        }
    }

    fun loadSearchRelays(relayList: List<Relay>) {
        searchRelays = relayList
        relayList.forEach { addSearchRelay(it) }
    }

    fun unloadRelays() {
        relays.forEach { it.unregister(this) }
        relays = listOf()
    }

    fun requestAndWatch() {
        relays.forEach { it.requestAndWatch() }
    }

    fun sendFilter(subscriptionId: String) {
        relays.forEach { it.sendFilter(subscriptionId) }
    }

    fun sendFilterOnlyIfDisconnected() {
        relays.forEach { it.sendFilterOnlyIfDisconnected() }
    }

    fun sendSearchRequest(searchText: String, requestId: String) {
        searchRelays.forEach {
            it.requestAndWatch()
            it.sendSearchRequest(searchText, requestId)
        }
    }

    fun send(signedEvent: Event) {
        relays.forEach { it.send(signedEvent) }
    }

    fun close(subscriptionId: String){
        relays.forEach { it.close(subscriptionId) }
    }

    fun disconnect() {
        relays.forEach { it.disconnect() }
    }

    fun addRelay(relay: Relay) {
        relay.register(this)
        relays += relay
    }

    fun addSearchRelay(relay: Relay) {
        relay.register(this)
    }

    fun removeRelay(relay: Relay) {
        relay.unregister(this)
        relays = relays.minus(relay)
    }

    fun register(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun unregister(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    interface Listener {
        fun onSearchEvent(event: TextNoteEvent, relay: Relay)
        fun onEvent(event: Event, subscriptionId: String, relay: Relay)

        fun onError(error: Error, subscriptionId: String, relay: Relay)

        fun onRelayStateChange(type: Relay.Type, relay: Relay, channel: String?)

        fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay)
    }

    @Synchronized
    override fun onSearchEvent(event: TextNoteEvent, relay: Relay) {
        listeners.forEach { it.onSearchEvent(event, relay) }
    }

    @Synchronized
    override fun onEvent(relay: Relay, subscriptionId: String, event: Event) {
        listeners.forEach { it.onEvent(event, subscriptionId, relay) }
    }

    override fun onError(relay: Relay, subscriptionId: String, error: Error) {
        listeners.forEach { it.onError(error, subscriptionId, relay) }
        refreshObservers()
    }

    override fun onRelayStateChange(relay: Relay, type: Relay.Type, channel: String?) {
        listeners.forEach { it.onRelayStateChange(type, relay, channel) }
        refreshObservers()
    }

    override fun onSendResponse(relay: Relay, eventId: String, success: Boolean, message: String) {
        listeners.forEach { it.onSendResponse(eventId, success, message, relay) }
    }

    // Observers line up here.
    val live: RelayPoolLiveData = RelayPoolLiveData(this)

    private fun refreshObservers() {
        scope.launch {
            live.refresh()
        }
    }
}

class RelayPoolLiveData(val relays: RelayPool): LiveData<RelayPoolState>(RelayPoolState(relays)) {
    fun refresh() {
        postValue(RelayPoolState(relays))
    }
}

class RelayPoolState(val relays: RelayPool)