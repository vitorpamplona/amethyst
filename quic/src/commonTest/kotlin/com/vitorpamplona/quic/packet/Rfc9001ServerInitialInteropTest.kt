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

import com.vitorpamplona.quic.crypto.Aes128Gcm
import com.vitorpamplona.quic.crypto.AesEcbHeaderProtection
import com.vitorpamplona.quic.crypto.InitialSecrets
import com.vitorpamplona.quic.crypto.PlatformAesOneBlock
import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.decodeFrames
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9001 Appendix A.3 — Server Initial response decrypt vector.
 *
 * The 135-byte protected server Initial decrypts bit-for-bit to a 99-byte
 * payload containing one ACK frame (acking the client's Initial packet
 * number 0) and one CRYPTO frame carrying the canonical ServerHello.
 *
 *   Original DCID = 8394c8f03e515708 (still the client's DCID; both sides
 *   derive Initial secrets from this same value)
 *   Server SCID   = f067a5502a4262b5
 *   Packet number = 1, encoded in 2 bytes
 */
class Rfc9001ServerInitialInteropTest {
    @Test
    fun rfc9001_a3_full_server_initial_decrypts_bit_for_bit() {
        val originalDcid = "8394c8f03e515708".hexToByteArray()
        val proto = InitialSecrets.derive(originalDcid)
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

        val protectedPacket = rfc9001A3Protected.hexToByteArray()
        assertEquals(135, protectedPacket.size, "RFC 9001 §A.3 packet must be exactly 135 bytes")

        val parsed =
            LongHeaderPacket.parseAndDecrypt(
                bytes = protectedPacket,
                offset = 0,
                aead = Aes128Gcm,
                key = proto.serverKey,
                iv = proto.serverIv,
                hp = hp,
                hpKey = proto.serverHp,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed, "RFC 9001 §A.3 server Initial must decrypt with canonical server_initial keys")

        // Header fields per RFC 9001 §A.3.
        assertEquals(LongHeaderType.INITIAL, parsed.packet.type)
        assertEquals(0x00000001, parsed.packet.version)
        assertEquals(1L, parsed.packet.packetNumber)
        assertEquals(0, parsed.packet.dcid.length, "server Initial echoes the client's zero-length SCID as DCID")
        assertEquals("f067a5502a4262b5", parsed.packet.scid.toHex())
        assertEquals(0, parsed.packet.token.size, "server Initial token is empty")

        // Plaintext payload size = length(0x75=117) - pnLen(2) - tag(16) = 99.
        assertEquals(99, parsed.packet.payload.size, "plaintext payload must be 99 bytes")

        val expectedPayload = rfc9001A3UnprotectedPayload.hexToByteArray()
        assertContentEquals(expectedPayload, parsed.packet.payload, "plaintext must match RFC §A.3 published bytes")

        // Frame decode: ACK frame for client packet 0, then CRYPTO frame at offset 0 carrying ServerHello.
        val frames = decodeFrames(parsed.packet.payload)
        assertEquals(2, frames.size, "expected exactly two frames (ACK, CRYPTO)")

        val ack = frames[0]
        assertTrue(ack is AckFrame, "first frame must be ACK (got ${ack::class.simpleName})")
        assertEquals(0L, ack.largestAcknowledged, "ACK must acknowledge client packet 0")

        val crypto = frames[1]
        assertTrue(crypto is CryptoFrame, "second frame must be CRYPTO (got ${crypto::class.simpleName})")
        assertEquals(0L, crypto.offset, "CRYPTO offset must be 0")
        assertEquals(0x02.toByte(), crypto.data[0], "CRYPTO body must start with TLS ServerHello (0x02)")

        assertEquals(135, parsed.consumed, "consumed byte count must equal datagram size")
    }

    /** RFC 9001 §A.3 unprotected server Initial payload — 99 bytes. */
    private val rfc9001A3UnprotectedPayload: String =
        "02000000000600405a020000560303eefce7f7b37ba1d1632e96677825ddf739" +
            "88cfc79825df566dc5430b9a045a1200130100002e00330024001d00209d3c940d" +
            "89690b84d08a60993c144eca684d1081287c834d5311bcf32bb9da1a002b00020304"

    /** RFC 9001 §A.3 fully-protected server Initial datagram — exactly 135 bytes. */
    private val rfc9001A3Protected: String =
        "cf000000010008f067a5502a4262b5004075c0d95a482cd0991cd25b0aac406a" +
            "5816b6394100f37a1c69797554780bb38cc5a99f5ede4cf73c3ec2493a1839b3db" +
            "cba3f6ea46c5b7684df3548e7ddeb9c3bf9c73cc3f3bded74b562bfb19fb84022f" +
            "8ef4cdd93795d77d06edbb7aaf2f58891850abbdca3d20398c276456cbc4215840" +
            "7dd074ee"
}
