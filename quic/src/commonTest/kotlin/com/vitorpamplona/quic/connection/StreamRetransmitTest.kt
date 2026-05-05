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
import com.vitorpamplona.quic.tls.InProcessTlsServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step C of the deferred-follow-ups pass: STREAM data retransmit
 * end-to-end. Verifies the writer emits a Stream token per
 * STREAM frame, the parser dispatches it to markAcked / markLost,
 * and the buffer either releases or re-queues the bytes
 * accordingly.
 */
class StreamRetransmitTest {
    @Test
    fun streamFrame_carriesStreamToken_inSentPacket() =
        runBlocking {
            val client = handshakedClient()
            val stream = client.openUniStream()
            stream.send.enqueue("hello".encodeToByteArray())

            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val streamPacket =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Stream }
                }
            assertNotNull(streamPacket, "writer must record a Stream token in the SentPacket")
            val streamToken =
                streamPacket.value.tokens
                    .filterIsInstance<RecoveryToken.Stream>()
                    .single()
            assertEquals(stream.streamId, streamToken.streamId)
            assertEquals(0L, streamToken.offset)
            assertEquals(5L, streamToken.length)
            assertEquals(false, streamToken.fin)
        }

    @Test
    fun streamData_lostAndRetransmittedOnNextDrain() =
        runBlocking {
            val client = handshakedClient()
            val stream = client.openUniStream()
            stream.send.enqueue("hello".encodeToByteArray())
            // First drain — emits StreamFrame, records SentPacket.
            runCatching { drainOutbound(client, nowMillis = 1L) }

            // Locate the Stream token's carrying packet.
            val firstPacketEntry =
                client.application.sentPackets.entries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Stream }
                }
            assertNotNull(firstPacketEntry, "expected a SentPacket with Stream token after first drain")
            val firstPn = firstPacketEntry.key

            // Simulate loss via direct dispatch.
            client.lock.lock()
            try {
                val streamToken =
                    firstPacketEntry.value.tokens
                        .filterIsInstance<RecoveryToken.Stream>()
                        .single()
                client.onTokensLost(listOf(streamToken))
                // Remove the lost packet from the sent map (the loss
                // detector would have done this).
                client.application.sentPackets.remove(firstPn)
            } finally {
                client.lock.unlock()
            }

            // SendBuffer should have re-queued the bytes for retransmit.
            assertEquals(5, stream.send.readableBytes, "lost 5 bytes should be back in the queue")

            // Second drain — emits the retransmit at the same offset.
            val sizeBeforeReplay = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 2L) }
            val replayEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBeforeReplay)
            val replayPacket =
                replayEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.Stream }
                }
            assertNotNull(replayPacket, "retransmit must produce a fresh SentPacket carrying the Stream token")
            val replayToken =
                replayPacket.value.tokens
                    .filterIsInstance<RecoveryToken.Stream>()
                    .single()
            assertEquals(0L, replayToken.offset, "retransmit must replay original offset (RFC 9000 §13.3 idempotent)")
            assertEquals(5L, replayToken.length)
            assertTrue(replayPacket.key != firstPn, "retransmit uses a fresh PN")
        }

    @Test
    fun streamData_ackedReleasesBuffer() =
        runBlocking {
            val client = handshakedClient()
            val stream = client.openUniStream()
            stream.send.enqueue("hello".encodeToByteArray())
            runCatching { drainOutbound(client, nowMillis = 1L) }

            // Bytes are in-flight. SendBuffer holds them.
            // Now ACK via direct dispatch.
            val packet =
                client.application.sentPackets.entries
                    .first { it.value.tokens.any { t -> t is RecoveryToken.Stream } }
            client.lock.lock()
            try {
                client.onTokensAcked(packet.value.tokens)
            } finally {
                client.lock.unlock()
            }

            // After ACK: enqueue more, observe that the buffer
            // continues from offset 5 (proves the prior bytes were
            // released, sentOffset advanced).
            stream.send.enqueue("world".encodeToByteArray())
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 2L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val nextStreamToken =
                newEntries
                    .flatMap { it.value.tokens }
                    .filterIsInstance<RecoveryToken.Stream>()
                    .firstOrNull()
            assertNotNull(nextStreamToken)
            assertEquals(5L, nextStreamToken.offset, "after ACK, next send picks up at offset 5")
            assertEquals(5L, nextStreamToken.length)
        }

    private fun handshakedClient(): QuicConnection =
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
}
