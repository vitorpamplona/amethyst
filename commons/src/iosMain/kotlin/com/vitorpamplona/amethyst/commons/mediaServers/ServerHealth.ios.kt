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
package com.vitorpamplona.amethyst.commons.mediaServers

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import io.ktor.client.request.head
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * The Blossom server reachability check of the JVM's MediaServerHealthProbe, over Ktor: a `HEAD`
 * to `/upload` (CDN-fronted hosts answer nothing at `/`), any answer counting as reachable, slow
 * past a second, offline past five, each answer reused for a minute.
 */
private object IosMediaServerHealth {
    const val SLOW_THRESHOLD_MS: Long = 1_000L
    const val PROBE_TIMEOUT_MS: Long = 5_000L
    const val CACHE_TTL_MS: Long = 60_000L

    class CachedResult(
        val status: ServerHealth,
        val atMs: Long,
    )

    val cache = ConcurrentMap<String, CachedResult>()

    fun cached(baseUrl: String): ServerHealth? {
        val entry = cache[baseUrl] ?: return null
        return if (TimeUtils.nowMillis() - entry.atMs < CACHE_TTL_MS) entry.status else null
    }
}

actual fun cachedMediaServerHealth(baseUrl: String): ServerHealth? = IosMediaServerHealth.cached(baseUrl)

actual suspend fun IRoleBasedHttpClientBuilder.probeMediaServer(baseUrl: String): ServerHealth {
    IosMediaServerHealth.cached(baseUrl)?.let { return it }
    val result =
        try {
            val startedAt = TimeUtils.nowMillis()
            // The status does not matter: /upload often answers 401, 404 or 405 without auth.
            withTimeout(IosMediaServerHealth.PROBE_TIMEOUT_MS) { ktorClientForPreview(baseUrl).head(BlossomServerUrl.upload(baseUrl)) }
            if (TimeUtils.nowMillis() - startedAt > IosMediaServerHealth.SLOW_THRESHOLD_MS) ServerHealth.Slow else ServerHealth.Online
        } catch (e: Exception) {
            // A timeout is a TimeoutCancellationException: offline, not a cancel of the caller.
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            ServerHealth.Offline
        }
    IosMediaServerHealth.cache[baseUrl] = IosMediaServerHealth.CachedResult(result, TimeUtils.nowMillis())
    return result
}
