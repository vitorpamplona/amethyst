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

/** Platform-provided implementation of one-block AES-ECB encryption (no padding). */
expect val PlatformAesOneBlock: AesOneBlockEncrypt

/** Platform-provided ChaCha20 keystream block encryptor (RFC 8439 IETF variant). */
expect val PlatformChaCha20Block: ChaCha20BlockEncrypt

/**
 * Build the platform's preferred AES-128-GCM AEAD for a fixed [key]. JVM
 * targets return a [JcaAesGcmAead]-style instance with the JCA Cipher and
 * SecretKeySpec cached so per-packet seal/open avoids the costly
 * `Cipher.getInstance` lookup. Other targets may return [Aes128Gcm] (the
 * stateless singleton) — correct, just not the fast path.
 */
expect fun bestAes128GcmAead(key: ByteArray): Aead
