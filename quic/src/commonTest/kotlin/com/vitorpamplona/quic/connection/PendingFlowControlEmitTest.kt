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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step 4 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
 * the writer drains `QuicConnection.pendingMax*` fields ahead of the
 * normal threshold check, re-emitting a fresh frame + token for each
 * pending entry and clearing it.
 *
 * Mirrors neqo's `fc.rs` retransmit tests
 * (`need_max_allowed_frame_after_loss`, `lost_after_increase`,
 * `multiple_retries_after_frame_pending_is_set`,
 * `new_retired_before_loss`). The `pending*` fields are populated
 * directly here; step 6 will populate them via the loss dispatcher.
 */
class PendingFlowControlEmitTest {
    @Test
    fun pendingMaxStreamsUni_drainEmitsFrameAndToken() =
        runBlocking {
            val client = handshakedClient()
            client.lock.lock()
            try {
                client.pendingMaxStreamsUni = 150L
            } finally {
                client.lock.unlock()
            }

            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)

            // Some new SentPacket must carry a MaxStreamsUni token
            // with the pending value.
            val withRetransmit =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it == RecoveryToken.MaxStreamsUni(maxStreams = 150L) }
                }
            assertNotNull(
                withRetransmit,
                "expected re-emit of MaxStreamsUni(150) but saw " +
                    newEntries.map { it.value.tokens.map { t -> t::class.simpleName } },
            )
            // Pending must be cleared after emit.
            assertNull(client.pendingMaxStreamsUni)
        }

    @Test
    fun pendingMaxStreamsBidi_drainEmitsFrameAndToken(): Unit =
        runBlocking {
            val client = handshakedClient()
            client.lock.lock()
            try {
                client.pendingMaxStreamsBidi = 200L
            } finally {
                client.lock.unlock()
            }
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val withRetransmit =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it == RecoveryToken.MaxStreamsBidi(maxStreams = 200L) }
                }
            assertNotNull(withRetransmit, "expected re-emit of MaxStreamsBidi(200)")
            assertNull(client.pendingMaxStreamsBidi)
        }

    @Test
    fun pendingMaxData_drainEmitsFrameAndToken(): Unit =
        runBlocking {
            val client = handshakedClient()
            client.lock.lock()
            try {
                client.pendingMaxData = 5_000_000L
            } finally {
                client.lock.unlock()
            }
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val withRetransmit =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it == RecoveryToken.MaxData(maxData = 5_000_000L) }
                }
            assertNotNull(withRetransmit, "expected re-emit of MaxData(5_000_000)")
            assertNull(client.pendingMaxData)
        }

    @Test
    fun pendingMaxStreamData_perStreamDrain() =
        runBlocking {
            val client = handshakedClient()
            client.lock.lock()
            try {
                client.pendingMaxStreamData[3L] = 1_024L
                client.pendingMaxStreamData[7L] = 2_048L
            } finally {
                client.lock.unlock()
            }
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val seenTokens =
                newEntries
                    .flatMap { it.value.tokens }
                    .filterIsInstance<RecoveryToken.MaxStreamData>()
                    .toSet()
            assertEquals(
                setOf(
                    RecoveryToken.MaxStreamData(streamId = 3L, maxData = 1_024L),
                    RecoveryToken.MaxStreamData(streamId = 7L, maxData = 2_048L),
                ),
                seenTokens,
            )
            assertTrue(client.pendingMaxStreamData.isEmpty())
        }

    @Test
    fun multiplePending_drainEmitsAllInOnePacket(): Unit =
        runBlocking {
            val client = handshakedClient()
            client.lock.lock()
            try {
                client.pendingMaxStreamsUni = 150L
                client.pendingMaxStreamsBidi = 200L
                client.pendingMaxData = 1_000_000L
            } finally {
                client.lock.unlock()
            }
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            // All three retransmits should land in one packet (writer
            // drains them sequentially before any threshold check).
            val packetTokens =
                newEntries
                    .firstOrNull { entry ->
                        entry.value.tokens.any { it is RecoveryToken.MaxStreamsUni } &&
                            entry.value.tokens.any { it is RecoveryToken.MaxStreamsBidi } &&
                            entry.value.tokens.any { it is RecoveryToken.MaxData }
                    }
            assertNotNull(packetTokens, "expected one packet carrying all three retransmits")
            assertNull(client.pendingMaxStreamsUni)
            assertNull(client.pendingMaxStreamsBidi)
            assertNull(client.pendingMaxData)
        }

    @Test
    fun noPending_drainDoesNotEmitRetransmits() =
        runBlocking {
            val client = handshakedClient()
            // Nothing to retransmit — no SentPacket should carry a
            // MAX_STREAMS / MAX_DATA / MAX_STREAM_DATA token unless
            // the rolling-extension threshold check fires (which it
            // shouldn't here — fresh handshake, no peer activity).
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val anyExtensionToken =
                newEntries
                    .flatMap { it.value.tokens }
                    .any { it is RecoveryToken.MaxStreamsUni || it is RecoveryToken.MaxStreamsBidi || it is RecoveryToken.MaxData || it is RecoveryToken.MaxStreamData }
            assertEquals(false, anyExtensionToken, "no pending → no extension tokens")
        }

    @Test
    fun pendingDrainBeforeThresholdCheck_supersedeOrderObservable(): Unit =
        runBlocking {
            // Set pendingMaxStreamsUni to a value that's below the current
            // advertised cap. The writer drains it as-is — supersede check
            // is in step 6 (the setter side), not here.
            val client = handshakedClient()
            client.lock.lock()
            try {
                client.pendingMaxStreamsUni = 50L
            } finally {
                client.lock.unlock()
            }
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val withRetransmit =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it == RecoveryToken.MaxStreamsUni(maxStreams = 50L) }
                }
            assertNotNull(
                withRetransmit,
                "writer must drain pendingMaxStreamsUni unconditionally; supersede check belongs to the setter (step 6)",
            )
        }

    @Test
    fun pendingClearedAcrossDrains() =
        runBlocking {
            val client = handshakedClient()
            client.lock.lock()
            try {
                client.pendingMaxStreamsUni = 150L
            } finally {
                client.lock.unlock()
            }
            // Drain once: pending consumed.
            runCatching { drainOutbound(client, nowMillis = 1L) }
            assertNull(client.pendingMaxStreamsUni)

            // Drain again with no new pending: no retransmit token in any
            // SentPacket recorded by this drain.
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 2L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val msuTokens =
                newEntries.flatMap { it.value.tokens }.filterIsInstance<RecoveryToken.MaxStreamsUni>()
            assertEquals(emptyList(), msuTokens, "second drain must not re-emit cleared pending")
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
