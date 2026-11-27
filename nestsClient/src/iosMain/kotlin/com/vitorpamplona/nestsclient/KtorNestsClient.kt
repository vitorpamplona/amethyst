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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlin.math.min

/**
 * The nests server's token mint over Ktor, for iOS: the JVM's OkHttpNestsClient call for call. The
 * NIP-98 header is signed again for every attempt (the server accepts a 60 s window), a transport
 * failure is retried once, a 429 waits out its Retry-After (or a capped exponential backoff), and
 * the whole mint is bounded by [callTimeoutMs].
 */
class KtorNestsClient(
    private val callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    private val httpClient: (String) -> HttpClient,
) : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String {
        val url = nestsAuthUrl(room.authBaseUrl)
        val bodyJson = "{\"namespace\":\"${room.moqNamespace()}\",\"publish\":$publish}"
        val bodyBytes = bodyJson.encodeToByteArray()

        val response =
            try {
                withTimeout(callTimeoutMs) { postWithRetry(url, bodyJson, bodyBytes, signer) }
            } catch (e: TimeoutCancellationException) {
                throw NestsException("nests mint timed out after ${callTimeoutMs}ms for $url", e)
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NestsException("nests server returned ${response.status.value} for $url: $body", status = response.status.value)
        }
        return try {
            NestsTokenResponse.parse(body).token
        } catch (e: IllegalArgumentException) {
            throw NestsException("Malformed nests response from $url", e)
        } catch (e: IllegalStateException) {
            throw NestsException("Malformed nests response from $url", e)
        } catch (e: SerializationException) {
            throw NestsException("Malformed nests response from $url", e)
        }
    }

    private suspend fun postWithRetry(
        url: String,
        bodyJson: String,
        bodyBytes: ByteArray,
        signer: NostrSigner,
    ): HttpResponse {
        var transportAttempts = 0
        var rateLimitAttempts = 0
        while (true) {
            val authHeader = NestsAuth.header(signer = signer, url = url, method = "POST", payload = bodyBytes)
            val response =
                try {
                    httpClient(url).post(url) {
                        header("Authorization", authHeader)
                        header("Accept", "application/json")
                        contentType(ContentType.Application.Json)
                        setBody(bodyJson)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (++transportAttempts >= MAX_TRANSPORT_RETRIES) throw NestsException("Failed to reach $url", e)
                    continue
                }
            if (response.status.value != 429 || rateLimitAttempts >= MAX_RATE_LIMIT_RETRIES) return response
            delay(rateLimitBackoffMs(response.headers["Retry-After"], rateLimitAttempts))
            rateLimitAttempts++
        }
    }

    companion object {
        private const val MAX_TRANSPORT_RETRIES = 2
        private const val MAX_RATE_LIMIT_RETRIES = 7
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 16_000L

        /** As OkHttpNestsClient's: a whole mint, retries included. */
        const val DEFAULT_CALL_TIMEOUT_MS: Long = 90_000L

        /** A Retry-After in seconds, else 1, 2, 4, 8, 16, 16 … seconds. (An HTTP-date one backs off.) */
        internal fun rateLimitBackoffMs(
            retryAfter: String?,
            attempt: Int,
        ): Long {
            retryAfter?.trim()?.toLongOrNull()?.let { return it.coerceAtLeast(0L) * 1_000L }
            return min(INITIAL_BACKOFF_MS shl attempt, MAX_BACKOFF_MS)
        }
    }
}
