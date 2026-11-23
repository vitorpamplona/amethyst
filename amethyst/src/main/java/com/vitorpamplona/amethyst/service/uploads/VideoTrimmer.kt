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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import com.davotoula.lightcompressor.sanitizer.Mp4Sanitizer
import com.davotoula.lightcompressor.sanitizer.TrimRange
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.PictureHeaders
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import com.vitorpamplona.amethyst.commons.service.uploads.gif.GifEditor
import com.vitorpamplona.quartz.utils.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.lang.ref.SoftReference
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Studio's video tools on Android. The trim is LightCompressor-enhanced's [Mp4Sanitizer]: one
 * pass copies the audio and video samples of the kept part into a fresh MP4, nothing decoded or
 * re-encoded, with the metadata stripped and the index in front (fast-start). The cut starts at
 * the key frame at or before the start, the result's clock starts at zero, and the rotation hint
 * carries over. The rest (duration, filmstrip frames, GIF edits) reads the file through the
 * platform's retriever.
 */
object VideoTrimmer {
    private const val TAG = "VideoTrimmer"

    /**
     * Writes [startMs]..[endMs] (both kept) of [uri] to [out], already stripped of its metadata;
     * false when it could not: an empty range, a start past the end of the video (of the audio,
     * for a file with no video track), a track MP4 can't hold, more than one video track. [out]
     * does not exist after a false or a cancellation. A cancelled caller interrupts the remux and
     * gets the cancellation, not a false.
     */
    suspend fun trim(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        out: File,
    ): Boolean =
        // An empty range fails in TrimRange's own check, before the library opens the file.
        MetadataStripper.runSanitizer(out, "Trim $startMs..$endMs of $uri failed") {
            Mp4Sanitizer.sanitize(context, uri, out, TrimRange(startMs, endMs))
        } != null

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
