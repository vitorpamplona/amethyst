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

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of a page [UrlPreview] reads before giving up on its `<head>`.
 */
class UrlPreviewSizeTest {
    private val contentType = "text/html; charset=utf-8"

    private fun serving(html: String): (String) -> OkHttpClient =
        { _ ->
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", contentType)
                        .body(html.toResponseBody(contentType.toMediaType()))
                        .build()
                }.build()
        }

    /** `<head>` opens with [scriptBytes] of inline script before the page's own meta tags. */
    private fun pageWithScriptHeavyHead(scriptBytes: Int): String =
        buildString {
            append("<!DOCTYPE html><html><head><script>var ytcfg = {")
            while (length < scriptBytes) append("\"k\":\"vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv\",")
            append("};</script>")
            append("<title>Goals vs. Systems - YouTube</title>")
            append("<meta property=\"og:title\" content=\"Goals vs. Systems\">")
            append("<meta property=\"og:image\" content=\"https://i.ytimg.com/vi/fwcKTYvupJw/maxresdefault.jpg\">")
            append("<meta property=\"og:video:url\" content=\"https://www.youtube.com/embed/fwcKTYvupJw\">")
            append("<meta property=\"og:video:type\" content=\"text/html\">")
            append("</head><body></body></html>")
        }

    @Test
    fun readsOgTagsBehindAYouTubeSizedHead() =
        runTest {
            // A YouTube watch page (youtu.be/fwcKTYvupJw, 2026-09) is 1.29 MB, and its og: block
            // starts 708,776 bytes in, still inside <head> (which closes at 716,956). A 512 KB read
            // stopped short of it, so every YouTube link lost its card.
            val url = "https://youtu.be/fwcKTYvupJw"
            val info = UrlPreview().getDocument(url, serving(pageWithScriptHeavyHead(708_000)))

            assertEquals("Goals vs. Systems", info.title)
            assertEquals("https://i.ytimg.com/vi/fwcKTYvupJw/maxresdefault.jpg", info.image)
            assertTrue(info.fetchComplete())
        }
}
