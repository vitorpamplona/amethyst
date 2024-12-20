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
package com.vitorpamplona.amethyst.service.playback

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager

class PlaybackService : MediaSessionService() {
    private var videoViewedPositionCache = VideoViewedPositionCache()
    private var managerAllInOneNoProxy: MultiPlayerPlaybackManager? = null
    private var managerAllInOneProxy: MultiPlayerPlaybackManager? = null

    @OptIn(UnstableApi::class)
    fun lazyDS(proxyPort: Int): MultiPlayerPlaybackManager {
        if (proxyPort <= 0) {
            // no proxy
            managerAllInOneNoProxy?.let {
                return it
            }

            // creates new
            val okHttp = HttpClientManager.getHttpClient(false)
            val newInstance = MultiPlayerPlaybackManager(CustomMediaSourceFactory(okHttp), videoViewedPositionCache)
            managerAllInOneNoProxy = newInstance
            return newInstance
        } else {
            // with proxy, check if the port is the same.
            managerAllInOneProxy?.let {
                val okHttp = HttpClientManager.getHttpClient(true)
                if (okHttp == it.dataSourceFactory.okHttpClient.proxy) {
                    return it
                }

                val toDestroyAllInOne = managerAllInOneProxy

                val newInstance = MultiPlayerPlaybackManager(CustomMediaSourceFactory(okHttp), videoViewedPositionCache)

                managerAllInOneProxy = newInstance

                toDestroyAllInOne?.releaseAppPlayers()

                return newInstance
            }

            // creates new
            val okHttp = HttpClientManager.getHttpClient(true)
            val newInstance = MultiPlayerPlaybackManager(CustomMediaSourceFactory(okHttp), videoViewedPositionCache)
            managerAllInOneProxy = newInstance
            return newInstance
        }
    }

    // Create your Player and MediaSession in the onCreate lifecycle event
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        Log.d("Lifetime Event", "PlaybackService.onCreate")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        Log.d("Lifetime Event", "onTaskRemoved")
    }

    override fun onDestroy() {
        Log.d("Lifetime Event", "PlaybackService.onDestroy")

        managerAllInOneProxy?.releaseAppPlayers()
        managerAllInOneNoProxy?.releaseAppPlayers()

        super.onDestroy()
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        // Updates any new player ready
        super.onUpdateNotification(session, startInForegroundRequired)

        // Overrides the notification with any player actually playing
        managerAllInOneProxy?.playingContent()?.forEach {
            if (it.player.isPlaying) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }

        // Overrides again with playing with audio
        managerAllInOneProxy?.playingContent()?.forEach {
            if (it.player.isPlaying && it.player.volume > 0) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }

        // Overrides the notification with any player actually playing
        managerAllInOneNoProxy?.playingContent()?.forEach {
            if (it.player.isPlaying) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }

        // Overrides again with playing with audio
        managerAllInOneNoProxy?.playingContent()?.forEach {
            if (it.player.isPlaying && it.player.volume > 0) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }
    }

    // Return a MediaSession to link with the MediaController that is making
    // this request.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val id = controllerInfo.connectionHints.getString("id") ?: return null
        val uri = controllerInfo.connectionHints.getString("uri") ?: return null
        val callbackUri = controllerInfo.connectionHints.getString("callbackUri")
        val proxyPort = controllerInfo.connectionHints.getInt("proxyPort")

        val manager = lazyDS(proxyPort)

        return manager.getMediaSession(
            id,
            uri,
            callbackUri,
            context = this,
            applicationContext = applicationContext,
        )
    }
}
