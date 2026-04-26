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

import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * The single HKDF-SHA256 instance used everywhere in the QUIC + TLS 1.3 stack.
 * SHA-256 covers both QUIC-mandatory cipher suites we care about
 * (TLS_AES_128_GCM_SHA256 and TLS_CHACHA20_POLY1305_SHA256).
 *
 * TLS_AES_256_GCM_SHA384 is the only mandatory suite we omit — its SHA-384
 * primitive isn't yet in Quartz and nests / mainstream HTTP/3 servers all
 * accept the SHA-256 suites by default.
 */
val HKDF: Hkdf = Hkdf("HmacSHA256", 32)

/** Empty-string SHA-256 — RFC 8446's "transcript hash of nothing" sentinel. */
val EMPTY_SHA256: ByteArray = sha256(ByteArray(0))

/** RFC 8446 §7.1 — Derive-Secret(secret, label, transcript_hash). */
fun deriveSecret(
    secret: ByteArray,
    label: String,
    transcriptHash: ByteArray,
): ByteArray = HKDF.expandLabel(secret, label, transcriptHash, 32)

/** RFC 8446 §7.1 — `HKDF-Expand-Label(secret, label, "" , length)`. */
fun expandLabel(
    secret: ByteArray,
    label: String,
    length: Int,
): ByteArray = HKDF.expandLabel(secret, label, ByteArray(0), length)
