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

import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CachedRangeReaderTest {
    private val dir: Path = platformTempDirectory / "CachedRangeReaderTest-${Random.nextLong().toULong()}"
    private val url = "https://host/video"
    private val file = ByteArray(300_000) { (it * 31 + 7).toByte() }

    @AfterTest
    fun cleanUp() {
        platformFileSystem.deleteRecursivelyQuietly(dir)
    }

    private fun cache() = SparseVideoCache(platformFileSystem, dir, 10_000_000)

    /** A server holding [file] at [url]. */
    private inner class FakeServer(
        var honorsRanges: Boolean = true,
        var sendsContentRange: Boolean = true,
        var contentType: String? = "application/octet-stream",
        var status: Int? = null,
        val chunk: Int = 7_000,
    ) : RangeSource {
        val requests = mutableListOf<Pair<Long, Long?>>()
        var bytesSent = 0L

        // When set, the body stops after this many bytes and waits for it.
        var pauseAfter: Int? = null
        val paused = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()

        override suspend fun <T> fetch(
            url: String,
            start: Long,
            endExclusive: Long?,
            block: suspend (RangeResponseHead, RangeBody) -> T,
        ): T {
            requests += start to endExclusive
            status?.let { return block(RangeResponseHead(it), RangeBody { -1 }) }
            val total = file.size.toLong()
            if (honorsRanges && start >= total) return block(RangeResponseHead(416, "bytes */$total"), RangeBody { -1 })
            val from = if (honorsRanges) start else 0L
            val to = if (honorsRanges && endExclusive != null) minOf(endExclusive, total) else total
            val head =
                if (honorsRanges) {
                    RangeResponseHead(206, if (sendsContentRange) "bytes $from-${to - 1}/$total" else null, to - from, contentType)
                } else {
                    RangeResponseHead(200, null, total, contentType)
                }
            var position = from
            var sentHere = 0
            return block(head) { buffer ->
                pauseAfter?.let {
                    if (sentHere >= it) {
                        paused.complete(Unit)
                        resume.await()
                    }
                }
                if (position >= to) return@block -1
                val n = minOf(chunk.toLong(), to - position, buffer.size.toLong()).toInt()
                file.copyInto(buffer, 0, position.toInt(), position.toInt() + n)
                position += n
                sentHere += n
                bytesSent += n
                n
            }
        }
    }

    private suspend fun CachedRangeReader.collect(
        start: Long,
        end: Long?,
    ): ByteArray {
        val out = mutableListOf<Byte>()
        read(url, start, end) { bytes, offset, count -> for (i in offset until offset + count) out += bytes[i] }
        return out.toByteArray()
    }

    @Test
    fun readsTheRangeAndThenServesItFromDisk() =
        runTest {
            val server = FakeServer()
            val reader = CachedRangeReader(cache(), server)
            assertContentEquals(file.copyOfRange(1000, 50_000), reader.collect(1000, 50_000))
            assertEquals(listOf<Pair<Long, Long?>>(1000L to 50_000L), server.requests)

            server.requests.clear()
            assertContentEquals(file.copyOfRange(2000, 40_000), reader.collect(2000, 40_000))
            assertEquals(emptyList<Pair<Long, Long?>>(), server.requests)
        }

    @Test
    fun onlyTheGapsGoToTheNetwork() =
        runTest {
            val server = FakeServer()
            val reader = CachedRangeReader(cache(), server)
            reader.collect(0, 10_000)
            reader.collect(20_000, 30_000)
            server.requests.clear()
            // To the end of the file, whose length the first answer gave.
            assertContentEquals(file, reader.collect(0, null))
            assertEquals(listOf<Pair<Long, Long?>>(10_000L to 20_000L, 30_000L to file.size.toLong()), server.requests)
        }

    @Test
    fun aServerIgnoringRangesStillWorksAndItsBytesAreKept() =
        runTest {
            val server = FakeServer(honorsRanges = false)
            val reader = CachedRangeReader(cache(), server)
            assertContentEquals(file.copyOfRange(100_000, 120_000), reader.collect(100_000, 120_000))
            // The bytes before the wanted offset came too, and are stored.
            server.requests.clear()
            assertContentEquals(file.copyOfRange(0, 120_000), reader.collect(0, 120_000))
            assertEquals(emptyList<Pair<Long, Long?>>(), server.requests)
            assertEquals(file.size.toLong(), reader.info(url).length)
        }

    @Test
    fun withoutContentRangeTheEndOfAnOpenRequestIsTheLength() =
        runTest {
            val server = FakeServer(sendsContentRange = false)
            val cache = cache()
            val reader = CachedRangeReader(cache, server)
            reader.collect(0, 1000)
            assertEquals(null, cache.length(url))
            assertContentEquals(file, reader.collect(0, null))
            assertEquals(file.size.toLong(), cache.length(url))
        }

    @Test
    fun infoProbesTheHeadAndKeepsIt() =
        runTest {
            val server = FakeServer(contentType = "video/quicktime")
            val reader = CachedRangeReader(cache(), server)
            assertEquals(CachedRangeReader.Info(file.size.toLong(), "video/quicktime"), reader.info(url))
            assertEquals(listOf<Pair<Long, Long?>>(0L to CachedRangeReader.PROBE_BYTES), server.requests)
            server.requests.clear()
            // Known now: no request, and the probed head is read from disk.
            reader.info(url)
            reader.collect(0, 1000)
            assertEquals(emptyList<Pair<Long, Long?>>(), server.requests)
        }

    @Test
    fun aPlaylistIsNotCacheable() =
        runTest {
            val reader = CachedRangeReader(cache(), FakeServer(contentType = "application/vnd.apple.mpegurl"))
            assertFailsWith<NotCacheableException> { reader.info(url) }
        }

    @Test
    fun anUnknownLengthIsNotCacheable() =
        runTest {
            val reader = CachedRangeReader(cache(), FakeServer(sendsContentRange = false))
            assertFailsWith<NotCacheableException> { reader.info(url) }
        }

    @Test
    fun httpErrorsSurface() =
        runTest {
            val reader = CachedRangeReader(cache(), FakeServer(status = 404))
            val error = assertFailsWith<VideoHttpException> { reader.collect(0, 10) }
            assertEquals(404, error.status)
        }

    @Test
    fun readingPastTheEndStopsAtTheEnd() =
        runTest {
            val server = FakeServer()
            val reader = CachedRangeReader(cache(), server)
            assertContentEquals(file.copyOfRange(290_000, file.size), reader.collect(290_000, 400_000))
            assertEquals(0, reader.collect(file.size.toLong(), null).size)
        }

    @Test
    fun aFillWithoutEmitSkipsStoredRuns() =
        runTest {
            val server = FakeServer()
            val reader = CachedRangeReader(cache(), server)
            reader.read(url, 0, 50_000, null)
            reader.read(url, 0, 100_000, null)
            assertEquals(listOf<Pair<Long, Long?>>(0L to 50_000L, 50_000L to 100_000L), server.requests)
            assertEquals(100_000L, server.bytesSent)
        }

    @Test
    fun aCancelledReadKeepsWhatArrived() =
        runTest {
            val server = FakeServer(chunk = 1000).apply { pauseAfter = 5000 }
            val cache = cache()
            val reader = CachedRangeReader(cache, server)
            val job = launch { reader.collect(0, 100_000) }
            server.paused.await()
            job.cancel()
            job.join()
            assertTrue(cache.covers(url, 0, 5000))
            assertTrue(!cache.covers(url, 0, 5001))
        }
}
