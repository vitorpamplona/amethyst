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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9000 §10.3 stateless-reset detection.
 *
 * A peer that lost connection state (crash, restart, route change)
 * signals so by sending a "Stateless Reset" datagram — a short-header-
 * shaped packet whose trailing 16 bytes equal a `stateless_reset_token`
 * the peer previously communicated. The receiver MUST detect this and
 * silently close (no CONNECTION_CLOSE; the peer doesn't have keys to
 * decrypt it anyway) instead of mistaking it for a forgery and
 * counting it toward the §6.6 integrity limit.
 *
 * Pre-fix the tokens were stored (via NEW_CONNECTION_ID into the
 * [PathValidator] pool, and via the peer's transport parameters) but
 * never matched against arriving datagrams — a peer's stateless reset
 * looked indistinguishable from noise and the connection lingered
 * until idle timeout (or worse, an attacker could spam look-like-
 * noise frames toward our integrity counter).
 */
class StatelessResetDetectionTest {
    @Test
    fun datagram_ending_in_peer_token_triggers_silent_close() {
        val (client, _) = newConnectedClient()
        // Set up a known token on the connection. In production this
        // arrives via either `peerTransportParameters.statelessResetToken`
        // (advertised by the server in EncryptedExtensions) or a
        // NEW_CONNECTION_ID frame; the test harness installs it
        // directly so we control the bytes.
        val token = ByteArray(16) { (0x42 + it).toByte() }
        client.peerTransportParameters =
            (
                client.peerTransportParameters ?: TransportParameters()
            ).copy(statelessResetToken = token)

        // Build a datagram that LOOKS like a short-header packet but
        // whose AEAD will fail (random ciphertext). The trailing 16
        // bytes match the token — that's the §10.3 signal.
        val dcid = client.sourceConnectionId.bytes
        // First byte: form-bit=0, fixed-bit=1, pnLen=1.
        val first = byteArrayOf(0x40.toByte())
        val pn = byteArrayOf(0x00)
        val randomBody = ByteArray(40) { (it * 7).toByte() }
        val datagram = first + dcid + pn + randomBody + token
        feedDatagram(client, datagram, nowMillis = 0L)

        assertEquals(QuicConnection.Status.CLOSED, client.status)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("stateless reset"), "expected stateless-reset reason, got: $reason")
    }

    @Test
    fun datagram_ending_in_unknown_token_does_not_close() {
        val (client, _) = newConnectedClient()
        // Don't install the token at all — but feed a datagram whose
        // trailing 16 bytes happen to be some random value.
        val unknown = ByteArray(16) { 0xAA.toByte() }
        val dcid = client.sourceConnectionId.bytes
        val first = byteArrayOf(0x40.toByte())
        val pn = byteArrayOf(0x00)
        val randomBody = ByteArray(40) { (it * 11).toByte() }
        val datagram = first + dcid + pn + randomBody + unknown
        feedDatagram(client, datagram, nowMillis = 0L)
        // Connection should still be CONNECTED (or at most have its
        // integrity counter bumped); definitely NOT closed-on-stateless-
        // reset.
        if (client.status == QuicConnection.Status.CLOSED) {
            assertFalse(
                client.closeReason!!.contains("stateless reset"),
                "non-matching token must not be misclassified as stateless reset",
            )
        }
    }

    @Test
    fun isStatelessReset_constant_time_check_works_against_pool_tokens() {
        val (client, _) = newConnectedClient()
        // Install a token on a NEW_CONNECTION_ID-shaped pool entry.
        val token = ByteArray(16) { (0x55 + it).toByte() }
        client.pathValidator.recordPeerNewConnectionId(
            sequenceNumber = 1L,
            retirePriorTo = 0L,
            connectionId = ByteArray(8) { (0xAB + it).toByte() },
            statelessResetToken = token,
        )

        val dcid = client.sourceConnectionId.bytes
        val datagram = byteArrayOf(0x40.toByte()) + dcid + ByteArray(40) + token
        assertTrue(client.isStatelessReset(datagram), "pool-stored token must match")

        val mismatch = byteArrayOf(0x40.toByte()) + dcid + ByteArray(40) + ByteArray(16)
        assertFalse(client.isStatelessReset(mismatch), "all-zeros trailer must not match a real token")
    }

    @Test
    fun isStatelessReset_rejects_long_header_form() {
        val (client, _) = newConnectedClient()
        val token = ByteArray(16) { 0x33 }
        client.peerTransportParameters =
            (
                client.peerTransportParameters ?: TransportParameters()
            ).copy(statelessResetToken = token)
        // First byte 0x80+ → long header form; spec says stateless
        // reset is short-header-shaped only.
        val datagram = byteArrayOf(0xC0.toByte()) + ByteArray(40) + token
        assertFalse(client.isStatelessReset(datagram), "long-header-form must not be classified as stateless reset")
    }

    @Test
    fun isStatelessReset_rejects_too_short_datagram() {
        val (client, _) = newConnectedClient()
        val token = ByteArray(16) { 0x77 }
        client.peerTransportParameters =
            (
                client.peerTransportParameters ?: TransportParameters()
            ).copy(statelessResetToken = token)
        // Datagram shorter than the §10.3 minimum (we use 22 bytes:
        // 5-byte minimum header + 16-byte trailer + 1).
        val tooShort = byteArrayOf(0x40.toByte()) + token
        assertFalse(client.isStatelessReset(tooShort))
    }
}
