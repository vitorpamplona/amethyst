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
package com.vitorpamplona.amethyst.commons.ui.playback.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Multiplatform equivalents of the media3-ui-compose state holders the Android player UI used
 * (`rememberPlayPauseButtonState`, `rememberProgressStateWithTickInterval`,
 * `rememberProgressStateWithTickCount`, `rememberPlaybackSpeedState`) and of the media3 `Util`
 * play/pause helpers, ported from media3 1.11 onto [AmethystPlayer] with the same events and the
 * same arithmetic, so the controls behave as they did. Player commands are taken as available: the
 * app's players always grant play/pause, prepare, seek and speed.
 */

/**
 * media3's `Util.shouldShowPlayButton`: offer play when it is not set to play, when it is idle or
 * ended, or when something outside the app holds it back.
 */
fun AmethystPlayer.shouldShowPlayButton(): Boolean =
    !playWhenReady ||
        playbackState == PlaybackState.IDLE ||
        playbackState == PlaybackState.ENDED ||
        isPlaybackSuppressed

/** media3's `Util.shouldEnablePlayPauseButton`, with every command available: there is an item. */
fun AmethystPlayer.shouldEnablePlayPauseButton(): Boolean = mediaId != null

/**
 * media3's `Util.handlePlayPauseButtonAction`: play re-prepares an idle player and rewinds an
 * ended one before playing; pause just pauses.
 */
fun AmethystPlayer.handlePlayPauseButtonAction() {
    if (shouldShowPlayButton()) {
        when (playbackState) {
            PlaybackState.IDLE -> prepare()
            PlaybackState.ENDED -> seekToDefaultPosition()
            else -> Unit
        }
        play()
    } else {
        pause()
    }
}

/** The play/pause button's state, refreshed on playback-state and play-when-ready changes. */
@Composable
fun rememberPlayPauseButtonState(player: AmethystPlayer): PlayPauseButtonState {
    val state = remember(player) { PlayPauseButtonState(player) }
    LaunchedEffect(player) { state.observe() }
    return state
}

@Stable
class PlayPauseButtonState(
    private val player: AmethystPlayer,
) {
    var isEnabled by mutableStateOf(false)
        private set

    var showPlay by mutableStateOf(true)
        private set

    private val listener =
        object : AmethystPlayer.Listener {
            override fun onPlaybackStateChanged(state: PlaybackState) = update()

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean) = update()
        }

    init {
        update()
    }

    private fun update() {
        isEnabled = player.shouldEnablePlayPauseButton()
        showPlay = player.shouldShowPlayButton()
    }

    fun onClick() {
        player.handlePlayPauseButtonAction()
    }

    suspend fun observe(): Nothing {
        update()
        player.addListener(listener)
        try {
            awaitCancellation()
        } finally {
            player.removeListener(listener)
        }
    }
}

/** The speed button's state: whether the speed can change, and what it is. */
@Composable
fun rememberPlaybackSpeedState(player: AmethystPlayer): PlaybackSpeedState {
    val state = remember(player) { PlaybackSpeedState(player) }
    LaunchedEffect(player) { state.observe() }
    return state
}

@Stable
class PlaybackSpeedState(
    private val player: AmethystPlayer,
) {
    var isEnabled by mutableStateOf(false)
        private set

    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    private val listener =
        object : AmethystPlayer.Listener {
            override fun onPlaybackSpeedChanged(speed: Float) = update()
        }

    init {
        update()
    }

    private fun update() {
        isEnabled = player.canSetPlaybackSpeed
        playbackSpeed = player.playbackSpeed
    }

    fun updatePlaybackSpeed(speed: Float) {
        if (player.canSetPlaybackSpeed) player.playbackSpeed = speed
    }

    suspend fun observe(): Nothing {
        update()
        player.addListener(listener)
        try {
            awaitCancellation()
        } finally {
            player.removeListener(listener)
        }
    }
}

/**
 * media3's `rememberProgressStateWithTickInterval`: position, buffered position and duration,
 * snapped to [tickIntervalMs] and refreshed once per tick while playing (once a second otherwise).
 */
@Composable
fun rememberProgressStateWithTickInterval(
    player: AmethystPlayer,
    tickIntervalMs: Long = 0,
    scope: CoroutineScope = rememberCoroutineScope(),
): ProgressStateWithTickInterval {
    val state = remember(player, tickIntervalMs) { ProgressStateWithTickInterval(player, tickIntervalMs, scope) }
    LaunchedEffect(player) { state.observe() }
    return state
}

