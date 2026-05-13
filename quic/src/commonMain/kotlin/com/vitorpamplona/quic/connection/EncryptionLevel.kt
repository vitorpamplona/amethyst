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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.crypto.Aead

/** Per-direction packet protection material at one encryption level. */
class PacketProtection(
    val aead: Aead,
    val key: ByteArray,
    val iv: ByteArray,
    val hp: com.vitorpamplona.quic.crypto.HeaderProtection,
    val hpKey: ByteArray,
) {
    /**
     * Per-direction nonce scratch buffer, sized to match [iv] (12 bytes for
     * QUIC's AEADs). Reused by hot-path callers via
     * [com.vitorpamplona.quic.crypto.aeadNonceInto] so the AEAD nonce no
     * longer allocates a fresh ByteArray on every encrypt/decrypt (round-5
     * #P2).
     *
     * Thread-safety: a `PacketProtection` instance only ever lives in ONE
     * direction (send or receive) at one encryption level. The writer and
     * parser both operate under `streamsLock`, so the scratch is touched
     * by at most one coroutine at a time. Callers that need to keep a
     * nonce around past a single seal/open call must copy it; the buffer
     * is overwritten on the next [com.vitorpamplona.quic.crypto.aeadNonceInto]
     * invocation.
     */
    val nonceScratch: ByteArray = ByteArray(iv.size)

    /**
     * Per-direction header-protection scratch — 16 bytes of AES-ECB output
     * for [hp.maskInto][com.vitorpamplona.quic.crypto.HeaderProtection.maskInto].
     * Reused per packet so the AES output no longer allocates (round-5 #P1).
     * Same single-direction thread-safety rationale as [nonceScratch].
     */
    val hpScratch: ByteArray = ByteArray(16)

    /**
     * Per-direction header-protection mask buffer — 5 bytes per RFC 9001
     * §5.4.3. Filled by [hp.maskInto][com.vitorpamplona.quic.crypto.HeaderProtection.maskInto]
     * and immediately consumed by [com.vitorpamplona.quic.crypto.applyHeaderProtectionMask]
     * (write path) or by inline first-byte / packet-number unmasking
     * (parse path). Consume before the next mask call on this instance.
     */
    val hpMask: ByteArray = ByteArray(5)
}

/** All four encryption levels we ever see in a QUIC client connection. */
enum class EncryptionLevel(
    val space: PacketNumberSpace,
) {
    INITIAL(PacketNumberSpace.INITIAL),
    HANDSHAKE(PacketNumberSpace.HANDSHAKE),
    APPLICATION(PacketNumberSpace.APPLICATION),
}
