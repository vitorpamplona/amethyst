/**
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
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.vitorpamplona.amethyst.Amethyst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object ShareHelper {
    private const val TAG = "ShareHelper"
    private const val DEFAULT_EXTENSION = "jpg"
    private const val SHARED_FILE_PREFIX = "shared_media"

    // Media type magic numbers
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    private val WEBP_HEADER_START = "RIFF".toByteArray()
    private val WEBP_HEADER_END = "WEBP".toByteArray()
    private val GIF_MAGIC = "GIF8".toByteArray()

    suspend fun getSharableUriFromUrl(
        context: Context,
        imageUrl: String,
    ): Pair<Uri, String> =
        withContext(Dispatchers.IO) {
            // Safely get snapshot and file
            Amethyst.instance.diskCache.openSnapshot(imageUrl)?.use { snapshot ->
                val file = snapshot.data.toFile()

                // Determine file extension and prepare sharable file
                val fileExtension = getImageExtension(file)
                val fileCopy = prepareSharableFile(context, file, fileExtension)

                // Return sharable uri
                return@use Pair(
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", fileCopy),
                    fileExtension,
                )
            } ?: throw IOException("Unable to open snapshot for: $imageUrl")
        }

    private fun getImageExtension(file: File): String =
        try {
            FileInputStream(file).use { inputStream ->
                val header = ByteArray(12)
                val bytesRead = inputStream.read(header)

                if (bytesRead < 4) {
                    // If we couldn't read at least 4 bytes, default to jpg
                    return DEFAULT_EXTENSION
                }

                when {
                    // JPEG: Check first 2 bytes
                    matchesMagicNumbers(header, 0, JPEG_MAGIC) -> "jpg"

                    // PNG: Check first 4 bytes
                    matchesMagicNumbers(header, 0, PNG_MAGIC) -> "png"

                    // GIF: Check first 4 bytes for "GIF8"
                    matchesMagicNumbers(header, 0, GIF_MAGIC) -> "gif"

                    // WEBP: Check "RIFF" (bytes 0-3) and "WEBP" (bytes 8-11)
                    matchesMagicNumbers(header, 0, WEBP_HEADER_START) &&
                        bytesRead >= 12 &&
                        matchesMagicNumbers(header, 8, WEBP_HEADER_END) -> "webp"

                    else -> DEFAULT_EXTENSION
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not determine image type for ${file.name}, defaulting to $DEFAULT_EXTENSION", e)
            DEFAULT_EXTENSION
        }

    private fun matchesMagicNumbers(
        data: ByteArray,
        offset: Int,
        magicBytes: ByteArray,
    ): Boolean {
        if (offset + magicBytes.size > data.size) {
            return false
        }

        for (i in magicBytes.indices) {
            if (data[offset + i] != magicBytes[i]) {
                return false
            }
        }
        return true
    }

    private fun prepareSharableFile(
        context: Context,
        originalFile: File,
        extension: String,
    ): File {
        val timestamp = System.currentTimeMillis()
        val sharableFile = File(context.cacheDir, "${SHARED_FILE_PREFIX}_$timestamp.$extension")

        try {
            originalFile.copyTo(sharableFile, overwrite = true)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy file for sharing", e)
            throw e
        }

        return sharableFile
    }
}
