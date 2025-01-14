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
package com.vitorpamplona.ammolite.relays.relays

import android.util.Log
import com.vitorpamplona.ammolite.relays.relays.sockets.WebSocket
import com.vitorpamplona.ammolite.relays.relays.sockets.WebSocketListener
import com.vitorpamplona.ammolite.relays.relays.sockets.WebsocketBuilder
import com.vitorpamplona.ammolite.service.checkNotInMainThread
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.relays.Filter
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.joinToStringLimited
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class SimpleRelay(
    val url: String,
    val socketBuilder: WebsocketBuilder,
    val subs: SubscriptionCollection,
    val stats: RelayStat = RelayStat(),
) {
    companion object {
        // waits 3 minutes to reconnect once things fail
        const val RECONNECTING_IN_SECONDS = 60 * 3
    }

    private var listeners = setOf<Listener>()
    private var socket: WebSocket? = null
    private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    private var lastConnectTentative: Long = 0L

    private var afterEOSEPerSubscription = mutableMapOf<String, Boolean>()

    private val authResponse = mutableMapOf<HexKey, Boolean>()
    private val authChallengesSent = mutableSetOf<String>()
    private val outboxCache = mutableMapOf<HexKey, Event>()

    private var connectingMutex = AtomicBoolean()

    fun register(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun unregister(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    fun isConnected(): Boolean = socket != null

    fun connect() {
        connectAndRun {
            checkNotInMainThread()

            // Sends everything.
            renewFilters()
            sendOutbox()
        }
    }

    fun sendOutbox() {
        synchronized(outboxCache) {
            outboxCache.values.forEach {
                send(it)
            }
        }
    }

    fun connectAndRun(onConnected: () -> Unit) {
        Log.d("Relay", "Relay.connect $url isAlreadyConnecting: ${connectingMutex.get()}")

        // If there is a connection, don't wait.
        if (connectingMutex.getAndSet(true)) {
            return
        }

        try {
            checkNotInMainThread()

            if (socket != null) {
                connectingMutex.set(false)
                return
            }

            lastConnectTentative = TimeUtils.now()

            socket = socketBuilder.build(url, RelayListener(onConnected))
            socket?.connect()
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            stats.newError(e.message ?: "Error trying to connect: ${e.javaClass.simpleName}")

            markConnectionAsClosed()
            e.printStackTrace()
        } finally {
            connectingMutex.set(false)
        }
    }

    inner class RelayListener(
        val onConnected: () -> Unit,
    ) : WebSocketListener {
        override fun onOpen(
            pingMillis: Long,
            compression: Boolean,
        ) {
            checkNotInMainThread()
            Log.d("Relay", "Connect onOpen $url $socket")

            markConnectionAsReady(pingMillis, compression)

            // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
            onConnected()

            listeners.forEach { it.onRelayStateChange(this@SimpleRelay, RelayState.CONNECTED) }
        }

        override fun onMessage(text: String) {
            checkNotInMainThread()

            stats.addBytesReceived(text.bytesUsedInMemory())

            try {
                processNewRelayMessage(text)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                text.chunked(2000) { chunked ->
                    listeners.forEach { it.onError(this@SimpleRelay, "", Error("Problem with $chunked")) }
                }
            }
        }

        override fun onClosing(
            code: Int,
            reason: String,
        ) {
            checkNotInMainThread()

            Log.w("Relay", "Relay onClosing $url: $reason")

            listeners.forEach {
                it.onRelayStateChange(this@SimpleRelay, RelayState.DISCONNECTING)
            }
        }

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            checkNotInMainThread()

            markConnectionAsClosed()

            Log.w("Relay", "Relay onClosed $url: $reason")

            listeners.forEach { it.onRelayStateChange(this@SimpleRelay, RelayState.DISCONNECTED) }
        }

        override fun onFailure(
            t: Throwable,
            responseMessage: String?,
        ) {
            checkNotInMainThread()

            socket?.cancel() // 1000, "Normal close"

            // checks if this is an actual failure. Closing the socket generates an onFailure as well.
            if (!(socket == null && (t.message == "Socket is closed" || t.message == "Socket closed"))) {
                stats.newError(responseMessage ?: t.message ?: "onFailure event from server: ${t.javaClass.simpleName}")
            }

            // Failures disconnect the relay.
            markConnectionAsClosed()

            Log.w("Relay", "Relay onFailure $url, $responseMessage $responseMessage ${t.message} $socket")
            t.printStackTrace()
            listeners.forEach {
                it.onError(
                    this@SimpleRelay,
                    "",
                    Error("WebSocket Failure. Response: $responseMessage. Exception: ${t.message}", t),
                )
            }
        }
    }

    fun markConnectionAsReady(
        pingInMs: Long,
        usingCompression: Boolean,
    ) {
        this.resetEOSEStatuses()
        this.isReady = true
        this.usingCompression = usingCompression

        stats.pingInMs = pingInMs
    }

    fun markConnectionAsClosed() {
        this.socket = null
        this.isReady = false
        this.usingCompression = false
        this.resetEOSEStatuses()
    }

    fun processNewRelayMessage(newMessage: String) {
        val msgArray = EventMapper.mapper.readTree(newMessage)

        when (val type = msgArray.get(0).asText()) {
            "EVENT" -> {
                val subscriptionId = msgArray.get(1).asText()
                val event = EventMapper.fromJson(msgArray.get(2))

                // Log.w("Relay", "Relay onEVENT ${event.kind} $url, $subscriptionId ${msgArray.get(2)}")

                listeners.forEach {
                    it.onEvent(
                        this@SimpleRelay,
                        subscriptionId,
                        event,
                        afterEOSEPerSubscription[subscriptionId] == true,
                    )
                }
            }
            "EOSE" ->
                listeners.forEach {
                    val subscriptionId = msgArray.get(1).asText()

                    afterEOSEPerSubscription[subscriptionId] = true
                    // Log.w("Relay", "Relay onEOSE $url $subscriptionId")
                    it.onEOSE(this@SimpleRelay, subscriptionId)
                }
            "NOTICE" ->
                listeners.forEach {
                    val message = msgArray.get(1).asText()
                    Log.w("Relay", "Relay onNotice $url, $message")

                    stats.newNotice(message)

                    it.onError(this@SimpleRelay, message, Error("Relay sent notice: $message"))
                }
            "OK" ->
                listeners.forEach {
                    val eventId = msgArray[1].asText()
                    val success = msgArray[2].asBoolean()
                    val message = if (msgArray.size() > 2) msgArray[3].asText() else ""

                    Log.w("Relay", "Relay on OK $url, $eventId, $success, $message")

                    if (authResponse.containsKey(eventId)) {
                        val wasAlreadyAuthenticated = authResponse.get(eventId)
                        authResponse.put(eventId, success)
                        if (wasAlreadyAuthenticated != true && success) {
                            renewFilters()
                            sendOutbox()
                        }
                    }

                    if (outboxCache.contains(eventId) && !message.startsWith("auth-required")) {
                        synchronized(outboxCache) {
                            outboxCache.remove(eventId)
                        }
                    }

                    if (!success) {
                        stats.newNotice("Failed to receive $eventId: $message")
                    }

                    it.onSendResponse(this@SimpleRelay, eventId, success, message)
                }
            "AUTH" ->
                listeners.forEach {
                    Log.w("Relay", "Relay onAuth $url, ${ msgArray[1].asText()}")
                    it.onAuth(this@SimpleRelay, msgArray[1].asText())
                }
            "NOTIFY" ->
                listeners.forEach {
                    // Log.w("Relay", "Relay onNotify $url, ${msg[1].asString}")
                    it.onNotify(this@SimpleRelay, msgArray[1].asText())
                }
            "CLOSED" -> listeners.forEach { Log.w("Relay", "Relay Closed Subscription $url, $newMessage") }
            else -> {
                stats.newError("Unsupported message: $newMessage")

                listeners.forEach {
                    Log.w("Relay", "Unsupported message: $newMessage")
                    it.onError(
                        this@SimpleRelay,
                        "",
                        Error("Unknown type $type on channel. Msg was $newMessage"),
                    )
                }
            }
        }
    }

    fun disconnect() {
        Log.d("Relay", "Relay.disconnect $url")
        checkNotInMainThread()

        lastConnectTentative = 0L // this is not an error, so prepare to reconnect as soon as requested.
        socket?.cancel()
        socket = null
        isReady = false
        usingCompression = false
        resetEOSEStatuses()
    }

    fun resetEOSEStatuses() {
        afterEOSEPerSubscription = LinkedHashMap(afterEOSEPerSubscription.size)

        authResponse.clear()
        authChallengesSent.clear()
    }

    fun sendFilter(
        requestId: String,
        filters: List<Filter>,
    ) {
        checkNotInMainThread()

        if (isConnected()) {
            if (isReady) {
                if (filters.isNotEmpty()) {
                    writeToSocket(
                        filters.joinToStringLimited(
                            separator = ",",
                            limit = 19,
                            prefix = """["REQ","$requestId",""",
                            postfix = "]",
                        ) {
                            it.toJson()
                        },
                    )

                    afterEOSEPerSubscription[requestId] = false
                }
            }
        } else {
            // waits 60 seconds to reconnect after disconnected.
            if (TimeUtils.now() > lastConnectTentative + RECONNECTING_IN_SECONDS) {
                // sends all filters after connection is successful.
                connect()
            }
        }
    }

    fun connectAndSendFiltersIfDisconnected() {
        checkNotInMainThread()

        if (socket == null) {
            // waits 60 seconds to reconnect after disconnected.
            if (TimeUtils.now() > lastConnectTentative + RECONNECTING_IN_SECONDS) {
                // println("sendfilter Only if Disconnected ${url} ")
                connect()
            }
        }
    }

    fun renewFilters() {
        // Force update all filters after AUTH.
        subs.allSubscriptions().forEach {
            sendFilter(requestId = it.id, it.filters)
        }
    }

    fun send(signedEvent: Event) {
        checkNotInMainThread()

        listeners.forEach { listener ->
            listener.onBeforeSend(this@SimpleRelay, signedEvent)
        }

        if (signedEvent is RelayAuthEvent) {
            sendAuth(signedEvent)
        } else {
            sendEvent(signedEvent)
        }
    }

    fun sendAuth(signedEvent: RelayAuthEvent) {
        val challenge = signedEvent.challenge() ?: ""

        // only send replies to new challenges to avoid infinite loop:
        // 1. Auth is sent
        // 2. auth is rejected
        // 3. auth is requested
        // 4. auth is sent
        // ...
        if (!authChallengesSent.contains(challenge)) {
            authResponse.put(signedEvent.id, false)
            authChallengesSent.add(challenge)
            writeToSocket("""["AUTH",${signedEvent.toJson()}]""")
        }
    }

    fun sendEvent(signedEvent: Event) {
        synchronized(outboxCache) {
            outboxCache.put(signedEvent.id, signedEvent)
        }

        if (isConnected()) {
            if (isReady) {
                writeToSocket("""["EVENT",${signedEvent.toJson()}]""")
            }
        } else {
            // sends all filters after connection is successful.
            connectAndRun {
                // Sends everything.
                renewFilters()
                sendOutbox()
            }
        }
    }

    private fun writeToSocket(str: String) {
        if (socket == null) {
            listeners.forEach { listener ->
                listener.onError(
                    this@SimpleRelay,
                    "",
                    Error("Failed to send $str. Relay is not connected."),
                )
            }
        }
        socket?.let {
            checkNotInMainThread()
            val result = it.send(str)
            listeners.forEach { listener ->
                listener.onSend(this@SimpleRelay, str, result)
            }
            stats.addBytesSent(str.bytesUsedInMemory())

            Log.d("Relay", "Relay send $url (${str.length} chars) $str")
        }
    }

    fun close(subscriptionId: String) {
        writeToSocket("""["CLOSE","$subscriptionId"]""")
    }

    interface Listener {
        fun onEvent(
            relay: SimpleRelay,
            subscriptionId: String,
            event: Event,
            afterEOSE: Boolean,
        )

        fun onEOSE(
            relay: SimpleRelay,
            subscriptionId: String,
        )

        fun onError(
            relay: SimpleRelay,
            subscriptionId: String,
            error: Error,
        )

        fun onSendResponse(
            relay: SimpleRelay,
            eventId: String,
            success: Boolean,
            message: String,
        )

        fun onAuth(
            relay: SimpleRelay,
            challenge: String,
        )

        fun onRelayStateChange(
            relay: SimpleRelay,
            type: RelayState,
        )

        /** Relay sent a notification */
        fun onNotify(
            relay: SimpleRelay,
            description: String,
        )

        fun onBeforeSend(
            relay: SimpleRelay,
            event: Event,
        )

        fun onSend(
            relay: SimpleRelay,
            msg: String,
            success: Boolean,
        )
    }
}
