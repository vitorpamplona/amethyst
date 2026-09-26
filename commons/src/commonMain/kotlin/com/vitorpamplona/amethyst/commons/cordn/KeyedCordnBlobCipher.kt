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

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * A [CordnBlobCipher] under a key the caller supplies: ChaCha20-Poly1305 with
 * a fresh random nonce per blob, framed `nonce:12 || ciphertext || tag:16`.
 *
 * For every platform that has no OS-held key store to hide the key in — the
 * desktop app and `amy`. It is the honest half of "encrypted at rest": the
 * cipher is real, and where the key lives decides what that buys. Android's
 * [CordnBlobCipher] keeps its key in hardware, so a blob read off the device
 * is useless; a caller that keeps the key in a file beside the blobs is
 * protecting against a stolen backup, not against a process running as the
 * same user. Say which one you are doing where the key is created, not here.
 *
 * ## The nonce is random, not a counter
 *
 * One key covers every blob this cipher writes, and the stores rewrite the
 * same group's state on every epoch change, so a counter would have to be
 * persisted and survive a crash to stay unique. A 96-bit random nonce does
 * not: at the volumes a client writes (thousands of blobs, not billions) the
 * collision probability is negligible, and a repeat would be a confidentiality
 * break rather than a corrupted file. Nonce reuse under ChaCha20-Poly1305
 * leaks the XOR of two plaintexts and the Poly1305 key — for an
 * `MlsGroupState` that is epoch secrets, so this is the one parameter here
 * worth being careful about.
 *
 * No associated data: the frame has no header worth binding, and the blob's
 * path is not authenticated on purpose. The stores move a file into place
 * atomically and a blob that lands at the wrong path fails to parse as the
 * thing that path expects, which is where that belongs.
 *
 * Stateless, so safe to call from several coroutines at once.
 */
class KeyedCordnBlobCipher(
    private val key: ByteArray,
) : CordnBlobCipher {
    init {
        require(key.size == KEY_LENGTH) { "a cordn blob key is $KEY_LENGTH bytes, got ${key.size}" }
    }

    override fun encrypt(bytes: ByteArray): ByteArray {
        val nonce = RandomInstance.bytes(NONCE_LENGTH)
        return nonce + ChaCha20Poly1305.encrypt(bytes, EMPTY, nonce, key)
    }

    override fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size > NONCE_LENGTH) { "not a cordn blob: ${bytes.size} bytes" }
        return ChaCha20Poly1305.decrypt(
            bytes.copyOfRange(NONCE_LENGTH, bytes.size),
            EMPTY,
            bytes.copyOfRange(0, NONCE_LENGTH),
            key,
        )
    }

    companion object {
        const val KEY_LENGTH = 32
        const val NONCE_LENGTH = 12

        private val EMPTY = ByteArray(0)

        /** A new key, for a caller about to persist one. */
        fun newKey(): ByteArray = RandomInstance.bytes(KEY_LENGTH)
    }
}
