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
 * Incremental SHA-256 with snapshot-without-consume.
 *
 * Used by [TlsTranscriptHash] to avoid re-hashing every accumulated message
 * on each snapshot. RFC 8446's key schedule samples the transcript at three
 * points (handshake keys, application keys, Finished verification) — each of
 * which would otherwise re-walk every prior handshake byte. Cloning the
 * digest is O(state size) ≈ a few hundred bytes, vs. O(transcript size) for
 * the re-hash approach.
 *
 * The contract: [update] feeds bytes; [snapshot] returns the SHA-256 of
 * everything fed so far without invalidating the running state, so further
 * [update] calls continue from the same position.
 */
expect class TlsRunningSha256() {
    fun update(bytes: ByteArray)

    fun snapshot(): ByteArray
}
