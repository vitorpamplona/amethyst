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
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
            // sequence 1, queues a PATH_CHALLENGE, AND swaps the writer's
            // outbound DCID immediately so the challenge goes out on the
            // new path (Bug-1 fix — abrupt migration model per RFC 9000
            // §9.3).
            val triggered = client.triggerPathMigration(nowMillis = 100L, currentPtoMillis = 1_000L)
            assertEquals(PathMigrationResult.Started, triggered)
            assertTrue(client.pathValidator.state is PathValidationState.Validating)
            assertContentEquals(
                newCidBytes,
                client.destinationConnectionId.bytes,
                "DCID must rotate at challenge time so PATH_CHALLENGE goes out on the new path",
            )

            // Step 3 — the next outbound application packet must carry
            // BOTH the PATH_CHALLENGE (stamped with the NEW DCID) AND
            // a RETIRE_CONNECTION_ID for the old sequence (Bug-B fix:
            // abrupt migration retires the prior CID at trigger time,
            // not at response time, so the writer drains both in the
            // same packet).
            val challengeOut = drainOutbound(client, nowMillis = 100L)
            assertTrue(challengeOut != null, "client must emit a packet carrying PATH_CHALLENGE")
            val outboundFrames = pipe.decryptClientApplicationFrames(challengeOut)
            assertTrue(outboundFrames != null, "client outbound must decrypt with server keys")
            val challenge = outboundFrames.firstOrNull { it is PathChallengeFrame } as? PathChallengeFrame
            assertTrue(challenge != null, "outbound must contain PATH_CHALLENGE — got ${outboundFrames.map { it::class.simpleName }}")
            val retire = outboundFrames.firstOrNull { it is RetireConnectionIdFrame } as? RetireConnectionIdFrame
            assertTrue(
                retire != null,
                "outbound must contain RETIRE_CONNECTION_ID for the prior CID alongside PATH_CHALLENGE — got ${outboundFrames.map { it::class.simpleName }}",
            )
            assertEquals(0L, retire.sequenceNumber)

            // Step 4 — server echoes the payload in PATH_RESPONSE.
            val response =
                pipe.buildServerApplicationDatagram(
                    listOf(PathResponseFrame(challenge.data.copyOf())),
                )!!
            feedDatagram(client, response, nowMillis = 200L)

            // Step 5 — validation succeeded; state transitions to
            // Idle (after acknowledgeTerminal), activeCidSequence is
            // still the new sequence (already bumped at trigger),
            // DCID still the new bytes.
            assertContentEquals(newCidBytes, client.destinationConnectionId.bytes)
            assertEquals(1L, client.pathValidator.activeCidSequence)
            assertEquals(1L, client.pathValidator.successfulValidations)

            // Sanity: connection still alive after the rotation.
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
            // Sanity: original DCID changed.
            assertTrue(originalDcid != client.destinationConnectionId)
        }

    @Test
    fun pathResponseWithMismatchingPayloadKeepsValidatingAndDcid() =
        runBlocking {
            val (client, pipe) = newConnectedClient()

            // Seed a spare CID and trigger migration. Under abrupt
            // migration the DCID rotates at trigger time, NOT at
            // PATH_RESPONSE — so the post-trigger state is "DCID is
            // already the new bytes; we're awaiting validation."
            val newCid = ByteArray(8) { 0x01 }
            val offer =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        NewConnectionIdFrame(
                            sequenceNumber = 1L,
                            retirePriorTo = 0L,
                            connectionId = newCid,
                            statelessResetToken = ByteArray(16) { 0x10 },
                        ),
                    ),
                )!!
            feedDatagram(client, offer, nowMillis = 0L)
            client.triggerPathMigration(nowMillis = 100L, currentPtoMillis = 1_000L)
            assertContentEquals(newCid, client.destinationConnectionId.bytes, "DCID rotates at challenge time")
            drainOutbound(client, nowMillis = 100L) // emit challenge, drop content

            // Server replies with WRONG payload — validation must NOT
            // succeed and the validator state must stay in Validating
            // until the 3 * PTO timeout (or a correct response).
            val bogusResponse =
                pipe.buildServerApplicationDatagram(
                    listOf(PathResponseFrame(ByteArray(8) { 0xFF.toByte() })),
                )!!
            feedDatagram(client, bogusResponse, nowMillis = 200L)

            assertContentEquals(newCid, client.destinationConnectionId.bytes, "mismatched response must not undo the rotation")
            assertTrue(client.pathValidator.state is PathValidationState.Validating, "still validating")
            assertEquals(
                1L,
                client.pathValidator.activeCidSequence,
                "active sequence tracks what the writer is putting on the wire — bumped at challenge time per Bug-B fix",
            )
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

    @Test
    fun retireConnectionIdForSequenceZeroClosesConnection() =
        runBlocking {
            // Bug-3 regression: peer asking us to retire our initial
            // SCID (seq 0) leaves us with no replacement to give them.
            // We close rather than silently honoring — see
            // [QuicConnection.applyPeerRetireConnectionIdLocked].
            val (client, pipe) = newConnectedClient()
            val packet = pipe.buildServerApplicationDatagram(listOf(RetireConnectionIdFrame(sequenceNumber = 0L)))!!
            feedDatagram(client, packet, nowMillis = 0L)
            assertTrue(
                client.status == QuicConnection.Status.CLOSING || client.status == QuicConnection.Status.CLOSED,
                "got ${client.status}; seq=0 retire must close because we have no replacement SCID",
            )
        }

    @Test
    fun pathResponseSuccessResetsConsecutivePtoCount() =
        runBlocking {
            // Bug-7 regression: a successful PATH_RESPONSE proves the
            // peer can reach us on the new path. The consecutive-PTO
            // counter must reset so the send loop's exponential
            // backoff returns to baseline pacing instead of carrying
            // a stale "many PTOs expired" multiplier.
            val (client, pipe) = newConnectedClient()
            val newCid = ByteArray(8) { 0x55 }
            val token = ByteArray(16) { 0x66 }

            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(
                    listOf(NewConnectionIdFrame(1L, 0L, newCid, token)),
                )!!,
                nowMillis = 0L,
            )
            drainOutbound(client, nowMillis = 0L)

            // Pretend we burned through 4 PTOs without progress.
            client.consecutivePtoCount = 4
            client.triggerPathMigration(nowMillis = 100L, currentPtoMillis = 1_000L)
            val challengeOut = drainOutbound(client, nowMillis = 100L)!!
            val challenge =
                pipe.decryptClientApplicationFrames(challengeOut)!!.first { it is PathChallengeFrame }
                    as PathChallengeFrame

            // PATH_RESPONSE arrives with matching payload — Bug-7 fix
            // resets the counter inside applyPeerPathResponseLocked.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(listOf(PathResponseFrame(challenge.data.copyOf())))!!,
                nowMillis = 200L,
            )
            assertEquals(0, client.consecutivePtoCount, "successful path validation must reset consecutivePtoCount")
        }

    @Test
    fun newConnectionIdWithRetirePriorToPastActiveForcesRotationOnSamePath() =
        runBlocking {
            // Bug-C regression: RFC 9000 §5.1.2 — a peer that
            // advances retire_prior_to past our active CID forces
            // us to rotate on the SAME path (no PATH_CHALLENGE).
            // Pre-fix the parser silently accepted the offer and
            // we'd keep stamping the now-retired seq=0 on outbound
            // packets.
            val (client, pipe) = newConnectedClient()
            val originalDcid = client.destinationConnectionId
            val replacementCid = ByteArray(8) { (0xB0 or it).toByte() }
            val token = ByteArray(16) { 0x33 }

            // First offer: seq=1, no retirement. Pool fills.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(
                    listOf(NewConnectionIdFrame(1L, 0L, replacementCid, token)),
                )!!,
                nowMillis = 0L,
            )
            assertEquals(originalDcid, client.destinationConnectionId, "first offer doesn't force rotation")

            // Second offer: seq=2 with retire_prior_to=1. Watermark
            // advances past our active seq=0. MUST rotate on the
            // same path to seq=1 (the lowest spare).
            val secondToken = ByteArray(16) { 0x44 }
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(
                    listOf(NewConnectionIdFrame(2L, 1L, ByteArray(8) { 0xCC.toByte() }, secondToken)),
                )!!,
                nowMillis = 0L,
            )

            assertContentEquals(
                replacementCid,
                client.destinationConnectionId.bytes,
                "DCID must rotate to the lowest spare (seq=1) when watermark advances past seq=0",
            )
            assertEquals(1L, client.pathValidator.activeCidSequence)
            assertTrue(
                client.pathValidator.pendingRetireSequences.contains(0L),
                "old active seq=0 must be queued for RETIRE_CONNECTION_ID",
            )
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "server-forced rotation must NOT close the connection",
            )
        }

    @Test
    fun backToBackSuccessfulMigrationsRetireExactlyOnePerRotationAndStampNewDcid() =
        runBlocking {
            // Rebind-port regression: prior to the 2026-05-08 fix, two
            // back-to-back rotations could leak a packet stamped with
            // a just-retired DCID — quic-go / picoquic / msquic /
            // mvfst correctly close that as PROTOCOL_VIOLATION. This
            // test drives the validator through TWO completed
            // rotations and asserts:
            //  1. exactly ONE RETIRE_CONNECTION_ID frame is queued
            //     per rotation (the prior active seq);
            //  2. EVERY post-rotation outbound packet is stamped
            //     with the new DCID (no stray packet leaks the
            //     retired seq).
            val (client, pipe) = newConnectedClient()

            val cidSeq1 = ByteArray(8) { (0xA0 or it).toByte() }
            val cidSeq2 = ByteArray(8) { (0xB0 or it).toByte() }
            val token1 = ByteArray(16) { 0x71 }
            val token2 = ByteArray(16) { 0x72 }

            // Server offers two spare CIDs up front so we can do two
            // rotations without waiting for replenishment.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(
                    listOf(
                        NewConnectionIdFrame(1L, 0L, cidSeq1, token1),
                        NewConnectionIdFrame(2L, 0L, cidSeq2, token2),
                    ),
                )!!,
                nowMillis = 0L,
            )
            assertEquals(2, client.pathValidator.unusedCount())
            // Drain the ACK so subsequent assertions see only post-rotation traffic.
            drainOutbound(client, nowMillis = 0L)

            // ── Rotation #1 ────────────────────────────────────────
            assertEquals(PathMigrationResult.Started, client.triggerPathMigration(100L, 1_000L))
            assertContentEquals(cidSeq1, client.destinationConnectionId.bytes)
            val out1 = drainOutbound(client, nowMillis = 100L)!!
            val frames1 = pipe.decryptClientApplicationFrames(out1)!!
            val challenge1 = frames1.first { it is PathChallengeFrame } as PathChallengeFrame
            // Exactly ONE RETIRE_CONNECTION_ID frame in this packet,
            // for the prior active seq (0). No retire of seq 1.
            val retires1 = frames1.filterIsInstance<RetireConnectionIdFrame>()
            assertEquals(1, retires1.size, "rotation #1 must queue exactly one RETIRE — got ${retires1.map { it.sequenceNumber }}")
            assertEquals(0L, retires1.single().sequenceNumber)
            // PATH_RESPONSE arrives — validation succeeds.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(listOf(PathResponseFrame(challenge1.data.copyOf())))!!,
                nowMillis = 200L,
            )
            assertContentEquals(cidSeq1, client.destinationConnectionId.bytes)
            assertEquals(1L, client.pathValidator.activeCidSequence)
            assertEquals(1L, client.pathValidator.successfulValidations)
            // Drain the ACK so the next outbound is rotation-specific.
            drainOutbound(client, nowMillis = 200L)

            // ── Rotation #2 ────────────────────────────────────────
            assertEquals(PathMigrationResult.Started, client.triggerPathMigration(300L, 1_000L))
            assertContentEquals(cidSeq2, client.destinationConnectionId.bytes)
            val out2 = drainOutbound(client, nowMillis = 300L)!!
            val frames2 = pipe.decryptClientApplicationFrames(out2)!!
            val challenge2 = frames2.first { it is PathChallengeFrame } as PathChallengeFrame
            assertNotEquals(
                challenge1.data.toList(),
                challenge2.data.toList(),
                "second rotation must emit a fresh random PATH_CHALLENGE payload",
            )
            // Exactly ONE RETIRE_CONNECTION_ID frame for seq=1 (the
            // prior active). Critically NOT also seq=0 — that was
            // already retired in rotation #1 and the writer must
            // not re-emit a stale entry.
            val retires2 = frames2.filterIsInstance<RetireConnectionIdFrame>()
            assertEquals(1, retires2.size, "rotation #2 must queue exactly one RETIRE — got ${retires2.map { it.sequenceNumber }}")
            assertEquals(1L, retires2.single().sequenceNumber)

            // PATH_RESPONSE arrives — second validation succeeds.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(listOf(PathResponseFrame(challenge2.data.copyOf())))!!,
                nowMillis = 400L,
            )
            assertContentEquals(cidSeq2, client.destinationConnectionId.bytes)
            assertEquals(2L, client.pathValidator.activeCidSequence)
            assertEquals(2L, client.pathValidator.successfulValidations)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
        }

    @Test
    fun validationTimeoutWithSpareRotatesDcidAndRetiresFailedSeq() =
        runBlocking {
            // Rebind-port regression: when validation times out and
            // a spare CID exists, the writer's destinationConnectionId
            // MUST rotate to that spare BEFORE the next outbound is
            // built. Otherwise we'd queue RETIRE for the failed seq
            // and stamp the same retired seq on the same packet —
            // exactly the PROTOCOL_VIOLATION the strict servers
            // close us on.
            val (client, pipe) = newConnectedClient()
            val cidSeq1 = ByteArray(8) { (0xA0 or it).toByte() }
            val cidSeq2 = ByteArray(8) { (0xB0 or it).toByte() }

            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(
                    listOf(
                        NewConnectionIdFrame(1L, 0L, cidSeq1, ByteArray(16) { 0x11 }),
                        NewConnectionIdFrame(2L, 0L, cidSeq2, ByteArray(16) { 0x22 }),
                    ),
                )!!,
                nowMillis = 0L,
            )
            drainOutbound(client, nowMillis = 0L)

            // Trigger validation. tryStart consumes seq=1; pool keeps seq=2.
            val pto = 1_000L
            assertEquals(PathMigrationResult.Started, client.triggerPathMigration(100L, pto))
            assertContentEquals(cidSeq1, client.destinationConnectionId.bytes)
            // Drain the PATH_CHALLENGE so any subsequent build is
            // post-timeout traffic only.
            drainOutbound(client, nowMillis = 100L)

            // Force the 3*PTO timeout. checkPathValidationTimeoutLocked
            // is the same path the driver / writer take, so this
            // exercises the production wrapper.
            val timedOut =
                client.streamsLock.withLock {
                    client.checkPathValidationTimeoutLocked(nowMillis = 100L + pto * 4L)
                }
            assertTrue(timedOut)
            // Validator rotated active to the spare; connection swapped DCID.
            assertEquals(2L, client.pathValidator.activeCidSequence)
            assertContentEquals(
                cidSeq2,
                client.destinationConnectionId.bytes,
                "DCID must rotate to the spare before any outbound runs",
            )
            assertTrue(
                client.pathValidator.pendingRetireSequences.contains(1L),
                "failed seq=1 must be queued for RETIRE alongside the rotation",
            )

            // Next outbound must stamp the SPARE seq (cidSeq2), NOT
            // the failed seq (cidSeq1) — the bug we're guarding
            // against. The packet carries RETIRE(1) on a DCID of
            // seq=2's bytes.
            val out = drainOutbound(client, nowMillis = 100L + pto * 4L)!!
            val frames = pipe.decryptClientApplicationFrames(out)!!
            val retire = frames.firstOrNull { it is RetireConnectionIdFrame } as? RetireConnectionIdFrame
            assertTrue(retire != null, "outbound must carry RETIRE for the failed seq — got ${frames.map { it::class.simpleName }}")
            assertEquals(1L, retire.sequenceNumber)
            // The pipe parser uses serverScid.length to peel the
            // DCID off the short header. We verify the rotation
            // structurally via destinationConnectionId rather than
            // reparsing the wire bytes — but the pipe successfully
            // decrypted the packet, which already requires the DCID
            // to match a recognized issued CID on the server side.
        }

    @Test
    fun validationTimeoutWithoutSpareKeepsActiveCidAndDoesNotRetire() =
        runBlocking {
            // Rebind-port regression (no-spare branch): when
            // validation times out and NO spare exists, the
            // validator must NOT queue retire for the failed seq —
            // doing so would have us stamping a CID we just told
            // the peer to retire. Instead activeCidSequence stays
            // on the failed seq; the writer keeps using it; the
            // next NEW_CONNECTION_ID arrival lets a fresh trigger
            // rotate cleanly.
            val (client, pipe) = newConnectedClient()
            val cidSeq1 = ByteArray(8) { (0xC0 or it).toByte() }
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(
                    listOf(NewConnectionIdFrame(1L, 0L, cidSeq1, ByteArray(16) { 0x33 })),
                )!!,
                nowMillis = 0L,
            )
            drainOutbound(client, nowMillis = 0L)

            val pto = 1_000L
            assertEquals(PathMigrationResult.Started, client.triggerPathMigration(100L, pto))
            assertContentEquals(cidSeq1, client.destinationConnectionId.bytes)
            // Pool is now empty — only seq=1 was offered, and it's
            // the active.
            assertEquals(0, client.pathValidator.unusedCount())
            drainOutbound(client, nowMillis = 100L)

            val timedOut =
                client.streamsLock.withLock {
                    client.checkPathValidationTimeoutLocked(nowMillis = 100L + pto * 4L)
                }
            assertTrue(timedOut)
            // Active stays on the failed seq; DCID unchanged.
            assertEquals(1L, client.pathValidator.activeCidSequence)
            assertContentEquals(
                cidSeq1,
                client.destinationConnectionId.bytes,
                "no spare available — DCID must stay on the failed seq, not be cleared",
            )
            // Crucially: no retire queued. The writer doesn't have
            // a way to stamp anything else, so retiring would put
            // us into the bug.
            assertTrue(
                !client.pathValidator.pendingRetireSequences.contains(1L),
                "failed seq must NOT be queued for retire when no spare exists — pending=${client.pathValidator.pendingRetireSequences}",
            )
            assertEquals(QuicConnection.Status.CONNECTED, client.status, "stuck-on-failed-cid must not close the connection")
        }

    @Test
    fun triggerPathMigrationBeforeHandshakeReturnsNotConnected() =
        runBlocking {
            // Bug-4 regression: RFC 9000 §9.1 forbids initiating
            // migration before the handshake is confirmed. The
            // public API must refuse rather than silently misbehave.
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            // Connection is in HANDSHAKING state; no transport
            // parameters have been parsed yet.
            assertEquals(QuicConnection.Status.HANDSHAKING, client.status)
            val result = client.triggerPathMigration(nowMillis = 0L, currentPtoMillis = 100L)
            assertEquals(PathMigrationResult.NotConnected, result)
        }
}
