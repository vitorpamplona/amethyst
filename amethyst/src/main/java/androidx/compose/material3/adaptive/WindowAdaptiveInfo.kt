/**
 * Copyright (c) 2024 Vitor Pamplona
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
package androidx.compose.material3.adaptive

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.flow.map

@Composable
fun currentWindowAdaptiveInfo(): WindowAdaptiveInfo {
    val windowSize =
        with(LocalDensity.current) {
            currentWindowSize().toSize().toDpSize()
        }
    return WindowAdaptiveInfo(
        WindowSizeClass.compute(windowSize.width.value, windowSize.height.value),
        calculatePosture(collectFoldingFeaturesAsState().value),
    )
}

/**
 * Returns and automatically update the current window size from [WindowMetricsCalculator].
 *
 * @return an [IntSize] that represents the current window size.
 */
@Composable
fun currentWindowSize(): IntSize {
    // Observe view configuration changes and recalculate the size class on each change. We can't
    // use Activity#onConfigurationChanged as this will sometimes fail to be called on different
    // API levels, hence why this function needs to be @Composable so we can observe the
    // ComposeView's configuration changes.
    LocalConfiguration.current
    val windowBounds =
        WindowMetricsCalculator
            .getOrCreate()
            .computeCurrentWindowMetrics(LocalContext.current)
            .bounds
    return IntSize(windowBounds.width(), windowBounds.height())
}

/**
 * Collects the current window folding features from [WindowInfoTracker] in to a [State].
 *
 * @return a [State] of a [FoldingFeature] list.
 */
@Composable
fun collectFoldingFeaturesAsState(): State<List<FoldingFeature>> {
    val context = LocalContext.current
    return remember(context) {
        if (context is Activity) {
            // TODO(b/284347941) remove the instance check after the test bug is fixed.
            WindowInfoTracker
                .getOrCreate(context)
                .windowLayoutInfo(context)
        } else {
            WindowInfoTracker
                .getOrCreate(context)
                .windowLayoutInfo(context)
        }.map { it.displayFeatures.filterIsInstance<FoldingFeature>() }
    }.collectAsState(emptyList())
}

/**
 * This class collects window info that affects adaptation decisions. An adaptive layout is supposed
 * to use the info from this class to decide how the layout is supposed to be adapted.
 *
 * @constructor create an instance of [WindowAdaptiveInfo]
 * @param windowSizeClass [WindowSizeClass] of the current window.
 * @param windowPosture [Posture] of the current window.
 */
@Immutable
class WindowAdaptiveInfo(
    val windowSizeClass: WindowSizeClass,
    val windowPosture: Posture,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowAdaptiveInfo) return false
        if (windowSizeClass != other.windowSizeClass) return false
        if (windowPosture != other.windowPosture) return false
        return true
    }

    override fun hashCode(): Int {
        var result = windowSizeClass.hashCode()
        result = 31 * result + windowPosture.hashCode()
        return result
    }

    override fun toString(): String = "WindowAdaptiveInfo(windowSizeClass=$windowSizeClass, windowPosture=$windowPosture)"
}
