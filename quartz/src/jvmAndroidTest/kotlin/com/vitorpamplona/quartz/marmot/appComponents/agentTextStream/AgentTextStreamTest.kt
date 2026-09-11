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
package com.vitorpamplona.quartz.marmot.appComponents.agentTextStream

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `marmot.group.agent-text-stream.quic.v1` (0x8006), checked against the bytes
 * MDK 0.9.20 actually installs. The fixture is the component state read off a
 * group `wn groups create` made on the interop harness.
 *
 * This component is the one that decides whether MDK will let us into a group
 * it created: its `required_member_roles` mask names MLS leaf capabilities, so
 * a client that neither advertises `0x8006` nor the `0xF2D1` receive
 * capability is refused at the Add, before any of our own code runs.
 */
class AgentTextStreamQuicPolicyV1Test {
    /** `wn groups create` default: require receive, allow receive+send, 4096-byte frames. */
    private val mdkDefaultState = "010300001000000000000000".hexToByteArray()

    @Test
    fun decodesTheComponentStateMdkInstalls() {
        val policy = AgentTextStreamQuicPolicyV1.decode(mdkDefaultState)

        assertEquals(AgentTextStreamRoles.RECEIVE, policy.requiredMemberRoles)
        assertEquals(AgentTextStreamRoles.RECEIVE or AgentTextStreamRoles.SEND, policy.allowedMemberRoles)
        assertEquals(4096L, policy.maxPlaintextFrameLen)
        assertEquals(0L, policy.replayTtlSecs)
        assertEquals(0, policy.paddingBucketBytes)
        assertTrue(policy.requires(AgentTextStreamRoles.RECEIVE))
        assertTrue(policy.allows(AgentTextStreamRoles.SEND))
    }

    @Test
    fun ourDefaultEncodesToTheSameBytes() {
        assertContentEquals(mdkDefaultState, AgentTextStreamQuicPolicyV1.userToAgentDefault().encode())
    }

    @Test
    fun requiredRolesMapToLeafCapabilities() {
        val policy = AgentTextStreamQuicPolicyV1.decode(mdkDefaultState)
        assertEquals(listOf(AgentTextStreamRoles.RECEIVE_CAPABILITY), policy.requiredRoleCapabilities())
        assertEquals(0xF2D1, AgentTextStreamRoles.RECEIVE_CAPABILITY)
        assertEquals(0xF2D2, AgentTextStreamRoles.SEND_CAPABILITY)
        assertEquals(0xF2D4, AgentTextStreamRoles.FANOUT_CAPABILITY)
    }

    @Test
    fun roundTripsEveryFieldAtItsBound() {
        val policy =
            AgentTextStreamQuicPolicyV1(
                requiredMemberRoles = AgentTextStreamRoles.MASK,
                allowedMemberRoles = AgentTextStreamRoles.MASK,
                maxPlaintextFrameLen = AgentTextStreamQuicPolicyV1.MAX_PLAINTEXT_FRAME_LEN,
                replayTtlSecs = AgentTextStreamQuicPolicyV1.MAX_REPLAY_TTL_SECS,
                paddingBucketBytes = AgentTextStreamQuicPolicyV1.MAX_PADDING_BUCKET_BYTES,
            )
        assertEquals(policy, AgentTextStreamQuicPolicyV1.decode(policy.encode()))
    }

