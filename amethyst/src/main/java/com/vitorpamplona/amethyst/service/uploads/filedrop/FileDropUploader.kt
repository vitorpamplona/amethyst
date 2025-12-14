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
package com.vitorpamplona.amethyst.service.uploads.filedrop

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.HttpStatusMessages
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.uploads.BlurhashMetadataCalculator
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.coroutines.executeAsync
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream

class FileDropUploader {
    fun Context.getFileName(uri: Uri): String? =
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
            else -> uri.path?.let(::File)?.name
        }

    private fun Context.getContentFileName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }
        }.getOrNull()

    suspend fun upload(
        uri: Uri,
        contentType: String?,
        size: Long?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit = {},
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val contentResolver = context.contentResolver
        val myContentType = contentType ?: contentResolver.getType(uri)
        val fileName = context.getFileName(uri) ?: "file-${RandomInstance.randomChars(8)}"
        val length = size ?: contentResolver.querySize(uri) ?: 0

        val localMetadata = BlurhashMetadataCalculator.computeFromUri(context, uri, myContentType)

        val imageInputStream = contentResolver.openInputStream(uri)
        checkNotNull(imageInputStream) { "Can't open the image input stream" }

        return imageInputStream
            .use { stream ->
                upload(
                    stream,
                    length,
                    fileName,
                    myContentType,
                    serverBaseUrl,
                    okHttpClient,
                    onProgress,
                    context,
                )
            }.mergeLocalMetadata(localMetadata)
    }

    private fun ContentResolver.querySize(uri: Uri): Long? =
        runCatching {
            query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                return@use cursor.getColumnIndexOrThrow(OpenableColumns.SIZE).let(cursor::getLong)
            }
        }.getOrNull()

    suspend fun upload(
        inputStream: InputStream,
        length: Long,
        fileName: String,
        contentType: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit = {},
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val apiUrl = serverBaseUrl.removeSuffix("/") + "/upload"

        val client = okHttpClient(apiUrl)
        val requestBuilder = Request.Builder()

        val requestBody: RequestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    if (extension.isNotEmpty()) "$fileName.$extension" else fileName,
                    object : RequestBody() {
                        override fun contentType() = contentType?.toMediaType()

                        override fun contentLength() = length

                        override fun writeTo(sink: BufferedSink) {
                            inputStream.source().use(sink::writeAll)
                        }
                    },
                ).build()

        requestBuilder
            .url(apiUrl)
            .post(requestBody)

        val request = requestBuilder.build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    response.body.use { body ->
                        convertToMediaResult(parseResults(body.string()))
                    }
                } else {
                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (explanation != null) {
                        throw RuntimeException(
                            stringRes(
                                context,
                                R.string.failed_to_upload_to_server_with_message,
                                serverBaseUrl.displayUrl(),
                                stringRes(context, explanation),
                            ),
                        )
                    } else {
                        throw RuntimeException(
                            stringRes(
                                context,
                                R.string.failed_to_upload_to_server_with_message,
                                serverBaseUrl.displayUrl(),
                                response.code.toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun convertToMediaResult(result: FileDropUploadResult): MediaUploadResult =
        MediaUploadResult(
            url = result.url,
            type = result.details.mimeType,
            sha256 = result.cid,
            size = result.size,
            ipfs = "ipfs://${result.cid}",
        )

    private fun parseResults(body: String): FileDropUploadResult = JsonMapper.decodeFromString(body)

    private fun String.displayUrl(): String {
        val shortened = this.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        return if (shortened.length > 50) {
            shortened.take(47) + "..."
        } else {
            shortened
        }
    }
}
