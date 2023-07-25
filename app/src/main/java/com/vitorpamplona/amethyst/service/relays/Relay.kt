package com.vitorpamplona.amethyst.service.relays

import android.util.Log
import com.google.gson.JsonElement
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.EventInterface
import com.vitorpamplona.amethyst.service.model.RelayAuthEvent
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

    var eventDownloadCounterInBytes = 0
    var eventUploadCounterInBytes = 0

    var spamCounter = 0
    var errorCounter = 0
    var pingInMs: Long? = null

    var closingTimeInSeconds = 0L

    var afterEOSE = false

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
            Client.allSubscriptions().forEach {
                sendFilter(requestId = it)
            }
        }
    }

    private var connectingBlock = AtomicBoolean()

    fun connectAndRun(onConnected: (Relay) -> Unit) {
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

            markConnectionAsReady(response.receivedResponseAtMillis - response.sentRequestAtMillis)

            // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
            onConnected(this@Relay)

            listeners.forEach { it.onRelayStateChange(this@Relay, Type.CONNECT, null) }
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
                    Type.DISCONNECTING,
                    null
                )
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            checkNotInMainThread()

            markConnectionAsClosed()

            listeners.forEach { it.onRelayStateChange(this@Relay, Type.DISCONNECT, null) }
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

    fun markConnectionAsReady(pingInMs: Long) {
        this.afterEOSE = false
        this.isReady = true
        this.pingInMs = pingInMs
    }

    fun markConnectionAsClosed() {
        this.socket = null
        this.isReady = false
        this.afterEOSE = false
        this.closingTimeInSeconds = TimeUtils.now()
    }

    fun processNewRelayMessage(newMessage: String) {
        val msgArray = Event.gson.fromJson(newMessage, JsonElement::class.java).asJsonArray
        val type = msgArray[0].asString
        val channel = msgArray[1].asString

        when (type) {
            "EVENT" -> {
                val event = Event.fromJson(msgArray[2], Client.lenient)

                // Log.w("Relay", "Relay onEVENT $url, $channel")
                listeners.forEach {
                    it.onEvent(this@Relay, channel, event)
                    if (afterEOSE) {
                        it.onRelayStateChange(this@Relay, Type.EOSE, channel)
                    }
                }
            }
            "EOSE" -> listeners.forEach {
                afterEOSE = true
                // Log.w("Relay", "Relay onEOSE $url, $channel")
                it.onRelayStateChange(this@Relay, Type.EOSE, channel)
            }
            "NOTICE" -> listeners.forEach {
                Log.w("Relay", "Relay onNotice $url, $channel")
                it.onError(this@Relay, channel, Error("Relay sent notice: " + channel))
            }
            "OK" -> listeners.forEach {
                Log.w("Relay", "Relay on OK $url, ${msgArray[1].asString}, ${msgArray[2].asBoolean}, ${msgArray[3].asString}")
                it.onSendResponse(this@Relay, msgArray[1].asString, msgArray[2].asBoolean, msgArray[3].asString)
            }
            "AUTH" -> listeners.forEach {
                // Log.w("Relay", "Relay$url, ${msg[1].asString}")
                it.onAuth(this@Relay, msgArray[1].asString)
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
        // httpClient.dispatcher.executorService.shutdown()
        closingTimeInSeconds = TimeUtils.now()
        socket?.close(1000, "Normal close")
        socket = null
        isReady = false
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

    fun send(signedEvent: EventInterface) {
        checkNotInMainThread()

        if (signedEvent is RelayAuthEvent) {
            val event = """["AUTH",${signedEvent.toJson()}]"""
            socket?.send(event)
            eventUploadCounterInBytes += event.bytesUsedInMemory()
        }

        if (write) {
            if (signedEvent !is RelayAuthEvent) {
                val event = """["EVENT",${signedEvent.toJson()}]"""
                socket?.send(event)
                eventUploadCounterInBytes += event.bytesUsedInMemory()
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

    enum class Type {
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
        fun onRelayStateChange(relay: Relay, type: Type, channel: String?)
    }
}

fun String.bytesUsedInMemory(): Int {
    return (8 * ((((this.length) * 2) + 45) / 8))
}
