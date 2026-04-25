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
import com.vitorpamplona.quic.crypto.AesEcbHeaderProtection
import com.vitorpamplona.quic.crypto.HeaderProtection
import com.vitorpamplona.quic.crypto.PlatformAesOneBlock
import com.vitorpamplona.quic.tls.TlsConstants
import com.vitorpamplona.quic.tls.deriveQuicKeys

/**
 * Build [PacketProtection] for one direction at one encryption level given a
 * TLS traffic secret + TLS cipher-suite identifier. The QUIC labels in
 * RFC 9001 §5.1 (`quic key`, `quic iv`, `quic hp`) drive the expansion.
 *
 * For Phase B–K we only support TLS_AES_128_GCM_SHA256 (16/12/16) and
 * TLS_CHACHA20_POLY1305_SHA256 — the SHA-256 suites; nests speaks both.
 */
fun packetProtectionFromSecret(
    cipherSuite: Int,
    secret: ByteArray,
): PacketProtection {
    val (aead, keyLen, ivLen, hpLen, hp) =
        when (cipherSuite) {
            TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256 -> {
                ProtectionParams(
                    aead = Aes128Gcm,
                    keyLen = 16,
                    ivLen = 12,
                    hpLen = 16,
                    hp = AesEcbHeaderProtection(PlatformAesOneBlock),
                )
            }

            TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256 -> {
                ProtectionParams(
                    aead = com.vitorpamplona.quic.crypto.ChaCha20Poly1305Aead,
                    keyLen = 32,
                    ivLen = 12,
                    hpLen = 32,
                    hp =
                        com.vitorpamplona.quic.crypto
                            .ChaCha20HeaderProtection(com.vitorpamplona.quic.crypto.PlatformChaCha20Block),
                )
            }

            else -> {
                error("unsupported cipher suite 0x${cipherSuite.toString(16)}")
            }
        }
    val keys = deriveQuicKeys(secret, keyLen, ivLen, hpLen)
    return PacketProtection(aead, keys.key, keys.iv, hp, keys.hp)
}

private data class ProtectionParams(
    val aead: Aead,
    val keyLen: Int,
    val ivLen: Int,
    val hpLen: Int,
    val hp: HeaderProtection,
)
