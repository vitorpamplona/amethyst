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
package com.vitorpamplona.amethyst.service.playback.playerPool.repeat

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Caps how many times a video loops on its own under [Player.REPEAT_MODE_ONE].
 *
 * Feed videos are configured to repeat forever (keepPlaying → REPEAT_MODE_ONE in
 * PlaybackService), which keeps decoders, network and the screen busy long after the
 * user stopped watching. This listener lets a video play [maxAutoPlays] full times and
 * then stops it at the end of the last one, so continuing requires an explicit press of
 * the play button.
 *
 * Each loop under REPEAT_MODE_ONE surfaces as an [onMediaItemTransition] with
 * [Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT]. When the last allowed play starts, this arms
 * ExoPlayer's pause-at-end-of-media-item, so the playback thread itself stops on the last
 * frame with the position at the end, before the next loop begins.
 *
 * Don't pause() from the transition instead: by the time a listener hears of it, the next
 * loop has already started on the playback thread, and the pause races it, so the frame on
 * screen and the progress bar can disagree with where the player really stopped. Stopping
 * at the end leaves nothing to race, so the frame, the position and the play button agree.
 *
 * Any resume — the user pressing play, or the feed mutex auto-playing when the video
 * scrolls back to the center — disarms the pause and grants a fresh allowance. A resume
 * from the stop at the end goes straight into the next loop, which is the first play of
 * that allowance. Switching to a different media item resets it too.
 */
class AutoReplayLimiter(
    val maxAutoPlays: Int = DEFAULT_MAX_AUTO_PLAYS,
    val setPauseAtEndOfMediaItems: (Boolean) -> Unit,
) : Player.Listener {
    // Plays begun since the last resume, the one in progress included.
    private var playsStarted = 1

    // Mirrors what was last pushed to the player, so a call only goes out on a change: arming
    // while the player is already reading the next loop makes ExoPlayer flush its renderers.
    private var armed = false

    // Paused by the armed stop, on the last frame: a resume from here starts the next play.
    private var stoppedAtEnd = false

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        playsStarted = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) playsStarted + 1 else 1
        stoppedAtEnd = false
        updatePauseAtEnd()
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (playWhenReady) {
            playsStarted = if (stoppedAtEnd) 0 else 1
            stoppedAtEnd = false
            updatePauseAtEnd()
        } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            stoppedAtEnd = true
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        // Seeking away from the stop at the end means the resume continues the current play.
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            stoppedAtEnd = false
        }
    }

    private fun updatePauseAtEnd() {
        val shouldArm = playsStarted >= maxAutoPlays
        if (shouldArm != armed) {
            armed = shouldArm
            setPauseAtEndOfMediaItems(shouldArm)
        }
    }

    companion object {
        const val DEFAULT_MAX_AUTO_PLAYS = 5
    }
}
