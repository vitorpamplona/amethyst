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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import okio.Buffer
import okio.IOException
import okio.Sink

/**
 * The one HTTP call the napplet code makes, a bounded GET, whatever the platform's HTTP stack:
 * [OkHttpNappletHttp] on Android and Desktop, `KtorNappletHttp` on iOS. Blob stores and the
 * resource fetcher take this instead of a client, so the same rules (the byte cap, the route, no
 * cookies on fetches nobody vouched for) hold on every platform.
 */
interface NappletHttp {
    /**
     * GETs [request]'s URL. A response arrives whatever its status; only a 2xx one has its body read,
     * and only up to [NappletHttpRequest.maxBytes]. Throws [NappletHttpTimeoutException] when the call
     * runs past its timeout, [NappletHttpBlockedException] when [NappletHttpRequest.publicOnly]
     * refused the address, and another IO exception for any other transport failure.
     */
    suspend fun get(request: NappletHttpRequest): NappletHttpResponse

    /**
     * [get], with a 2xx body written into [sink] as it arrives instead of held in memory: for a large
     * download (a webxdc archive). The response's [NappletHttpResponse.body] is null; `received` says
     * how much came, and `tooLarge` that it stopped at the cap (what reached [sink] is then incomplete).
     * [onProgress] follows the bytes written and the declared length. [sink] is flushed, not closed.
     *
     * By default the body is read whole by [get] and then written: an HTTP stack that can stream does.
     */
    suspend fun download(
        request: NappletHttpRequest,
        sink: Sink,
        onProgress: (bytes: Long, total: Long?) -> Unit = { _, _ -> },
    ): NappletHttpResponse {
        val response = get(request)
        val body = response.body?.takeIf { response.isSuccessful }
        if (body != null) {
            val buffer = Buffer().write(body)
            sink.write(buffer, buffer.size)
            sink.flush()
            onProgress(body.size.toLong(), body.size.toLong())
        }
        return NappletHttpResponse(response.status, response.location, null, response.tooLarge, response.received)
    }
}

class NappletHttpRequest(
    val url: String,
    /** Through Tor when true, else direct. An implementation with one route for its launch ignores it. */
    val viaTor: Boolean,
    /** The most body bytes read: a longer body (declared or sent) is [NappletHttpResponse.tooLarge]. */
    val maxBytes: Long,
    /** The whole call, end to end: a stalled server that trickles bytes still ends here. */
    val timeoutMs: Long,
    /** Follows redirects when true. When false, a 3xx comes back with its [NappletHttpResponse.location]. */
    val followRedirects: Boolean = true,
    /**
     * For a fetch on a page's behalf, to a server a stranger named: no cookies, no credentials, no
     * cache, and every address the host resolves to must be public (never the user's router or
     * localhost). Over Tor the exit resolves names, so only the URL's own IP literal is checked.
     */
    val publicOnly: Boolean = false,
)

class NappletHttpResponse(
    val status: Int,
    /** The `Location` header, for a redirect the caller follows itself. */
    val location: String?,
    /** The body of a 2xx response that fit in the cap; null otherwise. */
    val body: ByteArray?,
    /** A 2xx body over the cap, declared or as it came in. */
    val tooLarge: Boolean,
    /** Body bytes that came in, kept or not: a budget that counted only kept bodies could be gamed. */
    val received: Long,
) {
    val isSuccessful: Boolean get() = status in 200..299

    /** As OkHttp's `Response.isRedirect`: a 3xx that names where to go next. */
    val isRedirect: Boolean get() = status in REDIRECT_CODES

    private companion object {
        val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
    }
}

/** The call ran past [NappletHttpRequest.timeoutMs]. */
class NappletHttpTimeoutException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** [NappletHttpRequest.publicOnly] refused where the request would have gone. */
class NappletHttpBlockedException(
    message: String,
) : IOException(message)
