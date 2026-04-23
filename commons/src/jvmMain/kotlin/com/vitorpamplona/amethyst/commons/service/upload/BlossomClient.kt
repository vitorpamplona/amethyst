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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File

/**
 * Blossom HTTP client for JVM consumers (desktop + CLI). Owns no global
 * state — pass a configured [OkHttpClient] (e.g. desktop's Tor-aware
 * `DesktopHttpClient.currentClient()`) for proxying / connection pooling.
 * The default constructor uses a fresh OkHttpClient — fine for one-shot
 * uses such as the CLI.
 */
class BlossomClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun upload(
        file: File,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
    ): BlossomUploadResult =
        withContext(Dispatchers.IO) {
            val apiUrl = serverBaseUrl.removeSuffix("/") + "/upload"
            val requestBody =
                object : RequestBody() {
                    override fun contentType() = contentType.toMediaType()

                    override fun contentLength() = file.length()

                    override fun writeTo(sink: BufferedSink) {
                        file.inputStream().source().use(sink::writeAll)
                    }
                }

            val requestBuilder =
                Request
                    .Builder()
                    .url(apiUrl)
                    .put(requestBody)

            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    val reason = it.headers["X-Reason"] ?: it.code.toString()
                    throw RuntimeException("Upload failed ($serverBaseUrl): $reason")
                }
                val body = it.body ?: throw RuntimeException("Upload to $serverBaseUrl returned no body")
                JsonMapper.fromJson<BlossomUploadResult>(body.string())
            }
        }

    /**
     * Upload raw bytes (e.g. encrypted blobs) to a Blossom server.
     */
    suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
    ): BlossomUploadResult =
        withContext(Dispatchers.IO) {
            val apiUrl = serverBaseUrl.removeSuffix("/") + "/upload"
            val requestBody = bytes.toRequestBody(contentType.toMediaType())

            val requestBuilder =
                Request
                    .Builder()
                    .url(apiUrl)
                    .put(requestBody)

            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    val reason = it.headers["X-Reason"] ?: it.code.toString()
                    throw RuntimeException("Upload failed ($serverBaseUrl): $reason")
                }
                val body = it.body ?: throw RuntimeException("Upload to $serverBaseUrl returned no body")
                JsonMapper.fromJson<BlossomUploadResult>(body.string())
            }
        }
}
