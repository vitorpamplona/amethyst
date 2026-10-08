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
package com.vitorpamplona.amethyst.commons.wot.network

import com.vitorpamplona.amethyst.commons.util.moveOrCopy
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkCodec
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIds
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIndex
import com.vitorpamplona.quartz.utils.Log
import okio.FileSystem
import okio.Path

/**
 * The two files of one account's trust network, in [directory] (the account's own directory, so
 * deleting the account deletes them):
 *
 *  - [INDEX_FILE]: the index, read at startup.
 *  - [IDS_FILE]: event ids and tombstones, read only by syncs.
 *
 * Files are written to a temp file and moved into place, so a crash leaves the old file or the
 * new one, never half. A file that cannot be read (wrong version, truncated) reads as missing.
 * Blocking IO: call from `Dispatchers.IO`.
 */
class TrustNetworkStore(
    private val directory: Path,
    private val fileSystem: FileSystem = platformFileSystem,
) {
    private var legacyChecked = false

    fun readIndex(): TrustNetwork? {
        deleteLegacyFiles()
        return read(INDEX_FILE) { bytes -> TrustNetworkCodec.decodeIndex(bytes)?.let { (header, index) -> TrustNetwork(header, index) } }
    }

    fun readIds(): TrustNetworkIds? = read(IDS_FILE, TrustNetworkCodec::decodeIds)

    fun write(
        header: TrustNetworkHeader,
        index: TrustNetworkIndex,
        ids: TrustNetworkIds,
    ) {
        fileSystem.createDirectories(directory)
        // ids first: an index on disk always has matching ids, or a size mismatch that makes
        // the next sync download again.
        atomicWrite(directory / IDS_FILE, TrustNetworkCodec.encodeIds(ids))
        atomicWrite(directory / INDEX_FILE, TrustNetworkCodec.encodeIndex(header, index))
    }

    fun delete() {
        try {
            fileSystem.delete(directory / INDEX_FILE, mustExist = false)
            fileSystem.delete(directory / IDS_FILE, mustExist = false)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the trust network files", e)
        }
    }

    private fun <T> read(
        name: String,
        decode: (ByteArray) -> T?,
    ): T? {
        val file = directory / name
        return try {
            if (!fileSystem.exists(file)) return null
            decode(fileSystem.read(file) { readByteArray() })
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $name", e)
            null
        }
    }

    private fun atomicWrite(
        file: Path,
        bytes: ByteArray,
    ) {
        val temp = file.parent!! / "${file.name}.tmp"
        fileSystem.write(temp) { write(bytes) }
        fileSystem.moveOrCopy(temp, file)
    }

    /** Files of format version 1, which no build reads any more. */
    private fun deleteLegacyFiles() {
        if (legacyChecked) return
        legacyChecked = true
        LEGACY_FILES.forEach {
            try {
                fileSystem.delete(directory / it, mustExist = false)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete $it", e)
            }
        }
    }

    companion object {
        private const val TAG = "TrustNetworkStore"
        const val INDEX_FILE = "network-v2.bin"
        const val IDS_FILE = "network-ids-v2.bin"
        private val LEGACY_FILES = listOf("network-v1.bin", "network-ids-v1.bin")
    }
}
