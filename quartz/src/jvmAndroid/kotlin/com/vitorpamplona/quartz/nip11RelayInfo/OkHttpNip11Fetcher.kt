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
package com.vitorpamplona.quartz.nip11RelayInfo

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

/**
 * OkHttp-backed [Nip11Fetcher]: GETs the relay's https url with
 * `Accept: application/nostr+json` and parses the document. The client is
 * resolved per relay so callers can route Tor/proxy relays through a different
 * OkHttp instance (the same seam Amethyst's Nip11Retriever uses).
 */
class OkHttpNip11Fetcher(
    private val okHttpClient: (NormalizedRelayUrl) -> OkHttpClient,
) : Nip11Fetcher {
    override suspend fun fetch(relay: NormalizedRelayUrl): Nip11RelayInformation =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .header("Accept", "application/nostr+json")
                    .url(relay.toHttp())
                    .build()

            okHttpClient(relay).newCall(request).executeAsync().use { response ->
                if (!response.isSuccessful) {
                    throw Nip11FetchException("HTTP ${response.code} fetching NIP-11 from ${relay.url}")
                }
                val body = response.body.string()
                if (!body.startsWith("{")) {
                    throw Nip11FetchException("Not a NIP-11 document from ${relay.url}")
                }
                Nip11RelayInformation.fromJson(body)
            }
        }
}
