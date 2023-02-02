package com.vitorpamplona.amethyst.service.relays

import android.util.Log
import com.google.gson.JsonElement
import java.util.Date
import nostr.postr.events.Event
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class Relay(
  var url: String,
  var read: Boolean = true,
  var write: Boolean = true
) {
    private val httpClient = OkHttpClient()
    private var listeners = setOf<Listener>()
    private var socket: WebSocket? = null

    var eventDownloadCounter = 0
    var eventUploadCounter = 0
    var errorCounter = 0

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

    fun requestAndWatch() {
        try {
            val request = Request.Builder().url(url.trim()).build()
            val listener = object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
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
                                eventDownloadCounter++
                                val event = Event.fromJson(msg[2], Client.lenient)
                                listeners.forEach { it.onEvent(this@Relay, channel, event) }
                            }
                            "EOSE" -> listeners.forEach {
                                it.onRelayStateChange(this@Relay, Type.EOSE, channel)
                            }
                            "NOTICE" -> listeners.forEach {
                                // "channel" being the second string in the string array ...
                                it.onError(this@Relay, channel, Error("Relay sent notice: $channel"))
                            }
                            "OK" -> listeners.forEach {
                                it.onSendResponse(this@Relay, msg[1].asString, msg[2].asBoolean, msg[3].asString)
                            }
                            else -> listeners.forEach {
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
                    closingTime = Date().time / 1000
                    listeners.forEach { it.onRelayStateChange(this@Relay, Type.DISCONNECT, null) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    errorCounter++

                    socket?.close(1000, "Normal close")
                    // Failures disconnect the relay.
                    socket = null
                    closingTime = Date().time / 1000

                    Log.w("Relay", "Relay onFailure ${url}, ${response?.message}")
                    //t.printStackTrace()
                    listeners.forEach {
                        it.onError(this@Relay, "", Error("WebSocket Failure. Response: ${response}. Exception: ${t.message}", t))
                    }
                }
            }

            socket = httpClient.newWebSocket(request, listener)
        } catch (e: Exception) {
            closingTime = Date().time / 1000
            Log.e("Relay", "Relay Invalid ${url}")
            e.printStackTrace()
        }
    }

    fun disconnect() {
        //httpClient.dispatcher.executorService.shutdown()
        closingTime = Date().time / 1000
        socket?.close(1000, "Normal close")
        socket = null
    }

    fun sendFilter(requestId: String) {
        if (read) {
            if (socket == null) {
                // waits 10 seconds
                if (Date().time / 1000 > closingTime + 10) {
                    requestAndWatch()
                }
            } else {
                val filters = Client.getSubscriptionFilters(requestId)
                if (filters.isNotEmpty()) {
                    val request =
                        """["REQ","$requestId",${filters.take(10).joinToString(",") { it.toJson() }}]"""
                    //println("FILTERSSENT ${url} " + """["REQ","$requestId",${filters.joinToString(",") { it.toJson() }}]""")
                    socket?.send(request)
                }
            }
        }
    }

    fun sendFilterOnlyIfDisconnected() {
        if (socket == null) {
            requestAndWatch()
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
