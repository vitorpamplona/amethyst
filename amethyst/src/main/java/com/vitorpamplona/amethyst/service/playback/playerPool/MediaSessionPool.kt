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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SessionListener(
    val session: MediaSession,
    val playerListener: Player.Listener,
) {
    fun removeListeners() {
        session.player.removeListener(playerListener)
    }
}

/**
 * The goal for this class is to make sure all sessions and exoplayers are closed correctly.
 */
class MediaSessionPool(
    val exoPlayerPool: ExoPlayerPool,
    val reset: (MediaSession) -> Unit,
) {
    val globalCallback = MediaSessionCallback(this)
    var lastCleanup = TimeUtils.now()

    // protects from LruCache killing playing sessions
    private val playingMap = mutableMapOf<String, SessionListener>()

    private val cache =
        object : LruCache<String, SessionListener>(SimultaneousPlaybackCalculator.max()) { // up to 10 videos in the screen at the same time
            override fun entryRemoved(
                evicted: Boolean,
                key: String?,
                oldValue: SessionListener?,
                newValue: SessionListener?,
            ) {
                super.entryRemoved(evicted, key, oldValue, newValue)

                if (!playingMap.contains(key)) {
                    oldValue?.let { pair ->
                        pair.removeListeners()
                        exoPlayerPool.releasePlayerAsync(pair.session.player as ExoPlayer)
                        pair.session.release()
                    }
                }
            }
        }

    fun newSession(
        id: String,
        context: Context,
    ): MediaSession {
        val mediaSession =
            MediaSession
                .Builder(context, exoPlayerPool.acquirePlayer(context))
                .apply {
                    setId(id)
                    setCallback(globalCallback)
                }.build()

        val listener = MediaSessionExoPlayerConnector(mediaSession, this)

        mediaSession.player.addListener(listener)

        reset(mediaSession)

        cache.put(mediaSession.id, SessionListener(mediaSession, listener))

        return mediaSession
    }

    fun releaseSession(session: MediaSession) {
        val listener = playingMap.get(session.id) ?: cache.get(session.id)
        if (listener != null) {
            session.player.removeListener(listener.playerListener)
        }

        cache.remove(session.id)
        playingMap.remove(session.id)
        session.release()
        exoPlayerPool.releasePlayerAsync(session.player as ExoPlayer)

        cleanupUnused()
    }

    fun cleanupUnused() {
        if (lastCleanup < TimeUtils.oneMinuteAgo()) {
            lastCleanup = TimeUtils.now()
            GlobalScope.launch(Dispatchers.Main) {
                var counter = 0
                val snap = cache.snapshot()
                // makes a copy and awaits 10 seconds in case a new token was just created
                // but not connected yet.
                // delay(10000)
                snap.values.forEach {
                    Log.d("MediaSessionPoll", "Clean Up Unused ${it.session.connectedControllers.size} ${it.session.id}")
                    if (it.session.connectedControllers.isEmpty()) {
                        releaseSession(it.session)
                        counter++
                    }
                }
                lastCleanup = TimeUtils.now()
            }
        }
    }

    fun destroy() {
        GlobalScope.launch(Dispatchers.Main) {
            cache.evictAll()
            playingMap.forEach {
                it.value.removeListeners()
                exoPlayerPool.releasePlayer(it.value.session.player as ExoPlayer)
                it.value.session.release()
            }
            playingMap.clear()
        }

        exoPlayerPool.destroy()
    }

    fun getSession(
        id: String,
        context: Context,
    ): MediaSession {
        val existingSession = playingMap.get(id) ?: cache.get(id)
        if (existingSession != null) {
            return existingSession.session
        }

        return newSession(id, context)
    }

    fun playingContent() = playingMap.values

    class MediaSessionCallback(
        val pool: MediaSessionPool,
    ) : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            mediaSession.player.setMediaItems(mediaItems)

            // set up return call when clicking on the Notification bar
            mediaItems.firstOrNull()?.mediaMetadata?.extras?.getString("callbackUri")?.let {
                mediaSession.setSessionActivity(Amethyst.Companion.instance.createIntent(it))
            }

            return Futures.immediateFuture(mediaItems)
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            pool.releaseSession(session)
        }
    }

    class MediaSessionExoPlayerConnector(
        val mediaSession: MediaSession,
        val pool: MediaSessionPool,
    ) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                pool.playingMap.put(mediaSession.id, SessionListener(mediaSession, this))
            } else {
                pool.cache.put(mediaSession.id, SessionListener(mediaSession, this))
                pool.playingMap.remove(mediaSession.id)
            }
        }
    }
}
