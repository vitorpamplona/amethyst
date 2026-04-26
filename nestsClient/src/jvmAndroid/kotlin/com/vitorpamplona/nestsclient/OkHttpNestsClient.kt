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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * OkHttp-backed [NestsClient] used on JVM + Android. A shared [OkHttpClient]
 * can be injected so the app reuses connection pools / interceptors across
 * the process; the default constructor creates a dedicated client.
 */
class OkHttpNestsClient(
    private val http: OkHttpClient = OkHttpClient(),
) : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String {
        val url = nestsAuthUrl(room.authBaseUrl)
        val bodyJson =
            buildString {
                append('{')
                append("\"namespace\":\"").append(room.moqNamespace()).append('"')
                append(",\"publish\":").append(publish)
                append('}')
            }
        val bodyBytes = bodyJson.encodeToByteArray()
        // NIP-98 binds the signed event to (url, method, body-hash) so the
        // server can reject a token replayed against a different request.
        val authHeader =
            NestsAuth.header(
                signer = signer,
                url = url,
                method = "POST",
                payload = bodyBytes,
            )

        val request =
            Request
                .Builder()
                .url(url)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .build()

        return withContext(Dispatchers.IO) {
            runCatching { http.newCall(request).execute() }
                .getOrElse { throw NestsException("Failed to reach $url", it) }
                .use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw NestsException(
                            "nests server returned ${response.code} for $url: $body",
                            status = response.code,
                        )
                    }
                    try {
                        NestsTokenResponse.parse(body).token
                    } catch (e: IOException) {
                        throw NestsException("Malformed nests response from $url", e)
                    } catch (e: IllegalArgumentException) {
                        throw NestsException("Malformed nests response from $url", e)
                    } catch (e: kotlinx.serialization.SerializationException) {
                        throw NestsException("Malformed nests response from $url", e)
                    }
                }
        }
    }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
