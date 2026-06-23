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
package com.vitorpamplona.amethyst.napplethost

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Shared Blossom-blob HTTP fetcher, used by both the sandbox content server and the main-process
 * prefetcher so blobs always travel the same Tor-routed path. No OkHttp disk cache here — durability
 * is the content-addressed [NappletBlobCache], which (unlike OkHttp's journaled cache) is multi-process
 * safe.
 */
object NappletBlobHttp {
    const val MAX_BLOB_BYTES = 20L * 1024 * 1024

    /** An OkHttp client routed through the Tor SOCKS proxy when [proxyPort] > 0, else direct. */
    fun client(proxyPort: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (proxyPort > 0) builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort)))
        return builder.build()
    }

    /** Downloads [url], or null on error / when the body exceeds [MAX_BLOB_BYTES] (declared length). */
    fun download(
        client: OkHttpClient,
        url: String,
    ): ByteArray? =
        try {
            client
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build(),
                ).execute()
                .use { r ->
                    val declared = r.body.contentLength()
                    if (!r.isSuccessful || declared > MAX_BLOB_BYTES) {
                        null
                    } else {
                        r.body.bytes().takeIf { it.size <= MAX_BLOB_BYTES }
                    }
                }
        } catch (e: Exception) {
            Log.w("NappletBlobHttp", "Blob fetch failed for $url", e)
            null
        }
}
