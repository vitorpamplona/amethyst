package com.vitorpamplona.amethyst

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.LruCache
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.vitorpamplona.amethyst.ui.MainActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class MultiPlayerPlaybackManager(
    private val dataSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory? = null,
    private val cachedPositions: VideoViewedPositionCache
) {
    // protects from LruCache killing playing sessions
    private val playingMap = mutableMapOf<String, MediaSession>()

    private val cache =
        object : LruCache<String, MediaSession>(10) { // up to 10 videos in the screen at the same time
            override fun entryRemoved(
                evicted: Boolean,
                key: String?,
                oldValue: MediaSession?,
                newValue: MediaSession?
            ) {
                super.entryRemoved(evicted, key, oldValue, newValue)

                if (!playingMap.contains(key)) {
                    oldValue?.let {
                        it.player.release()
                        it.release()
                    }
                }
            }
        }

    private fun getCallbackIntent(callbackUri: String, applicationContext: Context): PendingIntent {
        return PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(Intent.ACTION_VIEW, callbackUri.toUri(), applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getMediaSession(id: String, uri: String, callbackUri: String?, context: Context, applicationContext: Context): MediaSession {
        val existingSession = playingMap.get(id) ?: cache.get(id)
        if (existingSession != null) return existingSession

        val player = ExoPlayer.Builder(context).run {
            dataSourceFactory?.let { setMediaSourceFactory(it) }
            build()
        }

        player.apply {
            repeatMode = Player.REPEAT_MODE_ALL
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            volume = 0f
        }

        val mediaSession = MediaSession.Builder(context, player).run {
            callbackUri?.let {
                setSessionActivity(getCallbackIntent(it, applicationContext))
            }
            setId(id)
            build()
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    player.setWakeMode(C.WAKE_MODE_NETWORK)
                    playingMap.put(id, mediaSession)
                } else {
                    player.setWakeMode(C.WAKE_MODE_NONE)
                    cachedPositions.add(uri, player.currentPosition)
                    cache.put(id, mediaSession)
                    playingMap.remove(id, mediaSession)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    STATE_IDLE -> {
                        // only saves if it wqs playing
                        if (abs(player.currentPosition) > 1) {
                            cachedPositions.add(uri, player.currentPosition)
                        }
                    }
                    STATE_READY -> {
                        cachedPositions.get(uri)?.let { lastPosition ->
                            if (abs(player.currentPosition - lastPosition) > 5 * 60) {
                                player.seekTo(lastPosition)
                            }
                        }
                    }
                    else -> {
                        // only saves if it wqs playing
                        if (abs(player.currentPosition) > 1) {
                            cachedPositions.add(uri, player.currentPosition)
                        }
                    }
                }
            }
        })

        cache.put(id, mediaSession)

        return mediaSession
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun releaseAppPlayers() {
        GlobalScope.launch(Dispatchers.Main) {
            cache.evictAll()
            playingMap.forEach {
                it.value.player.release()
                it.value.release()
            }
            playingMap.clear()
        }
    }

    fun playingContent(): Collection<MediaSession> {
        return playingMap.values
    }
}
