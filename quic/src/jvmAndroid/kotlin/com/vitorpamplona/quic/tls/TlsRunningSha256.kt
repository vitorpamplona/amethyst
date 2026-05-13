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

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * JCA-backed incremental SHA-256. `MessageDigest.clone()` is supported by all
 * stock JDK SHA-256 providers AND by Android's Conscrypt
 * `OpenSSLMessageDigestJDK` on shipping releases — but a handful of API
 * 26–28 builds have been reported to throw `CloneNotSupportedException`
 * from the native digest. Detect that on first snapshot and fall back to
 * one-shot SHA-256 over an accumulated byte buffer; we keep accumulating
 * regardless so the fallback always has the complete transcript to hash.
 *
 * Single-thread per instance: the TlsClient state machine is the sole caller,
 * driven by the QUIC connection's lock, so no synchronization is needed.
 */
actual class TlsRunningSha256 actual constructor() {
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    // Parallel byte accumulator — small (a few KB for a TLS transcript),
    // and only consulted on the cloneable-digest fallback path. Keeping it
    // populated unconditionally costs one `ByteArrayOutputStream.write` per
    // [update] but avoids a "first snapshot fails, we have no history"
    // failure mode on the broken-clone Android builds.
    private val accumulator = ByteArrayOutputStream(512)
    private var cloneable = true

    actual fun update(bytes: ByteArray) {
        digest.update(bytes)
        accumulator.write(bytes)
    }

    actual fun snapshot(): ByteArray {
        if (cloneable) {
            try {
                // Cloning the digest is the cheap path — independent digest
                // object with the current internal state, no consume.
                val clone = digest.clone() as MessageDigest
                return clone.digest()
            } catch (_: CloneNotSupportedException) {
                // Latch the fallback so we don't pay the JCA throw on every
                // subsequent snapshot.
                cloneable = false
            }
        }
        // Fallback: one-shot SHA-256 over the accumulated transcript bytes.
        // `MessageDigest.getInstance("SHA-256")` is mandated on every JCA
        // provider — only the `.clone()` capability varies — so this is
        // guaranteed to work on the same device that rejected the clone.
        return MessageDigest.getInstance("SHA-256").digest(accumulator.toByteArray())
    }
}
