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
package com.vitorpamplona.amethyst.ui.insets

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * How long the animated IME inset may disagree with the IME's animation target, without emitting
 * anything new, before we call it stranded rather than in flight.
 *
 * A real IME animation reports a new inset every frame, so any value comfortably above a few
 * dropped frames never trips on one. In the stranded case nothing is emitted at all, so this is
 * purely how long the stale padding stays on screen before it is corrected — which is why this is
 * kept as tight as the frame cadence safely allows rather than padded "to be sure".
 *
 * The gap that actually binds this is not the frame cadence but the dead time between the target
 * flipping and the animation's first onProgress: until that first frame lands, "not started yet"
 * and "wedged" look identical. Measured on a Pixel 8 (120Hz) over 12 real Gboard transitions:
 * that dead time is 17-36ms (mean 24ms; closes 24-36ms, opens 17-23ms), after which every
 * subsequent frame lands within 11ms across a ~264ms animation. 120ms therefore clears the worst
 * observed start-up gap by ~3.3x while keeping the stale padding below the threshold where it
 * reads as lag. The earlier 400ms was ~11x that gap, and once a window wedges its IME animation
 * the full 400ms was paid on *every* dismissal.
 *
 * A value set too low degrades to a cosmetic snap — the padding jumps to its final position
 * instead of animating — never to wrong padding, since the target is always the truthful reading.
 */
const val IME_STRAND_GRACE_MS = 120L

/**
 * The animated IME inset ([WindowInsets.ime]), corrected for a Compose insets listener that has
 * stopped updating.
 *
 * Compose keeps one `InsetsListener` per window in `WindowInsetsHolder`. It sets an internal
 * `runningAnimation` flag in `onPrepare` and clears it only in `onEnd` (plus an `onApplyWindowInsets`
 * fallback that is gated to API 30). While that flag is set, `onApplyWindowInsets` deliberately
 * skips `update(insets)` and waits for `onProgress` to drive the value instead. So an IME animation
 * that is prepared and then cancelled without ever delivering `onEnd` — which the back gesture can
 * do, because the predictive-back window animation races the IME's own close animation — leaves the
 * flag set forever. Every `WindowInsets` in the window then freezes at its last animated value, and
 * `imePadding()` holds a keyboard-sized gap open under a keyboard that is already gone.
 *
 * Nothing recovers that on its own: the listener is only reset when `WindowInsetsHolder`'s access
 * count goes 0 -> 1, and this app reads `WindowInsets.ime` app-wide continuously, so the count never
 * reaches zero while the activity lives. That is why the symptom is sticky — once it starts, only
 * recreating the activity clears it.
 *
 * The escape hatch is that `onApplyWindowInsets` updates
 * [imeAnimationTarget][WindowInsets.Companion.imeAnimationTarget] *before* it consults that flag, so
 * the target keeps tracking reality even while the animated value is frozen. This class watches the
 * two: when they disagree and stop moving, the animated value is stale and the target is the truth.
 *
 * That covers the freeze in both directions — a keyboard-height gap left behind after the keyboard
 * is gone, and missing padding under a keyboard that has come back — since the target is correct
 * either way.
 */
@Stable
class SafeImeInsets(
    private val animated: WindowInsets,
    private val target: WindowInsets,
) : WindowInsets {
    /**
     * `true` while [animated] is known to be frozen at a value the window no longer reports. Set by
     * the watchdog in [rememberSafeImeInsets]; read from the getters below, so flipping it re-runs
     * the layout that depends on this inset.
     */
    var isStranded by mutableStateOf(false)
        internal set

    private fun source() = if (isStranded) target else animated

    override fun getLeft(
        density: Density,
        layoutDirection: LayoutDirection,
    ) = source().getLeft(density, layoutDirection)

    override fun getTop(density: Density) = source().getTop(density)

    override fun getRight(
        density: Density,
        layoutDirection: LayoutDirection,
    ) = source().getRight(density, layoutDirection)

    override fun getBottom(density: Density) = source().getBottom(density)
}

/** One reading of the animated IME inset next to the target it is animating towards. */
internal data class ImeInsetSample(
    val animated: Int,
    val target: Int,
)

/**
 * Reports whether the animated IME inset has gone stale.
 *
 * The two readings differ for the whole length of every legitimate IME animation, so the value
 * alone can't tell "animating" from "frozen" — only the clock can. A running animation emits a new
 * sample every frame and [collectLatest] restarts the wait; a frozen one emits nothing, so the wait
 * is allowed to finish and the inset is declared stranded.
 *
 * Extracted from [rememberSafeImeInsets] so the timing can be tested on virtual time.
 */
internal suspend fun watchForStrandedIme(
    samples: Flow<ImeInsetSample>,
    graceMillis: Long = IME_STRAND_GRACE_MS,
    onStranded: (Boolean) -> Unit,
): Unit =
    samples.collectLatest { sample ->
        if (sample.animated == sample.target) {
            onStranded(false)
        } else {
            delay(graceMillis)
            onStranded(true)
        }
    }

/**
 * The IME inset to lay out against, in place of [WindowInsets.ime]. See [SafeImeInsets] for why the
 * raw one can't be trusted for the lifetime of the activity.
 *
 * Remembered per call site rather than shared through a `CompositionLocal` on purpose: the insets
 * are a property of the window, and a `Dialog` composes against its own one. A shared instance would
 * hand every dialog the host activity's insets. The watchdog is idle whenever the two readings agree
 * — that is, always, apart from the length of an IME animation — so the per-site cost is a state
 * object and a parked coroutine.
 *
 * `imeAnimationTarget` is still marked experimental, but it is the whole point of this file: it is
 * the only IME reading Compose keeps current while its listener is wedged, because
 * `onApplyWindowInsets` publishes it before it consults the flag that gates everything else.
 *
 * The underlying defect is upstream, not ours: a cancelled IME animation never delivers `onEnd`,
 * so `InsetsListener.runningAnimation` stays set and `composeInsets.update()` is never called
 * again — `WindowInsets.ime` is dead for the life of the window. Filed as **b/552500419**;
 * a regression in foundation-layout 1.4.0, still present in 1.12.0 and 1.13.0-alpha01. Compose's
 * own self-heal is scoped to `SDK_INT == R`, and its other reset (`WindowInsetsHolder.resetState()`)
 * only runs when the holder's accessCount goes 0 -> 1, which never happens in an app whose shell
 * always reads insets. Retire this file once that bug is fixed — `ComposeImeInsetWedgeTest`
 * (androidTest) is the canary that says when.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberSafeImeInsets(): SafeImeInsets {
    val density = LocalDensity.current
    val animated = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget

    val insets = remember(animated, target) { SafeImeInsets(animated, target) }

    LaunchedEffect(insets, density) {
        watchForStrandedIme(
            snapshotFlow { ImeInsetSample(animated.getBottom(density), target.getBottom(density)) },
        ) {
            insets.isStranded = it
        }
    }

    return insets
}

/**
 * Drop-in replacement for `Modifier.imePadding()` that survives a stranded IME inset.
 *
 * Prefer this everywhere in the app; `imePadding()` reads the raw animated inset and will hold a
 * keyboard-sized gap open for the rest of the activity's life once Compose's insets listener wedges.
 * Consumption semantics are identical — this is `windowInsetsPadding` over the same inset.
 */
@Composable
fun Modifier.imePaddingSafe(): Modifier = windowInsetsPadding(rememberSafeImeInsets())
