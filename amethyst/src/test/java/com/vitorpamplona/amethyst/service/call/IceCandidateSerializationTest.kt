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
package com.vitorpamplona.amethyst.service.call

import org.junit.Assert.assertEquals
import org.junit.Test

class IceCandidateSerializationTest {
    @Test
    fun parseIceCandidateFromJson() {
        val json = """{"candidate":"candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx","sdpMid":"0","sdpMLineIndex":0}"""
        val candidate = CallController.parseIceCandidate(json)

        assertEquals("candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx", candidate.sdp)
        assertEquals("0", candidate.sdpMid)
        assertEquals(0, candidate.sdpMLineIndex)
    }

    @Test
    fun parseIceCandidateWithDifferentIndex() {
        val json = """{"candidate":"candidate:1 1 tcp 1234","sdpMid":"audio","sdpMLineIndex":1}"""
        val candidate = CallController.parseIceCandidate(json)

        assertEquals("candidate:1 1 tcp 1234", candidate.sdp)
        assertEquals("audio", candidate.sdpMid)
        assertEquals(1, candidate.sdpMLineIndex)
    }

    @Test
    fun parseIceCandidateHandlesMissingFields() {
        val json = """{"candidate":"test"}"""
        val candidate = CallController.parseIceCandidate(json)

        assertEquals("test", candidate.sdp)
        assertEquals("0", candidate.sdpMid) // default
        assertEquals(0, candidate.sdpMLineIndex) // default
    }

    @Test
    fun serializeIceCandidateProducesValidJson() {
        val candidate = org.webrtc.IceCandidate("audio", 1, "candidate:123 1 udp 456")
        val json = CallController.serializeIceCandidate(candidate)

        // Verify it contains the expected fields
        assert(json.contains(""""candidate":"candidate:123 1 udp 456""""))
        assert(json.contains(""""sdpMid":"audio""""))
        assert(json.contains(""""sdpMLineIndex":1"""))
    }

    @Test
    fun roundTripIceCandidate() {
        val original = org.webrtc.IceCandidate("video", 2, "candidate:999 1 udp 789 10.0.0.1 5000 typ host")
        val json = CallController.serializeIceCandidate(original)
        val parsed = CallController.parseIceCandidate(json)

        assertEquals(original.sdp, parsed.sdp)
        assertEquals(original.sdpMid, parsed.sdpMid)
        assertEquals(original.sdpMLineIndex, parsed.sdpMLineIndex)
    }
}
