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
package coil3.video

import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.vitorpamplona.amethyst.stubs.PlatformGaps
import okio.sink
import java.io.File

/**
 * JVM stand-in for Coil's `coil-video` decoder — the one that turns a video URL
 * into a poster frame for a feed thumbnail.
 *
 * Coil publishes `coil-video` for Android only, and its decoder is a thin
 * wrapper over `MediaMetadataRetriever`. This is the same wrapper over *this
 * platform's* [MediaMetadataRetriever], which already routes frame extraction
 * through an installed extractor — so the desktop app's existing JCodec/FFmpeg
 * thumbnail path serves Coil with no second pipeline.
 *
 * With no extractor installed the frame is null, which Coil surfaces as a
 * failed load and the UI already draws its placeholder for.
 */
class VideoFrameDecoder(
    private val source: SourceFetchResult,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult? {
        val path = source.source.fileOrNull()?.toFile() ?: spillToTemp() ?: return null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path.absolutePath)
            val frame = retriever.getFrameAtTime(frameMicros()) ?: return notExtracted()
            return DecodeResult(image = frame.asImage(), isSampled = false)
        } finally {
            retriever.release()
        }
    }

    /** Coil's own default, and all the thumbnail call sites ask for. */
    private fun frameMicros(): Long = 0L

    private fun notExtracted(): DecodeResult? {
        PlatformGaps.report(
            "coil.videoFrame",
            "no video-frame extractor is installed, so video thumbnails cannot be decoded; " +
                "install one through MediaMetadataRetriever.setExtractor",
        )
        return null
    }

    /** The retriever's extractors read files, so a streamed source is spilled. */
    private fun spillToTemp(): File? =
        runCatching {
            val temp = File.createTempFile("coil-video", null)
            temp.deleteOnExit()
            source.source.source().use { input ->
                temp.outputStream().use { output -> input.readAll(output.sink()) }
            }
            temp
        }.getOrNull()

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? = if (isVideo(result.mimeType)) VideoFrameDecoder(result, options) else null

        private fun isVideo(mimeType: String?) = mimeType?.startsWith("video/") == true
    }
}
