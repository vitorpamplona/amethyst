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
import com.vitorpamplona.amethyst.service.playback.playerPool.PooledPlayer
import com.vitorpamplona.amethyst.service.playback.service.PlaybackService
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one playback the user has explicitly detached from the feed — today by sending it to
 * picture-in-picture, in future by any "keep playing" affordance.
 *
 * Exactly one at a time, which is what makes it the right owner of the system's single media
 * surface: the notification, the lock screen, the headset buttons and audio focus. Everything else
 * on screen is a muted feed video the user never asked the system to know about, and giving those a
 * MediaSession each is what used to leave "video paused" notifications in the shade for posts the
 * user had never opened.
 *
 * Promotion is deliberately a *player* handover, not a re-acquire by URI: the promoted player keeps
 * the buffer, position and decoder it already had, so there is no window in which the pool can
 * demote it for decoder headroom and the PiP window opens black.
 */
@OptIn(UnstableApi::class)
class BackgroundPlayback {
    private val _current = MutableStateFlow<PromotedPlayback?>(null)

    /** Collected by [PlaybackService] to attach and detach its single MediaSession. */
    val current: StateFlow<PromotedPlayback?> = _current.asStateFlow()

    fun isPromoted(player: Player?): Boolean = player != null && _current.value?.pooled?.player === player

    /** True when some *other* playback holds the slot — the caller must stay muted behind it. */
    fun hasPromotedOtherThan(player: Player?): Boolean {
        val promoted = _current.value ?: return false
        return promoted.pooled.player !== player
    }

    fun isPlaying(): Boolean =
        _current.value
            ?.pooled
            ?.player
            ?.isPlaying == true

    /**
     * Hands [pooled] the system media surface. Starts [PlaybackService] with the plain
     * `startService` rather than `startForegroundService`: at promotion the app is by definition in
     * the foreground (a user gesture got us here), and media3 promotes the service to the
     * foreground itself once the player is actually playing. Going through startForegroundService
     * would put us on the hook for a startForeground() call within ~5s, which we cannot honour when
     * the promoted playback is paused.
     */
    fun promote(
        pooled: PooledPlayer,
        context: Context,
    ) {
        val previous = _current.value
        if (previous?.pooled?.player === pooled.player) return

        // Promotion transfers ownership of the checkout to this slot, so a promotion it replaces
        // has to go home here — the composable that acquired it has already let go of it.
        previous?.let {
            detach(it)
            it.pooled.release()
        }

        // Only the promoted playback arbitrates with other apps. Turning this on for every feed
        // video would have five muted players fighting each other and ducking the user's music.
        pooled.player.setAudioAttributes(BACKGROUND_AUDIO_ATTRIBUTES, true)
        pooled.player.setHandleAudioBecomingNoisy(true)

        _current.value = PromotedPlayback(pooled)
        Log.d(TAG) { "Promoted ${pooled.player.currentMediaItem?.mediaId}" }

        // Promotion is always a foreground gesture, so a plain startService is allowed here. Losing
        // the race to a backgrounding costs the shade controls, not the playback, so it is logged
        // rather than thrown.
        try {
            context.applicationContext.startService(
                Intent(context.applicationContext, PlaybackService::class.java),
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not start PlaybackService for the promoted playback", e)
        }
    }

    /**
     * Gives up the slot and returns the player to the pool it came from — this slot owns the
     * checkout while it holds it. Idempotent, because both the UI leaving composition and the
     * service being torn down land here.
     */
    fun demote() {
        val promoted = _current.value ?: return
        _current.value = null
        detach(promoted)
        promoted.pooled.release()
        Log.d(TAG) { "Demoted ${promoted.player.currentMediaItem?.mediaId}" }
    }

    /** Gives up the slot only if [player] is the one holding it. */
    fun demoteIf(player: Player?) {
        if (isPromoted(player)) demote()
    }

    private fun detach(promoted: PromotedPlayback) {
        val player = promoted.pooled.player
        player.pause()
        player.setHandleAudioBecomingNoisy(false)
        // Drops the focus request so a pooled player reused by a muted feed video does not keep
        // arbitrating with other apps.
        player.setAudioAttributes(BACKGROUND_AUDIO_ATTRIBUTES, false)
    }

    companion object {
        private const val TAG = "BackgroundPlayback"

        private val BACKGROUND_AUDIO_ATTRIBUTES =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
    }
}

/**
 * The promoted playback. Only the pool checkout is held: the item whose metadata the shade shows is
 * read from the player itself, so it cannot drift out of step with what is actually loaded.
 */
@OptIn(UnstableApi::class)
class PromotedPlayback(
    val pooled: PooledPlayer,
) {
    val player: ExoPlayer get() = pooled.player
}

/**
 * Holds the slot for as long as this composable is in composition. Used by the picture-in-picture
 * window: leaving it (swipe away, expand back into the app) gives the slot up, which stops the
 * service and takes the notification down with it.
 */
@Composable
fun HoldBackgroundPlayback(player: Player) {
    DisposableEffect(player) {
        onDispose {
            Amethyst.instance.backgroundPlayback.demoteIf(player)
        }
    }
}
