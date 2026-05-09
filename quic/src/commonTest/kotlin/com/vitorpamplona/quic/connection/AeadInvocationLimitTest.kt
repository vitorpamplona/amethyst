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

import com.vitorpamplona.quic.crypto.Aead
import com.vitorpamplona.quic.crypto.Aes128Gcm
import com.vitorpamplona.quic.crypto.ChaCha20Poly1305Aead
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9001 §6.6 / §B.1 AEAD invocation limit enforcement.
 *
 *   - Confidentiality limit: max number of packets encrypted with one
 *     send key. AES-128-GCM = 2^23, ChaCha20-Poly1305 = 2^62.
 *   - Integrity limit: max number of forged-packet AEAD failures on
 *     one receive key. AES-128-GCM = 2^52, ChaCha20-Poly1305 = 2^36.
 *
 * Pre-fix neither limit was tracked. A long-running session with
 * AES-128-GCM would silently roll past 2^23 encrypts (security
 * argument no longer holds); an attacker spamming forged packets
 * could indefinitely grind for AEAD key recovery.
 *
 * The counter / limit logic is verified directly via the internal
 * fields rather than spinning the writer for ~8 million encrypts —
 * that would take minutes per test.
 */
class AeadInvocationLimitTest {
    @Test
    fun aes_128_gcm_limits_match_rfc_b1() {
        val aead: Aead = Aes128Gcm
        assertEquals(1L shl 23, aead.confidentialityLimit, "AES-128-GCM confidentiality limit per RFC 9001 §B.1")
        assertEquals(1L shl 52, aead.integrityLimit, "AES-128-GCM integrity limit per RFC 9001 §B.1")
    }

    @Test
    fun chacha20_poly1305_limits_match_rfc_b1() {
        val aead: Aead = ChaCha20Poly1305Aead
        assertEquals(1L shl 62, aead.confidentialityLimit, "ChaCha20-Poly1305 confidentiality limit per RFC 9001 §B.1")
        assertEquals(1L shl 36, aead.integrityLimit, "ChaCha20-Poly1305 integrity limit per RFC 9001 §B.1")
    }

    @Test
    fun encrypt_count_increments_per_application_packet() {
        val (client, _) = newConnectedClient()
        val before = client.aeadEncryptCount
        // Open a uni stream and write to it — guaranteed to produce an
        // ack-eliciting application packet on the next drain, regardless
        // of whether the peer advertised `max_datagram_frame_size`.
        runBlocking {
            val stream = client.openUniStream()
            stream.send.enqueue(byteArrayOf(0x01, 0x02, 0x03))
            stream.send.finish()
            client.streamsLock.lock()
            try {
                drainOutbound(client, nowMillis = 0L)
            } finally {
                client.streamsLock.unlock()
            }
        }
        assertTrue(client.aeadEncryptCount > before, "encrypt count must advance on outbound build, got $before -> ${client.aeadEncryptCount}")
    }

    @Test
    fun encrypt_count_resets_on_key_update_initiation() {
        val (client, pipe) = newConnectedClient()
        runBlocking { pipe.drive(maxRounds = 4) }
        client.aeadEncryptCount = 10_000L
        val rotated = client.initiateKeyUpdate()
        // initiateKeyUpdate may legitimately fail if handshake-confirm
        // hasn't propagated yet — the counter MUST still get reset on
        // success.
        if (rotated) {
            assertEquals(0L, client.aeadEncryptCount, "counter resets on rotation")
            assertEquals(0L, client.aeadDecryptFailureCount, "decrypt-failure counter resets on rotation too")
        }
    }

    @Test
    fun encrypt_at_confidentiality_limit_closes_connection() {
        val (client, pipe) = newConnectedClient()
        runBlocking { pipe.drive(maxRounds = 4) }
        // Pin the counter so the next outbound build crosses the limit.
        // Skip the key-update soft trigger by also latching
        // [aeadKeyUpdateRequested].
        client.aeadKeyUpdateRequested = true
        val limit =
            client.application.sendProtection
                ?.aead
                ?.confidentialityLimit
        assertNotNull(limit, "client must have application-level send keys after handshake")
        client.aeadEncryptCount = limit - 1L
        runBlocking {
            // Open a uni stream + write a few bytes; the resulting
            // STREAM frame guarantees an ack-eliciting application
            // packet on the next drain. The writer's post-encrypt
            // limit check fires at the boundary.
            val stream = client.openUniStream()
            stream.send.enqueue(byteArrayOf(0xff.toByte()))
            stream.send.finish()
            client.streamsLock.lock()
            try {
                drainOutbound(client, nowMillis = 0L)
            } finally {
                client.streamsLock.unlock()
            }
        }
        assertEquals(QuicConnection.Status.CLOSED, client.status)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("AEAD_LIMIT_REACHED"))
    }

    @Test
    fun decrypt_failure_at_integrity_limit_closes_connection() {
        val (client, pipe) = newConnectedClient()
        // Craft a real, properly-encrypted 1-RTT datagram from the
        // server's perspective, then flip one ciphertext byte so AEAD
        // verification fails on the client side. This exercises the
        // exact parser path (HP unmask succeeds, AEAD returns null,
        // counter advances) — feeding garbage bytes would trip the
        // reserved-bits PROTOCOL_VIOLATION check first.
        val cleanDatagram =
            // 100 PING frames pad the packet well past the 20 bytes of
            // header-protection sample range (sample ends at
            // byte (1 + dcidLen + 4 + 16) ≈ byte 29 for dcidLen=8).
            // Tampering a byte well past that range leaves the HP
            // mask intact while breaking the AEAD tag, so the
            // ShortHeaderPacket.parseAndDecrypt path returns null
            // (AEAD failure) instead of throwing on reserved-bit
            // mismatch (HP-mask divergence).
            pipe.buildServerApplicationDatagram(List(100) { com.vitorpamplona.quic.frame.PingFrame })!!
        val tampered = cleanDatagram.copyOf()
        // Flip the last byte of the AEAD tag — far past the HP sample
        // range so HP unmask still recovers the correct first byte.
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()
        val limit =
            client.application.receiveProtection
                ?.aead
                ?.integrityLimit
        assertNotNull(limit)
        // Pin the counter just under the limit so the tampered packet's
        // AEAD failure crosses the threshold.
        client.aeadDecryptFailureCount = limit - 1L
        feedDatagram(client, tampered, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CLOSED, client.status)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("AEAD_LIMIT_REACHED"), "expected AEAD_LIMIT_REACHED, got: $reason")
    }
}
