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
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotPublishObligationStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [MarmotPublishObligationStore], encrypted at rest
 * with [KeyStoreEncryption] like the group-state and KeyPackage stores.
 *
 * ```
 * <rootDir>/marmot_obligations/<obligationId>.obligation
 * ```
 *
 * Publish-before-apply only means anything if the record outlives the process.
 * A commit is recorded, published, and only then applied; a crash inside that
 * window has to leave a trace, or the next launch mints a REPLACEMENT commit
 * for the same epoch and forks this device against every peer that accepted
 * the first one. Android kills apps mid-work routinely, so "in memory" here is
 * not a simplification — it is the common case.
 *
 * One file per obligation rather than one appended log: two groups can publish
 * concurrently and resolve out of order, so removing one record must not
 * rewrite another's.
 */
class AndroidPublishObligationStore(
    private val rootDir: File,
    private val encryption: KeyStoreEncryption = KeyStoreEncryption(),
) : MarmotPublishObligationStore {
    private val mutex = Mutex()

    private fun dir(): File = File(rootDir, "marmot_obligations")

    private fun file(obligationId: String) = File(dir(), "$obligationId.obligation")

    override suspend fun save(
        obligationId: HexKey,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = file(obligationId)
            try {
                target.parentFile?.mkdirs()
                val encrypted = encryption.encrypt(bytes)
                val tmp = File(target.parentFile, "${target.name}.tmp")
                tmp.writeBytes(encrypted)
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    if (!tmp.delete()) Log.w(TAG) { "could not delete temp file ${tmp.absolutePath}" }
                }
            } catch (e: Exception) {
                // Failing to record is worse than failing to publish: an
                // unrecorded commit that peers accept is a fork we cannot
                // detect. Surface it rather than continuing to the publish.
                Log.e(TAG, "save($obligationId) FAILED", e)
                throw e
            }
        }
    }

    override suspend fun delete(obligationId: HexKey) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val target = file(obligationId)
                if (target.exists() && !target.delete()) {
                    Log.w(TAG) { "could not delete resolved obligation ${target.absolutePath}" }
                }
                Unit
            }
        }

    override suspend fun loadAll(): List<ByteArray> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                dir()
                    .listFiles { f -> f.isFile && f.name.endsWith(".obligation") }
                    ?.sortedBy { it.name }
                    ?.mapNotNull { file ->
                        try {
                            encryption.decrypt(file.readBytes())
                        } catch (e: Exception) {
                            // One unreadable record must not cost us the
                            // others; the gate treats a missing obligation as
                            // "never confirmed", which is the safe direction.
                            Log.w(TAG, "unreadable obligation ${file.name}: ${e.message}", e)
                            null
                        }
                    }.orEmpty()
            }
        }

    companion object {
        private const val TAG = "AndroidPublishObligationStore"
    }
}
