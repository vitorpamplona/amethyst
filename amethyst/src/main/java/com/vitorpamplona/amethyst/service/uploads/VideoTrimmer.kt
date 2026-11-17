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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.vitorpamplona.amethyst.commons.service.uploads.PictureHeaders
import com.vitorpamplona.quartz.utils.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Studio's video trim on Android, with the platform's own extractor and muxer: the audio and
 * video samples between the two times are copied into a fresh MP4, nothing decoded or re-encoded.
 * The cut starts at the key frame at or before the start, the result's clock starts at zero, and
 * the rotation hint carries over. Other tracks and the container's metadata do not.
 */
object VideoTrimmer {
    private const val TAG = "VideoTrimmer"
    private const val DEFAULT_SAMPLE_BUFFER = 4 * 1024 * 1024

    /** Writes [startMs]..[endMs] of [uri] to [out]; false when it could not (a track MP4 can't hold, no samples). */
    fun trim(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        out: File,
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor.setDataSource(context, uri, null)
            val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val tracks = HashMap<Int, Int>()
            var videoTrack = -1
            var bufferSize = DEFAULT_SAMPLE_BUFFER
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                tracks[i] = mux.addTrack(format)
                extractor.selectTrack(i)
                if (mime.startsWith("video/") && videoTrack < 0) videoTrack = i
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = max(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            if (tracks.isEmpty()) return false
            rotationOf(context, uri)?.let(mux::setOrientationHint)
            mux.start()
            started = true

            val endUs = endMs * 1000
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            // The clock starts at the first video key frame kept; audio before it is dropped.
            var baseUs = -1L
            var written = 0
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val time = extractor.sampleTime
                if (time > endUs) {
                    // Samples come roughly in time order: past the end on this track means done with it.
                    extractor.unselectTrack(track)
                    continue
                }
                if (baseUs < 0) {
                    if (videoTrack >= 0 && track != videoTrack) {
                        extractor.advance()
                        continue
                    }
                    baseUs = time
                }
                if (time < baseUs) {
                    extractor.advance()
                    continue
                }
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, time - baseUs, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(tracks.getValue(track), buffer, info)
                written++
                extractor.advance()
            }
            written > 0
        } catch (e: Exception) {
            Log.w(TAG, "Trim of $uri failed", e)
            false
        } finally {
            extractor.release()
            try {
                if (started) muxer?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Muxer stop failed", e)
            }
            muxer?.release()
        }
    }

    /** How long [uri] runs, in milliseconds: a video's own length, or a GIF's frame delays added up. */
    fun durationMs(
        context: Context,
        uri: Uri,
    ): Long? =
        gifBytes(context, uri)?.let(PictureHeaders::gifDurationMs)
            ?: retrieve(context, uri) { it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }

    /** The whole file when [uri] is a GIF (the retriever can't open one), else null. */
    private fun gifBytes(
        context: Context,
        uri: Uri,
    ): ByteArray? =
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(PictureHeaders.GIF_HEADER_BYTES)
                var read = 0
                while (read < header.size) {
                    val n = stream.read(header, read, header.size - read)
                    if (n < 0) break
                    read += n
                }
                if (PictureHeaders.gifSize(header.copyOf(read)) == null) null else header.copyOf(read) + stream.readBytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $uri", e)
            null
        }

    /** The GIF [bytes] drawn at [atMs], at most [maxEdge] on its long side. */
    @Suppress("DEPRECATION") // Movie is the platform's only GIF decoder that seeks to a time.
    private fun gifFrame(
        bytes: ByteArray,
        atMs: Long,
        maxEdge: Int,
    ): Bitmap? {
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (movie.width() <= 0 || movie.height() <= 0) return null
        val length = movie.duration().takeIf { it > 0 } ?: 1
        movie.setTime((atMs.coerceAtLeast(0) % length).toInt())
        val frame = createBitmap(movie.width(), movie.height())
        movie.draw(Canvas(frame), 0f, 0f)
        return fitWithin(frame, maxEdge)
    }

    /** A JPEG of the frame near [atMs], as it displays, at most [maxEdge] on its long side. */
    fun frame(
        context: Context,
        uri: Uri,
        atMs: Long,
        maxEdge: Int,
    ): ByteArray? =
        gifBytes(context, uri)?.let { gif -> gifFrame(gif, atMs, maxEdge)?.let(::toJpeg) } ?: retrieve(context, uri) { retriever ->
            val us = atMs.coerceAtLeast(0) * 1000
            val bitmap =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxEdge, maxEdge)
                } else {
                    retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { fitWithin(it, maxEdge) }
                } ?: return@retrieve null
            toJpeg(bitmap)
        }

    /** [bitmap] as JPEG bytes; recycles it. */
    private fun toJpeg(bitmap: Bitmap): ByteArray =
        try {
            ByteArrayOutputStream().use { bytes ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, FRAME_JPEG_QUALITY, bytes)
                bytes.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }

    private fun fitWithin(
        bitmap: Bitmap,
        maxEdge: Int,
    ): Bitmap {
        val long = max(bitmap.width, bitmap.height)
        if (long <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / long
        return bitmap.scale((bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1)).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun rotationOf(
        context: Context,
        uri: Uri,
    ): Int? =
        retrieve(context, uri) {
            it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.takeIf { degrees -> degrees in setOf(90, 180, 270) }
        }

    private fun <T> retrieve(
        context: Context,
        uri: Uri,
        read: (MediaMetadataRetriever) -> T?,
    ): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            read(retriever)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $uri", e)
            null
        } finally {
            retriever.release()
        }
    }

    private const val FRAME_JPEG_QUALITY = 80
}
