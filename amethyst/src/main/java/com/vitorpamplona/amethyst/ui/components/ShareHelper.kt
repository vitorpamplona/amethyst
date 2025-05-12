/**
 * Copyright (c) 2024 Vitor Pamplona
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
import java.io.File
import java.io.FileInputStream
import java.io.IOException

// TODO use passed in mime type rather than hard coding
// TODO Add unit tests for sharehelper
// TODO Rely on passed in mime type for image type?
object ShareHelper {
    fun getSharableUriFromUrl(
        context: Context,
        imageUrl: String,
        mimeType: String,
    ): Uri {
        try {
            // Safely get snapshot and file
            val snapshot =
                Amethyst.instance.diskCache.openSnapshot(imageUrl)
                    ?: throw IOException("Unable to open snapshot for: $imageUrl")

            snapshot.use { snapshot ->
                val file =
                    snapshot.data.toFile()

                // Determine file extension and prepare sharable file
                val fileExtension = getImageExtension(file)
                val fileCopy = prepareSharableImageFile(context, file, fileExtension)

                // Return sharable uri
                return getSharableUri(context, fileCopy)
            }
        } catch (e: IOException) {
            Log.e("ShareHelper", "Error sharing image", e)
            throw e
        }
    }

    private fun getImageExtension(file: File): String =
        try {
            FileInputStream(file).use { inputStream ->
                val header = ByteArray(12)
                inputStream.read(header)

                when {
                    // JPEG magic number: FF D8 FF
                    header.sliceArray(0..1).contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) -> "jpg"

                    // PNG magic number: 89 50 4E 47
                    header.sliceArray(0..3).contentEquals(
                        byteArrayOf(
                            0x89.toByte(),
                            0x50.toByte(),
                            0x4E.toByte(),
                            0x47.toByte(),
                        ),
                    ) -> "png"

                    // WEBP magic number: "RIFF....WEBP"
                    header.sliceArray(0..3).contentEquals("RIFF".toByteArray()) &&
                        header.sliceArray(8..11).contentEquals("WEBP".toByteArray()) -> "webp"

                    else -> "jpg" // default fallback
                }
            }
        } catch (e: IOException) {
            Log.w("ShareHelper", "Could not determine image type, defaulting to jpg", e)
            "jpg"
        }

    private fun prepareSharableImageFile(
        context: Context,
        originalFile: File,
        extension: String,
    ): File {
        val sharableFile = File(context.cacheDir, "shared_image.$extension")
        originalFile.copyTo(sharableFile, overwrite = true)
        return sharableFile
    }

    private fun getSharableUri(
        context: Context,
        file: File,
    ): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
}
