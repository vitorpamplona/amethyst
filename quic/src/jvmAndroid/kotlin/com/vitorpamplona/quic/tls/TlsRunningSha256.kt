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

import java.security.MessageDigest

/**
 * JCA-backed incremental SHA-256. `MessageDigest.clone()` is supported by all
 * stock JDK SHA-256 providers and produces an independent digest object — we
 * use that to take a snapshot without disturbing the running state.
 *
 * Single-thread per instance: the TlsClient state machine is the sole caller,
 * driven by the QUIC connection's lock, so no synchronization is needed.
 */
actual class TlsRunningSha256 actual constructor() {
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    actual fun update(bytes: ByteArray) {
        digest.update(bytes)
    }

    actual fun snapshot(): ByteArray {
        // Cloning the digest is the only way to read the current hash without
        // ending the running state — `digest.digest()` finalizes and resets,
        // which would silently corrupt subsequent updates.
        val clone = digest.clone() as MessageDigest
        return clone.digest()
    }
}
