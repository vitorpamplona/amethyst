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
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.PictureHeaders
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import com.vitorpamplona.amethyst.commons.service.uploads.gif.GifEditor
import com.vitorpamplona.quartz.utils.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.lang.ref.SoftReference
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
            // Video frames past the end, in decode order, that a frame still before the end may
            // reference (a B-frame decodes after the frame it points forward to): written only if
            // such a frame follows, so the last frames kept still decode.
            val held = ArrayList<HeldSample>()
            // The clock starts at the first video key frame kept; audio before it is dropped.
            var baseUs = -1L
            var written = 0
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val time = extractor.sampleTime
                val sync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
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
                if (time > endUs) {
                    if (track == videoTrack && !sync && held.size < MAX_HELD_FRAMES) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        held.add(HeldSample(ByteArray(size).also { buffer.duplicate().apply { position(0).limit(size) }.get(it) }, time))
                        extractor.advance()
                    } else {
                        // Past the end on this track; for video, a key frame or a run no frame
                        // before the end came back for. Samples come roughly in time order.
                        if (track == videoTrack) held.clear()
                        extractor.unselectTrack(track)
                    }
                    continue
                }
                if (track == videoTrack && held.isNotEmpty()) {
                    held.forEach { sample ->
                        info.set(0, sample.bytes.size, sample.timeUs - baseUs, 0)
                        mux.writeSampleData(tracks.getValue(track), ByteBuffer.wrap(sample.bytes), info)
                        written++
                    }
                    held.clear()
                }
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, time - baseUs, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(tracks.getValue(track), buffer, info)
                written++
                extractor.advance()
            }
            // Finished here, not in finally: a file the muxer could not close has no index and
            // would go up as a video nothing plays.
            started = false
            mux.stop()
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

    /** A video sample read past the trim's end and held in case a frame before the end needs it. */
    private class HeldSample(
        val bytes: ByteArray,
        val timeUs: Long,
    )

    /** The most frames held past the end: deeper than any B-frame reordering a camera writes. */
    private const val MAX_HELD_FRAMES = 8

    /** How long [uri] runs, in milliseconds: a video's own length, or a GIF's frame delays added up. */
    fun durationMs(
        context: Context,
        uri: Uri,
    ): Long? =
        gifBytes(context, uri)?.let(PictureHeaders::gifDurationMs)
            ?: retrieve(context, uri) { it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }

    /**
     * The whole file when [uri] is a GIF (the retriever can't open one) no larger than
     * [GifEditor.MAX_FILE_BYTES], else null. The last one read is kept while memory allows: the
     * Trim strip, the stage and the duration all ask for the same file.
     */
    private fun gifBytes(
        context: Context,
        uri: Uri,
    ): ByteArray? {
        val key = uri.toString()
        lastGif?.get()?.takeIf { it.first == key }?.let { return it.second }
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(PictureHeaders.GIF_HEADER_BYTES)
                val read = stream.readFully(header, header.size)
                if (PictureHeaders.gifSize(header.copyOf(read)) == null) return@use null
                val rest = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    rest.write(buffer, 0, n)
                    if (read + rest.size() > GifEditor.MAX_FILE_BYTES) return@use null
                }
                (header.copyOf(read) + rest.toByteArray()).also { lastGif = SoftReference(key to it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $uri", e)
            null
        }
    }

    @Volatile private var lastGif: SoftReference<Pair<String, ByteArray>>? = null

    private fun InputStream.readFully(
        into: ByteArray,
        length: Int,
    ): Int {
        var read = 0
        while (read < length) {
            val n = read(into, read, length - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    /** The GIF at [uri] cropped and trimmed, or null when it isn't a GIF, is too large, or [keepGoing] turned false. */
    fun editGif(
        context: Context,
        uri: Uri,
        crop: ImageCrop?,
        trim: VideoTrim?,
        keepGoing: () -> Boolean,
    ): ByteArray? = gifBytes(context, uri)?.let { GifEditor.edit(it, crop, trim, keepGoing) }

    /** The frame near [atMs], as it displays, at most [maxEdge] on its long side: a JPEG, or a one-frame GIF for a GIF. */
    fun frame(
        context: Context,
        uri: Uri,
        atMs: Long,
        maxEdge: Int,
    ): ByteArray? = frames(context, uri, listOf(atMs), maxEdge) { true }.firstOrNull()

    /** [frame] at each of [times]: a GIF in one decoding pass, a video through one retriever. */
    fun frames(
        context: Context,
        uri: Uri,
        times: List<Long>,
        maxEdge: Int,
        keepGoing: () -> Boolean,
    ): List<ByteArray?> {
        gifBytes(context, uri)?.let { gif -> return GifEditor.framesAt(gif, times, maxEdge, keepGoing) }
        return retrieve(context, uri) { retriever ->
            times.map { atMs ->
                if (!keepGoing()) return@map null
                val us = atMs.coerceAtLeast(0) * 1000
                val bitmap =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxEdge, maxEdge)
                    } else {
                        retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { fitWithin(it, maxEdge) }
                    }
                bitmap?.let(::toJpeg)
            }
        } ?: times.map { null }
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
