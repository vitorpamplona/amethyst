package com.vitorpamplona.amethyst.service.relays

import android.util.Log
import com.google.gson.JsonElement
import com.vitorpamplona.amethyst.BuildConfig
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
import java.util.Date

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
    val seconds = if (proxy != null) 20L else 10L
    val duration = Duration.ofSeconds(seconds)

    private val httpClient = OkHttpClient.Builder()
        .proxy(proxy)
        .readTimeout(duration)
        .connectTimeout(duration)
        .writeTimeout(duration)
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
    var ping: Long? = null

    var closingTime = 0L

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

    @Synchronized
    fun requestAndWatch() {
        checkNotInMainThread()
        requestAndWatch {
            checkNotInMainThread()

            // Sends everything.
            Client.allSubscriptions().forEach {
                sendFilter(requestId = it)
            }
        }
    }

    @Synchronized
    fun requestAndWatch(onConnected: (Relay) -> Unit) {
        checkNotInMainThread()
        if (socket != null) return

        try {
            val request = Request.Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(url.trim())
                .build()
            val listener = object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    checkNotInMainThread()

                    afterEOSE = false
                    isReady = true
                    ping = response.receivedResponseAtMillis - response.sentRequestAtMillis
                    // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
                    onConnected(this@Relay)

                    listeners.forEach { it.onRelayStateChange(this@Relay, Type.CONNECT, null) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    checkNotInMainThread()

                    eventDownloadCounterInBytes += text.bytesUsedInMemory()

                    try {
                        val msg = Event.gson.fromJson(text, JsonElement::class.java).asJsonArray
                        val type = msg[0].asString
                        val channel = msg[1].asString

                        when (type) {
                            "EVENT" -> {
                                val event = Event.fromJson(msg[2], Client.lenient)

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
                                Log.w("Relay", "Relay on OK $url, ${msg[1].asString}, ${msg[2].asBoolean}, ${msg[3].asString}")
                                it.onSendResponse(this@Relay, msg[1].asString, msg[2].asBoolean, msg[3].asString)
                            }
                            "AUTH" -> listeners.forEach {
                                // Log.w("Relay", "Relay$url, ${msg[1].asString}")
                                it.onAuth(this@Relay, msg[1].asString)
                            }
                            else -> listeners.forEach {
                                // Log.w("Relay", "Relay something else $url, $channel")
                                it.onError(
                                    this@Relay,
                                    channel,
                                    Error("Unknown type $type on channel $channel. Msg was $text")
                                )
                            }
                        }
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

                    socket = null
                    isReady = false
                    afterEOSE = false
                    closingTime = Date().time / 1000
                    listeners.forEach { it.onRelayStateChange(this@Relay, Type.DISCONNECT, null) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    checkNotInMainThread()

                    errorCounter++

                    socket?.close(1000, "Normal close")
                    // Failures disconnect the relay.
                    socket = null
                    isReady = false
                    afterEOSE = false
                    closingTime = Date().time / 1000

                    Log.w("Relay", "Relay onFailure $url, ${response?.message} $response")
                    t.printStackTrace()
                    listeners.forEach {
                        it.onError(this@Relay, "", Error("WebSocket Failure. Response: $response. Exception: ${t.message}", t))
                    }
                }
            }

            socket = httpClient.newWebSocket(request, listener)
        } catch (e: Exception) {
            errorCounter++
            isReady = false
            afterEOSE = false
            closingTime = Date().time / 1000
            Log.e("Relay", "Relay Invalid $url")
            e.printStackTrace()
        }
    }

    fun disconnect() {
        // httpClient.dispatcher.executorService.shutdown()
        closingTime = Date().time / 1000
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
                if (Date().time / 1000 > closingTime + 60) {
                    // sends all filters after connection is successful.
                    requestAndWatch()
                }
            }
        }
    }

    fun sendFilterOnlyIfDisconnected() {
        checkNotInMainThread()

        if (socket == null) {
            // waits 60 seconds to reconnect after disconnected.
            if (Date().time / 1000 > closingTime + 60) {
                // println("sendfilter Only if Disconnected ${url} ")
                requestAndWatch()
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
