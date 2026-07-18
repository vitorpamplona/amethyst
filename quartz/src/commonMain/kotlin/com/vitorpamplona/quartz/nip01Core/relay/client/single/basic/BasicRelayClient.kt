/*
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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient.Companion.DELAY_TO_RECONNECT_IN_SECS
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDecoder
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.concurrent.Volatile
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
 * - Uses exponential backoff to retry connections, starting with [DELAY_TO_RECONNECT_IN_SECS].
 * - Doubles the delay between reconnection attempts in case of failure.
 * - The backoff only resets after a connection stays open for at least
 *   [STABLE_CONNECTION_IN_SECS], so relays that accept the handshake but
 *   immediately drop the socket keep backing off instead of reconnecting in a loop.
 *
 * Message Handling:
 * - Processes relay messages (e.g., `EVENT`, `EOSE`, `OK`, `AUTH`) and delegates to appropriate callbacks.
 * - Dispatches received events, notices, and subscription closures via the [listener].
 */
@OptIn(ExperimentalAtomicApi::class)
open class BasicRelayClient(
    override val url: NormalizedRelayUrl,
    val socketBuilder: WebsocketBuilder,
    val listener: RelayConnectionListener,
    val nowInSeconds: () -> Long = TimeUtils::now,
    /**
     * Per-frame decode strategy. The default fully parses every frame; pass a
     * shared [com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder]
     * (one instance across the whole pool) to skip re-parsing duplicate EVENT
     * frames that other relays/subscriptions already delivered.
     */
    val decoder: MessageDecoder = MessageDecoder.Default,
) : IRelayClient {
    companion object {
        // minimum wait time to reconnect: 1 second
        const val DELAY_TO_RECONNECT_IN_SECS = 1

        // a connection must survive this long before a disconnect resets the
        // reconnect backoff. Relays that accept the handshake and then
        // immediately drop the socket would otherwise reset the backoff on
        // every onOpen and reconnect in a tight ~3s loop forever.
        const val STABLE_CONNECTION_IN_SECS = TimeUtils.ONE_MINUTE
    }

    private var socket: WebSocket? = null

    // True if it has received the onOpen call from the socket.
    // @Volatile: written on the serialized socket-callback thread, read from the
    // relay-pool/timer thread (see RelayLoadingCursors for the same pattern).
    @Volatile private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    // keeps increasing the delay to connect when errors happen.
    // This avoids the constant desire to connect when the server is
    // having trouble or offline. @Volatile: read on the pool thread in
    // connectAndSyncFiltersIfDisconnected, written on the socket-callback thread.
    @Volatile private var lastConnectTentativeInSeconds: Long = 0L // the beginning of time.

    @Volatile private var delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS

    // when the current connection became ready; used to decide if the
    // connection was stable enough to reset the backoff on disconnect.
    @Volatile private var connectedAtInSeconds: Long = 0L

    // Makes sure only one socket is open for each url
    private var connectingMutex = AtomicBoolean(false)

    fun isConnectionStarted(): Boolean = socket != null

    override fun isConnected(): Boolean = socket != null && isReady

    override fun needsToReconnect() = socket?.needsReconnect() ?: true

    override fun connect() {
        // Transport gate: skip the dial when the builder reports this relay's transport
        // isn't ready (e.g. a Tor-routed relay while Tor's SOCKS port isn't up). Returning
        // here before the mutex/socket/onConnecting means no doomed dial and no backoff
        // growth; a later reconnect pass (fired when the transport becomes ready) will dial.
        if (!socketBuilder.canConnect(url)) return

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

            lastConnectTentativeInSeconds = nowInSeconds()

            socket = socketBuilder.build(url, MyWebsocketListener())
            socket?.connect()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val typeName = e::class.simpleName
            val detail = e.message?.let { "$it ($typeName)" } ?: (typeName ?: "unknown error")
            listener.onCannotConnect(this, "Error when trying to connect: $detail")
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
                val msg = decoder.decode(text)
                listener.onIncomingMessage(this@BasicRelayClient, text, msg)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // doesn't expose parsing errors to lib users as errors
                Log.e("BasicRelayClient", "Failure to parse message from ${url.url}: $text", e)
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

                // suppression rules below must match the raw message; displayMsg is for listener output only.
                // Always include the exception's class name: message text is
                // localized and inconsistent across platforms, but the type
                // (SocketTimeoutException / UnknownHostException / SSLHandshakeException /
                // ConnectException …) is stable and lets listeners classify a failure
                // reliably — a busy relay (timeout) vs a dead one (bad domain / TLS).
                val msg = t.message
                val typeName = t::class.simpleName
                val displayMsg = if (msg != null) "$msg ($typeName)" else (typeName ?: "unknown error")

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
                        listener.onCannotConnect(this@BasicRelayClient, "Server Misconfigured. Response: $code $response. Exception: $displayMsg")
                    } else {
                        listener.onCannotConnect(this@BasicRelayClient, "WebSocket Failure: $displayMsg")
                    }
                } else {
                    // ignore local disconnect requests and tor errors
                    // listener.onServerError(this@BasicRelayClient, Error("Ignored Error $code $response. Exception: ${t.message}"))
                }

                // Failures disconnect the relay.
                markConnectionAsClosed()

                // A name that does not resolve is not a busy relay, it is very likely a relay
                // whose domain is gone (lapsed registration, decommissioned host). Doubling
                // from 1s wastes ~10 dials climbing to the ceiling it was always going to
                // reach, so go there directly — same treatment as an HTTP upgrade rejection.
                //
                // Matching on the type name rather than the message because message text is
                // localized and inconsistent across platforms ("Unable to resolve host ..."
                // on Android, "nodename nor servname provided" on others), while the class is
                // stable. This is why onCannotConnect appends it, see above.
                //
                // Safe to be this eager only because the verdict is cheap to revisit: a DNS
                // answer is a property of the network, not of the relay (a captive portal or a
                // filtering resolver forges NXDOMAIN; Tor resolves at the exit node instead),
                // and both a network-identity change and a transport/Tor change clear the
                // backoff outright — see IRelayClient.resetBackoff.
                val unresolvedHost = typeName == "UnknownHostException"

                if (code != null || unresolvedHost || t.message?.endsWith("Host unreachable") == true) {
                    dontTryAgainForALongTime()
                }

                listener.onDisconnected(this@BasicRelayClient)
            }
        }
    }

    fun markConnectionAsReady(usingCompression: Boolean) {
        this.isReady = true
        this.usingCompression = usingCompression
        this.connectedAtInSeconds = nowInSeconds()

        // The backoff delay is NOT reset here: a relay that accepts the
        // handshake and then immediately fails would defeat the exponential
        // backoff on every cycle. It resets in markConnectionAsClosed once
        // the session proves stable (see STABLE_CONNECTION_IN_SECS).
    }

    fun markConnectionAsClosed() {
        // resets any extra delays added while offline, but only if the
        // session was stable; flapping relays keep their growing backoff.
        if (isReady && nowInSeconds() - connectedAtInSeconds >= STABLE_CONNECTION_IN_SECS) {
            this.delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS
        }

        this.socket = null
        this.isReady = false
        this.usingCompression = false
    }

    override fun disconnect() {
        lastConnectTentativeInSeconds = 0L // this is not an error, so prepare to reconnect as soon as requested.
        delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS
        connectedAtInSeconds = 0L
        socket?.disconnect()
        socket = null
        isReady = false
        usingCompression = false
    }

    override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) {
        if (connectingMutex.load()) return

        if (isConnectionStarted()) {
            // A socket already exists. Normally leave it alone, but if it was opened for the wrong
            // transport — e.g. the relay's Tor classification changed since (a clearnet relay now routed
            // through the Tor proxy, or vice-versa) — tear it down and rebuild on the current transport.
            // Without this an in-flight dial on the wrong transport can never be preempted: a still-
            // connecting socket leaves isConnectionStarted() true (so the old guard skipped it) yet
            // isConnected() false (so RelayPool.reconnectIfNeedsTo's connected-relay branch never runs),
            // and the request blocks until that hung dial finally times out.
            if (socket?.needsReconnect() == true) {
                disconnect()
                connect()
            }
            return
        }

        // waits 60 seconds to reconnect after disconnected.
        if (ignoreRetryDelays || nowInSeconds() > lastConnectTentativeInSeconds + delayToConnectInSeconds) {
            upRelayDelayToConnect()
            connect()
        }
    }

    override fun resetBackoff() {
        // Same two fields disconnect() clears, but deliberately not the socket: a relay
        // that is currently connected must keep its session. This only forgives the wait
        // a disconnected relay would otherwise serve out.
        delayToConnectInSeconds = DELAY_TO_RECONNECT_IN_SECS
        lastConnectTentativeInSeconds = 0L
    }

    fun upRelayDelayToConnect() {
        if (delayToConnectInSeconds < TimeUtils.FIVE_MINUTES) {
            delayToConnectInSeconds = delayToConnectInSeconds * 2
        }
    }

    fun dontTryAgainForALongTime() {
        delayToConnectInSeconds = TimeUtils.FIVE_MINUTES
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
