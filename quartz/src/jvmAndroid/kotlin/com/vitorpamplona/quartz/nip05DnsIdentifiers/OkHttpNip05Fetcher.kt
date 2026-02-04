/**
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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

class OkHttpNip05Fetcher(
    val okHttpClient: (String) -> OkHttpClient,
) : Nip05Fetcher {
    override suspend fun fetch(url: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()

            // Fetchers MUST ignore any HTTP redirects given by the /.well-known/nostr.json endpoint.
            val client = okHttpClient(url).newBuilder().followRedirects(false).build()

            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    if (response.isSuccessful) {
                        response.body.string()
                    } else {
                        throw IllegalStateException("Error: ${response.code}, ${response.message}")
                    }
                }
            }
        }
}
