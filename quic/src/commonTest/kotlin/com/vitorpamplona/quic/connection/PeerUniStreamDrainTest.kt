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

import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.stream.StreamId
import com.vitorpamplona.quic.tls.InProcessTlsServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression coverage for the multiplexing-interop tear-down (audit-4 #3
 * "slow consumer" overflow on peer-initiated uni streams).
 *
 * Reproduction of the production symptom:
 *
 *   The interop runner's `multiplexing` testcase opens many parallel
 *   client-bidi GET streams via an H3 client. The H3 client opens its
 *   three local uni streams (control + QPACK encoder + QPACK decoder)
 *   per RFC 9114 §6.2.1 but **does not consume the server's three
 *   counterpart peer-initiated uni streams**. Their bytes
 *   (SETTINGS, dynamic-table inserts, ack signals) are routed by
 *   [QuicConnectionParser] into each stream's bounded
 *   `incomingChannel` (capacity 64). Once the QPACK encoder
 *   stream's burst of dynamic-table inserts saturates it, the next
 *   delivery trips the audit-4 #3 escape hatch:
 *
 *     conn.markClosedExternally("INTERNAL_ERROR: stream … consumer
 *                               overflowed incoming channel
 *                               (slow consumer)")
 *
 *   and the entire connection dies after ~4.5 s with zero requests
 *   completed.
 *
 * The test pair below pins both halves of the contract:
 *
 *   - [pre_fix_no_consumer_overflows_and_tears_down_connection] — without
 *     a consumer the connection MUST close with the documented reason.
 *   - [drainPeerInitiatedUniStreamsIntoBlackHole_keeps_connection_alive] —
 *     with [drainPeerInitiatedUniStreamsIntoBlackHole] running the
 *     connection MUST stay CONNECTED and absorb the same byte volume.
 *
 * The fix philosophy (variant B from the prompt's three-way menu): the
 * `:quic` library stays strict about backpressure — silently dropping
 * app-data bytes is worse than failing fast. The new public helper is
 * the explicit "I do not care about these particular peer streams"
 * opt-in for an H3 GET client that runs with the QPACK dynamic table
 * off. Default behaviour is unchanged.
 */
class PeerUniStreamDrainTest {
    @Test
    fun pre_fix_no_consumer_overflows_and_tears_down_connection() {
        runBlocking {
            val client = buildClient()
            val pipe = buildPipe(client)
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)

            // Push 65 chunks on a server-initiated uni stream — one more
            // than the per-stream incomingChannel capacity (64). The
            // first 64 land; the 65th overflows trySend, sets
            // QuicStream.overflowed, and the parser maps that to
            // markClosedExternally with the documented reason.
            //
            // Each chunk fits comfortably under the per-stream receive
            // limit (initialMaxStreamDataUni = 1 MiB) so the prior
            // receive-limit guard does NOT fire — this test is about
            // the *channel* overflow specifically, not flow control.
            val streamId = StreamId.build(StreamId.Kind.SERVER_UNI, 0)
            var offset = 0L
            for (i in 0 until 65) {
                val chunk = ByteArray(8) { (i + it).toByte() }
                val frame = StreamFrame(streamId = streamId, offset = offset, data = chunk, fin = false)
                offset += chunk.size.toLong()
                val packet = pipe.buildServerApplicationDatagram(listOf(frame))
                assertNotEquals(null, packet, "server has app keys after handshake")
                feedDatagram(client, packet!!, nowMillis = 0L)
            }

            // The 65th chunk must have torn the connection down.
            assertEquals(
                QuicConnection.Status.CLOSED,
                client.status,
                "without a peer-uni-stream consumer the audit-4 #3 escape " +
                    "hatch must fire on the 65th chunk",
            )
        }
    }

    @Test
    fun drainPeerInitiatedUniStreamsIntoBlackHole_keeps_connection_alive() {
        runBlocking {
            val client = buildClient()
            val pipe = buildPipe(client)
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)

            // Wire the explicit drainer BEFORE pushing the bytes.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                client.drainPeerInitiatedUniStreamsIntoBlackHole(scope)

                // Same volume as the pre-fix test, except this time we go
                // a long way past the channel capacity to prove sustained
                // operation (4× the bound). If the drainer were absent the
                // connection would have died at chunk 65; with the
                // drainer reading them as fast as the parser delivers, no
                // backpressure builds up.
                val streamId = StreamId.build(StreamId.Kind.SERVER_UNI, 0)
                var offset = 0L
                for (i in 0 until 256) {
                    val chunk = ByteArray(8) { (i + it).toByte() }
                    val frame = StreamFrame(streamId = streamId, offset = offset, data = chunk, fin = false)
                    offset += chunk.size.toLong()
                    val packet = pipe.buildServerApplicationDatagram(listOf(frame))!!
                    feedDatagram(client, packet, nowMillis = 0L)
                    // Yield occasionally so the drainer coroutine actually
                    // gets a chance to consume — feedDatagram is synchronous
                    // and the drainer launched with Dispatchers.Default needs
                    // a scheduling tick.
                    if (i % 16 == 15) {
                        withTimeout(2_000) { delay(1) }
                    }
                }
                // Ensure the drainer has caught up before we sample status.
                withTimeout(2_000) { delay(50) }

                assertEquals(
                    QuicConnection.Status.CONNECTED,
                    client.status,
                    "with drainPeerInitiatedUniStreamsIntoBlackHole the " +
                        "connection must absorb arbitrary peer-uni traffic " +
                        "without overflowing the channel; saw closeReason=" +
                        "${client.closeReason}",
                )
            } finally {
                scope.cancel()
            }
        }
    }

    private fun buildClient(): QuicConnection =
        QuicConnection(
            serverName = "example.test",
            config = QuicConnectionConfig(),
            tlsCertificateValidator =
                com.vitorpamplona.quic.tls
                    .PermissiveCertificateValidator(),
        )

    private fun buildPipe(client: QuicConnection): InMemoryQuicPipe {
        val serverScid = ConnectionId.random(8)
        val tlsServer =
            InProcessTlsServer(
                transportParameters =
                    TransportParameters(
                        initialMaxData = 10_000_000,
                        initialMaxStreamDataBidiLocal = 1_000_000,
                        initialMaxStreamDataBidiRemote = 1_000_000,
                        initialMaxStreamDataUni = 1_000_000,
                        initialMaxStreamsBidi = 16,
                        initialMaxStreamsUni = 16,
                        initialSourceConnectionId = serverScid.bytes,
                        originalDestinationConnectionId = client.destinationConnectionId.bytes,
                    ).encode(),
            )
        return InMemoryQuicPipe(
            client = client,
            initialDcid = client.destinationConnectionId.bytes,
            serverScid = serverScid,
            tlsServer = tlsServer,
        )
    }
}
