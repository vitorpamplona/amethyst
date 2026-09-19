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

import com.vitorpamplona.amethyst.commons.keystorage.KeyStoreEncryption

/**
 * The Android [CordnBlobCipher]: AES-GCM under a key held in the Android
 * KeyStore (StrongBox-backed where the device has it).
 *
 * ## Why the lock
 *
 * [KeyStoreEncryption] keeps one `Cipher` instance in a field and runs
 * `init(...)` then `doFinal(...)` against it. That pair is not atomic, and the
 * stores that use this cipher run on `Dispatchers.IO`, which is a thread pool
 * — two groups saving at once is ordinary, not a corner case. Interleaved, one
 * call's `init` lands between the other's `init` and `doFinal`, and the bytes
 * that reach disk are encrypted under the wrong IV or simply garbage. For an
 * `MlsGroupState` that is not a recoverable error: the group cannot be
 * re-derived from anywhere else on the device.
 *
 * Serialising here rather than fixing [KeyStoreEncryption] keeps this change
 * off code that Marmot and the account storage already depend on.
 */
class KeyStoreCordnBlobCipher : CordnBlobCipher {
    // Not a constructor parameter: KeyStoreEncryption is internal to commons,
    // so taking one publicly would leak an internal type. Nothing needs to
    // inject it either — a test that wanted a different cipher implements
    // [CordnBlobCipher] directly, which is what the seam is for.
    private val encryption = KeyStoreEncryption()
    private val lock = Any()

    override fun encrypt(bytes: ByteArray): ByteArray = synchronized(lock) { encryption.encrypt(bytes) }

    override fun decrypt(bytes: ByteArray): ByteArray = synchronized(lock) { encryption.decrypt(bytes) }
}
