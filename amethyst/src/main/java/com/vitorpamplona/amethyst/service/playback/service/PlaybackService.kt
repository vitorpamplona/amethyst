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
package com.vitorpamplona.amethyst.service.playback.service

import android.net.Uri
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.okhttp.DynamicCallFactory
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerBuilder
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerPool
import com.vitorpamplona.amethyst.service.playback.playerPool.MediaSessionPool
import com.vitorpamplona.amethyst.service.playback.playerPool.SimultaneousPlaybackCalculator
import com.vitorpamplona.amethyst.service.uploads.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.runBlocking

class PlaybackService : MediaSessionService() {
    private var poolNoProxy: MediaSessionPool? = null
    private var poolWithProxy: MediaSessionPool? = null

    @OptIn(UnstableApi::class)
    fun newPool(
        videoCache: VideoCache,
        okHttpClient: DynamicCallFactory,
        blossomServerResolver: BlossomServerResolver,
    ): MediaSessionPool {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        val resolvingDataSourceFactory: DataSource.Factory =
            ResolvingDataSource.Factory(
                dataSourceFactory,
                ResolvingDataSource.Resolver { dataSpec: DataSpec ->
                    val originalUri: Uri = dataSpec.uri
                    val scheme = originalUri.scheme
                    if (scheme != null && blossomServerResolver.canResolve(scheme)) {
                        val serverUrl =
                            runBlocking {
                                blossomServerResolver.findServers(originalUri.toString())
                            }
                        if (serverUrl != null) {
                            return@Resolver dataSpec.withUri(serverUrl.serverUrl.toUri())
                        }
                    }
                    dataSpec
                },
            )

        return MediaSessionPool(
            exoPlayerPool =
                ExoPlayerPool(
                    ExoPlayerBuilder(videoCache, resolvingDataSourceFactory),
                    poolSize = SimultaneousPlaybackCalculator.max(applicationContext),
                ),
            dataSourceFactory = resolvingDataSourceFactory,
            appContext = applicationContext,
            reset = { session, keepPlaying ->
                (session.player as ExoPlayer).apply {
                    repeatMode = if (keepPlaying) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    volume = 0f
                }
            },
        )
    }

    @OptIn(UnstableApi::class)
    fun lazyPool(proxyPort: Int): MediaSessionPool {
        if (proxyPort <= 0) {
            // no proxy
            poolNoProxy?.let { return it }

            val okHttpClient = Amethyst.instance.okHttpClients.getDynamicCallFactory(false)
            val videoCache = Amethyst.instance.videoCache
            val blossomServerResolver = Amethyst.instance.blossomResolver

            // creates new
            return newPool(videoCache, okHttpClient, blossomServerResolver)
                .also {
                    poolNoProxy = it
                    // Kick off the player pool warmup as soon as we know this pool is being used.
                    // It runs async on the main looper, yielding between builds, so the very first
                    // session still acquires synchronously while subsequent ones can grab a warm
                    // ExoPlayer instead of paying the build cost on the main thread.
                    it.exoPlayerPool.create(applicationContext)
                }
        } else {
            poolWithProxy?.let { return it }

            // creates brand new
            // proxy port can change without affecting the pool because
            // the choice of okhttp is resolved in newCall
            val okHttpClient = Amethyst.instance.okHttpClients.getDynamicCallFactory(true)
            val videoCache = Amethyst.instance.videoCache
            val blossomServerResolver = Amethyst.instance.blossomResolver

            return newPool(videoCache, okHttpClient, blossomServerResolver)
                .also {
                    poolWithProxy = it
                    it.exoPlayerPool.create(applicationContext)
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("PlaybackService", "PlaybackService.onCreate")
    }

    override fun onDestroy() {
        Log.d("PlaybackService", "PlaybackService.onDestroy")

        poolWithProxy?.destroy()
        poolNoProxy?.destroy()
        super.onDestroy()
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        // Updates any new player ready
        super.onUpdateNotification(session, startInForegroundRequired)

        // playback controllers control the last notification updated.
        // this procedure re-updates the notification to make sure it aligns
        // with users expectation on which playback they decide to control:
        // 1. If no video is being played, play the picture in picture if there.
        // 2. If there are videos being played the order is:
        // 2. a. Picture in picture if playing
        // 2. b. On screen video with volume on
        // 2. c. On screen video with volume off.

        val playing = (poolWithProxy?.playingContent() ?: emptyList()) + (poolNoProxy?.playingContent() ?: emptyList())

        // if nothing is pl
        if (playing.isEmpty() && BackgroundMedia.hasInstance()) {
            BackgroundMedia.bgInstance?.id?.let { id ->
                (poolNoProxy?.getSession(id) ?: poolWithProxy?.getSession(id))?.let {
                    super.onUpdateNotification(it, startInForegroundRequired)
                }
            }
            return
        }

        playing.forEach {
            if (it.session.player.isPlaying && it.session.player.volume > 0 && it.session.id == BackgroundMedia.bgInstance?.id) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
                return
            }
        }

        playing.forEach {
            if (it.session.player.isPlaying && it.session.player.volume > 0) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
                return
            }
        }

        // Falls through to the first muted-but-playing session. Earlier this loop missed
        // its return and called super.onUpdateNotification once per playing session,
        // hammering the notification system whenever multiple feed videos were preloading.
        playing.forEach {
            if (it.session.player.isPlaying) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
                return
            }
        }
    }

    // Return a MediaSession to link with the MediaController that is making
    // this request.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val id = controllerInfo.connectionHints.getString("id") ?: return null
        val proxyPort = controllerInfo.connectionHints.getInt("proxyPort")
        val keepPlaying = controllerInfo.connectionHints.getBoolean("keepPlaying", true)
        val manager = lazyPool(proxyPort)
        return manager.getSession(id, keepPlaying, applicationContext)
    }
}
