package com.vitorpamplona.amethyst.service.relays

import android.util.Log
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.events.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.TimeUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

enum class FeedType {
    FOLLOWS, PUBLIC_CHATS, PRIVATE_DMS, GLOBAL, SEARCH, WALLET_CONNECT
}

val COMMON_FEED_TYPES = setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS, FeedType.GLOBAL)

class Relay(
    var url: String,
    var read: Boolean = true,
    var write: Boolean = true,
    var activeTypes: Set<FeedType> = FeedType.values().toSet(),
    proxy: Proxy?
) {
    companion object {
        // waits 3 minutes to reconnect once things fail
        const val RECONNECTING_IN_SECONDS = 60 * 3
    }

    private val httpClient = OkHttpClient.Builder()
        .proxy(proxy)
        .readTimeout(Duration.ofSeconds(if (proxy != null) 20L else 10L))
        .connectTimeout(Duration.ofSeconds(if (proxy != null) 20L else 10L))
        .writeTimeout(Duration.ofSeconds(if (proxy != null) 20L else 10L))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var listeners = setOf<Listener>()
    private var socket: WebSocket? = null
    private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    var eventDownloadCounterInBytes = 0
    var eventUploadCounterInBytes = 0

    var spamCounter = 0
    var errorCounter = 0
    var pingInMs: Long? = null

    var closingTimeInSeconds = 0L

    var afterEOSE = false

    val authResponse = mutableMapOf<HexKey, Boolean>()

    fun register(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun unregister(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    fun isConnected(): Boolean {
        return socket != null
    }

    fun connect() {
        connectAndRun {
            checkNotInMainThread()

            // Sends everything.
            renewFilters()
        }
    }

    private var connectingBlock = AtomicBoolean()

    fun connectAndRun(onConnected: (Relay) -> Unit) {
        // BRB is crashing OkHttp Deflater object :(
        if (url.contains("brb.io")) return

        // If there is a connection, don't wait.
        if (connectingBlock.getAndSet(true)) {
            return
        }

        checkNotInMainThread()

        if (socket != null) return

        try {
            val request = Request.Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(url.trim())
                .build()

            socket = httpClient.newWebSocket(request, RelayListener(onConnected))
        } catch (e: Exception) {
            errorCounter++
            markConnectionAsClosed()
            Log.e("Relay", "Relay Invalid $url")
            e.printStackTrace()
        } finally {
            connectingBlock.set(false)
        }
    }

    inner class RelayListener(val onConnected: (Relay) -> Unit) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            checkNotInMainThread()

            markConnectionAsReady(
                pingInMs = response.receivedResponseAtMillis - response.sentRequestAtMillis,
                usingCompression = response.headers.get("Sec-WebSocket-Extensions")?.contains("permessage-deflate") ?: false
            )

            // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
            onConnected(this@Relay)

            listeners.forEach { it.onRelayStateChange(this@Relay, StateType.CONNECT, null) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            checkNotInMainThread()

            eventDownloadCounterInBytes += text.bytesUsedInMemory()

            try {
                processNewRelayMessage(text)
            } catch (t: Throwable) {
                t.printStackTrace()
                text.chunked(2000) { chunked ->
                    listeners.forEach { it.onError(this@Relay, "", Error("Problem with $chunked")) }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            checkNotInMainThread()

            listeners.forEach {
                it.onRelayStateChange(
                    this@Relay,
                    StateType.DISCONNECTING,
                    null
                )
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            checkNotInMainThread()

            markConnectionAsClosed()

            listeners.forEach { it.onRelayStateChange(this@Relay, StateType.DISCONNECT, null) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            checkNotInMainThread()

            errorCounter++

            socket?.close(1000, "Normal close")
            // Failures disconnect the relay.
            markConnectionAsClosed()

            Log.w("Relay", "Relay onFailure $url, ${response?.message} $response")
            t.printStackTrace()
            listeners.forEach {
                it.onError(this@Relay, "", Error("WebSocket Failure. Response: $response. Exception: ${t.message}", t))
            }
        }
    }

    fun markConnectionAsReady(pingInMs: Long, usingCompression: Boolean) {
        this.afterEOSE = false
        this.isReady = true
        this.pingInMs = pingInMs
        this.usingCompression = usingCompression
    }

    fun markConnectionAsClosed() {
        this.socket = null
        this.isReady = false
        this.usingCompression = false
        this.afterEOSE = false
        this.closingTimeInSeconds = TimeUtils.now()
    }

    fun processNewRelayMessage(newMessage: String) {
        val msgArray = Event.mapper.readTree(newMessage)
        val type = msgArray.get(0).asText()
        val channel = msgArray.get(1).asText()

        when (type) {
            "EVENT" -> {
                val event = Event.fromJson(msgArray.get(2))

                // Log.w("Relay", "Relay onEVENT $url, $channel")
                listeners.forEach {
                    it.onEvent(this@Relay, channel, event)
                    if (afterEOSE) {
                        it.onRelayStateChange(this@Relay, StateType.EOSE, channel)
                    }
                }
            }
            "EOSE" -> listeners.forEach {
                afterEOSE = true
                // Log.w("Relay", "Relay onEOSE $url, $channel")
                it.onRelayStateChange(this@Relay, StateType.EOSE, channel)
            }
            "NOTICE" -> listeners.forEach {
                Log.w("Relay", "Relay onNotice $url, $channel")
                it.onError(this@Relay, channel, Error("Relay sent notice: " + channel))
            }
            "OK" -> listeners.forEach {
                val eventId = msgArray[1].asText()
                val success = msgArray[2].asBoolean()
                val message = msgArray[3].asText()

                if (authResponse.containsKey(eventId)) {
                    val wasAlreadyAuthenticated = authResponse.get(eventId)
                    authResponse.put(eventId, success)
                    if (wasAlreadyAuthenticated != true && success) {
                        renewFilters()
                    }
                }

                Log.w("Relay", "Relay on OK $url, $eventId, $success, $message")
                it.onSendResponse(this@Relay, eventId, success, message)
            }
            "AUTH" -> listeners.forEach {
                // Log.w("Relay", "Relay$url, ${msg[1].asString}")
                it.onAuth(this@Relay, msgArray[1].asText())
            }
            else -> listeners.forEach {
                // Log.w("Relay", "Relay something else $url, $channel")
                it.onError(
                    this@Relay,
                    channel,
                    Error("Unknown type $type on channel $channel. Msg was $newMessage")
                )
            }
        }
    }

    fun disconnect() {
        checkNotInMainThread()

        closingTimeInSeconds = TimeUtils.now()
        socket?.close(1000, "Normal close")
        socket = null
        isReady = false
        usingCompression = false
        afterEOSE = false
    }

    fun sendFilter(requestId: String) {
        checkNotInMainThread()

        if (read) {
            if (isConnected()) {
                if (isReady) {
                    val filters = Client.getSubscriptionFilters(requestId).filter { activeTypes.intersect(it.types).isNotEmpty() }
                    if (filters.isNotEmpty()) {
                        val request =
                            """["REQ","$requestId",${filters.take(10).joinToString(",") { it.filter.toJson(url) }}]"""
                        // println("FILTERSSENT $url $request")
                        socket?.send(request)
                        eventUploadCounterInBytes += request.bytesUsedInMemory()
                        afterEOSE = false
                    }
                }
            } else {
                // waits 60 seconds to reconnect after disconnected.
                if (TimeUtils.now() > closingTimeInSeconds + RECONNECTING_IN_SECONDS) {
                    // sends all filters after connection is successful.
                    connect()
                }
            }
        }
    }

    fun sendFilterOnlyIfDisconnected() {
        checkNotInMainThread()

        if (socket == null) {
            // waits 60 seconds to reconnect after disconnected.
            if (TimeUtils.now() > closingTimeInSeconds + RECONNECTING_IN_SECONDS) {
                // println("sendfilter Only if Disconnected ${url} ")
                connect()
            }
        }
    }

    fun renewFilters() {
        // Force update all filters after AUTH.
        Client.allSubscriptions().forEach {
            sendFilter(requestId = it)
        }
    }

    fun send(signedEvent: EventInterface) {
        checkNotInMainThread()

        if (signedEvent is RelayAuthEvent) {
            authResponse.put(signedEvent.id, false)
            // specific protocol for this event.
            val event = """["AUTH",${signedEvent.toJson()}]"""
            socket?.send(event)
            eventUploadCounterInBytes += event.bytesUsedInMemory()
        } else {
            if (write) {
                if (isConnected()) {
                    if (isReady) {
                        val event = """["EVENT",${signedEvent.toJson()}]"""
                        socket?.send(event)
                        eventUploadCounterInBytes += event.bytesUsedInMemory()
                    }
                } else {
                    // waits 60 seconds to reconnect after disconnected.
                    if (TimeUtils.now() > closingTimeInSeconds + RECONNECTING_IN_SECONDS) {
                        // sends all filters after connection is successful.
                        connectAndRun {
                            checkNotInMainThread()

                            val event = """["EVENT",${signedEvent.toJson()}]"""
                            socket?.send(event)
                            eventUploadCounterInBytes += event.bytesUsedInMemory()

                            // Sends everything.
                            Client.allSubscriptions().forEach {
                                sendFilter(requestId = it)
                            }
                        }
                    }
                }
            }
        }
    }

    fun close(subscriptionId: String) {
        socket?.send("""["CLOSE","$subscriptionId"]""")
    }

    fun isSameRelayConfig(other: Relay): Boolean {
        return url == other.url &&
            write == other.write &&
            read == other.read &&
            activeTypes == other.activeTypes
    }

    enum class StateType {
        // Websocket connected
        CONNECT,

        // Websocket disconnecting
        DISCONNECTING,

        // Websocket disconnected
        DISCONNECT,

        // End Of Stored Events
        EOSE
    }

    interface Listener {
        /**
         * A new message was received
         */
        fun onEvent(relay: Relay, subscriptionId: String, event: Event)

        fun onError(relay: Relay, subscriptionId: String, error: Error)

        fun onSendResponse(relay: Relay, eventId: String, success: Boolean, message: String)

        fun onAuth(relay: Relay, challenge: String)

        /**
         * Connected to or disconnected from a relay
         *
         * @param type is 0 for disconnect and 1 for connect
         */
        fun onRelayStateChange(relay: Relay, type: StateType, channel: String?)
    }
}
