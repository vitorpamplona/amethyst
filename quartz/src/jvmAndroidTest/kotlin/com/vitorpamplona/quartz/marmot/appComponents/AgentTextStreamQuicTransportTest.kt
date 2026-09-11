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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.AgentTextStreamFraming
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.BrokerControlType
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicAlpn
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.QuicBrokerControlEnvelopeV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.QuicEndpointCandidate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `transports/quic.md` — the raw QUIC binding for agent text stream previews.
 *
 * This is the wire between us and a broker written by somebody else, so every
 * rule here is one an independent implementation will hold us to: the exact
 * ALPN strings, the control envelope's field layout and its literal protocol
 * string, the 4-byte frame prefix and its caps, and what a candidate URL does
 * and does not mean.
 */
class AgentTextStreamQuicTransportTest {
    private val streamId = ByteArray(32) { 0x11 }
    private val startEventId = ByteArray(32) { 0x22 }

    // --- ALPN -------------------------------------------------------------

    @Test
    fun theTwoAlpnsAreTheExactStringsTheSpecNames() {
        assertEquals("marmot.quic_broker.v1", MarmotQuicAlpn.BROKER.decodeToString())
        assertEquals("marmot.quic_stream.v1", MarmotQuicAlpn.DIRECT.decodeToString())
    }

    // --- Broker control envelope -----------------------------------------

    @Test
    fun aControlEnvelopeRoundTrips() {
        for (type in BrokerControlType.entries) {
            val envelope = QuicBrokerControlEnvelopeV1(type, streamId, startEventId)
            val decoded = QuicBrokerControlEnvelopeV1.decode(envelope.encode())
            assertEquals(type, decoded.controlType)
            assertArrayEquals(streamId, decoded.streamId)
            assertArrayEquals(startEventId, decoded.startEventId)
        }
    }

    @Test
    fun theEnvelopeStartsWithTheLengthPrefixedProtocolString() {
        val encoded = QuicBrokerControlEnvelopeV1(BrokerControlType.PUBLISH, streamId, startEventId).encode()
        // varint(21) fits in one byte, so the protocol string starts at index 1.
        assertEquals(21, encoded[0].toInt())
        assertEquals("marmot.quic_broker.v1", encoded.copyOfRange(1, 22).decodeToString())
        assertEquals(BrokerControlType.PUBLISH.code, encoded[22].toInt())
    }

