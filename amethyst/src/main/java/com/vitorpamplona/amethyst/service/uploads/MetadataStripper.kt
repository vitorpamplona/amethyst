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
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import java.io.File

class MetadataStripper {
    companion object {
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
    }

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

    fun stripImageMetadata(
        uri: Uri,
        context: Context,
    ): Uri {
        return try {
            val tempFile = File.createTempFile("stripped_", ".jpg", context.cacheDir)

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return uri

            val exif = ExifInterface(tempFile.absolutePath)
            for (tag in SENSITIVE_EXIF_TAGS) {
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()

            Log.d("MetadataStripper", "Stripped EXIF metadata from image")
            tempFile.toUri()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("MetadataStripper", "Failed to strip image metadata: ${e.message}")
            uri
        }
    }

    fun stripVideoMetadata(
        uri: Uri,
        context: Context,
    ): Uri {
        return try {
            val tempInputFile = File.createTempFile("video_input_", ".mp4", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempInputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return uri

            val tempOutputFile = File.createTempFile("stripped_video_", ".mp4", context.cacheDir)

            val extractor = MediaExtractor()
            extractor.setDataSource(tempInputFile.absolutePath)

            val muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val newTrackIndex = muxer.addTrack(format)
                trackIndexMap[i] = newTrackIndex
            }

            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            for (i in 0 until extractor.trackCount) {
                extractor.selectTrack(i)
                val outputTrack = trackIndexMap[i] ?: continue

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractorToCodecFlags(extractor.sampleFlags)

                    muxer.writeSampleData(outputTrack, buffer, bufferInfo)
                    extractor.advance()
                }

                extractor.unselectTrack(i)
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            tempInputFile.delete()

            Log.d("MetadataStripper", "Stripped metadata from video")
            tempOutputFile.toUri()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("MetadataStripper", "Failed to strip video metadata: ${e.message}")
            uri
        }
    }

    fun stripAudioMetadata(
        uri: Uri,
        context: Context,
    ): Uri {
        return try {
            val tempInputFile = File.createTempFile("audio_input_", ".tmp", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempInputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return uri

            val extractor = MediaExtractor()
            extractor.setDataSource(tempInputFile.absolutePath)

            if (extractor.trackCount == 0) {
                extractor.release()
                tempInputFile.delete()
                return uri
            }

            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val extension =
                when {
                    mime.contains("mp4a") || mime.contains("aac") -> ".m4a"
                    else -> ".mp4"
                }

            val tempOutputFile = File.createTempFile("stripped_audio_", extension, context.cacheDir)
            val muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            extractor.selectTrack(0)
            val outputTrack = muxer.addTrack(format)
            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractorToCodecFlags(extractor.sampleFlags)

                muxer.writeSampleData(outputTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            tempInputFile.delete()

            Log.d("MetadataStripper", "Stripped metadata from audio")
            tempOutputFile.toUri()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("MetadataStripper", "Failed to strip audio metadata: ${e.message}")
            uri
        }
    }

    fun strip(
        uri: Uri,
        mimeType: String?,
        context: Context,
    ): Uri =
        when {
            mimeType?.startsWith("image/", ignoreCase = true) == true -> stripImageMetadata(uri, context)
            mimeType?.startsWith("video/", ignoreCase = true) == true -> stripVideoMetadata(uri, context)
            mimeType?.startsWith("audio/", ignoreCase = true) == true -> stripAudioMetadata(uri, context)
            else -> uri
        }
}
