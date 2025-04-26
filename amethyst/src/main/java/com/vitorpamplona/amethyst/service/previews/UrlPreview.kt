/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.previews

import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

class UrlPreview {
    suspend fun fetch(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
        onComplete: suspend (urlInfo: UrlInfoItem) -> Unit,
        onFailed: suspend (t: Throwable) -> Unit,
    ) = try {
        onComplete(getDocument(url, okHttpClient))
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        onFailed(t)
    }

    suspend fun getDocument(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
    ): UrlInfoItem =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            okHttpClient(url).newCall(request).execute().use {
                checkNotInMainThread()
                if (it.isSuccessful) {
                    val mimeType =
                        it.headers["Content-Type"]?.toMediaType()
                            ?: throw IllegalArgumentException("Website returned unknown mimetype: ${it.headers["Content-Type"]}")
                    if (mimeType.type == "text" && mimeType.subtype == "html") {
                        val data = OpenGraphParser().extractUrlInfo(HtmlParser().parseHtml(it.body.source(), mimeType))
                        UrlInfoItem(url, data.title, data.description, data.image, mimeType.toString())
                    } else if (mimeType.type == "image") {
                        UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                    } else if (mimeType.type == "video") {
                        UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                    } else {
                        throw IllegalArgumentException("Website returned unknown encoding for previews: $mimeType")
                    }
                } else {
                    throw IllegalArgumentException("Website returned: " + it.code)
                }
            }
        }
}
