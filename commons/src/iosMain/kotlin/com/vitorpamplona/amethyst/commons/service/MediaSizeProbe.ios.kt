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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

private const val TAG = "MediaSizeProbe"

/** The file's length from [HttpResponse]'s headers: positive, and not the length of a compressed body. */
internal fun HttpResponse.fileLength(): Long? = headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0 && headers["Content-Encoding"] == null }

/** A HEAD over the role's client, as the JVM's OkHttp probe does. */
internal actual suspend fun IRoleBasedHttpClientBuilder.headContentLength(
    url: String,
    role: MediaSizeRole,
): HeadAnswer {
    val client =
        when (role) {
            MediaSizeRole.IMAGE -> ktorClientForImage(url)
            MediaSizeRole.VIDEO -> ktorClientForVideo(url)
            MediaSizeRole.PREVIEW -> ktorClientForPreview(url)
        }
    return try {
        // identity: a compressed answer's length is not the file's.
        val response = client.head(url) { header("Accept-Encoding", "identity") }
        val length = if (response.status.isSuccess()) response.fileLength() else null
        if (length != null) HeadAnswer.Size(length) else HeadAnswer.NoSize
    } catch (e: IOException) {
        // A failed request, not the host's answer: worth asking again later.
        Log.d(TAG, "size probe failed", e)
        HeadAnswer.Failed
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // NSURLSession reports a dropped connection as its own exception, not an IOException.
        if (e::class.simpleName?.contains("Darwin") == true) {
            Log.d(TAG, "size probe failed", e)
            HeadAnswer.Failed
        } else {
            Log.d(TAG, "size probe gave up", e)
            HeadAnswer.NoSize
        }
    }
}
