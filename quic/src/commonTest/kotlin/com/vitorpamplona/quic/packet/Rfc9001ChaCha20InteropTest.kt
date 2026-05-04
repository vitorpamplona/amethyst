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

import com.vitorpamplona.quic.crypto.ChaCha20HeaderProtection
import com.vitorpamplona.quic.crypto.ChaCha20Poly1305Aead
import com.vitorpamplona.quic.crypto.PlatformChaCha20Block
import com.vitorpamplona.quic.crypto.aeadNonce
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * RFC 9001 §A.5 — ChaCha20-Poly1305 short-header packet decrypt vector.
 *
 * Inputs (hex):
 *   secret = 9ac312a7f877468ebe69422748ad00a15443f18203a07d6060f688f30f21632b
 *   key    = c6d98ff3441c3fe1b2182094f69caa2ed4b716b65488960a7a984979fb23e1c8
 *   iv     = e0459b3474bdd0e44a41c144
 *   hp_key = 25a282b9e82f06f21f488917a4fc8f1b73573685608597d0efcb076b0ab7a7a4
 *
 *   protected packet = 4cfe4189655e5cd55c41f69080575d7999c25a5bfb
 *   packet number    = 654360564 (encoded in 3 bytes)
 *   plaintext        = 01 (a single PING frame)
 *
 * The DCID length on the wire is 0 (the example uses an implicit zero-length
 * connection id). We reproduce the exact wire decode: HP unmask + AEAD open
 * → expected plaintext bytes.
 */
class Rfc9001ChaCha20InteropTest {
    @Test
    fun rfc9001_a5_chacha20_short_header_decrypt() {
        val key = "c6d98ff3441c3fe1b2182094f69caa2ed4b716b65488960a7a984979fb23e1c8".hexToByteArray()
        val iv = "e0459b3474bdd0e44a41c144".hexToByteArray()
        val hpKey = "25a282b9e82f06f21f488917a4fc8f1b73573685608597d0efcb076b0ab7a7a4".hexToByteArray()
        val protectedPkt = "4cfe4189655e5cd55c41f69080575d7999c25a5bfb".hexToByteArray()

        val parsed =
            ShortHeaderPacket.parseAndDecrypt(
                bytes = protectedPkt,
                offset = 0,
                dcidLen = 0,
                aead = ChaCha20Poly1305Aead,
                key = key,
                iv = iv,
                hp = ChaCha20HeaderProtection(PlatformChaCha20Block),
                hpKey = hpKey,
                largestReceivedInSpace = 654_360_563L,
            )
        check(parsed != null) { "RFC 9001 §A.5 packet failed to decrypt" }
        // Packet number 654_360_564 = 0x2700_03B4
        assertContentEquals(byteArrayOf(0x01), parsed.packet.payload)
    }

    /**
     * Verifies the [aeadNonce] XOR direction against the RFC §A.5 packet number
     * 0x2700_BFF4 (= 654_360_564 decimal).
     *
     *   iv = e0459b3474bdd0e44a41c144
     *   pn padded big-endian to 12 bytes = 000000000000000027000bff4
     *   …but only the low 8 bytes of the iv participate in the XOR.
     *   Result: e0459b3474bdd0e46d417eb0
     */
    @Test
    fun rfc9001_a5_chacha20_nonce_derivation() {
        val iv = "e0459b3474bdd0e44a41c144".hexToByteArray()
        val pn = 654_360_564L
        val nonce = aeadNonce(iv, pn)
        assertContentEquals("e0459b3474bdd0e46d417eb0".hexToByteArray(), nonce)
    }
}
