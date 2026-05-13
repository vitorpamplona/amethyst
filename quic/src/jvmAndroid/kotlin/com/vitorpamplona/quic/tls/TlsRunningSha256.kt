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
 * from the native digest.
 *
 * We probe `clone()` ONCE at construction: if it works the JCA-clone path
 * runs for the lifetime of the instance with zero side-buffer overhead;
 * if it doesn't we switch to byte-accumulator-then-one-shot-SHA-256 mode
 * from the start, so the very first snapshot already has the complete
 * transcript. Working devices (the overwhelming majority) pay nothing
 * for the fallback.
 *
 * Single-thread per instance: the TlsClient state machine is the sole caller,
 * driven by the QUIC connection's lock, so no synchronization is needed.
 */
actual class TlsRunningSha256 actual constructor() {
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    /**
     * Result of the one-time `digest.clone()` probe. Conscrypt's clone
     * support is a property of the build (native bridge presence), not of
     * the digest's internal state, so a single probe is a reliable signal
     * — repeated clones on the same instance behave identically.
     */
    private val cloneable: Boolean =
        try {
            digest.clone()
            true
        } catch (_: CloneNotSupportedException) {
            false
        }

    /**
     * Byte accumulator allocated only on the broken-clone fallback path.
     * Holds every byte fed to [update] so [snapshot] can one-shot
     * SHA-256 the full transcript. On clone-capable devices this stays
     * `null` and [update] never touches it.
     */
    private val accumulator: ByteArrayOutputStream? =
        if (cloneable) null else ByteArrayOutputStream(512)

    actual fun update(bytes: ByteArray) {
        digest.update(bytes)
        accumulator?.write(bytes)
    }

    actual fun snapshot(): ByteArray {
        if (cloneable) {
            // Cheap path — independent digest object with current internal
            // state, no consume.
            val clone = digest.clone() as MessageDigest
            return clone.digest()
        }
        // Fallback: one-shot SHA-256 over the accumulated transcript bytes.
        // `MessageDigest.getInstance("SHA-256")` is mandated on every JCA
        // provider — only `.clone()` capability varies — so this is
        // guaranteed to work on the same device that rejected the clone.
        return MessageDigest.getInstance("SHA-256").digest(accumulator!!.toByteArray())
    }
}