@Stable
class ProgressStateWithTickInterval(
    private val player: AmethystPlayer,
    private val tickIntervalMs: Long = 0,
    scope: CoroutineScope,
) {
    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var bufferedPositionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(AmethystPlayer.TIME_UNSET)
        private set

    private val updateJob =
        ProgressStateJob(
            player = player,
            scope = scope,
            nextMediaTickMsSupplier = { nextMediaWakeUpPositionMs() },
            shouldScheduleTask = { player.isReadyOrBuffering() },
            scheduledTask = { updateProgress() },
        )

    init {
        require(tickIntervalMs >= 0)
        updateProgress()
    }

    suspend fun observe(): Nothing = updateJob.observeProgress()

    private fun nextMediaWakeUpPositionMs(): Long {
        if (tickIntervalMs == 0L) {
            return 0
        }
        val currentPositionMs = player.currentPosition
        val nextTickIndex = currentPositionMs / tickIntervalMs + 1
        var nextMediaWakeUpPositionMs = nextTickIndex * tickIntervalMs
        val idealDelayDuration = nextMediaWakeUpPositionMs - currentPositionMs
        if (idealDelayDuration < MIN_UPDATE_INTERVAL_MS) {
            nextMediaWakeUpPositionMs = (nextTickIndex + 1) * tickIntervalMs
        }
        return nextMediaWakeUpPositionMs
    }

    private fun updateProgress() {
        currentPositionMs = snapPositionToNearestTick(player.currentPosition)
        bufferedPositionMs = snapPositionToNearestTick(player.bufferedPosition)
        durationMs = player.duration
    }

    private fun snapPositionToNearestTick(actualPositionMs: Long): Long {
        if (tickIntervalMs == 0L || actualPositionMs % tickIntervalMs == 0L) {
            return actualPositionMs
        }
        val adjustedCurrentTick = (actualPositionMs + POSITION_CORRECTION_OFFSET_MS) / tickIntervalMs
        return adjustedCurrentTick * tickIntervalMs
    }
}

/**
 * media3's `rememberProgressStateWithTickCount`: position and buffered position as fractions of the
 * duration, moving in [totalTickCount] steps (one per pixel of a progress bar) and refreshed once per
 * step while playing.
 */
@Composable
fun rememberProgressStateWithTickCount(
    player: AmethystPlayer,
    totalTickCount: Int = 0,
    scope: CoroutineScope = rememberCoroutineScope(),
): ProgressStateWithTickCount {
    val state = remember(player) { ProgressStateWithTickCount(player, totalTickCount, scope) }
    LaunchedEffect(player) { state.observe() }
    LaunchedEffect(totalTickCount) { state.updateTotalTickCount(totalTickCount) }
    return state
}

@Stable
class ProgressStateWithTickCount(
    private val player: AmethystPlayer,
    private var totalTickCount: Int = 0,
    scope: CoroutineScope,
) {
    var currentPositionProgress by mutableFloatStateOf(0f)
        private set

    var bufferedPositionProgress by mutableFloatStateOf(0f)
        private set

    var durationMs by mutableLongStateOf(AmethystPlayer.TIME_UNSET)
        private set

    var changingProgressEnabled by mutableStateOf(false)
        private set

    private val updateJob =
        ProgressStateJob(
            player = player,
            scope = scope,
            nextMediaTickMsSupplier = { nextMediaWakeUpPositionMs() },
            shouldScheduleTask = {
                player.isReadyOrBuffering() && canCalculateTicks(totalTickCount, player.duration)
            },
            scheduledTask = { updateProgress() },
        )

    fun updateTotalTickCount(newTotalTickCount: Int) {
        if (totalTickCount != newTotalTickCount) {
            totalTickCount = newTotalTickCount
            updateJob.cancelPendingUpdatesAndMaybeRelaunch()
        }
    }

    fun updateCurrentPositionProgress(progress: Float) {
        if (durationMs > 0L) {
            player.seekTo((progress * durationMs).toLong())
        }
    }

    fun progressToPosition(progress: Float): Long = if (durationMs > 0) (progress * durationMs).toLong() else 0L

    init {
        require(totalTickCount >= 0)
        updateProgress()
    }

    suspend fun observe(): Nothing = updateJob.observeProgress()

    private fun nextMediaWakeUpPositionMs(): Long {
        check(totalTickCount != 0)
        check(durationMs > 0)
        val currentPositionTick = getPositionTick(player.currentPosition, durationMs, totalTickCount)
        val nextTickIndex = currentPositionTick + 1
        val midInterval = durationMs / (2 * totalTickCount)
        return (nextTickIndex * durationMs) / totalTickCount - midInterval
    }

    private fun updateProgress() {
        durationMs = player.duration
        currentPositionProgress = positionToProgress(player.currentPosition, durationMs, totalTickCount)
        bufferedPositionProgress = positionToProgress(player.bufferedPosition, durationMs, totalTickCount)
        changingProgressEnabled = durationMs > 0
    }

    private fun getPositionTick(
        position: Long,
        duration: Long,
        totalTickCount: Int,
    ): Int {
        // 1. accounting for player position estimation jitter with 10ms correction/990ms rounding
        // 2. rounding from half an interval (unlike the stopwatch nature of ProgressStateWithInterval
        // where rounding only happened at the tick plus jitter). Round up the pixel half way through.
        val midInterval = duration / (2 * totalTickCount)
        return ((position + POSITION_CORRECTION_OFFSET_MS + midInterval) * totalTickCount / duration)
            .toInt()
            .coerceIn(0, totalTickCount)
    }

    private fun positionToProgress(
        positionMs: Long,
        durationMs: Long,
        totalTickCount: Int,
    ): Float {
        if (!canCalculateTicks(totalTickCount, durationMs)) {
            return 0f
        }
        val tickIndex = getPositionTick(positionMs, durationMs, totalTickCount)
        return tickIndex.toFloat() / totalTickCount
    }

    private fun canCalculateTicks(
        totalTickCount: Int,
        durationMs: Long,
    ): Boolean = totalTickCount != 0 && durationMs > 0L
}

