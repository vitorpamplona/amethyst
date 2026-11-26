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
package com.vitorpamplona.amethyst.ios.playback.cache

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.amethyst.commons.video.cache.CachedRangeReader
import com.vitorpamplona.amethyst.commons.video.cache.KtorRangeSource
import com.vitorpamplona.amethyst.commons.video.cache.NotCacheableException
import com.vitorpamplona.amethyst.commons.video.cache.SparseVideoCache
import com.vitorpamplona.amethyst.commons.video.cache.VideoCacheBudget
import com.vitorpamplona.amethyst.commons.video.cache.VideoHeadPrefetcher
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import io.ktor.client.HttpClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.resourceLoader
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.darwin.dispatch_queue_create

/**
 * The disk cache of iOS's videos, Android's `VideoCache` + `VideoPrefetcher` on AVFoundation.
 *
 * A cacheable video's asset gets a URL with [SCHEME_PREFIX] in front (`amethyst-cache+https://…`),
 * a scheme AVFoundation cannot load itself, so it asks [VideoResourceLoader] for every byte range,
 * which serves them from a [SparseVideoCache] and fetches the gaps with range requests. Feeds
 * download the head of the videos below the screen into the same cache ([prefetch]).
 *
 * Kept off the cache, on the plain URL: HLS (a playlist names other files, a live one changes),
 * live streams, and anything not http(s). A URL whose server turns out to send a playlist, or no
 * length, is redirected to its plain URL and stays off the cache from then on.
 *
 * Tor: the cache fetches every byte through the video role's client, so a cached video follows
 * the Tor settings. AVFoundation fetching a plain URL itself does not (AVURLAsset takes no proxy),
 * so a video that must go through Tor and would play off the cache is refused instead
 * ([wouldBypassTor]) until HLS gets its own route (plans/2026-11-25-ios-tor.md, B12e).
 *
 * The files live in `Caches/videos` (iOS may purge them when the disk fills), sized by Android's
 * rule ([VideoCacheBudget]): a fifth of the free space, 256 MB to 4 GB. Nothing here touches the
 * disk until a background thread needs it: [assetFor] runs on the main thread.
 */
@OptIn(ExperimentalForeignApi::class)
object IosVideoCache {
    private const val TAG = "IosVideoCache"

    /** What a cached asset's URL starts with, before the original `https://…`. */
    const val SCHEME_PREFIX = "amethyst-cache+"

    private const val MAX_REMEMBERED = 500

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Guarded by [lock].
    private val lock = KmpLock()
    private var client: ((url: String) -> HttpClient)? = null
    private var requiresTor: (url: String) -> Boolean = { false }
    private val declaredTypes = LinkedHashMap<String, String>()
    private val refused = LinkedHashMap<String, Unit>()

    /** Opened on first use, from a background thread: it reads the index from disk. */
    internal val cache: SparseVideoCache by lazy {
        val dir = cacheDirectory()
        val budget = VideoCacheBudget.forAvailableBytes(freeBytes(dir))
        Log.d(TAG) { "Video cache at $dir, ${budget / (1024 * 1024)} MB" }
        SparseVideoCache(FileSystem.SYSTEM, dir, budget, now = { TimeUtils.nowMillis() })
    }

    internal val reader: CachedRangeReader by lazy {
        CachedRangeReader(cache, KtorRangeSource { url -> (lock.withLock { client } ?: error("IosVideoCache is not installed"))(url) })
    }

    // The resource loader keeps its delegate weakly: this holds it for every asset.
    private val loader = VideoResourceLoader(scope)
    private val loaderQueue = dispatch_queue_create("com.vitorpamplona.amethyst.video-cache", null)

    private val prefetcher = VideoHeadPrefetcher(scope) { url -> fetchHead(url) }

    /**
     * Starts caching, each URL's bytes over the client [httpClient] picks for it. Until then every
     * video plays from its plain URL. [requiresTor] says which URLs must go through Tor.
     */
    fun install(
        httpClient: (url: String) -> HttpClient,
        requiresTor: (url: String) -> Boolean,
    ) {
        lock.withLock {
            client = httpClient
            this.requiresTor = requiresTor
        }
    }

    private fun isInstalled() = lock.withLock { client != null }

