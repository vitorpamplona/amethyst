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

import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * What one prefetch download brought in: the body when it fit the cap and the status was a success,
 * and how many bytes came in either way (the budget pays for those too).
 */
class NappletPrefetchDownload(
    val bytes: ByteArray?,
    val received: Long,
)

/**
 * The part of warming the [NappletBlobCache] ahead of a launch that is the same on every platform:
 * which files a card warms, the byte budgets per card and per window, the one download gate, the
 * in-flight de-duplication and the hash check. Each platform hands it the download itself:
 * `NappletBlobPrefetcher` (JVM) its hardened OkHttp client, iOS a [NappletHttp] ([downloadWith]).
 *
 * A card runs because it scrolled by, not because the user asked, and its manifest is a stranger's:
 * it may list any number of files and servers. So a card warms only the start of a site (its entry
 * document first) and stops past a byte budget; a file too big for a prefetch is fetched when the
 * site is opened. All cards share one download gate and one budget per window, so a note quoting a
 * dozen sites costs no more than one.
 */
object NappletBlobPrefetch {
    private const val MAX_PARALLEL = 5

    const val MAX_FILES_PER_CARD = 16
    const val MAX_SERVERS = 4
    const val MAX_PREFETCH_BLOB_BYTES = 4L * 1024 * 1024
    const val MAX_CARD_BYTES = 8L * 1024 * 1024
    const val MAX_WINDOW_BYTES = 64L * 1024 * 1024
    private const val WINDOW_MS = 10L * 60 * 1000

    /** A prefetch download's whole call: a card that scrolled by never waits long on a stranger's server. */
    const val PREFETCH_TIMEOUT_MS = 45_000L

    // The whole cache is walked to trim it: not after every card, at most this often.
    private const val TRIM_INTERVAL_MS = 10L * 60 * 1000

    private val gate = Semaphore(MAX_PARALLEL)
    private val lock = KmpLock()
    private var windowStart = 0L
    private var windowBytes = 0L
    private var lastTrim = 0L

    // De-dupes blobs already being fetched for the same site by another visible card in this process.
    private val inFlight = ConcurrentSet<String>()

    /**
     * The servers a card may prefetch from on a platform without OkHttp's URL parser: https only (a
     * stranger's note says where), with a host, and only the first few.
     */
    fun prefetchServers(servers: List<String>): List<String> =
        servers
            .filter {
                val url = it.trim()
                url.startsWith("https://", ignoreCase = true) && url.length > "https://".length && url["https://".length] != '/'
            }.take(MAX_SERVERS)

    /** The files a card warms: the entry document first, then the rest in manifest order, at most [MAX_FILES_PER_CARD]. */
    fun plan(paths: List<PathTag>): List<PathTag> =
        paths
            .sortedByDescending { it.path == "/" || it.path == "/index.html" }
            .distinctBy { it.hash.lowercase() }
            .take(MAX_FILES_PER_CARD)

    /**
     * Warms [cache] (the site's partition, [partition]) with [paths] from [servers], already filtered by
     * the platform ([prefetchServers]), through [download] (a GET capped at its `maxBytes`). Whether it
     * downloaded anything; it stops once the card's or the window's byte budget is spent, and trims the
     * whole cache now and then afterwards.
     */
    suspend fun prefetchWith(
        paths: List<PathTag>,
        servers: List<String>,
        cache: NappletBlobCache,
        partition: String,
        download: suspend (url: String, maxBytes: Long) -> NappletPrefetchDownload,
        now: () -> Long = TimeUtils::nowMillis,
    ): Boolean {
        if (paths.isEmpty() || servers.isEmpty()) return false
        val cardLock = KmpLock()
        var cardBytes = 0L
        val downloaded =
            coroutineScope {
                plan(paths)
                    .map { path ->
                        async {
                            gate.withPermit {
                                fetchOne(
                                    path.hash.lowercase(),
                                    servers,
                                    cache,
                                    partition,
                                    download,
                                    hasRoom = { cardLock.withLock { cardBytes } < MAX_CARD_BYTES && windowHasRoom(now()) },
                                    onReceived = { bytes ->
                                        cardLock.withLock { cardBytes += bytes }
                                        lock.withLock { windowBytes += bytes }
                                    },
                                )
                            }
                        }
                    }.awaitAll()
            }
        return downloaded.any { it }
    }

    /** [prefetchWith] over [http]: a public-only GET, through Tor when [viaTor], with no redirects followed past what the platform allows. */
    suspend fun prefetchWith(
        paths: List<PathTag>,
        servers: List<String>,
        cache: NappletBlobCache,
        partition: String,
        http: NappletHttp,
        viaTor: Boolean,
    ): Boolean = prefetchWith(paths, servers, cache, partition, downloadWith(http, viaTor))

    /** A prefetch download on [http]: public addresses only, capped, and any failure is an empty download. */
    fun downloadWith(
        http: NappletHttp,
        viaTor: Boolean,
    ): suspend (String, Long) -> NappletPrefetchDownload =
        { url, maxBytes ->
            try {
                val response = http.get(NappletHttpRequest(url, viaTor = viaTor, maxBytes = maxBytes, timeoutMs = PREFETCH_TIMEOUT_MS, publicOnly = true))
                NappletPrefetchDownload(response.body?.takeIf { response.isSuccessful && !response.tooLarge }, response.received)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NappletPrefetchDownload(null, 0)
            }
        }

    /** Trims the whole cache when the last trim is old enough. */
    fun trimNowAndThen(
        cache: NappletBlobCache,
        now: Long = TimeUtils.nowMillis(),
    ) {
        val due =
            lock.withLock {
                if (now - lastTrim >= TRIM_INTERVAL_MS) {
                    lastTrim = now
                    true
                } else {
                    false
                }
            }
        if (due) cache.trimToSize(NappletBlobCache.DEFAULT_MAX_BYTES)
    }

    private fun windowHasRoom(now: Long): Boolean =
        lock.withLock {
            if (now - windowStart >= WINDOW_MS) {
                windowStart = now
                windowBytes = 0
            }
            windowBytes < MAX_WINDOW_BYTES
        }

    /** Forgets the window's spending (tests). */
    fun resetBudget() =
        lock.withLock {
            windowStart = 0
            windowBytes = 0
        }

    /**
     * Whether it stored [hash]. Every server it tries is a download the budget pays for: [hasRoom] is
     * asked before each, and [onReceived] told what each brought in, kept or not, so a server that
     * answers with junk or too much spends the budget like one that answers right.
     */
    private suspend fun fetchOne(
        hash: String,
        servers: List<String>,
        cache: NappletBlobCache,
        partition: String,
        download: suspend (String, Long) -> NappletPrefetchDownload,
        hasRoom: () -> Boolean,
        onReceived: (Long) -> Unit,
    ): Boolean {
        val key = "$partition|$hash"
        if (cache.has(hash) || !inFlight.add(key)) return false
        try {
            for (url in StaticSiteResolver.candidateUrls(servers, hash)) {
                if (!hasRoom()) return false
                val result = download(url, MAX_PREFETCH_BLOB_BYTES)
                onReceived(result.received)
                val bytes = result.bytes ?: continue
                if (StaticSiteResolver.verify(bytes, hash)) {
                    cache.putVerified(hash, bytes)
                    return true
                }
            }
            return false
        } finally {
            inFlight.remove(key)
        }
    }
}
