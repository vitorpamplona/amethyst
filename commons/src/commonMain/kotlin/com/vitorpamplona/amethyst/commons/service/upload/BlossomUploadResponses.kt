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

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads a Blossom server's answer to an upload or mirror, whatever HTTP client fetched it: a BUD-07
 * 402 becomes [BlossomPaymentException], a 409 naming the blob it already holds
 * [BlossomIdenticalMediaException], any other failure a [RuntimeException] carrying the server's
 * `X-Reason`, and a success its blob descriptor.
 */
fun blossomDescriptorOf(
    status: Int,
    header: (String) -> String?,
    body: () -> String,
    serverBaseUrl: String,
): BlossomUploadResult {
    if (status == 402) throw BlossomPaymentException(serverBaseUrl, BlossomPaymentRequired.fromHeaders(header))
    BlossomIdenticalMediaException.existingHashOf(status, header(BlossomIdenticalMediaException.IDENTICAL_MEDIA_HEADER))?.let {
        throw BlossomIdenticalMediaException(serverBaseUrl, it, header(BlossomServerUrl.REASON_HEADER))
    }
    if (status !in 200..299) {
        val reason = header(BlossomServerUrl.REASON_HEADER) ?: status.toString()
        throw RuntimeException("Request failed ($serverBaseUrl): $reason")
    }
    val text = body().ifBlank { throw RuntimeException("$serverBaseUrl returned no body") }
    return JsonMapper.fromJson<BlossomUploadResult>(text)
}

/**
 * [serverBaseUrl] first, then each of [fallbacks] (skipping duplicates by domain) until
 * one accepts. Servers differ in what they take -- several answer an opaque encrypted
 * blob with 415, paid ones with 402 -- and the user can't see that in advance. The auth
 * header is not server-scoped, so the same one serves every attempt. When all fail, the
 * first server's error is thrown: that is the server the user chose.
 */
suspend fun uploadToFirstAccepting(
    serverBaseUrl: String,
    fallbacks: List<String>,
    upload: suspend (String) -> BlossomUploadResult,
): AcceptedUpload {
    val seen = mutableSetOf<String>()
    val order = (listOf(serverBaseUrl) + fallbacks).filter { seen.add(BlossomServerUrl.domain(it)) }
    var firstError: Exception? = null
    for (server in order) {
        try {
            return AcceptedUpload(upload(server), server, firstError)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (firstError == null) firstError = e
            Log.w("UploadOrchestrator") { "Upload to $server failed (${e.message}), trying the next server" }
        }
    }
    throw firstError!!
}

/** The server that took the upload, and the chosen server's refusal when it was not that one. */
class AcceptedUpload(
    val result: BlossomUploadResult,
    val server: String,
    val refusal: Exception?,
) {
    /** What to tell the user when [server] is not [chosen]: where it went, and why. */
    fun notice(chosen: String): UploadNotice? =
        refusal?.let {
            UploadNotice.UploadedToAnotherServer(
                chosenServer = BlossomServerUrl.domain(chosen),
                usedServer = BlossomServerUrl.domain(server),
                reason = it.message,
            )
        }
}