    /**
     * Whether playing [url] would have AVFoundation fetch it directly although it must go through
     * Tor: an http(s) URL that is off the cache (HLS, a live stream, a server the cache refused).
     * The player refuses those rather than let them leave outside Tor. A local file never leaves.
     */
    fun wouldBypassTor(
        url: String,
        cacheable: Boolean,
    ): Boolean {
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return false
        val (mustUseTor, viaCache) = lock.withLock { requiresTor(url) to (cacheable && client != null && url !in refused) }
        return mustUseTor && !viaCache
    }

    /** Whether [url] must go through Tor under the current settings. */
    internal fun mustUseTor(url: String): Boolean = lock.withLock { requiresTor }(url)

    /** The NSError domain of a video refused because it would have left outside Tor. */
    const val TOR_REFUSAL_DOMAIN = "com.vitorpamplona.amethyst.TorRefusal"

    /** What a loading request for a Tor-routed video AVFoundation would fetch itself ends with. */
    internal fun torRefusalError(url: String): NSError =
        NSError.errorWithDomain(
            TOR_REFUSAL_DOMAIN,
            1,
            mapOf<Any?, Any?>(NSLocalizedDescriptionKey to "$url must go through Tor, which this player cannot route"),
        )

    /**
     * Whether a video may go through the cache: an http(s) file, not HLS by its URL or the type its
     * event declared, and not a live stream.
     */
    fun isCacheable(
        url: String,
        mimeType: String?,
        isLiveStream: Boolean,
    ): Boolean {
        if (isLiveStream || isHlsMedia(url, mimeType)) return false
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return false
        return lock.withLock { url !in refused }
    }

    /** Remembers the type [url]'s event declared, for a server that sends none worth using. */
    fun declareType(
        url: String,
        mimeType: String?,
    ) {
        if (mimeType.isNullOrBlank()) return
        lock.withLock { remember(declaredTypes, url, mimeType) }
    }

    internal fun declaredType(url: String): String? = lock.withLock { declaredTypes[url] }

    /** Keeps [url] off the cache from now on: its server sends a playlist, or no length. */
    internal fun refuse(url: String) {
        Log.d(TAG) { "Not caching $url" }
        lock.withLock { remember(refused, url, Unit) }
    }

    private fun <V> remember(
        map: LinkedHashMap<String, V>,
        url: String,
        value: V,
    ) {
        map.remove(url)
        map[url] = value
        if (map.size > MAX_REMEMBERED) map.keys.firstOrNull()?.let { map.remove(it) }
    }

    /**
     * The asset to play [url] with: reading through the cache when [cacheable] and the cache is
     * installed, the plain URL otherwise. Cheap: safe on the main thread.
     */
    fun assetFor(
        url: NSURL,
        cacheable: Boolean,
    ): AVURLAsset {
        val original = url.absoluteString
        val cachedUrl =
            if (cacheable && original != null && isInstalled() && lock.withLock { original !in refused }) {
                NSURL.URLWithString(SCHEME_PREFIX + original)
            } else {
                null
            }
        if (cachedUrl == null) return AVURLAsset(uRL = url, options = null)
        val asset = AVURLAsset(uRL = cachedUrl, options = null)
        asset.resourceLoader.setDelegate(loader, loaderQueue)
        return asset
    }

    /** Android's `prefetchVideos`: the heads of [urls], nearest first, for [owner]'s feed. */
    fun prefetch(
        owner: Any,
        urls: List<String>,
    ) {
        if (!isInstalled()) return
        prefetcher.prefetch(owner, urls.filter { isCacheable(it, declaredType(it), isLiveStream = false) })
    }

    private suspend fun fetchHead(url: String): Boolean {
        // Already there, from an earlier visit or a player. A partial head goes on: only the
        // missing ranges are fetched.
        val length = cache.length(url)
        val head = if (length == null) VideoHeadPrefetcher.PREFETCH_BYTES else minOf(VideoHeadPrefetcher.PREFETCH_BYTES, length)
        if (cache.covers(url, 0, head)) return true
        val start = TimeUtils.nowMillis()
        return try {
            reader.read(url, 0, VideoHeadPrefetcher.PREFETCH_BYTES, null)
            Log.d(TAG) { "PREFETCH ${cache.runEnd(url, 0)} bytes in ${TimeUtils.nowMillis() - start}ms $url" }
            true
        } catch (e: NotCacheableException) {
            refuse(url)
            true
        }
    }

    private fun cacheDirectory(): Path {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
        return (caches ?: NSTemporaryDirectory()).toPath() / "videos"
    }

    private fun freeBytes(dir: Path): Long {
        val probe = dir.parent?.toString() ?: dir.toString()
        val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(probe, error = null)
        return (attributes?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L
    }
}
