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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * The per-account switch for Marmot device groups, OFF unless its marker file exists:
 *
 * ```
 * <accountDir>/marmot_device_groups.enabled
 * ```
 *
 * Read once when the account's `MarmotManager` is built, because turning device groups on changes
 * what this device's KeyPackages advertise (`0x800e`/`0x800f`) and which stores it opens: a change
 * takes effect on the next launch. It stays off by default until history deliveries (kind 473)
 * land, since a newly linked device would otherwise get its chats without their past messages.
 */
class MarmotDeviceGroupSwitch(
    accountDir: File,
) {
    private val marker = File(accountDir, FILE_NAME)

    fun isEnabled(): Boolean = marker.exists()

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (!marker.exists() && !marker.createNewFile()) Log.w(TAG) { "could not create ${marker.absolutePath}" }
        } else if (marker.exists() && !marker.delete()) {
            throw IllegalStateException("could not delete ${marker.absolutePath}")
        }
    }

    companion object {
        private const val TAG = "MarmotDeviceGroupSwitch"
        const val FILE_NAME = "marmot_device_groups.enabled"
    }
}
