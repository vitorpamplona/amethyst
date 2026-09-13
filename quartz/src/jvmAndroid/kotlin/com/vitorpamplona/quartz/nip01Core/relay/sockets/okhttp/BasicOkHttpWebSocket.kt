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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket as OkHttpWebSocket
import okhttp3.WebSocketListener as OkHttpWebSocketListener

class BasicOkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
) : WebSocket {
    companion object {
        // Exists to avoid exceptions stopping the coroutine
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e("BasicOkHttpWebSocket", "WebsocketListener Caught exception: ${throwable.message}", throwable)
            }
    }

    private val lock = Any()

    /**
     * The OkHttp socket this adapter currently owns, or null once the session has ended -- by the
     * relay closing it, by a network failure, or by [disconnect].
     *
     * OkHttp names the socket in every callback, and only the owned one may reach [out]. That is
     * what makes this adapter honour the [WebSocket.disconnect] contract: after [disconnect] the
     * slot is empty, so the failure OkHttp raises for its own `cancel()` on the reader thread,
     * or the `onClosed` its writer thread delivers once a close handshake completes, is dropped
     * instead of reaching a relay client that has already moved on to a new socket. The terminal
     * callbacks claim the slot under [lock], so a session ends with exactly one report however it
     * ends.
     */
    @Volatile private var socket: OkHttpWebSocket? = null

    override fun needsReconnect() = socket == null

    override fun connect() {
        val request = Request.Builder().url(url.url).build()

        val listener =
            object : OkHttpWebSocketListener() {
                val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)

                // UNLIMITED on purpose — do NOT bound this channel. The app
                // holds 2000+ relay connections; a bounded buffer under a
                // slow consumer would block OkHttp reader threads (thread
                // starvation at that connection count) and park the backlog
                // on the RELAY's outbound buffers via TCP backpressure —
                // infrastructure that isn't ours. We drain the remote as
                // fast as it can send and own the buffering; consumer speed
                // is handled downstream (CachingEventDecoder,
                // ParallelEventVerifier).
                val incomingMessages: Channel<String> = Channel(Channel.UNLIMITED)
                val job = // Launch a coroutine to process messages from the channel.
                    scope.launch {
                        for (message in incomingMessages) {
                            out.onMessage(message)
                        }
                    }

                /** Only the socket this adapter still owns may reach [out]. */
                private fun isOwned(webSocket: OkHttpWebSocket) = synchronized(lock) { socket === webSocket }

                /** Claims the session's single terminal report. False if it already ended. */
                private fun endSession(webSocket: OkHttpWebSocket): Boolean {
                    val ended = synchronized(lock) { (socket === webSocket).also { if (it) socket = null } }
                    if (ended) {
                        incomingMessages.close()
                        job.cancel()
                        scope.cancel()
                    }
                    return ended
                }

                override fun onOpen(
                    webSocket: OkHttpWebSocket,
                    response: Response,
                ) {
                    if (!isOwned(webSocket)) return
                    out.onOpen(
                        (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt(),
                        response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
                    )
                }

                override fun onMessage(
                    webSocket: OkHttpWebSocket,
                    text: String,
                ) {
                    if (!isOwned(webSocket)) return
                    // Never blocks (unlimited channel): the OkHttp reader
                    // thread must stay free to keep draining the socket.
                    incomingMessages.trySendBlocking(text)
                }

                override fun onClosing(
                    webSocket: OkHttpWebSocket,
                    code: Int,
                    reason: String,
                ) {
                    // The relay sent a CLOSE frame. OkHttp's contract (WebSocketListener KDoc,
                    // RealWebSocket, and its own WebSocketEcho recipe) is that onClosed fires
                    // only once BOTH peers have sent a close, and sending ours is the
                    // application's job. Left unanswered, the socket sits half-closed: no
                    // onClosed, no onFailure, send() still accepted and silently discarded, and
                    // a later cancel() is silent too -- so the relay client kept believing it
                    // was connected, with its REQs live, until OkHttp's 120s ping path finally
                    // failed up to two intervals later. Answering completes the handshake and
                    // OkHttp reports onClosed at once, whether or not the relay still holds the
                    // TCP session open.
                    //
                    // Always 1000 rather than echoing `code`: close() validates the code it is
                    // asked to write and throws on the reserved ones (1005, 1006, 1015), and a
                    // relay may send anything.
                    webSocket.close(1000, null)
                }

                override fun onClosed(
                    webSocket: OkHttpWebSocket,
                    code: Int,
                    reason: String,
                ) {
                    if (!endSession(webSocket)) return
                    out.onClosed(code, reason)
                }

                override fun onFailure(
                    webSocket: OkHttpWebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    if (!endSession(webSocket)) return
                    out.onFailure(t, response?.code, response?.message)
                }
            }

        // Under the lock so a callback racing this dial (an instant failure lands on another
        // thread) waits until the socket is owned, rather than being dropped as foreign.
        synchronized(lock) {
            socket = httpClient(url).newWebSocket(request, listener)
        }
    }

    override fun disconnect() {
        // Claim the session ourselves: OkHttp's cancel() raises no callback when no reader is
        // left to fail (the state a relay-initiated close leaves behind), and when it does the
        // failure arrives later on its own thread. The relay client needs the answer now.
        val closing = synchronized(lock) { socket?.also { socket = null } } ?: return
        closing.cancel()
        out.onClosed(1000, "client disconnect")
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = BasicOkHttpWebSocket(url, httpClient, out)
    }
}
