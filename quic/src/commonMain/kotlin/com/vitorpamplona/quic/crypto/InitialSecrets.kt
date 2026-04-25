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
package com.vitorpamplona.quic.crypto

/**
 * Initial-packet protection secrets per RFC 9001 §5.2.
 *
 * The Initial salt for QUIC v1 is fixed:
 *   `38762cf7f55934b34d179ae6a4c80cadccbb7f0a` (20 bytes)
 *
 * The initial secret is `HKDF-Extract(salt, client_dst_connection_id)`.
 * Client and server then derive their per-direction secret with
 * `HKDF-Expand-Label(initial_secret, "client in"/"server in", "", 32)`.
 *
 * From those, key/iv/hp are derived via `HKDF-Expand-Label`.
 *
 * Initial packets always use the AES-128-GCM AEAD with AES-128 header
 * protection — those parameters are fixed for the QUIC v1 long-header
 * protection epoch.
 */
object InitialSecrets {
    val V1_INITIAL_SALT: ByteArray =
        byteArrayOf(
            0x38.toByte(),
            0x76.toByte(),
            0x2c.toByte(),
            0xf7.toByte(),
            0xf5.toByte(),
            0x59.toByte(),
            0x34.toByte(),
            0xb3.toByte(),
            0x4d.toByte(),
            0x17.toByte(),
            0x9a.toByte(),
            0xe6.toByte(),
            0xa4.toByte(),
            0xc8.toByte(),
            0x0c.toByte(),
            0xad.toByte(),
            0xcc.toByte(),
            0xbb.toByte(),
            0x7f.toByte(),
            0x0a.toByte(),
        )

    /**
     * Derive both directions' Initial protection material from the original
     * destination connection id (the random CID the client put in its first
     * Initial).
     */
    fun derive(clientDstConnectionId: ByteArray): InitialProtection {
        val initialSecret = HKDF.extract(clientDstConnectionId, V1_INITIAL_SALT)
        val clientSecret = HKDF.expandLabel(initialSecret, "client in", ByteArray(0), 32)
        val serverSecret = HKDF.expandLabel(initialSecret, "server in", ByteArray(0), 32)
        return InitialProtection(
            clientKey = HKDF.expandLabel(clientSecret, "quic key", ByteArray(0), 16),
            clientIv = HKDF.expandLabel(clientSecret, "quic iv", ByteArray(0), 12),
            clientHp = HKDF.expandLabel(clientSecret, "quic hp", ByteArray(0), 16),
            serverKey = HKDF.expandLabel(serverSecret, "quic key", ByteArray(0), 16),
            serverIv = HKDF.expandLabel(serverSecret, "quic iv", ByteArray(0), 12),
            serverHp = HKDF.expandLabel(serverSecret, "quic hp", ByteArray(0), 16),
        )
    }
}

class InitialProtection(
    val clientKey: ByteArray,
    val clientIv: ByteArray,
    val clientHp: ByteArray,
    val serverKey: ByteArray,
    val serverIv: ByteArray,
    val serverHp: ByteArray,
)
