/**
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
package com.vitorpamplona.quartz.experimental.decoupling

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey

object EncryptionKeyCache {
    private val sharedNonceKeyCache = LruCache<EncryptionKeyCacheKey, ByteArray>(50)

    private fun idx(
        key: HexKey,
        nonce: HexKey,
    ) = EncryptionKeyCacheKey(key, nonce)

    fun put(
        deriveFromPubKey: HexKey,
        nonce: HexKey,
        privKey: ByteArray,
    ): ByteArray? = sharedNonceKeyCache.put(idx(deriveFromPubKey, nonce), privKey)

    fun get(
        deriveFromPubKey: HexKey,
        nonce: HexKey,
    ): ByteArray? = sharedNonceKeyCache.get(idx(deriveFromPubKey, nonce))

    inline fun getOrLoad(
        deriveFromPubKey: HexKey,
        nonce: HexKey,
        load: () -> ByteArray?,
    ): ByteArray? {
        val cachedPrivKey = get(deriveFromPubKey, nonce)
        if (cachedPrivKey != null) {
            return cachedPrivKey
        }

        val newPrivKey = load()
        newPrivKey?.let {
            put(deriveFromPubKey, nonce, newPrivKey)
            newPrivKey
        }
        return newPrivKey
    }
}

// keeps a cache per signed-in user
private data class EncryptionKeyCacheKey(
    val pubkey: HexKey,
    val nonce: HexKey,
)
