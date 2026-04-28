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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
class ExoPlayerPool(
    val builder: ExoPlayerBuilder,
    private val poolSize: Int,
    // Requested ceiling on paused-with-buffer players retained across releases. Each retained
    // player keeps its decoder and LoadControl buffer alive, which costs both memory and a
    // MediaCodec instance, so warm slots count against the same [poolSize] codec budget as
    // cold players (see [warmSlotsCap]). Default 3 keeps the most recent few feed videos hot
    // for scroll-back without monopolizing the device's decoder pool.
    requestedWarmSlots: Int = DEFAULT_WARM_SLOTS,
) {
    // Cap warm slots at poolSize-1 so there's always at least one slot available for a cold
    // (cleared) player; otherwise a feed full of unique URIs would starve the cold pool and
    // every new URI would force a fresh ExoPlayer build.
    private val warmSlotsCap = requestedWarmSlots.coerceAtMost((poolSize - 1).coerceAtLeast(0))

    // Idle players that have been stop()'d and clearMediaItems()'d — ready to be re-prepared
    // with any URI. Maintained as a FIFO so the oldest cleared instance is reused first.
    private val coldPool = ConcurrentLinkedQueue<ExoPlayer>()
    private val poolStartingSize = 3

    // Most-recent paused players, indexed by the mediaId of the MediaItem they still hold.
    // ArrayDeque is used as an LRU: head = oldest, tail = newest. Access is guarded by
    // [warmPoolLock] (a plain monitor, since both acquire and release callers run on the
    // service's main thread but we don't want to require the suspending [mutex] in acquire).
    private data class WarmPlayer(
        val mediaId: String,
        val player: ExoPlayer,
    )

    private val warmPool = ArrayDeque<WarmPlayer>(warmSlotsCap.coerceAtLeast(1))
    private val warmPoolLock = Any()

    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("PlaybackService", "Caught exception: ${throwable.message}", throwable)
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    private val mutex = Mutex()

    // Guards against firing the warmup more than once if create() is called repeatedly
    // (e.g. on reconfiguration or when both pools share a startup hook).
    private val warmupStarted = AtomicBoolean(false)

    /**
     * Pre-warms the pool with [poolStartingSize] ExoPlayer instances on the main looper, yielding
     * between each build so the warmup is spread across frames instead of stalling the UI in one
     * burst. ExoPlayer must be constructed on the same thread that will operate it (the main
     * thread for this pool), so we cannot fan out across IO threads here. Idempotent — additional
     * calls are no-ops.
     */
    fun create(context: Context) {
        if (!warmupStarted.compareAndSet(false, true)) return
        scope.launch {
            while (coldPool.size < poolStartingSize) {
                coldPool.offer(builder.build(context))
                // Hand the frame back so an in-flight onGetSession / acquirePlayer / layout
                // pass isn't blocked behind the next build.
                yield()
            }
        }
    }

    /**
     * Acquire a player. When [preferredMediaId] matches a warm entry, returns that player intact
     * — it still holds its MediaItem and any populated LoadControl buffer, so the caller can
     * skip [androidx.media3.common.Player.setMediaItem] / [androidx.media3.common.Player.prepare]
     * and resume immediately. Falls back to a cold (cleared) player or a freshly built one.
     */
    fun acquirePlayer(
        context: Context,
        preferredMediaId: String? = null,
    ): ExoPlayer {
        if (preferredMediaId != null) {
            val warm = takeWarm(preferredMediaId)
            if (warm != null) {
                Log.d("PlaybackService") { "ExoPlayerPool warm hit: $preferredMediaId" }
                return warm
            }
        }
        return coldPool.poll() ?: builder.build(context)
    }

    private fun takeWarm(mediaId: String): ExoPlayer? =
        synchronized(warmPoolLock) {
            // Iterate from the newest end so a duplicated URI returns the freshest player.
            val it = warmPool.listIterator(warmPool.size)
            while (it.hasPrevious()) {
                val entry = it.previous()
                if (entry.mediaId == mediaId) {
                    it.remove()
                    return@synchronized entry.player
                }
            }
            null
        }

    fun releasePlayerAsync(player: ExoPlayer) {
        scope.launch {
            releasePlayer(player)
        }
    }

    suspend fun releasePlayer(player: ExoPlayer) {
        mutex.withLock {
            if (player.isReleased) return@withLock

            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null && warmSlotsCap > 0) {
                // Warm path: keep the player paused but loaded so a quick scroll-back to the
                // same video resumes from the existing buffer instead of re-fetching from disk
                // cache and re-priming the decoder.
                player.pause()
                val evicted = pushWarm(mediaId, player)
                if (evicted != null) {
                    Log.d("PlaybackService") { "ExoPlayerPool warm evict: ${evicted.mediaId}" }
                    demoteToCold(evicted.player)
                }
                return@withLock
            }

            demoteToCold(player)
        }
    }

    private fun pushWarm(
        mediaId: String,
        player: ExoPlayer,
    ): WarmPlayer? =
        synchronized(warmPoolLock) {
            // If the same URI is already warm (rare — duplicate VideoView in another scroller),
            // drop the older entry so it can be demoted; the freshest copy wins.
            val duplicate = warmPool.indexOfFirst { it.mediaId == mediaId }
            val displaced =
                if (duplicate >= 0) {
                    warmPool.removeAt(duplicate)
                } else if (warmPool.size >= warmSlotsCap) {
                    warmPool.removeFirst()
                } else {
                    null
                }
            warmPool.addLast(WarmPlayer(mediaId, player))
            displaced
        }

    private fun demoteToCold(player: ExoPlayer) {
        if (player.isReleased) return
        player.pause()
        player.stop()
        player.clearVideoSurface()
        player.clearMediaItems()

        // Clear any video quality overrides so the next video starts with Auto
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()

        // Total idle (cold + warm) must respect the device-derived poolSize cap so we don't
        // exceed the MediaCodec instance budget. Warm slots get first dibs; cold gets the rest.
        val warmSize = synchronized(warmPoolLock) { warmPool.size }
        val coldCap = (poolSize - warmSize).coerceAtLeast(0)
        if (coldPool.size < coldCap) {
            if (!coldPool.contains(player)) {
                coldPool.add(player)
            }
        } else {
            player.release() // Release if pool is full.
        }
    }

    fun destroy() {
        scope
            .launch {
                mutex.withLock {
                    val warmSnapshot =
                        synchronized(warmPoolLock) {
                            val copy = warmPool.toList()
                            warmPool.clear()
                            copy
                        }
                    warmSnapshot.forEach { it.player.release() }
                    coldPool.forEach { it.release() }
                    coldPool.clear()
                }
            }.invokeOnCompletion {
                scope.cancel()
            }
    }

    companion object {
        private const val DEFAULT_WARM_SLOTS = 3
    }
}