    @Test
    fun anEnvelopeNamingAnotherProtocolIsRejected() {
        val good = QuicBrokerControlEnvelopeV1(BrokerControlType.SUBSCRIBE, streamId, startEventId).encode()
        // Same length, different string: a broker must reject on the bytes, not the length.
        val tampered = good.copyOf()
        tampered[1] = 'M'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) { QuicBrokerControlEnvelopeV1.decode(tampered) }
    }

    @Test
    fun anUnknownControlTypeIsRejectedRatherThanIgnored() {
        val encoded = QuicBrokerControlEnvelopeV1(BrokerControlType.PUBLISH, streamId, startEventId).encode()
        encoded[22] = 0x7f
        assertThrows(IllegalArgumentException::class.java) { QuicBrokerControlEnvelopeV1.decode(encoded) }
    }

    @Test
    fun trailingBytesAfterTheEnvelopeAreRejected() {
        val encoded = QuicBrokerControlEnvelopeV1(BrokerControlType.PUBLISH, streamId, startEventId).encode()
        assertThrows(IllegalArgumentException::class.java) {
            QuicBrokerControlEnvelopeV1.decode(encoded + byteArrayOf(0x00))
        }
    }

    @Test
    fun anIdentityFieldOutsideItsBoundIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QuicBrokerControlEnvelopeV1(BrokerControlType.PUBLISH, ByteArray(0), startEventId).encode()
        }
        assertThrows(IllegalArgumentException::class.java) {
            QuicBrokerControlEnvelopeV1(BrokerControlType.PUBLISH, ByteArray(65), startEventId).encode()
        }
    }

    // --- Record framing ---------------------------------------------------

    @Test
    fun aFrameIsAFourByteBigEndianLengthFollowedByTheRecord() {
        val record = AgentTextStreamRecordV1(streamId, seq = 1, recordType = 1, frame = ByteArray(7) { 0x5a })
        val encoded = record.encode()
        val framed = AgentTextStreamFraming.frame(record)

        assertEquals(4 + encoded.size, framed.size)
        assertEquals(0, framed[0].toInt())
        assertEquals(0, framed[1].toInt())
        assertEquals((encoded.size shr 8) and 0xff, framed[2].toInt() and 0xff)
        assertEquals(encoded.size and 0xff, framed[3].toInt() and 0xff)
        assertArrayEquals(encoded, framed.copyOfRange(4, framed.size))
    }

    @Test
    fun theReaderDeliversRecordsAsTheirBytesArriveInAnySplit() {
        val records =
            (1..4).map {
                AgentTextStreamRecordV1(streamId, seq = it.toLong(), recordType = 1, frame = "chunk $it".encodeToByteArray())
            }
        val wire = records.fold(ByteArray(0)) { acc, r -> acc + AgentTextStreamFraming.frame(r) }

        // One byte at a time is the worst case a QUIC stream can hand us.
        val reader = AgentTextStreamFraming.Reader()
        val delivered = mutableListOf<AgentTextStreamRecordV1>()
        for (b in wire) delivered.addAll(reader.push(byteArrayOf(b)))

        assertEquals(records.size, delivered.size)
        delivered.forEachIndexed { i, r ->
            assertEquals(records[i].seq, r.seq)
            assertArrayEquals(records[i].frame, r.frame)
        }
    }

    @Test
    fun theReaderRefusesAFrameOverTheBrokerCap() {
        val reader = AgentTextStreamFraming.Reader()
        val tooBig = AgentTextStreamFraming.BROKER_MAX_FRAME_LEN + 1
        val header =
            byteArrayOf(
                ((tooBig shr 24) and 0xff).toByte(),
                ((tooBig shr 16) and 0xff).toByte(),
                ((tooBig shr 8) and 0xff).toByte(),
                (tooBig and 0xff).toByte(),
            )
        assertThrows(IllegalArgumentException::class.java) { reader.push(header) }
    }

    @Test
    fun aReaderThatKnowsTheGroupPolicyRefusesAnythingOverIt() {
        // max_plaintext_frame_len + the spec's 1024-byte header/tag allowance.
        val reader = AgentTextStreamFraming.Reader(maxPlaintextFrameLen = 16)
        val record = AgentTextStreamRecordV1(streamId, seq = 1, recordType = 1, frame = ByteArray(2000))
        assertThrows(IllegalArgumentException::class.java) { reader.push(AgentTextStreamFraming.frame(record)) }
    }

    @Test
    fun aReaderPinsTheStreamIdOfItsFirstRecord() {
        val reader = AgentTextStreamFraming.Reader()
        reader.push(AgentTextStreamFraming.frame(AgentTextStreamRecordV1(streamId, 1, 1, frame = ByteArray(1))))
        val otherStream = ByteArray(32) { 0x33 }
        assertThrows(IllegalArgumentException::class.java) {
            reader.push(AgentTextStreamFraming.frame(AgentTextStreamRecordV1(otherStream, 2, 1, frame = ByteArray(1))))
        }
    }

    // --- Endpoint candidates ---------------------------------------------

    @Test
    fun aCandidateIsAnAuthorityAndNothingAfterIt() {
        val parsed = QuicEndpointCandidate.parse("quic://broker.example:4433")!!
        assertEquals("broker.example", parsed.host)
        assertEquals(4433, parsed.port)
        assertTrue(parsed.isDnsName)

        // A path, query or fragment is ignored, not rejected.
        for (suffix in listOf("/room/1", "?x=1", "#frag")) {
            val withSuffix = QuicEndpointCandidate.parse("quic://broker.example:4433$suffix")!!
            assertEquals("broker.example", withSuffix.host)
            assertEquals(4433, withSuffix.port)
        }
    }

    @Test
    fun anIpv6LiteralKeepsItsBracketsOutOfTheHost() {
        val parsed = QuicEndpointCandidate.parse("quic://[2001:db8::1]:443")!!
        assertEquals("2001:db8::1", parsed.host)
        assertEquals(443, parsed.port)
        assertTrue(
            "an IP literal is matched against an iPAddress SAN and is never sent as SNI",
            !parsed.isDnsName,
        )
        assertNull(parsed.serverNameIndication)
    }

    @Test
    fun anIpv4LiteralIsAlsoNotASniName() {
        val parsed = QuicEndpointCandidate.parse("quic://192.0.2.7:4433")!!
        assertEquals("192.0.2.7", parsed.host)
        assertTrue(!parsed.isDnsName)
        assertNull(parsed.serverNameIndication)
    }

    @Test
    fun aDnsCandidateCarriesItsOwnSni() {
        assertEquals("broker.example", QuicEndpointCandidate.parse("quic://broker.example:4433")!!.serverNameIndication)
    }

    @Test
    fun anUnusableCandidateIsSkippedNotFatal() {
        // The spec says a receiver moves to the next candidate; null is that.
        val bad =
            listOf(
                "https://broker.example:4433",
                "quic://broker.example",
                "quic://broker.example:0",
                "quic://broker.example:65536",
                "quic://:4433",
                "quic://[2001:db8::1:443",
                "quic://" + "h".repeat(506) + ":443",
                "",
            )
        for (candidate in bad) {
            assertNull("must not accept $candidate", QuicEndpointCandidate.parse(candidate))
        }
    }

    @Test
    fun aCandidateOverTheByteBoundIsRejected() {
        val authority = "h".repeat(505 - 4) + ":443"
        assertEquals(512, ("quic://$authority").length)
        assertTrue(QuicEndpointCandidate.parse("quic://$authority") != null)
        assertNull(QuicEndpointCandidate.parse("quic://x$authority"))
    }
}
