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
    private val sharedKeyCache = LruCache<Int, ByteArray>(200)

    fun clearCache() {
        sharedKeyCache.evictAll()
    }

    fun combinedHashCode(
        a: ByteArray,
        b: ByteArray,
    ): Int {
        var result = 1
        for (element in a) result = 31 * result + element
        for (element in b) result = 31 * result + element
        return result
    }

    fun get(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray? = sharedKeyCache[combinedHashCode(privateKey, pubKey)]

    fun add(
        privateKey: ByteArray,
        pubKey: ByteArray,
        secret: ByteArray,
    ) {
        sharedKeyCache.put(combinedHashCode(privateKey, pubKey), secret)
    }
}
