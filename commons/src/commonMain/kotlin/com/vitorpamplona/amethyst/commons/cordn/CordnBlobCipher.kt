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
package com.vitorpamplona.amethyst.commons.cordn

/**
 * Encrypts the blobs the cordn stores put on disk.
 *
 * A seam, not an abstraction for its own sake. Everything cordn persists is key
 * material or derived from it — an `MlsGroupState` carries the ratchet tree and
 * epoch secrets, a `KeyPackageBundle` carries the private half that opens a
 * Welcome — so "encrypted at rest" is not a preference here, and the stores
 * refuse to be built without one.
 *
 * It exists as an interface for one concrete reason: the file layout, the key
 * encoding and the atomic-write behaviour are worth testing, and the Android
 * KeyStore cannot run in a JVM unit test. Marmot's equivalent store is welded
 * to the KeyStore and consequently has no unit test at all; this one does.
 *
 * Implementations must be safe to call from several coroutines at once.
 */
interface CordnBlobCipher {
    fun encrypt(bytes: ByteArray): ByteArray

    /** @throws Exception if [bytes] were not produced by this cipher. */
    fun decrypt(bytes: ByteArray): ByteArray
}
