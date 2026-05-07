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
package com.vitorpamplona.quic.interop

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quic.connection.EncryptionLevel
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates the JSON-NDJSON shape qvis (https://qvis.quictools.info/)
 * expects: header line + one event per line, every line independently
 * parseable as JSON.
 *
 * Drives [QlogWriter] through one event of each type to confirm we
 * don't emit anything that breaks the format.
 */
class QlogWriterTest {
    @Test
    fun headerThenOneEventPerLine_allParseable() {
        val tmp = Files.createTempFile("amethyst-qlog-test", ".sqlog").toFile()
        tmp.deleteOnExit()
        var clock = 0L
        QlogWriter(tmp, odcidHex = "deadbeef", nowMillis = { clock }).use { w ->
            clock = 5L
            w.onConnectionStarted("example.test", byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
            clock = 7L
            w.onTransportParametersSet("local", mapOf("initial_max_data" to "1000000"))
            clock = 10L
            w.onPacketSent(EncryptionLevel.INITIAL, packetNumber = 0, sizeBytes = 1200, frames = listOf("crypto"))
            clock = 15L
            w.onPacketReceived(EncryptionLevel.INITIAL, packetNumber = 0, sizeBytes = 800, frames = listOf("crypto", "ack"))
            clock = 20L
            w.onPacketDropped("AEAD auth failed", sizeBytes = 80)
            clock = 25L
            w.onKeyUpdated("server", EncryptionLevel.HANDSHAKE)
            clock = 30L
            w.onLossDetected(EncryptionLevel.APPLICATION, lostPacketNumbers = listOf(3L, 5L))
            clock = 35L
            w.onPtoFired(consecutivePtoCount = 1, ptoMillis = 333)
            clock = 40L
            w.onCongestionStateUpdated("recovery")
            clock = 45L
            w.onAlpnNegotiated("h3")
            clock = 50L
            w.onVersionInformation("v1", emptyList())
            clock = 55L
            w.onConnectionClosed("local", errorCode = 0, reason = "done")
        }

        val lines = tmp.readLines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 12, "expected >= 12 lines (header + at least 11 events) but got ${lines.size}")

        val mapper = jacksonObjectMapper()

        // Line 1: qlog header.
        val header = mapper.readTree(lines[0])
        assertEquals("0.3", header.get("qlog_version").asText(), "qlog_version must be 0.3")
        assertEquals("JSON-SEQ", header.get("qlog_format").asText(), "qlog_format must be JSON-SEQ")
        val vp = header.get("trace").get("vantage_point")
        assertEquals("client", vp.get("type").asText())
        assertEquals(
            "deadbeef",
            header
                .get("trace")
                .get("common_fields")
                .get("ODCID")
                .asText(),
        )

        // Lines 2..N: event objects with `time`, `name`, `data`.
        for (i in 1 until lines.size) {
            val node = mapper.readTree(lines[i])
            assertNotNull(node.get("time"), "line $i missing 'time': ${lines[i]}")
            val name = node.get("name")
            assertNotNull(name, "line $i missing 'name': ${lines[i]}")
            assertTrue(
                name.asText().contains(":"),
                "name '${name.asText()}' must be in '<category>:<event>' form",
            )
            assertNotNull(node.get("data"), "line $i missing 'data': ${lines[i]}")
        }

        // Spot-check specific events made it through.
        val names = lines.drop(1).map { mapper.readTree(it).get("name").asText() }
        assertTrue(names.contains("transport:connection_started"), names.toString())
        assertTrue(names.contains("transport:packet_sent"), names.toString())
        assertTrue(names.contains("transport:packet_received"), names.toString())
        assertTrue(names.contains("transport:packet_dropped"), names.toString())
        assertTrue(names.contains("security:key_updated"), names.toString())
        assertTrue(names.contains("recovery:packet_lost"), names.toString())
        assertTrue(names.contains("recovery:loss_timer_updated"), names.toString())
        assertTrue(names.contains("transport:parameters_set"), names.toString())
        assertTrue(names.contains("transport:alpn_information"), names.toString())
        assertTrue(names.contains("transport:version_information"), names.toString())
        assertTrue(names.contains("transport:connection_closed"), names.toString())
    }

    @Test
    fun timesAreRelativeToConstructorTime() {
        val tmp = Files.createTempFile("amethyst-qlog-rel", ".sqlog").toFile()
        tmp.deleteOnExit()
        var clock = 1_000L
        QlogWriter(tmp, odcidHex = "00", nowMillis = { clock }).use { w ->
            clock = 1_050L
            w.onAlpnNegotiated("h3")
        }
        val lines = tmp.readLines().filter { it.isNotBlank() }
        val mapper = jacksonObjectMapper()
        val event = mapper.readTree(lines[1])
        assertEquals(50L, event.get("time").asLong(), "time must be relative to constructor (1050 - 1000)")
    }

    @Test
    fun fileEndsWithNewline_qvisCompatible() {
        val tmp = Files.createTempFile("amethyst-qlog-nl", ".sqlog").toFile()
        tmp.deleteOnExit()
        QlogWriter(tmp, odcidHex = "00").use { w ->
            w.onAlpnNegotiated("h3")
        }
        val bytes = tmp.readBytes()
        assertTrue(bytes.isNotEmpty(), "file must not be empty")
        assertEquals('\n'.code.toByte(), bytes.last(), "file must end with '\\n' so trailing event parses")
    }

    @Test
    fun handlesEmptyFramesList(): Unit =
        File.createTempFile("amethyst-qlog-empty", ".sqlog").let { tmp ->
            tmp.deleteOnExit()
            QlogWriter(tmp, odcidHex = "00").use { w ->
                w.onPacketSent(EncryptionLevel.INITIAL, 0, 1200, emptyList())
            }
            val mapper = jacksonObjectMapper()
            val lines = tmp.readLines().filter { it.isNotBlank() }
            val frames = mapper.readTree(lines[1]).get("data").get("frames")
            assertTrue(frames.isArray, "frames must be an array even when empty")
            assertEquals(0, frames.size())
        }
}
