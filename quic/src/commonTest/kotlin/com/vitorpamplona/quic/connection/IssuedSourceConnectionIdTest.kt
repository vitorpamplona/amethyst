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
import com.vitorpamplona.quic.frame.RetireConnectionIdFrame
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the client-side issuance of source CIDs (RFC 9000 §5.1.1 +
 * §19.15). Once the handshake is confirmed, the client MUST be
 * willing to advertise spare source CIDs to the peer so it has
 * DCIDs available to send on alternative paths after a NAT rebind
 * or migration. Strict server stacks (quic-go, picoquic, msquic,
 * mvfst) refuse to validate a new path until they have a spare
 * DCID — the giveaway is the server log line
 * `"skipping validation of new path … since no connection ID is
 * available"`. Pre-fix the rebind-port / rebind-addr /
 * connectionmigration interop testcases against those servers all
 * timed out at the runner's 60 s budget; quinn was the only server
 * that passed because it migrates on src-port-change alone without
 * waiting for a fresh DCID.
 *
 * The implementation lives in
 * [QuicConnection.issueOwnConnectionIdsLocked] (called from the
 * writer once `handshakeConfirmed` flips) +
 * [QuicConnection.applyPeerRetireConnectionIdLocked] (which now
 * tops the pool back up via
 * [QuicConnection.issueOwnReplacementSourceCidLocked] instead of
 * closing).
 */
class IssuedSourceConnectionIdTest {
    @Test
    fun postHandshakeDrainEmitsNewConnectionIdFramesPerActiveConnectionIdLimit() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            // newConnectedClient now delivers HANDSHAKE_DONE so
            // [handshakeConfirmed] is true; the next outbound drain
            // gates spare-CID issuance off that flag.
            assertTrue(client.handshakeConfirmed, "fixture must deliver HANDSHAKE_DONE")
            assertEquals(
                false,
                client.ownSourceCidsIssued,
                "issuance happens at writer-drain time, not at handshake-confirm time",
            )

            // Run the writer once. Should pull `peer.activeConnectionIdLimit
            // - 1` fresh entries off the pool and emit them as
            // NEW_CONNECTION_ID frames.
            val out = drainOutbound(client, nowMillis = 0L)
            assertTrue(out != null && out.isNotEmpty(), "writer must drain")
            assertTrue(client.ownSourceCidsIssued, "writer must mark issuance done after first post-confirm drain")

            val frames = pipe.decryptClientApplicationFrames(out)!!
            val ncids = frames.filterIsInstance<NewConnectionIdFrame>()

            // The fixture's TLS server advertises
            // `activeConnectionIdLimit = 4` by default (transport
            // params symmetry — see [ConnectedClientFixture]). We
            // already implicitly issued seq=0, so we expect 3 fresh
            // sequences (1, 2, 3).
            val expectedCount = (client.peerTransportParameters?.activeConnectionIdLimit ?: 2L) - 1L
            assertEquals(expectedCount.toInt(), ncids.size, "wrong NEW_CONNECTION_ID count — got ${ncids.size}")

            // Sequence numbers are contiguous starting at 1.
            val seqs = ncids.map { it.sequenceNumber }.sorted()
            assertEquals((1L..expectedCount).toList(), seqs)
            // Each frame is a fresh-issuance — retirePriorTo=0.
            assertTrue(ncids.all { it.retirePriorTo == 0L }, "fresh issuances don't request retirement")
            // Each carries an 8-byte CID and 16-byte stateless reset token.
            assertTrue(ncids.all { it.connectionId.size == client.sourceConnectionId.length }, "CID length mismatch")
            assertTrue(ncids.all { it.statelessResetToken.size == 16 }, "stateless reset token must be 16 bytes")

            // The pool tracks every issued sequence (including seq=0).
            assertEquals(
                (0L..expectedCount).toSet(),
                client.issuedSourceConnectionIds.keys,
                "issuedSourceConnectionIds must contain every emitted sequence (plus seq=0)",
            )

