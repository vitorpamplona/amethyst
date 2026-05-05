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
package com.vitorpamplona.quic.connection.recovery

import com.vitorpamplona.quic.connection.EncryptionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Schema-level invariants for [SentPacket]: data-class equality
 * mirrors [RecoveryTokenTest], plus the convention that ACK-only
 * packets carry a non-empty `tokens` list with `RecoveryToken.Ack`
 * (so the sent-packet map's invariant — every retained entry has at
 * least one token to dispatch — holds uniformly across packet types).
 */
class SentPacketTest {
    @Test
    fun equality_byAllFields() {
        val a =
            SentPacket(
                packetNumber = 7L,
                sentAtMillis = 1_700_000_000_000L,
                ackEliciting = true,
                sizeBytes = 128,
                tokens = listOf(RecoveryToken.MaxStreamsUni(150L)),
            )
        val b =
            SentPacket(
                packetNumber = 7L,
                sentAtMillis = 1_700_000_000_000L,
                ackEliciting = true,
                sizeBytes = 128,
                tokens = listOf(RecoveryToken.MaxStreamsUni(150L)),
            )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equality_byPacketNumber() {
        val a =
            SentPacket(
                packetNumber = 7L,
                sentAtMillis = 0L,
                ackEliciting = false,
                sizeBytes = 16,
                tokens = listOf(RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = 0L)),
            )
        val differentPn = a.copy(packetNumber = 8L)
        assertNotEquals(a, differentPn)
    }

    @Test
    fun equality_byTokenList() {
        val a =
            SentPacket(
                packetNumber = 1L,
                sentAtMillis = 0L,
                ackEliciting = true,
                sizeBytes = 64,
                tokens = listOf(RecoveryToken.MaxData(1_000L), RecoveryToken.MaxStreamsUni(150L)),
            )
        val swapped = a.copy(tokens = listOf(RecoveryToken.MaxStreamsUni(150L), RecoveryToken.MaxData(1_000L)))
        // List equality is order-sensitive — these are different.
        assertNotEquals(a, swapped)
    }

    @Test
    fun ackOnlyPacket_carriesAckToken() {
        // ACK-only packet: not ack-eliciting (RFC 9000 §13.2.1) but
        // we still record a single Ack token so the sent-packet map's
        // "every entry has a token" invariant holds.
        val ackOnly =
            SentPacket(
                packetNumber = 42L,
                sentAtMillis = 0L,
                ackEliciting = false,
                sizeBytes = 24,
                tokens = listOf(RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = 0L)),
            )
        assertTrue(ackOnly.tokens.isNotEmpty())
        assertEquals(
            RecoveryToken.Ack(level = EncryptionLevel.APPLICATION, largestAcked = 0L),
            ackOnly.tokens.single(),
        )
    }

    @Test
    fun ackEliciting_packetCanCarryMultipleControlTokens() {
        // A real outbound packet that bumps both flow-control caps
        // simultaneously (e.g. publisher session with sustained
        // stream churn) carries two tokens. Loss declaration walks
        // both; ACK drops both.
        val multi =
            SentPacket(
                packetNumber = 100L,
                sentAtMillis = 0L,
                ackEliciting = true,
                sizeBytes = 80,
                tokens =
                    listOf(
                        RecoveryToken.MaxStreamsUni(maxStreams = 1_500_000L),
                        RecoveryToken.MaxData(maxData = 64L * 1024L * 1024L),
                    ),
            )
        assertEquals(2, multi.tokens.size)
        assertEquals(RecoveryToken.MaxStreamsUni(1_500_000L), multi.tokens[0])
        assertEquals(RecoveryToken.MaxData(64L * 1024L * 1024L), multi.tokens[1])
    }

    @Test
    fun copy_preservesUntouchedFields() {
        val base =
            SentPacket(
                packetNumber = 5L,
                sentAtMillis = 1L,
                ackEliciting = true,
                sizeBytes = 100,
                tokens = listOf(RecoveryToken.MaxStreamsUni(150L)),
            )
        val onlyTimeChanged = base.copy(sentAtMillis = 2L)
        assertEquals(base.packetNumber, onlyTimeChanged.packetNumber)
        assertEquals(base.ackEliciting, onlyTimeChanged.ackEliciting)
        assertEquals(base.sizeBytes, onlyTimeChanged.sizeBytes)
        assertEquals(base.tokens, onlyTimeChanged.tokens)
        assertNotEquals(base.sentAtMillis, onlyTimeChanged.sentAtMillis)
    }
}
