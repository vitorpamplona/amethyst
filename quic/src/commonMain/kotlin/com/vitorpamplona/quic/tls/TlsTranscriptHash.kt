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

import com.vitorpamplona.quartz.utils.sha256.sha256

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
 * For Phase B we keep this simple: we accumulate raw bytes and re-hash. The
 * volume is small (a few KB per handshake), so SHA-256 throughput isn't a
 * bottleneck. A streaming hash would be a nice optimisation later.
 */
class TlsTranscriptHash {
    private val buffer = ArrayList<ByteArray>()

    fun append(messageBytes: ByteArray) {
        buffer += messageBytes
    }

    /** Snapshot the current transcript hash (32 bytes). */
    fun snapshot(): ByteArray {
        var totalLen = 0
        for (b in buffer) totalLen += b.size
        val concat = ByteArray(totalLen)
        var pos = 0
        for (b in buffer) {
            b.copyInto(concat, pos)
            pos += b.size
        }
        return sha256(concat)
    }
}
