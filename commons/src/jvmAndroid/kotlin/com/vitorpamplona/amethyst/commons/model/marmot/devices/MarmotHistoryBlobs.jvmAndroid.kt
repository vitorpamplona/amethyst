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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun marmotHistoryBlobsOf(
    account: Account,
    httpClients: IRoleBasedHttpClientBuilder,
): MarmotHistoryBlobs? = BlossomHistoryBlobs({ account.blossomServers.flow.value }, account.signer) { url -> httpClients.okHttpClientForUploads(url) }

/**
 * [MarmotHistoryBlobs] over Blossom: the upload follows the rules of every Marmot attachment
 * (`application/octet-stream`, no alt text beyond the hash, BUD-02 `/upload`, never `/media`, which
 * would re-encode the ciphertext), and a fetch reads no more than it is allowed to.
 */
class BlossomHistoryBlobs(
    /** The servers to upload to, the account's own list (kind 10063), in its order. */
    private val servers: suspend () -> List<String>,
    private val signer: NostrSigner,
    private val clientFor: (url: String) -> OkHttpClient,
) : MarmotHistoryBlobs {
    override suspend fun upload(ciphertext: ByteArray): List<String>? {
        val targets = servers().distinct().take(MAX_SERVERS)
        if (targets.isEmpty()) return null
        val hash = sha256(ciphertext).toHexKey()
        val auth = BlossomAuth.createUploadAuth(hash, ciphertext.size.toLong(), "Uploading $hash", signer)
        val held =
            coroutineScope {
                targets
                    .map { server ->
                        async(Dispatchers.IO) {
                            try {
                                val result = BlossomClient(clientFor(server)).upload(bytes = ciphertext, contentType = OPAQUE, serverBaseUrl = server, authHeader = auth)
                                server.takeIf { result.url != null && (result.sha256 == null || result.sha256.equals(hash, ignoreCase = true)) }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "could not upload a history bundle to $server", e)
                                null
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }
        return held.ifEmpty { null }
    }

    /**
     * The blob at [url], at most [maxBytes] of it. Cancellable to the socket: cancelling the caller
     * cancels the HTTP call, whether it is still connecting or already reading the body.
     */
    override suspend fun fetch(
        url: String,
        maxBytes: Long,
    ): ByteArray? =
        try {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            clientFor(url).newCall(request).readCapped(maxBytes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "could not fetch a history bundle from $url", e)
            null
        }

    /**
     * Runs the call on OkHttp's dispatcher and reads its body there, at most [maxBytes]; null for an
     * unsuccessful answer or a longer body. A cancelled coroutine cancels the call, which also ends
     * a read in progress.
     */
    private suspend fun Call.readCapped(maxBytes: Long): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val bytes =
                            try {
                                response.use { readCapped(it, maxBytes) }
                            } catch (e: IOException) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                                return
                            }
                        if (continuation.isActive) continuation.resume(bytes)
                    }
                },
            )
        }

    private fun readCapped(
        response: Response,
        maxBytes: Long,
    ): ByteArray? {
        if (!response.isSuccessful) return null
        val body = response.body
        if (body.contentLength() > maxBytes) return null
        // Capped while reading: a server that streams forever does not fill the heap.
        val source = body.source()
        if (source.request(maxBytes + 1)) return null
        return source.buffer.readByteArray()
    }

    companion object {
        private const val TAG = "BlossomHistoryBlobs"
        private const val OPAQUE = "application/octet-stream"

        /** A history delivery names one to four servers. */
        const val MAX_SERVERS = 4
    }
}
