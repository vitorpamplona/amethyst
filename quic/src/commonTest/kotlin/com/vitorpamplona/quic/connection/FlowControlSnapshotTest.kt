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

import com.vitorpamplona.quic.tls.InProcessTlsServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the diagnostic [QuicConnection.flowControlSnapshot] surface:
 *
 *   - Before the handshake, peer-TP fields are null and the credit /
 *     index counters are zero.
 *   - After the handshake completes against an [InProcessTlsServer]
 *     advertising specific transport parameters, the snapshot reflects
 *     exactly those values.
 *   - Allocating uni / bidi streams advances the local index counters.
 *   - Enqueueing bytes to a stream's send buffer (without a writer
 *     drain) shows up as `totalEnqueuedNotSentBytes`.
 *
 * Used by `nestsClient/.../SendTraceScenario` to dump the connection's
 * flow-control state at known points during a sweep run; the cliff
 * investigation in
 * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`
 * relies on these fields to localise where data piles up.
 */
class FlowControlSnapshotTest {
    @Test
    fun snapshot_before_handshake_has_null_peer_tps_and_zero_counters() {
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            val snap = client.flowControlSnapshot()
            assertNull(snap.peerInitialMaxData, "no peer TPs before handshake")
            assertNull(snap.peerInitialMaxStreamDataUni)
            assertNull(snap.peerInitialMaxStreamsUni)
            assertEquals(0L, snap.sendConnectionFlowCredit)
            assertEquals(0L, snap.sendConnectionFlowConsumed)
            assertEquals(0L, snap.peerMaxStreamsUniCurrent)
            assertEquals(0L, snap.peerMaxStreamsBidiCurrent)
            assertEquals(0L, snap.nextLocalUniIndex)
            assertEquals(0L, snap.nextLocalBidiIndex)
            assertEquals(0L, snap.totalEnqueuedNotSentBytes)
            assertEquals(0, snap.streamsWithPendingBytes)
            assertEquals(0, snap.totalStreamsTracked)
        }
    }

    @Test
    fun snapshot_after_handshake_reflects_peer_transport_parameters() {
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 1_500_000,
                            initialMaxStreamDataBidiLocal = 200_000,
                            initialMaxStreamDataBidiRemote = 250_000,
                            initialMaxStreamDataUni = 300_000,
                            initialMaxStreamsBidi = 17,
                            initialMaxStreamsUni = 19,
                            initialSourceConnectionId = serverScid.bytes,
                            originalDestinationConnectionId = client.destinationConnectionId.bytes,
                        ).encode(),
                )
            val pipe =
                InMemoryQuicPipe(
                    client = client,
                    initialDcid = client.destinationConnectionId.bytes,
                    serverScid = serverScid,
                    tlsServer = tlsServer,
                )
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)

            val snap = client.flowControlSnapshot()
            assertEquals(1_500_000L, snap.peerInitialMaxData)
            assertEquals(300_000L, snap.peerInitialMaxStreamDataUni)
            assertEquals(250_000L, snap.peerInitialMaxStreamDataBidiRemote)
            assertEquals(19L, snap.peerInitialMaxStreamsUni)
            assertEquals(17L, snap.peerInitialMaxStreamsBidi)
            // Initial credit equals the initial cap until inbound MAX_DATA
            // raises it — no traffic has been sent yet.
            assertEquals(1_500_000L, snap.sendConnectionFlowCredit)
            assertEquals(0L, snap.sendConnectionFlowConsumed)
            assertEquals(19L, snap.peerMaxStreamsUniCurrent)
            assertEquals(17L, snap.peerMaxStreamsBidiCurrent)
            assertEquals(0L, snap.nextLocalUniIndex)
            assertEquals(0L, snap.nextLocalBidiIndex)
        }
    }

    @Test
    fun snapshot_tracks_enqueued_bytes_and_local_stream_indexes() {
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 1_000_000,
                            initialMaxStreamDataBidiLocal = 100_000,
                            initialMaxStreamDataBidiRemote = 100_000,
                            initialMaxStreamDataUni = 100_000,
                            initialMaxStreamsBidi = 10,
                            initialMaxStreamsUni = 10,
                            initialSourceConnectionId = serverScid.bytes,
                            originalDestinationConnectionId = client.destinationConnectionId.bytes,
                        ).encode(),
                )
            val pipe =
                InMemoryQuicPipe(
                    client = client,
                    initialDcid = client.destinationConnectionId.bytes,
                    serverScid = serverScid,
                    tlsServer = tlsServer,
                )
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)

            val s1 = client.openUniStream()
            val s2 = client.openUniStream()
            client.openBidiStream()
            s1.send.enqueue(ByteArray(100))
            s2.send.enqueue(ByteArray(50))

            val snap = client.flowControlSnapshot()
            assertEquals(2L, snap.nextLocalUniIndex)
            assertEquals(1L, snap.nextLocalBidiIndex)
            assertTrue(
                snap.totalStreamsTracked >= 3,
                "3 client-initiated streams should be tracked, saw ${snap.totalStreamsTracked}",
            )
            assertEquals(150L, snap.totalEnqueuedNotSentBytes)
            assertEquals(2, snap.streamsWithPendingBytes)
        }
    }
}
