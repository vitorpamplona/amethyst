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

import com.vitorpamplona.quic.frame.NewConnectionIdFrame
import com.vitorpamplona.quic.frame.PathChallengeFrame
import com.vitorpamplona.quic.frame.PathResponseFrame
import com.vitorpamplona.quic.frame.RetireConnectionIdFrame
import com.vitorpamplona.quic.frame.decodeFrames
import com.vitorpamplona.quic.frame.encodeFrames
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for client-initiated path validation + DCID
 * rotation (RFC 9000 §9). Drives the real client through the
 * [InMemoryQuicPipe] harness so the assertions cover the parser /
 * writer / [PathValidator] wiring end-to-end:
 *
 *  1. Server sends NEW_CONNECTION_ID — pool fills.
 *  2. Application calls [QuicConnection.triggerPathMigration].
 *  3. Client emits PATH_CHALLENGE (drained from
 *     [PathValidator.pendingChallenges]).
 *  4. Server replies with PATH_RESPONSE carrying the same 8 bytes.
 *  5. Client switches `destinationConnectionId` and queues a
 *     RETIRE_CONNECTION_ID for the old sequence.
 *  6. Next outbound application packet uses the NEW DCID and
 *     contains the RETIRE_CONNECTION_ID.
 *
 * Out of scope here:
 *  - PTO-driven rotation: covered by the unit-level
 *    [PathValidatorTest.validationTimeoutAfter3PtoTransitionsToFailed]
 *    plus the driver-level threshold constant. Driving a real PTO
 *    through the in-memory pipe would require simulating timer
 *    advancement, which the pipe doesn't model today.
 */
class ClientPathMigrationTest {
    @Test
    fun retireConnectionIdFrameRoundTripsThroughCodec() {
        val encoded = encodeFrames(listOf(RetireConnectionIdFrame(sequenceNumber = 7L)))
        val decoded = decodeFrames(encoded)
        assertEquals(1, decoded.size)
        val frame = decoded.first() as RetireConnectionIdFrame
        assertEquals(7L, frame.sequenceNumber)
    }