/**
 * media3's `ProgressStateJob`: runs [scheduledTask] on every play, seek, timeline and speed
 * change, and between them on a loop timed to the next tick while [shouldScheduleTask].
 */
internal class ProgressStateJob(
    private val player: AmethystPlayer,
    private val scope: CoroutineScope,
    private val nextMediaTickMsSupplier: () -> Long,
    private val shouldScheduleTask: () -> Boolean,
    private val scheduledTask: () -> Unit,
) {
    private var updateJob: Job? = null
    private var isObserving = false

    private val listener =
        object : AmethystPlayer.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = cancelPendingUpdatesAndMaybeRelaunch()

            override fun onPositionDiscontinuity() = cancelPendingUpdatesAndMaybeRelaunch()

            override fun onTimelineChanged() = cancelPendingUpdatesAndMaybeRelaunch()

            override fun onPlaybackSpeedChanged(speed: Float) = cancelPendingUpdatesAndMaybeRelaunch()
        }

    suspend fun observeProgress(): Nothing {
        isObserving = true
        try {
            cancelPendingUpdatesAndMaybeRelaunch()
            player.addListener(listener)
            awaitCancellation()
        } finally {
            player.removeListener(listener)
            isObserving = false
            updateJob?.cancel()
        }
    }

    fun cancelPendingUpdatesAndMaybeRelaunch() {
        scheduledTask()
        if (!isObserving) {
            return
        }
        updateJob?.cancel()
        if (shouldScheduleTask()) {
            updateJob =
                scope.launch {
                    while (isActive) {
                        smartDelay()
                        scheduledTask()
                    }
                }
        }
    }

    private suspend fun smartDelay() {
        if (player.isPlaying) {
            val mediaTimeToNextTickMs = nextMediaTickMsSupplier() - player.currentPosition
            // Convert the interval to wall-clock time
            val realTimeToNextTickMs = mediaTimeToNextTickMs / player.playbackSpeed
            if (realTimeToNextTickMs < MIN_UPDATE_INTERVAL_MS) {
                // Throttle by recomposition frequency
                withFrameMillis {}
            } else {
                // Prevent infinite delays by 0
                delay(realTimeToNextTickMs.toLong().coerceAtLeast(1L))
            }
        } else {
            delay(FALLBACK_UPDATE_INTERVAL_MS)
        }
    }
}

internal fun AmethystPlayer.isReadyOrBuffering(): Boolean = playbackState == PlaybackState.READY || playbackState == PlaybackState.BUFFERING

/**
 * media3's `Util.getStringForTime`: `mm:ss`, or `h:mm:ss` from an hour up, rounded to the nearest
 * second; [AmethystPlayer.TIME_UNSET] reads as zero.
 */
fun formatPlaybackTime(timeMs: Long): String {
    val time = if (timeMs == AmethystPlayer.TIME_UNSET) 0 else timeMs
    val prefix = if (time < 0) "-" else ""
    val totalSeconds = (abs(time) + 500) / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "$prefix$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
    } else {
        "$prefix${minutes.twoDigits()}:${seconds.twoDigits()}"
    }
}

private fun Long.twoDigits() = toString().padStart(2, '0')

// Taking highest frame rate as 120fps, interval is 1000/120
private const val MIN_UPDATE_INTERVAL_MS = 8L
private const val FALLBACK_UPDATE_INTERVAL_MS = 1000L
private const val POSITION_CORRECTION_OFFSET_MS = 10
