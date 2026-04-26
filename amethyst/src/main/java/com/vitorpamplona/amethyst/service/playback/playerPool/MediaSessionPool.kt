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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.amethyst.ui.MainActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
    val dataSourceFactory: DataSource.Factory,
    val appContext: Context,
    val reset: (MediaSession, Boolean) -> Unit,
) {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            com.vitorpamplona.quartz.utils.Log
                .e("MediaSessionPool", "Caught exception: ${throwable.message}", throwable)
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    val globalCallback = MediaSessionCallback(this, appContext)

    // Last cleanup timestamp in nanos, guarded by CAS so concurrent releaseSession() calls
    // can't all win the time check and each launch a redundant scope.launch sweep.
    private val lastCleanupNs = AtomicLong(System.nanoTime())

    // The bitmap loader is stateless w.r.t. the session; a fresh allocation per session was
    // pure noise. ExoPlayer's DEFAULT_EXECUTOR_SERVICE is a process-wide singleton, the
    // dataSourceFactory is owned by the pool, and the appContext is already retained.
    @OptIn(UnstableApi::class)
    private val sharedBitmapLoader by lazy {
        DataSourceBitmapLoader
            .Builder(appContext)
            .setExecutorService(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get())
            .setDataSourceFactory(dataSourceFactory)
            .build()
    }

    // protects from LruCache killing playing sessions
    private val playingMap = mutableMapOf<String, SessionListener>()

    private val cache =
        object : LruCache<String, SessionListener>(10) { // up to 10 videos in the screen at the same time
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

    @OptIn(UnstableApi::class)
    fun newSession(
        id: String,
        keepPlaying: Boolean,
        context: Context,
        // Best-effort affinity hint: when the pool still has a paused player carrying this
        // exact mediaId (matches MediaItem.mediaId, which is the videoUri), the warm player
        // is reused so the populated buffer survives. Null falls back to a cold acquire.
        preferredMediaId: String?,
    ): MediaSession {
        val mediaSession =
            MediaSession
                .Builder(context, exoPlayerPool.acquirePlayer(context, preferredMediaId))
                .apply {
                    setBitmapLoader(sharedBitmapLoader)
                    setId(id)
                    setCallback(globalCallback)
                }.build()

        val listener = MediaSessionExoPlayerConnector(mediaSession, this)

        mediaSession.player.addListener(listener)

        reset(mediaSession, keepPlaying)

        cache.put(mediaSession.id, SessionListener(mediaSession, listener))

        return mediaSession
    }

    fun releaseSession(session: MediaSession) {
        val listener = playingMap.get(session.id) ?: cache.get(session.id)
        if (listener != null) {
            session.player.removeListener(listener.playerListener)
        }

        playingMap.remove(session.id)
        cache.remove(session.id)
        session.release()
        cleanupUnused()
    }

    fun cleanupUnused() {
        val now = System.nanoTime()
        val previous = lastCleanupNs.get()
        if (now - previous < CLEANUP_INTERVAL_NS) return
        // CAS so only one caller actually launches the sweep when many releases fire at once.
        if (!lastCleanupNs.compareAndSet(previous, now)) return
        scope.launch {
            val snap = cache.snapshot()
            snap.values.forEach {
                if (it.session.connectedControllers.isEmpty()) {
                    releaseSession(it.session)
                }
            }
        }
    }

    fun destroy() {
        scope.launch {
            cache.evictAll()
            playingMap.forEach {
                it.value.removeListeners()
                exoPlayerPool.releasePlayer(it.value.session.player as ExoPlayer)
                it.value.session.release()
            }
            playingMap.clear()
        }

        exoPlayerPool.destroy()
        scope.cancel()
    }

    fun getSession(
        id: String,
        keepPlaying: Boolean,
        context: Context,
        preferredMediaId: String? = null,
    ): MediaSession {
        val existingSession = playingMap.get(id) ?: cache.get(id)
        if (existingSession != null) {
            return existingSession.session
        }

        return newSession(id, keepPlaying, context, preferredMediaId)
    }

    fun playingContent() = playingMap.values

    fun getSession(id: String) = cache.get(id)?.session

    class MediaSessionCallback(
        val pool: MediaSessionPool,
        val appContext: Context,
    ) : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            // set up return call when clicking on the Notification bar
            mediaItems.firstOrNull()?.mediaMetadata?.extras?.getString(MediaItemCache.EXTRA_CALLBACK_URI)?.let {
                mediaSession.setSessionActivity(
                    PendingIntent.getActivity(
                        appContext,
                        0,
                        Intent(Intent.ACTION_VIEW, it.toUri(), appContext, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
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

    companion object {
        private val CLEANUP_INTERVAL_NS = TimeUnit.MINUTES.toNanos(1)
    }
}
