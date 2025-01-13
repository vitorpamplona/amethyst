/**
 * Copyright (c) 2024 Vitor Pamplona
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

import android.util.LruCache
import com.vitorpamplona.quartz.nip17Dm.NostrCipher

/**
 * Neigther ExoPlayer, nor Coil support passing key and nonce to the Interceptor via
 * Request.tag, which would be the right way to do this.
 *
 * This class serves as a key cache to decrypt the body of HTTP calls that need it.
 */
class EncryptionKeyCache {
    val cache = LruCache<String, NostrCipher>(100)

    fun add(
        url: String?,
        cipher: NostrCipher,
    ) {
        if (cache.get(url) == null) {
            cache.put(url, cipher)
        }
    }

    fun get(url: String): NostrCipher? = cache.get(url)
}
