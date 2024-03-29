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
package com.vitorpamplona.amethyst.service

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.model.Account
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.util.Base64
import kotlin.coroutines.resume

val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomChars() = List(16) { charPool.random() }.joinToString("")

class Nip96Uploader(val account: Account?) {
    suspend fun uploadImage(
        uri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: String?,
        server: Nip96MediaServers.ServerName,
        contentResolver: ContentResolver,
        onProgress: (percentage: Float) -> Unit,
    ): PartialEvent {
        val serverInfo =
            Nip96Retriever()
                .loadInfo(
                    server.baseUrl,
                )

        return uploadImage(
            uri,
            contentType,
            size,
            alt,
            sensitiveContent,
            serverInfo,
            contentResolver,
            onProgress,
        )
    }

    suspend fun uploadImage(
        uri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: String?,
        server: Nip96Retriever.ServerInfo,
        contentResolver: ContentResolver,
        onProgress: (percentage: Float) -> Unit,
    ): PartialEvent {
        checkNotInMainThread()

        val myContentType = contentType ?: contentResolver.getType(uri)
        val imageInputStream = contentResolver.openInputStream(uri)

        val length =
            size
                ?: contentResolver.query(uri, null, null, null, null)?.use {
                    it.moveToFirst()
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    it.getLong(sizeIndex)
                }
                ?: kotlin.runCatching { uri.toFile().length() }.getOrNull() ?: 0

        checkNotNull(imageInputStream) { "Can't open the image input stream" }

        return uploadImage(
            imageInputStream,
            length,
            myContentType,
            alt,
            sensitiveContent,
            server,
            onProgress,
        )
    }

    suspend fun uploadImage(
        inputStream: InputStream,
        length: Long,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        server: Nip96Retriever.ServerInfo,
        onProgress: (percentage: Float) -> Unit,
    ): PartialEvent {
        checkNotInMainThread()

        val fileName = randomChars()
        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = HttpClientManager.getHttpClient()
        val requestBody: RequestBody
        val requestBuilder = Request.Builder()

        requestBody =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("expiration", "")
                .addFormDataPart("size", length.toString())
                .also { body ->
                    alt?.let { body.addFormDataPart("alt", it) }
                    sensitiveContent?.let { body.addFormDataPart("content-warning", it) }
                    contentType?.let { body.addFormDataPart("content_type", it) }
                }
                .addFormDataPart(
                    "file",
                    "$fileName.$extension",
                    object : RequestBody() {
                        override fun contentType() = contentType?.toMediaType()

                        override fun contentLength() = length

                        override fun writeTo(sink: BufferedSink) {
                            inputStream.source().use(sink::writeAll)
                        }
                    },
                )
                .build()

        nip98Header(server.apiUrl)?.let { requestBuilder.addHeader("Authorization", it) }

        requestBuilder
            .addHeader("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
            .url(server.apiUrl)
            .post(requestBody)

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body.use { body ->
                    val str = body.string()
                    val result = parseResults(str)

                    if (!result.processingUrl.isNullOrBlank()) {
                        return waitProcessing(result, server, onProgress)
                    } else if (result.status == "success" && result.nip94Event != null) {
                        return result.nip94Event
                    } else {
                        throw RuntimeException("Failed to upload with message: ${result.message}")
                    }
                }
            } else {
                throw RuntimeException("Error Uploading image: ${response.code}")
            }
        }
    }

    suspend fun delete(
        hash: String,
        contentType: String?,
        server: Nip96Retriever.ServerInfo,
    ): Boolean {
        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = HttpClientManager.getHttpClient()

        val requestBuilder = Request.Builder()

        nip98Header(server.apiUrl)?.let { requestBuilder.addHeader("Authorization", it) }

        val request =
            requestBuilder
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(server.apiUrl.removeSuffix("/") + "/$hash.$extension")
                .delete()
                .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body.use { body ->
                    val str = body.string()
                    val result = parseDeleteResults(str)
                    return result.status == "success"
                }
            } else {
                throw RuntimeException("Error Uploading image: ${response.code}")
            }
        }
    }

    private suspend fun waitProcessing(
        result: Nip96Result,
        server: Nip96Retriever.ServerInfo,
        onProgress: (percentage: Float) -> Unit,
    ): PartialEvent {
        val client = HttpClientManager.getHttpClient()
        var currentResult = result

        while (!result.processingUrl.isNullOrBlank() && (currentResult.percentage ?: 100) < 100) {
            onProgress((currentResult.percentage ?: 100) / 100f)

            val request: Request =
                Request.Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .url(result.processingUrl)
                    .build()

            client.newCall(request).execute().use {
                if (it.isSuccessful) {
                    it.body.use { currentResult = parseResults(it.string()) }
                }
            }

            delay(500)
        }
        onProgress((currentResult.percentage ?: 100) / 100f)

        val nip94 = currentResult.nip94Event

        if (nip94 != null) {
            return nip94
        } else {
            throw RuntimeException("Error waiting for processing. Final result is unavailable")
        }
    }

    suspend fun nip98Header(url: String): String? {
        return withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { continuation ->
                nip98Header(url, "POST") { authorizationToken -> continuation.resume(authorizationToken) }
            }
        }
    }

    fun nip98Header(
        url: String,
        method: String,
        file: ByteArray? = null,
        onReady: (String?) -> Unit,
    ) {
        val myAccount = account

        if (myAccount == null) {
            onReady(null)
            return
        }

        myAccount.createHTTPAuthorization(url, method, file) {
            val encodedNIP98Event = Base64.getEncoder().encodeToString(it.toJson().toByteArray())
            onReady("Nostr $encodedNIP98Event")
        }
    }

    data class DeleteResult(
        val status: String?,
        val message: String?,
    )

    private fun parseDeleteResults(body: String): DeleteResult {
        val mapper =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return mapper.readValue(body, DeleteResult::class.java)
    }

    data class Nip96Result(
        val status: String? = null,
        val message: String? = null,
        @JsonProperty("processing_url") val processingUrl: String? = null,
        val percentage: Int? = null,
        @JsonProperty("nip94_event") val nip94Event: PartialEvent? = null,
    )

    class PartialEvent(
        val tags: Array<Array<String>>? = null,
        val content: String? = null,
    )

    private fun parseResults(body: String): Nip96Result {
        val mapper =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return mapper.readValue(body, Nip96Result::class.java)
    }
}
