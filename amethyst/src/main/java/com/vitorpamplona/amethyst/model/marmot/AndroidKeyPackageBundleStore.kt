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
package com.vitorpamplona.amethyst.model.marmot

import com.vitorpamplona.amethyst.model.preferences.KeyStoreEncryption
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [KeyPackageBundleStore] using file-based encrypted storage.
 *
 * Storage layout:
 * ```
 * <rootDir>/marmot_keypackages/state    — encrypted snapshot
 * ```
 *
 * The blob contains private key material — init keys, encryption keys,
 * signature keys — that the MLS engine needs to process Welcome events
 * received days or weeks after the corresponding KeyPackage was published.
 * It is encrypted at rest with [KeyStoreEncryption] (AES/GCM via Android
 * KeyStore), the same primitive used by [AndroidMlsGroupStateStore].
 */
class AndroidKeyPackageBundleStore(
    private val rootDir: File,
    private val encryption: KeyStoreEncryption = KeyStoreEncryption(),
) : KeyPackageBundleStore {
    private val mutex = Mutex()

    init {
        Log.d(TAG) {
            "Initialized AndroidKeyPackageBundleStore: rootDir=${rootDir.absolutePath}"
        }
    }

    private fun stateFile(): File = File(rootDir, "marmot_keypackages/state")

    override suspend fun save(snapshot: ByteArray) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = stateFile()
                try {
                    file.parentFile?.mkdirs()
                    val encrypted = encryption.encrypt(snapshot)
                    atomicWrite(file, encrypted)
                    Log.d(TAG) {
                        "save(): wrote ${encrypted.size} encrypted bytes (${snapshot.size} plain)"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "save() FAILED: ${e.message}", e)
                    throw e
                }
            }
        }

    override suspend fun load(): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = stateFile()
            if (!file.exists()) {
                Log.d(TAG) { "load(): no state file at ${file.absolutePath}" }
                return@withContext null
            }
            try {
                val encrypted = file.readBytes()
                val decrypted = encryption.decrypt(encrypted)
                Log.d(TAG) {
                    "load(): read ${encrypted.size} encrypted bytes → ${decrypted?.size ?: -1} plain"
                }
                decrypted
            } catch (e: Exception) {
                Log.e(TAG, "load() FAILED: ${e.message}", e)
                null
            }
        }

    override suspend fun delete() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = stateFile()
                if (file.exists()) {
                    if (!file.delete()) {
                        Log.w(TAG) { "delete(): failed to remove ${file.absolutePath}" }
                    }
                }
            }
        }
    }

    private fun atomicWrite(
        target: File,
        data: ByteArray,
    ) {
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        tempFile.writeBytes(data)
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            if (!tempFile.delete()) {
                Log.w(TAG) { "Failed to delete temp file after copy fallback: ${tempFile.absolutePath}" }
            }
        }
    }

    companion object {
        private const val TAG = "AndroidKeyPackageBundleStore"
    }
}
