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

import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.QuicVersion
import com.vitorpamplona.quic.packet.RetryPacket
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retry packet handling end-to-end through [QuicConnection], per RFC 9000
 * §17.2.5 (semantics) + RFC 9001 §5.8 (integrity tag).
 *
 * Synthesizes valid-and-invalid Retry packets, feeds them through
 * [feedDatagram], and asserts the resulting connection state:
 *
 *   1. Happy path: DCID swaps, retryToken stored, Initial PN reset to 0,
 *      next outbound Initial carries the token in its header, contains the
 *      ClientHello CRYPTO, and the datagram is padded to ≥ 1200 bytes.
 *   2. Bad-tag path: corrupting the integrity tag must be silently dropped;
 *      no state advances.
 *   3. Second-Retry path: a second valid Retry after a first one is dropped
 *      (RFC 9000 §17.2.5.2 — at most one Retry per connection).
 */
class RetryHandlingTest {
    private fun newClient(): QuicConnection =
        QuicConnection(
            serverName = "example.test",
            config = QuicConnectionConfig(),
            tlsCertificateValidator = PermissiveCertificateValidator(),
        )

    /**
     * Build the on-wire bytes of a valid Retry packet for [client], with the
     * given [retryScid] and [retryToken]. Computes the integrity tag using
     * the client's [QuicConnection.originalDestinationConnectionId] so the
     * client's [RetryPacket.verifyIntegrityTag] check passes.
     *
     * The Retry packet's DCID is the client's source CID (servers echo it
     * even though it's unused — RFC 9000 §17.2.5.1). The high 4 bits of
     * the first byte are 1100 (long header + RETRY type); the low 4 bits
     * are unused — we set them to 0.
     */
    private fun buildRetry(
        client: QuicConnection,
        retryScid: ConnectionId,
        retryToken: ByteArray,
    ): ByteArray {
        val w = QuicWriter()
        // Header form (1) | fixed bit (1) | long packet type RETRY (11) | unused (0000)
        w.writeByte(0xC0 or (LongHeaderType.RETRY.code shl 4))
        w.writeUint32(QuicVersion.V1)
        w.writeByte(client.sourceConnectionId.length)
        w.writeBytes(client.sourceConnectionId.bytes)
        w.writeByte(retryScid.length)
        w.writeBytes(retryScid.bytes)
        w.writeBytes(retryToken)
        val withoutTag = w.toByteArray()
        val tag =
            RetryPacket.computeIntegrityTag(
                retryPacketWithoutTag = withoutTag,
                originalDestinationConnectionId = client.originalDestinationConnectionId.bytes,
            )
        return withoutTag + tag
    }

    /**
     * Pull the Initial packet's Token field out of an on-wire datagram so
     * we can assert on it. [LongHeaderPacket.parseAndDecrypt] decrypts the
     * payload but doesn't surface the unprotected Token; we re-walk the
     * header here to extract it without crypto.
     */
    private fun extractInitialToken(datagram: ByteArray): ByteArray {
        val r = QuicReader(datagram, 0)
        val first = r.readByte()
        require((first and 0x80) != 0) { "expected long header" }
        val type = (first ushr 4) and 0x03
        require(type == LongHeaderType.INITIAL.code) { "expected INITIAL, got type=$type" }
        r.readUint32() // version
        val dcidLen = r.readByte()
        r.readBytes(dcidLen)
        val scidLen = r.readByte()
        r.readBytes(scidLen)
        val tokenLen = r.readVarint().toInt()
        return r.readBytes(tokenLen)
    }

    @Test
    fun valid_retry_swaps_dcid_resets_pn_and_threads_token_into_next_initial() {
        val client = newClient()
        val originalDcid = client.originalDestinationConnectionId.bytes.copyOf()
        client.start()

        // Drain the initial datagram (carries ClientHello at PN=0 with empty
        // token field) so we can assert the pre-Retry state.
        val firstDatagram = drainOutbound(client, nowMillis = 0L)
        assertNotNull(firstDatagram, "client.start() should produce an Initial datagram")
        assertEquals(0, extractInitialToken(firstDatagram).size, "pre-Retry Initial must have empty token")

        // Server picks a fresh source connection id and a token of its choice.
        val retryScid = ConnectionId(byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x78))
        val retryToken = "server-issued-retry-token".encodeToByteArray()
        val retryDatagram = buildRetry(client, retryScid, retryToken)

        feedDatagram(client, retryDatagram, nowMillis = 1L)

        // DCID is now the Retry's SCID, originalDcid is unchanged.
        assertContentEquals(retryScid.bytes, client.destinationConnectionId.bytes)
        assertContentEquals(originalDcid, client.originalDestinationConnectionId.bytes)
        assertNotEquals(originalDcid.toList(), retryScid.bytes.toList())

        // Retry token captured.
        assertContentEquals(retryToken, client.retryToken)
        assertTrue(client.retryConsumed)

