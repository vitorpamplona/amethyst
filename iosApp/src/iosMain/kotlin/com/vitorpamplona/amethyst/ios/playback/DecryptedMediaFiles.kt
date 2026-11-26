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
package com.vitorpamplona.amethyst.ios.playback

import com.vitorpamplona.amethyst.commons.service.http.DecryptInformation
import com.vitorpamplona.amethyst.commons.service.http.decryptBlob
import com.vitorpamplona.amethyst.ios.toNSData
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToFile

/**
 * Decrypted copies of encrypted audio and video, one per URL, in the temp folder (iOS clears it).
 * AVPlayer reads a file by its extension, so the copy gets one from the event's MIME type.
 */
@OptIn(ExperimentalForeignApi::class)
class DecryptedMediaFiles(
    private val download: suspend (String) -> ByteArray?,
) {
    private val lock = Mutex()
    private val files = mutableMapOf<String, String>()

    /** The URL the player was given for [originalUrl]: its decrypted copy, or itself. */
    fun playingUrl(originalUrl: String): String = files[originalUrl] ?: originalUrl

    /** A `file://` URL of [url]'s decrypted copy, or null when it can't be fetched or opened. */
    suspend fun fileUrlFor(
        url: String,
        info: DecryptInformation,
    ): String? =
        lock.withLock {
            files[url]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val bytes = download(url) ?: return@withContext null
                val plain = info.decryptBlob(bytes)
                if (plain == null) {
                    Log.w(TAG) { "Could not decrypt $url" }
                    return@withContext null
                }
                val folder = NSTemporaryDirectory() + "decrypted/"
                NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
                val path = folder + sha256(url.encodeToByteArray()).toHexKey().take(32) + "." + extensionFor(info.mimeType, url)
                if (!plain.toNSData().writeToFile(path, atomically = true)) {
                    Log.w(TAG) { "Could not write the decrypted copy of $url" }
                    return@withContext null
                }
                "file://$path"
            }?.also { files[url] = it }
        }

    private fun extensionFor(
        mimeType: String?,
        url: String,
    ): String =
        when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/ogg", "audio/opus" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            else ->
                url
                    .substringBefore('?')
                    .substringAfterLast('/')
                    .substringAfterLast('.', "mp4")
                    .takeIf { it.length in 2..4 } ?: "mp4"
        }

    companion object {
        private const val TAG = "DecryptedMediaFiles"
    }
}
