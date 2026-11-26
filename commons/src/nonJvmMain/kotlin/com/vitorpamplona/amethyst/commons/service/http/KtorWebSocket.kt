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
import io.ktor.client.request.header
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
 * Compression: the engine may offer permessage-deflate. A relay whose compressed frames the engine
 * cannot read ([Compression.isFailure] says which errors those are) is dialed again without it,
 * for as long as the app runs.
 *
 * It belongs next to `BasicOkHttpWebSocket` in quartz once quartz gains a non-JVM source set;
 * it lives here until then.
 */
class KtorWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (NormalizedRelayUrl) -> HttpClient,
    val out: WebSocketListener,
    private val scope: CoroutineScope,
    private val compression: Compression = Compression(),
    private val explainFailure: (NormalizedRelayUrl, Throwable) -> Throwable? = { _, _ -> null },
    private val refusedStatus: (Throwable) -> Int? = { null },
) : WebSocket {
    /**
     * The relays that get no compression offer, shared by every socket of one [Builder].
     * [isFailure] tells an engine error caused by a relay's compressed frames from any other.
     */
    class Compression(
        val isFailure: (Throwable) -> Boolean = { false },
    ) {
        private val disabled = MutableStateFlow<Set<String>>(emptySet())

        fun isDisabled(url: NormalizedRelayUrl) = url.url in disabled.value

        /** True the first time [url] is turned off. */
        fun disable(url: NormalizedRelayUrl): Boolean {
            var added = false
            disabled.update {
                added = url.url !in it
                it + url.url
            }
            return added
        }
    }

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
            val uncompressed = compression.isDisabled(url)
            httpClient(url).webSocket(
                urlString = url.url,
                request = {
                    // Offering an extension no server knows replaces the engine's deflate offer:
                    // servers ignore unknown ones, and an empty header stalls the handshake.
                    if (uncompressed) header(EXTENSIONS_HEADER, NO_EXTENSION)
                },
            ) {
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
            if (compression.isFailure(e) && compression.disable(url)) {
                Log.i(TAG) { "${url.url} sent compressed frames this engine cannot read: dialing it without compression" }
            }
            if (isCurrent(me)) {
                // The relay client reads the failure's type and message to tell a dead domain from a
                // busy relay, as OkHttp reports them on Android; the engine's own error can hide that
                // (NSURLSession says "bad URL" for every Tor refusal), so a better one wins.
                val explained = explainFailure(url, e)
                val status = (e as? ResponseException)?.response?.status?.value ?: refusedStatus(e)
                if (explained != null) {
                    out.onFailure(explained, null, null)
                } else {
                    // A response only when there was one: OkHttp passes null for a failure without
                    // an HTTP answer, and the relay client labels anything else a misconfigured server.
                    out.onFailure(e, status, if (status != null) e.message else null)
                }
            }
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

    /**
     * Builds [KtorWebSocket]s that dial with the client [httpClient] picks for each relay.
     *
     * [canDial] gates each dial, as `BasicOkHttpWebSocket.Builder`'s does: false skips it. The
     * apps hold a Tor-routed relay back until Tor can carry it, so a bootstrap is not met by a
     * burst of doomed dials; the relay client dials again when the transport changes.
     */
    class Builder(
        private val httpClient: (NormalizedRelayUrl) -> HttpClient,
        private val scope: CoroutineScope,
        private val compression: Compression = Compression(),
        private val canDial: (NormalizedRelayUrl) -> Boolean = { true },
        private val explainFailure: (NormalizedRelayUrl, Throwable) -> Throwable? = { _, _ -> null },
        private val refusedStatus: (Throwable) -> Int? = { null },
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = KtorWebSocket(url, httpClient, out, scope, compression, explainFailure, refusedStatus)

        override fun canConnect(url: NormalizedRelayUrl) = canDial(url)
    }

    companion object {
        private const val TAG = "KtorWebSocket"
        private const val EXTENSIONS_HEADER = "Sec-WebSocket-Extensions"
        private const val NO_EXTENSION = "x-amethyst-no-compression"
    }
}
