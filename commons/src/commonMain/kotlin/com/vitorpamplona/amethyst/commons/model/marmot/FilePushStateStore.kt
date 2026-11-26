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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.util.deleteQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.renameQuietly
import com.vitorpamplona.amethyst.commons.util.sibling
import com.vitorpamplona.marmot.mip05PushNotifications.MarmotPushStateStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * File-backed [MarmotPushStateStore] — one JSON file per group
 * under `<rootDir>/marmot_push/<group id>.json`.
 *
 * Was `AndroidPushStateStore`; the files and their format are unchanged.
 *
 * Durability is the whole point of this class. A push tombstone is the only
 * lasting record that a token was revoked: any current member can re-emit a
 * revoked-but-still-signed record in a fresh kind `448` at any later epoch, and
 * a client that forgot the tombstone would accept it and start waking a device
 * its owner asked to be forgotten. So this survives restarts, and it is not
 * bounded by any wall clock, `owner_ts` or epoch count — a key is cleared only
 * by a strictly newer registration, or by its leaf leaving the group.
 *
 * Not encrypted, deliberately: the file holds tokens already encrypted to a
 * notification server this device cannot read, plus public routing. A failure
 * to decrypt would cost a tombstone, which is worse than the file being
 * readable by a process that has already broken out of the app sandbox.
 */
class FilePushStateStore(
    private val rootDir: Path,
    private val fileSystem: FileSystem = platformFileSystem,
) : MarmotPushStateStore {
    private val mutex = Mutex()

    private fun dir(): Path = rootDir / "marmot_push"

    /**
     * A group id is 32 hex characters from the protocol, but it reaches here as
     * a plain string, so anything that is not hex is refused rather than turned
     * into a path.
     */
    private fun file(nostrGroupId: HexKey): Path? {
        if (nostrGroupId.isEmpty() || !nostrGroupId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return dir() / "$nostrGroupId.json"
    }

    override suspend fun load(nostrGroupId: HexKey): String? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    file(nostrGroupId)?.takeIf { fileSystem.exists(it) }?.let { fileSystem.read(it) { readUtf8() } }
                } catch (e: Exception) {
                    Log.w(TAG, "could not read push state for $nostrGroupId: ${e.message}", e)
                    null
                }
            }
        }

    override suspend fun save(
        nostrGroupId: HexKey,
        state: String,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = file(nostrGroupId) ?: return@withLock
            try {
                target.parent?.let { fileSystem.createDirectories(it) }
                // Write-then-rename: a half-written state file would silently
                // drop tombstones, and a lost tombstone is exactly the failure
                // this store exists to prevent.
                val temp = target.sibling("${target.name}.tmp")
                fileSystem.write(temp) { writeUtf8(state) }
                if (!fileSystem.renameQuietly(temp, target)) {
                    fileSystem.write(target) { writeUtf8(state) }
                    if (!fileSystem.deleteQuietly(temp)) {
                        Log.w(TAG) { "could not remove temp push state file ${temp.name}" }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not persist push state for $nostrGroupId: ${e.message}", e)
            }
        }
    }

    override suspend fun clear(nostrGroupId: HexKey) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val target = file(nostrGroupId)?.takeIf { fileSystem.exists(it) }
                    if (target != null && !fileSystem.deleteQuietly(target)) {
                        Log.w(TAG) { "could not delete push state for $nostrGroupId" }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "could not clear push state for $nostrGroupId: ${e.message}", e)
                }
                Unit
            }
        }

    companion object {
        private const val TAG = "FilePushStateStore"
    }
}