    @Test
    fun newConnectionIdFromServerPopulatesPathValidatorPool() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val newCid = ByteArray(8) { (0xC0 or it).toByte() }
            val token = ByteArray(16) { 0x42 }
            val packet =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        NewConnectionIdFrame(
                            sequenceNumber = 1L,
                            retirePriorTo = 0L,
                            connectionId = newCid,
                            statelessResetToken = token,
                        ),
                    ),
                )!!
            feedDatagram(client, packet, nowMillis = 0L)

            assertEquals(1, client.pathValidator.unusedCount())
            assertEquals(listOf(1L), client.pathValidator.unusedSequences())
        }

    @Test
    fun newConnectionIdWithRetirePriorToGreaterThanSequenceClosesConnection() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val packet =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        NewConnectionIdFrame(
                            sequenceNumber = 1L,
                            retirePriorTo = 5L,
                            connectionId = ByteArray(8) { 0x01 },
                            statelessResetToken = ByteArray(16),
                        ),
                    ),
                )!!
            feedDatagram(client, packet, nowMillis = 0L)
            // FRAME_ENCODING_ERROR per RFC 9000 §19.15 — closes the connection.
            assertTrue(
                client.status == QuicConnection.Status.CLOSING || client.status == QuicConnection.Status.CLOSED,
                "got ${client.status}; expected CLOSING/CLOSED",
            )
        }

    @Test
    fun triggerPathMigrationWithoutSpareCidIsNoOp() =
        runBlocking {
            val (client, _) = newConnectedClient()
            val priorDcid = client.destinationConnectionId
            val result = client.triggerPathMigration(nowMillis = 0L, currentPtoMillis = 100L)
            assertEquals(PathMigrationResult.NoSpareCid, result)
            assertEquals(priorDcid, client.destinationConnectionId, "DCID must not change without a spare CID")
            assertTrue(client.pathValidator.state is PathValidationState.Idle)
        }

    @Test
    fun fullMigrationRoundTripSwitchesDcidAndQueuesRetire() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val originalDcid = client.destinationConnectionId
            val newCidBytes = ByteArray(8) { (0xA0 or it).toByte() }
            val token = ByteArray(16) { 0x77 }

            // Step 1 — server offers a fresh CID via NEW_CONNECTION_ID.
            val offer =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        NewConnectionIdFrame(
                            sequenceNumber = 1L,
                            retirePriorTo = 0L,
                            connectionId = newCidBytes,
                            statelessResetToken = token,
                        ),
                    ),
                )!!
            feedDatagram(client, offer, nowMillis = 0L)
            assertEquals(1, client.pathValidator.unusedCount())

            // Drain the client's ACK for that packet so subsequent assertions about
            // outbound contents aren't polluted by leftover ACK frames.
            drainOutbound(client, nowMillis = 0L)

            // Step 2 — application triggers migration. The validator picks
            // sequence 1 and queues a PATH_CHALLENGE.
            val triggered = client.triggerPathMigration(nowMillis = 100L, currentPtoMillis = 1_000L)
            assertEquals(PathMigrationResult.Started, triggered)
            assertTrue(client.pathValidator.state is PathValidationState.Validating)

            // Step 3 — the next outbound application packet must carry a
            // PATH_CHALLENGE.
            val challengeOut = drainOutbound(client, nowMillis = 100L)
            assertTrue(challengeOut != null, "client must emit a packet carrying PATH_CHALLENGE")
            val outboundFrames = pipe.decryptClientApplicationFrames(challengeOut)
            assertTrue(outboundFrames != null, "client outbound must decrypt with server keys")
            val challenge = outboundFrames.firstOrNull { it is PathChallengeFrame } as? PathChallengeFrame
            assertTrue(challenge != null, "outbound must contain PATH_CHALLENGE — got ${outboundFrames.map { it::class.simpleName }}")

            // Step 4 — server echoes the payload in PATH_RESPONSE.
            val response =
                pipe.buildServerApplicationDatagram(
                    listOf(PathResponseFrame(challenge.data.copyOf())),
                )!!
            feedDatagram(client, response, nowMillis = 200L)

            // Step 5 — DCID must have rotated to the new bytes; the prior
            // sequence (0) must be queued for RETIRE_CONNECTION_ID.
            assertContentEquals(newCidBytes, client.destinationConnectionId.bytes, "DCID must rotate to new bytes")
            assertEquals(1L, client.pathValidator.activeCidSequence)
            assertTrue(
                client.pathValidator.pendingRetireSequences.contains(0L),
                "old sequence number must be queued for retire",
            )

            // Step 6 — the next outbound packet should carry the
            // RETIRE_CONNECTION_ID for the old sequence.
            val retireOut = drainOutbound(client, nowMillis = 200L)
            assertTrue(retireOut != null, "client must emit a packet after PATH_RESPONSE")
            val frames = pipe.decryptClientApplicationFrames(retireOut)
            assertTrue(frames != null, "outbound after rotation must decrypt")
            val retire = frames.firstOrNull { it is RetireConnectionIdFrame } as? RetireConnectionIdFrame
            assertTrue(retire != null, "outbound must contain RETIRE_CONNECTION_ID — got ${frames.map { it::class.simpleName }}")
            assertEquals(0L, retire.sequenceNumber)

            // Sanity: connection still alive after the rotation.
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
            // Sanity: original DCID changed.
            assertTrue(originalDcid != client.destinationConnectionId)
        }

    @Test
    fun pathResponseWithMismatchingPayloadDoesNotRotateDcid() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val originalDcid = client.destinationConnectionId

            // Seed a spare CID and trigger migration.
            val offer =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        NewConnectionIdFrame(
                            sequenceNumber = 1L,
                            retirePriorTo = 0L,
                            connectionId = ByteArray(8) { 0x01 },
                            statelessResetToken = ByteArray(16) { 0x10 },
                        ),
                    ),
                )!!
            feedDatagram(client, offer, nowMillis = 0L)
            client.triggerPathMigration(nowMillis = 100L, currentPtoMillis = 1_000L)
            drainOutbound(client, nowMillis = 100L) // emit challenge, drop content

            // Server replies with WRONG payload — must not rotate.
            val bogusResponse =
                pipe.buildServerApplicationDatagram(
                    listOf(PathResponseFrame(ByteArray(8) { 0xFF.toByte() })),
                )!!
            feedDatagram(client, bogusResponse, nowMillis = 200L)

            assertEquals(originalDcid, client.destinationConnectionId, "mismatched response must NOT rotate DCID")
            assertTrue(client.pathValidator.state is PathValidationState.Validating, "still validating")
        }

    @Test
    fun unsolicitedPathResponseIsSilentlyDropped() =
        runBlocking {
            // Defence-in-depth: a peer (or attacker) sending PATH_RESPONSE
            // without a preceding PATH_CHALLENGE from us must NOT rotate the
            // DCID, must NOT crash, and the connection must stay alive.
            val (client, pipe) = newConnectedClient()
            val originalDcid = client.destinationConnectionId
            val packet = pipe.buildServerApplicationDatagram(listOf(PathResponseFrame(ByteArray(8))))!!
            feedDatagram(client, packet, nowMillis = 0L)
            assertEquals(originalDcid, client.destinationConnectionId)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
            assertTrue(client.pathValidator.state is PathValidationState.Idle)
        }

    @Test
    fun retireConnectionIdFromServerForUnissuedSequenceIsProtocolViolation() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            // We only ever issue sequence 0; any seq > 0 is a protocol
            // violation per RFC 9000 §19.16.
            val packet = pipe.buildServerApplicationDatagram(listOf(RetireConnectionIdFrame(sequenceNumber = 7L)))!!
            feedDatagram(client, packet, nowMillis = 0L)
            assertTrue(
                client.status == QuicConnection.Status.CLOSING || client.status == QuicConnection.Status.CLOSED,
                "got ${client.status}",
            )
        }
}
