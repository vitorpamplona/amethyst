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
package com.vitorpamplona.amethyst.commons.video.cache

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable

/**
 * [RangeSource] over a Ktor client (on iOS the Darwin engine, so NSURLSession): one GET with a
 * `Range` header, its body streamed as it arrives. Leaving [fetch]'s block early cancels the call.
 */
class KtorRangeSource(
    /** The client for a range of this URL: the video role's, so a Tor-routed video gets Tor. */
    private val client: (url: String) -> HttpClient,
) : RangeSource {
    override suspend fun <T> fetch(
        url: String,
        start: Long,
        endExclusive: Long?,
        block: suspend (RangeResponseHead, RangeBody) -> T,
    ): T =
        client(url)
            .prepareGet(url) {
                header(HttpHeaders.Range, if (endExclusive != null) "bytes=$start-${endExclusive - 1}" else "bytes=$start-")
                // A compressed body's offsets are not the file's.
                header(HttpHeaders.AcceptEncoding, "identity")
            }.execute { response ->
                val headers = response.headers
                val head =
                    RangeResponseHead(
                        status = response.status.value,
                        contentRange = headers[HttpHeaders.ContentRange],
                        contentLength = headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                        contentType = headers[HttpHeaders.ContentType],
                        contentEncoding = headers[HttpHeaders.ContentEncoding],
                    )
                val channel = response.bodyAsChannel()
                block(head, RangeBody { buffer -> channel.readAvailable(buffer, 0, buffer.size) })
            }
}
