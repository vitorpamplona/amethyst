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
package com.vitorpamplona.quic.interop.runner

import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.connection.drainPeerInitiatedUniStreamsIntoBlackHole
import com.vitorpamplona.quic.http3.Http3Frame
import com.vitorpamplona.quic.http3.Http3FrameReader
import com.vitorpamplona.quic.http3.Http3FrameType
import com.vitorpamplona.quic.http3.Http3Settings
import com.vitorpamplona.quic.http3.Http3StreamType
import com.vitorpamplona.quic.qpack.QpackDecoder
import com.vitorpamplona.quic.qpack.QpackEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.withLock

/** Common shape for the two interop GET clients (HTTP/3 and HQ-interop).
 *
 *  The interface is split into two phases so the parallel multiplexing
 *  path can BATCH enqueues (synchronous, serial) and SINGLE-wakeup the
 *  send loop, vs. waking on every individual request which produces
 *  one tiny packet per stream instead of coalesced packets per drain. */
interface GetClient {
    /** Open a stream + enqueue the request bytes + FIN. Does NOT wake the
     *  send loop — caller is responsible for batching wakes. Returns an
     *  opaque handle the caller passes to [awaitResponse]. */
    suspend fun prepareRequest(
        authority: String,
        path: String,
    ): RequestHandle

    /**
     * Atomically open + enqueue + FIN N streams under a single hold of
     * the connection lock. The send loop cannot interject between
     * opens, so when it next drains it sees ALL N streams' data ready
     * and packs them into coalesced packets instead of emitting one
     * tiny packet per stream.
     *
     * Without this, the equivalent serial loop of [prepareRequest]
     * yields between calls (lock release → send loop wakes → drains
     * one stream → next prepareRequest acquires...) and we send one
     * stream per packet — what cratered the multiplexing testcase.
     */
    suspend fun prepareRequests(
        authority: String,
        paths: List<String>,
    ): List<RequestHandle>

    /** Suspend until the server FINs the response stream associated with
     *  [handle]. Returns the assembled response. */
    suspend fun awaitResponse(handle: RequestHandle): GetResponse

    /** Convenience shortcut for the sequential / single-request paths. */
    suspend fun get(
        authority: String,
        path: String,
    ): GetResponse {
        val h = prepareRequest(authority, path)
        return awaitResponse(h)
    }
}

/** Opaque handle returned by [GetClient.prepareRequest]. Implementations
 *  cast it back to their internal stream representation. */
interface RequestHandle

data class GetResponse(
    val status: Int,
    val body: ByteArray,
)

/**
 * Minimal HTTP/3 GET client used by the interop endpoint to satisfy the
 * `http3` and `multiplexing` testcases.
 *
 * Opens the three required client-side unidirectional streams (control,
 * QPACK encoder, QPACK decoder) per RFC 9114 §6.2.1. Encodes requests with
 * the literal-only [QpackEncoder] (no dynamic table — RFC 9204 Required
 * Insert Count = 0) so we don't need to push QPACK encoder instructions.
 *
 * Not a production HTTP/3 client. Specifically: no GOAWAY handling, no
 * priority, no push-promise, no trailers, no dynamic QPACK table.
 */
