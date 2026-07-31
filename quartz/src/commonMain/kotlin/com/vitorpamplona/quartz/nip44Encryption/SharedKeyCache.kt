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
package com.vitorpamplona.quartz.nip44Encryption

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

class SharedKeyCache {
    // Keyed by the full (privateKey || pubKey) content, NOT by a 32-bit hashCode.
    // A hashCode key silently collides: two distinct peers whose (priv, pub) bytes
    // hash to the same Int would share a cache slot, so `get` could return one
    // peer's conversation key for a message meant for the other — a silent
    // wrong-key encrypt/decrypt. The polynomial hash that was used here collides
    // independently of the private key, so an attacker could grind a pubkey that
    // aliases a victim's contact. Keying on the raw bytes removes the collision.
    private val sharedKeyCache = LruCache<String, ByteArray>(200)

    fun clearCache() {
        sharedKeyCache.evictAll()
    }

    private fun cacheKey(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = privateKey.toHexKey() + pubKey.toHexKey()

    fun get(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray? = sharedKeyCache[cacheKey(privateKey, pubKey)]

    fun add(
        privateKey: ByteArray,
        pubKey: ByteArray,
        secret: ByteArray,
    ) {
        sharedKeyCache.put(cacheKey(privateKey, pubKey), secret)
    }
}
