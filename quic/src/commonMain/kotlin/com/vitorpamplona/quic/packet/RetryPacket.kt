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
package com.vitorpamplona.quic.packet

import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.connection.ConnectionId
import com.vitorpamplona.quic.crypto.Aes128Gcm

/**
 * Retry packet codec per RFC 9000 §17.2.5 + RFC 9001 §5.8.
 *
 * Wire layout (no header protection, no AEAD on the payload — Retry uses
 * only an integrity tag):
 *
 *   first_byte           = 1|1|11|unused(4)
 *   version              (4 bytes)
 *   dcid_len + dcid
 *   scid_len + scid
 *   retry_token          (variable; consumes everything before the 16-byte tag)
 *   retry_integrity_tag  (16 bytes, AES-128-GCM AEAD over the retry pseudo-packet)
 *
 * We don't follow Retry (a follow-on Initial with the new token), but we
 * MUST recognize Retry packets so we don't try to decrypt them as ordinary
 * Initial packets and so we can validate their integrity.
 */
data class RetryPacket(
    val version: Int,
    val dcid: ConnectionId,
    val scid: ConnectionId,
    val retryToken: ByteArray,
    val retryIntegrityTag: ByteArray,
) {
    companion object {
        /** Parse a Retry packet. Returns null if [bytes] isn't a Retry packet. */
        fun parse(bytes: ByteArray): RetryPacket? {
            if (bytes.size < 1 + 4 + 1 + 1 + 16) return null
            val first = bytes[0].toInt() and 0xFF
            if ((first and 0xC0) != 0xC0) return null // not a long header
            val typeBits = (first ushr 4) and 0x03
            if (typeBits != LongHeaderType.RETRY.code) return null

            val r = QuicReader(bytes, 1)
            val version = r.readUint32().toInt()
            val dcidLen = r.readByte()
            val dcid = ConnectionId(r.readBytes(dcidLen))
            val scidLen = r.readByte()
            val scid = ConnectionId(r.readBytes(scidLen))
            // Retry token consumes everything up to the last 16 bytes (tag).
            val tokenLen = bytes.size - r.position - 16
            if (tokenLen < 0) return null
            val token = r.readBytes(tokenLen)
            val tag = r.readBytes(16)
            return RetryPacket(version, dcid, scid, token, tag)
        }

        /**
         * Compute the canonical Retry integrity tag for [retryPacket] given
         * [originalDestinationConnectionId] (the DCID the client used in its
         * first Initial — the server is required to echo this back via the
         * tag's AAD construction).
         *
         * Per RFC 9001 §5.8:
         *   key   = 0xbe0c690b9f66575a1d766b54e368c84e
         *   nonce = 0x461599d35d632bf2239825bb
         *   AEAD  = AES-128-GCM
         *   AAD   = original_dest_connection_id_len (1) ||
         *           original_dest_connection_id      ||
         *           retry_packet_without_tag
         *
         * The tag is the AEAD ciphertext of an empty plaintext (i.e., just
         * the 16-byte authentication tag).
         */
        fun computeIntegrityTag(
            retryPacketWithoutTag: ByteArray,
            originalDestinationConnectionId: ByteArray,
        ): ByteArray {
            val aad = QuicWriter()
            aad.writeByte(originalDestinationConnectionId.size)
            aad.writeBytes(originalDestinationConnectionId)
            aad.writeBytes(retryPacketWithoutTag)
            return Aes128Gcm.seal(
                key = V1_RETRY_KEY,
                nonce = V1_RETRY_NONCE,
                aad = aad.toByteArray(),
                plaintext = ByteArray(0),
            )
        }

        /** RFC 9001 §5.8 — fixed retry-integrity AES-128-GCM key for QUIC v1. */
        val V1_RETRY_KEY: ByteArray =
            byteArrayOf(
                0xbe.toByte(),
                0x0c.toByte(),
                0x69.toByte(),
                0x0b.toByte(),
                0x9f.toByte(),
                0x66.toByte(),
                0x57.toByte(),
                0x5a.toByte(),
                0x1d.toByte(),
                0x76.toByte(),
                0x6b.toByte(),
                0x54.toByte(),
                0xe3.toByte(),
                0x68.toByte(),
                0xc8.toByte(),
                0x4e.toByte(),
            )

        /** RFC 9001 §5.8 — fixed retry-integrity AES-128-GCM nonce for QUIC v1. */
        val V1_RETRY_NONCE: ByteArray =
            byteArrayOf(
                0x46.toByte(),
                0x15.toByte(),
                0x99.toByte(),
                0xd3.toByte(),
                0x5d.toByte(),
                0x63.toByte(),
                0x2b.toByte(),
                0xf2.toByte(),
                0x23.toByte(),
                0x98.toByte(),
                0x25.toByte(),
                0xbb.toByte(),
            )
    }

    /**
     * Verify the integrity tag against the original DCID the client used.
     * Caller passes the original on-wire packet bytes so the AAD includes
     * the exact first-byte unused bits as transmitted (RFC 9001 §5.8 fixes
     * none of them).
     */
    fun verifyIntegrityTag(
        originalPacketBytes: ByteArray,
        originalDestinationConnectionId: ByteArray,
    ): Boolean {
        if (originalPacketBytes.size < 16) return false
        val withoutTag = originalPacketBytes.copyOfRange(0, originalPacketBytes.size - 16)
        val expected = computeIntegrityTag(withoutTag, originalDestinationConnectionId)
        return expected.contentEquals(retryIntegrityTag)
    }
}