class Http3GetClient(
    private val conn: QuicConnection,
    private val driver: QuicConnectionDriver,
) : GetClient {
    suspend fun init(scope: CoroutineScope) {
        // Control stream: type-0x00 prefix followed by a SETTINGS frame
        // (empty body is legal — RFC 9114 §7.2.4). Empty body = spec
        // defaults: QPACK_MAX_TABLE_CAPACITY=0, QPACK_BLOCKED_STREAMS=0,
        // MAX_FIELD_SECTION_SIZE=unlimited. We deliberately do NOT
        // explicitly send QPACK_MAX_TABLE_CAPACITY=0 — empirically
        // (aioquic 2026-05-07) that worsened the multiplexing case
        // from 1 file to 0 files.
        val control = conn.openUniStream()
        val w = QuicWriter()
        w.writeVarint(Http3StreamType.CONTROL)
        w.writeBytes(Http3Settings(emptyMap()).encodeFrame())
        control.send.enqueue(w.toByteArray())
        // Control stream stays open for the lifetime of the H3 connection;
        // do NOT call finish() — peers treat that as H3_CLOSED_CRITICAL_STREAM.

        // Required: open QPACK encoder + decoder streams, even though we
        // never insert into the dynamic table. Just send the type prefix.
        val qpackEnc = conn.openUniStream()
        val w2 = QuicWriter()
        w2.writeVarint(Http3StreamType.QPACK_ENCODER)
        qpackEnc.send.enqueue(w2.toByteArray())

        val qpackDec = conn.openUniStream()
        val w3 = QuicWriter()
        w3.writeVarint(Http3StreamType.QPACK_DECODER)
        qpackDec.send.enqueue(w3.toByteArray())

        // RFC 9114 §6.2: the server opens its own three uni streams
        // (control, qpack encoder, qpack decoder). Their bytes accumulate
        // in per-stream `incomingChannel`s (capacity 64); without an
        // active consumer the channel saturates and `:quic` tears down
        // the connection with INTERNAL_ERROR. We don't actually use the
        // dynamic table or care about the server's settings, so drain
        // and discard. Without this, multiplexing testcase fails after
        // ~4.5s with "consumer overflowed" tear-down.
        conn.drainPeerInitiatedUniStreamsIntoBlackHole(scope)
    }

    override suspend fun prepareRequest(
        authority: String,
        path: String,
    ): RequestHandle {
        val stream = conn.openBidiStream()
        stream.send.enqueue(encodeRequest(authority, path))
        stream.send.finish()
        return Http3RequestHandle(stream)
    }

    override suspend fun prepareRequests(
        authority: String,
        paths: List<String>,
    ): List<RequestHandle> =
        // CRITICAL: streamsLock — the lock the writer's drainOutbound
        // takes. Holding lifecycleLock (the deprecated `conn.lock`
        // alias) here did NOT block the send loop, so the writer
        // interjected between every openBidiStreamLocked call and
        // emitted ONE STREAM frame per packet. aioquic interop
        // 2026-05-06 qlog: 2898 packets sent in 60s, each carrying
        // exactly one stream frame; server processed streams strictly
        // sequentially at ~1 RTT per stream → 1421/2000 files in 60s
        // before timeout. Holding streamsLock blocks the drain so
        // all N opens land before any drain runs, the writer then
        // packs many STREAM frames into each datagram, and the
        // server sees the burst on the wire.
        conn.streamsLock.withLock {
            paths.map { path ->
                val stream = conn.openBidiStreamLocked()
                stream.send.enqueue(encodeRequest(authority, path))
                stream.send.finish()
                Http3RequestHandle(stream)
            }
        }

    override suspend fun awaitResponse(handle: RequestHandle): GetResponse {
        val stream = (handle as Http3RequestHandle).stream
        val reader = Http3FrameReader()
        var status = 0
        val body = mutableListOf<ByteArray>()
        stream.incoming.collect { chunk ->
            reader.push(chunk)
            while (true) {
                val frame = reader.next() ?: break
                when (frame) {
                    is Http3Frame.Headers -> {
                        val fields = QpackDecoder().decodeFieldSection(frame.qpackPayload)
                        status = fields.firstOrNull { it.first == ":status" }?.second?.toIntOrNull() ?: 0
                    }

                    is Http3Frame.Data -> {
                        body += frame.body
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
        return GetResponse(status = status, body = concat(body))
    }
}

private class Http3RequestHandle(
    val stream: com.vitorpamplona.quic.stream.QuicStream,
) : RequestHandle

/**
 * Serialize a GET request as a single HEADERS frame ready to be enqueued
 * onto a fresh bidi stream. Exposed for unit-testing the wire format
 * without spinning up a QUIC connection.
 */
internal fun encodeRequest(
    authority: String,
    path: String,
): ByteArray {
    val headers =
        listOf(
            ":method" to "GET",
            ":scheme" to "https",
            ":authority" to authority,
            ":path" to path,
        )
    val qpack = QpackEncoder().encodeFieldSection(headers)
    val w = QuicWriter()
    w.writeVarint(Http3FrameType.HEADERS)
    w.writeVarint(qpack.size.toLong())
    w.writeBytes(qpack)
    return w.toByteArray()
}

private fun concat(parts: List<ByteArray>): ByteArray {
    val total = parts.sumOf { it.size }
    val out = ByteArray(total)
    var off = 0
    for (p in parts) {
        p.copyInto(out, off)
        off += p.size
    }
    return out
}
