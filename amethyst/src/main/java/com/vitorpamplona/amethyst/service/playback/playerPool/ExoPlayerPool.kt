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
) {
    private val playerPool = ConcurrentLinkedQueue<ExoPlayer>()
    private val poolStartingSize = 3

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
            while (playerPool.size < poolStartingSize) {
                playerPool.offer(builder.build(context))
                // Hand the frame back so an in-flight onGetSession / acquirePlayer / layout
                // pass isn't blocked behind the next build.
                yield()
            }
        }
    }

    fun acquirePlayer(context: Context): ExoPlayer {
        if (playerPool.isEmpty()) {
            // If the pool is empty, create a new player (or handle it differently)
            return builder.build(context)
        }

        return playerPool.poll() ?: builder.build(context)
    }

    fun releasePlayerAsync(player: ExoPlayer) {
        scope.launch {
            releasePlayer(player)
        }
    }

    suspend fun releasePlayer(player: ExoPlayer) {
        mutex.withLock {
            if (!player.isReleased) {
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

                if (playerPool.size < poolSize) {
                    if (!playerPool.contains(player)) {
                        playerPool.add(player)
                    }
                } else {
                    player.release() // Release if pool is full.
                }
            }
        }
    }

    fun destroy() {
        scope
            .launch {
                mutex.withLock {
                    playerPool.forEach { it.release() }
                    playerPool.clear()
                }
            }.invokeOnCompletion {
                scope.cancel()
            }
    }
}
