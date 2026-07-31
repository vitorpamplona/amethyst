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

class SharedKeyCache {
    // Keyed by the full (privateKey, pubKey) content via [CacheKey], NOT by a bare
    // 32-bit hashCode. Using a hashCode as the whole map key silently collides:
    // two distinct peers whose (priv, pub) bytes hash to the same Int would share a
    // slot, so `get` could return one peer's conversation key for a message meant
    // for the other — a silent wrong-key encrypt/decrypt (and the polynomial hash
    // that was used collides independently of the private key, so a pubkey aliasing
    // a victim's contact is grindable). [CacheKey] keeps the cheap Int hash only as
    // a bucket selector and disambiguates collisions with a full contentEquals, so
    // it stays correct while avoiding the per-lookup allocation of a hex String key.
    private val sharedKeyCache = LruCache<CacheKey, ByteArray>(200)

    fun clearCache() {
        sharedKeyCache.evictAll()
    }

    fun get(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray? = sharedKeyCache[CacheKey(privateKey, pubKey)]

    fun add(
        privateKey: ByteArray,
        pubKey: ByteArray,
        secret: ByteArray,
    ) {
        sharedKeyCache.put(CacheKey(privateKey, pubKey), secret)
    }

    /**
     * Content-addressed cache key holding the raw key references (no byte copy, no
     * hex string). The precomputed [hash] is only a bucket selector — [equals] does
     * the authoritative full-content comparison, so hash collisions can never return
     * the wrong peer's secret. Callers must treat the passed arrays as immutable
     * (the same value-type contract [com.vitorpamplona.quartz.marmot.mls.crypto.X25519KeyPair]
     * relies on when used as a map key).
     */
    private class CacheKey(
        val privateKey: ByteArray,
        val pubKey: ByteArray,
    ) {
        private val hash = privateKey.contentHashCode() * 31 + pubKey.contentHashCode()

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CacheKey) return false
            return hash == other.hash &&
                privateKey.contentEquals(other.privateKey) &&
                pubKey.contentEquals(other.pubKey)
        }
    }
}
