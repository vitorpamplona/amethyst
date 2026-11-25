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
package com.vitorpamplona.amethyst.commons.ui.playback.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.audio.player.AudioPlaybackUi
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackState
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlayerFailure
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSize
import com.vitorpamplona.amethyst.commons.ui.playback.player.shouldShowPlayButton
import kotlinx.coroutines.delay

/** The player's state as the card draws it: event-driven, plus a position poll while it plays. */
@Composable
fun rememberAudioPlaybackUi(player: AmethystPlayer): State<AudioPlaybackUi> {
    val state = remember(player) { mutableStateOf(player.toPlaybackUi()) }
    DisposableEffect(player) {
        // Refreshed on every change the player announces, as media3's onEvents did.
        val listener = AnyChangeListener { state.value = player.toPlaybackUi() }
        player.addListener(listener)
        state.value = player.toPlaybackUi()
        onDispose { player.removeListener(listener) }
    }

    val isPlaying = state.value.isPlaying
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            delay(POSITION_POLL_MS)
            state.value = player.toPlaybackUi()
        }
    }
    return state
}

private fun AmethystPlayer.toPlaybackUi() =
    AudioPlaybackUi(
        // Matches what the button should offer: pause while playing or about to (buffering with
        // playWhenReady), play when paused, ended or idle.
        isPlaying = !shouldShowPlayButton(),
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = duration.takeIf { it != AmethystPlayer.TIME_UNSET && it > 0 },
    )

/** Runs [onChange] for every change a player announces: the shared stand-in for media3's `onEvents`. */
class AnyChangeListener(
    private val onChange: () -> Unit,
) : AmethystPlayer.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) = onChange()

    override fun onPlaybackStateChanged(state: PlaybackState) = onChange()

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean) = onChange()

    override fun onPlaybackSuppressionChanged(suppressed: Boolean) = onChange()

    override fun onTimelineChanged() = onChange()

    override fun onVolumeChanged(volume: Float) = onChange()

    override fun onVideoSizeChanged(size: VideoSize) = onChange()

    override fun onTracksChanged() = onChange()

    override fun onPlayerErrorChanged(error: PlayerFailure?) = onChange()

    override fun onPositionDiscontinuity() = onChange()

    override fun onMediaItemTransition(mediaId: String?) = onChange()

    override fun onPlaybackSpeedChanged(speed: Float) = onChange()

    override fun onCues(lines: List<String>) = onChange()
}

private const val POSITION_POLL_MS = 250L
