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
package com.vitorpamplona.amethyst.commons.ui.components.animated

import kotlin.concurrent.Volatile

/**
 * Where [AnimatedGifImage] gets the bytes of an animated image. Coil decodes only a GIF's first
 * frame on desktop and iOS, so the player fetches the file itself, and each platform says how:
 * the desktop over its OkHttp clients (Tor per URL, `java.io.File` for picked files), iOS over its
 * Ktor client. Either way, encrypted media comes back decrypted.
 *
 * The app installs one at start ([installed]); until then every load fails and the player falls
 * back to the image loader's still.
 */
interface AnimatedImageFetcher {
    /** The bytes of a file the user picked on this device (the media Studio), or null when [url] names none. */
    fun readLocal(url: String): ByteArray?

    /**
     * Whether [url]'s file may be written to the image loader's disk cache: false for media that
     * went over Tor or was encrypted, which, like the other media caches, never touches the disk.
     */
    fun mayCacheOnDisk(url: String): Boolean

    /** [url]'s bytes, decrypted when it is encrypted media; null when the download fails (an error page is not a GIF). */
    suspend fun download(url: String): ByteArray?

    companion object {
        /** Fetches nothing: the player shows the image loader's still instead. */
        val None =
            object : AnimatedImageFetcher {
                override fun readLocal(url: String): ByteArray? = null

                override fun mayCacheOnDisk(url: String): Boolean = false

                override suspend fun download(url: String): ByteArray? = null
            }

        /** The platform's fetcher, set once at start, before the first window draws. */
        @Volatile
        var installed: AnimatedImageFetcher = None
    }
}
