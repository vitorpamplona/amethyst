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

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerBuilder
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerPool
import com.vitorpamplona.amethyst.service.playback.playerPool.MediaSessionPool
import com.vitorpamplona.amethyst.service.playback.playerPool.SimultaneousPlaybackCalculator
import com.vitorpamplona.quartz.utils.Log
import okhttp3.OkHttpClient

class PlaybackService : MediaSessionService() {
    private var poolNoProxy: MediaSessionPool? = null
    private var poolWithProxy: MediaSessionPool? = null

    @OptIn(UnstableApi::class)
    fun newPool(okHttp: OkHttpClient): MediaSessionPool =
        MediaSessionPool(
            exoPlayerPool =
                ExoPlayerPool(
                    ExoPlayerBuilder(okHttp),
                    poolSize = SimultaneousPlaybackCalculator.max(applicationContext),
                ),
            okHttpClient = okHttp,
            appContext = applicationContext,
            reset = { session, keepPlaying ->
                (session.player as ExoPlayer).apply {
                    repeatMode = if (keepPlaying) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    volume = 0f
                }
            },
        )

    @OptIn(UnstableApi::class)
    fun lazyPool(proxyPort: Int): MediaSessionPool {
        if (proxyPort <= 0) {
            // no proxy
            poolNoProxy?.let { return it }

            // creates new
            return newPool(Amethyst.instance.okHttpClients.getHttpClient(false)).also { poolNoProxy = it }
        } else {
            poolWithProxy?.let { pool ->
                // with proxy, check if the port is the same.
                val okHttp = Amethyst.instance.okHttpClients.getHttpClient(true)
                if (okHttp.proxy != null && okHttp.proxy == pool.exoPlayerPool.builder.okHttp.proxy) {
                    return pool
                }

                pool.destroy()
                return newPool(okHttp).also { poolWithProxy = it }
            }

            // creates brand new
            return newPool(Amethyst.instance.okHttpClients.getHttpClient(true)).also { poolWithProxy = it }
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

        playing.forEachIndexed { idx, it ->
            if (it.session.player.isPlaying && it.session.player.volume > 0 && it.session.id == BackgroundMedia.bgInstance?.id) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
                return
            }
        }

        playing.forEachIndexed { idx, it ->
            if (it.session.player.isPlaying && it.session.player.volume > 0) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
                return
            }
        }

        playing.forEachIndexed { idx, it ->
            if (it.session.player.isPlaying) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
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
