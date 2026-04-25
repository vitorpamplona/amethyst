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

import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.stream.QuicStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Container that bundles a [QuicConnection], its [QuicConnectionDriver], and
 * the QUIC stream id of the CONNECT bidi (the WT session id).
 *
 * Application code (nestsClient's MoQ layer) gets at this via the
 * [QuicWebTransportFactory] adapter which exposes the platform-agnostic
 * [com.vitorpamplona.nestsclient.transport.WebTransportSession] interface.
 */
class QuicWebTransportSessionState(
    val connection: QuicConnection,
    val driver: QuicConnectionDriver,
    val connectStreamId: Long,
    /**
     * Scope used to spawn the peer-stream demux. Defaults to a SupervisorJob
     * so a single misbehaving stream doesn't tear down the session.
     */
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** True until [close] is called or the underlying QUIC connection terminates. */
    val isOpen: Boolean
        get() = connection.status == QuicConnection.Status.CONNECTED

    private val demux: WtPeerStreamDemux = WtPeerStreamDemux(connectStreamId, scope)

    /**
     * Completes once a WT_CLOSE_SESSION capsule arrives on the CONNECT bidi.
     * Application code can `await()` this to detect a peer-initiated graceful
     * close and tear down its own state. Stays uncompleted for the entire
     * lifetime of a healthy session.
     */
    private val peerCloseDeferred = CompletableDeferred<WtCloseSession>()

    /**
     * Mirror of the close-capsule contents that is safe to read without the
     * opt-in `getCompleted()` API. Set in the same place we complete
     * [peerCloseDeferred]; non-null once a WT_CLOSE_SESSION has been observed.
     */
    @Volatile
    private var peerCloseSnapshot: WtCloseSession? = null

    init {
        // Spawn the dispatcher that routes peer-initiated streams through the
        // demux. Without this, the server's CONTROL stream (carrying SETTINGS)
        // would be handed to the application, which would interpret SETTINGS
        // bytes as MoQ frames and break framing.
        scope.launch {
            // awaitIncomingPeerStream suspends on a CONFLATED channel that the
            // parser fires whenever it appends a peer stream — replaces the
            // earlier delay(5) busy-loop. Returns null when the connection
            // closes, which is our exit condition.
            while (true) {
                val s = connection.awaitIncomingPeerStream() ?: break
                demux.process(s)
            }
        }

        // Spawn a reader on the CONNECT bidi that decodes capsules. The
        // CONNECT stream carries WT_CLOSE_SESSION (graceful peer close) and
        // possibly WT_DRAIN_SESSION; without this reader, peer-initiated
        // close goes silent and the application keeps trying to send on a
        // half-closed session.
        scope.launch {
            val connectStream = connection.streamById(connectStreamId) ?: return@launch
            val capsuleReader = CapsuleReader()
            try {
                connectStream.incoming.collect { chunk ->
                    capsuleReader.push(chunk)
                    while (true) {
                        val capsule = capsuleReader.next() ?: break
                        if (capsule is WtCloseSession) {
                            // Complete on first WT_CLOSE_SESSION; subsequent
                            // capsules on the same stream are ignored.
                            if (peerCloseSnapshot == null) {
                                peerCloseSnapshot = capsule
                                peerCloseDeferred.complete(capsule)
                            }
                            return@collect
                        }
                        // Other capsule types (DRAIN, unknown) are currently
                        // observed but not surfaced — extend here if/when the
                        // application needs them.
                    }
                }
            } catch (_: Throwable) {
                // Stream closed before a capsule arrived. Leave peerCloseDeferred
                // uncompleted — the connection-level close path is the source
                // of truth in that case.
            }
        }
    }

    /** Server SETTINGS once received on the H3 control stream; null until then. */
    val peerSettings get() = demux.peerSettings

    /**
     * Server-sent GOAWAY stream id, if one has arrived. Null until the H3
     * CONTROL stream produces a GOAWAY frame. Applications should treat
     * non-null as "stop opening new streams; existing ones may still finish."
     */
    val peerGoawayStreamId get() = demux.peerGoawayStreamId

    /**
     * The WT_CLOSE_SESSION capsule the peer sent on the CONNECT bidi, or null
     * if no graceful close has arrived yet. Applications wanting to react
     * synchronously can `peerCloseSession()` and check for null; coroutines
     * wanting to suspend until close should use [awaitPeerClose].
     */
    val peerCloseSession: WtCloseSession?
        get() = peerCloseSnapshot

    /** Suspends until a peer-initiated WT_CLOSE_SESSION arrives. */
    suspend fun awaitPeerClose(): WtCloseSession = peerCloseDeferred.await()

    /** Flow of peer-initiated WT streams whose framing prefix has been stripped. */
    val incomingStrippedStreams: Flow<StrippedWtStream> get() = demux.incomingStrippedStreams

    /** Open a new client-initiated bidirectional WebTransport stream. */
    suspend fun openBidiStream(): QuicStream {
        val s = connection.openBidiStream()
        // Prefix bytes go onto the new stream first.
        s.send.enqueue(encodeWtBidiStreamPrefix(connectStreamId))
        driver.wakeup()
        return s
    }

    /** Open a new client-initiated unidirectional WebTransport stream. */
    suspend fun openUniStream(): QuicStream {
        val s = connection.openUniStream()
        s.send.enqueue(encodeWtUniStreamPrefix(connectStreamId))
        driver.wakeup()
        return s
    }

    /** Send a WebTransport datagram via QUIC's datagram extension. */
    suspend fun sendDatagram(payload: ByteArray) {
        val wrapped = WtDatagram.encode(connectStreamId, payload)
        connection.queueDatagram(wrapped)
        driver.wakeup()
    }

    suspend fun pollIncomingDatagram(): ByteArray? {
        val raw = connection.pollIncomingDatagram() ?: return null
        val decoded = WtDatagram.decode(raw) ?: return null
        if (decoded.sessionStreamId != connectStreamId) return null
        return decoded.payload
    }

    /**
     * @deprecated Use [incomingStrippedStreams] which yields streams whose
     * framing prefix (CONTROL/QPACK/WT type bytes + quarter session id) has
     * already been stripped. Direct callers of this would receive raw peer
     * streams including the server's CONTROL stream, with SETTINGS bytes
     * interpreted as application data.
     */
    @Deprecated("Use incomingStrippedStreams instead", ReplaceWith("incomingStrippedStreams"))
    suspend fun pollIncomingPeerStream(): QuicStream? = connection.pollIncomingPeerStream()

    suspend fun close(
        errorCode: Int = 0,
        reason: String = "",
    ) {
        connection.streamById(connectStreamId)?.let {
            it.send.enqueue(encodeCloseSessionCapsule(errorCode, reason))
            it.send.finish()
        }
        driver.close()
    }
}
