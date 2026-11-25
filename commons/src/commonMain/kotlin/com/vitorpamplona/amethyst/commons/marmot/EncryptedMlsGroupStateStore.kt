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

import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.amethyst.commons.util.deleteQuietly
import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.renameQuietly
import com.vitorpamplona.amethyst.commons.util.sibling
import com.vitorpamplona.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.use

/**
 * File-backed [MlsGroupStateStore], encrypted at rest.
 *
 * All MLS group state (containing private keys and epoch secrets) is encrypted
 * at rest using [SecretEncryption] (AES-256-GCM, keyed by the platform's keystore).
 *
 * Storage layout:
 * ```
 * <rootDir>/mls_groups/<nostrGroupId>/state     — encrypted MlsGroupState
 * <rootDir>/mls_groups/<nostrGroupId>/retained  — encrypted retained epoch secrets
 * <rootDir>/mls_groups/<nostrGroupId>/ratchet   — encrypted OwnSenderRatchet
 * ```
 */
class EncryptedMlsGroupStateStore(
    private val rootDir: Path,
    private val encryption: SecretEncryption = SecretEncryption(),
    private val fileSystem: FileSystem = platformFileSystem,
) : MlsGroupStateStore {
    init {
        Log.d(TAG) {
            "Initialized EncryptedMlsGroupStateStore: rootDir=$rootDir, " +
                "mls_groups exists=${fileSystem.exists(rootDir / "mls_groups")}"
        }
    }

    private fun groupDir(nostrGroupId: String): Path {
        // Validate nostrGroupId is a hex string to prevent path traversal
        require(nostrGroupId.matches(HEX_PATTERN)) {
            "Invalid nostrGroupId: must be a hex string"
        }
        return rootDir / "mls_groups" / nostrGroupId
    }

    companion object {
        private const val TAG = "EncryptedMlsGroupStateStore"
        private val HEX_PATTERN = Regex("^[0-9a-fA-F]+$")
    }

    private fun stateFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "state"

    private fun retainedFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "retained"

    private fun ratchetFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "ratchet"

    private fun convergenceFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "convergence"

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val file = stateFile(nostrGroupId)
        Log.d(TAG) { "save($nostrGroupId): ${state.size} bytes → $file" }
        try {
            file.parent?.let { fileSystem.createDirectories(it) }
            val encrypted = encryption.encrypt(state)
            atomicWrite(file, encrypted)
            Log.d(TAG) {
                "save($nostrGroupId): wrote ${encrypted.size} encrypted bytes, " +
                    "file exists=${fileSystem.exists(file)}, file size=${fileSystem.metadataOrNull(file)?.size ?: -1}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "save($nostrGroupId) FAILED: ${e.message}", e)
            throw e
        }
    }

    override suspend fun load(nostrGroupId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = stateFile(nostrGroupId)
            if (!fileSystem.exists(file)) {
                Log.d(TAG) { "load($nostrGroupId): file does not exist at $file" }
                return@withContext null
            }
            try {
                val bytes = readBytes(file)
                Log.d(TAG) { "load($nostrGroupId): read ${bytes.size} encrypted bytes from $file" }
                val decrypted = encryption.decrypt(bytes)
                Log.d(TAG) { "load($nostrGroupId): decrypted to ${decrypted?.size ?: -1} bytes" }
                decrypted
            } catch (e: Exception) {
                Log.e(TAG, "load($nostrGroupId) FAILED: ${e.message}", e)
                throw e
            }
        }

    override suspend fun delete(nostrGroupId: String) {
        withContext(Dispatchers.IO) {
            val dir = groupDir(nostrGroupId)
            Log.w(TAG) { "delete($nostrGroupId): deleting $dir" }
            fileSystem.deleteRecursivelyQuietly(dir)
        }
    }

    override suspend fun listGroups(): List<String> =
        withContext(Dispatchers.IO) {
            val baseDir = rootDir / "mls_groups"
            if (!fileSystem.exists(baseDir)) {
                Log.d(TAG) { "listGroups(): base dir does not exist at $baseDir" }
                return@withContext emptyList()
            }
            val allEntries = fileSystem.listOrNull(baseDir) ?: emptyList()
            val result =
                allEntries
                    .filter { fileSystem.metadataOrNull(it)?.isDirectory == true && fileSystem.exists(it / "state") }
                    .map { it.name }
            Log.d(TAG) {
                "listGroups(): $baseDir has ${allEntries.size} entries, " +
                    "${result.size} valid groups: $result"
            }
            result
        }

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) = withContext(Dispatchers.IO) {
        val file = retainedFile(nostrGroupId)
        file.parent?.let { fileSystem.createDirectories(it) }

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

    /**
     * The per-send ratchet position, written on its own so a message does not
     * re-encrypt the whole group state to record ~72 bytes.
     *
     * Written atomically, through a temp file and a rename. An in-place write
     * looked adequate — the record is small and written whole — but the failure
     * it allows is the one this record exists to prevent. A torn record is
     * discarded on load, which falls back to the position in the full state:
     * that is the position as of the last COMMIT, behind by every send since.
     * The next send would then re-emit every generation in between, reusing
     * AEAD key+nonce pairs. With a rename, a crash leaves the previous complete
     * record instead, which is at worst one send behind — and that send's
     * ciphertext was never published, because publishing waits for this write.
     */
    override suspend fun saveSenderRatchet(
        nostrGroupId: String,
        state: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val file = ratchetFile(nostrGroupId)
            try {
                file.parent?.let { fileSystem.createDirectories(it) }
                val encrypted = encryption.encrypt(state)
                val tempFile = file.sibling("${file.name}.tmp")
                fileSystem.openReadWrite(tempFile).use { handle ->
                    // Truncated first, as opening a FileOutputStream did: a stale temp
                    // longer than this record must not leave its tail behind.
                    handle.resize(0L)
                    handle.write(0L, encrypted, 0, encrypted.size)
                    // An fsync on the JVM and Android; on iOS okio only flushes to the
                    // kernel, which outlives the process but not a power loss.
                    handle.flush()
                }
                if (!fileSystem.renameQuietly(tempFile, file)) {
                    fileSystem.copy(tempFile, file)
                    if (!fileSystem.deleteQuietly(tempFile)) {
                        Log.w(TAG) { "Failed to delete the temp ratchet file: $tempFile" }
                    }
                }
                true
            } catch (e: Exception) {
                // Returning false sends the caller to a full state write, which
                // is slower but carries the same guarantee. Swallowing this and
                // returning true would drop the position on the floor.
                Log.w(TAG, "saveSenderRatchet($nostrGroupId) FAILED, falling back to a full state write: ${e.message}", e)
                false
            }
        }

    override suspend fun loadSenderRatchet(nostrGroupId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = ratchetFile(nostrGroupId)
            if (!fileSystem.exists(file)) return@withContext null
            try {
                encryption.decrypt(readBytes(file))
            } catch (e: Exception) {
                // Serious: the fallback is the full state's position, which is
                // BEHIND, and a behind position re-emits used generations. The
                // atomic write above is what should make this unreachable, so
                // if it ever fires the group's key material is suspect.
                Log.e(TAG, "loadSenderRatchet($nostrGroupId) unreadable — the ratchet may rewind: ${e.message}", e)
                null
            }
        }

    /**
     * The convergence window: the retained states of the last few epochs, so a fork whose other
     * half arrives after a restart is still resolved. Private state, encrypted like [save]; the
     * group directory [delete] removes holds it.
     */
    override suspend fun saveConvergenceWindow(
        nostrGroupId: String,
        window: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = convergenceFile(nostrGroupId)
                file.parent?.let { fileSystem.createDirectories(it) }
                atomicWrite(file, encryption.encrypt(window))
                true
            } catch (e: Exception) {
                Log.w(TAG, "saveConvergenceWindow($nostrGroupId) failed", e)
                false
            }
        }

    override suspend fun loadConvergenceWindow(nostrGroupId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = convergenceFile(nostrGroupId)
            if (!fileSystem.exists(file)) return@withContext null
            try {
                encryption.decrypt(readBytes(file))
            } catch (e: Exception) {
                // Losing it costs only a fork's recovery across this restart.
                Log.w(TAG, "loadConvergenceWindow($nostrGroupId) unreadable", e)
                null
            }
        }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> =
        withContext(Dispatchers.IO) {
            val file = retainedFile(nostrGroupId)
            if (!fileSystem.exists(file)) return@withContext emptyList()

            val buffer = encryption.decrypt(readBytes(file)) ?: return@withContext emptyList()
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
        target: Path,
        data: ByteArray,
    ) {
        val tempFile = target.sibling("${target.name}.tmp")
        fileSystem.write(tempFile) { write(data) }
        if (!fileSystem.renameQuietly(tempFile, target)) {
            // Fallback: if rename fails (e.g., cross-filesystem), copy and delete
            fileSystem.copy(tempFile, target)
            if (!fileSystem.deleteQuietly(tempFile)) {
                Log.w("EncryptedMlsGroupStateStore") { "Failed to delete temp file after copy fallback: $tempFile" }
            }
        }
    }

    private fun readBytes(file: Path): ByteArray = fileSystem.read(file) { readByteArray() }
}
