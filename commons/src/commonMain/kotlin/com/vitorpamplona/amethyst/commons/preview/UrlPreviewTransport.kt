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

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder

/**
 * The one HTTP call a link preview makes: a GET of the link, its body read only as far as
 * [UrlPreview] needs. Android and the desktop run it on the preview OkHttp client (so a link goes
 * through Tor when the user's settings say so); iOS on its Ktor client.
 */
interface UrlPreviewTransport {
    /**
     * GETs [url], following redirects, and hands the response to [read] while the body is still
     * open. Returning from [read] closes the call, unread bytes and all.
     */
    suspend fun <T> get(
        url: String,
        read: suspend (UrlPreviewResponse) -> T,
    ): T
}

/** The status, the raw `Content-Type` header (null when absent) and the body of a preview GET. */
class UrlPreviewResponse(
    val code: Int,
    val contentType: String?,
    val body: UrlPreviewBody,
)

/** A response body read as it arrives. */
fun interface UrlPreviewBody {
    /** Reads up to [length] bytes into [buffer] at [offset]: how many it read, or -1 at the end. */
    suspend fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int
}

/** The transport link previews use, on this account's preview HTTP client. */
expect fun IRoleBasedHttpClientBuilder.urlPreviewTransport(): UrlPreviewTransport
