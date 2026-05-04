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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Type-level invariants for [RecoveryToken]: equality + hashCode
 * correctness across data-class variants, the singleton [Ack] object,
 * and the typed-token shape required by the dispatcher in step 6 of
 * `quic/plans/2026-05-04-control-frame-retransmit.md`.
 *
 * No connection state is exercised here — these are unit tests on
 * the type itself.
 */
class RecoveryTokenTest {
    @Test
    fun ack_isSingleton() {
        val a: RecoveryToken = RecoveryToken.Ack
        val b: RecoveryToken = RecoveryToken.Ack
        // `data object Ack` ⇒ same reference, same hash.
        assertTrue(a === b)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun maxStreamsUni_equalityByValue() {
        val t1 = RecoveryToken.MaxStreamsUni(maxStreams = 150L)
        val t2 = RecoveryToken.MaxStreamsUni(maxStreams = 150L)
        val t3 = RecoveryToken.MaxStreamsUni(maxStreams = 200L)

        assertEquals(t1, t2)
        assertEquals(t1.hashCode(), t2.hashCode())
        assertNotEquals(t1, t3)
    }

    @Test
    fun maxStreamsBidi_distinctFromUni() {
        val uni: RecoveryToken = RecoveryToken.MaxStreamsUni(maxStreams = 100L)
        val bidi: RecoveryToken = RecoveryToken.MaxStreamsBidi(maxStreams = 100L)
        assertNotEquals(uni, bidi)
    }

    @Test
    fun maxData_equalityByValue() {
        val t1 = RecoveryToken.MaxData(maxData = 1_000_000L)
        val t2 = RecoveryToken.MaxData(maxData = 1_000_000L)
        val t3 = RecoveryToken.MaxData(maxData = 2_000_000L)

        assertEquals(t1, t2)
        assertNotEquals(t1, t3)
    }

    @Test
    fun maxStreamData_equalityByPair() {
        val t1 = RecoveryToken.MaxStreamData(streamId = 0L, maxData = 1024L)
        val t2 = RecoveryToken.MaxStreamData(streamId = 0L, maxData = 1024L)
        val differentStream = RecoveryToken.MaxStreamData(streamId = 4L, maxData = 1024L)
        val differentValue = RecoveryToken.MaxStreamData(streamId = 0L, maxData = 2048L)

        assertEquals(t1, t2)
        assertNotEquals(t1, differentStream)
        assertNotEquals(t1, differentValue)
    }

    @Test
    fun whenDispatch_isExhaustive() {
        // Compile-time assertion that the sealed hierarchy is fixed and
        // an exhaustive `when` actually compiles. If a new variant is
        // added without updating the dispatcher, this test stops
        // compiling — caught at build time, not at runtime.
        val tokens: List<RecoveryToken> =
            listOf(
                RecoveryToken.Ack,
                RecoveryToken.MaxStreamsUni(150L),
                RecoveryToken.MaxStreamsBidi(150L),
                RecoveryToken.MaxData(1_000_000L),
                RecoveryToken.MaxStreamData(streamId = 0L, maxData = 1024L),
                RecoveryToken.Stream(streamId = 4L, offset = 0L, length = 100L, fin = false),
                RecoveryToken.Crypto(
                    level = com.vitorpamplona.quic.connection.EncryptionLevel.HANDSHAKE,
                    offset = 0L,
                    length = 64L,
                ),
                RecoveryToken.ResetStream(streamId = 4L, errorCode = 0L, finalSize = 100L),
                RecoveryToken.StopSending(streamId = 4L, errorCode = 0L),
                RecoveryToken.NewConnectionId(
                    sequenceNumber = 1L,
                    retirePriorTo = 0L,
                    connectionId = byteArrayOf(1, 2, 3, 4),
                    statelessResetToken = ByteArray(16) { it.toByte() },
                ),
            )
        val labels =
            tokens.map {
                when (it) {
                    RecoveryToken.Ack -> "ack"
                    is RecoveryToken.MaxStreamsUni -> "msu:${it.maxStreams}"
                    is RecoveryToken.MaxStreamsBidi -> "msb:${it.maxStreams}"
                    is RecoveryToken.MaxData -> "md:${it.maxData}"
                    is RecoveryToken.MaxStreamData -> "msd:${it.streamId}:${it.maxData}"
                    is RecoveryToken.Stream -> "s:${it.streamId}:${it.offset}:${it.length}:${it.fin}"
                    is RecoveryToken.Crypto -> "c:${it.level}:${it.offset}:${it.length}"
                    is RecoveryToken.ResetStream -> "rs:${it.streamId}:${it.errorCode}:${it.finalSize}"
                    is RecoveryToken.StopSending -> "ss:${it.streamId}:${it.errorCode}"
                    is RecoveryToken.NewConnectionId -> "ncid:${it.sequenceNumber}"
                }
            }
        assertEquals(
            listOf(
                "ack",
                "msu:150",
                "msb:150",
                "md:1000000",
                "msd:0:1024",
                "s:4:0:100:false",
                "c:HANDSHAKE:0:64",
                "rs:4:0:100",
                "ss:4:0",
                "ncid:1",
            ),
            labels,
        )
    }

    @Test
    fun newConnectionId_arrayEqualityIsByContent() {
        // ByteArray fields must equal by content, not identity, so
        // the sent-packet map's equality semantics work correctly.
        val a =
            RecoveryToken.NewConnectionId(
                sequenceNumber = 1L,
                retirePriorTo = 0L,
                connectionId = byteArrayOf(1, 2, 3),
                statelessResetToken = ByteArray(16) { 0 },
            )
        val b =
            RecoveryToken.NewConnectionId(
                sequenceNumber = 1L,
                retirePriorTo = 0L,
                connectionId = byteArrayOf(1, 2, 3),
                statelessResetToken = ByteArray(16) { 0 },
            )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, b.copy(connectionId = byteArrayOf(9, 9, 9)))
        assertNotEquals(a, b.copy(statelessResetToken = ByteArray(16) { 7 }))
    }

    @Test
    fun stream_equalityByValue() {
        val t1 = RecoveryToken.Stream(streamId = 4L, offset = 0L, length = 100L, fin = false)
        val t2 = RecoveryToken.Stream(streamId = 4L, offset = 0L, length = 100L, fin = false)
        val tFin = RecoveryToken.Stream(streamId = 4L, offset = 0L, length = 100L, fin = true)
        val tDifferentOffset = RecoveryToken.Stream(streamId = 4L, offset = 100L, length = 100L, fin = false)
        assertEquals(t1, t2)
        assertNotEquals(t1, tFin)
        assertNotEquals(t1, tDifferentOffset)
    }

    @Test
    fun crypto_equalityByValue() {
        val a =
            RecoveryToken.Crypto(
                level = com.vitorpamplona.quic.connection.EncryptionLevel.INITIAL,
                offset = 0L,
                length = 64L,
            )
        val b =
            RecoveryToken.Crypto(
                level = com.vitorpamplona.quic.connection.EncryptionLevel.INITIAL,
                offset = 0L,
                length = 64L,
            )
        val differentLevel = a.copy(level = com.vitorpamplona.quic.connection.EncryptionLevel.HANDSHAKE)
        assertEquals(a, b)
        assertNotEquals(a, differentLevel)
    }
}
