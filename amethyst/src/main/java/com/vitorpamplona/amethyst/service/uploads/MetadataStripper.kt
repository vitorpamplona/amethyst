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
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.ByteBuffer

data class StrippingResult(
    val uri: Uri,
    val stripped: Boolean,
)

object MetadataStripper {
    private const val DEFAULT_REMUX_BUFFER_SIZE = 8 * 1024 * 1024

    private val SENSITIVE_EXIF_TAGS =
        arrayOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_USER_COMMENT,
        )

    private fun extractorToCodecFlags(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private fun remuxTracks(
        uri: Uri,
        context: Context,
        outputFile: File,
        preStart: (MediaMuxer, MediaExtractor, Context, Uri) -> Unit = { _, _, _, _ -> },
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var succeeded = false
        try {
            extractor.setDataSource(context, uri, null)

            if (extractor.trackCount == 0) return false

            // Note: MediaMuxer may still write a creation timestamp and encoder info into
            // the new container. This is not controllable via the Android API and is a
            // known residual privacy limitation of the remux approach.
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                trackIndexMap[i] = muxer.addTrack(format)
                extractor.selectTrack(i)
            }

            preStart(muxer, extractor, context, uri)

            muxer.start()
            muxerStarted = true

            // Size buffer to the largest track's KEY_MAX_INPUT_SIZE (covers 4K keyframes),
            // falling back to 8MB if the format doesn't report it.
            var maxInputSize = DEFAULT_REMUX_BUFFER_SIZE
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxInputSize = maxOf(maxInputSize, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            val buffer = ByteBuffer.allocateDirect(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val outputTrack = trackIndexMap[extractor.sampleTrackIndex] ?: break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractorToCodecFlags(extractor.sampleFlags)

                muxer.writeSampleData(outputTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            succeeded = true
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
            extractor.release()
            if (!succeeded && !outputFile.delete()) {
                Log.w("MetadataStripper", "Failed to delete temp file: ${outputFile.absolutePath}")
            }
        }
        return succeeded
    }

    fun stripImageMetadata(
        uri: Uri,
        context: Context,
    ): StrippingResult {
        var tempFile: File? = null
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val extension =
                when {
                    mimeType.endsWith("jpeg", ignoreCase = true) ||
                        mimeType.endsWith("jpg", ignoreCase = true) -> ".jpg"

                    mimeType.endsWith("png", ignoreCase = true) -> ".png"

                    mimeType.endsWith("webp", ignoreCase = true) -> ".webp"

                    else -> ".tmp"
                }
            tempFile = File.createTempFile("stripped_", extension, context.cacheDir)

            val inputStream =
                context.contentResolver.openInputStream(uri)
                    ?: run {
                        if (!tempFile.delete()) {
                            Log.w("MetadataStripper", "Failed to delete temp file: ${tempFile.absolutePath}")
                        }
                        return StrippingResult(uri, false)
                    }
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val exif = ExifInterface(tempFile.absolutePath)
            for (tag in SENSITIVE_EXIF_TAGS) {
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()

            Log.d("MetadataStripper", "Stripped EXIF metadata from image")
            StrippingResult(tempFile.toUri(), true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (tempFile?.delete() == false) {
                Log.w("MetadataStripper", "Failed to delete temp file: ${tempFile.absolutePath}")
            }
            Log.d("MetadataStripper") { "Failed to strip image metadata: ${e.message}" }
            StrippingResult(uri, false)
        }
    }

    fun stripVideoMetadata(
        uri: Uri,
        context: Context,
    ): StrippingResult {
        return try {
            val tempOutputFile = File.createTempFile("stripped_video_", ".mp4", context.cacheDir)

            val succeeded =
                remuxTracks(uri, context, tempOutputFile) { muxer, _, ctx, sourceUri ->
                    // Rotation is a container-level property not included in track formats;
                    // read it explicitly and reapply so the output plays back with the correct orientation.
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(ctx, sourceUri)
                        val rotation =
                            retriever
                                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                                ?.toIntOrNull() ?: 0
                        if (rotation != 0) muxer.setOrientationHint(rotation)
                    } finally {
                        retriever.release()
                    }
                }

            if (!succeeded) return StrippingResult(uri, false)

            Log.d("MetadataStripper", "Stripped metadata from video")
            StrippingResult(tempOutputFile.toUri(), true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("MetadataStripper") { "Failed to strip video metadata: ${e.message}" }
            StrippingResult(uri, false)
        }
    }

    fun stripAudioMetadata(
        uri: Uri,
        context: Context,
    ): StrippingResult {
        return try {
            // Verify the primary track is AAC/MP4A before remuxing
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                if (extractor.trackCount == 0) return StrippingResult(uri, false)
                val primaryMime = extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME) ?: ""
                if (!primaryMime.contains("mp4a") && !primaryMime.contains("aac")) {
                    return StrippingResult(uri, false)
                }
            } finally {
                extractor.release()
            }

            val tempOutputFile = File.createTempFile("stripped_audio_", ".m4a", context.cacheDir)

            val succeeded = remuxTracks(uri, context, tempOutputFile)

            if (!succeeded) return StrippingResult(uri, false)

            Log.d("MetadataStripper", "Stripped metadata from audio")
            StrippingResult(tempOutputFile.toUri(), true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("MetadataStripper") { "Failed to strip audio metadata: ${e.message}" }
            StrippingResult(uri, false)
        }
    }

    fun stripMp3Metadata(
        uri: Uri,
        context: Context,
    ): StrippingResult {
        var tempInputFile: File? = null
        return try {
            tempInputFile = File.createTempFile("mp3_input_", ".mp3", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempInputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                if (!tempInputFile.delete()) {
                    Log.w("MetadataStripper", "Failed to delete temp file: ${tempInputFile.absolutePath}")
                }
                return StrippingResult(uri, false)
            }

            val fileSize = tempInputFile.length()
            var startOffset = 0L
            var endOffset = fileSize

            // Read first 10 bytes to check for ID3v2 header
            val header = ByteArray(10)
            tempInputFile.inputStream().use { it.read(header) }

            if (fileSize >= 10 &&
                header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()
            ) {
                val size =
                    (header[6].toInt() and 0x7F shl 21) or
                        (header[7].toInt() and 0x7F shl 14) or
                        (header[8].toInt() and 0x7F shl 7) or
                        (header[9].toInt() and 0x7F)
                startOffset = 10L + size
            }

            // Read last 128 bytes to check for ID3v1 tag
            if (endOffset - startOffset >= 128) {
                val tail = ByteArray(128)
                java.io.RandomAccessFile(tempInputFile, "r").use { raf ->
                    raf.seek(endOffset - 128)
                    raf.readFully(tail)
                }
                if (tail[0] == 'T'.code.toByte() &&
                    tail[1] == 'A'.code.toByte() &&
                    tail[2] == 'G'.code.toByte()
                ) {
                    endOffset -= 128
                }
            }

            if (startOffset == 0L && endOffset == fileSize) {
                if (!tempInputFile.delete()) {
                    Log.w("MetadataStripper", "Failed to delete temp file: ${tempInputFile.absolutePath}")
                }
                tempInputFile = null
                return StrippingResult(uri, true) // no tags found, already clean
            }

            val tempOutputFile = File.createTempFile("stripped_mp3_", ".mp3", context.cacheDir)
            java.io.RandomAccessFile(tempInputFile, "r").use { raf ->
                raf.seek(startOffset)
                tempOutputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var remaining = endOffset - startOffset
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            if (!tempInputFile.delete()) {
                Log.w("MetadataStripper", "Failed to delete temp file: ${tempInputFile.absolutePath}")
            }
            tempInputFile = null

            Log.d("MetadataStripper", "Stripped ID3 tags from MP3")
            StrippingResult(tempOutputFile.toUri(), true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (tempInputFile?.delete() == false) {
                Log.w("MetadataStripper", "Failed to delete temp file: ${tempInputFile.absolutePath}")
            }
            Log.d("MetadataStripper") { "Failed to strip MP3 metadata: ${e.message}" }
            StrippingResult(uri, false)
        }
    }

    fun strip(
        uri: Uri,
        mimeType: String?,
        context: Context,
    ): StrippingResult =
        when {
            mimeType?.startsWith("image/", ignoreCase = true) == true -> stripImageMetadata(uri, context)
            mimeType?.startsWith("video/", ignoreCase = true) == true -> stripVideoMetadata(uri, context)
            mimeType?.equals("audio/mpeg", ignoreCase = true) == true -> stripMp3Metadata(uri, context)
            mimeType?.startsWith("audio/", ignoreCase = true) == true -> stripAudioMetadata(uri, context)
            else -> StrippingResult(uri, false)
        }
}
