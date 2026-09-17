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
package com.vitorpamplona.amethyst.service.resourceusage

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.view.FrameMetrics
import android.view.Window
import com.vitorpamplona.quartz.utils.Log

/**
 * Counts frames the app actually drew, split by visibility, plus the slow ones.
 *
 * **Why frames and not recompositions.** The diagnosis plan asked for a
 * "recompositions while backgrounded" tripwire. Recomposition turns out not to
 * be passively observable: the in-process hooks that can see it —
 * `withFrameNanos`, a self-reposting `Choreographer` callback — keep the frame
 * clock running by asking, so the measurement would manufacture the work it
 * claims to measure, on a battery investigation. `OnFrameMetricsAvailableListener`
 * fires only when a frame really was produced, asks for nothing, and reports the
 * thing that actually costs energy: the GPU and display pipeline, rather than
 * the composition upstream of it.
 *
 * **What a reading means.** A backgrounded window is not drawn, so
 * `ui.frames.bg.count` should be ~0. Zero closes half of hypothesis H5 with
 * evidence — the `while (true)` loops in composables are not driving frames when
 * nobody is looking. Anything else names a surface still drawing while invisible
 * (a leaked overlay, a PiP window, a player that outlived its screen), which is
 * a bug worth having found.
 *
 * The foreground half is useful on its own: frames and slow frames per hour in
 * the app is the jank signal, from production, without a profiler attached.
 *
 * **Cost.** One callback per drawn frame — the same mechanism AndroidX JankStats
 * uses in shipping apps — doing three counter increments on a dedicated
 * background thread, never on main. At 120Hz that is on the order of ten
 * microseconds per second of rendering.
 *
 * It is nonetheless the only counter in the ledger that touches the rendering
 * path at all, and the only one whose cost could not be measured off-device, so
 * it has a runtime off switch in the shape `WorkerThreadPriorityGovernor` uses —
 * which also makes it A/B-able on a real device without a rebuild:
 * ```
 * adb shell settings put global amethyst_frame_metrics 0   # off
 * adb shell settings delete global amethyst_frame_metrics  # back on
 * ```
 */
class FrameMetricsCollector(
    private val accountant: ResourceUsageAccountant,
    private val isForeground: () -> Boolean,
) {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val listener =
        Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            // Read inside the callback: FrameMetrics is recycled after it returns.
            val totalNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val visibility = if (isForeground()) UsageKeys.FG else UsageKeys.BG
            accountant.add(UsageKeys.uiFrames(visibility), 1)
            val totalMs = totalNanos / 1_000_000
            if (totalMs > 0) accountant.add(UsageKeys.uiFrameMs(visibility), totalMs)
            if (totalMs >= UsageKeys.SLOW_FRAME_MS) {
                accountant.add(UsageKeys.uiSlowFrames(visibility), 1)
            }
        }

    /** Attaches to an activity's window. Safe to call more than once; the second call is a no-op. */
    fun attach(activity: Activity) {
        if (thread != null) return
        if (!isEnabled(activity)) {
            Log.i(TAG) { "Frame metrics disabled via $SETTING_KEY" }
            return
        }
        // Its own thread rather than the main looper: the whole point is to not
        // add work to the thread whose frames are being measured.
        val handlerThread = HandlerThread("frame-metrics").apply { start() }
        val handler = Handler(handlerThread.looper)
        thread = handlerThread
        this.handler = handler
        runCatching { activity.window.addOnFrameMetricsAvailableListener(listener, handler) }
            .onFailure { detach(activity) }
    }

    private fun isEnabled(activity: Activity): Boolean =
        runCatching {
            Settings.Global.getInt(activity.contentResolver, SETTING_KEY, 1) != 0
        }.getOrDefault(true)

    fun detach(activity: Activity) {
        runCatching { activity.window.removeOnFrameMetricsAvailableListener(listener) }
        thread?.quitSafely()
        thread = null
        handler = null
    }

    companion object {
        private const val TAG = "FrameMetrics"

        /** `Settings.Global` kill switch; any value of 0 disables the collector. */
        const val SETTING_KEY = "amethyst_frame_metrics"
    }
}
