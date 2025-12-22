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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient.Companion.DELAY_TO_RECONNECT_IN_SECS
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * A base implementation for a relay client that establishes and manages a WebSocket connection to a Nostr relay.
 * This class provides fundamental connection handling, message parsing, reconnection logic, and event dispatching.
 *
 * @property url The relay's normalized URL.
 * @property socketBuilder Provides the WebSocket instance for connection.
 * @property listener Interface to notify the application of relay events and errors.
 *
 * Reconnection Strategy:
 * - Uses exponential backoff to retry connections, starting with [DELAY_TO_RECONNECT_IN_SECS] (500ms).
 * - Doubles the delay between reconnection attempts in case of failure.
 *
 * Message Handling:
 * - Processes relay messages (e.g., `EVENT`, `EOSE`, `OK`, `AUTH`) and delegates to appropriate callbacks.
 * - Dispatches received events, notices, and subscription closures via the [listener].
 */
@OptIn(ExperimentalAtomicApi::class)
open class BasicRelayClient(
    override val url: NormalizedRelayUrl,
    val socketBuilder: WebsocketBuilder,
    val listener: IRelayClientListener,
) : IRelayClient {
    companion object {
        // minimum wait time to reconnect: 1 second
        const val DELAY_TO_RECONNECT_IN_SECS = 1
    }

    private var socket: WebSocket? = null

    // True if it has received the onOpen call from the socket.
    private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    // keeps increasing the delay to connect when errors happen.
    // This avoids the constant desire to connect when the server is
    // having trouble or offline.
    private var lastConnectTentativeInSeconds: Long = 0L // the beginning of time.
    private var delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS

    // Makes sure only one socket is open for each url
    private var connectingMutex = AtomicBoolean(false)

    fun isConnectionStarted(): Boolean = socket != null

    override fun isConnected(): Boolean = socket != null && isReady

    override fun needsToReconnect() = socket?.needsReconnect() ?: true

    override fun connect() {
        // If there is a connection, don't wait.
        if (connectingMutex.exchange(true)) {
            return
        }

        try {
            if (socket != null) {
                connectingMutex.store(false)
                return
            }

            listener.onConnecting(this)

            lastConnectTentativeInSeconds = TimeUtils.now()

            socket = socketBuilder.build(url, MyWebsocketListener())
            socket?.connect()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            listener.onCannotConnect(this, "Error when trying to connect: ${e.message}")
            listener.onDisconnected(this)
            dontTryAgainForALongTime()
            markConnectionAsClosed()
        } finally {
            connectingMutex.store(false)
        }
    }

    inner class MyWebsocketListener : WebSocketListener {
        override fun onOpen(
            pingMillis: Int,
            compression: Boolean,
        ) {
            markConnectionAsReady(compression)
            listener.onConnected(this@BasicRelayClient, pingMillis, compression)
        }

        override fun onMessage(text: String) {
            try {
                val msg = OptimizedJsonMapper.fromJsonToMessage(text)
                listener.onIncomingMessage(this@BasicRelayClient, text, msg)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // doesn't expose parsing errors to lib users as errors
                Log.e("BasicRelayClient", "Failure to parse message from Relay", e)
            }
        }

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            markConnectionAsClosed()
            listener.onDisconnected(this@BasicRelayClient)
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
                listener.onDisconnected(this@BasicRelayClient)
            } else {
                socket?.disconnect()

                val msg = t.message

                // checks if this is an actual failure. Closing the socket generates an onFailure as well.
                // ignore tor errors.
                if (msg == null ||
                    (
                        !msg.startsWith("failed to connect to /127.0.0.1") &&
                            msg != "Socket closed" &&
                            msg != "Socket is closed" &&
                            msg != "Cancelled"
                    )
                ) {
                    if (code != null || response != null) {
                        listener.onCannotConnect(this@BasicRelayClient, "Server Misconfigured. Response: $code $response. Exception: ${t.message}")
                    } else {
                        listener.onCannotConnect(this@BasicRelayClient, "WebSocket Failure: ${t.message}")
                    }
                } else {
                    // ignore local disconnect requests and tor errors
                    // listener.onServerError(this@BasicRelayClient, Error("Ignored Error $code $response. Exception: ${t.message}"))
                }

                // Failures disconnect the relay.
                markConnectionAsClosed()

                if (code != null || t.message?.endsWith("Host unreachable") == true) {
                    dontTryAgainForALongTime()
                }

                listener.onDisconnected(this@BasicRelayClient)
            }
        }
    }

    fun markConnectionAsReady(usingCompression: Boolean) {
        this.isReady = true
        this.usingCompression = usingCompression

        // resets any extra delays added during on offline state
        this.delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS
    }

    fun markConnectionAsClosed() {
        this.socket = null
        this.isReady = false
        this.usingCompression = false
    }

    override fun disconnect() {
        lastConnectTentativeInSeconds = 0L // this is not an error, so prepare to reconnect as soon as requested.
        delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS
        socket?.disconnect()
        socket = null
        isReady = false
        usingCompression = false
    }

    override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) {
        if (!isConnectionStarted() && !connectingMutex.load()) {
            // waits 60 seconds to reconnect after disconnected.
            if (ignoreRetryDelays || TimeUtils.now() > lastConnectTentativeInSeconds + delayToConnectInSeconds) {
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

    override fun sendOrConnectAndSync(cmd: Command) {
        if (isConnectionStarted()) {
            if (isReady && cmd.isValid()) {
                sendIfConnected(cmd)
            }
        } else {
            connectAndSyncFiltersIfDisconnected()
        }
    }

    override fun sendIfConnected(cmd: Command) {
        socket?.let {
            val msg = OptimizedJsonMapper.toJson(cmd)
            val success = it.send(msg)
            listener.onSent(this@BasicRelayClient, msg, cmd, success)
        }
    }
}
