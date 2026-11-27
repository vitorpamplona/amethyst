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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentProof
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Blossom calls an upload makes (BUD-02 `PUT /upload`, BUD-01 `HEAD` and `GET`) on a Ktor
 * [HttpClient], for the targets without OkHttp, plus the blob manager's and the mirror queue's
 * ([BlossomBlobClient]: BUD-04 mirror, BUD-02 list and delete, BUD-09 report). Answers are read by
 * the same [blossomDescriptorOf] the OkHttp `BlossomClient` uses, and fail the same way.
 */
class KtorBlossomClient(
    /** The client for a request to this URL: the upload role's, so a Tor-routed server gets Tor. */
    private val client: (url: String) -> HttpClient,
) : BlossomBlobClient {
    /** [onProgress] follows the bytes sent. */
    override suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?,
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
    override suspend fun has(
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

    /**
     * BUD-04 `PUT /mirror`: the server fetches [sourceUrl] itself. Throws
     * [BlossomMirrorUnsupportedException] when the server has no such endpoint (404, 405, 501).
     */
    suspend fun mirror(
        sourceUrl: String,
        serverBaseUrl: String,
        authHeader: String?,
        paymentProof: BlossomPaymentProof? = null,
    ): BlossomUploadResult {
        val mirrorUrl = BlossomServerUrl.mirror(serverBaseUrl)
        val response =
            client(mirrorUrl).put(mirrorUrl) {
                authHeader?.let { header("Authorization", it) }
                paymentProof?.headers()?.forEach { (name, value) -> header(name, value) }
                contentType(ContentType.Application.Json)
                setBody(JsonMapper.toJson(MirrorRequest(sourceUrl)))
            }
        if (response.status.value in MIRROR_UNSUPPORTED_CODES) throw BlossomMirrorUnsupportedException(serverBaseUrl, response.status.value)
        return descriptorOf(response, serverBaseUrl)
    }

    /**
     * [mirror], or, where the server cannot mirror, a download here checked against [expectedHash]
     * and an upload, as the OkHttp client does: a server is untrusted, and the same upload token
     * (whose `x` tag is the expected hash) goes with the fallback.
     */
    override suspend fun mirrorOrUpload(
        sourceUrl: String,
        expectedHash: HexKey,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        paymentProof: BlossomPaymentProof?,
    ): BlossomUploadResult =
        try {
            mirror(sourceUrl, serverBaseUrl, authHeader, paymentProof)
        } catch (e: BlossomMirrorUnsupportedException) {
            val bytes = download(sourceUrl) ?: throw RuntimeException("Could not download $sourceUrl to upload to $serverBaseUrl")
            val actualHash = sha256(bytes).toHexKey()
            if (actualHash != expectedHash) {
                throw RuntimeException("$sourceUrl returned content ($actualHash) that does not match the expected $expectedHash")
            }
            upload(bytes, contentType, serverBaseUrl, authHeader)
        }

    /** BUD-02 `GET /list/<pubkey>`: the pubkey's blobs on this server. */
    override suspend fun list(
        serverBaseUrl: String,
        pubkey: HexKey,
        authHeader: String?,
    ): List<BlossomUploadResult> {
        val listUrl = BlossomServerUrl.list(serverBaseUrl, pubkey)
        val response = client(listUrl).get(listUrl) { authHeader?.let { header("Authorization", it) } }
        if (response.status.value == 402) {
            throw BlossomPaymentException(serverBaseUrl, BlossomPaymentRequired.fromHeaders { response.headers[it] })
        }
        if (!response.status.isSuccess()) {
            val reason = response.headers[BlossomServerUrl.REASON_HEADER] ?: response.status.value.toString()
            throw RuntimeException("List failed ($serverBaseUrl): $reason")
        }
        return JsonMapper.fromJson<List<BlossomUploadResult>>(response.bodyAsText().ifBlank { "[]" })
    }

    /** BUD-02 `DELETE /<sha256>[.ext]`. True on 2xx. */
    override suspend fun delete(
        hash: HexKey,
        serverBaseUrl: String,
        authHeader: String?,
        extension: String,
    ): Boolean {
        val blobUrl = BlossomServerUrl.blob(serverBaseUrl, hash, extension)
        return client(blobUrl).delete(blobUrl) { authHeader?.let { header("Authorization", it) } }.status.isSuccess()
    }

    /** BUD-09 `PUT /report` with a signed NIP-56 report as the body. True on 2xx. */
    override suspend fun report(
        serverBaseUrl: String,
        reportEventJson: String,
    ): Boolean {
        val reportUrl = BlossomServerUrl.report(serverBaseUrl)
        return client(reportUrl)
            .put(reportUrl) {
                contentType(ContentType.Application.Json)
                setBody(reportEventJson)
            }.status
            .isSuccess()
    }

    private suspend fun descriptorOf(
        response: HttpResponse,
        serverBaseUrl: String,
    ): BlossomUploadResult {
        val body = response.bodyAsText()
        return blossomDescriptorOf(response.status.value, { response.headers[it] }, { body }, serverBaseUrl)
    }

    @Serializable
    private data class MirrorRequest(
        val url: String,
    )

    companion object {
        private const val TAG = "KtorBlossomClient"

        /** `PUT /mirror` statuses that mean the server has no such endpoint, not a refused mirror. */
        private val MIRROR_UNSUPPORTED_CODES = setOf(404, 405, 501)
    }
}
