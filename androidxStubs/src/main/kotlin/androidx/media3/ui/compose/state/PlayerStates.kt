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
package androidx.media3.ui.compose.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/**
 * JVM stand-ins for media3's Compose state holders.
 *
 * These are thin observers over [Player] — they subscribe to its listener and
 * expose the bits a button or progress bar needs — so they are reimplemented
 * here rather than stubbed inert. A play/pause button that never updates would
 * look broken in a way no error message explains.
 */
class PlayPauseButtonState internal constructor(
    private val player: Player,
    val showPlay: Boolean,
    val isEnabled: Boolean,
) {
    fun onClick() {
        if (player.isPlaying) player.pause() else player.play()
    }
}

@Composable
fun rememberPlayPauseButtonState(player: Player): PlayPauseButtonState {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var state by remember(player) { mutableStateOf(player.playbackState) }
    ObservePlayer(player) { p ->
        isPlaying = p.isPlaying
        state = p.playbackState
    }
    return PlayPauseButtonState(player, showPlay = !isPlaying, isEnabled = state != Player.STATE_IDLE)
}

class MuteButtonState internal constructor(
    private val player: Player,
    val showMuted: Boolean,
) {
    fun onClick() {
        player.volume = if (player.volume == 0f) 1f else 0f
    }
}

@Composable
fun rememberMuteButtonState(player: Player): MuteButtonState {
    var volume by remember(player) { mutableStateOf(player.volume) }
    ObservePlayer(player) { volume = it.volume }
    return MuteButtonState(player, showMuted = volume == 0f)
}

class PlaybackSpeedState internal constructor(
    val playbackSpeed: Float,
) {
    fun updatePlaybackSpeed(speed: Float) = Unit
}

@Composable
fun rememberPlaybackSpeedState(player: Player): PlaybackSpeedState = remember(player) { PlaybackSpeedState(1f) }

class ProgressState internal constructor(
    val currentPositionProgress: Float,
    val bufferedPositionProgress: Float,
    val currentPositionMs: Long,
    val durationMs: Long,
)

@Composable
fun rememberProgressStateWithTickInterval(
    player: Player,
    tickIntervalMs: Long,
): ProgressState = rememberProgress(player)

@Composable
fun rememberProgressStateWithTickCount(
    player: Player,
    tickCount: Int,
): ProgressState = rememberProgress(player)

@Composable
private fun rememberProgress(player: Player): ProgressState {
    var position by remember(player) { mutableStateOf(player.currentPosition) }
    var buffered by remember(player) { mutableStateOf(player.bufferedPosition) }
    var duration by remember(player) { mutableStateOf(player.duration) }
    ObservePlayer(player) { p ->
        position = p.currentPosition
        buffered = p.bufferedPosition
        duration = p.duration
    }
    val total = duration.takeIf { it > 0 } ?: 0L
    return ProgressState(
        currentPositionProgress = if (total > 0) position.toFloat() / total else 0f,
        bufferedPositionProgress = if (total > 0) buffered.toFloat() / total else 0f,
        currentPositionMs = position,
        durationMs = duration,
    )
}

/**
 * Subscribes for as long as the caller is composed. The engine drives these
 * callbacks; nothing here polls, so a paused video costs nothing.
 */
@Composable
private fun ObservePlayer(
    player: Player,
    onChanged: (Player) -> Unit,
) {
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = onChanged(player)

                override fun onPlaybackStateChanged(playbackState: Int) = onChanged(player)

                override fun onVolumeChanged(volume: Float) = onChanged(player)

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) = onChanged(player)
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}
