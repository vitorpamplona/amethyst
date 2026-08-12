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

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedKeyCacheTest {
    /**
     * The cache maps (privateKey, pubKey) to a shared/conversation key. If two
     * DISTINCT peer pubkeys can map to the same cache slot, the cache returns one
     * peer's secret for a message intended for the other — a silent wrong-key
     * encryption/decryption (confidentiality break).
     *
     * `pubA` and `pubB` below are engineered to collide under a 31-multiplier
     * polynomial hash of their bytes: bumping byte[31] by +31 and byte[30] by -1
     * leaves `31 * acc + byte` unchanged (31^1 * (-1) + 31^0 * (+31) == 0). They
     * are otherwise different keys, so a correct cache MUST treat them as distinct.
     */
    @Test
    fun collidingPubKeysMustNotShareCacheEntry() {
        val cache = SharedKeyCache()
        val priv = ByteArray(32) { 1 }

        val pubA =
            ByteArray(32).also {
                it[30] = 0x10
                it[31] = 0x00
            }
        val pubB =
            ByteArray(32).also {
                it[30] = 0x0F
                it[31] = 0x1F
            }

        // Sanity: these are genuinely different peer public keys.
        assertTrue(!pubA.contentEquals(pubB), "test setup: pubkeys must differ")

        val secretForA = ByteArray(32) { 0xAA.toByte() }
        cache.add(priv, pubA, secretForA)

        // The secret cached for peer A must never be handed back for peer B.
        assertNull(
            cache.get(priv, pubB),
            "cache returned peer A's shared secret when asked for peer B (hash collision leaks the wrong key)",
        )

        // The legitimate lookup must still work.
        assertTrue(secretForA.contentEquals(cache.get(priv, pubA)!!))
    }

    @Test
    fun distinctPeersGetDistinctEntries() {
        val cache = SharedKeyCache()
        val priv = ByteArray(32) { 7 }
        val pub1 = ByteArray(32) { 2 }
        val pub2 = ByteArray(32) { 3 }
        val s1 = ByteArray(32) { 0x11 }
        val s2 = ByteArray(32) { 0x22 }

        cache.add(priv, pub1, s1)
        cache.add(priv, pub2, s2)

        assertTrue(s1.contentEquals(cache.get(priv, pub1)!!))
        assertTrue(s2.contentEquals(cache.get(priv, pub2)!!))
    }
}
