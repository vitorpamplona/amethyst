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
package com.vitorpamplona.quartz.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo

object DeviceUtils {
    fun getDeviceOrientation(context: Context): Int {
        val deviceConfiguration = context.resources.configuration
        return deviceConfiguration.orientation
    }

    /**
     * Alternative for determining if the device is
     * in landscape mode.
     * The [getDeviceOrientation] method could be used as well
     * to achieve the same purpose.
     * Credits: Newpipe devs
     *
     */
    fun isLandscape(context: Context): Boolean = context.resources.displayMetrics.heightPixels < context.resources.displayMetrics.widthPixels

    fun changeDeviceOrientation(
        isInLandscape: Boolean,
        context: Activity,
    ) {
        val newOrientation =
            if (isInLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        context.requestedOrientation = newOrientation
    }
}
