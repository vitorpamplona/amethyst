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

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Core
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * One-block AES-ECB encryption via JCA. Used only by QUIC header protection
 * (one block per packet, so no need for a more elaborate API).
 */
actual val PlatformAesOneBlock: AesOneBlockEncrypt =
    AesOneBlockEncrypt { key, block ->
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.doFinal(block)
    }

/**
 * ChaCha20 block encryption (RFC 8439 IETF variant) for header protection.
 * Reuses Quartz's pure-Kotlin ChaCha20Core.chaCha20Xor.
 */
actual val PlatformChaCha20Block: ChaCha20BlockEncrypt =
    ChaCha20BlockEncrypt { key, nonce, counter, plaintext ->
        ChaCha20Core.chaCha20Xor(plaintext, key, nonce, counter)
    }

actual fun bestAes128GcmAead(key: ByteArray): Aead = JcaAesGcmAead(key)
