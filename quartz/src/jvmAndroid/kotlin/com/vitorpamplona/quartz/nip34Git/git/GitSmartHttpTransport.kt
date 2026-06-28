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
package com.vitorpamplona.quartz.nip34Git.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

/** A ref returned by `ls-refs`: an oid, its full name, and (for symrefs) its target. */
class GitRef(
    val oid: String,
    val name: String,
    val symrefTarget: String? = null,
)

/** Parsed protocol-v2 server capabilities. */
class GitCapabilities(
    val agent: String?,
    val objectFormat: String,
    val supportsLsRefs: Boolean,
    val fetchFeatures: Set<String>,
) {
    /** Some servers advertise `fetch` with no feature list; track that we saw the command. */
    var rawHadFetch: Boolean = false

    val supportsFetch: Boolean get() = fetchFeatures.isNotEmpty() || rawHadFetch
    val supportsFilter: Boolean get() = fetchFeatures.contains("filter")
    val supportsShallow: Boolean get() = fetchFeatures.contains("shallow")
}

/**
 * Low-level git smart-HTTP **protocol v2** transport (`git-upload-pack` only;
 * we never write). The three exchanges are `info/refs` (capabilities),
 * `ls-refs`, and `fetch`. Wire encoding/decoding lives in [GitUploadPackV2].
 *
 * [okHttpClient] is the shared per-URL client provider, so every request inherits
 * the app's proxy / Tor routing and onion-rewrite interceptors.
 */
class GitSmartHttpTransport(
    private val okHttpClient: (String) -> OkHttpClient,
) {
    suspend fun fetchCapabilities(cloneUrl: String): GitCapabilities =
        withContext(Dispatchers.IO) {
            val url = "${base(cloneUrl)}/info/refs?service=git-upload-pack"
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Git-Protocol", "version=2")
                    .header("Accept", "*/*")
                    .get()
                    .build()
            GitUploadPackV2.parseCapabilities(PktLineCodec.parse(execute(url, request)))
        }

    suspend fun lsRefs(
        cloneUrl: String,
        caps: GitCapabilities,
    ): List<GitRef> =
        withContext(Dispatchers.IO) {
            val body = GitUploadPackV2.lsRefsRequest(caps.objectFormat)
            GitUploadPackV2.parseRefs(PktLineCodec.parse(postUploadPack(cloneUrl, body)))
        }

    /**
     * Runs a `fetch` and returns the raw packfile bytes (sideband demuxed).
     *
     * @param wants object ids to request.
     * @param deepen shallow depth (1 = tip only). Null for a full fetch.
     * @param filterBlobNone request `filter blob:none` (omit file contents).
     */
    suspend fun fetchPack(
        cloneUrl: String,
        caps: GitCapabilities,
        wants: List<String>,
        deepen: Int?,
        filterBlobNone: Boolean,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            require(wants.isNotEmpty()) { "fetch requires at least one want" }
            val body =
                GitUploadPackV2.fetchRequest(
                    objectFormat = caps.objectFormat,
                    wants = wants,
                    deepen = if (caps.supportsShallow) deepen else null,
                    filterBlobNone = filterBlobNone && caps.supportsFilter,
                )
            GitUploadPackV2.extractPack(PktLineCodec.parse(postUploadPack(cloneUrl, body)))
        }

    private suspend fun postUploadPack(
        cloneUrl: String,
        body: ByteArray,
    ): ByteArray {
        val url = "${base(cloneUrl)}/git-upload-pack"
        val request =
            Request
                .Builder()
                .url(url)
                .header("Git-Protocol", "version=2")
                .header("Accept", "application/x-git-upload-pack-result")
                .post(body.toRequestBody(GIT_UPLOAD_PACK_REQUEST))
                .build()
        return execute(url, request)
    }

    private suspend fun execute(
        url: String,
        request: Request,
    ): ByteArray =
        okHttpClient(url).newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw GitHttpException("HTTP ${response.code} from $url")
            }
            response.body.bytes()
        }

    private fun base(cloneUrl: String): String = cloneUrl.trim().trimEnd('/')

    companion object {
        private val GIT_UPLOAD_PACK_REQUEST = "application/x-git-upload-pack-request".toMediaType()
    }
}

class GitHttpException(
    message: String,
) : Exception(message)
