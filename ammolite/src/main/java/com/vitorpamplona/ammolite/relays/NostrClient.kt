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

import android.system.Os.close
import android.util.Log
import androidx.core.app.PendingIntentCompat.send
import com.vitorpamplona.ammolite.service.checkNotInMainThread
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilderFactory
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Nostr Client manages multiple personae the user may switch between. Events are received and
 * published through multiple relays. Events are stored with their respective persona.
 */
class NostrClient(
    private val websocketBuilder: WebsocketBuilderFactory,
) : RelayPool.Listener {
    private val relayPool: RelayPool = RelayPool()
    private val subscriptions: MutableSubscriptionManager = MutableSubscriptionManager()

    private var listeners = setOf<Listener>()
    private var relays = emptyArray<Relay>()

    fun buildRelay(it: RelaySetupInfoToConnect): Relay = Relay(it.url, it.read, it.write, it.forceProxy, it.feedTypes, websocketBuilder, subscriptions)

    fun getRelay(url: String): Relay? = relayPool.getRelay(url)

    fun reconnect() {
        // Reconnects all relays that may have disconnected
        relayPool.requestAndWatch()
    }

    @Synchronized
    fun reconnect(
        relays: Array<RelaySetupInfoToConnect>?,
        onlyIfChanged: Boolean = false,
    ) {
        Log.d("Relay", "Relay Pool Reconnecting to ${relays?.size} relays: \n${relays?.joinToString("\n") { it.url + " " + it.forceProxy + " " + it.read + " " + it.write + " " + it.feedTypes.joinToString(",") { it.name } }}")
        checkNotInMainThread()

        if (onlyIfChanged) {
            if (!isSameRelaySetConfig(relays)) {
                if (this.relays.isNotEmpty()) {
                    relayPool.disconnect()
                    relayPool.unregister(this)
                    relayPool.unloadRelays()
                }

                if (relays != null) {
                    val newRelays = relays.map(::buildRelay)
                    relayPool.register(this)
                    relayPool.loadRelays(newRelays)
                    relayPool.requestAndWatch()
                    this.relays = newRelays.toTypedArray()
                }
            } else {
                // Reconnects all relays that may have disconnected
                relayPool.requestAndWatch()
            }
        } else {
            if (this.relays.isNotEmpty()) {
                relayPool.disconnect()
                relayPool.unregister(this)
                relayPool.unloadRelays()
            }

            if (relays != null) {
                val newRelays = relays.map(::buildRelay)
                relayPool.register(this)
                relayPool.loadRelays(newRelays)
                relayPool.requestAndWatch()
                this.relays = newRelays.toTypedArray()
            }
        }
    }

    fun isSameRelaySetConfig(newRelayConfig: Array<RelaySetupInfoToConnect>?): Boolean {
        if (relays.size != newRelayConfig?.size) return false

        relays.forEach { oldRelayInfo ->
            val newRelayInfo = newRelayConfig.find { it.url == oldRelayInfo.url } ?: return false

            if (!oldRelayInfo.isSameRelayConfig(newRelayInfo)) return false
        }

        return true
    }

    fun sendFilter(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: List<TypedFilter> = listOf(),
    ) {
        checkNotInMainThread()

        subscriptions.add(subscriptionId, filters)
        relayPool.sendFilter(subscriptionId, filters)
    }

    fun sendFilterAndStopOnFirstResponse(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: List<TypedFilter> = listOf(),
        onResponse: (Event) -> Unit,
    ) {
        checkNotInMainThread()

        subscribe(
            object : Listener {
                override fun onEvent(
                    event: Event,
                    subId: String,
                    relay: Relay,
                    afterEOSE: Boolean,
                ) {
                    if (subId == subscriptionId) {
                        onResponse(event)
                        unsubscribe(this)
                        close(subscriptionId)
                    }
                }
            },
        )

        subscriptions.add(subscriptionId, filters)
        relayPool.sendFilter(subscriptionId, filters)
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun sendAndWaitForResponse(
        signedEvent: Event,
        relay: String? = null,
        forceProxy: Boolean = false,
        feedTypes: Set<FeedType>? = null,
        relayList: List<RelaySetupInfo>? = null,
        onDone: (() -> Unit)? = null,
        additionalListener: Listener? = null,
        timeoutInSeconds: Long = 15,
    ): Boolean {
        checkNotInMainThread()

        val size = if (relay != null) 1 else relayList?.size ?: relayPool.availableRelays()
        val latch = CountDownLatch(size)
        val relayErrors = mutableMapOf<String, String>()
        var result = false

        Log.d("sendAndWaitForResponse", "Waiting for $size responses")

        val subscription =
            object : Listener {
                override fun onError(
                    error: Error,
                    subscriptionId: String,
                    relay: Relay,
                ) {
                    relayErrors[relay.url]?.let {
                        latch.countDown()
                    }
                    Log.d("sendAndWaitForResponse", "onError Error from relay ${relay.url} count: ${latch.count} error: $error")
                }

                override fun onEOSE(
                    relay: Relay,
                    subscriptionId: String,
                ) {
                    latch.countDown()
                    Log.d("sendAndWaitForResponse", "onEOSE relay ${relay.url} count: ${latch.count}")
                }

                override fun onRelayStateChange(
                    type: RelayState,
                    relay: Relay,
                ) {
                    if (type == RelayState.DISCONNECTED) {
                        latch.countDown()
                    }
                    if (type == RelayState.CONNECTED) {
                        Log.d("sendAndWaitForResponse", "${type.name} Sending event to relay ${relay.url} count: ${latch.count}")
                        relay.send(signedEvent)
                    }
                    Log.d("sendAndWaitForResponse", "onRelayStateChange ${type.name} from relay ${relay.url} count: ${latch.count}")
                }

                override fun onSendResponse(
                    eventId: String,
                    success: Boolean,
                    message: String,
                    relay: Relay,
                ) {
                    if (eventId == signedEvent.id) {
                        if (success) {
                            result = true
                        }
                        latch.countDown()
                        Log.d("sendAndWaitForResponse", "onSendResponse Received response for $eventId from relay ${relay.url} count: ${latch.count} message $message success $success")
                    }
                }
            }

        subscribe(subscription)
        additionalListener?.let { subscribe(it) }

        val job =
            GlobalScope.launch(Dispatchers.IO) {
                if (relayList != null) {
                    send(signedEvent, relayList)
                } else if (relay == null) {
                    send(signedEvent)
                } else {
                    sendSingle(signedEvent, RelaySetupInfoToConnect(relay, forceProxy, true, true, emptySet()), onDone ?: {})
                }
            }
        job.join()

        runBlocking {
            latch.await(timeoutInSeconds, TimeUnit.SECONDS)
        }
        Log.d("sendAndWaitForResponse", "countdown finished")
        unsubscribe(subscription)
        additionalListener?.let { unsubscribe(it) }
        return result
    }

    fun getAll(): List<Relay> = relayPool.getAll()

    fun sendFilterOnlyIfDisconnected(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: List<TypedFilter> = listOf(),
    ) {
        checkNotInMainThread()

        subscriptions.add(subscriptionId, filters)
        relayPool.connectAndSendFiltersIfDisconnected()
    }

    fun sendIfExists(
        signedEvent: Event,
        connectedRelay: Relay,
    ) {
        checkNotInMainThread()

        relayPool.getRelays(connectedRelay.url).forEach {
            it.send(signedEvent)
        }
    }

    fun sendSingle(
        signedEvent: Event,
        relayTemplate: RelaySetupInfoToConnect,
        onDone: (() -> Unit),
    ) {
        checkNotInMainThread()

        relayPool.runCreatingIfNeeded(buildRelay(relayTemplate), onDone = onDone) {
            it.send(signedEvent)
        }
    }

    fun send(signedEvent: Event) {
        checkNotInMainThread()
        relayPool.send(signedEvent)
    }

    fun send(
        signedEvent: Event,
        relayList: List<RelaySetupInfo>,
    ) {
        checkNotInMainThread()

        relayPool.sendToSelectedRelays(relayList, signedEvent)
    }

    fun sendPrivately(
        signedEvent: Event,
        relayList: List<RelaySetupInfoToConnect>,
    ) {
        checkNotInMainThread()

        relayList.forEach { relayTemplate ->
            relayPool.runCreatingIfNeeded(buildRelay(relayTemplate)) {
                it.sendOverride(signedEvent)
            }
        }
    }

    fun close(subscriptionId: String) {
        relayPool.close(subscriptionId)
        subscriptions.remove(subscriptionId)
    }

    fun isActive(subscriptionId: String): Boolean = subscriptions.isActive(subscriptionId)

    @OptIn(DelicateCoroutinesApi::class)
    override fun onEvent(
        event: Event,
        subscriptionId: String,
        relay: Relay,
        afterEOSE: Boolean,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onEvent(event, subscriptionId, relay, afterEOSE) }
        }
    }

    override fun onEOSE(
        relay: Relay,
        subscriptionId: String,
    ) {
        listeners.forEach { it.onEOSE(relay, subscriptionId) }
    }

    override fun onRelayStateChange(
        type: RelayState,
        relay: Relay,
    ) {
        listeners.forEach { it.onRelayStateChange(type, relay) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onSendResponse(
        eventId: String,
        success: Boolean,
        message: String,
        relay: Relay,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onSendResponse(eventId, success, message, relay) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onAuth(
        relay: Relay,
        challenge: String,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) { listeners.forEach { it.onAuth(relay, challenge) } }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onNotify(
        relay: Relay,
        description: String,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onNotify(relay, description) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onSend(
        relay: Relay,
        msg: String,
        success: Boolean,
    ) {
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onSend(relay, msg, success) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onBeforeSend(
        relay: Relay,
        event: Event,
    ) {
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onBeforeSend(relay, event) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onError(
        error: Error,
        subscriptionId: String,
        relay: Relay,
    ) {
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onError(error, subscriptionId, relay) }
        }
    }

    fun subscribe(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun isSubscribed(listener: Listener): Boolean = listeners.contains(listener)

    fun unsubscribe(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    fun allSubscriptions(): Map<String, List<TypedFilter>> = subscriptions.allSubscriptions()

    fun getSubscriptionFilters(subId: String): List<TypedFilter> = subscriptions.getSubscriptionFilters(subId)

    fun connectedRelays() = relayPool.connectedRelays()

    fun relayStatusFlow() = relayPool.statusFlow

    interface Listener {
        /** A new message was received */
        open fun onEvent(
            event: Event,
            subscriptionId: String,
            relay: Relay,
            afterEOSE: Boolean,
        ) = Unit

        /** Connected to or disconnected from a relay */
        open fun onEOSE(
            relay: Relay,
            subscriptionId: String,
        ) = Unit

        /** Connected to or disconnected from a relay */
        open fun onRelayStateChange(
            type: RelayState,
            relay: Relay,
        ) = Unit

        /** When an relay saves or rejects a new event. */
        open fun onSendResponse(
            eventId: String,
            success: Boolean,
            message: String,
            relay: Relay,
        ) = Unit

        open fun onAuth(
            relay: Relay,
            challenge: String,
        ) = Unit

        open fun onNotify(
            relay: Relay,
            description: String,
        ) = Unit

        open fun onSend(
            relay: Relay,
            msg: String,
            success: Boolean,
        ) = Unit

        open fun onBeforeSend(
            relay: Relay,
            event: Event,
        ) = Unit

        open fun onError(
            error: Error,
            subscriptionId: String,
            relay: Relay,
        ) = Unit
    }
}
