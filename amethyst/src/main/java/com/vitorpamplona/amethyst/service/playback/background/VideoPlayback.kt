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
package com.vitorpamplona.amethyst.service.playback.background

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.playback.coordinator.VideoPlaybackCoordinator
import com.vitorpamplona.amethyst.service.playback.coordinator.VideoRequest
import com.vitorpamplona.amethyst.service.playback.playerPool.PooledPlayer
import com.vitorpamplona.amethyst.service.playback.playerPool.VideoPlayerPools
import com.vitorpamplona.amethyst.service.playback.service.PlaybackService
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's view of who is playing video: every on-screen slot, plus the one playback the user has
 * explicitly detached from the feed — today by sending it to picture-in-picture, in future by any
 * "keep playing" affordance.
 *
 * Exactly one can be detached at a time, which is what makes it the right owner of the system's
 * single media surface: the notification, the lock screen, the headset buttons and audio focus.
 * Everything else on screen is a muted feed video the user never asked the system to know about,
 * and giving those a MediaSession each is what used to leave "video paused" notifications in the
 * shade for posts the user had never opened.
 *
 * The ownership rules — who gives a player back to the pool, and exactly once — live in
 * [VideoPlaybackCoordinator], where they are unit-tested without media3. This class is the thin
 * Android layer over them: the audio-focus handover and starting the media session host.
 */
@OptIn(UnstableApi::class)
class VideoPlayback(
    pools: VideoPlayerPools,
) {
    private val coordinator =
        VideoPlaybackCoordinator<PooledPlayer>(
            acquire = { pools.acquire(it.proxyPort, it.mediaId, it.repeatMode) },
            release = { it.release() },
        )

    /** The promoted checkout, or null. Collected by [PlaybackService] to attach its session. */
    val promoted: StateFlow<PooledPlayer?> = coordinator.promoted

    fun attach(
        slot: Any,
        request: VideoRequest,
    ): PooledPlayer = coordinator.attach(slot, request)

    fun detach(slot: Any) = coordinator.detach(slot)

    fun isPromoted(player: Player?): Boolean = player != null && coordinator.promoted.value?.player === player

    /** True when some *other* playback holds the surface — the caller must stay muted behind it. */
    fun hasPromotedOtherThan(player: Player?): Boolean {
        val current = coordinator.promoted.value ?: return false
        return current.player !== player
    }

    fun isPlaying(): Boolean =
        coordinator.promoted.value
            ?.player
            ?.isPlaying == true

    /**
     * Hands [checkout] the system media surface and starts the session host.
     *
     * Uses the plain `startService` rather than `startForegroundService`: at promotion the app is by
     * definition in the foreground (a user gesture got us here), and media3 promotes the service to
     * the foreground itself once the player is actually playing. Going through
     * startForegroundService would put us on the hook for a startForeground() call within ~5s, which
     * we cannot honour when the promoted playback is paused.
     */
    fun promote(
        checkout: PooledPlayer,
        context: Context,
    ) {
        if (coordinator.isPromoted(checkout)) return

        coordinator.promoted.value?.let { releaseSystemAudio(it.player) }
        claimSystemAudio(checkout.player)
        coordinator.promote(checkout)
        Log.d(TAG) { "Promoted ${checkout.player.currentMediaItem?.mediaId}" }

        try {
            context.applicationContext.startService(Intent(context.applicationContext, PlaybackService::class.java))
        } catch (e: IllegalStateException) {
            // Losing the race to a backgrounding costs the shade controls, not the playback.
            Log.w(TAG, "Could not start PlaybackService for the promoted playback", e)
        }
    }

    /** Promotes a playback no slot is showing — a PiP window reopening after a process restart. */
    fun promoteDetached(
        request: VideoRequest,
        context: Context,
    ): PooledPlayer {
        val checkout = coordinator.promoteDetached(request)
        claimSystemAudio(checkout.player)

        try {
            context.applicationContext.startService(Intent(context.applicationContext, PlaybackService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not start PlaybackService for the promoted playback", e)
        }
        return checkout
    }

    /**
     * Gives the surface up. The player goes back to the pool only if no slot is still showing it —
     * closing the window while the video is on screen simply drops it back inline.
     */
    fun demote() {
        val checkout = coordinator.promoted.value ?: return
        releaseSystemAudio(checkout.player)
        coordinator.demote()
        Log.d(TAG) { "Demoted ${checkout.player.currentMediaItem?.mediaId}" }
    }

    fun demoteIf(player: Player?) {
        if (isPromoted(player)) demote()
    }

    fun destroy() = coordinator.releaseAll()

    // Only the promoted playback arbitrates with other apps. Turning this on for every feed video
    // would have five muted players fighting each other and ducking the user's music.
    private fun claimSystemAudio(player: ExoPlayer) {
        player.setAudioAttributes(PROMOTED_AUDIO_ATTRIBUTES, true)
        player.setHandleAudioBecomingNoisy(true)
    }

    private fun releaseSystemAudio(player: ExoPlayer) {
        player.pause()
        player.setHandleAudioBecomingNoisy(false)
        // Drops the focus request so a pooled player reused by a muted feed video does not keep
        // arbitrating with other apps.
        player.setAudioAttributes(PROMOTED_AUDIO_ATTRIBUTES, false)
    }

    companion object {
        private const val TAG = "VideoPlayback"

        private val PROMOTED_AUDIO_ATTRIBUTES =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
    }
}

/**
 * Holds the promotion for as long as this composable is in composition. Used by the
 * picture-in-picture window: leaving it (swipe away, expand back into the app) gives the surface
 * up, which stops the service and takes the notification down with it.
 */
@Composable
fun HoldPromotedPlayback(player: Player) {
    DisposableEffect(player) {
        onDispose {
            Amethyst.instance.videoPlayback.demoteIf(player)
        }
    }
}
