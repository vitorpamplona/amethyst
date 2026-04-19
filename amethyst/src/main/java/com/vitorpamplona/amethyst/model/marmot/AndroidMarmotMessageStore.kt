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
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [MarmotMessageStore] using file-based encrypted storage.
 *
 * Stored alongside the [AndroidMlsGroupStateStore] data:
 * ```
 * <rootDir>/mls_groups/<nostrGroupId>/messages    — encrypted message log
 * ```
 *
 * The on-disk format (after decryption) is a sequence of length-prefixed
 * UTF-8 entries:
 * ```
 * uint32 count
 * for each entry:
 *   uint32 length
 *   byte[length] utf8
 * ```
 *
 * The whole blob is rewritten on each append (via atomic rename) — this
 * keeps encryption simple (one GCM nonce per write) and is acceptable for
 * conversation-scale histories.
 */
class AndroidMarmotMessageStore(
    private val rootDir: File,
    private val encryption: KeyStoreEncryption = KeyStoreEncryption(),
) : MarmotMessageStore {
    private val writeMutex = Mutex()

    init {
        Log.d(TAG) {
            "Initialized AndroidMarmotMessageStore: rootDir=${rootDir.absolutePath}"
        }
    }

    private fun groupDir(nostrGroupId: String): File {
        require(nostrGroupId.matches(HEX_PATTERN)) {
            "Invalid nostrGroupId: must be a hex string"
        }
        return File(rootDir, "mls_groups/$nostrGroupId")
    }

    private fun messagesFile(nostrGroupId: String): File = File(groupDir(nostrGroupId), "messages")

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val existing = readAll(nostrGroupId).toMutableList()
                existing.add(innerEventJson)
                writeAll(nostrGroupId, existing)
                Log.d(TAG) {
                    "appendMessage($nostrGroupId): now ${existing.size} message(s) persisted"
                }
            } catch (e: Exception) {
                Log.e(TAG, "appendMessage($nostrGroupId) FAILED: ${e.message}", e)
                throw e
            }
        }
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val messages = readAll(nostrGroupId)
                Log.d(TAG) {
                    "loadMessages($nostrGroupId): loaded ${messages.size} message(s)"
                }
                messages
            } catch (e: Exception) {
                Log.e(TAG, "loadMessages($nostrGroupId) FAILED: ${e.message}", e)
                emptyList()
            }
        }

    override suspend fun delete(nostrGroupId: String) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val file = messagesFile(nostrGroupId)
                if (file.exists()) {
                    if (!file.delete()) {
                        Log.w(TAG) { "delete($nostrGroupId): failed to remove ${file.absolutePath}" }
                    }
                }
            }
        }
    }

    private fun readAll(nostrGroupId: String): List<String> {
        val file = messagesFile(nostrGroupId)
        if (!file.exists()) return emptyList()
        val encrypted = file.readBytes()
        val plain = encryption.decrypt(encrypted) ?: return emptyList()
        if (plain.size < 4) return emptyList()

        var offset = 0
        val count =
            ((plain[offset++].toInt() and 0xFF) shl 24) or
                ((plain[offset++].toInt() and 0xFF) shl 16) or
                ((plain[offset++].toInt() and 0xFF) shl 8) or
                (plain[offset++].toInt() and 0xFF)

        val result = ArrayList<String>(count.coerceAtMost(MAX_MESSAGES))
        for (i in 0 until count) {
            if (offset + 4 > plain.size) break
            val len =
                ((plain[offset++].toInt() and 0xFF) shl 24) or
                    ((plain[offset++].toInt() and 0xFF) shl 16) or
                    ((plain[offset++].toInt() and 0xFF) shl 8) or
                    (plain[offset++].toInt() and 0xFF)
            if (len < 0 || offset + len > plain.size) break
            result.add(plain.copyOfRange(offset, offset + len).decodeToString())
            offset += len
        }
        return result
    }

    private fun writeAll(
        nostrGroupId: String,
        messages: List<String>,
    ) {
        val file = messagesFile(nostrGroupId)
        file.parentFile?.mkdirs()

        val encodedEntries = messages.map { it.encodeToByteArray() }
        val totalSize = 4 + encodedEntries.sumOf { 4 + it.size }
        val buffer = ByteArray(totalSize)
        var offset = 0

        val count = encodedEntries.size
        buffer[offset++] = (count shr 24).toByte()
        buffer[offset++] = (count shr 16).toByte()
        buffer[offset++] = (count shr 8).toByte()
        buffer[offset++] = count.toByte()

        for (entry in encodedEntries) {
            val len = entry.size
            buffer[offset++] = (len shr 24).toByte()
            buffer[offset++] = (len shr 16).toByte()
            buffer[offset++] = (len shr 8).toByte()
            buffer[offset++] = len.toByte()
            entry.copyInto(buffer, offset)
            offset += len
        }

        val encrypted = encryption.encrypt(buffer)
        atomicWrite(file, encrypted)
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
        private const val TAG = "AndroidMarmotMessageStore"
        private const val MAX_MESSAGES = 1_000_000
        private val HEX_PATTERN = Regex("^[0-9a-fA-F]+$")
    }
}
