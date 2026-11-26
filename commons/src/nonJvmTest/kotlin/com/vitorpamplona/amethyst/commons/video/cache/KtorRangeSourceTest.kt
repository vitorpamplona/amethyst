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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [KtorRangeSource] under [CachedRangeReader], against a mock server: no network. */
class KtorRangeSourceTest {
    private val dir: Path = platformTempDirectory / "KtorRangeSourceTest-${Random.nextLong().toULong()}"
    private val url = "https://media.example/abc.mp4"
    private val file = ByteArray(200_000) { (it * 13 + 1).toByte() }

    @AfterTest
    fun cleanUp() {
        platformFileSystem.deleteRecursivelyQuietly(dir)
    }

    private val ranges = mutableListOf<String?>()
    private val encodings = mutableListOf<String?>()

    private fun reader(
        honorsRanges: Boolean = true,
        contentType: String = "video/mp4",
    ): CachedRangeReader {
        val engine =
            MockEngine { request ->
                val range = request.headers[HttpHeaders.Range]
                ranges += range
                encodings += request.headers[HttpHeaders.AcceptEncoding]
                val spec = range?.removePrefix("bytes=")?.split('-')
                val start = spec?.getOrNull(0)?.toLongOrNull()
                if (!honorsRanges || start == null) {
                    respond(file, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf(contentType), HttpHeaders.ContentLength to listOf(file.size.toString())))
                } else {
                    val end = spec.getOrNull(1)?.toLongOrNull()?.let { minOf(it + 1, file.size.toLong()) } ?: file.size.toLong()
                    respond(
                        file.copyOfRange(start.toInt(), end.toInt()),
                        HttpStatusCode.PartialContent,
                        headersOf(
                            HttpHeaders.ContentType to listOf(contentType),
                            HttpHeaders.ContentRange to listOf("bytes $start-${end - 1}/${file.size}"),
                        ),
                    )
                }
            }
        val client = HttpClient(engine)
        return CachedRangeReader(SparseVideoCache(platformFileSystem, dir, 10_000_000), KtorRangeSource { client })
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
    fun asksForTheRangeUncompressedAndCachesIt() =
        runTest {
            val reader = reader()
            assertEquals(CachedRangeReader.Info(file.size.toLong(), "video/mp4"), reader.info(url))
            assertContentEquals(file.copyOfRange(100_000, 150_000), reader.collect(100_000, 150_000))
            assertContentEquals(file, reader.collect(0, null))
            // The length is known after the probe, so even "to the end" asks for a closed range.
            assertEquals<List<String?>>(
                listOf("bytes=0-${CachedRangeReader.PROBE_BYTES - 1}", "bytes=100000-149999", "bytes=${CachedRangeReader.PROBE_BYTES}-99999", "bytes=150000-199999"),
                ranges,
            )
            assertEquals<List<String?>>(listOf("identity"), encodings.distinct())
        }

    @Test
    fun aServerIgnoringRangesStillServesTheMiddle() =
        runTest {
            val reader = reader(honorsRanges = false)
            assertContentEquals(file.copyOfRange(150_000, 160_000), reader.collect(150_000, 160_000))
            assertContentEquals(file.copyOfRange(0, 150_000), reader.collect(0, 150_000))
            assertEquals(1, ranges.size)
        }

    @Test
    fun aPlaylistIsRefused() =
        runTest {
            val reader = reader(contentType = "application/x-mpegURL")
            assertFailsWith<NotCacheableException> { reader.info(url) }
        }
}
