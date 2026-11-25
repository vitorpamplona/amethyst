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

/**
 * The network side of [CachedRangeReader]: one HTTP GET of a byte range. Each platform implements
 * it with its own client (Ktor's Darwin engine on iOS).
 */
interface RangeSource {
    /**
     * Asks for [url]'s bytes `[start, endExclusive)` (to the end of the file when [endExclusive] is
     * null) with a `Range` header, and hands the response to [block] while its body is still open.
     * The body is closed when [block] returns, so a reader that stops early stops the download.
     */
    suspend fun <T> fetch(
        url: String,
        start: Long,
        endExclusive: Long?,
        block: suspend (RangeResponseHead, RangeBody) -> T,
    ): T
}

/** A response body, read in chunks. */
fun interface RangeBody {
    /** Fills the start of [buffer]; the count, or -1 once the body has ended. */
    suspend fun read(buffer: ByteArray): Int
}

/** The parts of an HTTP response a range read needs. */
data class RangeResponseHead(
    val status: Int,
    val contentRange: String? = null,
    val contentLength: Long? = null,
    val contentType: String? = null,
    val contentEncoding: String? = null,
)

/** What a [RangeResponseHead] means for a request that asked for bytes from some offset. */
sealed class RangeAnswer {
    /** The body holds the file's bytes from [bodyStart]; [fileLength] is the whole file, when known. */
    data class Bytes(
        val bodyStart: Long,
        val fileLength: Long?,
    ) : RangeAnswer()

    /** The request started at or past the end of the file (416); [fileLength] when the server said it. */
    data class PastEnd(
        val fileLength: Long?,
    ) : RangeAnswer()

    /** Anything else: the server refused or failed. */
    data class Failed(
        val status: Int,
    ) : RangeAnswer()

    companion object {
        /**
         * Reads [head] as the answer to a request for bytes from [requestedStart].
         *
         * A 206 says where its body starts and the file's size in `Content-Range`. A 200 is a server
         * that ignored the range: its body is the whole file from byte 0, and its `Content-Length`
         * is the file's length unless the body is compressed. A 206 without `Content-Range` is taken
         * to start where it was asked, its length unknown: a slice's own length stored as the file's
         * would end the video there (the reason Android's prefetcher leaves it unknown too).
         */
        fun of(
            head: RangeResponseHead,
            requestedStart: Long,
        ): RangeAnswer =
            when (head.status) {
                206 -> {
                    val range = head.contentRange?.let { parseContentRange(it) }
                    if (range != null) Bytes(range.first, range.second) else Bytes(requestedStart, null)
                }

                200 -> {
                    val identity = head.contentEncoding.isNullOrBlank() || head.contentEncoding.equals("identity", ignoreCase = true)
                    Bytes(0L, head.contentLength?.takeIf { identity && it >= 0 })
                }

                416 -> PastEnd(head.contentRange?.let { parseUnsatisfiedRange(it) })

                else -> Failed(head.status)
            }

        /** `bytes 100-199/1000` as (100, 1000); a total of `*` (unknown) as (100, null). */
        fun parseContentRange(value: String): Pair<Long, Long?>? {
            val spec = value.trim()
            if (!spec.startsWith("bytes", ignoreCase = true)) return null
            val rest = spec.substring(5).trim()
            val slash = rest.indexOf('/')
            if (slash < 0) return null
            val range = rest.substring(0, slash).trim()
            val total = rest.substring(slash + 1).trim()
            val dash = range.indexOf('-')
            if (dash <= 0) return null
            val start = range.substring(0, dash).trim().toLongOrNull() ?: return null
            val totalLength = if (total == "*") null else total.toLongOrNull() ?: return null
            return start to totalLength
        }

        /** A 416's `Content-Range` (`bytes`, then `*`, a slash and the total) as the total. */
        fun parseUnsatisfiedRange(value: String): Long? = value.substringAfter('/', "").trim().toLongOrNull()
    }
}

/** The server answered with an HTTP status that holds no bytes of the file. */
class VideoHttpException(
    val status: Int,
    url: String,
) : Exception("HTTP $status for $url")

/** A download ended before it brought any byte. */
class VideoFetchException(
    message: String,
) : Exception(message)

/**
 * The URL is not a plain file this cache can serve: the server sent an HLS playlist, or would not
 * say how long the file is (a player needs the length before it reads anything).
 */
class NotCacheableException(
    message: String,
) : Exception(message)
