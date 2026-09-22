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
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.vitorpamplona.amethyst.commons.service.http.DynamicCallFactory
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.uploads.blossom.bud10.BlossomServerResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Process-wide owner of the video [ExoPlayerPool]s.
 *
 * This used to live inside PlaybackService, which forced every feed video through a MediaSession
 * (and a MediaController IPC bind) just to get hold of a player. Players are not a media-session
 * concern: the decoder budget they compete for is a **process** resource — [ExoPlayerPool] already
 * counts it in process-wide statics — and playback has to survive both an Activity dying and the
 * user switching accounts. So the pools belong to AppModules, and the service is left owning only
 * the one MediaSession that a promoted playback claims.
 *
 * Two pools, because the choice of proxied vs direct traffic is baked into the player when it is
 * built (the DataSource factory goes into CustomMediaSourceFactory), so a player built for one
 * route can never be handed to the other. That is also why a player must go home to the pool it
 * came from — see [PooledPlayer].
 */
@OptIn(UnstableApi::class)
class VideoPlayerPools(
    private val appContext: Context,
    private val videoCache: () -> VideoCache,
    private val callFactory: (proxied: Boolean) -> DynamicCallFactory,
    private val blossomResolver: () -> BlossomServerResolver,
) {
    // The device's concurrent-decoder ceiling bounds how many players may be checked out at once
    // and how many the pool may retain, since each one pins a MediaCodec instance.
    private val decoderBudget by lazy { SimultaneousPlaybackCalculator.max(appContext) }

    // Volatile because [trimMemory] and [destroy] are not main-thread callers: AppModules.trim runs
    // from the heap-pressure watchdog on the IO scope, and a stale null read there would silently
    // skip the reclaim it was woken up to do.
    @Volatile private var direct: ExoPlayerPool? = null

    @Volatile private var proxied: ExoPlayerPool? = null

    private fun newPool(proxied: Boolean): ExoPlayerPool {
        val dataSourceFactory = OkHttpDataSource.Factory(callFactory(proxied))
        val resolver = blossomResolver()

        val resolvingDataSourceFactory: DataSource.Factory =
            ResolvingDataSource.Factory(dataSourceFactory) { dataSpec: DataSpec ->
                val originalUri = dataSpec.uri
                val scheme = originalUri.scheme
                if (scheme != null && resolver.canResolve(scheme)) {
                    val serverUrl = runBlocking { resolver.findServers(originalUri.toString()) }
                    if (serverUrl != null) {
                        return@Factory dataSpec.withUri(serverUrl.serverUrl.toUri())
                    }
                }
                dataSpec
            }

        return ExoPlayerPool(
            ExoPlayerBuilder(videoCache(), resolvingDataSourceFactory),
            poolSize = decoderBudget,
        ).also {
            // Warm the pool as soon as we know this route is in use. It builds on the main looper
            // and yields between instances, so the first acquire still happens synchronously while
            // later ones get a warm player instead of paying the build cost mid-scroll.
            it.create(appContext)
        }
    }

    /**
     * The pool serving [proxyPort]. Main thread only — the lazy build below is unsynchronised, and
     * two racing callers would each build a pool, double-counting the shared decoder budget. Every
     * caller is a Compose effect or the playback service, all of which run on the main thread.
     *
     * A port at or below zero means direct traffic. The port itself
     * never reaches the pool: which OkHttp client to use is resolved per call inside the factory,
     * so a changed Tor port does not invalidate the players already built for the proxied route.
     */
    fun pool(proxyPort: Int?): ExoPlayerPool =
        if ((proxyPort ?: 0) <= 0) {
            direct ?: newPool(proxied = false).also { direct = it }
        } else {
            proxied ?: newPool(proxied = true).also { proxied = it }
        }

    /**
     * Checks out a player for [videoUri], preferring a warm one that still holds that exact URI so
     * its buffer and position survive a scroll-back. The returned handle carries the owning pool so
     * the player can be returned by a caller that no longer knows which route it came from.
     */
    fun acquire(
        proxyPort: Int?,
        videoUri: String?,
        repeatMode: Boolean,
    ): PooledPlayer {
        val pool = pool(proxyPort)
        val player = pool.acquirePlayer(appContext, videoUri)

        // Applied on every checkout, not just on a fresh build: a warm player carries whatever the
        // last view left on it.
        player.repeatMode = if (repeatMode) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        player.volume = 0f

        return PooledPlayer(player, pool)
    }

    /**
     * Builds the direct pool and its cold players before the first video asks for one.
     *
     * Without this the warmup is kicked off by the very first [acquire], which then loses the race
     * to its own coroutine and builds an ExoPlayer inline on the main thread — during composition,
     * on the first frame a feed video appears.
     *
     * Only the direct pool: which route a video takes is decided per URL from the account's Tor
     * settings, which are not loaded yet at this point, and direct is what the large majority use.
     * A proxied-video user pays one inline build and the proxied pool then warms itself exactly as
     * it does today.
     *
     * Call after the video cache is materialised — building a pool reads it, and doing that cold
     * would put SimpleCache's index walk on the main thread, which is the whole reason the cache
     * has a warmup of its own.
     */
    suspend fun warmUp() {
        withContext(Dispatchers.Main) { pool(null) }
    }

    /** Releases the warm (paused-with-buffer) half of both pools under memory pressure. Any thread. */
    fun trimMemory() {
        direct?.releaseWarmPool()
        proxied?.releaseWarmPool()
    }

    fun destroy() {
        direct?.destroy()
        proxied?.destroy()
        direct = null
        proxied = null
    }
}

/**
 * A checked-out [ExoPlayer] paired with the pool that owns it, so [release] always sends it home to
 * the route it was built for. Handing a proxied player back to the direct pool would quietly put
 * Tor-routed players on the open web the next time one was reused.
 */
@OptIn(UnstableApi::class)
class PooledPlayer(
    val player: ExoPlayer,
    private val pool: ExoPlayerPool,
) {
    fun release() {
        pool.releasePlayerAsync(player)
    }
}
