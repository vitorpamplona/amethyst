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

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.service.http.DynamicCallFactory
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerBuilder
import com.vitorpamplona.amethyst.service.playback.playerPool.ExoPlayerPool
import com.vitorpamplona.amethyst.service.playback.playerPool.MediaSessionPool
import com.vitorpamplona.amethyst.service.playback.playerPool.MemoryPressure
import com.vitorpamplona.amethyst.service.playback.playerPool.SimultaneousPlaybackCalculator
import com.vitorpamplona.amethyst.service.playback.playerPool.mediaDataSourceFactory
import com.vitorpamplona.quartz.utils.Log

class PlaybackService : MediaSessionService() {
    private var poolNoProxy: MediaSessionPool? = null
    private var poolWithProxy: MediaSessionPool? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val foregroundStartGuard = Runnable { satisfyPendingForegroundStart() }

    // Shared by both pools, so the direct and Tor pools read one cached answer.
    private val memoryPressure by lazy { MemoryPressure(applicationContext, Amethyst.instance.applicationIOScope) }

    @OptIn(UnstableApi::class)
    fun newPool(
        videoCache: VideoCache,
        okHttpClient: DynamicCallFactory,
        blossomServerResolver: BlossomServerResolver,
    ): MediaSessionPool {
        val resolvingDataSourceFactory = mediaDataSourceFactory(okHttpClient, blossomServerResolver)

        // The device's concurrent-decoder ceiling bounds both how many players may be checked out
        // at once (the session cache) and how many the pool may retain, since a session and a warm
        // pool entry each pin one MediaCodec instance.
        val decoderBudget = SimultaneousPlaybackCalculator.max(applicationContext)
        val totalRam = SimultaneousPlaybackCalculator.totalRamBytes(applicationContext)

        return MediaSessionPool(
            exoPlayerPool =
                ExoPlayerPool(
                    ExoPlayerBuilder(videoCache, resolvingDataSourceFactory),
                    poolSize = decoderBudget,
                    requestedWarmSlots = ExoPlayerPool.warmSlotsFor(decoderBudget, totalRam),
                    memoryLevel = memoryPressure::level,
                ),
            dataSourceFactory = resolvingDataSourceFactory,
            appContext = applicationContext,
            maxSessions = decoderBudget,
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
        return if (proxyPort <= 0) {
            // no proxy
            poolNoProxy?.let { return it }

            val okHttpClient = Amethyst.instance.okHttpClients.getDynamicCallFactory(false)
            val videoCache = Amethyst.instance.videoCache
            val blossomServerResolver = Amethyst.instance.blossomResolver

            // creates new
            newPool(videoCache, okHttpClient, blossomServerResolver)
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

            newPool(videoCache, okHttpClient, blossomServerResolver)
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

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val result =
            try {
                super.onStartCommand(intent, flags, startId)
            } catch (e: IllegalStateException) {
                // Media3's MediaSessionService.onStartCommand can call stopSelfSafely() when there is
                // no active playback to handle a delivered intent (e.g. a MEDIA_BUTTON from a headset
                // arriving while the app is backgrounded). stopSelfSafely() invokes startForeground()
                // to detach the foreground notification before stopping, which Android 12+ rejects
                // from the background with ForegroundServiceStartNotAllowedException. There is no
                // playback to keep alive in this path, so swallow it and stop the service.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.w("PlaybackService") { "Foreground service start not allowed; stopping PlaybackService" }
                    stopSelf()
                    START_NOT_STICKY
                } else {
                    throw e
                }
            }

        if (intent != null && isMediaOrCustomAction(intent)) {
            mainHandler.removeCallbacks(foregroundStartGuard)
            mainHandler.postDelayed(foregroundStartGuard, FOREGROUND_START_GUARD_MS)
        }

        return result
    }

    /**
     * Media button and notification action intents reach this service through
     * `PendingIntent.getForegroundService` (the "play" action of a paused notification, and the
     * session's media button receiver, which Android keeps after the session is released and
     * fires on a headset or Bluetooth key). Each one obliges the service to call startForeground().
     *
     * Media3 only calls it when the intent results in playback. When it does not, because the
     * session the intent names is gone (the feed released it), [onGetSession] declines the
     * fallback caller, or the player cannot resume, Media3 returns without calling it unless this
     * is the service's first start, and the system kills the app with
     * ForegroundServiceDidNotStartInTimeException. So if the service is still not in the
     * foreground shortly after such an intent, enter and immediately leave the foreground with an
     * invisible notification, which is what Media3's own stopSelfSafely() does on first start.
     */
    @OptIn(UnstableApi::class)
    private fun satisfyPendingForegroundStart() {
        val inForeground =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                foregroundServiceType != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            } else {
                // Before Q there is no way to ask; Media3 reports false only if it never went
                // into the foreground, so don't risk replacing its notification otherwise.
                isPlaybackOngoing
            }
        if (inForeground) return

        try {
            val channelId = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
            val notificationManager = getSystemService<NotificationManager>()
            if (notificationManager != null && notificationManager.getNotificationChannel(channelId) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        getString(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }

            val notification =
                NotificationCompat
                    .Builder(this, channelId)
                    .setSmallIcon(R.drawable.amethyst)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setOnlyAlertOnce(true)
                    .setOngoing(false)
                    .build()

            ServiceCompat.startForeground(
                this,
                FOREGROUND_GUARD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            Log.d("PlaybackService") { "Media intent did not start playback; left the foreground it required" }
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the intent was a plain startService (a
            // pause action), so no startForeground() was owed and there is nothing to do.
            Log.d("PlaybackService", "Not allowed to enter the foreground after a media intent", e)
        }
    }

    private fun isMediaOrCustomAction(intent: Intent): Boolean = intent.action == Intent.ACTION_MEDIA_BUTTON || intent.action == MEDIA3_CUSTOM_ACTION

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Since API 34 the OS only delivers UI_HIDDEN and BACKGROUND; BACKGROUND (process on
        // the system LRU list) is the real reclaim-pressure signal, so release the warm pool then.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            poolNoProxy?.exoPlayerPool?.releaseWarmPool()
            poolWithProxy?.exoPlayerPool?.releaseWarmPool()
        }
    }

    override fun onDestroy() {
        Log.d("PlaybackService", "PlaybackService.onDestroy")
        mainHandler.removeCallbacks(foregroundStartGuard)

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
        // Optional warm-pool affinity hint: when the pool still has a paused ExoPlayer
        // holding this exact URI, the new session reuses it so the buffer survives.
        val preferredMediaId = controllerInfo.connectionHints.getString(HINT_VIDEO_URI)
        val manager = lazyPool(proxyPort)
        return manager.getSession(id, keepPlaying, applicationContext, preferredMediaId)
    }

    companion object {
        const val HINT_ID = "id"
        const val HINT_PROXY_PORT = "proxyPort"
        const val HINT_KEEP_PLAYING = "keepPlaying"
        const val HINT_VIDEO_URI = "videoUri"

        // Media3's CustomCommandPendingIntentBuilder.ACTION_CUSTOM, which is package-private.
        private const val MEDIA3_CUSTOM_ACTION = "androidx.media3.session.CUSTOM_NOTIFICATION_ACTION"

        // Well under the system's foreground-start deadline (5 s and up), and long enough for
        // Media3 to apply a headset key (it waits out the double-tap window) and post its own
        // notification first.
        private const val FOREGROUND_START_GUARD_MS = 2_000L

        // Distinct from Media3's media notification (1001), its shutdown notification (20938) and
        // the app's other notifications, so removing it never removes any of them.
        private const val FOREGROUND_GUARD_NOTIFICATION_ID = 0x504C4159 // "PLAY"
    }
}
