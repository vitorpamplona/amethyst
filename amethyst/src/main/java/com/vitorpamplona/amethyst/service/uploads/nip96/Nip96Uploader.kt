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
package com.vitorpamplona.amethyst.service.uploads.nip96

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.HttpStatusMessages
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip96FileStorage.actions.DeleteResult
import com.vitorpamplona.quartz.nip96FileStorage.actions.PartialEvent
import com.vitorpamplona.quartz.nip96FileStorage.actions.UploadResult
import com.vitorpamplona.quartz.nip96FileStorage.info.ServerInfo
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.coroutines.executeAsync
import okio.BufferedSink
import okio.source
import java.io.InputStream

class Nip96Uploader {
    suspend fun upload(
        uri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
        httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        context: Context,
    ) = upload(
        uri,
        contentType,
        size,
        alt,
        sensitiveContent,
        ServerInfoRetriever().loadInfo(serverBaseUrl, okHttpClient),
        okHttpClient,
        onProgress,
        httpAuth,
        context,
    )

    fun ContentResolver.querySize(uri: Uri) =
        query(uri, null, null, null, null)?.use {
            it.moveToFirst()
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            it.getLong(sizeIndex)
        }

    fun fileSize(uri: Uri) = runCatching { uri.toFile().length() }.getOrNull()

    suspend fun upload(
        uri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: String?,
        server: ServerInfo,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
        httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val contentResolver = context.contentResolver
        val myContentType = contentType ?: contentResolver.getType(uri)
        val length = size ?: contentResolver.querySize(uri) ?: fileSize(uri) ?: 0

        val imageInputStream = contentResolver.openInputStream(uri)

        checkNotNull(imageInputStream) { "Can't open the image input stream" }

        return imageInputStream.use { stream ->
            upload(
                stream,
                length,
                myContentType,
                alt,
                sensitiveContent,
                server,
                okHttpClient,
                onProgress,
                httpAuth,
                context,
            )
        }
    }

    suspend fun upload(
        inputStream: InputStream,
        length: Long,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        server: ServerInfo,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
        httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val fileName = RandomInstance.randomChars(16)
        val extension = contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = okHttpClient(server.apiUrl)
        val requestBuilder = Request.Builder()

        val requestBody: RequestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("expiration", "")
                .addFormDataPart("size", length.toString())
                .also { body ->
                    alt?.ifBlank { null }?.let { body.addFormDataPart("alt", it) }
                    sensitiveContent?.let { body.addFormDataPart(ContentWarningTag.TAG_NAME, it) }
                    contentType?.let { body.addFormDataPart("content_type", it) }
                }.addFormDataPart(
                    "file",
                    "$fileName.$extension",
                    object : RequestBody() {
                        override fun contentType() = contentType?.toMediaType()

                        override fun contentLength() = length

                        override fun writeTo(sink: BufferedSink) {
                            inputStream.source().use(sink::writeAll)
                        }
                    },
                ).build()

        httpAuth(server.apiUrl, "POST", null)?.let { requestBuilder.addHeader("Authorization", it.toAuthToken()) }

        requestBuilder
            .url(server.apiUrl)
            .post(requestBody)

        val request = requestBuilder.build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    response.body.use { body ->
                        val result = JsonMapper.fromJson<UploadResult>(body.string())
                        if (!result.processingUrl.isNullOrBlank()) {
                            waitProcessing(result, server, okHttpClient, onProgress)
                        } else if (result.status == "success") {
                            val event = result.nip94Event
                            if (event != null) {
                                convertToMediaResult(event)
                            } else {
                                throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), result.message))
                            }
                        } else {
                            throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), result.message))
                        }
                    }
                } else {
                    val msg = response.body.string()

                    val errorMessage =
                        try {
                            val tree = jacksonObjectMapper().readTree(msg)
                            val status = tree.get("status")?.asText()
                            val message = tree.get("message")?.asText()
                            if (status == "error" && message != null) {
                                message
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }

                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (errorMessage != null) {
                        throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), errorMessage))
                    } else if (explanation != null) {
                        throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), stringRes(context, explanation)))
                    } else {
                        throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), response.code.toString()))
                    }
                }
            }
        }
    }

    fun String.displayUrl() = this.removeSuffix("/").removePrefix("https://")

    fun convertToMediaResult(nip96: PartialEvent): MediaUploadResult {
        // Images don't seem to be ready immediately after upload
        val imageUrl = nip96.tags?.firstOrNull { it.size > 1 && it[0] == "url" }?.get(1)
        val remoteMimeType =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "m" }
                ?.get(1)
                ?.ifBlank { null }
        val hash =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "x" }
                ?.get(1)
                ?.ifBlank { null }
        val dim =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "dim" }
                ?.get(1)
                ?.ifBlank { null }
                ?.let { DimensionTag.parse(it) }
        val magnet =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "magnet" }
                ?.get(1)
                ?.ifBlank { null }

        return MediaUploadResult(
            url = imageUrl,
            type = remoteMimeType,
            sha256 = hash,
            dimension = dim,
            magnet = magnet,
        )
    }

    suspend fun delete(
        hash: String,
        contentType: String?,
        server: ServerInfo,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        context: Context,
    ): Boolean {
        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = okHttpClient(server.apiUrl)

        val requestBuilder = Request.Builder()

        httpAuth(server.apiUrl, "DELETE", null)?.let { requestBuilder.addHeader("Authorization", it.toAuthToken()) }

        val request =
            requestBuilder
                .url(server.apiUrl.removeSuffix("/") + "/$hash.$extension")
                .delete()
                .build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    val result = JsonMapper.fromJson<DeleteResult>(response.body.string())
                    result.status == "success"
                } else {
                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (explanation != null) {
                        throw RuntimeException(stringRes(context, R.string.failed_to_delete_with_message, stringRes(context, explanation)))
                    } else {
                        throw RuntimeException(stringRes(context, R.string.failed_to_delete_with_message, response.code))
                    }
                }
            }
        }
    }

    private suspend fun waitProcessing(
        result: UploadResult,
        server: ServerInfo,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
    ): MediaUploadResult {
        var currentResult = result

        val procUrl = result.processingUrl

        while (!procUrl.isNullOrBlank() && (currentResult.percentage ?: 100) < 100) {
            onProgress((currentResult.percentage ?: 100) / 100f)

            val request: Request =
                Request
                    .Builder()
                    .url(procUrl)
                    .build()

            val client = okHttpClient(procUrl)
            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    if (response.isSuccessful) {
                        currentResult = JsonMapper.fromJson<UploadResult>(response.body.string())
                    }
                }
            }

            delay(500)
        }
        onProgress((currentResult.percentage ?: 100) / 100f)

        val nip94 = currentResult.nip94Event

        if (nip94 != null) {
            return convertToMediaResult(nip94)
        } else {
            throw RuntimeException("Error waiting for processing. Final result is unavailable")
        }
    }
}
