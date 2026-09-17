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
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.amethyst.service.playback.playerPool.MetadataArtworkBitmapLoader
import com.vitorpamplona.amethyst.service.playback.playerPool.PooledPlayer
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the single MediaSession for whichever playback the user has promoted out of the feed, and
 * nothing else.
 *
 * It used to own the player pools and mint a MediaSession per on-screen video. That is what put a
 * "video paused" card in the shade for posts the user had never opened: media3 shows a notification
 * for any session whose player is merely *prepared*, and every video scrolling through a feed is
 * prepared. Players now come from
 * [com.vitorpamplona.amethyst.service.playback.playerPool.VideoPlayerPools] in AppModules, and this
 * service exists only while
 * [com.vitorpamplona.amethyst.service.playback.background.VideoPlayback] holds a promotion —
 * so the session that exists is, by construction, the one the user means to control. No election,
 * no filtering.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PlaybackService.onCreate")

        scope.launch {
            Amethyst.instance.videoPlayback.promoted.collect { promoted ->
                if (promoted != null) {
                    attach(promoted)
                } else {
                    // The slot was given up: nothing is left for this service to host.
                    detach()
                    stopSelf()
                }
            }
        }
    }

    /**
     * Points the session at the promoted player. Reuses the existing session via setPlayer rather
     * than rebuilding one: media3 requires a replacement player on the same application looper,
     * which pooled players always are, and swapping in place keeps the notification from flickering
     * away and back when one promotion replaces another.
     */
    private fun attach(promoted: PooledPlayer) {
        val existing = session
        if (existing != null) {
            existing.player = promoted.player
            bindSessionActivity(existing, promoted.player.currentMediaItem)
            return
        }

        val built =
            MediaSession
                .Builder(applicationContext, promoted.player)
                .setBitmapLoader(bitmapLoader)
                .build()

        bindSessionActivity(built, promoted.player.currentMediaItem)
        session = built

        // Nothing binds this service with a MediaController any more, so the session has to be
        // registered by hand — that registration is what gives media3 a notification to post.
        addSession(built)
    }

    private fun detach() {
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Makes tapping the notification open the nostr event the media came from.
     *
     * The request code is shared across videos on purpose: PendingIntent identity already includes
     * the Intent's data URI (Intent.filterEquals), so two different posts get two different pending
     * intents regardless.
     */
    private fun bindSessionActivity(
        session: MediaSession,
        mediaItem: MediaItem?,
    ) {
        val callbackUri = mediaItem?.mediaMetadata?.extras?.getString(MediaItemCache.EXTRA_CALLBACK_URI) ?: return
        session.setSessionActivity(
            PendingIntent.getActivity(
                applicationContext,
                0,
                Intent(Intent.ACTION_VIEW, callbackUri.toUri(), applicationContext, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int =
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
                Log.w(TAG) { "Foreground service start not allowed; stopping PlaybackService" }
                stopSelf()
                START_NOT_STICKY
            } else {
                throw e
            }
        }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService.onDestroy")

        scope.cancel()
        detach()

        // Deliberately does NOT demote. This service hosts the session; it does not own the
        // playback. PipVideoActivity is launchMode=singleInstance, so it sits in its own task and
        // survives the main task being swiped off Recents — at which point media3's default
        // onTaskRemoved calls pauseAllPlayersAndStopSelf() and lands here. Demoting would hand the
        // player back to the pool out from under a picture-in-picture window that is still on
        // screen. The same applies to the ForegroundServiceStartNotAllowedException path above.
        // Whoever promoted gives the slot up (HoldPromotedPlayback), and AppModules.terminate()
        // sweeps anything still held at process teardown.

        // When nothing is playing media3 posts through NotificationManager.notify() and takes the
        // service back out of the foreground, so the notification is NOT owned by the foreground
        // service and nothing cancels it when the service dies. Without this it survives the
        // service — and the process — and sits in the shade backed by nothing.
        removeMediaNotification()

        super.onDestroy()
    }

    /**
     * Mirrors media3's own (private) MediaNotificationManager.removeNotification(): both the
     * foreground detach and the explicit cancel are needed, because the notification may have been
     * posted through either path depending on whether playback was running at the time.
     */
    private fun removeMediaNotification() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
    }

    /**
     * Artwork comes from arbitrary nostr imeta URLs, so it routinely arrives far larger than the
     * platform's metadata bitmap ceiling, and android.media.session.MediaSession.setMetadata() then
     * re-scales it inside MediaMetadata.Builder.build(). media3 stores the *same* Bitmap instance
     * under both METADATA_KEY_DISPLAY_ICON and METADATA_KEY_ALBUM_ART, so build() scales that one
     * instance twice. AOSP leaves the source alone, but on ROMs that recycle it while scaling the
     * second pass throws "cannot use a recycled source in createBitmap" on the main thread, inside
     * a Guava callback the app cannot intercept.
     *
     * media3 does size-limit artwork (SizeLimitedBitmapLoader), but it reads the ceiling from
     * Resources.getSystem() and falls back to the full display width when
     * config_mediaMetadataBitmapMaxSize can't be resolved by name, while the framework itself reads
     * it from the context that created the session. Capping it here, the same way the framework
     * measures it, keeps build() from scaling at all.
     */
    private val bitmapLoader: BitmapLoader by lazy {
        val decoder =
            DataSourceBitmapLoader
                .Builder(applicationContext)
                .setExecutorService(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get())
                // Subsampling only halves, so decoding straight to the ceiling can land at half the
                // size the session would have accepted (a 1080px image under a 900px ceiling decodes
                // to 540px). Decoding to just under 2x and letting MetadataArtworkBitmapLoader scale
                // precisely is the same recipe media3 uses for its own default loader.
                .setMaximumOutputDimension((metadataBitmapMaxSize() * 2 - 1).coerceAtLeast(1))
                .build()

        MetadataArtworkBitmapLoader(decoder, ::metadataBitmapMaxSize)
    }

    // getIdentifier() is a by-name lookup, so it is resolved once; the dimension itself is re-read
    // per load because it is a dp value and the app survives display-size changes without a restart.
    private val metadataBitmapMaxSizeResId by lazy {
        resources.getIdentifier("config_mediaMetadataBitmapMaxSize", "dimen", "android")
    }

    /**
     * Mirrors how android.media.session.MediaSession derives its metadata bitmap ceiling: the
     * framework dimension config_mediaMetadataBitmapMaxSize, resolved from the app context so it
     * matches the value the platform compares against. Falls back to AOSP's 320dp default when the
     * (hidden, framework-internal) resource can't be resolved by name.
     */
    private fun metadataBitmapMaxSize(): Int {
        val resolved =
            if (metadataBitmapMaxSizeResId != 0) {
                try {
                    resources.getDimensionPixelSize(metadataBitmapMaxSizeResId)
                } catch (e: Resources.NotFoundException) {
                    Log.w(TAG, "config_mediaMetadataBitmapMaxSize could not be read", e)
                    0
                }
            } else {
                0
            }
        return if (resolved > 0) resolved else (DEFAULT_METADATA_BITMAP_DP * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "PlaybackService"

        // AOSP default for config_mediaMetadataBitmapMaxSize, used when the framework resource
        // can't be resolved by name on a given ROM.
        private const val DEFAULT_METADATA_BITMAP_DP = 320
    }
}
