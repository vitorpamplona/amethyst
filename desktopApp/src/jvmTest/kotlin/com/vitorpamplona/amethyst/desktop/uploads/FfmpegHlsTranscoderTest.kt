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
package com.vitorpamplona.amethyst.desktop.uploads

import com.vitorpamplona.amethyst.commons.uploads.VideoCodecChoice
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsConfig
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsLadder
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsRenditionSummary
import com.vitorpamplona.amethyst.commons.uploads.hls.Rendition
import com.vitorpamplona.amethyst.commons.uploads.hls.Resolution
import com.vitorpamplona.amethyst.desktop.service.uploads.FfmpegHlsTranscoder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A malformed HLS ladder does not fail loudly — it produces playlists that
 * players silently refuse, or segments the NIP-71 event describes wrongly. So
 * the command line and the master playlist are asserted directly.
 */
class FfmpegHlsTranscoderTest {
    private val transcoder = FfmpegHlsTranscoder(File("/tmp/amethyst-hls-test"))
    private val input = File("/tmp/in.mov")
    private val dir = File("/tmp/out")
    private val playlist = File("/tmp/out/720p.m3u8")

    private fun config(
        codec: VideoCodecChoice = VideoCodecChoice.H264,
        segmentSeconds: Int = 4,
        disableAudio: Boolean = false,
        singleFile: Boolean = true,
    ) = HlsConfig(
        ladder = HlsLadder.default(),
        codec = codec,
        segmentDurationSeconds = segmentSeconds,
        disableAudio = disableAudio,
        singleFilePerRendition = singleFile,
    )

    private fun commandFor(
        rendition: Rendition = Rendition(Resolution.HD_720, 2800),
        config: HlsConfig = config(),
    ) = transcoder.command("ffmpeg", input, dir, playlist, rendition, config)

    private fun after(
        command: List<String>,
        flag: String,
    ) = command.getOrNull(command.indexOf(flag) + 1)

    @Test
    fun producesAVodFmp4Ladder() {
        val command = commandFor()
        assertEquals("hls", after(command, "-f"))
        assertEquals("fmp4", after(command, "-hls_segment_type"))
        // A rolling window would drop the start of the video from the playlist.
        assertEquals("vod", after(command, "-hls_playlist_type"))
        assertEquals("init.mp4", after(command, "-hls_fmp4_init_filename"))
    }

    @Test
    fun theRenditionBitrateIsPerRungNotGlobal() {
        assertEquals("2800k", after(commandFor(Rendition(Resolution.HD_720, 2800)), "-b:v"))
        assertEquals("800k", after(commandFor(Rendition(Resolution.SD_360, 800)), "-b:v"))
    }

    @Test
    fun theScaleFilterUsesThisRungsShortSideAndNeverUpscales() {
        val filter = after(commandFor(Rendition(Resolution.SD_540, 1400)), "-vf")!!
        assertTrue("540" in filter, filter)
        assertTrue("min(" in filter, "the filter can upscale: $filter")
        assertTrue("-2" in filter, "the free axis is not even-rounded: $filter")
    }

    @Test
    fun segmentDurationAndCodecFollowTheConfig() {
        assertEquals("6", after(commandFor(config = config(segmentSeconds = 6)), "-hls_time"))
        assertEquals("libx265", after(commandFor(config = config(codec = VideoCodecChoice.H265)), "-c:v"))
        assertEquals("libx264", after(commandFor(config = config(codec = VideoCodecChoice.H264)), "-c:v"))
    }

    @Test
    fun disablingAudioDropsTheAudioStreamRatherThanEncodingSilence() {
        assertTrue("-an" in commandFor(config = config(disableAudio = true)))
        assertTrue("-an" !in commandFor(config = config(disableAudio = false)))
        assertEquals("aac", after(commandFor(config = config(disableAudio = false)), "-c:a"))
    }

    @Test
    fun singleFilePerRenditionIsAFlagNotADefault() {
        assertEquals("single_file", after(commandFor(config = config(singleFile = true)), "-hls_flags"))
        assertTrue("-hls_flags" !in commandFor(config = config(singleFile = false)))
    }

    @Test
    fun theLadderTrimsToTheSourceAndNeverEmpties() {
        // A rung above the source would upscale: more bytes, worse picture.
        val for720 = HlsLadder.default().forSource(720)
        assertTrue(for720.renditions.all { it.resolution.shortSide <= 720 })
        assertTrue(for720.renditions.isNotEmpty())

        // A source smaller than every rung still gets one, or nothing publishes.
        val tiny = HlsLadder.default().forSource(120)
        assertEquals(1, tiny.renditions.size)
        assertEquals(Resolution.SD_360, tiny.renditions.first().resolution)
    }

    @Test
    fun theMasterPlaylistNamesEachRungWithItsBandwidth() {
        val master =
            transcoder.masterPlaylist(
                listOf(
                    summary(Resolution.HD_720, 2800, 1280, 720, "720p.m3u8"),
                    summary(Resolution.SD_360, 800, 640, 360, "360p.m3u8"),
                ),
            )

        assertTrue(master.startsWith("#EXTM3U"), master)
        // BANDWIDTH is bits per second; kbps here would make players pick the
        // wrong rung by a factor of a thousand.
        assertTrue("BANDWIDTH=2800000" in master, master)
        assertTrue("BANDWIDTH=800000" in master, master)
        assertTrue("RESOLUTION=1280x720" in master, master)
        assertTrue("720p.m3u8" in master, master)
        assertTrue("360p.m3u8" in master, master)
    }

    @Test
    fun aRungWithNoMeasuredSizeStillListsItsBandwidth() {
        val master = transcoder.masterPlaylist(listOf(summary(Resolution.HD_720, 2800, 0, 0, "720p.m3u8")))
        assertTrue("BANDWIDTH=2800000" in master, master)
        assertTrue("RESOLUTION=" !in master, "an unmeasured rung should not claim a size: $master")
    }

    private fun summary(
        resolution: Resolution,
        kbps: Int,
        width: Int,
        height: Int,
        filename: String,
    ) = HlsRenditionSummary(
        rendition = Rendition(resolution, kbps),
        mediaPlaylist = "",
        playlistFilename = filename,
        width = width,
        height = height,
        codecString = "avc1.640028",
        combinedFilename = null,
    )
}
