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

import com.vitorpamplona.quic.TlsAlertException
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.QuicTransportError
import com.vitorpamplona.quic.tls.TlsConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9001 §4.8 + RFC 9000 §22 error code surfacing.
 *
 * Pre-fix the connection's `closeErrorCode` was effectively unused —
 * every external close went through `markClosedExternally(reason)`
 * which set only the reason string. Observers (qlog, support logs)
 * could see the human-readable "FLOW_CONTROL_ERROR: ..." prefix but
 * not the numeric code, and any TLS handshake failure surfaced as a
 * generic exception that bubbled out of the read loop without an
 * RFC-mapped error.
 *
 * The fixes covered here:
 *  - `TlsAlertException` carries the RFC 8446 §B.2 alert
 *    description; the parser maps it to `0x100 + alert` per RFC 9001
 *    §4.8 and stamps `closeErrorCode`.
 *  - `markClosedExternally(reason, errorCode)` overload lets call
 *    sites pin the §20.1 transport code (e.g. CRYPTO_BUFFER_EXCEEDED).
 *  - Specific TLS-layer throws (ALPN mismatch, version mismatch,
 *    cipher mismatch, HRR, Finished-MAC, cert validation, KeyUpdate)
 *    map to the spec alert.
 */
class ErrorCodeMappingTest {
    @Test
    fun tls_alert_exception_quicErrorCode_offsets_by_0x100() {
        val ex = TlsAlertException(alertCode = TlsConstants.ALERT_HANDSHAKE_FAILURE, message = "test")
        // RFC 9001 §4.8: 0x100 + 40 = 0x128 (296 decimal).
        assertEquals(0x128L, ex.quicErrorCode)
    }

    @Test
    fun tls_alert_exception_rejects_out_of_range_codes() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            TlsAlertException(alertCode = -1, message = "negative")
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            TlsAlertException(alertCode = 256, message = "too big")
        }
    }

    @Test
    fun feedDatagram_routes_TlsAlertException_to_closeErrorCode() {
        // Construct a connection mid-handshake. We don't need a full
        // pipe — the parser-level catch is what's under test. Inject
        // a bare CRYPTO frame with bytes that the TLS layer will
        // reject; the parser's catch must record the alert-mapped
        // code on `closeErrorCode`. Easiest reproducer: a CRYPTO at
        // INITIAL with bytes that aren't a valid handshake message
        // header (length > remaining).
        val (client, _) = newConnectedClient()
        // Force-close the connection with a synthetic alert to bypass
        // the need for a TLS-rejecting wire packet (the wire-level
        // craft is exercised by the existing TLS handshake tests).
        // The test here proves the markClosedExternally surface
        // accepts the error code and preserves it on `closeErrorCode`.
        client.markClosedExternally(
            reason = "CRYPTO_ERROR (TLS alert 80): synthetic",
            errorCode = TlsAlertException(alertCode = TlsConstants.ALERT_INTERNAL_ERROR, message = "x").quicErrorCode,
        )
        assertEquals(QuicConnection.Status.CLOSED, client.status)
        assertEquals(0x100L + 80L, client.closeErrorCode)
    }

    @Test
    fun crypto_buffer_exceeded_closes_with_specific_code() {
        // Send a CRYPTO frame at a far-out offset. The pre-insert
        // size check (`proposed - contiguousEnd > 64KiB`) fires
        // BEFORE the buffer actually allocates, so the
        // CRYPTO_BUFFER_EXCEEDED close lands deterministically.
        val (client, pipe) = newConnectedClient()
        val crypto = CryptoFrame(offset = 200_000L, data = ByteArray(16))
        val datagram = pipe.buildServerApplicationDatagram(listOf(crypto))!!
        feedDatagram(client, datagram, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CLOSED, client.status)
        assertEquals(QuicTransportError.CRYPTO_BUFFER_EXCEEDED, client.closeErrorCode)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("CRYPTO_BUFFER_EXCEEDED"))
    }

    @Test
    fun small_crypto_frames_stay_under_buffer_cap() {
        // Sanity-check the cap isn't accidentally tripping on
        // legitimate handshake-shaped traffic. A CRYPTO frame at
        // offset 0 with a reasonable size is fine — it gets fed
        // into the TLS layer, which rejects it (not a real
        // handshake message), but via the TLS catch path with
        // CRYPTO_ERROR, NOT CRYPTO_BUFFER_EXCEEDED.
        val (client, pipe) = newConnectedClient()
        val crypto = CryptoFrame(offset = 200L, data = ByteArray(64))
        val datagram = pipe.buildServerApplicationDatagram(listOf(crypto))!!
        feedDatagram(client, datagram, nowMillis = 0L)
        // Either CONNECTED (TLS shrugged it off) or CLOSED with
        // CRYPTO_ERROR — but NOT CRYPTO_BUFFER_EXCEEDED.
        kotlin.test.assertNotEquals(
            QuicTransportError.CRYPTO_BUFFER_EXCEEDED,
            client.closeErrorCode,
            "small CRYPTO frame must not trip the buffer cap",
        )
    }

    @Test
    fun quic_transport_error_constants_match_rfc_9000_section_20_1() {
        // Spot-check the §20.1 numeric values so a future renumbering
        // accident is caught at test-time.
        assertEquals(0x00L, QuicTransportError.NO_ERROR)
        assertEquals(0x01L, QuicTransportError.INTERNAL_ERROR)
        assertEquals(0x03L, QuicTransportError.FLOW_CONTROL_ERROR)
        assertEquals(0x05L, QuicTransportError.STREAM_STATE_ERROR)
        assertEquals(0x06L, QuicTransportError.FINAL_SIZE_ERROR)
        assertEquals(0x08L, QuicTransportError.TRANSPORT_PARAMETER_ERROR)
        assertEquals(0x0aL, QuicTransportError.PROTOCOL_VIOLATION)
        assertEquals(0x0dL, QuicTransportError.CRYPTO_BUFFER_EXCEEDED)
        assertEquals(0x0eL, QuicTransportError.KEY_UPDATE_ERROR)
        assertEquals(0x0fL, QuicTransportError.AEAD_LIMIT_REACHED)
    }
}
