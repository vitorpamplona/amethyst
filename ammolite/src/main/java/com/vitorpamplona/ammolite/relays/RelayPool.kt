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
package com.vitorpamplona.ammolite.relays

import androidx.compose.runtime.Immutable
import com.vitorpamplona.ammolite.service.checkNotInMainThread
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * RelayPool manages the connection to multiple Relays and lets consumers deal with simple events.
 */
class RelayPool : Relay.Listener {
    private var relays = listOf<Relay>()
    private var listeners = setOf<Listener>()

    // Backing property to avoid flow emissions from other classes
    private var lastStatus = RelayPoolStatus(0, 0)
    private val _statusFlow =
        MutableSharedFlow<RelayPoolStatus>(1, 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val statusFlow: SharedFlow<RelayPoolStatus> = _statusFlow.asSharedFlow()

    fun availableRelays(): Int = relays.size

    fun connectedRelays(): Int = relays.count { it.isConnected() }

    fun getRelay(url: String): Relay? = relays.firstOrNull { it.url == url }

    fun getRelays(url: String): List<Relay> = relays.filter { it.url == url }

    fun getAll() = relays

    fun runCreatingIfNeeded(
        relay: Relay,
        timeout: Long = 60000,
        onDone: (() -> Unit)? = null,
        whenConnected: (Relay) -> Unit,
    ) {
        synchronized(this) {
            val matching = getRelays(relay.url)
            if (matching.isNotEmpty()) {
                matching.forEach { whenConnected(it) }
            } else {
                addRelay(relay)

                relay.connectAndRun {
                    relay.renewFilters()
                    relay.sendOutbox()

                    whenConnected(relay)

                    GlobalScope.launch(Dispatchers.IO) {
                        delay(timeout) // waits for a reply
                        relay.disconnect()
                        removeRelay(relay)

                        if (onDone != null) {
                            onDone()
                        }
                    }
                }
            }
        }
    }

    fun loadRelays(relayList: List<Relay>) {
        check(relayList.isNotEmpty()) { "Relay list should never be empty" }
        relayList.forEach { addRelayInner(it) }
        updateStatus()
    }

    fun unloadRelays() {
        relays.forEach { it.unregister(this) }
        relays = listOf()
    }

    fun requestAndWatch() {
        checkNotInMainThread()

        relays.forEach { it.connect() }
    }

    fun sendFilter(
        subscriptionId: String,
        filters: List<TypedFilter>,
    ) {
        relays.forEach { relay ->
            relay.sendFilter(subscriptionId, filters)
        }
    }

    fun connectAndSendFiltersIfDisconnected() {
        relays.forEach { it.connectAndSendFiltersIfDisconnected() }
    }

    fun sendToSelectedRelays(
        list: List<RelaySetupInfo>,
        signedEvent: EventInterface,
    ) {
        list.forEach { relay -> relays.filter { it.url == relay.url }.forEach { it.sendOverride(signedEvent) } }
    }

    fun send(signedEvent: EventInterface) {
        relays.forEach { it.send(signedEvent) }
    }

    fun sendOverride(signedEvent: EventInterface) {
        relays.forEach { it.sendOverride(signedEvent) }
    }

    fun close(subscriptionId: String) {
        relays.forEach { it.close(subscriptionId) }
    }

    fun disconnect() {
        relays.forEach { it.disconnect() }
    }

    fun addRelay(relay: Relay) {
        addRelayInner(relay)
        updateStatus()
    }

    private fun addRelayInner(relay: Relay) {
        relay.register(this)
        relays += relay
    }

    fun removeRelay(relay: Relay) {
        relay.unregister(this)
        relays = relays.minus(relay)
        updateStatus()
    }

    fun register(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun unregister(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    interface Listener {
        fun onEvent(
            event: Event,
            subscriptionId: String,
            relay: Relay,
            afterEOSE: Boolean,
        )

        fun onRelayStateChange(
            type: Relay.StateType,
            relay: Relay,
            channel: String?,
        )

        fun onSendResponse(
            eventId: String,
            success: Boolean,
            message: String,
            relay: Relay,
        )

        fun onAuth(
            relay: Relay,
            challenge: String,
        )

        fun onNotify(
            relay: Relay,
            description: String,
        )

        fun onSend(
            relay: Relay,
            msg: String,
            success: Boolean,
        )

        fun onBeforeSend(
            relay: Relay,
            event: EventInterface,
        )

        fun onError(
            error: Error,
            subscriptionId: String,
            relay: Relay,
        )
    }

    override fun onEvent(
        relay: Relay,
        subscriptionId: String,
        event: Event,
        afterEOSE: Boolean,
    ) {
        listeners.forEach { it.onEvent(event, subscriptionId, relay, afterEOSE) }
    }

    override fun onError(
        relay: Relay,
        subscriptionId: String,
        error: Error,
    ) {
        listeners.forEach { it.onError(error, subscriptionId, relay) }
        updateStatus()
    }

    override fun onRelayStateChange(
        relay: Relay,
        type: Relay.StateType,
        channel: String?,
    ) {
        listeners.forEach { it.onRelayStateChange(type, relay, channel) }
        if (type != Relay.StateType.EOSE) {
            updateStatus()
        }
    }

    override fun onSendResponse(
        relay: Relay,
        eventId: String,
        success: Boolean,
        message: String,
    ) {
        listeners.forEach { it.onSendResponse(eventId, success, message, relay) }
    }

    override fun onAuth(
        relay: Relay,
        challenge: String,
    ) {
        listeners.forEach { it.onAuth(relay, challenge) }
    }

    override fun onNotify(
        relay: Relay,
        description: String,
    ) {
        listeners.forEach { it.onNotify(relay, description) }
    }

    override fun onSend(
        relay: Relay,
        msg: String,
        success: Boolean,
    ) {
        listeners.forEach { it.onSend(relay, msg, success) }
    }

    override fun onBeforeSend(
        relay: Relay,
        event: EventInterface,
    ) {
        listeners.forEach { it.onBeforeSend(relay, event) }
    }

    private fun updateStatus() {
        val connected = connectedRelays()
        val available = availableRelays()
        if (lastStatus.connected != connected || lastStatus.available != available) {
            lastStatus = RelayPoolStatus(connected, available)
            _statusFlow.tryEmit(lastStatus)
        }
    }
}

@Immutable
data class RelayPoolStatus(
    val connected: Int,
    val available: Int,
    val isConnected: Boolean = connected > 0,
)
