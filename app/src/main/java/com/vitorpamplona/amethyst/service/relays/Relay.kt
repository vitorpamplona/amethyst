package com.vitorpamplona.amethyst.service.relays

import android.util.Log
import com.google.gson.JsonElement
import java.util.Date
import com.vitorpamplona.amethyst.service.model.Event
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class FeedType {
    FOLLOWS, PUBLIC_CHATS, PRIVATE_DMS, GLOBAL
}

class Relay(
    var url: String,
    var read: Boolean = true,
    var write: Boolean = true,
    var activeTypes: Set<FeedType> = FeedType.values().toSet(),
) {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build();

    private var listeners = setOf<Listener>()
    private var socket: WebSocket? = null
    private var isReady: Boolean = false

    var eventDownloadCounter = 0
    var spamCounter = 0
    var eventUploadCounter = 0
    var errorCounter = 0
    var ping: Long? = null

    var closingTime = 0L

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
        if (socket != null) return

        try {
            val request = Request.Builder().url(url.trim()).build()
            val listener = object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isReady = true
                    ping = response.receivedResponseAtMillis - response.sentRequestAtMillis

                    // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
                    // Sends everything.
                    Client.allSubscriptions().forEach {
                        sendFilter(requestId = it)
                    }
                    listeners.forEach { it.onRelayStateChange(this@Relay, Type.CONNECT, null) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = Event.gson.fromJson(text, JsonElement::class.java).asJsonArray
                        val type = msg[0].asString
                        val channel = msg[1].asString
                        when (type) {
                            "EVENT" -> {
                                //Log.w("Relay", "Relay onEVENT $url, $channel")
                                eventDownloadCounter++
                                val event = Event.fromJson(msg[2], Client.lenient)
                                listeners.forEach { it.onEvent(this@Relay, channel, event) }
                            }
                            "EOSE" -> listeners.forEach {
                                //Log.w("Relay", "Relay onEOSE $url, $channel")
                                it.onRelayStateChange(this@Relay, Type.EOSE, channel)
                            }
                            "NOTICE" -> listeners.forEach {
                                //Log.w("Relay", "Relay onNotice $url, $channel")
                                // "channel" being the second string in the string array ...
                                it.onError(this@Relay, channel, Error("Relay sent notice: " + channel))
                            }
                            "OK" -> listeners.forEach {
                                //Log.w("Relay", "Relay onOK $url, $channel")
                                it.onSendResponse(this@Relay, msg[1].asString, msg[2].asBoolean, msg[3].asString)
                            }
                            else -> listeners.forEach {
                                //Log.w("Relay", "Relay something else $url, $channel")
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
                    listeners.forEach { it.onRelayStateChange(
                        this@Relay,
                        Type.DISCONNECTING,
                        null
                    ) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                    isReady = false
                    closingTime = Date().time / 1000
                    listeners.forEach { it.onRelayStateChange(this@Relay, Type.DISCONNECT, null) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    errorCounter++

                    socket?.close(1000, "Normal close")
                    // Failures disconnect the relay.
                    socket = null
                    isReady = false
                    closingTime = Date().time / 1000

                    Log.w("Relay", "Relay onFailure $url, ${response?.message} ${response}")
                    t.printStackTrace()
                    listeners.forEach {
                        it.onError(this@Relay, "", Error("WebSocket Failure. Response: ${response}. Exception: ${t.message}", t))
                    }
                }
            }

            socket = httpClient.newWebSocket(request, listener)
        } catch (e: Exception) {
            errorCounter++
            isReady = false
            closingTime = Date().time / 1000
            Log.e("Relay", "Relay Invalid $url")
            e.printStackTrace()
        }
    }

    fun disconnect() {
        //httpClient.dispatcher.executorService.shutdown()
        closingTime = Date().time / 1000
        socket?.close(1000, "Normal close")
        socket = null
        isReady = false
    }

    fun sendFilter(requestId: String) {
        if (read) {
            if (isConnected()) {
                if (isReady) {
                    val filters = Client.getSubscriptionFilters(requestId).filter { activeTypes.intersect(it.types).isNotEmpty() }
                    if (filters.isNotEmpty()) {
                        val request =
                            """["REQ","$requestId",${filters.take(10).joinToString(",") { it.filter.toJson() }}]"""
                        //println("FILTERSSENT ${url} ${request}")
                        socket?.send(request)
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
        if (socket == null) {
            // waits 60 seconds to reconnect after disconnected.
            if (Date().time / 1000 > closingTime + 60) {
                //println("sendfilter Only if Disconnected ${url} ")
                requestAndWatch()
            }
        }
    }

    fun send(signedEvent: Event) {
        if (write) {
            socket?.send("""["EVENT",${signedEvent.toJson()}]""")
            eventUploadCounter++
        }
    }

    fun close(subscriptionId: String){
        socket?.send("""["CLOSE","$subscriptionId"]""")
    }

    fun isSameRelayConfig(other: Relay): Boolean {
        return url == other.url
            && write == other.write
            && read == other.read
            && activeTypes == other.activeTypes
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
        /**
         * Connected to or disconnected from a relay
         *
         * @param type is 0 for disconnect and 1 for connect
         */
        fun onRelayStateChange(relay: Relay, type: Type, channel: String?)
    }
}
