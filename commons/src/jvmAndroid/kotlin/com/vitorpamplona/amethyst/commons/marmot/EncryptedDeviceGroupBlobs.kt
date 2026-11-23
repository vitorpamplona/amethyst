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

import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupBlobs
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.amethyst.commons.util.moveOrCopy
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * The device-group records ([DeviceGroupBlobs]), encrypted at rest with [SecretEncryption] like
 * the group-state, KeyPackage and obligation stores beside it:
 *
 * ```
 * <rootDir>/marmot_device_group/<name>.bin
 * ```
 *
 * Written to a temp file and renamed, so a crash mid-write leaves the previous record readable:
 * losing the trust record would orphan this device's place in its device group.
 */
class EncryptedDeviceGroupBlobs(
    private val rootDir: File,
    private val encryption: SecretEncryption = SecretEncryption(),
) : DeviceGroupBlobs {
    private val mutex = Mutex()

    private fun file(name: String): File {
        require(name.all { it.isLetterOrDigit() || it == '_' }) { "bad record name $name" }
        return File(File(rootDir, DIR), "$name.bin")
    }

    override suspend fun load(name: String): ByteArray? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val target = file(name)
                if (!target.exists()) return@withLock null
                // Thrown, never null: a null reads as "no record", so marmot-kmp would start a fresh
                // one and the next save would overwrite the real record.
                try {
                    checkNotNull(encryption.decrypt(target.readBytes())) { "could not decrypt" }
                } catch (e: Exception) {
                    Log.e(TAG, "unreadable device-group record $name", e)
                    throw e
                }
            }
        }

    override suspend fun save(
        name: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = file(name)
            val parent = checkNotNull(target.parentFile)
            if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("could not create ${parent.absolutePath}")
            val tmp = File(parent, "${target.name}.tmp")
            tmp.writeBytes(encryption.encrypt(bytes))
            FileSystem.SYSTEM.moveOrCopy(tmp.toOkioPath(), target.toOkioPath())
        }
    }

    companion object {
        private const val TAG = "EncryptedDeviceGroupBlobs"
        const val DIR = "marmot_device_group"
    }
}
