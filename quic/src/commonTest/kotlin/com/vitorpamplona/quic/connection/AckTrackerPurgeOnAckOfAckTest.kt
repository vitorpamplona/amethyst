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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RFC 9000 §13.2.1: an ACK frame is itself acknowledged via the
 * carrying packet. Once the peer ACKs that packet, we know the peer
 * received our ACK and we can drop the corresponding inbound PNs from
 * our [com.vitorpamplona.quic.recovery.AckTracker].
 *
 * Pre-fix, `QuicConnectionParser` purged the tracker on every inbound
 * ACK using `frame.largestAcknowledged - frame.firstAckRange` — but
 * that value is in OUR outbound PN space, not the inbound PN space the
 * tracker holds. The mistake mostly hid because both spaces grow at
 * similar rates, but caused range-list bloat over long sessions where
 * traffic is asymmetric (e.g. listener receives ~50 audio frames/sec
 * while sending ~1 ACK/sec back).
 */
class AckTrackerPurgeOnAckOfAckTest {
    private fun newConn(): QuicConnection =
        QuicConnection(
            serverName = "example.test",
            config = QuicConnectionConfig(),
            tlsCertificateValidator =
                com.vitorpamplona.quic.tls
                    .PermissiveCertificateValidator(),
        )

    @Test
    fun ackOfAck_purgesTrackerBelowLargestAcked() =
        runBlocking {
            val conn = newConn()
            // Simulate having received 5 inbound packets from the peer.
            for (pn in 0L..4L) {
                conn.application.ackTracker.receivedPacket(pn, ackEliciting = true, receivedAtMillis = 1L)
            }
            assertEquals(4L, conn.application.ackTracker.largestReceived())
            assertFalse(conn.application.ackTracker.isEmpty())

            // Peer ACKs the packet that carried our outbound ACK
            // covering up to PN 4.
            conn.lock.lock()
            try {
                conn.onTokensAcked(
                    listOf(
                        RecoveryToken.Ack(
                            level = EncryptionLevel.APPLICATION,
                            largestAcked = 4L,
                        ),
                    ),
                )
            } finally {
                conn.lock.unlock()
            }
            // Tracker is now empty: peer has confirmed receipt of our
            // ACK that covered everything up to PN 4. Re-advertising
            // those PNs in subsequent outbound ACKs is wasted bytes.
            assertTrue(conn.application.ackTracker.isEmpty(), "tracker fully purged below largestAcked + 1")
        }

    @Test
    fun ackOfAck_routesToCorrectLevel() =
        runBlocking {
            val conn = newConn()
            // Independent state on Initial vs Application trackers.
            for (pn in 0L..2L) {
                conn.initial.ackTracker.receivedPacket(pn, ackEliciting = true, receivedAtMillis = 1L)
            }
            for (pn in 0L..4L) {
                conn.application.ackTracker.receivedPacket(pn, ackEliciting = true, receivedAtMillis = 1L)
            }

            // Peer ACKs our Initial-level outbound ACK.
            conn.lock.lock()
            try {
                conn.onTokensAcked(
                    listOf(
                        RecoveryToken.Ack(level = EncryptionLevel.INITIAL, largestAcked = 2L),
                    ),
                )
            } finally {
                conn.lock.unlock()
            }
            // Initial tracker drained; Application tracker untouched.
            assertTrue(conn.initial.ackTracker.isEmpty())
            assertEquals(4L, conn.application.ackTracker.largestReceived())
        }

    @Test
    fun ackOfAck_partialPurge_keepsHigherPns() =
        runBlocking {
            val conn = newConn()
            for (pn in 0L..9L) {
                conn.application.ackTracker.receivedPacket(pn, ackEliciting = true, receivedAtMillis = 1L)
            }
            // Peer ACKs our outbound ACK that covered up to PN 4 only;
            // the tracker's higher-PN ranges (5..9) must survive.
            conn.lock.lock()
            try {
                conn.onTokensAcked(
                    listOf(
                        RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = 4L),
                    ),
                )
            } finally {
                conn.lock.unlock()
            }
            assertFalse(conn.application.ackTracker.isEmpty())
            assertEquals(9L, conn.application.ackTracker.largestReceived())
        }

    @Test
    fun ackOfAck_outOfOrder_isSafe() =
        runBlocking {
            // Two outbound ACKs go out: ACK#A covers up-to PN 4, ACK#B
            // covers up-to PN 9. Peer ACKs ACK#B first, then ACK#A.
            // After ACK#B drains, tracker is empty. ACK#A's purge is
            // a no-op — must not throw, must not re-resurrect anything.
            val conn = newConn()
            for (pn in 0L..9L) {
                conn.application.ackTracker.receivedPacket(pn, ackEliciting = true, receivedAtMillis = 1L)
            }
            conn.lock.lock()
            try {
                conn.onTokensAcked(
                    listOf(
                        RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = 9L),
                    ),
                )
                assertTrue(conn.application.ackTracker.isEmpty())
                conn.onTokensAcked(
                    listOf(
                        RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = 4L),
                    ),
                )
                assertTrue(conn.application.ackTracker.isEmpty())
            } finally {
                conn.lock.unlock()
            }
        }
}
