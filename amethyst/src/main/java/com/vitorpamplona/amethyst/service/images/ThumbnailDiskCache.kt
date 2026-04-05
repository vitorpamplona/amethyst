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
package com.vitorpamplona.amethyst.service.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk cache for pre-resized profile picture thumbnails.
 *
 * Stores small JPEG thumbnails (typically ~5-10KB each) keyed by URL hash.
 * This avoids re-decoding large original images from Coil's disk cache on
 * every recomposition when images are evicted from the memory cache.
 *
 * Thread safety:
 * - Writes use atomic temp-file-then-rename to prevent partial reads.
 * - [inFlight] tracks URLs currently being saved to prevent duplicate work
 *   when multiple composables request the same profile picture simultaneously.
 */
class ThumbnailDiskCache(
    private val cacheDir: File,
    private val maxEntries: Int = 10_000,
) {
    companion object {
        const val THUMBNAIL_SIZE_PX = 256
        const val JPEG_QUALITY = 80
    }

    // Tracks URLs currently being written to prevent duplicate saves
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    init {
        cacheDir.mkdirs()
    }

    private fun keyFor(url: String): String = sha256(url.toByteArray()).toHexKey()

    fun get(url: String): File? {
        val file = File(cacheDir, keyFor(url))
        return if (file.exists()) file else null
    }

    /**
     * Saves a bitmap as a JPEG thumbnail. Uses atomic write (temp file + rename)
     * to prevent readers from seeing a partially written file.
     *
     * Returns true if saved, false if already in flight or on error.
     */
    fun save(
        url: String,
        bitmap: Bitmap,
    ): Boolean {
        // Skip if another coroutine is already saving this URL
        if (!inFlight.add(url)) return false

        try {
            val key = keyFor(url)
            val finalFile = File(cacheDir, key)

            // Already saved by another coroutine that finished before us
            if (finalFile.exists()) return true

            // Write to temp file, then atomically rename
            val tempFile = File(cacheDir, "$key.tmp")
            tempFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            tempFile.renameTo(finalFile)

            evictIfNeeded()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            inFlight.remove(url)
        }
    }

    fun decodeThumbnail(file: File): Bitmap? =
        try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            file.delete()
            null
        }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles() ?: return
        if (files.size > maxEntries) {
            files
                .sortedBy { it.lastModified() }
                .take(files.size - maxEntries)
                .forEach { it.delete() }
        }
    }
}
