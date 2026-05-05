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
 * Public-API + writer drain for RESET_STREAM / STOP_SENDING /
 * NEW_CONNECTION_ID. Verifies:
 *
 *   1. App calls [com.vitorpamplona.quic.stream.QuicStream.resetStream]
 *      / [com.vitorpamplona.quic.stream.QuicStream.stopSending]; the
 *      next writer drain emits the matching frame with a token.
 *   2. Loss dispatch re-flags the per-stream emit-pending bit; next
 *      drain re-emits.
 *   3. ACK dispatch latches resetAcked / stopSendingAcked; subsequent
 *      stale loss tokens are dropped.
 *   4. NEW_CONNECTION_ID retransmit drains
 *      [QuicConnection.pendingNewConnectionId].
 */
class ResetStopSendingEmitTest {
    @Test
    fun resetStream_emitsResetStreamFrameAndToken() =
        runBlocking {
            val client = handshakedClient()
            val stream = client.openUniStream()
            stream.send.enqueue("partial-upload".encodeToByteArray())
            // App cancels mid-write.
            stream.resetStream(errorCode = 7L)

            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val tokenEntry =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.ResetStream }
                }
            assertNotNull(tokenEntry, "resetStream() must produce a SentPacket carrying ResetStream")
            val token =
                tokenEntry.value.tokens
                    .filterIsInstance<RecoveryToken.ResetStream>()
                    .single()
            assertEquals(stream.streamId, token.streamId)
            assertEquals(7L, token.errorCode)
            // finalSize == nextOffset at reset time. We enqueued 14 bytes.
            assertEquals(14L, token.finalSize, "finalSize is the largest enqueued offset")
            // Pending flag cleared after emit.
            assertEquals(false, stream.resetEmitPending)
            assertEquals(false, stream.resetAcked, "ACK hasn't arrived yet")
        }

    @Test
    fun resetStream_retransmittedOnLoss() =
        runBlocking {
            val client = handshakedClient()
            val stream = client.openUniStream()
            stream.resetStream(errorCode = 13L)
            runCatching { drainOutbound(client, nowMillis = 1L) }

            val firstEntry =
                client.application.sentPackets.entries
                    .first { it.value.tokens.any { it is RecoveryToken.ResetStream } }
            val token =
                firstEntry.value.tokens
                    .filterIsInstance<RecoveryToken.ResetStream>()
                    .single()

            // Simulate loss.
            client.lock.lock()
            try {
                client.onTokensLost(listOf(token))
                client.application.sentPackets.remove(firstEntry.key)
            } finally {
                client.lock.unlock()
            }
            // Per-stream emit-pending should be re-flagged.
            assertTrue(stream.resetEmitPending, "loss must re-flag resetEmitPending")

            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 2L) }
            val replay =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
                    .firstOrNull { entry -> entry.value.tokens.any { it is RecoveryToken.ResetStream } }
            assertNotNull(replay, "retransmit must produce a fresh SentPacket carrying ResetStream")
            val replayToken =
                replay.value.tokens
                    .filterIsInstance<RecoveryToken.ResetStream>()
                    .single()
            assertEquals(token, replayToken, "retransmit replays identical RESET_STREAM contents")
        }

    @Test
    fun resetStream_acked_doesNotReEmitOnStaleLoss() =
        runBlocking {
            // ACK comes first, then a stale loss token arrives. The
            // dispatcher must NOT re-flag resetEmitPending.
            val client = handshakedClient()
            val stream = client.openUniStream()
            stream.resetStream(errorCode = 99L)
            runCatching { drainOutbound(client, nowMillis = 1L) }

            val packet =
                client.application.sentPackets.entries
                    .first { it.value.tokens.any { it is RecoveryToken.ResetStream } }
            val token =
                packet.value.tokens
                    .filterIsInstance<RecoveryToken.ResetStream>()
                    .single()

            // ACK first.
            client.lock.lock()
            try {
                client.onTokensAcked(listOf(token))
            } finally {
                client.lock.unlock()
            }
            assertEquals(true, stream.resetAcked)
            assertEquals(false, stream.resetEmitPending)

            // Now a stale loss notification arrives. Defensive: drop.
            client.lock.lock()
            try {
                client.onTokensLost(listOf(token))
            } finally {
                client.lock.unlock()
            }
            assertEquals(false, stream.resetEmitPending, "stale loss after ACK must not re-flag emit-pending")
        }

    @Test
    fun stopSending_emitsStopSendingFrameAndToken() =
        runBlocking {
            val client = handshakedClient()
            val stream = client.openBidiStream()
            stream.stopSending(errorCode = 5L)

            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val newEntries =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
            val tokenEntry =
                newEntries.firstOrNull { entry ->
                    entry.value.tokens.any { it is RecoveryToken.StopSending }
                }
            assertNotNull(tokenEntry)
            val token =
                tokenEntry.value.tokens
                    .filterIsInstance<RecoveryToken.StopSending>()
                    .single()
            assertEquals(stream.streamId, token.streamId)
            assertEquals(5L, token.errorCode)
            assertEquals(false, stream.stopSendingEmitPending)
        }

    @Test
    fun resetStream_secondCallIsNoOp_finalSizeFrozen() =
        runBlocking {
            // RFC 9000 §3.5: finalSize is fixed at first emission.
            // A second resetStream() must not overwrite resetState
            // — otherwise a retransmit after additional enqueue would
            // replay with a larger finalSize, triggering FINAL_SIZE_ERROR.
            val client = handshakedClient()
            val stream = client.openUniStream()
            stream.send.enqueue("first".encodeToByteArray()) // 5 bytes
            stream.resetStream(errorCode = 1L)

            // App enqueues more bytes (writer's send loop is racing) and
            // calls resetStream again with a different code.
            stream.send.enqueue("more-bytes".encodeToByteArray()) // +10 bytes
            stream.resetStream(errorCode = 99L)

            runCatching { drainOutbound(client, nowMillis = 1L) }
            val tokenEntry =
                client.application.sentPackets.entries
                    .firstOrNull { it.value.tokens.any { t -> t is RecoveryToken.ResetStream } }
            assertNotNull(tokenEntry)
            val token =
                tokenEntry.value.tokens
                    .filterIsInstance<RecoveryToken.ResetStream>()
                    .single()
            assertEquals(1L, token.errorCode, "first errorCode wins")
            assertEquals(5L, token.finalSize, "finalSize frozen at first call (RFC 9000 §3.5)")
        }

    @Test
    fun stopSending_secondCallIsNoOp() =
        runBlocking {
            val client = handshakedClient()
            val stream = client.openBidiStream()
            stream.stopSending(errorCode = 5L)
            stream.stopSending(errorCode = 42L)

            runCatching { drainOutbound(client, nowMillis = 1L) }
            val tokenEntry =
                client.application.sentPackets.entries
                    .firstOrNull { it.value.tokens.any { t -> t is RecoveryToken.StopSending } }
            assertNotNull(tokenEntry)
            val token =
                tokenEntry.value.tokens
                    .filterIsInstance<RecoveryToken.StopSending>()
                    .single()
            assertEquals(5L, token.errorCode, "first errorCode wins")
        }

    @Test
    fun newConnectionId_retransmittedOnLoss() =
        runBlocking {
            val client = handshakedClient()
            // Inject a NewConnectionId loss token directly — there's no
            // public emit API for NEW_CONNECTION_ID since :quic doesn't
            // do connection-ID rotation, but the retransmit machinery
            // is wired so a future emit path lands cleanly.
            val token =
                RecoveryToken.NewConnectionId(
                    sequenceNumber = 1L,
                    retirePriorTo = 0L,
                    connectionId = byteArrayOf(1, 2, 3, 4),
                    statelessResetToken = ByteArray(16) { it.toByte() },
                )
            client.lock.lock()
            try {
                client.onTokensLost(listOf(token))
            } finally {
                client.lock.unlock()
            }
            assertEquals(token, client.pendingNewConnectionId[1L])

            // Next drain emits the NEW_CONNECTION_ID frame and clears the map.
            val sizeBefore = client.application.sentPackets.size
            runCatching { drainOutbound(client, nowMillis = 1L) }
            val replay =
                client.application.sentPackets.entries
                    .sortedBy { it.key }
                    .drop(sizeBefore)
                    .firstOrNull { entry -> entry.value.tokens.any { it is RecoveryToken.NewConnectionId } }
            assertNotNull(replay, "writer must drain pendingNewConnectionId")
            assertTrue(client.pendingNewConnectionId.isEmpty(), "map must be cleared after drain")
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
