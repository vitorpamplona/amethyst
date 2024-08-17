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
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

object DeviceUtils {
    fun getDeviceOrientation(): Int {
        val config = Resources.getSystem().configuration
        return config.orientation
    }

    /**
     * Alternative for determining if the device is
     * in landscape mode.
     * The [getDeviceOrientation] method could be used as well
     * to achieve the same purpose.
     * Credits: Newpipe devs
     *
     */
    fun isLandscapeMetric(context: Context): Boolean = context.resources.displayMetrics.heightPixels < context.resources.displayMetrics.widthPixels

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
