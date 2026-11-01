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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

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
 *
 * The index also names the video's codec ([videoCodecOf]), so a video the engine can't decode goes
 * to Chromium before the engine plays its sound under a blank picture (see [MacVideoDecode]).
 */
internal object VideoIndexPrefetch {
    private const val TAG = "VideoIndexPrefetch"
    private const val PROBE_BYTES = 64 * 1024

    // An index after the media is read only to name the codec: a long video's can be several MB.
    private const val MAX_TRAILING_INDEX_BYTES = 8L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = HashMap<String, Deferred<Unit>>()

    // The video codec each prefetched index names (its sample entry type), by URL.
    private val codecs = ConcurrentHashMap<String, String>()

    /** The video codec of [url] (`avc1`, `hvc1`, `av01`…) once its index has been read; null before, or when unknown. */
    fun videoCodecOf(url: String): String? = codecs[url]

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
            val end = endOfLeadingIndex(head)
            if (end == null) {
                readTrailingIndex(url, head)
                return
            }
            if (end > VideoDiskCache.MAX_HEAD_BYTES) return
            val held = VideoDiskCache.heldStart(url)?.first ?: 0L
            if (held < end) {
                val started = System.currentTimeMillis()
                if (!fetch(url, held, end - 1)) return
                Log.d(TAG) { "Fetched the ${end / 1024} KiB index of $url in ${System.currentTimeMillis() - started} ms" }
            }
            val index = VideoDiskCache.readStart(url, end.toInt()) ?: return
            videoCodec(index)?.let { codecs[url] = it }
        } catch (e: IOException) {
            Log.d(TAG) { "Could not fetch the index of $url: ${e.message}" }
        }
    }

    /**
     * Reads the codec of a file whose index comes after its media (`ftyp`, `mdat`, `moov`): only the
     * index, from where the media ends to the end of the file, at most [MAX_TRAILING_INDEX_BYTES]. The
     * engine reads that tail early anyway, and finds it on disk when the file is small enough to keep.
     */
    private fun readTrailingIndex(
        url: String,
        head: ByteArray,
    ) {
        val at = startOfTrailingIndex(head) ?: return
        val total = VideoDiskCache.heldStart(url)?.second ?: return
        if (at >= total || total - at > MAX_TRAILING_INDEX_BYTES) return
        val tail = ByteArrayOutputStream()
        if (!fetch(url, at, total - 1, tail)) return
        videoCodec(tail.toByteArray())?.let { codecs[url] = it }
    }

    // Fetches bytes [first]..[last] of [url] into the disk cache, and into [copy] when given; false
    // when the host won't give a range.
    private fun fetch(
        url: String,
        first: Long,
        last: Long,
        copy: OutputStream? = null,
    ): Boolean {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Range", "bytes=$first-$last")
                .build()
        MediaHttp.client(url).newCall(request).execute().use { response ->
            if (response.code != 206) return false
            // A big file keeps only its head on disk: its tail is read into [copy] alone.
            val cache = VideoDiskCache.writerFor(url, response)
            if (cache == null && copy == null) return false
            try {
                response.body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        cache?.write(buf, n)
                        copy?.write(buf, 0, n)
                    }
                }
            } finally {
                cache?.close()
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

    /**
     * Where the box after the media (`mdat`) starts in the MP4 starting with [head], when the media
     * comes before any index; null otherwise, or when [head] isn't an MP4.
     */
    internal fun startOfTrailingIndex(head: ByteArray): Long? {
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
            if (size < header) return null
            when (boxType(head, i + 4)) {
                "moov" -> return null
                "mdat" -> return at + size
            }
            at += size
        }
        return null
    }

    /**
     * The sample entry type of the first video track in the MP4 whose start, through its `moov`, is
     * [file]: `moov` > `trak` (with an `hdlr` of `vide`) > `mdia` > `minf` > `stbl` > `stsd`.
     */
    internal fun videoCodec(file: ByteArray): String? {
        val moov = children(file, 0, file.size).firstOrNull { it.type == "moov" } ?: return null
        for (trak in children(file, moov.body, moov.end).filter { it.type == "trak" }) {
            val mdia = children(file, trak.body, trak.end).firstOrNull { it.type == "mdia" } ?: continue
            val mdiaBoxes = children(file, mdia.body, mdia.end)
            // hdlr: version and flags, pre_defined, then the handler type.
            val hdlr = mdiaBoxes.firstOrNull { it.type == "hdlr" } ?: continue
            if (hdlr.body + 12 > hdlr.end || boxType(file, hdlr.body + 8) != "vide") continue
            val minf = mdiaBoxes.firstOrNull { it.type == "minf" } ?: continue
            val stbl = children(file, minf.body, minf.end).firstOrNull { it.type == "stbl" } ?: continue
            val stsd = children(file, stbl.body, stbl.end).firstOrNull { it.type == "stsd" } ?: continue
            // stsd: version and flags, the entry count, then the entries, each a box named by its codec.
            val entry = stsd.body + 8
            if (entry + 8 > stsd.end) continue
            return boxType(file, entry + 4)
        }
        return null
    }

    private class Box(
        val type: String,
        val body: Int,
        val end: Int,
    )

    // The boxes laid end to end in [from]..[until] of [b]; stops at the first that doesn't fit.
    private fun children(
        b: ByteArray,
        from: Int,
        until: Int,
    ): List<Box> {
        val boxes = ArrayList<Box>()
        var at = from
        while (at + 8 <= until) {
            var size = u32(b, at)
            var header = 8
            if (size == 1L) {
                if (at + 16 > until) break
                size = u64(b, at + 8)
                header = 16
            }
            if (size < header || at + size > until) break
            boxes += Box(boxType(b, at + 4), at + header, (at + size).toInt())
            at += size.toInt()
        }
        return boxes
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
