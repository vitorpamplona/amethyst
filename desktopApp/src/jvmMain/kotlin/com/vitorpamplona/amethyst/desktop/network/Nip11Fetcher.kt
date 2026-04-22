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
package com.vitorpamplona.amethyst.desktop.network

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches and caches NIP-11 relay information documents.
 * Session-only cache (no disk persistence).
 *
 * Uses fail-closed HTTP client to prevent IP leaks during Tor bootstrap.
 * Limits response body to 256KB to prevent DoS from malicious relays.
 * Deduplicates concurrent fetches via per-URL Mutex.
 */
class Nip11Fetcher {
    private val cache = ConcurrentHashMap<NormalizedRelayUrl, Nip11RelayInformation>()
    private val locks = ConcurrentHashMap<NormalizedRelayUrl, Mutex>()

    companion object {
        private const val MAX_RESPONSE_BYTES = 256 * 1024L
        private val SEMAPHORE = Semaphore(5)
    }

    suspend fun fetch(url: NormalizedRelayUrl): Nip11RelayInformation? {
        cache[url]?.let { return it }

        val mutex = locks.getOrPut(url) { Mutex() }
        return mutex.withLock {
            cache[url]?.let { return it } // double-check after lock

            SEMAPHORE
                .withPermit {
                    withContext(Dispatchers.IO) {
                        fetchFromNetwork(url)
                    }
                }?.also { info ->
                    cache[url] = info
                }
        }
    }

    private fun fetchFromNetwork(url: NormalizedRelayUrl): Nip11RelayInformation? {
        // FAIL-CLOSED: use currentClient() not getHttpClient()
        val client = DesktopHttpClient.currentClient()
        val httpUrl = url.toHttp()
        val request =
            Request
                .Builder()
                .url(httpUrl)
                .header("Accept", "application/nostr+json")
                .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val source = response.body.source()
                    source.request(MAX_RESPONSE_BYTES) // buffer up to limit
                    val body = source.readUtf8()
                    Nip11RelayInformation.fromJson(body)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getCached(url: NormalizedRelayUrl): Nip11RelayInformation? = cache[url]

    fun clearCache() {
        cache.clear()
    }
}
