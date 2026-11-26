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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Blossom calls an upload makes (BUD-02 `PUT /upload`, BUD-01 `HEAD` and `GET`) on a Ktor
 * [HttpClient], for the targets without OkHttp. Answers are read by the same
 * [blossomDescriptorOf] the OkHttp `BlossomClient` uses.
 */
class KtorBlossomClient(
    /** The client for a request to this URL: the upload role's, so a Tor-routed server gets Tor. */
    private val client: (url: String) -> HttpClient,
) {
    /** [onProgress] follows the bytes sent. */
    suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): BlossomUploadResult {
        val uploadUrl = BlossomServerUrl.upload(serverBaseUrl)
        val response =
            client(uploadUrl).put(uploadUrl) {
                authHeader?.let { header("Authorization", it) }
                contentType(ContentType.parse(contentType))
                setBody(bytes)
                if (onProgress != null) onUpload { sent, total -> onProgress(sent, total ?: bytes.size.toLong()) }
            }
        val body = response.bodyAsText()
        return blossomDescriptorOf(response.status.value, { response.headers[it] }, { body }, serverBaseUrl)
    }

    /**
     * [put], or the descriptor of the blob [serverBaseUrl] already holds under [hash] when it refused
     * the upload but serves that blob (it hashes to its name), as `BlossomClient.uploadOrHeld` does.
     */
    suspend fun uploadOrHeld(
        hash: HexKey,
        size: Long,
        contentType: String?,
        serverBaseUrl: String,
        put: suspend () -> BlossomUploadResult,
    ): BlossomUploadResult =
        try {
            put()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!has(hash, serverBaseUrl)) throw e
            Log.d(TAG) { "$serverBaseUrl refused $hash, which it already holds: ${e.message}" }
            BlossomUploadResult(url = BlossomServerUrl.blob(serverBaseUrl, hash), sha256 = hash, size = size, type = contentType)
        }

    /** BUD-01 `HEAD /<sha256>`: whether the server holds the blob. */
    suspend fun has(
        hash: HexKey,
        serverBaseUrl: String,
    ): Boolean =
        try {
            val blobUrl = BlossomServerUrl.blob(serverBaseUrl, hash)
            client(blobUrl).head(blobUrl).status.isSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "HEAD $hash on $serverBaseUrl failed", e)
            false
        }

    /** The bytes at [url], or null when it can't be fetched. */
    suspend fun download(url: String): ByteArray? =
        try {
            val response = client(url).get(url)
            if (response.status.isSuccess()) response.bodyAsBytes() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Download of $url failed", e)
            null
        }

    companion object {
        private const val TAG = "KtorBlossomClient"
    }
}
