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
package com.vitorpamplona.amethyst.service.uploads.originless

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.originless.OriginlessUploadResponse
import com.vitorpamplona.amethyst.commons.originless.OriginlessUrls
import com.vitorpamplona.amethyst.service.HttpStatusMessages
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.service.uploads.PreviewMetadataCalculator
import com.vitorpamplona.amethyst.service.uploads.extensionFromMimeType
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
import java.io.InputStream

/**
 * NIP-96-style Originless client: multipart `POST {base}/upload` with field `file`
 * and no auth. The note URL is `ipfs://{cid}`; fetches go through `{base}/ipfs/{cid}`.
 */
class OriginlessUploader {
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
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val contentResolver = context.contentResolver
        val myContentType = contentType ?: contentResolver.getType(uri)
        val length = size ?: contentResolver.querySize(uri) ?: fileSize(uri) ?: 0

        val localMetadata = PreviewMetadataCalculator.computeFromUri(context, uri, myContentType)
        val imageInputStream = contentResolver.openInputStream(uri)

        checkNotNull(imageInputStream) { "Can't open the image input stream" }

        return imageInputStream
            .use { stream ->
                upload(
                    stream,
                    length,
                    myContentType,
                    serverBaseUrl,
                    okHttpClient,
                    onProgress,
                    context,
                )
            }.mergeLocalMetadata(localMetadata)
    }

    suspend fun upload(
        inputStream: InputStream,
        length: Long,
        contentType: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val fileName = RandomInstance.randomChars(16)
        val extension =
            contentType?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it) ?: extensionFromMimeType(it)
            } ?: ""

        val apiUrl = OriginlessUrls.uploadUrl(serverBaseUrl)
        val client = okHttpClient(apiUrl)

        val requestBody: RequestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
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
                ).build()

        val request =
            Request
                .Builder()
                .url(apiUrl)
                .post(requestBody)
                .build()

        onProgress(0f)

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                val body = response.body.string()
                if (response.isSuccessful) {
                    val result = parseResponse(body)
                    if (result.isError()) {
                        throw RuntimeException(
                            stringRes(
                                context,
                                R.string.failed_to_upload_to_server_with_message,
                                displayUrl(apiUrl),
                                result.errorMessage() ?: body,
                            ),
                        )
                    }
                    val cid = result.requireCid()
                    onProgress(1f)
                    MediaUploadResult(
                        url = OriginlessUrls.toIpfsUri(cid),
                        type = result.type ?: contentType,
                        size = result.size ?: length,
                        ipfs = OriginlessUrls.toIpfsUri(cid),
                    )
                } else {
                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    val parsedMessage = runCatching { parseResponse(body).errorMessage() }.getOrNull()
                    if (parsedMessage != null) {
                        throw RuntimeException(
                            stringRes(context, R.string.failed_to_upload_to_server_with_message, displayUrl(apiUrl), parsedMessage),
                        )
                    } else if (explanation != null) {
                        throw RuntimeException(
                            stringRes(
                                context,
                                R.string.failed_to_upload_to_server_with_message,
                                displayUrl(apiUrl),
                                stringRes(context, explanation),
                            ),
                        )
                    } else {
                        throw RuntimeException(
                            stringRes(context, R.string.failed_to_upload_to_server_with_message, displayUrl(apiUrl), response.code.toString()),
                        )
                    }
                }
            }
        }
    }

    internal fun parseResponse(body: String): OriginlessUploadResponse = JsonMapper.fromJson(body)

    private fun displayUrl(url: String) = url.removeSuffix("/").removePrefix("https://").removePrefix("http://")
}
