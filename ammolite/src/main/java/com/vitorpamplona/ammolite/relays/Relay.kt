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

import android.util.Log
import com.vitorpamplona.ammolite.BuildConfig
import com.vitorpamplona.ammolite.service.HttpClientManager
import com.vitorpamplona.ammolite.service.checkNotInMainThread
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean

enum class FeedType {
    FOLLOWS,
    PUBLIC_CHATS,
    PRIVATE_DMS,
    GLOBAL,
    SEARCH,
    WALLET_CONNECT,
}

val COMMON_FEED_TYPES =
    setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS, FeedType.GLOBAL)

val EVENT_FINDER_TYPES =
    setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.GLOBAL)

class Relay(
    val url: String,
    val read: Boolean = true,
    val write: Boolean = true,
    val activeTypes: Set<FeedType>,
) {
    companion object {
        // waits 3 minutes to reconnect once things fail
        const val RECONNECTING_IN_SECONDS = 60 * 3
    }

    val brief = RelayBriefInfoCache.get(url)

    private var listeners = setOf<Listener>()
    private var socket: WebSocket? = null
    private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    private var lastConnectTentative: Long = 0L

    private var afterEOSEPerSubscription = mutableMapOf<String, Boolean>()

    private val authResponse = mutableMapOf<HexKey, Boolean>()
    private val authChallengesSent = mutableSetOf<String>()
    private val outboxCache = mutableMapOf<HexKey, EventInterface>()

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

    private fun sendOutbox() {
        synchronized(outboxCache) {
            outboxCache.values.forEach {
                send(it)
            }
        }
    }

    private var connectingBlock = AtomicBoolean()

    fun connectAndRun(onConnected: (Relay) -> Unit) {
        Log.d("Relay", "Relay.connect $url isAlreadyConnecting: ${connectingBlock.get()}")
        // BRB is crashing OkHttp Deflater object :(
        if (url.contains("brb.io")) return

        // If there is a connection, don't wait.
        if (connectingBlock.getAndSet(true)) {
            return
        }

        try {
            checkNotInMainThread()

            if (socket != null) {
                connectingBlock.set(false)
                return
            }

            lastConnectTentative = TimeUtils.now()

            val request =
                Request
                    .Builder()
                    .header("User-Agent", HttpClientManager.getDefaultUserAgentHeader())
                    .url(url.trim())
                    .build()

            socket = HttpClientManager.getHttpClientForUrl(url).newWebSocket(request, RelayListener(onConnected))
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            RelayStats.newError(url, e.message)

            markConnectionAsClosed()
            e.printStackTrace()
        } finally {
            connectingBlock.set(false)
        }
    }

    inner class RelayListener(
        val onConnected: (Relay) -> Unit,
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            checkNotInMainThread()
            Log.d("Relay", "Connect onOpen $url $socket")

            markConnectionAsReady(
                pingInMs = response.receivedResponseAtMillis - response.sentRequestAtMillis,
                usingCompression =
                    response.headers.get("Sec-WebSocket-Extensions")?.contains("permessage-deflate") ?: false,
            )

            // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
            onConnected(this@Relay)

            listeners.forEach { it.onRelayStateChange(this@Relay, StateType.CONNECT, null) }
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            checkNotInMainThread()

            RelayStats.addBytesReceived(url, text.bytesUsedInMemory())

            try {
                processNewRelayMessage(text)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                text.chunked(2000) { chunked ->
                    listeners.forEach { it.onError(this@Relay, "", Error("Problem with $chunked")) }
                }
            }
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            checkNotInMainThread()

            Log.w("Relay", "Relay onClosing $url: $reason")

            listeners.forEach {
                it.onRelayStateChange(
                    this@Relay,
                    StateType.DISCONNECTING,
                    null,
                )
            }
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            checkNotInMainThread()

            markConnectionAsClosed()

            Log.w("Relay", "Relay onClosed $url: $reason")

            listeners.forEach { it.onRelayStateChange(this@Relay, StateType.DISCONNECT, null) }
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            checkNotInMainThread()

            socket?.cancel() // 1000, "Normal close"

            // checks if this is an actual failure. Closing the socket generates an onFailure as well.
            if (!(socket == null && (t.message == "Socket is closed" || t.message == "Socket closed"))) {
                RelayStats.newError(url, response?.message ?: t.message)

                Log.w("Relay", "Relay onFailure $url, ${response?.message} $response ${t.message} $socket")
                t.printStackTrace()
                listeners.forEach {
                    it.onError(
                        this@Relay,
                        "",
                        Error("WebSocket Failure. Response: $response. Exception: ${t.message}", t),
                    )
                }
            }

            // Failures disconnect the relay.
            markConnectionAsClosed()
        }
    }

    fun markConnectionAsReady(
        pingInMs: Long,
        usingCompression: Boolean,
    ) {
        this.resetEOSEStatuses()
        this.isReady = true
        this.usingCompression = usingCompression

        RelayStats.setPing(url, pingInMs)
    }

    fun markConnectionAsClosed() {
        this.socket = null
        this.isReady = false
        this.usingCompression = false
        this.resetEOSEStatuses()
    }

    fun processNewRelayMessage(newMessage: String) {
        val msgArray = Event.mapper.readTree(newMessage)

        when (val type = msgArray.get(0).asText()) {
            "EVENT" -> {
                val subscriptionId = msgArray.get(1).asText()
                val event = Event.fromJson(msgArray.get(2))

                // Log.w("Relay", "Relay onEVENT ${event.kind} $url, $subscriptionId ${msgArray.get(2)}")

                listeners.forEach {
                    it.onEvent(
                        this@Relay,
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
                    it.onRelayStateChange(this@Relay, StateType.EOSE, subscriptionId)
                }
            "NOTICE" ->
                listeners.forEach {
                    val message = msgArray.get(1).asText()
                    Log.w("Relay", "Relay onNotice $url, $message")

                    RelayStats.newNotice(url, message)

                    it.onError(this@Relay, message, Error("Relay sent notice: $message"))
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
                        RelayStats.newNotice(url, "Failed to receive $eventId: $message")
                    }

                    it.onSendResponse(this@Relay, eventId, success, message)
                }
            "AUTH" ->
                listeners.forEach {
                    Log.w("Relay", "Relay onAuth $url, ${ msgArray[1].asText()}")
                    it.onAuth(this@Relay, msgArray[1].asText())
                }
            "NOTIFY" ->
                listeners.forEach {
                    // Log.w("Relay", "Relay onNotify $url, ${msg[1].asString}")
                    it.onNotify(this@Relay, msgArray[1].asText())
                }
            "CLOSED" -> listeners.forEach { Log.w("Relay", "Relay onClosed $url, $newMessage") }
            else -> {
                RelayStats.newError(url, "Unsupported message: $newMessage")

                listeners.forEach {
                    Log.w("Relay", "Unsupported message: $newMessage")
                    it.onError(
                        this@Relay,
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
        filters: List<TypedFilter>,
    ) {
        checkNotInMainThread()

        if (read) {
            if (isConnected()) {
                if (isReady) {
                    val relayFilters =
                        filters.filter { filter ->
                            activeTypes.any { it in filter.types }
                        }

                    if (relayFilters.isNotEmpty()) {
                        val request =
                            relayFilters.joinToStringLimited(
                                separator = ",",
                                limit = 20,
                                prefix = """["REQ","$requestId",""",
                                postfix = "]",
                            ) {
                                it.filter.toJson(url)
                            }

                        writeToSocket(request)

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
    }

    fun <T> Iterable<T>.joinToStringLimited(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "",
        limit: Int = -1,
        transform: ((T) -> CharSequence)? = null,
    ): String {
        val buffer = StringBuilder()
        buffer.append(prefix)
        var count = 0
        for (element in this) {
            if (limit < 0 || count <= limit) {
                if (++count > 1) buffer.append(separator)
                when {
                    transform != null -> buffer.append(transform(element))
                    element is CharSequence? -> buffer.append(element)
                    element is Char -> buffer.append(element)
                    else -> buffer.append(element.toString())
                }
            } else {
                break
            }
        }
        buffer.append(postfix)
        return buffer.toString()
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
        Client.allSubscriptions().forEach {
            sendFilter(requestId = it.key, it.value)
        }
    }

    // This function sends the event regardless of the relay being write or not.
    fun sendOverride(signedEvent: EventInterface) {
        checkNotInMainThread()

        listeners.forEach { listener ->
            listener.onBeforeSend(this@Relay, signedEvent)
        }

        if (signedEvent is RelayAuthEvent) {
            sendAuth(signedEvent)
        } else {
            sendEvent(signedEvent)
        }
    }

    fun send(signedEvent: EventInterface) {
        checkNotInMainThread()

        listeners.forEach { listener ->
            listener.onBeforeSend(this@Relay, signedEvent)
        }

        if (signedEvent is RelayAuthEvent) {
            sendAuth(signedEvent)
        } else {
            if (write) {
                sendEvent(signedEvent)
            }
        }
    }

    private fun sendAuth(signedEvent: RelayAuthEvent) {
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

    private fun sendEvent(signedEvent: EventInterface) {
        synchronized(outboxCache) {
            outboxCache.put(signedEvent.id(), signedEvent)
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
                    this@Relay,
                    "",
                    Error("Failed to send $str. Relay is not connected."),
                )
            }
        }
        socket?.let {
            checkNotInMainThread()
            val result = it.send(str)
            listeners.forEach { listener ->
                listener.onSend(this@Relay, str, result)
            }
            RelayStats.addBytesSent(url, str.bytesUsedInMemory())

            if (BuildConfig.DEBUG) {
                Log.d("Relay", "Relay send $url $str")
            }
        }
    }

    fun close(subscriptionId: String) {
        writeToSocket("""["CLOSE","$subscriptionId"]""")
    }

    fun isSameRelayConfig(other: RelaySetupInfo): Boolean =
        url == other.url &&
            write == other.write &&
            read == other.read &&
            activeTypes == other.feedTypes

    enum class StateType {
        // Websocket connected
        CONNECT,

        // Websocket disconnecting
        DISCONNECTING,

        // Websocket disconnected
        DISCONNECT,

        // End Of Stored Events
        EOSE,
    }

    interface Listener {
        /** A new message was received */
        fun onEvent(
            relay: Relay,
            subscriptionId: String,
            event: Event,
            afterEOSE: Boolean,
        )

        fun onError(
            relay: Relay,
            subscriptionId: String,
            error: Error,
        )

        fun onSendResponse(
            relay: Relay,
            eventId: String,
            success: Boolean,
            message: String,
        )

        fun onAuth(
            relay: Relay,
            challenge: String,
        )

        /**
         * Connected to or disconnected from a relay
         *
         * @param type is 0 for disconnect and 1 for connect
         */
        fun onRelayStateChange(
            relay: Relay,
            type: StateType,
            channel: String?,
        )

        /** Relay sent an invoice */
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
    }
}
