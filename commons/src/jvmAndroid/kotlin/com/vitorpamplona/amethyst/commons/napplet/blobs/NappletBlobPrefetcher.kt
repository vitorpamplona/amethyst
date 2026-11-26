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

import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Warms the shared [NappletBlobCache] with a manifest's blobs ahead of launch, so opening a napplet /
 * nSite is instant (every file already verified on disk). Called from the browse/feed cards when they
 * render. Each blob is downloaded once (in-flight + on-disk de-duplicated), verified against the
 * manifest hash, and stored content-addressed — exactly what the on-device host serves.
 *
 * The budgets, the plan and the checks are the shared [NappletBlobPrefetch]; this is its JVM entry,
 * downloading on [NappletBlobHttp]'s hardened OkHttp client.
 */
object NappletBlobPrefetcher {
    const val MAX_FILES_PER_CARD = NappletBlobPrefetch.MAX_FILES_PER_CARD
    const val MAX_SERVERS = NappletBlobPrefetch.MAX_SERVERS
    const val MAX_PREFETCH_BLOB_BYTES = NappletBlobPrefetch.MAX_PREFETCH_BLOB_BYTES
    const val MAX_CARD_BYTES = NappletBlobPrefetch.MAX_CARD_BYTES
    const val MAX_WINDOW_BYTES = NappletBlobPrefetch.MAX_WINDOW_BYTES

    // Clearnet and the current Tor port, with room for a change of port mid-launch.
    private const val MAX_CLIENTS = 3

    // One client per route, for the life of the process: cards reuse its connections (over Tor, its
    // circuits and TLS sessions) instead of building a client, pool and dispatcher each.
    private val clients = ConcurrentHashMap<Int, OkHttpClient>()

    /**
     * Downloads every not-yet-cached blob in [paths] from [servers] (Tor-routed via [proxyPort]) into
     * the site's partition of the cache under [cacheDir]. Suspends on [Dispatchers.IO] and stops,
     * downloads included, when the card scrolls out of composition.
     */
    suspend fun prefetch(
        paths: List<PathTag>,
        servers: List<String>,
        cacheDir: File,
        proxyPort: Int,
        // The site's cache partition: NappletBlobCache.partitionOf, as its launch computes it.
        partition: String,
    ) {
        val allowed = prefetchServers(servers)
        if (paths.isEmpty() || allowed.isEmpty()) return

        withContext(Dispatchers.IO) {
            val cache = NappletBlobCache(NappletBlobCache.dirFor(cacheDir.toOkioPath())).forSite(partition)
            val client = clients.getOrPut(proxyPort) { NappletBlobHttp.client(proxyPort, publicOnly = true) }
            // Tor's port changes when it restarts (desktop picks one each time): keep the client of
            // the route in use, not one per port ever seen.
            if (clients.size > MAX_CLIENTS) clients.keys.removeIf { it != proxyPort }
            if (prefetchWith(paths, allowed, cache, client, partition)) NappletBlobPrefetch.trimNowAndThen(cache)
        }
    }

    /** The servers a card may prefetch from: https only (a stranger's note says where), and only the first few. */
    internal fun prefetchServers(servers: List<String>): List<String> = servers.filter { it.toHttpUrlOrNull()?.scheme == "https" }.take(MAX_SERVERS)

    /** The files a card warms: the entry document first, then the rest in manifest order, at most [MAX_FILES_PER_CARD]. */
    internal fun plan(paths: List<PathTag>): List<PathTag> = NappletBlobPrefetch.plan(paths)

    /** Whether it downloaded anything. Stops once the card's or the window's byte budget is spent. */
    internal suspend fun prefetchWith(
        paths: List<PathTag>,
        servers: List<String>,
        cache: NappletBlobCache,
        client: OkHttpClient,
        partition: String,
        now: () -> Long = System::currentTimeMillis,
    ): Boolean =
        NappletBlobPrefetch.prefetchWith(paths, servers, cache, partition, download = { url, maxBytes ->
            val download = NappletBlobHttp.downloadCounted(client, url, maxBytes)
            NappletPrefetchDownload(download.bytes, download.received)
        }, now = now)

    /** Forgets the window's spending (tests). */
    internal fun resetBudget() = NappletBlobPrefetch.resetBudget()
}
