/**
 * Copyright (c) 2024 Vitor Pamplona
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

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerBuilder
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerPool
import com.vitorpamplona.amethyst.service.playback.playerPool.MediaSessionPool
import okhttp3.OkHttpClient

class PlaybackService : MediaSessionService() {
    private var poolNoProxy: MediaSessionPool? = null
    private var poolWithProxy: MediaSessionPool? = null

    @OptIn(UnstableApi::class)
    fun newPool(okHttp: OkHttpClient): MediaSessionPool =
        MediaSessionPool(
            ExoPlayerPool(ExoPlayerBuilder(okHttp)),
            reset = { session ->
                (session.player as ExoPlayer).apply {
                    repeatMode = Player.REPEAT_MODE_ONE
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
            return newPool(HttpClientManager.getHttpClient(false)).also { poolNoProxy = it }
        } else {
            poolWithProxy?.let { pool ->
                // with proxy, check if the port is the same.
                val okHttp = HttpClientManager.getHttpClient(true)
                if (okHttp.proxy == pool.exoPlayerPool.builder.okHttp.proxy) {
                    return pool
                }

                pool.destroy()
                return newPool(okHttp).also { poolWithProxy = it }
            }

            // creates brand new
            return newPool(HttpClientManager.getHttpClient(true)).also { poolWithProxy = it }
        }
    }

    override fun onDestroy() {
        Log.d("Lifetime Event", "PlaybackService.onDestroy")

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

        val proxyPlaying = poolWithProxy?.playingContent()

        // Overrides the notification with any player actually playing
        proxyPlaying?.forEach {
            if (it.session.player.isPlaying) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
            }
        }

        // Overrides again with playing with audio
        proxyPlaying?.forEach {
            if (it.session.player.isPlaying && it.session.player.volume > 0) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
            }
        }

        val noProxyPlaying = poolNoProxy?.playingContent()

        // Overrides the notification with any player actually playing
        noProxyPlaying?.forEach {
            if (it.session.player.isPlaying) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
            }
        }

        // Overrides again with playing with audio
        noProxyPlaying?.forEach {
            if (it.session.player.isPlaying && it.session.player.volume > 0) {
                super.onUpdateNotification(it.session, startInForegroundRequired)
            }
        }
    }

    // Return a MediaSession to link with the MediaController that is making
    // this request.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val id = controllerInfo.connectionHints.getString("id") ?: return null
        val proxyPort = controllerInfo.connectionHints.getInt("proxyPort")
        val manager = lazyPool(proxyPort)
        return manager.getSession(id, applicationContext)
    }
}
