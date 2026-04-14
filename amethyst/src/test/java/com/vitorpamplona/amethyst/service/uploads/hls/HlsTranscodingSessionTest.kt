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

import com.davotoula.lightcompressor.Resolution
import com.davotoula.lightcompressor.hls.HlsError
import com.davotoula.lightcompressor.hls.HlsSegment
import com.davotoula.lightcompressor.hls.Rendition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class HlsTranscodingSessionTest {
    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("hls-session-test").toFile()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    private fun rendition360p() = Rendition(Resolution.SD_360, 500)

    private fun rendition540p() = Rendition(Resolution.SD_540, 1200)

    private fun fakeCombinedSegment(payload: String = "fake-mp4-bytes"): HlsSegment {
        val temp = Files.createTempFile("hls-seg", ".mp4").toFile()
        temp.writeText(payload)
        return HlsSegment(
            file = temp,
            index = 0,
            durationSeconds = 6.0,
            isInitSegment = false,
            isCombinedRendition = true,
        )
    }

    private fun driveHappyPath(
        session: HlsTranscodingSession,
        rendition: Rendition,
        playlist: String,
        segmentPayload: String = "fake-mp4-bytes",
    ) {
        session.onStart(1)
        session.onRenditionStart(rendition)
        session.onSegmentReady(rendition, fakeCombinedSegment(segmentPayload))
        session.onRenditionComplete(rendition, playlist)
    }

    @Test
    fun onCompleteEmitsHlsBundleWithMasterPlaylist() {
        val session = HlsTranscodingSession(workDir)
        val rendition = rendition360p()
        val mediaPlaylist = "#EXTM3U\n#EXT-X-MAP:URI=\"360p.mp4\"\n"
        val masterPlaylist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000\n360p/media.m3u8\n"

        driveHappyPath(session, rendition, mediaPlaylist)
        session.onComplete(masterPlaylist)

        val bundle = session.terminal.getCompleted()
        assertEquals(masterPlaylist, bundle.masterPlaylist)
        assertEquals(1, bundle.renditions.size)
        assertEquals("360p", bundle.renditions[0].label)
        assertEquals(mediaPlaylist, bundle.renditions[0].mediaPlaylist)
        assertEquals(500, bundle.renditions[0].bitrateKbps)
    }

    @Test
    fun onSegmentReadyRenamesCombinedFileToWorkDir() {
        val session = HlsTranscodingSession(workDir)
        val rendition = rendition360p()
        val segment = fakeCombinedSegment(payload = "payload-360p")
        val originalPath = segment.file.absolutePath

        session.onStart(1)
        session.onRenditionStart(rendition)
        session.onSegmentReady(rendition, segment)
        session.onRenditionComplete(rendition, "#EXTM3U\n")
        session.onComplete("#EXTM3U\n")

        val bundle = session.terminal.getCompleted()
        val combined = bundle.renditions[0].combinedFile

        assertEquals(File(workDir, "360p.mp4"), combined)
        assertTrue(combined.exists())
        assertEquals("payload-360p", combined.readText())
        assertFalse(File(originalPath).exists())
    }

    @Test
    fun happyPathWithTwoRenditionsProducesBundleWithBoth() {
        val session = HlsTranscodingSession(workDir)

        session.onStart(2)
        session.onRenditionStart(rendition360p())
        session.onSegmentReady(rendition360p(), fakeCombinedSegment("p360"))
        session.onRenditionComplete(rendition360p(), "p360-playlist")

        session.onRenditionStart(rendition540p())
        session.onSegmentReady(rendition540p(), fakeCombinedSegment("p540"))
        session.onRenditionComplete(rendition540p(), "p540-playlist")

        session.onComplete("master-playlist")

        val bundle = session.terminal.getCompleted()
        assertEquals(2, bundle.renditions.size)
        assertEquals(listOf("360p", "540p"), bundle.renditions.map { it.label })
        assertEquals("p360-playlist", bundle.renditions[0].mediaPlaylist)
        assertEquals("p540-playlist", bundle.renditions[1].mediaPlaylist)
        assertEquals("p360", bundle.renditions[0].combinedFile.readText())
        assertEquals("p540", bundle.renditions[1].combinedFile.readText())
    }

    @Test
    fun onFailureCompletesTerminalExceptionally() {
        val session = HlsTranscodingSession(workDir)
        session.onStart(1)
        session.onFailure(HlsError("boom", emptyList(), emptyList()))

        assertTrue(session.terminal.isCompleted)
        try {
            session.terminal.getCompleted()
            fail("expected exception")
        } catch (e: Throwable) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("boom"))
        }
    }

    @Test
    fun onCancelledCancelsTerminal() {
        val session = HlsTranscodingSession(workDir)
        session.onStart(1)
        session.onCancelled()

        assertTrue(session.terminal.isCancelled)
    }

    @Test
    fun onProgressForwardsToCallback() {
        val observed = mutableListOf<Pair<String, Int>>()
        val session =
            HlsTranscodingSession(workDir) { label, percent ->
                observed += label to percent
            }

        session.onStart(1)
        session.onRenditionStart(rendition360p())
        session.onProgress(rendition360p(), 33.7f)
        session.onProgress(rendition360p(), 75.0f)

        assertEquals(listOf("360p" to 33, "360p" to 75), observed)
    }

    @Test
    fun nonCombinedSegmentsAreIgnored() {
        val session = HlsTranscodingSession(workDir)
        val rendition = rendition360p()
        val nonCombined =
            HlsSegment(
                file = Files.createTempFile("hls-init", ".mp4").toFile().apply { writeText("init") },
                index = 0,
                durationSeconds = 0.0,
                isInitSegment = true,
                isCombinedRendition = false,
            )
        val combined = fakeCombinedSegment("combined")

        session.onStart(1)
        session.onRenditionStart(rendition)
        session.onSegmentReady(rendition, nonCombined)
        session.onSegmentReady(rendition, combined)
        session.onRenditionComplete(rendition, "playlist")
        session.onComplete("master")

        val bundle = session.terminal.getCompleted()
        assertEquals(1, bundle.renditions.size)
        assertEquals("combined", bundle.renditions[0].combinedFile.readText())
    }
}
