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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.decryptBlob
import com.vitorpamplona.amethyst.commons.ui.components.animated.AnimatedImageFetcher
import com.vitorpamplona.quartz.utils.Log
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSURL

/**
 * iOS's half of the shared GIF player: downloads over the app's Ktor client ([download], the one
 * Coil uses) and decrypts encrypted media with the account's [keys], as [EncryptedBlobFetcher]
 * does for stills. Encrypted media is never written to the disk cache. There is no Tor on iOS yet
 * (B12), so every other URL may be.
 */
class IosAnimatedImageFetcher(
    private val keys: EncryptionKeyCache,
    private val download: suspend (String) -> ByteArray?,
) : AnimatedImageFetcher {
    override fun readLocal(url: String): ByteArray? {
        val path =
            when {
                url.startsWith("file:") -> NSURL.URLWithString(url)?.path
                url.startsWith("http://") || url.startsWith("https://") -> null
                else -> url
            } ?: return null
        return try {
            FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read the picked file", e)
            null
        }
    }

    override fun mayCacheOnDisk(url: String): Boolean = keys.get(url) == null

    override suspend fun download(url: String): ByteArray? {
        val bytes = download.invoke(url) ?: return null
        val info = keys.get(url) ?: return bytes
        return info.decryptBlob(bytes) ?: run {
            Log.w(TAG) { "Could not decrypt $url" }
            null
        }
    }

    private companion object {
        const val TAG = "IosAnimatedImageFetcher"
    }
}
