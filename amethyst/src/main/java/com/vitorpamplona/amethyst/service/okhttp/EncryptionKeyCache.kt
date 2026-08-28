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
package com.vitorpamplona.amethyst.service.okhttp

import androidx.collection.LruCache
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher

/**
 * Neither ExoPlayer nor Coil support passing key and nonce to the Interceptor via
 * Request.tag, which would be the right way to do this.
 *
 * This class is a string-to-cipher map for HTTP bodies that need decrypting.
 * URL aliases (ipfs://CID vs Originless `{gateway}/ipfs/{CID}`) are registered
 * by the caller via [addForMediaUrl]; lookups here stay exact.
 */
class EncryptionKeyCache {
    // androidx.collection, not android.util: the latter is a no-op stub under
    // unitTests.isReturnDefaultValues = true.
    val cache = LruCache<String, DecryptInformation>(100)

    fun add(
        url: String?,
        decryptInformation: DecryptInformation,
    ) {
        if (url == null) return
        // First write wins: a later add must not clobber an existing cipher for this URL.
        if (cache.get(url) == null) {
            cache.put(url, decryptInformation)
        }
    }

    fun add(
        url: String?,
        cipher: NostrCipher,
        expectedMimeType: String?,
    ) = add(url, DecryptInformation(cipher, expectedMimeType))

    fun get(url: String): DecryptInformation? = cache.get(url)
}

class DecryptInformation(
    val cipher: NostrCipher,
    val mimeType: String?,
)
