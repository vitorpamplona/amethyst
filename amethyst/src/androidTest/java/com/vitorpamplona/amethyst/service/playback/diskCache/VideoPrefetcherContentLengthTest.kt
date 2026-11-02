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
package com.vitorpamplona.amethyst.service.playback.diskCache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/**
 * An mp4 may end in an `mdat` box of size 0, meaning "to the end of the file". media3's sniffer can
 * only size that box when it knows how long the file is, and without a length no extractor accepts
 * the stream: the viewer gets ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED for a video every browser
 * plays. So a prefetched head has to leave the file's length in the cache, or the player that opens
 * it reads a stream of unknown length.
 *
 * Runs on a real [androidx.media3.datasource.cache.SimpleCache] and a real [OkHttpDataSource] over a
 * canned OkHttp response, because the bugs here live in how those two hand the length over.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoPrefetcherContentLengthTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val video = openEndedMp4(1_000_000)

    private lateinit var cacheDir: File
    private lateinit var videoCache: VideoCache
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        cacheDir = File(context.cacheDir, "prefetcher-test-${UUID.randomUUID()}")
        videoCache = VideoCache().apply { initFileCache(context, cacheDir) }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        videoCache.simpleCache.release()
        cacheDir.deleteRecursively()
    }

    @Test
    fun aPrefetchedHeadKeepsAnOpenEndedMp4Playable() {
        val server = hostServing(video, honoursRange = true)

        prefetchHead(server)

        val opened = playerOpens(server)
        assertEquals(video.size.toLong(), opened.length)
        assertTrue("the mp4 extractor must accept the stream", opened.mp4ExtractorAccepts)
    }

    @Test
    fun theLengthIsKnownWhenTheServerIgnoresTheRangeRequest() {
        val server = hostServing(video, honoursRange = false)

        prefetchHead(server)

        assertEquals(video.size.toLong(), playerOpens(server).length)
    }

    private fun prefetchHead(server: DataSource.Factory) {
        VideoPrefetcher({ videoCache }, { server }, scope).prefetch(this, listOf(VideoPrefetcher.Video(URL, useProxy = false)))

        runBlocking {
            withTimeout(10_000) {
                while (videoCache.simpleCache.getCachedBytes(URL, 0, VideoPrefetcher.PREFETCH_BYTES) < VideoPrefetcher.PREFETCH_BYTES) {
                    delay(20)
                }
            }
        }
    }

    private class Opened(
        val length: Long,
        val mp4ExtractorAccepts: Boolean,
    )

    // What the player does with the cached head: the same cache route, an open with no length, then
    // sniffing the stream at whatever length that open reported.
    private fun playerOpens(server: DataSource.Factory): Opened {
        val source =
            CacheDataSource
                .Factory()
                .setCache(videoCache.simpleCache)
                .setUpstreamDataSourceFactory(server)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()

        try {
            val length = source.open(DataSpec.Builder().setUri(URL).build())
            return Opened(length, Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED).sniff(DefaultExtractorInput(source, 0, length)))
        } finally {
            source.close()
        }
    }

    private fun hostServing(
        body: ByteArray,
        honoursRange: Boolean,
    ): DataSource.Factory {
        val client: Call.Factory =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val range = if (honoursRange) RANGE.find(chain.request().header("Range").orEmpty()) else null
                    if (range == null) {
                        respond(chain.request(), 200, body)
                    } else {
                        val start = range.groupValues[1].toInt()
                        val end = range.groupValues[2].toIntOrNull()?.coerceAtMost(body.lastIndex) ?: body.lastIndex
                        respond(chain.request(), 206, body.copyOfRange(start, end + 1)) {
                            header("Content-Range", "bytes $start-$end/${body.size}")
                        }
                    }
                }.build()
        return OkHttpDataSource.Factory(client)
    }

    private fun respond(
        request: Request,
        code: Int,
        bytes: ByteArray,
        extra: Response.Builder.() -> Unit = {},
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
            .header("Content-Length", bytes.size.toString())
            .apply(extra)
            .body(bytes.toResponseBody("video/mp4".toMediaType()))
            .build()

    private companion object {
        const val URL = "https://media.example/4529b13894e51742e137dff7a4db703890cd0a487fa2bd2c25ecae1d99c61be4.mp4"
        val RANGE = Regex("bytes=(\\d+)-(\\d*)")

        // ftyp, an empty moov, and an `mdat` of size 0 running to the end of [size] bytes.
        fun openEndedMp4(size: Int): ByteArray =
            ByteBuffer
                .allocate(size)
                .putInt(24)
                .put("ftypmp42".toByteArray())
                .putInt(0)
                .put("isommp42".toByteArray())
                .putInt(8)
                .put("moov".toByteArray())
                .putInt(0)
                .put("mdat".toByteArray())
                .array()
    }
}
