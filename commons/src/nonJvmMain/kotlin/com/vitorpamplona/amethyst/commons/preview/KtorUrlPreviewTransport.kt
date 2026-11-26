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
package com.vitorpamplona.amethyst.commons.preview

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable

/**
 * [UrlPreviewTransport] on a Ktor client (on iOS the Darwin engine, so NSURLSession): the body is
 * streamed as it arrives, and leaving [get]'s block early cancels the rest of the download, so a
 * page that never ends costs at most what [UrlPreview] reads of it. [client] picks the client per URL.
 */
class KtorUrlPreviewTransport(
    private val client: (String) -> HttpClient,
) : UrlPreviewTransport {
    override suspend fun <T> get(
        url: String,
        read: suspend (UrlPreviewResponse) -> T,
    ): T =
        client(url).prepareGet(url).execute { response ->
            val channel = response.bodyAsChannel()
            read(
                UrlPreviewResponse(
                    code = response.status.value,
                    contentType = response.headers[HttpHeaders.ContentType],
                    body = UrlPreviewBody { buffer, offset, length -> channel.readAvailable(buffer, offset, length) },
                ),
            )
        }
}
