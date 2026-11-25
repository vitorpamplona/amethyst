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

import com.vitorpamplona.amethyst.commons.ui.components.animated.AnimatedImageFetcher
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File
import java.net.URI

/**
 * The desktop's half of the shared GIF player: files the user picked come from disk, everything
 * else over the image role's OkHttp client ([MediaHttp.imageClient]), which follows the user's Tor
 * choice and decrypts encrypted media. Nothing that went over Tor or was encrypted is cached on disk.
 */
object DesktopAnimatedImageFetcher : AnimatedImageFetcher {
    override fun readLocal(url: String): ByteArray? = localFile(url)?.readBytes()

    override fun mayCacheOnDisk(url: String): Boolean = !MediaHttp.viaTor(url) && !MediaHttp.isEncrypted(url)

    override suspend fun download(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        return MediaHttp.imageClient(url).newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) return null
            response.body.bytes()
        }
    }

    /** The file [url] names when it is a local path (`/home/…`, `C:\…`) or a `file:` URL; null for anything fetched. */
    private fun localFile(url: String): File? {
        val file =
            when {
                url.startsWith("file:") -> runCatching { File(URI(url)) }.getOrNull()
                url.startsWith("http://") || url.startsWith("https://") -> null
                else -> File(url)
            }
        return file?.takeIf { it.isFile }
    }
}
