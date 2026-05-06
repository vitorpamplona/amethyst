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
package com.vitorpamplona.quic.interop.runner

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SslKeyLoggerTest {
    @Test
    fun `emits NSS Key Log lines for handshake and application secrets`() {
        val tmp = File.createTempFile("ssl-keylog-test", ".log").also { it.deleteOnExit() }
        val logger = SslKeyLogger(tmp)

        logger.listener.onHandshakeKeysReady(
            cipherSuite = 0x1301,
            clientSecret = ByteArray(32) { 0xAA.toByte() },
            serverSecret = ByteArray(32) { 0xBB.toByte() },
        )
        logger.listener.onApplicationKeysReady(
            cipherSuite = 0x1301,
            clientSecret = ByteArray(32) { 0xCC.toByte() },
            serverSecret = ByteArray(32) { 0xDD.toByte() },
        )

        val clientRandom = ByteArray(32) { it.toByte() }
        logger.flush(clientRandom)

        val lines =
            tmp
                .readText()
                .lineSequence()
                .filter { it.isNotEmpty() }
                .toList()
        assertEquals(4, lines.size, "one line per secret")

        val randomHex = clientRandom.toHex()
        val expectedClientHs = "CLIENT_HANDSHAKE_TRAFFIC_SECRET $randomHex ${ByteArray(32) { 0xAA.toByte() }.toHex()}"
        val expectedServerHs = "SERVER_HANDSHAKE_TRAFFIC_SECRET $randomHex ${ByteArray(32) { 0xBB.toByte() }.toHex()}"
        val expectedClientApp = "CLIENT_TRAFFIC_SECRET_0 $randomHex ${ByteArray(32) { 0xCC.toByte() }.toHex()}"
        val expectedServerApp = "SERVER_TRAFFIC_SECRET_0 $randomHex ${ByteArray(32) { 0xDD.toByte() }.toHex()}"

        assertEquals(expectedClientHs, lines[0])
        assertEquals(expectedServerHs, lines[1])
        assertEquals(expectedClientApp, lines[2])
        assertEquals(expectedServerApp, lines[3])
    }

    @Test
    fun `flush is idempotent — second flush emits nothing`() {
        val tmp = File.createTempFile("ssl-keylog-test", ".log").also { it.deleteOnExit() }
        val logger = SslKeyLogger(tmp)

        logger.listener.onHandshakeKeysReady(
            cipherSuite = 0x1301,
            clientSecret = ByteArray(32) { 1 },
            serverSecret = ByteArray(32) { 2 },
        )
        logger.flush(ByteArray(32))
        val firstLen = tmp.length()

        logger.flush(ByteArray(32))
        assertEquals(firstLen, tmp.length(), "second flush must not append")
    }

    @Test
    fun `toHex round-trips lowercase`() {
        val bytes = byteArrayOf(0x00, 0x0f, 0x10.toByte(), 0xff.toByte())
        assertEquals("000f10ff", bytes.toHex())
        assertTrue(bytes.toHex().all { it.isDigit() || it in 'a'..'f' })
    }
}
