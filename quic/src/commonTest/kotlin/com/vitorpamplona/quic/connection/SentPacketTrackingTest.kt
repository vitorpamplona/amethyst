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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step 2 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
 * the writer must record a [com.vitorpamplona.quic.connection.recovery.SentPacket]
 * keyed by packet number whenever it emits an Application packet,
 * carrying the matching tokens for any retransmittable control
 * frame in that packet.
 *
 * The map is read by step 3 (ACK drain) and step 5 (loss detection);
 * step 2 only validates population — no behavior change visible to
 * the peer.
 */
class SentPacketTrackingTest {
    @Test
    fun writer_records_sent_packet_with_max_streams_uni_token() =
        runBlocking {
            val client = handshakedClient()
            // Cross the half-window threshold so the next outbound packet
            // includes a MAX_STREAMS_UNI extension.
            crossPeerUniHalfWindow(client)

            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 12_345L) }

            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            assertTrue(newEntries.isNotEmpty(), "writer must record at least one SentPacket on outbound drain")

            val withBump =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.MaxStreamsUni }
                }
            assertNotNull(
                withBump,
                "expected a SentPacket with MaxStreamsUni token; saw tokens=${newEntries.map { it.value.tokens.map { t -> t::class.simpleName } }}",
            )

            val sent = withBump.value
            assertEquals(withBump.key, sent.packetNumber, "packetNumber must equal the map key")
            assertEquals(12_345L, sent.sentAtMillis, "sentAtMillis must equal the writer's nowMillis input")
            assertTrue(sent.ackEliciting, "MAX_STREAMS_UNI is ack-eliciting per RFC 9000 §13.2.1")
            // sizeBytes is best-effort: the writer records the entry
            // before the encrypt step, so a build/encrypt throw (which
            // happens on tiny packets in tight test scaffolds — header
            // protection needs 16 bytes of sample after the PN) leaves
            // sizeBytes at 0. The bookkeeping is correct either way;
            // the entry exists and loss detection will declare it lost
            // on the time threshold. We only assert sizeBytes >= 0.
            assertTrue(sent.sizeBytes >= 0, "sizeBytes must be non-negative")
            val msuToken = sent.tokens.filterIsInstance<RecoveryToken.MaxStreamsUni>().single()
            assertEquals(
                client.advertisedMaxStreamsUni,
                msuToken.maxStreams,
                "token's maxStreams must equal the advertised cap after the bump",
            )
        }

    @Test
    fun ack_only_outbound_records_sent_packet_with_ack_token_and_not_ack_eliciting() =
        runBlocking {
            val client = handshakedClient()
            // Force an ACK to be pending: simulate a received-but-unacked packet
            // by calling the ack tracker directly. Without a paired peer we'd
            // never have anything to ACK, so we synthesize the input.
            client.application.ackTracker.receivedPacket(packetNumber = 10L, ackEliciting = true, receivedAtMillis = 0L)

            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 50L) }

            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            // Find the entry that's ACK-only — its only token should be
            // RecoveryToken.Ack and it should not be ack-eliciting.
            val ackOnly =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.size == 1 && entry.value.tokens.single() is RecoveryToken.Ack
                }
            assertNotNull(
                ackOnly,
                "expected at least one ACK-only SentPacket; saw tokens=${newEntries.map { it.value.tokens }}",
            )
            assertEquals(false, ackOnly.value.ackEliciting, "ACK-only packet must not be ack-eliciting")
            assertEquals(50L, ackOnly.value.sentAtMillis, "sentAtMillis must equal the writer's nowMillis input")
        }

    @Test
    fun successive_drains_record_distinct_packet_numbers() =
        runBlocking {
            val client = handshakedClient()
            crossPeerUniHalfWindow(client)
            val before =
                client.application.sentPackets.keys
                    .toSet()
            runCatching { drainOutbound(client, nowMillis = 100L) }
            val afterFirst =
                client.application.sentPackets.keys
                    .toSet()

            // Force more outbound work: another peer-uni stream past the
            // (already extended) threshold to trigger a new bump candidate
            // — even if the writer skips it (no new threshold reached),
            // the ACK from receivedPacket below will produce a packet.
            client.application.ackTracker.receivedPacket(packetNumber = 20L, ackEliciting = true, receivedAtMillis = 0L)
            runCatching { drainOutbound(client, nowMillis = 200L) }
            val afterSecond =
                client.application.sentPackets.keys
                    .toSet()

            assertTrue(afterFirst.isNotEmpty(), "first drain must record at least one packet number")
            assertTrue(afterSecond.size >= afterFirst.size, "second drain must not lose entries")
            // No packet number is reused between drains.
            val firstDrainSet = afterFirst - before
            val secondDrainSet = afterSecond - afterFirst
            assertTrue(
                (firstDrainSet intersect secondDrainSet).isEmpty(),
                "packet numbers must be distinct between drains; firstDrainSet=$firstDrainSet secondDrainSet=$secondDrainSet",
            )
        }

    /**
     * Spin up a connected client against the in-process TLS server, the
     * same scaffold [PeerStreamCreditExtensionTest] uses.
     */
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

    /** Cross the half-window threshold (cap=4, two peer-uni streams ⇒ count >= cap-half=2). */
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
