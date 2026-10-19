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
package com.vitorpamplona.amethyst.service.playback.diskCache

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Downloads the first [PREFETCH_BYTES] of the videos a feed is about to show into [videoCache], so
 * the player that opens one when its card is composed reads the start from disk instead of waiting
 * on the network for its first bytes and the container header.
 *
 * Only the head: an mp4's header and the buffer a player needs before it starts, not the whole
 * file. [WORKERS] downloads at a time, nearest the viewport first, so it never takes more than that
 * many connections from the video on screen. HLS is never sent here (its playlist and segments are
 * separate requests, and a live playlist must not be cached).
 *
 * A download keeps going when its card reaches the screen: that is when the player needs it, and
 * cancelling there would throw away the bytes just before they are read. Only a feed that leaves
 * (an empty list from every feed) stops the ones in flight.
 */
@OptIn(UnstableApi::class)
class VideoPrefetcher(
    private val videoCache: VideoCache,
    private val upstream: (useProxy: Boolean) -> DataSource.Factory,
    private val scope: CoroutineScope,
) {
    /** A video to prefetch, and whether it must go through the Tor proxy, as the player would. */
    data class Video(
        val url: String,
        val useProxy: Boolean,
    )

    private val lock = Any()

    // Each feed's wanted list (feed order), keyed by the feed asking. Guarded by [lock].
    private val requests = LinkedHashMap<Any, List<Video>>()

    // URLs already tried, so a failing or tiny video isn't fetched on every scroll. Bounded LRU.
    // Guarded by [lock].
    private val attempted =
        object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_REMEMBERED
        }

    // Downloads in flight. Guarded by [lock].
    private val running = mutableSetOf<Job>()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var workers: List<Job>? = null

    /** Replaces [owner]'s wanted list with [videos], nearest first; an empty list withdraws it. */
    fun prefetch(
        owner: Any,
        videos: List<Video>,
    ) {
        synchronized(lock) {
            requests.remove(owner)
            if (videos.isNotEmpty()) requests[owner] = videos
            if (requests.isEmpty()) running.forEach { it.cancel() }
            if (workers == null) workers = List(WORKERS) { scope.launch { work() } }
        }
        // Every worker is waiting on the same conflated channel, so wake one per slot.
        repeat(WORKERS) { wake.trySend(Unit) }
    }

    // The most recent feed's list first. Callers hold [lock].
    private fun wanted(): List<Video> =
        requests.values
            .reversed()
            .flatten()
            .distinctBy { it.url }

    private fun next(): Video? =
        synchronized(lock) {
            wanted().firstOrNull { it.url !in attempted }?.also { attempted[it.url] = Unit }
        }

    private suspend fun work() {
        for (signal in wake) {
            while (true) {
                val video = next() ?: break
                val job = scope.launch { fetchHead(video) }
                synchronized(lock) { running.add(job) }
                job.join()
                synchronized(lock) {
                    running.remove(job)
                    // Cancelled because the feed left: let a later visit retry it.
                    if (job.isCancelled) attempted.remove(video.url)
                }
            }
        }
    }

    private suspend fun fetchHead(video: Video) {
        val cache = videoCache.simpleCache
        // Already there: an earlier visit, or the player of a card on screen is loading it now.
        if (cache.getCachedBytes(video.url, 0, PREFETCH_BYTES) > 0) return

        val source =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream(video.useProxy))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()

        val spec =
            DataSpec
                .Builder()
                .setUri(video.url)
                .setPosition(0)
                .setLength(PREFETCH_BYTES)
                .build()

        val start = SystemClock.elapsedRealtime()
        val writer = CacheWriter(source, spec, null, null)
        try {
            runInterruptible { writer.cache() }
            Log.d(PLAYBACK_DIAG_TAG) { "PREFETCH ${cache.getCachedBytes(video.url, 0, PREFETCH_BYTES)} bytes in ${SystemClock.elapsedRealtime() - start}ms ${video.url}" }
        } catch (e: CancellationException) {
            writer.cancel()
            throw e
        } catch (e: Exception) {
            Log.d(PLAYBACK_DIAG_TAG) { "PREFETCH failed ${video.url}: ${e.message}" }
        }
    }

    companion object {
        // An mp4's header plus what a player buffers before it starts (750 ms): a few seconds of
        // the ~0.4-0.7 Mbps clips most feed videos are, and still the start of an 8 Mbps 1080p one.
        const val PREFETCH_BYTES = 512L * 1024

        // Downloads at once. One fell behind a scroll: a head took 0.5-3 s on the feed's hosts.
        private const val WORKERS = 2

        private const val MAX_REMEMBERED = 500
    }
}
