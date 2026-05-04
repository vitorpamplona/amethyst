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
package com.vitorpamplona.quic.tls

/**
 * Running SHA-256 over the concatenated handshake messages, per RFC 8446 §4.4.1.
 *
 * The transcript order is:
 *   ClientHello
 *   ServerHello
 *   EncryptedExtensions
 *   Certificate
 *   CertificateVerify
 *   server Finished
 *   client Finished
 *
 * Each message is appended with its 4-byte handshake header included.
 *
 * Backed by an incremental [TlsRunningSha256] (JCA `MessageDigest` on JVM).
 * Each [snapshot] clones the running state and finalises the clone, so
 * subsequent [append] calls keep extending the same hash. Earlier versions
 * accumulated raw bytes and re-hashed on every snapshot — O(n²) across the
 * three+ snapshots a TLS 1.3 handshake takes.
 */
class TlsTranscriptHash {
    private val running = TlsRunningSha256()

    fun append(messageBytes: ByteArray) {
        running.update(messageBytes)
    }

    /** Snapshot the current transcript hash (32 bytes). */
    fun snapshot(): ByteArray = running.snapshot()
}
