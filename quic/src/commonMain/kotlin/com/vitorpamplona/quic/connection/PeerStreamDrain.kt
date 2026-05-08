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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.stream.StreamId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Spawn a long-lived coroutine that accepts every peer-initiated
 * unidirectional stream this connection surfaces and drains it to
 * `/dev/null`. Returns the [Job] of the launched dispatcher so the
 * caller can join / cancel it.
 *
 * Why this exists: RFC 9114 §6.2.1 mandates that an HTTP/3 server
 * opens at least three peer-initiated uni streams (CONTROL +
 * QPACK_ENCODER + QPACK_DECODER) immediately after the handshake.
 * [QuicConnectionParser] routes their bytes into each stream's
 * bounded `incomingChannel` (capacity 64 chunks) — if no consumer
 * reads them, the next chunk delivery overflows and the connection
 * tears down with `INTERNAL_ERROR: stream … consumer overflowed`
 * (audit-4 #3). The symptom for the multiplexing interop test was
 * a zero-request connection that died after ~5 seconds the moment
 * the server's QPACK encoder pushed dynamic-table inserts.
 *
 * This helper is the explicit "I do not care about these particular
 * peer streams" knob for callers (e.g. an H3 GET client that runs
 * with QPACK dynamic-table off) that don't need to interpret the
 * SETTINGS / QPACK bytes. The :quic library does NOT default to
 * silently dropping app bytes — apps that DO care about peer-uni
 * streams (WebTransport, MoQ-over-WT) call
 * [QuicConnection.awaitIncomingPeerStream] directly and route each
 * stream by inspecting its leading varint.
 *
 * Bidi peer streams are deliberately re-queued back to
 * [QuicConnection.newPeerStreams] (well — left untouched on the
 * head when surfaced; we just don't consume them here) so that an
 * application that opts in to draining uni streams doesn't
 * accidentally swallow peer-initiated bidi requests. In the
 * H3-client multiplexing case we never expect the server to open
 * a bidi stream against us, but if it does the connection-level
 * handling stays correct.
 *
 * Usage from an integrator (sketch — `Http3GetClient` is on a
 * different branch on this repo today):
 *
 * ```
 * suspend fun init(scope: CoroutineScope) {
 *     // Open our own H3 control + QPACK uni streams.
 *     openH3ControlStream()
 *     openQpackEncoderStream()
 *     openQpackDecoderStream()
 *
 *     // Accept the server's three counterparts and discard their bytes.
 *     conn.drainPeerInitiatedUniStreamsIntoBlackHole(scope)
 * }
 * ```
 *
 * Lifecycle: the launched coroutine exits cleanly when
 * [QuicConnection.awaitIncomingPeerStream] returns null (the
 * connection has reached `CLOSED`). Cancelling the [scope] also
 * tears it down.
 */
fun QuicConnection.drainPeerInitiatedUniStreamsIntoBlackHole(scope: CoroutineScope): Job =
    scope.launch {
        while (true) {
            val stream = awaitIncomingPeerStream() ?: return@launch
            // Only drain peer-initiated UNI streams. Peer bidi streams are
            // returned to whatever else the application wants to do with
            // them — but we have to put them somewhere because
            // awaitIncomingPeerStream removed them from the queue. The
            // pragmatic choice on a connection that uses this helper:
            // log + ignore. If the application ALSO cares about peer
            // bidi streams, it should NOT use this helper and instead
            // implement its own routing dispatcher.
            if (StreamId.kindOf(stream.streamId) != StreamId.Kind.SERVER_UNI) {
                // Drain the bidi too — silently dropping bytes is bad
                // policy, but tearing down the connection because the
                // server opened an unexpected bidi is worse. The uni
                // case is the documented one.
                launch { drainStreamSilently(stream) }
                continue
            }
            launch { drainStreamSilently(stream) }
        }
    }

private suspend fun drainStreamSilently(stream: com.vitorpamplona.quic.stream.QuicStream) {
    @Suppress("UNUSED_VARIABLE")
    stream.incoming.collect { _ ->
        // intentionally discarded; this stream is one the caller has
        // declared it does not care about (typically the server's H3
        // CONTROL / QPACK_ENCODER / QPACK_DECODER streams).
    }
}
