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
package com.vitorpamplona.amethyst.desktop.service.media

import org.jcodec.api.awt.AWTSequenceEncoder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * H.264 codes whole 16-pixel macroblocks, so a video whose size isn't a multiple of 16 decodes to a
 * larger frame plus a crop. The poster must be the cropped picture: a 624x626 phone clip came out
 * 624x640, and the card was the wrong shape until the video loaded.
 */
class VideoThumbnailCacheCropTest {
    private val video = File.createTempFile("crop", ".mp4")

    @AfterTest
    fun cleanUp() {
        if (!video.delete() && video.exists()) video.deleteOnExit()
    }

    @Test
    fun posterOfAVideoNotSizedInMacroblocksIsTheVideosSize() {
        val encoder = AWTSequenceEncoder.create30Fps(video)
        val frame = BufferedImage(624, 626, BufferedImage.TYPE_3BYTE_BGR)
        val g = frame.createGraphics()
        g.color = Color(200, 120, 60)
        g.fillRect(0, 0, 624, 626)
        g.dispose()
        // The poster is taken about a second in.
        repeat(45) { encoder.encodeImage(frame) }
        encoder.finish()

        val poster = assertNotNull(VideoThumbnailCache.tryJCodec(video, "https://example.com/crop.mp4"))

        assertEquals(624, poster.width)
        assertEquals(626, poster.height)
    }
}
