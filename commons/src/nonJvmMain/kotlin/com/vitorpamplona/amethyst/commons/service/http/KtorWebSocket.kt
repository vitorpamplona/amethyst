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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * A relay socket on a Ktor [HttpClient] with the WebSockets plugin installed: the off-JVM
 * counterpart of quartz's `BasicOkHttpWebSocket`, with the same contract.
 *
 * - [connect] returns at once and dials in [scope].
 * - [WebSocketListener.onMessage] runs for one frame at a time, in the order the relay sent them.
 * - [send] queues the frame and returns false only when no session is open.
 * - [disconnect] reports `onClosed(1000, "client disconnect")`, and nothing more comes from that
 *   session.
 *
 * It belongs next to `BasicOkHttpWebSocket` in quartz once quartz gains a non-JVM source set;
 * it lives here until then.
 */
class KtorWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (NormalizedRelayUrl) -> HttpClient,
    val out: WebSocketListener,
    private val scope: CoroutineScope,
) : WebSocket {
    /** The running session: its job and the queue of frames waiting to go out. */
    private class Session(
        val job: Job,
        val outgoing: Channel<String>,
    ) {
        @Volatile var open = false
    }

    @Volatile private var session: Session? = null

    override fun needsReconnect(): Boolean = session?.job?.isActive != true

    override fun connect() {
        if (session?.job?.isActive == true) return

        val outgoing = Channel<String>(Channel.UNLIMITED)
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                run(outgoing)
            }
        session = Session(job, outgoing)
        job.start()
    }

    /** Whether [job] still belongs to the session on record: a replaced or closed one reports nothing. */
    private fun isCurrent(job: Job?) = job != null && session?.job === job

    private suspend fun run(outgoing: Channel<String>) {
        val me = currentCoroutineContext()[Job]
        val started = TimeSource.Monotonic.markNow()
        try {
            httpClient(url).webSocket(urlString = url.url) {
                session?.takeIf { it.job === me }?.open = true
                if (isCurrent(me)) out.onOpen(started.elapsedNow().inWholeMilliseconds.toInt(), false)

                coroutineScope {
                    // Frames go out in the order they were queued.
                    val writer =
                        launch {
                            for (msg in outgoing) send(Frame.Text(msg))
                        }
                    for (frame in incoming) {
                        if (frame is Frame.Text && isCurrent(me)) out.onMessage(frame.readText())
                    }
                    writer.cancel()
                }

                val reason = closeReason.await()
                if (isCurrent(me)) {
                    out.onClosed(
                        reason?.code?.toInt() ?: CloseReason.Codes.NORMAL.code
                            .toInt(),
                        reason?.message.orEmpty(),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG) { "${url.url} failed: ${e.message}" }
            if (isCurrent(me)) out.onFailure(e, (e as? ResponseException)?.response?.status?.value, e.message)
        } finally {
            outgoing.close()
            if (isCurrent(me)) session = null
        }
    }

    override fun disconnect() {
        val current = session ?: return
        session = null
        current.outgoing.close()
        current.job.cancel()
        // As BasicOkHttpWebSocket does: the relay client learns of its own disconnect here, and
        // nothing more arrives from that session.
        out.onClosed(
            CloseReason.Codes.NORMAL.code
                .toInt(),
            "client disconnect",
        )
    }

    override fun send(msg: String): Boolean {
        val current = session ?: return false
        if (!current.open) return false
        return current.outgoing.trySend(msg).isSuccess
    }

    /** Builds [KtorWebSocket]s that dial with the client [httpClient] picks for each relay. */
    class Builder(
        private val httpClient: (NormalizedRelayUrl) -> HttpClient,
        private val scope: CoroutineScope,
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = KtorWebSocket(url, httpClient, out, scope)
    }

    companion object {
        private const val TAG = "KtorWebSocket"
    }
}
