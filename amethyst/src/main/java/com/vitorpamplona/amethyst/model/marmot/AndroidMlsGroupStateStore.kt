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
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [MlsGroupStateStore] using file-based encrypted storage.
 *
 * All MLS group state (containing private keys and epoch secrets) is encrypted
 * at rest using [KeyStoreEncryption] (AES/GCM backed by Android KeyStore).
 *
 * Storage layout:
 * ```
 * <rootDir>/mls_groups/<nostrGroupId>/state    — encrypted MlsGroupState
 * <rootDir>/mls_groups/<nostrGroupId>/retained  — encrypted retained epoch secrets
 * ```
 */
class AndroidMlsGroupStateStore(
    private val rootDir: File,
    private val encryption: KeyStoreEncryption = KeyStoreEncryption(),
) : MlsGroupStateStore {
    private fun groupDir(nostrGroupId: String): File {
        // Validate nostrGroupId is a hex string to prevent path traversal
        require(nostrGroupId.matches(HEX_PATTERN)) {
            "Invalid nostrGroupId: must be a hex string"
        }
        return File(rootDir, "mls_groups/$nostrGroupId")
    }

    companion object {
        private val HEX_PATTERN = Regex("^[0-9a-fA-F]+$")
    }

    private fun stateFile(nostrGroupId: String): File = File(groupDir(nostrGroupId), "state")

    private fun retainedFile(nostrGroupId: String): File = File(groupDir(nostrGroupId), "retained")

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val file = stateFile(nostrGroupId)
        file.parentFile?.mkdirs()
        atomicWrite(file, encryption.encrypt(state))
    }

    override suspend fun load(nostrGroupId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = stateFile(nostrGroupId)
            if (!file.exists()) return@withContext null
            encryption.decrypt(file.readBytes())
        }

    override suspend fun delete(nostrGroupId: String) {
        withContext(Dispatchers.IO) {
            val dir = groupDir(nostrGroupId)
            dir.deleteRecursively()
        }
    }

    override suspend fun listGroups(): List<String> =
        withContext(Dispatchers.IO) {
            val baseDir = File(rootDir, "mls_groups")
            if (!baseDir.exists()) return@withContext emptyList()
            baseDir
                .listFiles()
                ?.filter { it.isDirectory && File(it, "state").exists() }
                ?.map { it.name }
                ?: emptyList()
        }

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) = withContext(Dispatchers.IO) {
        val file = retainedFile(nostrGroupId)
        file.parentFile?.mkdirs()

        // Format: 4-byte count, then for each: 4-byte length + data
        val totalSize = 4 + retainedSecrets.sumOf { 4 + it.size }
        val buffer = ByteArray(totalSize)
        var offset = 0

        // Write count
        val count = retainedSecrets.size
        buffer[offset++] = (count shr 24).toByte()
        buffer[offset++] = (count shr 16).toByte()
        buffer[offset++] = (count shr 8).toByte()
        buffer[offset++] = count.toByte()

        // Write each entry
        for (secret in retainedSecrets) {
            val len = secret.size
            buffer[offset++] = (len shr 24).toByte()
            buffer[offset++] = (len shr 16).toByte()
            buffer[offset++] = (len shr 8).toByte()
            buffer[offset++] = len.toByte()
            secret.copyInto(buffer, offset)
            offset += len
        }

        atomicWrite(file, encryption.encrypt(buffer))
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> =
        withContext(Dispatchers.IO) {
            val file = retainedFile(nostrGroupId)
            if (!file.exists()) return@withContext emptyList()

            val buffer = encryption.decrypt(file.readBytes()) ?: return@withContext emptyList()
            if (buffer.size < 4) return@withContext emptyList()

            var offset = 0
            val count =
                ((buffer[offset++].toInt() and 0xFF) shl 24) or
                    ((buffer[offset++].toInt() and 0xFF) shl 16) or
                    ((buffer[offset++].toInt() and 0xFF) shl 8) or
                    (buffer[offset++].toInt() and 0xFF)

            val result = mutableListOf<ByteArray>()
            for (i in 0 until count) {
                if (offset + 4 > buffer.size) break
                val len =
                    ((buffer[offset++].toInt() and 0xFF) shl 24) or
                        ((buffer[offset++].toInt() and 0xFF) shl 16) or
                        ((buffer[offset++].toInt() and 0xFF) shl 8) or
                        (buffer[offset++].toInt() and 0xFF)
                if (offset + len > buffer.size) break
                result.add(buffer.copyOfRange(offset, offset + len))
                offset += len
            }
            result
        }

    /**
     * Write data atomically: write to a temp file first, then rename.
     * This avoids corrupted state if the app crashes mid-write.
     */
    private fun atomicWrite(
        target: File,
        data: ByteArray,
    ) {
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        tempFile.writeBytes(data)
        if (!tempFile.renameTo(target)) {
            // Fallback: if rename fails (e.g., cross-filesystem), copy and delete
            tempFile.copyTo(target, overwrite = true)
            if (!tempFile.delete()) {
                Log.w("AndroidMlsGroupStateStore") { "Failed to delete temp file after copy fallback: ${tempFile.absolutePath}" }
            }
        }
    }
}
