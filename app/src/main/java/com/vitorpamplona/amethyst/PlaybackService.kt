package com.vitorpamplona.amethyst

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.service.HttpClient

@UnstableApi // Extend MediaSessionService
class PlaybackService : MediaSessionService() {
    private var videoViewedPositionCache = VideoViewedPositionCache()

    private var managerHls: MultiPlayerPlaybackManager? = null
    private var managerProgressive: MultiPlayerPlaybackManager? = null
    private var managerLocal: MultiPlayerPlaybackManager? = null

    fun newHslDataSource(): MediaSource.Factory {
        return HlsMediaSource.Factory(OkHttpDataSource.Factory(HttpClient.getHttpClient()))
    }

    fun newProgressiveDataSource(): MediaSource.Factory {
        return ProgressiveMediaSource.Factory(VideoCache.get(Amethyst.instance, HttpClient.getHttpClient()))
    }

    // Create your Player and MediaSession in the onCreate lifecycle event
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        managerHls = MultiPlayerPlaybackManager(newHslDataSource(), videoViewedPositionCache)
        managerProgressive = MultiPlayerPlaybackManager(newProgressiveDataSource(), videoViewedPositionCache)
        managerLocal = MultiPlayerPlaybackManager(cachedPositions = videoViewedPositionCache)

        // Stop all videos and recreates all managers when the proxy changes.
        HttpClient.proxyChangeListeners.add(this@PlaybackService::onProxyUpdated)
    }

    private fun onProxyUpdated() {
        val toDestroyHls = managerHls
        val toDestroyProgressive = managerProgressive

        managerHls = MultiPlayerPlaybackManager(newHslDataSource(), videoViewedPositionCache)
        managerProgressive = MultiPlayerPlaybackManager(newProgressiveDataSource(), videoViewedPositionCache)

        toDestroyHls?.releaseAppPlayers()
        toDestroyProgressive?.releaseAppPlayers()
    }

    override fun onDestroy() {
        HttpClient.proxyChangeListeners.remove(this@PlaybackService::onProxyUpdated)

        managerHls?.releaseAppPlayers()
        managerLocal?.releaseAppPlayers()
        managerProgressive?.releaseAppPlayers()

        super.onDestroy()
    }

    fun getAppropriateMediaSessionManager(fileName: String): MultiPlayerPlaybackManager? {
        return if (fileName.startsWith("file")) {
            managerLocal
        } else if (fileName.endsWith("m3u8")) {
            managerHls
        } else {
            managerProgressive
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Updates any new player ready
        super.onUpdateNotification(session, startInForegroundRequired)

        // Overrides the notification with any player actually playing
        managerHls?.playingContent()?.forEach {
            if (it.player.isPlaying) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }
        managerLocal?.playingContent()?.forEach {
            if (it.player.isPlaying) {
                super.onUpdateNotification(session, startInForegroundRequired)
            }
        }
        managerProgressive?.playingContent()?.forEach {
            if (it.player.isPlaying) {
                super.onUpdateNotification(session, startInForegroundRequired)
            }
        }

        // Overrides again with playing with audio
        managerHls?.playingContent()?.forEach {
            if (it.player.isPlaying && it.player.volume > 0) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }
        managerLocal?.playingContent()?.forEach {
            if (it.player.isPlaying && it.player.volume > 0) {
                super.onUpdateNotification(session, startInForegroundRequired)
            }
        }
        managerProgressive?.playingContent()?.forEach {
            if (it.player.isPlaying && it.player.volume > 0) {
                super.onUpdateNotification(session, startInForegroundRequired)
            }
        }
    }

    // Return a MediaSession to link with the MediaController that is making
    // this request.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val id = controllerInfo.connectionHints.getString("id") ?: return null
        val uri = controllerInfo.connectionHints.getString("uri") ?: return null
        val callbackUri = controllerInfo.connectionHints.getString("callbackUri")

        val manager = getAppropriateMediaSessionManager(uri)

        return manager?.getMediaSession(id, uri, callbackUri, context = this, applicationContext = applicationContext)
    }
}
