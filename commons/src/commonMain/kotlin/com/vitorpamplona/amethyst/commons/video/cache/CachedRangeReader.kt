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
package com.vitorpamplona.amethyst.commons.video.cache

import com.vitorpamplona.amethyst.commons.video.HLS_MIME_TYPE
import com.vitorpamplona.amethyst.commons.video.normalizeStreamMimeType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Reads byte ranges of a video file through [cache]: the stored runs come from disk, each gap is
 * one HTTP range request to [source], and what arrives is stored as it streams by. A player's
 * request and a prefetch of the same file share the bytes, wherever on the file they fall.
 *
 * A server that ignores `Range` (200, the whole file from byte 0) still works: the bytes before the
 * wanted offset are stored and skipped. HLS is refused ([NotCacheableException]): its playlist
 * names other files, and a live one changes.
 */
class CachedRangeReader(
    private val cache: SparseVideoCache,
    private val source: RangeSource,
) {
    /** A file's length and the content type its server sent, if any. */
    data class Info(
        val length: Long,
        val mimeType: String?,
    )

    /**
     * [url]'s length and type: from the index, or from a small download of the first bytes not
     * stored yet (they are kept, so a player that asks for them next reads them from disk).
     *
     * @throws NotCacheableException when the server sends a playlist or will not say the length.
     */
    suspend fun info(url: String): Info {
        cache.acquire(url)
        try {
            cache.length(url)?.let { return Info(it, cache.mimeType(url)) }
            val probeStart = cache.runEnd(url, 0)
            val probeEnd = minOfKnown(cache.nextCachedStart(url, probeStart), probeStart + PROBE_BYTES)
            download(url, probeStart, probeEnd, null)
            val length = cache.length(url) ?: throw NotCacheableException("$url did not say its length")
            return Info(length, cache.mimeType(url))
        } finally {
            cache.release(url)
        }
    }

    /**
     * Delivers [url]'s bytes `[start, endExclusive)` (to the end of the file when [endExclusive] is
     * null) to [emit], in order and without gaps. [emit] gets a buffer, an offset in it and a count;
     * the buffer is reused, so copy what you keep. A null [emit] only fills the cache: stored runs
     * are skipped without reading them.
     */
    suspend fun read(
        url: String,
        start: Long,
        endExclusive: Long?,
        emit: (suspend (ByteArray, Int, Int) -> Unit)?,
    ) {
        cache.acquire(url)
        try {
            var offset = start
            while (true) {
                currentCoroutineContext().ensureActive()
                val end = minOfKnown(endExclusive, cache.length(url))
                if (end != null && offset >= end) return
                val runEnd = cache.runEnd(url, offset)
                val next =
                    if (runEnd > offset) {
                        val stop = if (end != null) minOf(runEnd, end) else runEnd
                        if (emit == null) stop else readStored(url, offset, stop, emit)
                    } else {
                        download(url, offset, minOfKnown(cache.nextCachedStart(url, offset), end), emit)
                    }
                if (next <= offset) {
                    // The stream ended where the file does (now known), or before it gave a byte.
                    if (cache.length(url)?.let { offset >= it } == true) return
                    throw VideoFetchException("No bytes of $url from $offset")
                }
                offset = next
            }
        } finally {
            cache.release(url)
        }
    }

    /** Emits the stored bytes `[offset, stop)`; where the disk fails, downloads the rest of the run. */
    private suspend fun readStored(
        url: String,
        offset: Long,
        stop: Long,
        emit: suspend (ByteArray, Int, Int) -> Unit,
    ): Long {
        var position = offset
        while (position < stop) {
            val chunk = cache.read(url, position, minOf(CHUNK_BYTES.toLong(), stop - position).toInt())
            if (chunk.isEmpty()) return download(url, position, stop, emit)
            emit(chunk, 0, chunk.size)
            position += chunk.size
        }
        return position
    }

    /**
     * Downloads `[from, to)` (to the end when [to] is null), storing it and emitting it from [from].
     * Returns the offset the download reached.
     */
    private suspend fun download(
        url: String,
        from: Long,
        to: Long?,
        emit: (suspend (ByteArray, Int, Int) -> Unit)?,
    ): Long =
        source.fetch(url, from, to) { head, body ->
            val mimeType =
                head.contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            if (mimeType != null && normalizeStreamMimeType(mimeType) == HLS_MIME_TYPE) throw NotCacheableException("$url is an HLS playlist")
            when (val answer = RangeAnswer.of(head, from)) {
                is RangeAnswer.Failed -> {
                    throw VideoHttpException(answer.status, url)
                }

                is RangeAnswer.PastEnd -> {
                    // Asked from the end of the file or beyond it: the file ends at or before [from].
                    cache.setInfo(url, answer.fileLength ?: from, mimeType)
                    from
                }

                is RangeAnswer.Bytes -> {
                    if (answer.bodyStart > from) throw VideoFetchException("$url answered from ${answer.bodyStart}, not $from")
                    cache.setInfo(url, answer.fileLength, mimeType)
                    stream(url, from, to, answer, body, emit)
                }
            }
        }

    private suspend fun stream(
        url: String,
        from: Long,
        to: Long?,
        answer: RangeAnswer.Bytes,
        body: RangeBody,
        emit: (suspend (ByteArray, Int, Int) -> Unit)?,
    ): Long {
        val buffer = ByteArray(CHUNK_BYTES)
        var position = answer.bodyStart
        while (to == null || position < to) {
            val n = body.read(buffer)
            if (n < 0) {
                // The body ended. Asked to the end of the file, its end is the file's when no header said so.
                if (answer.fileLength == null && to == null) cache.setInfo(url, position, null)
                break
            }
            if (n == 0) continue
            val count = if (to != null) minOf(n.toLong(), to - position).toInt() else n
            cache.write(url, position, buffer, count)
            if (emit != null && position + count > from) {
                val skip = (from - position).coerceAtLeast(0L).toInt()
                emit(buffer, skip, count - skip)
            }
            position += count
        }
        return maxOf(position, from)
    }

    companion object {
        // Read and emitted at a time. AVFoundation takes data in pieces of about this size.
        const val CHUNK_BYTES = 64 * 1024

        // What [info] downloads to learn a file's length; also the head a player reads first.
        const val PROBE_BYTES = 16L * 1024

        private fun minOfKnown(
            a: Long?,
            b: Long?,
        ): Long? =
            when {
                a == null -> b
                b == null -> a
                else -> minOf(a, b)
            }
    }
}
