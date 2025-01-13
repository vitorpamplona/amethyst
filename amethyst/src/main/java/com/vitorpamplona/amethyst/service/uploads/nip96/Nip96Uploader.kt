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
package com.vitorpamplona.amethyst.service.uploads.nip96

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.HttpStatusMessages
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import com.vitorpamplona.quartz.nip96FileStorage.AuthToken
import com.vitorpamplona.quartz.nip96FileStorage.Nip96Result
import com.vitorpamplona.quartz.nip96FileStorage.PartialEvent
import com.vitorpamplona.quartz.nip96FileStorage.ResultParser
import com.vitorpamplona.quartz.nip96FileStorage.ServerInfo
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomChars() = List(16) { charPool.random() }.joinToString("")

class Nip96Uploader {
    suspend fun upload(
        uri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: String?,
        serverBaseUrl: String,
        forceProxy: (String) -> Boolean,
        onProgress: (percentage: Float) -> Unit,
        httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        context: Context,
    ) = upload(
        uri,
        contentType,
        size,
        alt,
        sensitiveContent,
        ServerInfoRetriever().loadInfo(serverBaseUrl, forceProxy(serverBaseUrl)),
        forceProxy,
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
        forceProxy: (String) -> Boolean,
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

        return upload(
            imageInputStream,
            length,
            myContentType,
            alt,
            sensitiveContent,
            server,
            forceProxy,
            onProgress,
            httpAuth,
            context,
        )
    }

    suspend fun upload(
        inputStream: InputStream,
        length: Long,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        server: ServerInfo,
        forceProxy: (String) -> Boolean,
        onProgress: (percentage: Float) -> Unit,
        httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val fileName = randomChars()
        val extension = contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = HttpClientManager.getHttpClient(forceProxy(server.apiUrl))
        val requestBuilder = Request.Builder()

        val requestBody: RequestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("expiration", "")
                .addFormDataPart("size", length.toString())
                .also { body ->
                    alt?.ifBlank { null }?.let { body.addFormDataPart("alt", it) }
                    sensitiveContent?.let { body.addFormDataPart("content-warning", it) }
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

        httpAuth(server.apiUrl, "POST", null)?.let { requestBuilder.addHeader("Authorization", AuthToken().encodeAuth(it)) }

        requestBuilder
            .addHeader("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
            .url(server.apiUrl)
            .post(requestBody)

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body.use { body ->
                    val result = ResultParser().parseResults(body.string())
                    if (!result.processingUrl.isNullOrBlank()) {
                        return waitProcessing(result, server, forceProxy, onProgress)
                    } else if (result.status == "success") {
                        val event = result.nip94Event
                        if (event != null) {
                            return convertToMediaResult(event)
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
                ?.let { Dimension.parse(it) }
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
        forceProxy: (String) -> Boolean,
        httpAuth: (String, String, ByteArray?) -> HTTPAuthorizationEvent,
        context: Context,
    ): Boolean {
        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = HttpClientManager.getHttpClient(forceProxy(server.apiUrl))

        val requestBuilder = Request.Builder()

        httpAuth(server.apiUrl, "DELETE", null)?.let { requestBuilder.addHeader("Authorization", AuthToken().encodeAuth(it)) }

        val request =
            requestBuilder
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(server.apiUrl.removeSuffix("/") + "/$hash.$extension")
                .delete()
                .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body.use { body ->
                    val result = ResultParser().parseDeleteResults(body.string())
                    return result.status == "success"
                }
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

    private suspend fun waitProcessing(
        result: Nip96Result,
        server: ServerInfo,
        forceProxy: (String) -> Boolean,
        onProgress: (percentage: Float) -> Unit,
    ): MediaUploadResult {
        var currentResult = result

        val procUrl = result.processingUrl

        while (!procUrl.isNullOrBlank() && (currentResult.percentage ?: 100) < 100) {
            onProgress((currentResult.percentage ?: 100) / 100f)

            val request: Request =
                Request
                    .Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .url(procUrl)
                    .build()

            val client = HttpClientManager.getHttpClient(forceProxy(procUrl))
            client.newCall(request).execute().use {
                if (it.isSuccessful) {
                    it.body.use { currentResult = ResultParser().parseResults(it.string()) }
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
