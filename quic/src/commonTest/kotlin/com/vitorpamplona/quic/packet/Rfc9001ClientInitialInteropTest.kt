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
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.decodeFrames
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9001 Appendix A.2 — Client Initial packet end-to-end decrypt vector.
 *
 * The RFC publishes the entire 1200-byte protected client Initial. We
 * exercise the full receive path: long-header parse → HP unmask → AEAD
 * open → frame decode. Successful decrypt yields a CRYPTO frame containing
 * the canonical ClientHello (245 bytes) followed by PADDING out to the
 * 1200-byte minimum-datagram requirement.
 *
 *   Original DCID (random) = 8394c8f03e515708
 *
 * The protection material is the canonical RFC 9001 §A.1 derivation,
 * already verified by InitialSecretsTest.
 */
class Rfc9001ClientInitialInteropTest {
    @Test
    fun rfc9001_a2_full_client_initial_decrypts_bit_for_bit() {
        val dcid = "8394c8f03e515708".hexToByteArray()
        val proto = InitialSecrets.derive(dcid)
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

        val protectedPacket = rfc9001A2Protected.hexToByteArray()
        assertEquals(1200, protectedPacket.size, "RFC 9001 §A.2 packet must be exactly 1200 bytes")

        val parsed =
            LongHeaderPacket.parseAndDecrypt(
                bytes = protectedPacket,
                offset = 0,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed, "RFC 9001 §A.2 client Initial must decrypt with canonical client_initial keys")

        // Header fields per RFC 9001 §A.2.
        assertEquals(LongHeaderType.INITIAL, parsed.packet.type)
        assertEquals(0x00000001, parsed.packet.version)
        assertEquals(2L, parsed.packet.packetNumber)
        assertEquals("8394c8f03e515708", parsed.packet.dcid.toHex())
        assertEquals(0, parsed.packet.scid.length, "client Initial uses zero-length SCID")
        assertEquals(0, parsed.packet.token.size, "client Initial token is empty")

        // Plaintext payload size = 1200 (datagram) - 22 (header before PN) - 4 (PN) - 16 (tag) = 1158.
        // Actually: total = pnLen(4) + plaintext + tag(16). The length field is 0x449e = 1182.
        // So plaintext = 1182 - 4 - 16 = 1162 bytes.
        assertEquals(1162, parsed.packet.payload.size, "plaintext payload must be 1162 bytes")

        // The first 245 bytes of the plaintext are the unprotected CRYPTO frame
        // contents from RFC 9001 §A.2; the remaining 917 bytes are PADDING (0x00).
        val expectedHead = rfc9001A2UnprotectedPayload.hexToByteArray()
        assertEquals(245, expectedHead.size)
        val actualHead = parsed.packet.payload.copyOfRange(0, 245)
        assertContentEquals(expectedHead, actualHead, "first 245 bytes must match RFC §A.2 unprotected payload")

        for (i in 245 until parsed.packet.payload.size) {
            assertEquals(0.toByte(), parsed.packet.payload[i], "byte $i must be PADDING (0x00)")
        }

        // Frame-decoder sanity: first frame is a CRYPTO frame at offset 0 carrying
        // the TLS ClientHello (handshake type 0x01).
        val frames = decodeFrames(parsed.packet.payload)
        val first = frames.firstOrNull()
        assertTrue(first is CryptoFrame, "first frame must be CRYPTO (got ${first?.let { it::class.simpleName }})")
        assertEquals(0L, first.offset, "CRYPTO offset must be 0")
        assertEquals(0x01.toByte(), first.data[0], "CRYPTO body must start with TLS ClientHello (0x01)")

        assertEquals(1200, parsed.consumed, "consumed byte count must equal datagram size")
    }

    /**
     * The unprotected CRYPTO frame contents (TLS ClientHello + transport
     * parameters) as published in RFC 9001 §A.2. 245 bytes.
     */
    private val rfc9001A2UnprotectedPayload: String =
        (
            "060040f1010000ed0303ebf8fa56f12939b9584a3896472ec40bb863cfd3e868" +
                "04fe3a47f06a2b69484c00000413011302010000c000000010000e00000b6578" +
                "616d706c652e636f6dff01000100000a00080006001d0017001800100007000504" +
                "616c706e000500050100000000003300260024001d00209370b2c9caa47fbabaf4" +
                "559fedba753de171fa71f50f1ce15d43e994ec74d748002b00030203040" +
                "00d0010000e0403050306030203080408050806002d00020101001c00024001003" +
                "900320408ffffffffffffffff05048000ffff07048000ffff0801100104800" +
                "075300901100f088394c8f03e51570806048000ffff"
        ).replace(" ", "").replace("\n", "")

