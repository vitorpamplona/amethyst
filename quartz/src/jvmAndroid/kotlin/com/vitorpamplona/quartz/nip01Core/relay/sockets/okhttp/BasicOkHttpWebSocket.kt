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
import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.WebSocket as OkHttpWebSocket
import okhttp3.WebSocketListener as OkHttpWebSocketListener

class BasicOkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
) : WebSocket {
    companion object {
        private const val OPEN = 0
        private const val DRAINING = 1
        private const val CLOSED = 2

        // Exists to avoid exceptions stopping the coroutine
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e("BasicOkHttpWebSocket", "WebsocketListener Caught exception: ${throwable.message}", throwable)
            }
    }

    @Volatile private var socket: OkHttpWebSocket? = null

    /**
     * Where this session is: [OPEN] (dialing, or up), [DRAINING] (the relay ended it, and the
     * messages it had already sent are still being delivered), [CLOSED] (its one terminal callback
     * was reported). Moved only by compare-and-set, so a [disconnect] racing the relay's own end
     * still yields exactly one report.
     *
     * One adapter is one session: the relay client builds a fresh one per dial, and OkHttp binds
     * exactly one socket to the listener created in [connect], so anything that reaches that
     * listener is from this session by construction. The only question a callback has to ask is
     * whether the session already ended -- which is what keeps the [WebSocket.disconnect] contract:
     * after [disconnect] the failure OkHttp raises for its own `cancel()` on the reader thread, or
     * the `onClosed` its writer thread delivers once a close handshake completes, is dropped rather
     * than reaching a relay client that has already moved on.
     */
    private val state = AtomicInteger(OPEN)

    /** The channel and coroutine delivering this session's messages, once [connect] made them. */
    @Volatile private var delivery: Delivery? = null

    /**
     * True from the start of [connect] until OkHttp hands back its socket. A dial in flight is not
     * a dead session: reporting it as one let a concurrent reconnect pass tear it down and dial
     * again, and the first dial, never ended, opened as well. Measured with amy under load: two
     * connections to one relay, the REQ sent twice and every message of the page delivered twice.
     */
    @Volatile private var dialing = false

    /** The client this session was dialed with, to notice when [httpClient] would now dial differently. */
    @Volatile private var dialedWith: OkHttpClient? = null

    /**
     * Not while [DRAINING]: the relay client still holds this session until its end is reported,
     * and a reconnect pass tearing it down would drop the messages it is still delivering. An open
     * session needs one when [httpClient] would now dial it differently ([clientChanged]).
     */
    override fun needsReconnect() =
        when (state.get()) {
            OPEN -> if (socket == null) !dialing else clientChanged()
            DRAINING -> false
            else -> true
        }

    /**
     * Whether the client [httpClient] returns now differs from the one this session was dialed
     * with in its proxy or timeouts: a relay moved to or off Tor, or a changed timeout, takes
     * effect by reconnecting. A constant client never differs, so this costs one lambda call.
     */
    private fun clientChanged(): Boolean {
        val used = dialedWith ?: return false
        val current = httpClient(url)
        if (current === used) return false
        return used.proxy != current.proxy ||
            used.readTimeoutMillis != current.readTimeoutMillis ||
            used.writeTimeoutMillis != current.writeTimeoutMillis ||
            used.connectTimeoutMillis != current.connectTimeoutMillis ||
            used.callTimeoutMillis != current.callTimeoutMillis
    }

    /** Stops delivering: drops what is queued and ends the coroutine. */
    private fun endDelivery() {
        delivery?.let {
            it.incoming.cancel()
            it.scope.cancel()
        }
    }

    private class Delivery(
        val scope: CoroutineScope,
        // Strings are messages; a [SessionEnd] is the last item, the session's terminal report.
        val incoming: Channel<Any>,
    )

    private class SessionEnd(
        val report: () -> Unit,
    )

    override fun connect() {
        if (socket != null || dialing || state.get() != OPEN) return
        dialing = true

        val request = Request.Builder().url(url.url).build()

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
        val incoming = Channel<Any>(Channel.UNLIMITED)
        delivery = Delivery(scope, incoming)
        scope.launch {
            for (item in incoming) {
                if (item is SessionEnd) {
                    if (state.compareAndSet(DRAINING, CLOSED)) item.report()
                    break
                }
                // A listener that throws must not end delivery: the session's end is queued behind
                // this message, and without it the relay client would never learn the session closed.
                try {
                    out.onMessage(item as String)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e("BasicOkHttpWebSocket", "Listener failed on a message from ${url.url}", e)
                }
            }
            scope.cancel()
        }

        val listener =
            object : OkHttpWebSocketListener() {
                /**
                 * The relay ended the session. Messages it had already sent are delivered first and
                 * then the end, from the one coroutine that delivers messages, so the relay client
                 * sees everything the relay sent, in order, before it learns the session is over.
                 * The delivering coroutine used to be cancelled here, dropping what was queued (a
                 * slow consumer's backlog, often most of a page) and cancelling the message in hand
                 * where it suspended.
                 */
                private fun endedByRelay(report: () -> Unit) {
                    if (!state.compareAndSet(OPEN, DRAINING)) return
                    socket = null
                    incoming.trySend(SessionEnd(report))
                    incoming.close()
                }

                override fun onOpen(
                    webSocket: OkHttpWebSocket,
                    response: Response,
                ) {
                    if (state.get() != OPEN) return
                    // OkHttp dials on its own thread, so this can run before newWebSocket()
                    // returns to connect() and stores the socket. The relay client sends its
                    // REQs from onOpen: without this they went to a null socket and were
                    // dropped, and the relay, never asked, never answered.
                    socket = webSocket
                    out.onOpen(
                        (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt(),
                        response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
                    )
                }

                override fun onMessage(
                    webSocket: OkHttpWebSocket,
                    text: String,
                ) {
                    if (state.get() != OPEN) return
                    // Never blocks (unlimited channel): the OkHttp reader
                    // thread must stay free to keep draining the socket.
                    incoming.trySendBlocking(text)
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
                ) = endedByRelay { out.onClosed(code, reason) }

                override fun onFailure(
                    webSocket: OkHttpWebSocket,
                    t: Throwable,
                    response: Response?,
                ) = endedByRelay { out.onFailure(t, response?.code, response?.message) }
            }

        try {
            val client = httpClient(url)
            dialedWith = client
            val dialed = client.newWebSocket(request, listener)
            socket = dialed
            // A disconnect() that landed while dialing ended the session but had no socket to
            // cancel yet: cancel it now, or it opens and lives on beside the relay client's next one.
            // It may also have run before [delivery] was set, leaving that coroutine to end here.
            if (state.get() != OPEN) {
                socket = null
                dialed.cancel()
                endDelivery()
            }
        } catch (e: Throwable) {
            // No session to deliver for: the relay client reports the failed dial itself and
            // drops this adapter without calling disconnect(), so the coroutine would wait forever.
            endDelivery()
            throw e
        } finally {
            dialing = false
        }
    }

    override fun disconnect() {
        // Claim the session ourselves: OkHttp's cancel() raises no callback when no reader is
        // left to fail (the state a relay-initiated close leaves behind), and when it does the
        // failure arrives later on its own thread. The relay client needs the answer now.
        //
        // Claimed even with no socket yet: a dial still in flight must not open after this
        // (connect() cancels it once OkHttp returns it), and its callbacks must find the session
        // ended. Nothing was open, so there is nothing to report.
        val previous = state.getAndSet(CLOSED)
        if (previous == CLOSED) return
        val closing = socket
        socket = null
        // Nothing reaches the listener after this, not even messages already queued: the relay
        // client may dial a new socket right after this call.
        endDelivery()
        closing?.cancel()
        // A session the relay already ended, still delivering, ends here instead: its own end
        // is dropped with the rest of the queue, so it is still reported exactly once.
        if (closing != null || previous == DRAINING) out.onClosed(1000, "client disconnect")
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
        val canDial: (NormalizedRelayUrl) -> Boolean,
    ) : WebsocketBuilder {
        // Its own constructor rather than a default: `Builder { okHttp }` keeps binding its lambda
        // to httpClient instead of to a trailing canDial.
        constructor(httpClient: (NormalizedRelayUrl) -> OkHttpClient) : this(httpClient, { true })

        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = BasicOkHttpWebSocket(url, httpClient, out)

        // Gates the dial: false skips it (the apps hold a Tor-routed relay until Tor is ready).
        override fun canConnect(url: NormalizedRelayUrl) = canDial(url)
    }
}
