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
package com.vitorpamplona.amethyst.ios.service.upload

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class IosBlossomClient {
    private val httpClient by lazy { HttpClient() }

    suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
    ): BlossomUploadResult {
        val apiUrl = serverBaseUrl.removeSuffix("/") + "/upload"

        val response =
            httpClient.put(apiUrl) {
                contentType(ContentType.parse(contentType))
                authHeader?.let { header("Authorization", it) }
                setBody(bytes)
            }

        if (!response.status.isSuccess()) {
            val reason = response.headers["X-Reason"] ?: response.status.toString()
            throw RuntimeException("Upload failed ($serverBaseUrl): $reason")
        }

        val jsonString = response.bodyAsText()
        return JsonMapper.fromJson<BlossomUploadResult>(jsonString)
    }
}
