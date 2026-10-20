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
package com.vitorpamplona.quartz.mls.crypto

import com.vitorpamplona.mls.crypto.X25519 as MlsX25519

/*
 * Binary-compatibility shim. The MLS engine moved to vitorpamplona/mls-kmp
 * (`com.vitorpamplona.mls`), but the published quic-kmp 1.0.0 was compiled against
 * Quartz when X25519 lived here, and its TLS 1.3 handshake still calls
 * `X25519.generateKeyPair()`, `X25519.dh()` and the two `X25519KeyPair` getters
 * under this package. The root build substitutes this checkout's :quartz for that
 * dependency, so without these classes nests and marmotQuic would fail at runtime
 * with NoClassDefFoundError, which no compiler would catch.
 *
 * Delete this file once a quic-kmp release imports `com.vitorpamplona.mls.crypto.X25519`.
 */

@Deprecated(
    "Moved to mls-kmp; kept only for quic-kmp 1.0.0 binary compatibility.",
    ReplaceWith("X25519", "com.vitorpamplona.mls.crypto.X25519"),
)
object X25519 {
    fun generateKeyPair(): X25519KeyPair = MlsX25519.generateKeyPair().let { X25519KeyPair(it.privateKey, it.publicKey) }

    fun dh(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray = MlsX25519.dh(privateKey, publicKey)

    fun publicFromPrivate(privateKey: ByteArray): ByteArray = MlsX25519.publicFromPrivate(privateKey)
}

@Deprecated(
    "Moved to mls-kmp; kept only for quic-kmp 1.0.0 binary compatibility.",
    ReplaceWith("X25519KeyPair", "com.vitorpamplona.mls.crypto.X25519KeyPair"),
)
class X25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
)
