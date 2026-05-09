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
package com.vitorpamplona.quic.webtransport

import com.vitorpamplona.quic.Varint
import com.vitorpamplona.quic.http3.Http3ErrorCode
import com.vitorpamplona.quic.http3.Http3Frame
import com.vitorpamplona.quic.http3.Http3FrameReader
import com.vitorpamplona.quic.http3.Http3Settings
import com.vitorpamplona.quic.http3.Http3SettingsId
import com.vitorpamplona.quic.http3.Http3StreamType
import com.vitorpamplona.quic.stream.QuicStream
import com.vitorpamplona.quic.stream.StreamId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * A peer-initiated WebTransport stream whose framing prefix has already been
 * stripped. The [data] flow yields only application-level bytes.
 *
 * For peer-initiated **bidirectional** streams (where [isUnidirectional]
 * is false), [send] and [finish] are wired to the stream's outbound
 * half so the application can write its response. They are null on
 * unidirectional streams.
 *
 * For both directions, [reset] (RFC 9000 §3.5 RESET_STREAM) and
 * [stopSending] (RFC 9000 §3.5 STOP_SENDING) are exposed so application
 * code (e.g. moq-lite Lite-03's typed cancel paths) can convey error
 * codes instead of relying on graceful FIN. [reset] is null on uni
 * streams whose write side belongs to the peer; [stopSending] is null
 * when there's no read side to ask the peer to stop on (i.e. never
 * — every stream we surface here has a read side).
 */
class StrippedWtStream(
    val streamId: Long,
    val isUnidirectional: Boolean,
    val data: Flow<ByteArray>,
    /**
     * Suspends until [chunk] is queued on the stream's send half. Null
     * for unidirectional streams (write side is the peer's, not ours).
     */
    val send: (suspend (chunk: ByteArray) -> Unit)? = null,
    /**
     * Half-close the send side (FIN). Null for unidirectional streams.
     */
    val finish: (suspend () -> Unit)? = null,
    /**
     * Send `RESET_STREAM(applicationErrorCode)` on the send half (RFC
     * 9000 §3.5). Null when the application doesn't own the send side
     * (peer-initiated uni stream). The first call wins per
     * `QuicStream.resetStream`'s lock-free first-call-wins gate;
     * duplicate calls with different codes are silently ignored to
     * keep the wire frame stable.
     */
    val reset: (suspend (errorCode: Long) -> Unit)? = null,
    /**
     * Send `STOP_SENDING(applicationErrorCode)` on the receive half
     * (RFC 9000 §3.5) — asks the peer to RESET its corresponding
     * send side. Always non-null on streams surfaced here: every
     * stripped stream has a read side. First call wins.
     */
    val stopSending: (suspend (errorCode: Long) -> Unit)? = null,
)

/**
 * Demultiplexes peer-initiated streams by their leading varint(s):
 *
 *   - HTTP/3 CONTROL stream (0x00) — drained internally, SETTINGS captured.
 *   - QPACK encoder/decoder streams (0x02 / 0x03) — drained (we run with
 *     dynamic table off).
 *   - WT unidirectional (0x54) + matching quarter session id — surfaced.
 *   - WT bidirectional signal (0x41) + matching quarter session id — surfaced.
 *   - Anything else — dropped per RFC 9114 §9.
 *
 * Without this, the server's CONTROL stream would deliver its SETTINGS frame
 * bytes directly to MoQ as application data — a live-interop break.
 */
