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
package com.vitorpamplona.amethyst.commons.video.cache

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Downloads the head of the videos a feed is about to show (see `NotePlatform.prefetchVideos`),
 * by the rules of Android's `VideoPrefetcher`: [fetchHead] takes only the first
 * [PREFETCH_BYTES] of a file; [workers] downloads at a time, nearest the viewport first; the most
 * recent feed's list goes first; a URL is tried once (again [RETRY_FAILED_MS] after a failure);
 * and a download keeps going when its card reaches the screen, since that is when the player
 * reads it. Only every feed leaving (an empty list from each) stops the ones in flight, and those
 * may be tried again on a later visit. HLS never comes here; the callers filter it.
 *
 * Android's Tor rule (one proxied head at a time) has no counterpart: iOS has no Tor routing.
 */
class VideoHeadPrefetcher(
    private val scope: CoroutineScope,
    private val workers: Int = WORKERS,
    private val now: () -> Long = { TimeUtils.nowMillis() },
    /** Caches the head of a URL; false when the download failed. */
    private val fetchHead: suspend (String) -> Boolean,
) {
    private val lock = KmpLock()

    // Each feed's wanted list (feed order), keyed by the feed asking. Guarded by [lock].
    private val requests = LinkedHashMap<Any, List<String>>()

    // URLs already tried, each with the time it may be tried again: never while in flight or done,
    // a few minutes after a failure. Oldest first, bounded by [MAX_REMEMBERED]. Guarded by [lock].
    private val attempted = LinkedHashMap<String, Long>()

    // Downloads in flight. Guarded by [lock].
    private val running = mutableSetOf<Job>()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var started = false

    /** Replaces [owner]'s wanted list with [urls], nearest first; an empty list withdraws it. */
    fun prefetch(
        owner: Any,
        urls: List<String>,
    ) {
        lock.withLock {
            requests.remove(owner)
            if (urls.isNotEmpty()) requests[owner] = urls
            if (requests.isEmpty()) running.forEach { it.cancel() }
            if (!started) {
                started = true
                repeat(workers) { scope.launch { work() } }
            }
        }
        // Every worker waits on the same conflated channel, so wake one per slot.
        repeat(workers) { wake.trySend(Unit) }
    }

    /** The next URL to download, marked in flight; null when everything wanted was tried. */
    internal fun next(): String? =
        lock.withLock {
            val time = now()
            requests.values
                .reversed()
                .asSequence()
                .flatten()
                .distinct()
                .firstOrNull { (attempted[it] ?: 0L) <= time }
                ?.also { remember(it, Long.MAX_VALUE) }
        }

    private fun remember(
        url: String,
        retryAt: Long,
    ) {
        attempted.remove(url)
        attempted[url] = retryAt
        if (attempted.size > MAX_REMEMBERED) {
            val iterator = attempted.keys.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    private suspend fun work() {
        for (signal in wake) {
            while (true) {
                val url = next() ?: break
                var failed = false
                val job =
                    scope.launch {
                        failed =
                            try {
                                !fetchHead(url)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.d(TAG) { "PREFETCH failed $url: ${e.message}" }
                                true
                            }
                    }
                lock.withLock { running.add(job) }
                job.join()
                lock.withLock {
                    running.remove(job)
                    when {
                        // Cancelled because the feed left: let a later visit retry it.
                        job.isCancelled -> attempted.remove(url)
                        failed -> remember(url, now() + RETRY_FAILED_MS)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "VideoHeadPrefetcher"

        /** Android's head: an mp4's header plus what a player buffers before it starts. */
        const val PREFETCH_BYTES = 512L * 1024

        // Downloads at once, as on Android: one fell behind a scroll there.
        const val WORKERS = 2

        private const val MAX_REMEMBERED = 500

        const val RETRY_FAILED_MS = 5 * 60 * 1000L
    }
}
