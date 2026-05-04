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
package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk.AMETHYST_SUBDIRECTORY
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object MediaSaverToDisk {
    suspend fun saveDownloadingIfNeeded(
        videoUri: String?,
        okHttpClient: (String) -> OkHttpClient,
        mimeType: String?,
        localContext: Context,
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?,
    ) = withContext(Dispatchers.IO) {
        when {
            videoUri.isNullOrBlank() -> {
                return@withContext
            }

            videoUri.startsWith("file") -> {
                save(
                    localFile = videoUri.toUri().toFile(),
                    mimeType = mimeType,
                    context = localContext,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }

            else -> {
                downloadAndSave(
                    url = videoUri,
                    mimeType = mimeType,
                    okHttpClient = okHttpClient,
                    context = localContext,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }
        }
    }

    /**
     * Saves the image to the gallery. May require a storage permission.
     *
     * @see AMETHYST_SUBDIRECTORY
     */
    suspend fun downloadAndSave(
        url: String,
        mimeType: String?,
        okHttpClient: (String) -> OkHttpClient,
        context: Context,
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?,
    ) {
        try {
            val client = okHttpClient(url)

            val request =
                Request
                    .Builder()
                    .get()
                    .url(url)
                    .build()

            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    check(response.isSuccessful)

                    val trimmedUrl = trimInlineMetaData(url)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val headerType =
                            response
                                .header("Content-Type")
                                ?.substringBefore(";")
                                ?.trim()

                        val realType =
                            headerType?.takeIf(::isSaveableMimeType)
                                ?: mimeType?.takeIf(::isSaveableMimeType)
                                ?: getMimeTypeFromExtension(trimmedUrl).takeIf(::isSaveableMimeType)
                                ?: ""
                        check(realType.isNotBlank()) { "Can't find out the content type" }

                        saveContentQ(
                            displayName = File(trimmedUrl).nameWithoutExtension,
                            contentType = realType,
                            contentSource = response.body.source(),
                            contentResolver = context.contentResolver,
                        )
                    } else {
                        saveContentDefault(
                            fileName = File(trimmedUrl).name,
                            contentSource = response.body.source(),
                            context = context,
                        )
                    }
                    onSuccess()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MediaSaverToDisk", "Error parsing response", e)
            onError(e)
        }
    }

    private fun getMimeTypeFromExtension(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase().let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it).orEmpty()
        }

    private fun isSaveableMimeType(type: String): Boolean =
        type.isNotBlank() &&
            (
                type.startsWith("image/", ignoreCase = true) ||
                    type.startsWith("video/", ignoreCase = true) ||
                    type.startsWith("audio/", ignoreCase = true) ||
                    type.equals(PDF_MIME_TYPE, ignoreCase = true)
            )

    @OptIn(ExperimentalUuidApi::class)
    fun save(
        localFile: File,
        mimeType: String?,
        context: Context,
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?,
    ) {
        try {
            val extension =
                mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""
            val buffer = localFile.inputStream().source().buffer()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveContentQ(
                    displayName = Uuid.random().toString(),
                    contentType = mimeType ?: "",
                    contentSource = buffer,
                    contentResolver = context.contentResolver,
                )
            } else {
                saveContentDefault(
                    fileName = "${Uuid.random()}.$extension",
                    contentSource = buffer,
                    context = context,
                )
            }
            onSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MediaSaverToDisk", "Unable to save", e)
            onError(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveContentQ(
        displayName: String,
        contentType: String,
        contentSource: BufferedSource,
        contentResolver: ContentResolver,
    ) {
        val cleanMimeType = contentType.substringBefore(";").trim()

        val (masterUri, baseDir) =
            when {
                cleanMimeType.startsWith("image/", ignoreCase = true) -> {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
                }

                cleanMimeType.equals(PDF_MIME_TYPE, ignoreCase = true) -> {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
                }

                else -> {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
                }
            }

        val contentValues =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, cleanMimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    baseDir + File.separatorChar + AMETHYST_SUBDIRECTORY,
                )
            }

        val uri = contentResolver.insert(masterUri, contentValues)
        checkNotNull(uri) { "Can't insert the new content" }

        try {
            val outputStream = contentResolver.openOutputStream(uri)
            checkNotNull(outputStream) { "Can't open the content output stream" }

            outputStream.use { contentSource.readAll(it.sink()) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveContentDefault(
        fileName: String,
        contentSource: BufferedSource,
        context: Context,
    ) {
        val subdirectory =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                AMETHYST_SUBDIRECTORY,
            ).apply {
                if (!exists()) mkdirs()
            }

        val outputFile = File(subdirectory, fileName)

        outputFile.outputStream().use { contentSource.readAll(it.sink()) }

        // Call the media scanner manually, so the image
        // appears in the gallery faster.
        MediaScannerConnection.scanFile(context, arrayOf(outputFile.toString()), null, null)
    }

    private fun trimInlineMetaData(url: String): String = url.substringBefore("#")

    private const val AMETHYST_SUBDIRECTORY = "Amethyst"
    private const val PDF_MIME_TYPE = "application/pdf"
}
