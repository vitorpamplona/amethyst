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
package com.vitorpamplona.amethyst.ui.components.util

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import com.vitorpamplona.amethyst.ui.components.util.DeviceUtils.screenOrientationIsLocked

object DeviceUtils {
    /**
     * Tries to determine if the device is
     * in landscape mode, by using the [android.util.DisplayMetrics] API.
     *
     * Credits: NewPipe devs
     */
    fun isLandscapeMetric(context: Context): Boolean = context.resources.displayMetrics.heightPixels < context.resources.displayMetrics.widthPixels

    /**
     * Checks if the device's orientation is set to locked.
     *
     * Credits: NewPipe devs
     */
    fun screenOrientationIsLocked(context: Context): Boolean {
        // 1: Screen orientation changes using accelerometer
        // 0: Screen orientation is locked
        // if the accelerometer sensor is missing completely, assume locked orientation
        return (
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0,
            ) == 0 ||
                !context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
        )
    }

    /**
     * Changes the device's orientation. This works even if the device's orientation
     * is set to locked.
     * Thus, to prevent unwanted behaviour,
     * it's use can be guarded by conditions such as [screenOrientationIsLocked].
     */
    fun changeDeviceOrientation(
        isInLandscape: Boolean,
        currentActivity: Activity,
    ) {
        val newOrientation =
            if (isInLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        currentActivity.requestedOrientation = newOrientation
    }

    /**
     * This method looks at the window in which the app resides,
     * and determines if it is large, while making sure not to be affected
     * by configuration changes(such as screen rotation),
     * as the device display metrics can be affected as well.
     *
     * It could be used as an approximation of the type of device(as is the case here),
     * though one ought to be careful about multi-window situations.
     */

    @Composable
    fun windowIsLarge(
        isInLandscapeMode: Boolean,
        windowSize: WindowSizeClass,
    ): Boolean =
        remember(windowSize) {
            if (isInLandscapeMode) {
                when (windowSize.windowHeightSizeClass) {
                    WindowHeightSizeClass.COMPACT -> false
                    WindowHeightSizeClass.MEDIUM -> true
                    WindowHeightSizeClass.EXPANDED -> true
                    else -> true
                }
            } else {
                when (windowSize.windowWidthSizeClass) {
                    WindowWidthSizeClass.EXPANDED -> true
                    WindowWidthSizeClass.MEDIUM -> true
                    WindowWidthSizeClass.COMPACT -> false
                    else -> true
                }
            }
        }
}