    /**
     * The fully-protected client Initial datagram from RFC 9001 §A.2.
     * Exactly 1200 bytes (2400 hex chars).
     */
    private val rfc9001A2Protected: String =
        (
            "c000000001088394c8f03e5157080000449e7b9aec34d1b1c98dd7689fb8ec11" +
                "d242b123dc9bd8bab936b47d92ec356c0bab7df5976d27cd449f63300099f3991" +
                "c260ec4c60d17b31f8429157bb35a1282a643a8d2262cad67500cadb8e7378c8e" +
                "b7539ec4d4905fed1bee1fc8aafba17c750e2c7ace01e6005f80fcb7df621230c" +
                "83711b39343fa028cea7f7fb5ff89eac2308249a02252155e2347b63d58c5457a" +
                "fd84d05dfffdb20392844ae812154682e9cf012f9021a6f0be17ddd0c2084dce2" +
                "5ff9b06cde535d0f920a2db1bf362c23e596d11a4f5a6cf3948838a3aec4e15da" +
                "f8500a6ef69ec4e3feb6b1d98e610ac8b7ec3faf6ad760b7bad1db4ba3485e8a9" +
                "4dc250ae3fdb41ed15fb6a8e5eba0fc3dd60bc8e30c5c4287e53805db059ae064" +
                "8db2f64264ed5e39be2e20d82df566da8dd5998ccabdae053060ae6c7b4378e84" +
                "6d29f37ed7b4ea9ec5d82e7961b7f25a9323851f681d582363aa5f89937f5a672" +
                "58bf63ad6f1a0b1d96dbd4faddfcefc5266ba6611722395c906556be52afe3f56" +
                "5636ad1b17d508b73d8743eeb524be22b3dcbc2c7468d54119c7468449a13d8e3" +
                "b95811a198f3491de3e7fe942b330407abf82a4ed7c1b311663ac69890f415701" +
                "5853d91e923037c227a33cdd5ec281ca3f79c44546b9d90ca00f064c99e3dd979" +
                "11d39fe9c5d0b23a229a234cb36186c4819e8b9c5927726632291d6a418211cc2" +
                "962e20fe47feb3edf330f2c603a9d48c0fcb5699dbfe5896425c5bac4aee82e57" +
                "a85aaf4e2513e4f05796b07ba2ee47d80506f8d2c25e50fd14de71e6c41855930" +
                "2f939b0e1abd576f279c4b2e0feb85c1f28ff18f58891ffef132eef2fa09346ae" +
                "e33c28eb130ff28f5b766953334113211996d20011a198e3fc433f9f2541010ae" +
                "17c1bf202580f6047472fb36857fe843b19f5984009ddc324044e847a4f4a0ab3" +
                "4f719595de37252d6235365e9b84392b061085349d73203a4a13e96f5432ec0fd" +
                "4a1ee65accdd5e3904df54c1da510b0ff20dcc0c77fcb2c0e0eb605cb0504db87" +
                "632cf3d8b4dae6e705769d1de354270123cb11450efc60ac47683d7b8d0f81136" +
                "5565fd98c4c8eb936bcab8d069fc33bd801b03adea2e1fbc5aa463d08ca19896d" +
                "2bf59a071b851e6c239052172f296bfb5e72404790a2181014f3b94a4e97d117b" +
                "438130368cc39dbb2d198065ae3986547926cd2162f40a29f0c3c8745c0f50fba" +
                "3852e566d44575c29d39a03f0cda721984b6f440591f355e12d439ff150aab761" +
                "3499dbd49adabc8676eef023b15b65bfc5ca06948109f23f350db82123535eb8a" +
                "7433bdabcb909271a6ecbcb58b936a88cd4e8f2e6ff5800175f113253d8fa9ca8" +
                "885c2f552e657dc603f252e1a8e308f76f0be79e2fb8f5d5fbbe2e30ecadd2207" +
                "23c8c0aea8078cdfcb3868263ff8f0940054da48781893a7e49ad5aff4af300cd" +
                "804a6b6279ab3ff3afb64491c85194aab760d58a606654f9f4400e8b38591356f" +
                "bf6425aca26dc85244259ff2b19c41b9f96f3ca9ec1dde434da7d2d392b905ddf" +
                "3d1f9af93d1af5950bd493f5aa731b4056df31bd267b6b90a079831aaf579be0a" +
                "39013137aac6d404f518cfd46840647e78bfe706ca4cf5e9c5453e9f7cfd2b8b4" +
                "c8d169a44e55c88d4a9a7f9474241e221af44860018ab0856972e194cd934"
        ).replace(" ", "").replace("\n", "")
}