class WtPeerStreamDemux(
    private val expectedConnectStreamId: Long,
    private val scope: CoroutineScope,
    /**
     * Optional driver hook. When non-null, every write on a surfaced
     * [StrippedWtStream]'s send half wakes the QUIC pump so frames
     * actually leave the connection. Tests that drive the demux
     * without a real driver (the stream's own send queue is enough)
     * can leave this null.
     */
    private val driver: com.vitorpamplona.quic.connection.QuicConnectionDriver? = null,
    /**
     * Cap on the number of peer-initiated streams that may queue here
     * waiting for the application to consume them. Pre-fix this was
     * `Channel.UNLIMITED` — a peer that opens streams faster than the
     * application drains could pin one [StrippedWtStream] (and its
     * captured `chunkChannel`) per stream id, indefinitely. Bounded
     * buffer + suspending `send` propagates backpressure: when the app
     * is slow, [route] suspends inside [emitStripped] before launching
     * any further per-stream work, which in turn lets QUIC's per-conn
     * `MAX_STREAMS_*` accounting throttle the peer.
     */
    private val readyStreamsBuffer: Int = DEFAULT_READY_STREAMS_BUFFER,
) {
    private val readyStreams = Channel<StrippedWtStream>(readyStreamsBuffer)

    @Volatile
    var peerSettings: Http3Settings? = null
        private set

    /**
     * Server-sent GOAWAY stream id (RFC 9114 §5.2). The server is signalling
     * "I won't accept any new request streams >= this id"; for WebTransport we
     * treat it as the session entering drain — existing streams may continue
     * but the application should not open new ones.
     *
     * Stays null until a GOAWAY arrives on the CONTROL stream.
     */
    @Volatile
    var peerGoawayStreamId: Long? = null
        private set

    /**
     * Round-5 #4: surface H3_ID_ERROR (RFC 9114 §5.2 violation: GOAWAY id
     * increased) so the QUIC layer can act on it instead of having the
     * route()-level `catch (_: Throwable)` swallow it. Stays null until a
     * regressing GOAWAY arrives. Application code (or
     * [QuicWebTransportSessionState]) should poll this and close the
     * connection if non-null.
     */
    @Volatile
    var peerGoawayProtocolError: String? = null
        private set

    /**
     * RFC 9114 §6.2.1 + RFC 9204 §4.2 critical-stream closure record.
     * Latches the (errorCode, reason) pair the demux passed to its
     * driver-side `connection.close` when ANY critical unidirectional
     * stream — control, QPACK encoder, QPACK decoder — was closed by
     * the peer (clean FIN) or violated an HTTP/3 invariant.
     *
     * Production callers don't need to observe this; the
     * `connection.close` already drives the QUIC layer to CLOSED.
     * Tests use it to assert the closure path fired without wiring a
     * real driver: the flag is set inside [closeConnection] before
     * (and independently of) the suspending `connection.close` call.
     */
    @Volatile
    var criticalStreamClosureCode: Long? = null
        private set

    @Volatile
    var criticalStreamClosureReason: String? = null
        private set

    /**
     * Surface RFC 9114 §7.2 frame-validation violations the
     * [Http3FrameReader] catches (H3_FRAME_UNEXPECTED, H3_MISSING_SETTINGS,
     * forbidden reserved types). The route()-level `catch (_: Throwable)`
     * cancels the per-stream collector but doesn't propagate the
     * underlying [com.vitorpamplona.quic.QuicCodecException]'s message
     * upstream — without this field a buggy server would just see
     * "stream stops being read" with no diagnostic. Stays null until a
     * violation is observed; the QUIC-layer caller polls it and closes
     * the connection on non-null.
     */
    @Volatile
    var peerH3ProtocolError: String? = null
        private set

    val incomingStrippedStreams: Flow<StrippedWtStream> = readyStreams.consumeAsFlow()

    /**
     * Begin processing a peer-initiated stream. Caller must invoke this for
     * every stream returned by [com.vitorpamplona.quic.connection.QuicConnection.pollIncomingPeerStream].
     */
    fun process(stream: QuicStream) {
        scope.launch { route(stream) }
    }

    private suspend fun route(stream: QuicStream) {
        // Round-5 concurrency #2: wrap the whole route in coroutineScope so
        // the inner collector launched below is joined on EVERY exit path,
        // not just the ones that called drainBlackHole. Pre-fix four early-
        // return sites (mismatched stream-type prefixes, unknown WT signal,
        // foreign session id) left the collector orphaned, draining
        // stream.incoming into a chunkChannel nobody read — unbounded
        // memory growth per misbehaving peer stream.
        kotlinx.coroutines.coroutineScope {
            val pending = ArrayDeque<ByteArray>()
            val flowIterator = stream.incoming
            // Bounded suspending channel — pre-fix `UNLIMITED` let a
            // single misbehaving peer stream pin gigabytes of heap when
            // the consumer (the application) couldn't keep up. With a
            // bounded buffer + suspending `send`, the collector below
            // back-pressures the QuicStream's incoming flow — which in
            // turn delays our outbound MAX_STREAM_DATA grants and
            // throttles the peer at the QUIC layer. The fixed size is
            // small (64 chunks ≈ ~64 KiB at typical packet payloads):
            // anything bigger just delays the back-pressure signal.
            val chunkChannel = Channel<ByteArray>(CHUNK_CHANNEL_BUFFER)
            val collector =
                launch {
                    try {
                        flowIterator.collect { chunkChannel.send(it) }
                    } finally {
                        chunkChannel.close()
                    }
                }

            // Helper: read the next available bytes; returns null on stream close
            // before enough bytes are present.
            suspend fun moreBytes(): Boolean {
                val chunk = chunkChannel.receiveCatching().getOrNull() ?: return false
                pending.addLast(chunk)
                return true
            }

            suspend fun readVarintFromPending(): Long? {
                while (true) {
                    val flat = flatten(pending)
                    val res = Varint.decode(flat, 0)
                    if (res != null) {
                        consumeFromPending(pending, res.bytesConsumed)
                        return res.value
                    }
                    if (!moreBytes()) return null
                }
            }

            try {
                if (StreamId.isUnidirectional(stream.streamId)) {
                    val streamType = readVarintFromPending()
                    if (streamType == null) {
                        collector.cancel()
                        return@coroutineScope
                    }
                    when (streamType) {
                        Http3StreamType.CONTROL -> {
                            drainControlStream(pending, chunkChannel)
                        }

                        Http3StreamType.QPACK_ENCODER, Http3StreamType.QPACK_DECODER -> {
                            // RFC 9204 §4.2: QPACK encoder + decoder
                            // streams are critical. Closure of either
                            // MUST be treated as H3_CLOSED_CRITICAL_STREAM
                            // — same wire-spec class as the control
                            // stream. We don't speak the QPACK
                            // dynamic-table instructions either way
                            // (encoder is RIC=0, decoder is best-effort
                            // ACKs we ignore), so the drain is still
                            // black-hole; only the FIN handling differs.
                            drainCriticalStream(
                                chunkChannel,
                                "QPACK ${if (streamType == Http3StreamType.QPACK_ENCODER) "encoder" else "decoder"} stream",
                            )
                        }

                        Http3StreamType.WEBTRANSPORT_UNI_STREAM -> {
                            val quarter = readVarintFromPending()
                            if (quarter == null || quarter * 4L != expectedConnectStreamId) {
                                drainBlackHole(chunkChannel) // not our session / truncated
                                return@coroutineScope
                            }
                            emitStripped(stream, pending, chunkChannel, isUni = true)
                        }

                        else -> {
                            drainBlackHole(chunkChannel) // unknown — drop per RFC 9114 §9
                        }
                    }
                } else {
                    // Server-initiated bidi: per draft-ietf-webtrans-http3, prefixed
                    // with WT_BIDI_STREAM (0x41) varint then quarter session id.
                    val signal = readVarintFromPending()
                    if (signal == null || signal != WtStreamType.WT_BIDI_STREAM) {
                        drainBlackHole(chunkChannel)
                        return@coroutineScope
                    }
                    val quarter = readVarintFromPending()
                    if (quarter == null || quarter * 4L != expectedConnectStreamId) {
                        drainBlackHole(chunkChannel)
                        return@coroutineScope
                    }
                    emitStripped(stream, pending, chunkChannel, isUni = false)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // Audit-4 #17: don't swallow cancellation — needs to propagate
                // to actually tear down the coroutine when scope is cancelled.
                throw ce
            } catch (_: Throwable) {
                // peer closed mid-prefix or framing error — drop quietly. The
                // surrounding coroutineScope joins the collector on exit, so
                // no leak even on swallowed errors.
                collector.cancel()
            }
        }
    }

    private suspend fun drainControlStream(
        pending: ArrayDeque<ByteArray>,
        chunkChannel: Channel<ByteArray>,
    ) {
        val reader = Http3FrameReader(context = Http3FrameReader.StreamContext.CONTROL)
        // Push whatever we already buffered.
        try {
            while (pending.isNotEmpty()) reader.push(pending.removeFirst())
            consumeFrames(reader)
            for (chunk in chunkChannel) {
                reader.push(chunk)
                consumeFrames(reader)
            }
            // Channel iteration exited cleanly → peer FIN'd the
            // control stream. RFC 9114 §6.2.1: closure of any
            // critical stream MUST be treated as a connection error
            // of type H3_CLOSED_CRITICAL_STREAM.
            closeConnection(
                Http3ErrorCode.CLOSED_CRITICAL_STREAM,
                "H3_CLOSED_CRITICAL_STREAM: peer closed CONTROL stream",
            )
        } catch (e: com.vitorpamplona.quic.QuicCodecException) {
            // RFC 9114 §7.2 frame-validation throws (H3_FRAME_UNEXPECTED /
            // H3_MISSING_SETTINGS / reserved type) land here. Record the
            // diagnostic so observability tools (qlog, tests) see a
            // structured message; ALSO drive the connection closed via
            // [closeConnection] so the application doesn't need to poll
            // [peerH3ProtocolError] separately. Idempotent on duplicate
            // hits.
            if (peerH3ProtocolError == null) {
                peerH3ProtocolError = e.message ?: "HTTP/3 protocol violation on CONTROL stream"
            }
            closeConnection(
                http3ErrorCodeForMessage(e.message),
                e.message ?: "HTTP/3 protocol violation on CONTROL stream",
            )
            throw e
        }
    }

    /**
     * Drain a critical unidirectional stream (QPACK encoder / decoder)
     * whose payload we don't otherwise process. The QPACK control
     * messages would only matter if we ran a dynamic table — since we
     * don't, the bytes themselves are dropped, but the §6.2.1
     * "critical stream closed = connection error" obligation still
     * applies. On peer FIN we call [closeConnection] with
     * H3_CLOSED_CRITICAL_STREAM.
     */
    private suspend fun drainCriticalStream(
        chunkChannel: Channel<ByteArray>,
        label: String,
    ) {
        @Suppress("UNUSED_VARIABLE")
        for (discarded in chunkChannel) {
            // intentionally discarded — QPACK dynamic-table is off.
        }
        closeConnection(
            Http3ErrorCode.CLOSED_CRITICAL_STREAM,
            "H3_CLOSED_CRITICAL_STREAM: peer closed $label",
        )
    }

    /**
     * Close the underlying QUIC connection with an HTTP/3
     * application-level error code. No-op when the demux is running
     * without a [driver] (test path) — the test is then expected to
     * read [peerH3ProtocolError] / observe the FIN explicitly.
     *
     * Launched on the demux's [scope] so the suspending close call
     * doesn't block the per-stream collector that's exiting.
     */
    private fun closeConnection(
        errorCode: Long,
        reason: String,
    ) {
        // Latch the closure intent FIRST so tests (and qlog observers)
        // can see what we tried to do, regardless of whether a
        // driver/connection is wired to follow through. Idempotent: a
        // duplicate fire (e.g. control stream errors then closes)
        // doesn't overwrite the first reason.
        if (criticalStreamClosureCode == null) {
            criticalStreamClosureCode = errorCode
            criticalStreamClosureReason = reason
        }
        val conn = driver?.connection ?: return
        scope.launch {
            conn.close(errorCode, reason)
            driver.wakeup()
        }
    }

    /**
     * Best-effort mapping from a [QuicCodecException] message raised by
     * the HTTP/3 frame reader / SETTINGS validator to the matching
     * RFC 9114 §8.1 error code. We attach the code to the
     * application-level CONNECTION_CLOSE so downstream observers
     * (qlog, peer) get the precise spec category instead of a generic
     * GENERAL_PROTOCOL_ERROR.
     */
    private fun http3ErrorCodeForMessage(message: String?): Long {
        if (message == null) return Http3ErrorCode.GENERAL_PROTOCOL_ERROR
        return when {
            message.contains("H3_FRAME_UNEXPECTED") -> Http3ErrorCode.FRAME_UNEXPECTED
            message.contains("H3_MISSING_SETTINGS") -> Http3ErrorCode.MISSING_SETTINGS
            message.contains("H3_SETTINGS_ERROR") -> Http3ErrorCode.SETTINGS_ERROR
            message.contains("H3_ID_ERROR") -> Http3ErrorCode.ID_ERROR
            message.contains("H3_FRAME_ERROR") -> Http3ErrorCode.FRAME_ERROR
            else -> Http3ErrorCode.GENERAL_PROTOCOL_ERROR
        }
    }

    private fun consumeFrames(reader: Http3FrameReader) {
        while (true) {
            val frame = reader.next() ?: return
            when (frame) {
                is Http3Frame.Settings -> {
                    // draft-ietf-webtrans-http3 §3 + RFC 8441: a server that
                    // accepts WebTransport MUST advertise both
                    // ENABLE_WEBTRANSPORT=1 and ENABLE_CONNECT_PROTOCOL=1.
                    // If either is missing/zero, the WT session can't
                    // proceed — surface as an H3 protocol error so
                    // [drainControlStream]'s catch records it on
                    // [peerH3ProtocolError] and the application closes
                    // the connection instead of issuing an Extended
                    // CONNECT the server will reject anyway.
                    val s = frame.settings.settings
                    val enableWt = s[Http3SettingsId.ENABLE_WEBTRANSPORT] ?: 0L
                    val enableConnect = s[Http3SettingsId.ENABLE_CONNECT_PROTOCOL] ?: 0L
                    if (enableWt != 1L) {
                        throw com.vitorpamplona.quic.QuicCodecException(
                            "peer SETTINGS missing ENABLE_WEBTRANSPORT=1 (got $enableWt)",
                        )
                    }
                    if (enableConnect != 1L) {
                        throw com.vitorpamplona.quic.QuicCodecException(
                            "peer SETTINGS missing ENABLE_CONNECT_PROTOCOL=1 (got $enableConnect)",
                        )
                    }
                    peerSettings = frame.settings
                }

                is Http3Frame.Goaway -> {
                    // GOAWAY body is a single varint Stream ID. Decode it so
                    // applications can observe drain state via
                    // [peerGoawayStreamId]. Malformed bodies are dropped.
                    //
                    // Audit-4 #5: RFC 9114 §5.2 — a subsequent GOAWAY id MUST
                    // be ≤ the previous one (the "last accepted" id only
                    // shrinks). A peer regressing this is H3_ID_ERROR; we
                    // throw, the surrounding `route` catch maps that to a
                    // black-hole, and the application sees the previously
                    // recorded id stay put.
                    val res = Varint.decode(frame.body, 0)
                    if (res != null) {
                        val prev = peerGoawayStreamId
                        if (prev != null && res.value > prev) {
                            // Round-5 #4: surface the protocol error via
                            // peerGoawayProtocolError so the QUIC layer can
                            // close the connection. Throwing also exits this
                            // CONTROL-stream reader; the surrounding route()
                            // catch handles cleanup (collector cancel +
                            // chunkChannel close).
                            peerGoawayProtocolError =
                                "H3_ID_ERROR: GOAWAY id increased ($prev → ${res.value})"
                            throw com.vitorpamplona.quic.QuicCodecException(
                                peerGoawayProtocolError!!,
                            )
                        }
                        peerGoawayStreamId = res.value
                    }
                }

                // no new requests; we don't enforce yet
                else -> {
                    Unit
                }
            }
        }
    }

    private suspend fun drainBlackHole(chunkChannel: Channel<ByteArray>) {
        @Suppress("UNUSED_VARIABLE")
        for (discarded in chunkChannel) {
            // intentionally discarded — stream type is one we don't process
        }
    }

    private suspend fun emitStripped(
        stream: QuicStream,
        pending: ArrayDeque<ByteArray>,
        chunkChannel: Channel<ByteArray>,
        isUni: Boolean,
    ) {
        val prebuffered = pending.toList()
        pending.clear()
        val data: Flow<ByteArray> =
            flow {
                for (b in prebuffered) if (b.isNotEmpty()) emit(b)
                for (chunk in chunkChannel) if (chunk.isNotEmpty()) emit(chunk)
            }
        // For peer-initiated bidi streams the application also needs to
        // write back. We expose stream.send.enqueue + driver.wakeup so
        // the surfaced stream looks symmetric to a locally-opened bidi.
        // For uni streams (peer is the sender) we leave both null.
        val send: (suspend (ByteArray) -> Unit)? =
            if (isUni) {
                null
            } else {
                { chunk ->
                    stream.send.enqueue(chunk)
                    driver?.wakeup()
                }
            }
        val finish: (suspend () -> Unit)? =
            if (isUni) {
                null
            } else {
                {
                    stream.send.finish()
                    driver?.wakeup()
                }
            }
        // RESET_STREAM is meaningful only on streams whose write side
        // belongs to us. Same null pattern as [send] / [finish] for
        // peer-initiated uni streams.
        val reset: (suspend (Long) -> Unit)? =
            if (isUni) {
                null
            } else {
                { errorCode ->
                    stream.resetStream(errorCode)
                    driver?.wakeup()
                }
            }
        // STOP_SENDING is meaningful on the receive half — every
        // stripped stream we surface here has one (uni: peer is
        // sender; bidi: peer's send side is our receive side), so
        // wire unconditionally.
        val stopSending: (suspend (Long) -> Unit) = { errorCode ->
            stream.stopSending(errorCode)
            driver?.wakeup()
        }
        // Suspending send instead of `trySend` so a slow application
        // back-pressures the demux instead of silently dropping the
        // stream. With a bounded buffer ([readyStreamsBuffer]),
        // [readyStreams.send] suspends when the app hasn't drained,
        // which keeps `route()` busy and (combined with [process]'s
        // `scope.launch`) lets the next peer stream wait its turn
        // rather than spawning a coroutine for every offered id.
        readyStreams.send(
            StrippedWtStream(
                streamId = stream.streamId,
                isUnidirectional = isUni,
                data = data,
                send = send,
                finish = finish,
                reset = reset,
                stopSending = stopSending,
            ),
        )
    }

    companion object {
        /** Default cap on queued peer-initiated streams (1024). */
        const val DEFAULT_READY_STREAMS_BUFFER: Int = 1024

        /** Per-stream chunk-channel capacity (64). */
        internal const val CHUNK_CHANNEL_BUFFER: Int = 64
    }
}

private fun flatten(chunks: ArrayDeque<ByteArray>): ByteArray {
    var total = 0
    for (c in chunks) total += c.size
    val out = ByteArray(total)
    var pos = 0
    for (c in chunks) {
        c.copyInto(out, pos)
        pos += c.size
    }
    return out
}

private fun consumeFromPending(
    pending: ArrayDeque<ByteArray>,
    bytes: Int,
) {
    var remaining = bytes
    while (remaining > 0 && pending.isNotEmpty()) {
        val head = pending.first()
        if (head.size <= remaining) {
            pending.removeFirst()
            remaining -= head.size
        } else {
            pending[0] = head.copyOfRange(remaining, head.size)
            remaining = 0
        }
    }
}
