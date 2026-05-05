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

import com.vitorpamplona.quic.connection.recovery.RecoveryToken
import com.vitorpamplona.quic.stream.StreamId
import com.vitorpamplona.quic.tls.InProcessTlsServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step 8 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
 * end-to-end exercise of the retransmit pipeline. Drives the full
 * chain (writer → SentPacket → loss detection → dispatch → drain)
 * by simulating packet loss directly on QuicConnection state, since
 * the in-process pipe doesn't model loss.
 *
 * This is the test that, before this work landed, could not have
 * been written: there was no way to express "MAX_STREAMS_UNI was
 * lost; verify it gets re-emitted". Now there is.
 */
class RetransmitIntegrationTest {
    @Test
    fun maxStreamsUni_lostByPacketThreshold_isRetransmitted() =
        runBlocking {
            val client = handshakedClient()

            // 1. Cross peer-uni half-window so the writer emits a
            //    MAX_STREAMS_UNI in its next drain.
            crossPeerUniHalfWindow(client)
            val capBefore = client.advertisedMaxStreamsUni
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val capAfterFirstDrain = client.advertisedMaxStreamsUni
            assertTrue(capAfterFirstDrain > capBefore, "writer must have advertised a higher cap")

            // 2. Locate the SentPacket carrying the MaxStreamsUni token.
            val msuEntry =
                client.application.sentPackets.entries.firstOrNull { entry ->
                    entry.value.tokens.any { it == RecoveryToken.MaxStreamsUni(maxStreams = capAfterFirstDrain) }
                }
            assertNotNull(msuEntry, "expected SentPacket with MaxStreamsUni token after first drain")
            val msuPn = msuEntry.key

            // 3. Simulate ACK that newly-acks PN = msuPn + 4 (above the
            //    packet-threshold of 3). The MAX_STREAMS_UNI packet is
            //    ACK'd by reordering — its PN < largestAckedPn -
            //    PACKET_THRESHOLD ⇒ declared lost.
            val futurePn = msuPn + 4L
            client.lock.lock()
            try {
                // Inject a phantom SentPacket at futurePn so the loss
                // detector has a credible "newly acked" reference, then
                // ACK exactly that PN.
                client.application.sentPackets[futurePn] =
                    com.vitorpamplona.quic.connection.recovery.SentPacket(
                        packetNumber = futurePn,
                        sentAtMillis = 1L,
                        ackEliciting = true,
                        sizeBytes = 64,
                        tokens =
                            listOf(
                                RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = 0L),
                            ),
                    )
                // Drain the ACK'd packet from the map (simulating the
                // parser path).
                client.application.sentPackets.remove(futurePn)
                client.application.largestAckedPn = futurePn
                client.application.largestAckedSentTimeMs = 1L
                // 4. Run loss detection — msuPn is < futurePn - 3 ⇒ lost.
                val lost =
                    client.lossDetection.detectAndRemoveLost(
                        sentPackets = client.application.sentPackets,
                        largestAckedPn = futurePn,
                        nowMs = 2L,
                    )
                val lostMsuPacket = lost.firstOrNull { it.packetNumber == msuPn }
                assertNotNull(lostMsuPacket, "msuPn=$msuPn must be declared lost (largestAckedPn=$futurePn, threshold=3)")
                // 5. Dispatch lost tokens — pendingMaxStreamsUni gets set.
                client.onTokensLost(lostMsuPacket.tokens)
            } finally {
                client.lock.unlock()
            }
            assertEquals(
                capAfterFirstDrain,
                client.pendingMaxStreamsUni,
                "lost MaxStreamsUni token must populate pendingMaxStreamsUni",
            )

            // 6. Drain again — writer must re-emit the MAX_STREAMS_UNI
            //    in a NEW packet (different PN). The pending* field
            //    is cleared after drain.
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 3L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val retransmitEntry =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it == RecoveryToken.MaxStreamsUni(maxStreams = capAfterFirstDrain) }
                }
            assertNotNull(
                retransmitEntry,
                "retransmit must produce a fresh SentPacket carrying MaxStreamsUni; saw " +
                    newEntries.map { it.value.tokens.map { t -> t::class.simpleName } },
            )
            assertNotEquals(msuPn, retransmitEntry.key, "retransmit must use a NEW packet number")
            assertNull(client.pendingMaxStreamsUni, "pendingMaxStreamsUni must be cleared after retransmit drain")
        }

    @Test
    fun lossDispatch_handlesSupersedeAcrossMultipleEmits() =
        runBlocking {
            // Two consecutive MAX_STREAMS_UNI emissions; the OLDER one
            // is declared lost. Because the newer extension already
            // covers it, the supersede check (in onTokensLost) drops
            // the older lost token without populating pending*.
            val client = handshakedClient()

            // First bump.
            crossPeerUniHalfWindow(client)
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val firstCap = client.advertisedMaxStreamsUni

            // Second bump: open more peer-uni streams to cross the
            // (already extended) threshold again.
            client.lock.lock()
            try {
                client.getOrCreatePeerStreamLocked(StreamId.build(StreamId.Kind.SERVER_UNI, 2))
                client.getOrCreatePeerStreamLocked(StreamId.build(StreamId.Kind.SERVER_UNI, 3))
                client.getOrCreatePeerStreamLocked(StreamId.build(StreamId.Kind.SERVER_UNI, 4))
            } finally {
                client.lock.unlock()
            }
            runCatching { drainOutbound(client, nowMillis = 2L) }
            val secondCap = client.advertisedMaxStreamsUni
            assertTrue(secondCap > firstCap, "second drain must advertise a still-higher cap; saw $firstCap → $secondCap")

            // Now declare the FIRST emit lost via direct dispatch.
            client.lock.lock()
            try {
                client.onTokensLost(listOf(RecoveryToken.MaxStreamsUni(maxStreams = firstCap)))
            } finally {
                client.lock.unlock()
            }
            // Supersede check: firstCap != advertisedMaxStreamsUni (now == secondCap),
            // so pending must remain null.
            assertNull(client.pendingMaxStreamsUni, "older lost extension is superseded by newer emit; no retransmit")
        }

    private fun handshakedClient(): QuicConnection =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config =
                        QuicConnectionConfig(
                            initialMaxStreamsUni = 4,
                            initialMaxStreamsBidi = 4,
                        ),
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
                            initialMaxStreamsBidi = 100,
                            initialMaxStreamsUni = 100,
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
            client
        }

    private fun crossPeerUniHalfWindow(client: QuicConnection) =
        runBlocking {
            client.lock.lock()
            try {
                client.getOrCreatePeerStreamLocked(StreamId.build(StreamId.Kind.SERVER_UNI, 0))
                client.getOrCreatePeerStreamLocked(StreamId.build(StreamId.Kind.SERVER_UNI, 1))
            } finally {
                client.lock.unlock()
            }
        }
}
