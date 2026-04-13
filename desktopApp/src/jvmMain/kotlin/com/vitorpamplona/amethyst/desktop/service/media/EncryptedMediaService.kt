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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles decryption of media files for NIP-17 DMs.
 * Uses AESGCM cipher from quartz (commonMain).
 * Caches decrypted bytes in memory to avoid re-downloading on recomposition.
 */
object EncryptedMediaService {
    private val httpClient get() = DesktopHttpClient.currentClient()
    private val cache = ConcurrentHashMap<String, ByteArray>()
    private const val MAX_CACHE_ENTRIES = 20

    /**
     * Download and decrypt an encrypted file from a URL.
     * Results are cached by URL to avoid re-downloading on scroll/recomposition.
     * Returns the decrypted bytes.
     */
    suspend fun downloadAndDecrypt(
        url: String,
        keyBytes: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        cache[url]?.let { return it }

        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val encryptedBytes =
                response.use {
                    if (!it.isSuccessful) throw RuntimeException("Download failed: ${it.code}")
                    it.body.bytes()
                }

            val cipher = AESGCM(keyBytes, nonce)
            val decrypted = cipher.decrypt(encryptedBytes)

            // Evict oldest entries if cache is full
            if (cache.size >= MAX_CACHE_ENTRIES) {
                cache.keys.firstOrNull()?.let { cache.remove(it) }
            }
            cache[url] = decrypted

            decrypted
        }
    }
}
