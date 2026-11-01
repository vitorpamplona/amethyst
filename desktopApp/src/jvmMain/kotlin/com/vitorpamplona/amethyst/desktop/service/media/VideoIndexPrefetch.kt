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

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.Request
import java.io.IOException

/**
 * Fetches the index of an MP4 into [VideoDiskCache] before an engine opens it, so the engine reads
 * the index from disk.
 *
 * macOS's engine gives up on a video whose size it hasn't learned within 5 s ("Failed to retrieve
 * video size"), and it learns the size only from the index (`moov`). An hour-long episode carries a
 * 4.6 MB index at its front; from a slow host that took 4 to 8 s with nothing else downloading, so
 * the engine never played it, and the bundled Chromium has no H.264 to fall back on.
 *
 * Only the index: the first [PROBE_BYTES] to find it, then up to its end, never past
 * [VideoDiskCache.MAX_HEAD_BYTES]. A file with its index at the end (after `mdat`), or anything that
 * isn't an MP4, is left to the engine. Opens of the same video share one fetch.
 */
internal object VideoIndexPrefetch {
    private const val TAG = "VideoIndexPrefetch"
    private const val PROBE_BYTES = 64 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = HashMap<String, Deferred<Unit>>()

    /** Returns once [url]'s index is on disk, or it is clear it won't be. */
    suspend fun prefetch(url: String) {
        if (!VideoDiskCache.cacheable(url)) return
        val job =
            synchronized(inFlight) {
                inFlight.getOrPut(url) {
                    scope.async {
                        try {
                            fetchIndex(url)
                        } finally {
                            synchronized(inFlight) { inFlight.remove(url) }
                        }
                    }
                }
            }
        job.await()
    }

    private fun fetchIndex(url: String) {
        try {
            if ((VideoDiskCache.heldStart(url)?.first ?: 0L) < PROBE_BYTES && !fetch(url, 0, PROBE_BYTES - 1L)) return
            val head = VideoDiskCache.readStart(url, PROBE_BYTES) ?: return
            val end = endOfLeadingIndex(head) ?: return
            if (end > VideoDiskCache.MAX_HEAD_BYTES) return
            val held = VideoDiskCache.heldStart(url)?.first ?: 0L
            if (held >= end) return
            val started = System.currentTimeMillis()
            if (fetch(url, held, end - 1)) Log.d(TAG) { "Fetched the ${end / 1024} KiB index of $url in ${System.currentTimeMillis() - started} ms" }
        } catch (e: IOException) {
            Log.d(TAG) { "Could not fetch the index of $url: ${e.message}" }
        }
    }

    // Fetches bytes [first]..[last] of [url] into the disk cache; false when the host won't give a range.
    private fun fetch(
        url: String,
        first: Long,
        last: Long,
    ): Boolean {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Range", "bytes=$first-$last")
                .build()
        MediaHttp.client(url).newCall(request).execute().use { response ->
            if (response.code != 206) return false
            val cache = VideoDiskCache.writerFor(url, response) ?: return false
            try {
                response.body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        cache.write(buf, n)
                    }
                }
            } finally {
                cache.close()
            }
            return true
        }
    }

    /**
     * Where the `moov` box of the MP4 starting with [head] ends, when it comes before the media
     * (`mdat`) and its header is within [head]; null otherwise, or when [head] isn't an MP4.
     */
    internal fun endOfLeadingIndex(head: ByteArray): Long? {
        if (head.size < 8 || boxType(head, 4) != "ftyp") return null
        var at = 0L
        while (at + 8 <= head.size) {
            val i = at.toInt()
            var size = u32(head, i)
            var header = 8
            if (size == 1L) {
                if (at + 16 > head.size) return null
                size = u64(head, i + 8)
                header = 16
            }
            // 0 runs to the end of the file; anything below its own header is not a box.
            if (size < header) return null
            when (boxType(head, i + 4)) {
                "moov" -> return at + size
                "mdat" -> return null
            }
            at += size
        }
        return null
    }

    private fun boxType(
        b: ByteArray,
        at: Int,
    ): String = String(b, at, 4, Charsets.ISO_8859_1)

    private fun u32(
        b: ByteArray,
        at: Int,
    ): Long = (0 until 4).fold(0L) { acc, k -> (acc shl 8) or (b[at + k].toLong() and 0xFF) }

    private fun u64(
        b: ByteArray,
        at: Int,
    ): Long = (0 until 8).fold(0L) { acc, k -> (acc shl 8) or (b[at + k].toLong() and 0xFF) }
}
