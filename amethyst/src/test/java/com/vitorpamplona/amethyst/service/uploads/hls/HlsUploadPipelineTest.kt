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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HlsUploadPipelineTest {
    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("hls-pipeline-test").toFile()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    private class FakeUploader : HlsBlobUploader {
        data class Call(
            val fileName: String,
            val contentType: String,
            val content: String,
        )

        val calls = mutableListOf<Call>()

        override suspend fun upload(
            file: File,
            contentType: String,
        ): MediaUploadResult {
            val content = file.readText()
            calls += Call(file.name, contentType, content)
            val url = "https://cdn.test/${calls.size}-${file.name}"
            return MediaUploadResult(url = url, sha256 = "sha-${calls.size}", size = file.length())
        }
    }

    private class BareUrlUploader : HlsBlobUploader {
        val calls = mutableListOf<Triple<String, String, String>>()

        override suspend fun upload(
            file: File,
            contentType: String,
        ): MediaUploadResult {
            val content = file.readText()
            calls += Triple(file.name, contentType, content)
            return MediaUploadResult(url = "https://blossom.test/bare-${calls.size}", sha256 = "sha-${calls.size}", size = file.length())
        }
    }

    private fun createBundle(labels: List<String>): HlsBundle {
        val renditions =
            labels.map { label ->
                val combined = File(workDir, "$label.mp4").apply { writeText("bytes-$label") }
                val mediaPlaylist =
                    """
                    #EXTM3U
                    #EXT-X-VERSION:7
                    #EXT-X-MAP:URI="$label.mp4",BYTERANGE="1000@0"

                    #EXTINF:6.000,
                    #EXT-X-BYTERANGE:500000@1000
                    $label.mp4
                    #EXT-X-ENDLIST
                    """.trimIndent()
                HlsBundleRendition(
                    label = label,
                    combinedFile = combined,
                    mediaPlaylist = mediaPlaylist,
                    bitrateKbps = 500 + labels.indexOf(label) * 1000,
                )
            }

        val masterLines =
            buildList {
                add("#EXTM3U")
                add("#EXT-X-VERSION:7")
                renditions.forEach {
                    add("#EXT-X-STREAM-INF:BANDWIDTH=${it.bitrateKbps * 1000}")
                    add("${it.label}/media.m3u8")
                }
            }

        return HlsBundle(
            workDir = workDir,
            masterPlaylist = masterLines.joinToString("\n"),
            renditions = renditions,
        )
    }

    @Test
    fun uploadsCombinedThenMediaThenMasterInOrder() {
        val bundle = createBundle(listOf("360p"))
        val uploader = FakeUploader()
        val pipeline = HlsUploadPipeline(uploader)

        runBlocking { pipeline.upload(bundle) }

        assertEquals(3, uploader.calls.size)
        assertEquals("360p.mp4", uploader.calls[0].fileName)
        assertEquals("video/mp4", uploader.calls[0].contentType)
        assertTrue(uploader.calls[1].fileName.endsWith(".m3u8"))
        assertEquals("application/vnd.apple.mpegurl", uploader.calls[1].contentType)
        assertEquals("application/vnd.apple.mpegurl", uploader.calls[2].contentType)
    }

    @Test
    fun mediaPlaylistIsRewrittenWithUploadedCombinedUrl() {
        val bundle = createBundle(listOf("360p"))
        val uploader = FakeUploader()
        val pipeline = HlsUploadPipeline(uploader)

        runBlocking { pipeline.upload(bundle) }

        val combinedUrl = "https://cdn.test/1-360p.mp4"
        val uploadedMediaPlaylist = uploader.calls[1].content
        assertTrue(uploadedMediaPlaylist.contains(combinedUrl))
        // Original filename reference must be gone
        assertTrue(!uploadedMediaPlaylist.lines().any { it.trim() == "360p.mp4" })
        // EXTINF metadata must still be present
        assertTrue(uploadedMediaPlaylist.contains("#EXTINF:6.000,"))
        // BYTERANGE must still be present
        assertTrue(uploadedMediaPlaylist.contains("#EXT-X-BYTERANGE:500000@1000"))
    }

    @Test
    fun masterPlaylistIsRewrittenWithUploadedMediaPlaylistUrls() {
        val bundle = createBundle(listOf("360p", "540p"))
        val uploader = FakeUploader()
        val pipeline = HlsUploadPipeline(uploader)

        runBlocking { pipeline.upload(bundle) }

        // 2 renditions × (combined + media) + 1 master = 5 uploads
        assertEquals(5, uploader.calls.size)
        val masterContent = uploader.calls[4].content

        // The uploaded media playlist URLs should appear in the rewritten master
        val media360Url = uploader.calls[1].content.let { "https://cdn.test/2-" } // 2nd call is 360p media
        // Extract the actual URLs the fake returned for each media playlist upload
        val media360PlaylistUrl = "https://cdn.test/2-" + uploader.calls[1].fileName
        val media540PlaylistUrl = "https://cdn.test/4-" + uploader.calls[3].fileName
        assertTrue("master should contain $media360PlaylistUrl", masterContent.contains(media360PlaylistUrl))
        assertTrue("master should contain $media540PlaylistUrl", masterContent.contains(media540PlaylistUrl))

        // EXT-X-STREAM-INF metadata must survive
        assertTrue(masterContent.contains("#EXT-X-STREAM-INF:BANDWIDTH=500000"))
        assertTrue(masterContent.contains("#EXT-X-STREAM-INF:BANDWIDTH=1500000"))
        // Original rendition filenames must be gone
        assertTrue(!masterContent.lines().any { it.trim() == "360p/media.m3u8" })
        assertTrue(!masterContent.lines().any { it.trim() == "540p/media.m3u8" })
    }

    @Test
    fun bareServerUrlsPassThroughVerbatim() {
        // Policy: the pipeline uses whatever URL the server returned, unchanged.
        // Even a bare-hash URL with no extension flows straight into the rewritten
        // playlists. If it does not play, the fix is server-side (return a playable URL).
        val bundle = createBundle(listOf("360p"))
        val uploader = BareUrlUploader()
        val pipeline = HlsUploadPipeline(uploader)

        val result = runBlocking { pipeline.upload(bundle) }

        assertEquals("https://blossom.test/bare-1", result.renditions[0].combinedUrl)
        assertEquals("https://blossom.test/bare-2", result.renditions[0].playlistUrl)
        assertEquals("https://blossom.test/bare-3", result.masterUrl)
        // The rewritten media playlist that the server received must contain the bare url.
        val uploadedMediaPlaylist = uploader.calls[1].third
        assertTrue(
            "media playlist should reference bare url: $uploadedMediaPlaylist",
            uploadedMediaPlaylist.contains("https://blossom.test/bare-1"),
        )
    }

    @Test
    fun serverUrlsAlreadyWithExtensionPassThroughUntouched() {
        // Matches the server-side fix where the NIP-96 plugin returns clean "<hash>.m3u8"
        // URLs. The pipeline must not append a second ".m3u8" on top.
        val bundle = createBundle(listOf("360p"))
        val uploader =
            object : HlsBlobUploader {
                var count = 0

                override suspend fun upload(
                    file: File,
                    contentType: String,
                ): MediaUploadResult {
                    count++
                    val ext =
                        when (contentType) {
                            HlsUploadPipeline.CONTENT_TYPE_VIDEO_MP4 -> "mp4"
                            HlsUploadPipeline.CONTENT_TYPE_HLS -> "m3u8"
                            else -> "bin"
                        }
                    return MediaUploadResult(
                        url = "https://server.test/hash-$count.$ext",
                        sha256 = "sha-$count",
                        size = file.length(),
                    )
                }
            }
        val pipeline = HlsUploadPipeline(uploader)

        val result = runBlocking { pipeline.upload(bundle) }

        assertEquals("https://server.test/hash-1.mp4", result.renditions[0].combinedUrl)
        assertEquals("https://server.test/hash-2.m3u8", result.renditions[0].playlistUrl)
        assertEquals("https://server.test/hash-3.m3u8", result.masterUrl)
        // And crucially, no double-extension anywhere:
        assertTrue(!result.masterUrl.contains(".m3u8.m3u8"))
        assertTrue(!result.renditions[0].combinedUrl.contains(".mp4.mp4"))
        assertTrue(!result.renditions[0].playlistUrl.contains(".m3u8.m3u8"))
    }

    @Test
    fun reportsUploadProgressWithCurrentLabelBeforeEachStep() {
        val bundle = createBundle(listOf("360p", "540p"))
        val uploader = FakeUploader()
        val pipeline = HlsUploadPipeline(uploader)
        val observed = mutableListOf<Triple<Int, Int, String>>()

        runBlocking {
            pipeline.upload(bundle) { done, total, label ->
                observed += Triple(done, total, label)
            }
        }

        // 2 renditions × 2 + 1 master = 5 uploads. Label is emitted BEFORE each upload with
        // the done-count of previously-completed uploads, so the UI can show "Uploading 360p
        // video (0 / 5)" while the first file is actually in flight. A trailing (5, 5, "")
        // marks the final completion.
        assertEquals(
            listOf(
                Triple(0, 5, "360p video"),
                Triple(1, 5, "360p playlist"),
                Triple(2, 5, "540p video"),
                Triple(3, 5, "540p playlist"),
                Triple(4, 5, "master playlist"),
                Triple(5, 5, ""),
            ),
            observed,
        )
    }

    @Test
    fun resultExposesMasterUrlAndPerRenditionDetails() {
        val bundle = createBundle(listOf("360p", "540p"))
        val uploader = FakeUploader()
        val pipeline = HlsUploadPipeline(uploader)

        val result = runBlocking { pipeline.upload(bundle) }

        // Master was the 5th upload
        assertEquals("https://cdn.test/5-master.m3u8", result.masterUrl)
        assertEquals("sha-5", result.masterSha256)

        assertEquals(2, result.renditions.size)
        val r360 = result.renditions[0]
        assertEquals("360p", r360.label)
        assertEquals("https://cdn.test/1-360p.mp4", r360.combinedUrl)
        assertEquals("sha-1", r360.combinedSha256)
        assertEquals(500, r360.bitrateKbps)

        val r540 = result.renditions[1]
        assertEquals("540p", r540.label)
        assertEquals("https://cdn.test/3-540p.mp4", r540.combinedUrl)
        assertEquals(1500, r540.bitrateKbps)
    }
}
