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

import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupSwitch
import com.vitorpamplona.amethyst.commons.util.createNewFileQuietly
import com.vitorpamplona.amethyst.commons.util.deleteQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.utils.Log
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * The per-account switch for Marmot device groups, on while its marker file exists:
 *
 * ```
 * <accountDir>/marmot_device_groups.enabled
 * ```
 *
 * Read once when the account's `MarmotManager` is built, because turning device groups on changes
 * what this device's KeyPackages advertise (`0x800e`/`0x800f`) and which stores it opens: a change
 * takes effect on the next launch.
 *
 * On by default for an account this install has not held before ([newAccount]: its directory did
 * not exist yet), which writes the marker once; the user turns it off from the devices screen. An
 * account an earlier version already held keeps what it had, off unless its user turned it on.
 */
class MarmotDeviceGroupSwitch(
    accountDir: Path,
    newAccount: Boolean = false,
    private val fileSystem: FileSystem = platformFileSystem,
) : DeviceGroupSwitch {
    private val marker = accountDir / FILE_NAME

    init {
        if (newAccount && !fileSystem.exists(marker)) {
            val dir = marker.parent
            if (dir != null && !fileSystem.exists(dir) && !createDirectories(dir)) Log.w(TAG) { "could not create $dir" }
            if (!fileSystem.createNewFileQuietly(marker) && !fileSystem.exists(marker)) Log.w(TAG) { "could not create $marker" }
        }
    }

    override fun isEnabled(): Boolean = fileSystem.exists(marker)

    override fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (!fileSystem.exists(marker) && !fileSystem.createNewFileQuietly(marker)) Log.w(TAG) { "could not create $marker" }
        } else if (fileSystem.exists(marker) && !fileSystem.deleteQuietly(marker)) {
            throw IllegalStateException("could not delete $marker")
        }
    }

    /** `File.mkdirs()`: false, not a throw, when the directory cannot be made. */
    private fun createDirectories(dir: Path): Boolean =
        try {
            fileSystem.createDirectories(dir)
            true
        } catch (e: IOException) {
            false
        }

    companion object {
        private const val TAG = "MarmotDeviceGroupSwitch"
        const val FILE_NAME = "marmot_device_groups.enabled"
    }
}