        // Initial PN space reset — next allocation is 0 again.
        assertEquals(0L, client.initial.pnSpace.nextPacketNumber)
        assertEquals(-1L, client.initial.pnSpace.largestReceived)

        // Next drain produces the retried Initial: token in header, ClientHello
        // CRYPTO inside, datagram padded to ≥ 1200 (RFC 9000 §14.1).
        val secondDatagram = drainOutbound(client, nowMillis = 2L)
        assertNotNull(secondDatagram, "post-Retry drain must produce another Initial")
        assertContentEquals(retryToken, extractInitialToken(secondDatagram))
        assertTrue(
            secondDatagram.size >= 1200,
            "retried Initial datagram must be padded to >= 1200 bytes (was ${secondDatagram.size})",
        )

        // The Initial is encrypted under the new keys derived from the retryScid
        // DCID. Decrypt + verify it carries CRYPTO with the captured ClientHello
        // bytes (== the prefix of the original ClientHello — drained ALL the
        // bytes from cryptoSend on Retry replay).
        val newSecrets =
            com.vitorpamplona.quic.crypto.InitialSecrets
                .derive(retryScid.bytes)
        val proto = client.initial.sendProtection!!
        val parsed =
            LongHeaderPacket.parseAndDecrypt(
                bytes = secondDatagram,
                offset = 0,
                aead = proto.aead,
                key = newSecrets.clientKey,
                iv = newSecrets.clientIv,
                hp =
                    com.vitorpamplona.quic.crypto.AesEcbHeaderProtection(
                        com.vitorpamplona.quic.crypto.PlatformAesOneBlock,
                    ),
                hpKey = newSecrets.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed, "retried Initial must decrypt under keys derived from new DCID")
        assertEquals(0L, parsed.packet.packetNumber, "retried Initial PN must be 0 (RFC 9000 §17.2.5.2)")
        // Decoded payload starts with at least one CRYPTO frame (frame type 0x06).
        val frames =
            com.vitorpamplona.quic.frame
                .decodeFrames(parsed.packet.payload)
        val cryptoFrames = frames.filterIsInstance<com.vitorpamplona.quic.frame.CryptoFrame>()
        assertTrue(cryptoFrames.isNotEmpty(), "retried Initial payload must contain CRYPTO frames (the ClientHello)")
        assertEquals(0L, cryptoFrames.first().offset, "CRYPTO must restart at offset 0 on the new keys")
    }

    @Test
    fun retry_with_corrupted_integrity_tag_is_silently_dropped() {
        val client = newClient()
        val originalDcid = client.destinationConnectionId.bytes.copyOf()
        client.start()
        // Drain pre-Retry datagram so the test mirrors a realistic ordering.
        drainOutbound(client, nowMillis = 0L)

        val retryScid = ConnectionId(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()))
        val good = buildRetry(client, retryScid, "tk".encodeToByteArray())
        // Flip a bit in the last byte — the integrity tag.
        val corrupted = good.copyOf()
        corrupted[corrupted.size - 1] = (corrupted[corrupted.size - 1].toInt() xor 0x01).toByte()

        feedDatagram(client, corrupted, nowMillis = 1L)

        // No state advanced.
        assertNull(client.retryToken)
        assertFalse(client.retryConsumed)
        assertContentEquals(originalDcid, client.destinationConnectionId.bytes)
    }

    @Test
    fun second_valid_retry_after_one_is_consumed_is_dropped() {
        val client = newClient()
        client.start()
        drainOutbound(client, nowMillis = 0L)

        val firstScid = ConnectionId(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
        val firstToken = "first".encodeToByteArray()
        feedDatagram(client, buildRetry(client, firstScid, firstToken), nowMillis = 1L)

        // Sanity: first applied.
        assertTrue(client.retryConsumed)
        assertContentEquals(firstToken, client.retryToken)
        assertContentEquals(firstScid.bytes, client.destinationConnectionId.bytes)

        // Build a second VALID Retry. The integrity tag is computed against
        // [originalDestinationConnectionId] (still the very first random one,
        // unchanged), so this packet's tag genuinely verifies.
        val secondScid = ConnectionId(byteArrayOf(0x99.toByte(), 0x88.toByte(), 0x77.toByte(), 0x66.toByte()))
        val secondToken = "second-should-be-ignored".encodeToByteArray()
        val secondRetry = buildRetry(client, secondScid, secondToken)

        // Confirm the integrity tag really would verify in isolation —
        // otherwise this test would conflate "bad tag" with "second retry".
        val parsedSecond = RetryPacket.parse(secondRetry)
        assertNotNull(parsedSecond)
        assertTrue(
            parsedSecond.verifyIntegrityTag(secondRetry, client.originalDestinationConnectionId.bytes),
            "second retry's tag must be valid in isolation; otherwise this test is meaningless",
        )

        feedDatagram(client, secondRetry, nowMillis = 2L)

        // State unchanged from after the first retry.
        assertContentEquals(firstToken, client.retryToken)
        assertContentEquals(firstScid.bytes, client.destinationConnectionId.bytes)
    }
}
