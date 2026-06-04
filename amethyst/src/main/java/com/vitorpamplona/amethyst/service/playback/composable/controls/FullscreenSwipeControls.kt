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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import android.content.ContentResolver
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import kotlinx.coroutines.delay

enum class SwipeAxis { Brightness, Volume }

private const val BRIGHTNESS_FLOOR = 0.01f
private const val AUTO_HIDE_MILLIS = 800L

/**
 * Holds the live state for the fullscreen brightness/volume swipe. A single instance is remembered
 * by RenderVideoPlayer and shared between the drag [Modifier] and the [FullscreenSwipeLevelIndicator]
 * overlay. All device side-effects (AudioManager / window brightness) are applied here.
 */
class FullscreenSwipeControlsState {
    var axis by mutableStateOf<SwipeAxis?>(null)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var visible by mutableStateOf(false)
        private set

    // Bumped on every drag event and on drag end; the overlay keys its auto-hide timer on this so
    // the timer restarts while dragging and fires AUTO_HIDE_MILLIS after the last event.
    var interactionId by mutableIntStateOf(0)
        private set

    private var dragStartLevel = 0f
    private var accumulatedDragPx = 0f

    fun startDrag(
        axis: SwipeAxis,
        audioManager: AudioManager?,
        window: Window?,
        resolver: ContentResolver,
    ) {
        this.axis = axis
        accumulatedDragPx = 0f
        dragStartLevel =
            when (axis) {
                SwipeAxis.Volume -> currentVolumeFraction(audioManager)
                SwipeAxis.Brightness -> currentBrightnessFraction(window, resolver)
            }
        level = dragStartLevel
        visible = true
        interactionId++
    }

    fun onDrag(
        dragAmountPx: Float,
        heightPx: Float,
        audioManager: AudioManager?,
        window: Window?,
    ) {
        accumulatedDragPx += dragAmountPx
        level = computeLevel(dragStartLevel, accumulatedDragPx, heightPx)
        when (axis) {
            SwipeAxis.Volume -> audioManager?.let { applyVolume(it, level) }
            SwipeAxis.Brightness -> window?.let { applyBrightness(it, level) }
            null -> Unit
        }
        interactionId++
    }

    fun endDrag() {
        interactionId++
    }

    fun hide() {
        visible = false
    }

    /**
     * Clears any brightness override this controller applied, restoring the window to the system
     * brightness. Call from the fullscreen player's onDispose so leaving fullscreen never leaves
     * the screen dimmed.
     */
    fun releaseBrightness(window: Window?) {
        window?.let { releaseBrightnessOverride(it) }
    }
}

private fun currentVolumeFraction(audioManager: AudioManager?): Float {
    audioManager ?: return 0f
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return 0f
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun currentBrightnessFraction(
    window: Window?,
    resolver: ContentResolver,
): Float {
    val override = window?.attributes?.screenBrightness ?: -1f
    if (override in 0f..1f) return override
    val system =
        try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            128
        }
    return (system / 255f).coerceIn(0f, 1f)
}

private fun applyVolume(
    audioManager: AudioManager,
    level: Float,
) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return
    // Flag 0 = no system volume UI; we draw our own ring.
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, levelToVolumeIndex(level, max), 0)
}

private fun applyBrightness(
    window: Window,
    level: Float,
) {
    val params = window.attributes
    params.screenBrightness = level.coerceIn(BRIGHTNESS_FLOOR, 1f)
    window.attributes = params
}

private fun releaseBrightnessOverride(window: Window) {
    val params = window.attributes
    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = params
}

/**
 * Vertical-drag handler for the fullscreen video surface. Left half of the surface controls
 * brightness, right half controls volume. Must be a separate [pointerInput] from the existing
 * tap/double-tap handler so taps still work.
 */
fun Modifier.fullscreenSwipeControls(
    state: FullscreenSwipeControlsState,
    audioManager: AudioManager?,
    window: Window?,
    resolver: ContentResolver,
): Modifier =
    pointerInput(state, audioManager, window, resolver) {
        detectVerticalDragGestures(
            onDragStart = { offset ->
                val axis = if (offset.x < size.width / 2f) SwipeAxis.Brightness else SwipeAxis.Volume
                state.startDrag(axis, audioManager, window, resolver)
            },
            onVerticalDrag = { _, dragAmount ->
                state.onDrag(dragAmount, size.height.toFloat(), audioManager, window)
            },
            onDragEnd = { state.endDrag() },
            onDragCancel = { state.endDrag() },
        )
    }

/** Centered ring + glyph that appears while swiping and fades out shortly after the drag ends. */
@Composable
fun BoxScope.FullscreenSwipeLevelIndicator(state: FullscreenSwipeControlsState) {
    LaunchedEffect(state.interactionId) {
        if (state.visible) {
            delay(AUTO_HIDE_MILLIS)
            state.hide()
        }
    }

    val alpha by animateFloatAsState(if (state.visible) 1f else 0f, label = "swipeIndicatorAlpha")
    if (alpha <= 0f) return

    val axis = state.axis ?: return
    val level = state.level
    val ringColor = MaterialTheme.colorScheme.onBackground
    val trackColor = ringColor.copy(alpha = 0.25f)
    val backdrop = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)

    Box(
        modifier =
            Modifier
                .align(Alignment.Center)
                .size(110.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * level.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }

        val symbol =
            when (axis) {
                SwipeAxis.Brightness -> MaterialSymbols.BrightnessMedium
                SwipeAxis.Volume ->
                    if (level <= 0f) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp
            }
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = ringColor,
            modifier = Modifier.size(36.dp),
        )
    }
}
