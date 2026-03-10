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
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk.SUBDIRECTORY
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
     * Downloads and saves media to the appropriate storage location.
     * Images go to Pictures, videos to Movies, audio to Music,
     * and documents (PDFs, etc.) to Downloads.
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentType = response.header("Content-Type") ?: getMimeTypeFromExtension(trimInlineMetaData(url))
                        check(contentType.isNotBlank()) { "Can't find out the content type" }

                        val realType =
                            if (contentType == "application/octet-stream") {
                                mimeType ?: getMimeTypeFromExtension(url)
                            } else {
                                contentType
                            }

                        saveContentQ(
                            displayName = File(trimInlineMetaData(url)).nameWithoutExtension,
                            contentType = realType,
                            contentSource = response.body.source(),
                            contentResolver = context.contentResolver,
                        )
                    } else {
                        saveContentDefault(
                            fileName = File(trimInlineMetaData(url)).name,
                            mimeType = mimeType,
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
                    mimeType = mimeType,
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

        val (masterUri, relativePath) = mediaStoreInfoFor(cleanMimeType)

        val contentValues =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, cleanMimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreInfoFor(mimeType: String): Pair<Uri, String> =
        when {
            mimeType.startsWith("image") ->
                Pair(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES + File.separatorChar + SUBDIRECTORY,
                )
            mimeType.startsWith("audio") ->
                Pair(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MUSIC + File.separatorChar + SUBDIRECTORY,
                )
            mimeType.startsWith("video") ->
                Pair(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MOVIES + File.separatorChar + SUBDIRECTORY,
                )
            else ->
                Pair(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_DOWNLOADS + File.separatorChar + SUBDIRECTORY,
                )
        }

    private fun saveContentDefault(
        fileName: String,
        mimeType: String?,
        contentSource: BufferedSource,
        context: Context,
    ) {
        val baseDir = defaultDirectoryFor(mimeType)

        val subdirectory =
            File(
                Environment.getExternalStoragePublicDirectory(baseDir),
                SUBDIRECTORY,
            ).apply {
                if (!exists()) mkdirs()
            }

        val outputFile = File(subdirectory, fileName)

        outputFile.outputStream().use { contentSource.readAll(it.sink()) }

        // Call the media scanner manually, so the file
        // appears in the gallery/app faster.
        MediaScannerConnection.scanFile(context, arrayOf(outputFile.toString()), null, null)
    }

    private fun defaultDirectoryFor(mimeType: String?): String =
        when {
            mimeType == null -> Environment.DIRECTORY_PICTURES
            mimeType.startsWith("image") -> Environment.DIRECTORY_PICTURES
            mimeType.startsWith("audio") -> Environment.DIRECTORY_MUSIC
            mimeType.startsWith("video") -> Environment.DIRECTORY_MOVIES
            else -> Environment.DIRECTORY_DOWNLOADS
        }

    enum class MediaType {
        IMAGE,
        VIDEO,
        AUDIO,
        DOCUMENT,
    }

    fun mediaTypeOf(mimeType: String?): MediaType =
        when {
            mimeType == null -> MediaType.DOCUMENT
            mimeType.startsWith("image") -> MediaType.IMAGE
            mimeType.startsWith("video") -> MediaType.VIDEO
            mimeType.startsWith("audio") -> MediaType.AUDIO
            else -> MediaType.DOCUMENT
        }

    private fun trimInlineMetaData(url: String): String = url.substringBefore("#")

    private const val SUBDIRECTORY = "Amethyst"
}
