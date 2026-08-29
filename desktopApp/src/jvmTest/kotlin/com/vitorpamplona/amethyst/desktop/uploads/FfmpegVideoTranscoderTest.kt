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

import com.vitorpamplona.amethyst.commons.uploads.TranscodeConfig
import com.vitorpamplona.amethyst.commons.uploads.VideoCodecChoice
import com.vitorpamplona.amethyst.desktop.service.uploads.FfmpegVideoTranscoder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The command line is what actually decides the size and codec of every video
 * this app uploads, and a wrong flag produces a file rather than an error — so
 * it is asserted rather than trusted.
 */
class FfmpegVideoTranscoderTest {
    private val transcoder = FfmpegVideoTranscoder(File("/tmp/amethyst-test"))
    private val input = File("/tmp/in.mov")
    private val output = File("/tmp/out.mp4")

    private fun config(
        bitrate: Long = 2_000_000,
        shortSide: Int? = 720,
        codec: VideoCodecChoice = VideoCodecChoice.H264,
    ) = TranscodeConfig(
        videoBitrateInBps = bitrate,
        shortSideLimit = shortSide,
        codec = codec,
        outputName = "out",
    )

    private fun commandFor(
        config: TranscodeConfig = config(),
        streamable: Boolean = true,
    ) = transcoder.command("ffmpeg", input, output, config, streamable)

    @Test
    fun theBitrateTheCallerChoseIsTheBitrateFfmpegGets() {
        val command = commandFor(config(bitrate = 1_234_000))
        val index = command.indexOf("-b:v")
        assertTrue(index >= 0, "no bitrate flag")
        assertEquals("1234000", command[index + 1])
    }

    @Test
    fun theCodecChoiceSelectsTheEncoder() {
        assertTrue("libx264" in commandFor(config(codec = VideoCodecChoice.H264)))
        assertTrue("libx265" in commandFor(config(codec = VideoCodecChoice.H265)))
    }

    @Test
    fun noShortSideLimitMeansNoScaleFilter() {
        // The resizer is optional on the Android side too; a scale filter with
        // no limit would silently re-encode at some default size.
        assertTrue(commandFor(config(shortSide = null)).none { it == "-vf" })
    }

    @Test
    fun theScaleFilterLimitsTheShortSideAndNeverUpscales() {
        val command = commandFor(config(shortSide = 480))
        val filter = command[command.indexOf("-vf") + 1]

        assertTrue("480" in filter, "the limit is not in the filter: $filter")
        // `min(...)` is what keeps a smaller source from being blown up.
        assertTrue("min(" in filter, "the filter can upscale: $filter")
        // `-2` lets ffmpeg pick the other side as an even number, which H.264 needs.
        assertTrue("-2" in filter, "the free axis is not even-rounded: $filter")
    }

    @Test
    fun streamableAddsFaststartAndOtherwiseDoesNot() {
        val streamable = commandFor(streamable = true)
        assertEquals("+faststart", streamable[streamable.indexOf("-movflags") + 1])
        assertTrue(commandFor(streamable = false).none { it == "-movflags" })
    }

    @Test
    fun inputAndOutputEndUpInTheRightPlaces() {
        val command = commandFor()
        assertEquals("ffmpeg", command.first())
        assertEquals(input.absolutePath, command[command.indexOf("-i") + 1])
        assertEquals(output.absolutePath, command.last())
        // Without -y ffmpeg stops on a prompt nobody can answer.
        assertTrue("-y" in command)
    }

    @Test
    fun progressIsAPercentageOfTheRealDuration() {
        assertEquals(50f, transcoder.progressOf("frame= 30 time=00:00:30.00 bitrate=1", 60.0))
        assertEquals(0f, transcoder.progressOf("time=00:00:00.00", 60.0))
        assertEquals(100f, transcoder.progressOf("time=00:01:00.00", 60.0))
        assertEquals(50f, transcoder.progressOf("time=01:00:00.00", 7200.0))
    }

    @Test
    fun progressPastTheEndIsClampedNotOverAHundred() {
        assertEquals(100f, transcoder.progressOf("time=00:02:00.00", 60.0))
    }

    @Test
    fun aLineWithoutATimestampOrDurationYieldsNothing() {
        assertNull(transcoder.progressOf("Stream #0:0: Video: h264", 60.0))
        assertNull(transcoder.progressOf("time=00:00:30.00", null))
        assertNull(transcoder.progressOf("time=00:00:30.00", 0.0))
    }
}
