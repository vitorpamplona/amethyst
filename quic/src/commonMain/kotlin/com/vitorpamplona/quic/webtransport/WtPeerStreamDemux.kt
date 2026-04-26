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
import com.vitorpamplona.quic.http3.Http3Frame
import com.vitorpamplona.quic.http3.Http3FrameReader
import com.vitorpamplona.quic.http3.Http3Settings
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
 */
class StrippedWtStream(
    val streamId: Long,
    val isUnidirectional: Boolean,
    val data: Flow<ByteArray>,
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
) {
    private val readyStreams = Channel<StrippedWtStream>(Channel.UNLIMITED)

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

    val incomingStrippedStreams: Flow<StrippedWtStream> = readyStreams.consumeAsFlow()

    /**
     * Begin processing a peer-initiated stream. Caller must invoke this for
     * every stream returned by [com.vitorpamplona.quic.connection.QuicConnection.pollIncomingPeerStream].
     */
    fun process(stream: QuicStream) {
        scope.launch { route(stream) }
    }

    private suspend fun route(stream: QuicStream) {
        // Build a buffered chunk source: keeps unconsumed bytes until we
        // know what kind of stream we're looking at.
        val pending = ArrayDeque<ByteArray>()
        val flowIterator = stream.incoming
        val chunkChannel = Channel<ByteArray>(Channel.UNLIMITED)
        scope.launch {
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
                val streamType = readVarintFromPending() ?: return
                when (streamType) {
                    Http3StreamType.CONTROL -> {
                        drainControlStream(pending, chunkChannel)
                    }

                    Http3StreamType.QPACK_ENCODER, Http3StreamType.QPACK_DECODER -> {
                        drainBlackHole(chunkChannel)
                    }

                    Http3StreamType.WEBTRANSPORT_UNI_STREAM -> {
                        val quarter = readVarintFromPending() ?: return
                        if (quarter * 4L != expectedConnectStreamId) {
                            drainBlackHole(chunkChannel) // not our session
                            return
                        }
                        emitStripped(stream, pending, chunkChannel, isUni = true)
                    }

                    else -> {
                        drainBlackHole(chunkChannel)
                    } // unknown — drop per RFC 9114 §9
                }
            } else {
                // Server-initiated bidi: per draft-ietf-webtrans-http3, prefixed
                // with WT_BIDI_STREAM (0x41) varint then quarter session id.
                val signal = readVarintFromPending() ?: return
                if (signal != WtStreamType.WT_BIDI_STREAM) {
                    drainBlackHole(chunkChannel)
                    return
                }
                val quarter = readVarintFromPending() ?: return
                if (quarter * 4L != expectedConnectStreamId) {
                    drainBlackHole(chunkChannel)
                    return
                }
                emitStripped(stream, pending, chunkChannel, isUni = false)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Audit-4 #17: don't swallow cancellation — needs to propagate
            // to actually tear down the coroutine when scope is cancelled.
            throw ce
        } catch (_: Throwable) {
            // peer closed mid-prefix or framing error — drop quietly
        }
    }

    private suspend fun drainControlStream(
        pending: ArrayDeque<ByteArray>,
        chunkChannel: Channel<ByteArray>,
    ) {
        val reader = Http3FrameReader()
        // Push whatever we already buffered.
        while (pending.isNotEmpty()) reader.push(pending.removeFirst())
        consumeFrames(reader)
        for (chunk in chunkChannel) {
            reader.push(chunk)
            consumeFrames(reader)
        }
    }

    private fun consumeFrames(reader: Http3FrameReader) {
        while (true) {
            val frame = reader.next() ?: return
            when (frame) {
                is Http3Frame.Settings -> {
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
                            throw com.vitorpamplona.quic.QuicCodecException(
                                "H3_ID_ERROR: GOAWAY id increased ($prev → ${res.value})",
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

    private fun emitStripped(
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
        readyStreams.trySend(
            StrippedWtStream(
                streamId = stream.streamId,
                isUnidirectional = isUni,
                data = data,
            ),
        )
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