    /**
     * These bytes sit in signed group state. A decoder that repaired them
     * would admit a member the group's own policy refuses, so every one of
     * these is a hard failure rather than a default.
     */
    @Test
    fun rejectsStateItCannotActOn() {
        // Wrong length.
        assertFailsWith<IllegalArgumentException> { AgentTextStreamQuicPolicyV1.decode(ByteArray(11)) }
        assertFailsWith<IllegalArgumentException> { AgentTextStreamQuicPolicyV1.decode(ByteArray(13)) }
        // No required role at all.
        assertFailsWith<IllegalArgumentException> {
            AgentTextStreamQuicPolicyV1.decode("000300001000000000000000".hexToByteArray())
        }
        // Unknown role bit — a mask we cannot map to a capability.
        assertFailsWith<IllegalArgumentException> {
            AgentTextStreamQuicPolicyV1.decode("080b00001000000000000000".hexToByteArray())
        }
        // Required roles outside the allowed set.
        assertFailsWith<IllegalArgumentException> {
            AgentTextStreamQuicPolicyV1.decode("020100001000000000000000".hexToByteArray())
        }
        // Zero frame limit, and one past the app-profile cap (65519 -> 65520).
        assertFailsWith<IllegalArgumentException> {
            AgentTextStreamQuicPolicyV1.decode("010300000000000000000000".hexToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            AgentTextStreamQuicPolicyV1.decode("01030000fff0000000000000".hexToByteArray())
        }
        assertNull(AgentTextStreamQuicPolicyV1.decodeOrNull(ByteArray(0)))
    }
}

/**
 * The QUIC variable-length integer is the length prefix for every field in the
 * stream wire formats, and it is also hashed into the transcript and the key
 * context — so a wrong prefix is not a parse error but a different key.
 */
class QuicVarIntTest {
    @Test
    fun encodesEachWidthAtItsBoundary() {
        assertContentEquals(byteArrayOf(0x00), QuicVarInt.encode(0))
        assertContentEquals(byteArrayOf(0x20), QuicVarInt.encode(32))
        assertContentEquals(byteArrayOf(0x3f), QuicVarInt.encode(63))
        assertContentEquals(byteArrayOf(0x40, 0x40), QuicVarInt.encode(64))
        assertContentEquals(byteArrayOf(0x7f, 0xff.toByte()), QuicVarInt.encode(16_383))
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x00, 0x40, 0x00), QuicVarInt.encode(16_384))
    }

    @Test
    fun roundTripsAcrossWidths() {
        for (value in listOf(0L, 1L, 63L, 64L, 16_383L, 16_384L, 1_073_741_823L, 1_073_741_824L)) {
            val encoded = QuicVarInt.encode(value)
            val decoded = QuicVarInt.decode(encoded)
            assertEquals(value, decoded.value)
            assertEquals(encoded.size, decoded.length)
        }
    }

    @Test
    fun rejectsATruncatedPrefix() {
        assertFailsWith<IllegalArgumentException> { QuicVarInt.decode(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { QuicVarInt.decode(byteArrayOf(0x40)) }
    }
}

class AgentTextStreamRecordV1Test {
    private val streamId = ByteArray(32) { 0x5a }

    @Test
    fun roundTripsAFrame() {
        val record = AgentTextStreamRecordV1.textDelta(streamId, 7, "hello".encodeToByteArray())
        val decoded = AgentTextStreamRecordV1.decode(record.encode())

        assertEquals(record, decoded)
        assertEquals(record.encodedLength(), record.encode().size)
    }

    /**
     * A newer advisory record type must not tear down an otherwise valid
     * preview stream, so framing accepts types it has no semantics for.
     */
    @Test
    fun acceptsAnUnknownRecordType() {
        val record = AgentTextStreamRecordV1(streamId, 1, recordType = 0x7f, frame = ByteArray(4))
        assertEquals(0x7f, AgentTextStreamRecordV1.decode(record.encode()).recordType)
    }

    @Test
    fun rejectsFramingItCannotTrust() {
        val encoded = AgentTextStreamRecordV1.textDelta(streamId, 1, ByteArray(4)).encode()

        // Trailing bytes: the sender and receiver disagree where the record ends.
        assertFailsWith<IllegalArgumentException> { AgentTextStreamRecordV1.decode(encoded + 0x00) }
        // Truncated.
        assertFailsWith<IllegalArgumentException> { AgentTextStreamRecordV1.decode(encoded.copyOf(encoded.size - 1)) }
        // A version we do not speak.
        val wrongVersion = encoded.copyOf()
        wrongVersion[0] = 2
        assertFailsWith<IllegalArgumentException> { AgentTextStreamRecordV1.decode(wrongVersion) }
        // An empty stream id names no stream.
        assertFailsWith<IllegalArgumentException> {
            AgentTextStreamRecordV1(ByteArray(0), 1, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, frame = ByteArray(0))
        }
    }
}

class AgentTextStreamCryptoTest {
    private val secret = ByteArray(32) { it.toByte() }
    private val streamId = ByteArray(32) { 0x11 }
    private val startEventId = ByteArray(32) { 0x22 }

    private fun crypto(
        epoch: Long = 3,
        stream: ByteArray = streamId,
    ) = AgentTextStreamCrypto(
        secret,
        AgentTextStreamKeyContextV1(
            groupId = ByteArray(16) { 0x33 },
            streamId = stream,
            mlsEpoch = epoch,
            senderId = ByteArray(32) { 0x44 },
            startEventId = startEventId,
        ),
    )

    @Test
    fun sealAndOpenRoundTrip() {
        val c = crypto()
        val record = AgentTextStreamRecordV1.textDelta(streamId, 5, "streamed text".encodeToByteArray())
        val sealed = c.seal(record)

        assertTrue(sealed.frame.size == record.frame.size + AgentTextStreamRecordV1.AEAD_TAG_LEN)
        assertContentEquals(record.frame, c.open(sealed).frame)
    }

    /**
     * `seq` is in both the nonce and the AAD, so a replayed or reordered
     * record does not merely look wrong — it fails to open.
     */
    @Test
    fun aRecordDoesNotOpenAtADifferentSequence() {
        val c = crypto()
        val sealed = c.seal(AgentTextStreamRecordV1.textDelta(streamId, 5, "abc".encodeToByteArray()))
        val replayed =
            AgentTextStreamRecordV1(
                streamId = sealed.streamId,
                seq = 6,
                recordType = sealed.recordType,
                flags = sealed.flags,
                frame = sealed.frame,
            )
        assertNull(c.openOrNull(replayed))
    }

    /** Nonce 0 is the base itself, and each seq flips only the low 64 bits. */
    @Test
    fun theNonceIsTheBaseXorTheSequence() {
        val c = crypto()
        val base = c.recordNonce(0)
        val one = c.recordNonce(1)
        assertEquals(AgentTextStreamCrypto.NONCE_LENGTH, base.size)
        assertContentEquals(base.copyOf(11), one.copyOf(11))
        assertEquals((base[11].toInt() xor 1).toByte(), one[11])
    }

    /**
     * The key context is the only thing separating two streams that share one
     * group exporter secret, so changing any part of it must change the key.
     */
    @Test
    fun everyKeyContextFieldSeparatesTheKey() {
        val base = crypto().recordKey().toHexKey()
        assertTrue(crypto(epoch = 4).recordKey().toHexKey() != base)
        assertTrue(crypto(stream = ByteArray(32) { 0x12 }).recordKey().toHexKey() != base)
    }

    @Test
    fun refusesARecordFromAnotherStream() {
        val c = crypto()
        val foreign = AgentTextStreamRecordV1.textDelta(ByteArray(32) { 0x77 }, 1, ByteArray(4))
        assertFailsWith<IllegalArgumentException> { c.seal(foreign) }
    }

    @Test
    fun refusesAFrameOverTheGroupLimit() {
        val c = crypto()
        val record = AgentTextStreamRecordV1.textDelta(streamId, 1, ByteArray(5000))
        assertFailsWith<IllegalArgumentException> { c.seal(record, maxPlaintextFrameLen = 4096) }
    }
}

class AgentTextStreamTranscriptV1Test {
    private val streamId = ByteArray(32) { 0x11 }
    private val startEventId = ByteArray(32) { 0x22 }

    @Test
    fun foldsRecordsInOrder() {
        val a = AgentTextStreamTranscriptV1.start(streamId, startEventId)
        val b = AgentTextStreamTranscriptV1.start(streamId, startEventId)

        a.append(0, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "one".encodeToByteArray())
        a.append(1, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "two".encodeToByteArray())
        b.append(1, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "two".encodeToByteArray())
        b.append(0, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "one".encodeToByteArray())

        assertEquals(2, a.chunkCount)
        assertTrue(!a.hash.contentEquals(b.hash), "a reordered stream must not hash the same")
    }

    @Test
    fun resumesFromDurableState() {
        val original = AgentTextStreamTranscriptV1.start(streamId, startEventId)
        original.append(0, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "one".encodeToByteArray())

        val resumed = AgentTextStreamTranscriptV1.resume(streamId, startEventId, original.hash, original.chunkCount)
        resumed.append(1, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "two".encodeToByteArray())
        original.append(1, AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "two".encodeToByteArray())

        assertContentEquals(original.hash, resumed.hash)
        assertEquals(original.chunkCount, resumed.chunkCount)
    }
}

class AgentTextStreamStartTest {
    @Test
    fun readsTheAnchorTags() {
        val tags =
            AgentTextStreamStart.tags(
                streamId = ByteArray(32) { 0x5a }.toHexKey(),
                brokerCandidates = listOf("https://broker.example:4443", "https://alt.example:4443"),
            )
        val start = assertNotNull(AgentTextStreamStart.fromTags(AgentTextStreamStart.KIND, tags))

        assertTrue(start.isQuicRoute)
        assertEquals(2, start.brokerCandidates.size)
        assertEquals(ByteArray(32) { 0x5a }.toHexKey(), start.streamId)
    }

    @Test
    fun defaultsAMissingRouteToQuicButNeverAMissingStreamId() {
        assertTrue(
            assertNotNull(
                AgentTextStreamStart.fromTags(AgentTextStreamStart.KIND, arrayOf(arrayOf("stream", "ab"))),
            ).isQuicRoute,
        )
        assertNull(AgentTextStreamStart.fromTags(AgentTextStreamStart.KIND, arrayOf(arrayOf("route", "quic"))))
        assertNull(AgentTextStreamStart.fromTags(9, arrayOf(arrayOf("stream", "ab"))))
    }

    @Test
    fun readsTheFinalTranscriptTags() {
        val tags = AgentTextStreamFinal.tags("aa", "bb", 12)
        val final = assertNotNull(AgentTextStreamFinal.fromTags(tags))
        assertEquals("aa", final.streamId)
        assertEquals("bb", final.transcriptHash)
        assertEquals(12, final.chunkCount)
        assertNull(AgentTextStreamFinal.fromTags(arrayOf(arrayOf("stream", "aa"))))
    }
}
