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

import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for two RFC 9000 violations found via the
 * `quic-interop-runner` against aioquic on 2026-05-06:
 *
 *   - §10.2.3 — `CONNECTION_CLOSE (Application)` (0x1d) MUST NOT appear in
 *     Initial / Handshake packets. Application-error closes that fire before
 *     1-RTT keys exist must be encoded as `CONNECTION_CLOSE (Transport)`
 *     (0x1c) with `errorCode = APPLICATION_ERROR (0x0c)`.
 *   - §14.1 — any client datagram containing an Initial MUST be ≥ 1200
 *     bytes, even when carrying only a `CONNECTION_CLOSE`.
 *
 * Pre-fix, the writer's CLOSING branch built a tiny ~45-byte Initial with
 * frame type 0x1d. The runner's aioquic server silently dropped it (correct
 * per spec) and our handshake hung until our 10s timeout.
 */
class CloseDatagramRfcComplianceTest {
    @Test
    fun `pre-handshake close datagram is padded to at least 1200 bytes per RFC 9000 sec 14_1`() =
        runBlocking {
            val conn =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                )
            // Initial sendProtection is wired in QuicConnection's init block;
            // no handshake required to exercise the close path.
            conn.status = QuicConnection.Status.CLOSING

            val datagram = drainOutbound(conn, nowMillis = 0L)
            requireNotNull(datagram) { "drainOutbound must produce a close datagram when CLOSING with Initial keys" }

            assertTrue(
                datagram.size >= 1200,
                "client Initial datagram MUST be ≥ 1200 bytes per RFC 9000 §14.1, " +
                    "got ${datagram.size} (this was bug A from the aioquic interop run)",
            )
            // First byte: 1100????b — long-header form + Initial type.
            val firstByte = datagram[0].toInt() and 0xff
            assertEquals(0xc0, firstByte and 0xf0, "must be a long-header packet")
            assertEquals(0x00, firstByte and 0x30, "must be type=Initial (00)")
        }

    @Test
    fun `ConnectionCloseFrame encodes type 0x1c when frameType is non-null (transport close)`() {
        // Bug B fix relies on the writer passing frameType=0L (instead of
        // null) when emitting a close at Initial / Handshake level. Encode
        // path must produce 0x1c for that case, 0x1d only for app-level.
        val transportClose = ConnectionCloseFrame(errorCode = 0x0c, frameType = 0L, reason = "")
        val w1 = QuicWriter()
        transportClose.encode(w1)
        val transportBytes = w1.toByteArray()
        assertEquals(0x1c.toByte(), transportBytes[0], "transport CONNECTION_CLOSE must serialize as 0x1c")

        val appClose = ConnectionCloseFrame(errorCode = 0, frameType = null, reason = "")
        val w2 = QuicWriter()
        appClose.encode(w2)
        val appBytes = w2.toByteArray()
        assertEquals(0x1d.toByte(), appBytes[0], "application CONNECTION_CLOSE must serialize as 0x1d")
    }
}