            // A second drain doesn't re-emit (the queue was drained, the
            // ownSourceCidsIssued latch is set).
            val followup = drainOutbound(client, nowMillis = 1L)
            if (followup != null && followup.isNotEmpty()) {
                val followupFrames = pipe.decryptClientApplicationFrames(followup) ?: emptyList()
                assertTrue(
                    followupFrames.none { it is NewConnectionIdFrame },
                    "second drain must NOT re-issue — got ${followupFrames.map { it::class.simpleName }}",
                )
            }
        }

    @Test
    fun preHandshakeDrainDoesNotIssueOwnConnectionIds() =
        runBlocking {
            // Pre-confirmation the writer must skip issuance — the
            // peer's active_connection_id_limit hasn't been parsed
            // yet, and emitting NEW_CONNECTION_ID at handshake level
            // is meaningless (frame is application-only per §19.15).
            val (client, _) = newConnectedClient(deliverHandshakeDone = false)
            assertEquals(false, client.handshakeConfirmed)
            drainOutbound(client, nowMillis = 0L)
            assertEquals(
                false,
                client.ownSourceCidsIssued,
                "writer must not issue spare CIDs before HANDSHAKE_DONE arrives",
            )
            assertEquals(setOf(0L), client.issuedSourceConnectionIds.keys)
        }

    @Test
    fun peerRetireOfIssuedSequenceFreesEntryAndIssuesReplacement() =
        runBlocking {
            // RFC 9000 §5.1.2 endorsement: top up the pool when the
            // peer retires one of our spares. Without this, after
            // each NAT rebind the strict server consumes one spare
            // and we'd run out — leaving the next rebind unable to
            // validate.
            val (client, pipe) = newConnectedClient()
            drainOutbound(client, nowMillis = 0L) // emit initial NEW_CONNECTION_IDs
            val initialPool = client.issuedSourceConnectionIds.keys.toSet()
            val initialNext = client.nextOwnSourceCidSeq

            // Peer retires sequence 1 (one of the post-handshake spares).
            val packet = pipe.buildServerApplicationDatagram(listOf(RetireConnectionIdFrame(sequenceNumber = 1L)))!!
            feedDatagram(client, packet, nowMillis = 1L)

            assertTrue(
                !client.issuedSourceConnectionIds.containsKey(1L),
                "retired seq=1 must be removed from issuedSourceConnectionIds — got ${client.issuedSourceConnectionIds.keys}",
            )
            assertEquals(
                initialNext + 1L,
                client.nextOwnSourceCidSeq,
                "must allocate a fresh sequence for the replacement",
            )
            assertTrue(
                client.issuedSourceConnectionIds.containsKey(initialNext),
                "replacement seq=$initialNext must be in the pool",
            )
            // Connection still CONNECTED.
            assertEquals(QuicConnection.Status.CONNECTED, client.status)

            // The replacement is queued for the next outbound.
            val out2 = drainOutbound(client, nowMillis = 2L)
            val frames = pipe.decryptClientApplicationFrames(out2 ?: ByteArray(0)) ?: emptyList()
            val replacement = frames.filterIsInstance<NewConnectionIdFrame>().firstOrNull()
            assertTrue(replacement != null, "replacement must emit on next drain — got ${frames.map { it::class.simpleName }}")
            assertEquals(initialNext, replacement.sequenceNumber)
            // Sanity: original pool minus seq=1 plus seq=initialNext is the new pool.
            assertEquals(
                initialPool - 1L + initialNext,
                client.issuedSourceConnectionIds.keys,
            )
        }

    @Test
    fun peerRetireOfUnissuedSequenceIsProtocolViolation() =
        runBlocking {
            // §19.16 — the peer can't retire something we never
            // advertised. Pre-fix any seq > 0 closed the connection
            // because we never issued anything; now seq must be ≥
            // [nextOwnSourceCidSeq] to violate (anything below either
            // exists in the pool or has been retired already, both
            // of which are benign).
            val (client, pipe) = newConnectedClient()
            drainOutbound(client, nowMillis = 0L) // issuance runs
            val far = client.nextOwnSourceCidSeq + 100L
            val packet = pipe.buildServerApplicationDatagram(listOf(RetireConnectionIdFrame(sequenceNumber = far)))!!
            feedDatagram(client, packet, nowMillis = 1L)
            assertTrue(
                client.status == QuicConnection.Status.CLOSING || client.status == QuicConnection.Status.CLOSED,
                "got ${client.status}",
            )
        }

    @Test
    fun peerRetireRetransmitForAlreadyRetiredSequenceIsBenign() =
        runBlocking {
            // §19.16 nuance: a peer that doesn't see our ACK may
            // retransmit RETIRE_CONNECTION_ID for an already-removed
            // entry. Closing on that would be hostile — the second
            // arrival is just retransmit noise.
            val (client, pipe) = newConnectedClient()
            drainOutbound(client, nowMillis = 0L)
            // First retire of seq=1.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(listOf(RetireConnectionIdFrame(sequenceNumber = 1L)))!!,
                nowMillis = 1L,
            )
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
            drainOutbound(client, nowMillis = 2L) // emit replacement
            // Duplicate retire of seq=1 (already removed).
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(listOf(RetireConnectionIdFrame(sequenceNumber = 1L)))!!,
                nowMillis = 3L,
            )
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "duplicate retire must not close — peer is just retransmitting",
            )
        }
}
