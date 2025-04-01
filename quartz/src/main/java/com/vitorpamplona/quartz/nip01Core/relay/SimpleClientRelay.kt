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
package com.vitorpamplona.quartz.nip01Core.relay

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ToClientParser
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class SimpleClientRelay(
    val url: String,
    val socketBuilder: WebsocketBuilder,
    val subs: SubscriptionCollection,
    val listener: Listener,
    val stats: RelayStat = RelayStat(),
) {
    companion object {
        // waits 3 minutes to reconnect once things fail
        const val RECONNECTING_IN_SECONDS = 60 * 3
    }

    private var socket: WebSocket? = null
    private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    private var lastConnectTentative: Long = 0L

    private var afterEOSEPerSubscription = mutableMapOf<String, Boolean>()

    private val authResponseWatcher = mutableMapOf<HexKey, Boolean>()
    private val authChallengesSent = mutableSetOf<String>()

    /**
     * Auth procedures require us to keep track of the outgoing events
     * to make sure the relay waits for the auth to finish and send them.
     */
    private val outboxCache = mutableMapOf<HexKey, Event>()

    private var connectingMutex = AtomicBoolean()

    private val parser = ToClientParser()

    fun isConnectionStarted(): Boolean = socket != null

    fun isConnected(): Boolean = socket != null && isReady

    fun connect() = connectAndRunOverride(::sendEverything)

    fun sendEverything() {
        renewSubscriptions()
        sendOutbox()
    }

    fun sendOutbox() {
        synchronized(outboxCache) {
            outboxCache.values.forEach {
                send(it)
            }
        }
    }

    fun connectAndRunAfterSync(onConnected: () -> Unit) {
        connectAndRunOverride {
            sendEverything()
            onConnected()
        }
    }

    fun connectAndRunOverride(onConnected: () -> Unit) {
        Log.d("Relay", "Relay.connect $url isAlreadyConnecting: ${connectingMutex.get()}")

        // If there is a connection, don't wait.
        if (connectingMutex.getAndSet(true)) {
            return
        }

        try {
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
            Log.d("Relay", "Connect onOpen $url $socket")

            markConnectionAsReady(pingMillis, compression)

            // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
            onConnected()

            listener.onRelayStateChange(this@SimpleClientRelay, RelayState.CONNECTED)
        }

        override fun onMessage(text: String) {
            stats.addBytesReceived(text.bytesUsedInMemory())

            try {
                processNewRelayMessage(text)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                stats.newError("Error processing: $text")
                Log.e("Relay", "Error processing: $text")
                listener.onError(this@SimpleClientRelay, "", Error("Error processing $text"))
            }
        }

        override fun onClosing(
            code: Int,
            reason: String,
        ) {
            Log.w("Relay", "Relay onClosing $url: $reason")

            listener.onRelayStateChange(this@SimpleClientRelay, RelayState.DISCONNECTING)
        }

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            markConnectionAsClosed()

            Log.w("Relay", "Relay onClosed $url: $reason")

            listener.onRelayStateChange(this@SimpleClientRelay, RelayState.DISCONNECTED)
        }

        override fun onFailure(
            t: Throwable,
            response: String?,
        ) {
            socket?.cancel() // 1000, "Normal close"

            // checks if this is an actual failure. Closing the socket generates an onFailure as well.
            if (!(socket == null && (t.message == "Socket is closed" || t.message == "Socket closed"))) {
                stats.newError(response ?: t.message ?: "onFailure event from server: ${t.javaClass.simpleName}")
            }

            // Failures disconnect the relay.
            markConnectionAsClosed()

            Log.w("Relay", "Relay onFailure $url, $response $response ${t.message} $socket")
            t.printStackTrace()
            listener.onError(
                this@SimpleClientRelay,
                "",
                Error("WebSocket Failure. Response: $response. Exception: ${t.message}", t),
            )
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
        when (val msg = parser.parse(newMessage)) {
            is EventMessage -> {
                // Log.w("Relay", "Relay onEVENT $url $newMessage")
                listener.onEvent(this, msg.subId, msg.event, afterEOSEPerSubscription[msg.subId] == true)
            }
            is EoseMessage -> {
                // Log.w("Relay", "Relay onEOSE $url $newMessage")
                afterEOSEPerSubscription[msg.subId] = true
                listener.onEOSE(this@SimpleClientRelay, msg.subId)
            }
            is NoticeMessage -> {
                // Log.w("Relay", "Relay onNotice $url, $newMessage")
                stats.newNotice(msg.message)
                listener.onError(this@SimpleClientRelay, msg.message, Error("Relay sent notice: $msg.message"))
            }
            is OkMessage -> {
                Log.w("Relay", "Relay on OK $url, $newMessage")

                // if this is the OK of an auth event, renew all subscriptions and resend all outgoing events.
                if (authResponseWatcher.containsKey(msg.eventId)) {
                    val wasAlreadyAuthenticated = authResponseWatcher[msg.eventId]
                    authResponseWatcher.put(msg.eventId, msg.success)
                    if (wasAlreadyAuthenticated != true && msg.success) {
                        sendEverything()
                    }
                }

                // remove from cache for any error that is not an auth required error.
                // for auth required, we will do the auth and try to send again.
                if (outboxCache.contains(msg.eventId) && !msg.message.startsWith("auth-required")) {
                    synchronized(outboxCache) {
                        outboxCache.remove(msg.eventId)
                    }
                }

                if (!msg.success) {
                    stats.newNotice("Rejected event ${msg.eventId}: ${msg.message}")
                }

                listener.onSendResponse(this@SimpleClientRelay, msg.eventId, msg.success, msg.message)
            }
            is AuthMessage -> {
                // Log.d("Relay", "Relay onAuth $url, $newMessage")
                listener.onAuth(this@SimpleClientRelay, msg.challenge)
            }
            is NotifyMessage -> {
                // Log.w("Relay", "Relay onNotify $url, $newMessage")
                listener.onNotify(this@SimpleClientRelay, msg.message)
            }
            is ClosedMessage -> {
                // Log.w("Relay", "Relay Closed Subscription $url, $newMessage")
                listener.onClosed(this@SimpleClientRelay, msg.subscriptionId, msg.message)
            }
            else -> {
                stats.newError("Unsupported message: $newMessage")
                Log.w("Relay", "Unsupported message: $newMessage")
                listener.onError(this, "", Error("Unsupported message: $newMessage"))
            }
        }
    }

    fun disconnect() {
        Log.d("Relay", "Relay.disconnect $url")
        lastConnectTentative = 0L // this is not an error, so prepare to reconnect as soon as requested.
        socket?.cancel()
        socket = null
        isReady = false
        usingCompression = false
        resetEOSEStatuses()
    }

    fun resetEOSEStatuses() {
        afterEOSEPerSubscription = LinkedHashMap(afterEOSEPerSubscription.size)

        authResponseWatcher.clear()
        authChallengesSent.clear()
    }

    fun sendRequest(
        requestId: String,
        filters: List<Filter>,
    ) {
        if (isConnectionStarted()) {
            if (isReady) {
                if (filters.isNotEmpty()) {
                    writeToSocket(ReqCmd.toJson(requestId, filters))
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

    fun sendCount(
        requestId: String,
        filters: List<Filter>,
    ) {
        if (isConnectionStarted()) {
            if (isReady) {
                if (filters.isNotEmpty()) {
                    writeToSocket(CountCmd.toJson(requestId, filters))
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
        if (socket == null) {
            // waits 60 seconds to reconnect after disconnected.
            if (TimeUtils.now() > lastConnectTentative + RECONNECTING_IN_SECONDS) {
                connect()
            }
        }
    }

    fun renewSubscriptions() {
        // Force update all filters after AUTH.
        subs.allSubscriptions().forEach {
            sendRequest(requestId = it.id, it.filters)
        }
    }

    fun send(signedEvent: Event) {
        listener.onBeforeSend(this@SimpleClientRelay, signedEvent)

        if (signedEvent is RelayAuthEvent) {
            sendAuth(signedEvent)
        } else {
            sendEvent(signedEvent)
        }
    }

    fun sendAuth(signedEvent: RelayAuthEvent) {
        val challenge = signedEvent.challenge()

        // only send replies to new challenges to avoid infinite loop:
        if (challenge != null && challenge !in authChallengesSent) {
            authResponseWatcher[signedEvent.id] = false
            authChallengesSent.add(challenge)
            writeToSocket(AuthCmd.toJson(signedEvent))
        }
    }

    fun sendEvent(signedEvent: Event) {
        synchronized(outboxCache) {
            outboxCache.put(signedEvent.id, signedEvent)
        }

        if (isConnectionStarted()) {
            if (isReady) {
                writeToSocket(EventCmd.toJson(signedEvent))
            }
        } else {
            // automatically sends all filters after connection is successful.
            connect()
        }
    }

    private fun writeToSocket(str: String) {
        if (socket == null) {
            listener.onError(
                this@SimpleClientRelay,
                "",
                Error("Failed to send $str. Relay is not connected."),
            )
        }
        socket?.let {
            val result = it.send(str)
            listener.onSend(this@SimpleClientRelay, str, result)
            stats.addBytesSent(str.bytesUsedInMemory())

            Log.d("Relay", "Relay send $url (${str.length} chars) $str")
        }
    }

    fun close(subscriptionId: String) {
        writeToSocket(CloseCmd.toJson(subscriptionId))
    }

    interface Listener {
        fun onEvent(
            relay: SimpleClientRelay,
            subscriptionId: String,
            event: Event,
            afterEOSE: Boolean,
        )

        fun onEOSE(
            relay: SimpleClientRelay,
            subscriptionId: String,
        )

        fun onError(
            relay: SimpleClientRelay,
            subscriptionId: String,
            error: Error,
        )

        fun onAuth(
            relay: SimpleClientRelay,
            challenge: String,
        )

        fun onRelayStateChange(
            relay: SimpleClientRelay,
            type: RelayState,
        )

        fun onNotify(
            relay: SimpleClientRelay,
            description: String,
        )

        fun onClosed(
            relay: SimpleClientRelay,
            subscriptionId: String,
            message: String,
        )

        fun onBeforeSend(
            relay: SimpleClientRelay,
            event: Event,
        )

        fun onSend(
            relay: SimpleClientRelay,
            msg: String,
            success: Boolean,
        )

        fun onSendResponse(
            relay: SimpleClientRelay,
            eventId: String,
            success: Boolean,
            message: String,
        )
    }
}
