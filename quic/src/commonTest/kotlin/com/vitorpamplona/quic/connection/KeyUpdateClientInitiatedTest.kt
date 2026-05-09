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

import com.vitorpamplona.quic.frame.HandshakeDoneFrame
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the client-initiated 1-RTT key update gate (RFC 9001 §6.1 +
 * §4.1.2): the client MUST NOT roll `KEY_PHASE` before
 * `HANDSHAKE_DONE` has been received from the server. Strict servers
 * — quinn, quic-go, picoquic — close the connection with
 * PROTOCOL_VIOLATION ("illegal packet: key update error") otherwise.
 *
 * Pre-2026-05-08 the gate inside [QuicConnection.initiateKeyUpdate]
 * was just "1-RTT keys installed", which fires when TLS Finished is
 * derived — well before HANDSHAKE_DONE arrives. Confirmed against
 * quinn's reference server: at ~33% of runs the interop runner's
 * `keyupdate` testcase initiated the update inside the gap and
 * tripped the violation. See `quic/plans/2026-05-08-keyupdate-vs-quinn.md`.
 *
 * The fix introduces a separate [QuicConnection.handshakeConfirmed]
 * field flipped only by the parser on `HANDSHAKE_DONE` receipt;
 * [QuicConnection.initiateKeyUpdate] now requires it.
 */
class KeyUpdateClientInitiatedTest {
    @Test
    fun initiateKeyUpdateBeforeHandshakeDoneIsRejectedAndDoesNotRotateKeys() =
        runBlocking {
            val (client, _) = newConnectedClient(deliverHandshakeDone = false)

            // newConnectedClient drives the handshake to TLS-Finished
            // (so 1-RTT keys are installed and `handshakeComplete` is
            // true), but DOES NOT deliver HANDSHAKE_DONE — exactly
            // the window where strict servers reject a client-side
            // key update.
            assertTrue(client.handshakeComplete, "TLS-side handshake completed")
            assertFalse(client.handshakeConfirmed, "HANDSHAKE_DONE not yet received")

            val originalReceiveProtection = client.application.receiveProtection
            val originalSendProtection = client.application.sendProtection

            assertFalse(
                client.initiateKeyUpdate(),
                "initiateKeyUpdate must reject before HANDSHAKE_DONE — RFC 9001 §6.1 + §4.1.2",
            )

            // Phase bits stay at 0; protections stay at the post-handshake
            // values; no demotion to previousReceiveProtection.
            assertEquals(false, client.currentSendKeyPhase)
            assertEquals(false, client.currentReceiveKeyPhase)
            assertEquals(originalReceiveProtection, client.application.receiveProtection)
            assertEquals(originalSendProtection, client.application.sendProtection)
            assertEquals(null, client.previousReceiveProtection)
        }

    @Test
    fun initiateKeyUpdateAfterHandshakeDoneRotatesBothDirections() =
        runBlocking {
            val (client, pipe) = newConnectedClient(deliverHandshakeDone = false)

            // Deliver HANDSHAKE_DONE — the parser flips
            // [QuicConnection.handshakeConfirmed] and unblocks the
            // gate inside `initiateKeyUpdate`.
            val handshakeDonePacket = pipe.buildServerApplicationDatagram(listOf(HandshakeDoneFrame()))!!
            feedDatagram(client, handshakeDonePacket, nowMillis = 0L)
            assertTrue(client.handshakeConfirmed, "HANDSHAKE_DONE must flip handshakeConfirmed")

            val originalReceiveProtection = client.application.receiveProtection
            val originalSendProtection = client.application.sendProtection
            assertNotNull(originalReceiveProtection)
            assertNotNull(originalSendProtection)

            assertTrue(client.initiateKeyUpdate(), "must accept after HANDSHAKE_DONE")

            // Both directions rotated; phase bits flipped; old receive
            // keys retained for the §6.1 reorder window.
            assertEquals(true, client.currentSendKeyPhase)
            assertEquals(true, client.currentReceiveKeyPhase)
            assertEquals(
                originalReceiveProtection,
                client.previousReceiveProtection,
                "pre-rotation receive keys must move into previousReceiveProtection",
            )
            assertTrue(
                originalReceiveProtection != client.application.receiveProtection,
                "fresh receive protection installed",
            )
            assertTrue(
                originalSendProtection != client.application.sendProtection,
                "fresh send protection installed",
            )
        }

    @Test
    fun awaitHandshakeConfirmedSuspendsUntilHandshakeDone() =
        runBlocking {
            val (client, pipe) = newConnectedClient(deliverHandshakeDone = false)
            assertFalse(client.handshakeConfirmed, "not confirmed before HANDSHAKE_DONE")
            // Deliver HANDSHAKE_DONE and then await — the awaiter must
            // observe the post-receipt state without suspending forever.
            val packet = pipe.buildServerApplicationDatagram(listOf(HandshakeDoneFrame()))!!
            feedDatagram(client, packet, nowMillis = 0L)
            client.awaitHandshakeConfirmed()
            assertTrue(client.handshakeConfirmed)
        }

    @Test
    fun triggerPathMigrationBeforeHandshakeDoneReturnsNotConnected() =
        runBlocking {
            // Symmetric gate: RFC 9000 §9.1 also requires handshake
            // confirmation before initiating connection migration.
            // Pre-fix this gated on `handshakeComplete && status ==
            // CONNECTED` — both flip on TLS Finished, so migration
            // could fire before HANDSHAKE_DONE.
            val (client, pipe) = newConnectedClient(deliverHandshakeDone = false)
            // Pool a CID so NoSpareCid wouldn't shadow the gate.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(
                    listOf(
                        com.vitorpamplona.quic.frame.NewConnectionIdFrame(
                            sequenceNumber = 1L,
                            retirePriorTo = 0L,
                            connectionId = ByteArray(8) { 0x42 },
                            statelessResetToken = ByteArray(16) { 0x77 },
                        ),
                    ),
                )!!,
                nowMillis = 0L,
            )
            assertFalse(client.handshakeConfirmed)
            val result = client.triggerPathMigration(nowMillis = 0L, currentPtoMillis = 100L)
            assertEquals(PathMigrationResult.NotConnected, result)
        }
}
