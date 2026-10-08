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

import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Encrypted media as files the native engine can open. The engine streams a URL itself, around
 * the app's HTTP stack, so an encrypted blob reached it as ciphertext. Android's player gets the
 * plaintext through the decrypting OkHttp client; here the blob is downloaded through that same
 * client (via [MediaHttp]) into the app's owner-only temp dir, once per URL, and the engine plays
 * the file. The copies go when the app exits (and the temp-dir sweep catches a crash).
 *
 * Range requests are no help anyway: the AEAD tag covers the whole blob, so it is decrypted whole.
 */
object DecryptedMediaFiles {
    private val files = ConcurrentHashMap<String, File>()
    private val downloads = ConcurrentHashMap<String, Mutex>()

    /**
     * The decrypted copy of [url], downloading it on first use; null when [url] is not encrypted.
     * Throws [IOException] when the download fails.
     */
    suspend fun fileFor(url: String): File? {
        if (!MediaHttp.isEncrypted(url)) return null
        files[url]?.takeIf { it.exists() }?.let { return it }

        return downloads.getOrPut(url) { Mutex() }.withLock {
            files[url]?.takeIf { it.exists() }?.let { return@withLock it }
            download(url).also { files[url] = it }
        }
    }

    private suspend fun download(url: String): File =
        withContext(Dispatchers.IO) {
            val file = AmethystTempDir.createTempFile("amethyst_media_", extensionFor(url))
            file.deleteOnExit()
            try {
                val request = Request.Builder().url(url).build()
                MediaHttp.client(url).newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                    response.body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                file
            } catch (e: Exception) {
                file.delete()
                if (e is CancellationException || e is IOException) throw e
                throw IOException("Could not download $url", e)
            }
        }

    /** A suffix the engine can sniff the container from: the declared MIME type's, else the URL's. */
    private fun extensionFor(url: String): String {
        val fromMime =
            when (
                MediaHttp
                    .encryptedMimeType(url)
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
            ) {
                "video/mp4" -> ".mp4"
                "video/quicktime" -> ".mov"
                "video/webm" -> ".webm"
                "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> ".m4a"
                "audio/mpeg", "audio/mp3" -> ".mp3"
                "audio/ogg", "audio/opus" -> ".ogg"
                "audio/webm" -> ".weba"
                "audio/wav", "audio/x-wav" -> ".wav"
                else -> null
            }
        if (fromMime != null) return fromMime
        val fromUrl = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
        return if (fromUrl.length in 2..5 && fromUrl.all { it.isLetterOrDigit() }) ".$fromUrl" else ".bin"
    }
}
