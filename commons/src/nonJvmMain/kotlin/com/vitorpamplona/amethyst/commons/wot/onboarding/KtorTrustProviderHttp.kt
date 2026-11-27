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
package com.vitorpamplona.amethyst.commons.wot.onboarding

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readRemaining
import kotlinx.io.IOException
import kotlinx.io.readByteArray

/** [TrustProviderHttp] over Ktor, as OkHttpTrustProviderHttp. [client] picks the client for each URL (Tor). */
class KtorTrustProviderHttp(
    private val client: (String) -> HttpClient,
) : TrustProviderHttp {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): TrustProviderHttpResponse = client(url).prepareGet(url) { common(headers) }.execute { read(url, it) }

    override suspend fun post(
        url: String,
        jsonBody: String?,
        headers: Map<String, String>,
    ): TrustProviderHttpResponse =
        client(url)
            .preparePost(url) {
                common(headers)
                contentType(ContentType.Application.Json)
                setBody(jsonBody ?: "")
            }.execute { read(url, it) }

    private fun HttpRequestBuilder.common(headers: Map<String, String>) {
        headers.forEach { (name, value) -> header(name, value) }
        header("Accept", "application/json")
    }

    /** Setup answers are a few KB; a misbehaving endpoint must not stream the app out of memory. */
    private suspend fun read(
        url: String,
        response: HttpResponse,
    ): TrustProviderHttpResponse {
        val bytes = response.bodyAsChannel().readRemaining(MAX_BODY_BYTES + 1).readByteArray()
        if (bytes.size > MAX_BODY_BYTES) throw IOException("$url answered with more than $MAX_BODY_BYTES bytes")
        return TrustProviderHttpResponse(response.status.value, bytes.decodeToString())
    }

    companion object {
        private const val MAX_BODY_BYTES = 1_048_576L
    }
}
