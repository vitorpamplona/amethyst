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

import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Warms the shared [NappletBlobCache] with a manifest's blobs ahead of launch, so opening a napplet /
 * nSite is instant (every file already verified on disk). Called from the browse/feed cards when they
 * render. Each blob is downloaded once (in-flight + on-disk de-duplicated), verified against the
 * manifest hash, and stored content-addressed — exactly what the on-device host serves.
 */
object NappletBlobPrefetcher {
    private const val MAX_PARALLEL = 5

    // De-dupes blobs already being fetched by another visible card in this process.
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Downloads every not-yet-cached blob in [paths] from [servers] (Tor-routed via [proxyPort]) into
     * the shared cache under [cacheDir]. Suspends on [Dispatchers.IO]; cancellation-aware so it stops
     * cleanly when the card scrolls out of composition.
     */
    suspend fun prefetch(
        paths: List<PathTag>,
        servers: List<String>,
        cacheDir: File,
        proxyPort: Int,
    ) {
        if (paths.isEmpty() || servers.isEmpty()) return

        withContext(Dispatchers.IO) {
            val cache = NappletBlobCache(NappletBlobCache.dirFor(cacheDir))
            val client = NappletBlobHttp.client(proxyPort)
            val gate = Semaphore(MAX_PARALLEL)

            // Fetch the entry document(s) first so first paint is fastest, then the rest in parallel
            // (bounded), instead of strictly sequentially.
            coroutineScope {
                paths
                    .sortedByDescending { it.path == "/" || it.path == "/index.html" }
                    .map { path ->
                        async {
                            gate.withPermit { fetchOne(path.hash.lowercase(), servers, cache, client) }
                        }
                    }.awaitAll()
            }
            cache.trimToSize(NappletBlobCache.DEFAULT_MAX_BYTES)
        }
    }

    private suspend fun fetchOne(
        hash: String,
        servers: List<String>,
        cache: NappletBlobCache,
        client: OkHttpClient,
    ) {
        if (cache.has(hash) || !inFlight.add(hash)) return
        try {
            for (url in StaticSiteResolver.candidateUrls(servers, hash)) {
                coroutineContext.ensureActive()
                val bytes = NappletBlobHttp.download(client, url) ?: continue
                if (StaticSiteResolver.verify(bytes, hash)) {
                    cache.put(hash, bytes)
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            inFlight.remove(hash)
        }
    }
}
