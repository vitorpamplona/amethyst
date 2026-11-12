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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The front page's art (its `<img>`s and CSS images), kept on disk by URL.
 *
 * Chromium never caches it: the paper is an opaque-origin document, and what such a frame loads isn't
 * written to the HTTP cache. Every time the paper came back on screen (a tab switch, a scroll) it
 * downloaded every picture again, each a megabyte or more and, with Tor on, 8 to 12 seconds. So the
 * session serves the art itself, from here.
 *
 * Shared by every account: the paper runs no script, so no page can time a load and learn what this
 * device already fetched. Files are written whole (temp file + rename), so two copies of the app can
 * share the folder.
 */
class PaperArtCache(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    /** The cached bytes for [url], or null when they aren't here. */
    fun get(url: String): ByteArray? {
        val file = fileFor(url).takeIf { it.isFile } ?: return null
        val now = System.currentTimeMillis()
        // Marks it used, so trimming drops what was read longest ago; at most once an hour.
        if (now - file.lastModified() > TOUCH_INTERVAL_MS && !file.setLastModified(now)) {
            Log.d(TAG) { "Could not mark $file as used" }
        }
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun put(
        url: String,
        bytes: ByteArray,
    ) {
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(TAG) { "Cannot create $dir" }
            return
        }
        val target = fileFor(url)
        val tmp = File(dir, "${target.name}$TEMP_MARKER${System.nanoTime()}")
        try {
            FileOutputStream(tmp).use { it.write(bytes) }
            if (!tmp.renameTo(target)) tmp.deleteOrWarn(TAG, "leftover temp file")
        } catch (e: IOException) {
            Log.w(TAG, "Could not cache the art of $url", e)
            tmp.deleteOrWarn(TAG, "partial temp file")
        }
    }

    /**
     * [url]'s bytes: from disk, or downloaded with [client] and kept. Null when the download fails,
     * isn't a success, or is larger than [MAX_ITEM_BYTES].
     */
    fun load(
        url: String,
        client: OkHttpClient,
    ): ByteArray? {
        get(url)?.let { return it }
        val bytes =
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body.contentLength() > MAX_ITEM_BYTES) return null
                    body.byteStream().use { input ->
                        val read = input.readNBytes(MAX_ITEM_BYTES + 1)
                        if (read.size > MAX_ITEM_BYTES) return null
                        read
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG) { "Could not fetch the art of $url: ${e.message}" }
                return null
            } catch (e: IllegalArgumentException) {
                // Not a URL OkHttp takes.
                return null
            }
        put(url, bytes)
        return bytes
    }

    /** Drops what was used longest ago until the folder is under [maxBytes]. */
    fun trim() {
        runCatching {
            val now = System.currentTimeMillis()
            // A temp file being written now stays; one a killed write left behind goes with the rest.
            val files = dir.listFiles()?.filter { it.isFile && (!it.name.contains(TEMP_MARKER) || now - it.lastModified() > TOUCH_INTERVAL_MS) } ?: return
            var total = files.sumOf { it.length() }
            if (total <= maxBytes) return
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= maxBytes) return
                val length = file.length()
                if (file.deleteOrWarn(TAG, "front page art")) total -= length
            }
        }
    }

    private fun fileFor(url: String) = File(dir, sha256(url.encodeToByteArray()).toHexKey())

    companion object {
        private const val TAG = "PaperArtCache"
        const val DEFAULT_MAX_BYTES = 128L * 1024 * 1024
        const val MAX_ITEM_BYTES = 20 * 1024 * 1024
        private const val TOUCH_INTERVAL_MS = 60L * 60 * 1000
        private const val TEMP_MARKER = ".tmp."

        fun dirFor(filesDir: File) = File(filesDir, "front-page-art")

        /**
         * The image type [bytes] start with. Chromium sniffs a raster image whatever the header says,
         * but draws an SVG only when it is called one.
         */
        fun mimeOf(bytes: ByteArray): String {
            fun startsWith(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
            return when {
                startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
                startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
                startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
                bytes.size >= 12 && startsWith(0x52, 0x49, 0x46, 0x46) && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                bytes.size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "image/avif"
                String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8).contains("<svg", ignoreCase = true) -> "image/svg+xml"
                else -> "application/octet-stream"
            }
        }
    }
}
