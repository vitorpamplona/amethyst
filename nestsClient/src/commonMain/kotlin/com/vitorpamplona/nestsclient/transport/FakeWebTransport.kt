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
package com.vitorpamplona.nestsclient.transport

import com.vitorpamplona.nestsclient.transport.FakeWebTransport.Companion.pair
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [WebTransportSession] used by tests for MoQ framing / session logic.
 *
 * A pair of fakes is connected via [pair] — anything written on one side is
 * delivered on the other. This deliberately simulates *success* semantics
 * only (no packet loss, no congestion); the real `:quic`-backed transport
 * exercises those codepaths via its own pipe + interop tests.
 *
 * [incomingDatagrams] and [FakeBidiStream.incoming] use [receiveAsFlow]
 * semantics: a `take(1)` / `first()` followed by a long-running `collect`
 * works on the same underlying channel, and cancelling a consumer does not
 * close the channel for future readers.
 */
class FakeWebTransport private constructor(
    private val outboundDatagrams: Channel<ByteArray>,
    private val inboundDatagrams: Channel<ByteArray>,
    private val outboundBidiStreams: Channel<FakeBidiStream>,
    private val inboundBidiStreams: Channel<FakeBidiStream>,
    private val inboundUniStreams: Channel<WebTransportReadStream>,
    private val outboundUniStreams: Channel<WebTransportReadStream>,
) : WebTransportSession {
    private val stateLock = Mutex()
    private var open = true

    override val isOpen: Boolean get() = open

    override suspend fun openBidiStream(): WebTransportBidiStream {
        stateLock.withLock { check(open) { "session closed" } }
        val localToPeer = Channel<ByteArray>(Channel.BUFFERED)
        val peerToLocal = Channel<ByteArray>(Channel.BUFFERED)
        val local = FakeBidiStream(write = localToPeer, read = peerToLocal)
        val peer = FakeBidiStream(write = peerToLocal, read = localToPeer)
        // Send *peer* half to the other side's inbound queue.
        outboundBidiStreams.send(peer)
        return local
    }

    override fun incomingUniStreams(): Flow<WebTransportReadStream> = inboundUniStreams.receiveAsFlow()

    override suspend fun sendDatagram(payload: ByteArray): Boolean {
        if (!open) return false
        outboundDatagrams.send(payload)
        return true
    }

    override fun incomingDatagrams(): Flow<ByteArray> = inboundDatagrams.receiveAsFlow()

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        stateLock.withLock {
            if (!open) return
            open = false
        }
        outboundDatagrams.close()
        inboundDatagrams.close()
        outboundBidiStreams.close()
        inboundBidiStreams.close()
        inboundUniStreams.close()
        outboundUniStreams.close()
    }

    /**
     * Flow of peer-opened bidi streams (i.e. the local endpoint of a stream the
     * other side created via [openBidiStream]).
     */
    fun peerOpenedBidiStreams(): Flow<FakeBidiStream> = inboundBidiStreams.receiveAsFlow()

    /**
     * Open a uni stream from this side toward the paired peer. Tests use this
     * to simulate a publisher pushing a moq-lite group uni stream — write the
     * full sequence of chunks via [WebTransportWriteStream.write] then call
     * [WebTransportWriteStream.finish] to FIN.
     */
    suspend fun openPeerUniStream(): WebTransportWriteStream {
        stateLock.withLock { check(open) { "session closed" } }
        val pipe = Channel<ByteArray>(Channel.BUFFERED)
        outboundUniStreams.send(FakeReadStream(pipe))
        return ChannelWriteStream(pipe)
    }

    companion object {
        /**
         * Create two linked fakes that act as "client" and "server" endpoints of
         * the same virtual WebTransport session. Datagrams and streams opened on
         * one side appear on the other.
         */
        fun pair(): Pair<FakeWebTransport, FakeWebTransport> {
            val aToBDatagrams = Channel<ByteArray>(Channel.BUFFERED)
            val bToADatagrams = Channel<ByteArray>(Channel.BUFFERED)
            val aToBBidi = Channel<FakeBidiStream>(Channel.BUFFERED)
            val bToABidi = Channel<FakeBidiStream>(Channel.BUFFERED)
            val aToBUni = Channel<WebTransportReadStream>(Channel.BUFFERED)
            val bToAUni = Channel<WebTransportReadStream>(Channel.BUFFERED)

            val a =
                FakeWebTransport(
                    outboundDatagrams = aToBDatagrams,
                    inboundDatagrams = bToADatagrams,
                    outboundBidiStreams = aToBBidi,
                    inboundBidiStreams = bToABidi,
                    inboundUniStreams = bToAUni,
                    outboundUniStreams = aToBUni,
                )
            val b =
                FakeWebTransport(
                    outboundDatagrams = bToADatagrams,
                    inboundDatagrams = aToBDatagrams,
                    outboundBidiStreams = bToABidi,
                    inboundBidiStreams = aToBBidi,
                    inboundUniStreams = aToBUni,
                    outboundUniStreams = bToAUni,
                )
            return a to b
        }
    }
}

class FakeBidiStream internal constructor(
    private val write: Channel<ByteArray>,
    private val read: Channel<ByteArray>,
) : WebTransportBidiStream {
    override fun incoming(): Flow<ByteArray> = read.receiveAsFlow()

    override suspend fun write(chunk: ByteArray) {
        write.send(chunk)
    }

    override suspend fun finish() {
        write.close()
    }
}

class FakeReadStream internal constructor(
    private val read: Channel<ByteArray>,
) : WebTransportReadStream {
    override fun incoming(): Flow<ByteArray> = read.receiveAsFlow()
}

/**
 * Write-only adapter over a [Channel]. Used by [FakeWebTransport.openPeerUniStream]
 * so a test can drive a peer-initiated uni stream by writing chunks then
 * FIN'ing via [finish].
 */
private class ChannelWriteStream(
    private val channel: Channel<ByteArray>,
) : WebTransportWriteStream {
    override suspend fun write(chunk: ByteArray) {
        channel.send(chunk)
    }

    override suspend fun finish() {
        channel.close()
    }
}
