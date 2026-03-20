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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.bloom.MurmurHash3

/**
 * The seed is used to compute the hash of the Key, which
 * becomes the seed for the hash of the value
 */
class TagNameValueHasher(
    val seed: Long,
) {
    val hasher = MurmurHash3()

    // small performance improvements on inserting
    val pTagHash by lazy {
        hasher.hash128x64Half("p".encodeToByteArray(), seed)
    }
    val eTagHash by lazy {
        hasher.hash128x64Half("e".encodeToByteArray(), seed)
    }
    val aTagHash by lazy {
        hasher.hash128x64Half("a".encodeToByteArray(), seed)
    }

    fun hash(
        key: ByteArray,
        value: ByteArray,
    ) = hasher.hash128x64Half(value, hasher.hash128x64Half(key, seed))

    /*
     * We tried caching these values to avoid recomputation of the hash
     * But caching on a LruCache<String, Long> is slower than recomputing
     */
    fun hash(
        key: String,
        value: String,
    ) = hash(key.encodeToByteArray(), value.encodeToByteArray())

    fun hashATag(value: String) = hasher.hash128x64Half(value.encodeToByteArray(), aTagHash)

    fun hashETag(value: String) = hasher.hash128x64Half(value.encodeToByteArray(), eTagHash)

    fun hashPTag(value: String) = hasher.hash128x64Half(value.encodeToByteArray(), pTagHash)

    fun hash(value: HexKey) = hasher.hash128x64Half(value.encodeToByteArray(), seed)
}
