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

import com.davotoula.lightcompressor.VideoCodec
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.HlsPublishOrchestrator
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.HlsPublishRequest
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.HlsPublishState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HlsPublishOrchestratorTest {
    private lateinit var workDir: File

    private val server = ServerName("Test Blossom", "https://test.example/", ServerType.Blossom)

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("hls-orchestrator-test").toFile()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    private fun fakeBundle(labels: List<String> = listOf("360p")): HlsBundle {
        val renditions =
            labels.map { label ->
                val file = File(workDir, "$label.mp4").apply { writeText("bytes-$label") }
                HlsBundleRendition(
                    label = label,
                    combinedFile = file,
                    mediaPlaylist =
                        """
                        #EXTM3U
                        #EXT-X-MAP:URI="$label.mp4",BYTERANGE="100@0"
                        #EXTINF:6.0,
                        $label.mp4
                        """.trimIndent(),
                    bitrateKbps = 500,
                )
            }
        val master =
            buildString {
                appendLine("#EXTM3U")
                labels.forEachIndexed { i, label ->
                    appendLine("#EXT-X-STREAM-INF:BANDWIDTH=${(i + 1) * 500000},RESOLUTION=${640 + i * 320}x${360 + i * 180}")
                    appendLine("$label/media.m3u8")
                }
            }
        return HlsBundle(workDir, master, renditions)
    }

    private class CannedUploader : HlsBlobUploader {
        var count = 0

        override suspend fun upload(
            file: File,
            contentType: String,
        ): MediaUploadResult {
            count++
            return MediaUploadResult(url = "https://cdn.test/$count", sha256 = "sha-$count", size = file.length())
        }
    }

    private fun newRequest(
        title: String = "My HD Clip",
        description: String = "A test clip",
        sensitive: Boolean = false,
        warningReason: String = "",
    ) = HlsPublishRequest(
        title = title,
        description = description,
        sensitiveContent = sensitive,
        contentWarningReason = warningReason,
        codec = VideoCodec.H265,
        server = server,
    )

    @Test
    fun happyPathEndsInSuccessWithMasterUrlAndEventId() {
        val publishedTemplates = mutableListOf<HlsVideoEventTemplate>()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, _ -> fakeBundle() },
                buildUploader = { CannedUploader() },
                signAndPublish = { tpl ->
                    publishedTemplates += tpl
                    "signed-event-id"
                },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue("expected Success, was $final", final is HlsPublishState.Success)
        final as HlsPublishState.Success
        assertEquals("signed-event-id", final.eventId)
        assertTrue("masterUrl should contain https://cdn.test/", final.masterUrl.startsWith("https://cdn.test/"))

        assertEquals(1, publishedTemplates.size)
        assertTrue(publishedTemplates[0] is HlsVideoEventTemplate.Horizontal)
    }

    @Test
    fun statePhasesVisibleToFakesDuringPublish() {
        // Capture state.value at the point each phase's fake runs — this verifies that the
        // orchestrator has already transitioned into the right state before dispatching the
        // corresponding dep call.
        lateinit var orchestrator: HlsPublishOrchestrator
        val capturedDuringTranscode = mutableListOf<HlsPublishState>()
        val capturedDuringUpload = mutableListOf<HlsPublishState>()
        val capturedDuringPublish = mutableListOf<HlsPublishState>()

        orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, onProgress ->
                    capturedDuringTranscode += orchestrator.state.value
                    onProgress("360p", 42)
                    capturedDuringTranscode += orchestrator.state.value
                    fakeBundle()
                },
                buildUploader = {
                    capturedDuringUpload += orchestrator.state.value
                    CannedUploader()
                },
                signAndPublish = {
                    capturedDuringPublish += orchestrator.state.value
                    "event-id"
                },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        assertTrue(capturedDuringTranscode.all { it is HlsPublishState.Transcoding })
        assertEquals("360p", (capturedDuringTranscode.last() as HlsPublishState.Transcoding).currentLabel)
        assertEquals(42, (capturedDuringTranscode.last() as HlsPublishState.Transcoding).percent)

        assertTrue(capturedDuringUpload.single() is HlsPublishState.Uploading)
        assertTrue(capturedDuringPublish.single() is HlsPublishState.Publishing)

        assertTrue(orchestrator.state.value is HlsPublishState.Success)
    }

    @Test
    fun transcodeExceptionTransitionsToFailure() {
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, _ -> throw RuntimeException("decode failed") },
                buildUploader = { CannedUploader() },
                signAndPublish = { "never" },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue("expected Failure, was $final", final is HlsPublishState.Failure)
        assertEquals("decode failed", (final as HlsPublishState.Failure).message)
    }

    @Test
    fun uploadExceptionTransitionsToFailure() {
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, _ -> fakeBundle() },
                buildUploader = {
                    HlsBlobUploader { _, _ -> throw RuntimeException("server 500") }
                },
                signAndPublish = { "never" },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue(final is HlsPublishState.Failure)
        assertEquals("server 500", (final as HlsPublishState.Failure).message)
    }

    @Test
    fun publishExceptionTransitionsToFailure() {
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, _ -> fakeBundle() },
                buildUploader = { CannedUploader() },
                signAndPublish = { throw RuntimeException("relay rejected") },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue(final is HlsPublishState.Failure)
        assertEquals("relay rejected", (final as HlsPublishState.Failure).message)
    }

    @Test
    fun sensitiveContentPassesContentWarningIntoTemplate() {
        val captured = mutableListOf<HlsVideoEventTemplate>()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, _ -> fakeBundle() },
                buildUploader = { CannedUploader() },
                signAndPublish = { tpl ->
                    captured += tpl
                    "event-id"
                },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking {
            orchestrator.publish(newRequest(sensitive = true, warningReason = "NSFW"))
        }

        val template = (captured.single() as HlsVideoEventTemplate.Horizontal).template
        val cw = template.tags.firstOrNull { it.isNotEmpty() && it[0] == "content-warning" }
        assertNotNull(cw)
        assertEquals("NSFW", cw!![1])
    }

    @Test
    fun portraitBundleProducesVerticalTemplate() {
        val portraitMaster =
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=360x640
            360p/media.m3u8
            """.trimIndent()
        val rendition =
            HlsBundleRendition(
                label = "360p",
                combinedFile = File(workDir, "360p.mp4").apply { writeText("bytes") },
                mediaPlaylist = "#EXTM3U\n#EXT-X-MAP:URI=\"360p.mp4\"\n#EXTINF:6.0,\n360p.mp4\n",
                bitrateKbps = 500,
            )
        val captured = mutableListOf<HlsVideoEventTemplate>()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, _ -> HlsBundle(workDir, portraitMaster, listOf(rendition)) },
                buildUploader = { CannedUploader() },
                signAndPublish = { tpl ->
                    captured += tpl
                    "event-id"
                },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        assertTrue(captured.single() is HlsVideoEventTemplate.Vertical)
    }

    @Test
    fun resetRestoresIdleState() {
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runTranscode = { _, _, _ -> throw RuntimeException("boom") },
                buildUploader = { CannedUploader() },
                signAndPublish = { "never" },
                workDirFactory = { File(workDir, "work").apply { mkdirs() } },
            )

        runBlocking { orchestrator.publish(newRequest()) }
        assertTrue(orchestrator.state.value is HlsPublishState.Failure)

        orchestrator.reset()
        assertEquals(HlsPublishState.Idle, orchestrator.state.value)
    }
}
