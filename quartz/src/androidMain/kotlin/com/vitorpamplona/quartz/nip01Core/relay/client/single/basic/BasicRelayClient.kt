/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.basic

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStat
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * A base implementation for a relay client that establishes and manages a WebSocket connection to a Nostr relay.
 * This class provides fundamental connection handling, message parsing, reconnection logic, and event dispatching.
 *
 * @property url The relay's normalized URL.
 * @property socketBuilder Provides the WebSocket instance for connection.
 * @property listener Interface to notify the application of relay events and errors.
 * @property stats Tracks operational statistics of the relay connection.
 * @property defaultOnConnect Callback executed after a successful connection, allowing subclasses to add initialization logic.
 *
 * Reconnection Strategy:
 * - Uses exponential backoff to retry connections, starting with [DELAY_TO_RECONNECT_IN_MSECS] (500ms).
 * - Doubles the delay between reconnection attempts in case of failure.
 *
 * Message Handling:
 * - Processes relay messages (e.g., `EVENT`, `EOSE`, `OK`, `AUTH`) and delegates to appropriate callbacks.
 * - Dispatches received events, notices, and subscription closures via the [listener].
 */
open class BasicRelayClient(
    override val url: NormalizedRelayUrl,
    val socketBuilder: WebsocketBuilder,
    val listener: IRelayClientListener,
    val stats: RelayStat = RelayStat(),
    val scope: CoroutineScope,
    val defaultOnConnect: (BasicRelayClient) -> Unit = { },
) : IRelayClient {
    companion object {
        // waits 3 minutes to reconnect once things fail
        const val DELAY_TO_RECONNECT_IN_SECS = 1
        const val EVENT_MESSAGE_PREFIX = "[\"${EventMessage.LABEL}\""
    }

    private val logTag = "Relay ${url.displayUrl()}"

    private var socket: WebSocket? = null
    private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    private var lastConnectTentativeInSeconds: Long = 0L // the beginning of time.
    private var delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS

    private var afterEOSEPerSubscription = mutableMapOf<String, Boolean>()

    private val authResponseWatcher = mutableMapOf<HexKey, Boolean>()
    private val authChallengesSent = mutableSetOf<String>()

    private var connectingMutex = AtomicBoolean()

    private val parser = ToClientParser()

    fun isConnectionStarted(): Boolean = socket != null

    override fun isConnected(): Boolean = socket != null && isReady

    override fun needsToReconnect() = socket?.needsReconnect() ?: true

    override fun connect() =
        connectAndRunOverride {
            defaultOnConnect(this)
        }

    override fun connectAndRunAfterSync(onConnected: () -> Unit) {
        connectAndRunOverride {
            defaultOnConnect(this)
            onConnected()
        }
    }

    fun connectAndRunOverride(onConnected: () -> Unit) {
        // If there is a connection, don't wait.
        if (connectingMutex.getAndSet(true)) {
            return
        }

        try {
            if (socket != null) {
                connectingMutex.set(false)
                return
            }

            Log.d(logTag, "Connecting...")

            lastConnectTentativeInSeconds = TimeUtils.now()

            socket = socketBuilder.build(url, MyWebsocketListener(onConnected))
            socket?.connect()
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Log.w(logTag, "Crash before connecting", e)
            stats.newError(e.message ?: "Error trying to connect: ${e.javaClass.simpleName}")

            markConnectionAsClosed()
        } finally {
            connectingMutex.set(false)
        }
    }

    inner class MyWebsocketListener(
        val onConnected: () -> Unit,
    ) : WebSocketListener {
        override fun onOpen(
            pingMillis: Long,
            compression: Boolean,
        ) {
            Log.d(logTag, "OnOpen (ping: ${pingMillis}ms${if (compression) ", using compression" else ""})")

            markConnectionAsReady(pingMillis, compression)

            scope.launch(Dispatchers.Default) {
                onConnected()
            }

            listener.onRelayStateChange(this@BasicRelayClient, RelayState.CONNECTED)
        }

        override fun onMessage(text: String) {
            if (text.startsWith(EVENT_MESSAGE_PREFIX)) {
                // defers the parsing of ["EVENTS" to avoid blocking the HTTP thread
                scope.launch(Dispatchers.Default) {
                    consumeIncomingCommand(text, onConnected)
                }
            } else {
                consumeIncomingCommand(text, onConnected)
            }
        }

        override fun onClosing(
            code: Int,
            reason: String,
        ) {
            Log.w(logTag, "OnClosing $code $reason")

            listener.onRelayStateChange(this@BasicRelayClient, RelayState.DISCONNECTING)
        }

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            Log.w(logTag, "OnClosed $reason")

            markConnectionAsClosed()
            listener.onRelayStateChange(this@BasicRelayClient, RelayState.DISCONNECTED)
        }

        override fun onFailure(
            t: Throwable,
            code: Int?,
            response: String?,
        ) {
            // socket is already closed
            // socket?.disconnect()

            if (socket == null) {
                // comes from the disconnect action.
                listener.onRelayStateChange(this@BasicRelayClient, RelayState.DISCONNECTED)
            } else {
                // checks if this is an actual failure. Closing the socket generates an onFailure as well.
                if (!(socket == null && (t.message == "Socket is closed" || t.message == "Socket closed"))) {
                    stats.newError(response ?: t.message ?: "onFailure event from server: ${t.javaClass.simpleName}")
                }

                // Failures disconnect the relay.
                markConnectionAsClosed()

                Log.w(logTag, "OnFailure $code $response ${t.message}")

                if (code == 403 || code == 502 || code == 503 || code == 402 || code == 410 || t.message == "SOCKS: Host unreachable") {
                    dontTryAgainForALongTime()
                }

                listener.onRelayStateChange(this@BasicRelayClient, RelayState.DISCONNECTED)
                listener.onError(this@BasicRelayClient, "", Error("WebSocket Failure. Response: $code $response. Exception: ${t.message}", t))
            }
        }
    }

    fun consumeIncomingCommand(
        text: String,
        onConnected: () -> Unit,
    ) {
        // Log.d(logTag, "Receiving: $text")
        stats.addBytesReceived(text.bytesUsedInMemory())

        try {
            when (val msg = parser.parse(text)) {
                is EventMessage -> processEvent(msg)
                is EoseMessage -> processEose(msg)
                is NoticeMessage -> processNotice(msg)
                is OkMessage -> processOk(msg, onConnected)
                is AuthMessage -> processAuth(msg)
                is NotifyMessage -> processNotify(msg)
                is ClosedMessage -> processClosed(msg)
                else -> processUnknownMessage(text)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            stats.newError("Error processing: $text")
            Log.e(logTag, "Error processing: $text")
            listener.onError(this@BasicRelayClient, "", Error("Error processing $text"))
        }
    }

    fun markConnectionAsReady(
        pingInMs: Long,
        usingCompression: Boolean,
    ) {
        this.resetEOSEStatuses()
        this.isReady = true
        this.usingCompression = usingCompression

        // resets any extra delays added during on offline state
        this.delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS

        stats.pingInMs = pingInMs
    }

    fun markConnectionAsClosed() {
        this.socket = null
        this.isReady = false
        this.usingCompression = false
        this.resetEOSEStatuses()
    }

    private fun processEvent(msg: EventMessage) {
        // Log.w(logTag, "Event ${msg.subId} ${msg.event.toJson()}")
        listener.onEvent(
            relay = this,
            subId = msg.subId,
            event = msg.event,
            arrivalTime = TimeUtils.now(),
            afterEOSE = afterEOSEPerSubscription[msg.subId] == true,
        )
    }

    private fun processEose(msg: EoseMessage) {
        // Log.w(logTag, "EOSE ${msg.subId}")
        afterEOSEPerSubscription[msg.subId] = true
        listener.onEOSE(this, msg.subId, TimeUtils.now())
    }

    private fun processNotice(msg: NoticeMessage) {
        Log.w(logTag, "Notice ${msg.message}")
        stats.newNotice(msg.message)
        listener.onError(this@BasicRelayClient, msg.message, Error("Relay sent notice: $msg.message"))
    }

    private fun processOk(
        msg: OkMessage,
        onConnected: () -> Unit,
    ) {
        Log.w(logTag, "OK: ${msg.eventId} ${msg.success} ${msg.message}")

        // if this is the OK of an auth event, renew all subscriptions and resend all outgoing events.
        if (authResponseWatcher.containsKey(msg.eventId)) {
            val wasAlreadyAuthenticated = authResponseWatcher[msg.eventId]
            authResponseWatcher.put(msg.eventId, msg.success)
            if (wasAlreadyAuthenticated != true && msg.success) {
                onConnected()
                listener.onAuthed(this@BasicRelayClient, msg.eventId, msg.success, msg.message)
            }
        }

        if (!msg.success) {
            stats.newNotice("Rejected event ${msg.eventId}: ${msg.message}")
        }

        listener.onSendResponse(this@BasicRelayClient, msg.eventId, msg.success, msg.message)
    }

    private fun processAuth(msg: AuthMessage) {
        Log.d(logTag, "Auth ${msg.challenge}")
        listener.onAuth(this@BasicRelayClient, msg.challenge)
    }

    private fun processNotify(msg: NotifyMessage) {
        // Log.w(logTag, "Notify $newMessage")
        listener.onNotify(this@BasicRelayClient, msg.message)
    }

    private fun processClosed(msg: ClosedMessage) {
        Log.w(logTag, "Relay Closed Subscription ${msg.subscriptionId} ${msg.message}")
        stats.newNotice("Subscription closed: ${msg.subscriptionId} ${msg.message}")
        afterEOSEPerSubscription[msg.subscriptionId] = false
        listener.onClosed(this@BasicRelayClient, msg.subscriptionId, msg.message)
    }

    private fun processUnknownMessage(newMessage: String) {
        stats.newError("Unsupported message: $newMessage")
        Log.w(logTag, "Unsupported message: $newMessage")
        listener.onError(this, "", Error("Unsupported message: $newMessage"))
    }

    override fun disconnect() {
        Log.d(logTag, "Disconnecting...")
        lastConnectTentativeInSeconds = 0L // this is not an error, so prepare to reconnect as soon as requested.
        delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS
        socket?.disconnect()
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

    override fun sendRequest(
        subId: String,
        filters: List<Filter>,
    ) {
        if (isConnectionStarted()) {
            if (isReady) {
                if (filters.isNotEmpty()) {
                    afterEOSEPerSubscription[subId] = false
                    writeToSocket(ReqCmd.Companion.toJson(subId, filters))
                }
            }
        } else {
            connectAndSyncFiltersIfDisconnected()
        }
    }

    override fun sendCount(
        subId: String,
        filters: List<Filter>,
    ) {
        if (isConnectionStarted()) {
            if (isReady) {
                if (filters.isNotEmpty()) {
                    afterEOSEPerSubscription[subId] = false
                    writeToSocket(CountCmd.Companion.toJson(subId, filters))
                }
            }
        } else {
            connectAndSyncFiltersIfDisconnected()
        }
    }

    override fun connectAndSyncFiltersIfDisconnected() {
        if (!isConnectionStarted() && !connectingMutex.get()) {
            // waits 60 seconds to reconnect after disconnected.
            if (TimeUtils.now() > lastConnectTentativeInSeconds + delayToConnectInSeconds) {
                upRelayDelayToConnect()
                connect()
            }
        }
    }

    fun upRelayDelayToConnect() {
        if (delayToConnectInSeconds < TimeUtils.FIVE_MINUTES) {
            delayToConnectInSeconds = delayToConnectInSeconds * 2
        }
    }

    fun dontTryAgainForALongTime() {
        delayToConnectInSeconds = TimeUtils.ONE_DAY
    }

    override fun send(event: Event) {
        listener.onBeforeSend(this@BasicRelayClient, event)

        if (event is RelayAuthEvent) {
            sendAuth(event)
        } else {
            sendEvent(event)
        }
    }

    override fun sendAuth(signedEvent: RelayAuthEvent) {
        val challenge = signedEvent.challenge()

        // only send replies to new challenges to avoid infinite loop:
        if (challenge != null && challenge !in authChallengesSent) {
            authResponseWatcher[signedEvent.id] = false
            authChallengesSent.add(challenge)
            writeToSocket(AuthCmd.Companion.toJson(signedEvent))
        }
    }

    override fun sendEvent(event: Event) {
        if (isConnectionStarted()) {
            if (isReady) {
                writeToSocket(EventCmd.Companion.toJson(event))
            }
        } else {
            connectAndSyncFiltersIfDisconnected()
        }
    }

    private fun writeToSocket(str: String) {
        if (socket == null) {
            listener.onError(
                this@BasicRelayClient,
                "",
                Error("Failed to send $str. Relay is not connected."),
            )
        }
        socket?.let {
            // Log.d(logTag, "Sending: $str")
            val result = it.send(str)
            listener.onSend(this@BasicRelayClient, str, result)
            stats.addBytesSent(str.bytesUsedInMemory())
        }
    }

    override fun close(subscriptionId: String) {
        // avoids sending closes for subscriptions that were never sent to this relay.
        if (afterEOSEPerSubscription.containsKey(subscriptionId)) {
            writeToSocket(CloseCmd.toJson(subscriptionId))
            afterEOSEPerSubscription[subscriptionId] = false
        }
    }
}
