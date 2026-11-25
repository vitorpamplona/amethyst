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

import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.marmot.MarmotIngestDedupStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * File-backed [MarmotIngestDedupStore] — one hex event id per
 * line under `<rootDir>/marmot_ingested.ids`.
 *
 * Was `AndroidIngestDedupStore`; the file and its format are unchanged.
 *
 * Deliberately NOT encrypted: the file holds public relay event ids and no key
 * material, and a marker lost to a decryption failure would silently cost a
 * re-decision rather than fail loudly.
 *
 * Capped and trimmed oldest-first. The worst case for a forgotten marker is
 * one wasted NIP-59 unwrap on the next sync, so bounding growth is worth more
 * than remembering every id forever.
 */
class FileIngestDedupStore(
    private val rootDir: Path,
    private val maxEntries: Int = 20_000,
    private val fileSystem: FileSystem = platformFileSystem,
) : MarmotIngestDedupStore {
    private val mutex = Mutex()

    private fun file(): Path = rootDir / "marmot_ingested.ids"

    override suspend fun mark(eventId: HexKey) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val target = file()
                try {
                    target.parent?.let { fileSystem.createDirectories(it) }
                    appendText(target, eventId + "\n")
                    if (length(target) > maxEntries.toLong() * 65L) {
                        val kept = readLines(target).filter { it.isNotBlank() }.takeLast(maxEntries / 2)
                        writeText(target, kept.joinToString("\n") + "\n")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "could not record ingest marker: ${e.message}", e)
                }
                Unit
            }
        }

    override suspend fun loadAll(): Set<HexKey> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    file()
                        .takeIf { fileSystem.exists(it) }
                        ?.let { readLines(it) }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        .orEmpty()
                } catch (e: Exception) {
                    Log.w(TAG, "could not read ingest markers: ${e.message}", e)
                    emptySet()
                }
            }
        }

    private fun ownEchoFile(): Path = rootDir / "marmot_own_echoes.ids"

    /**
     * The kind:445 ids this device sent, in their own file so they never crowd the Welcome and
     * created-locally markers out of [mark]'s capped one. Trimmed to the newest
     * [MarmotIngestDedupStore.OWN_ECHO_LIMIT] once it holds twice that.
     */
    override suspend fun markOwnEcho(eventId: HexKey) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val target = ownEchoFile()
                try {
                    target.parent?.let { fileSystem.createDirectories(it) }
                    appendText(target, eventId + "\n")
                    if (length(target) > 2L * MarmotIngestDedupStore.OWN_ECHO_LIMIT * 65L) {
                        val kept = readLines(target).filter { it.isNotBlank() }.takeLast(MarmotIngestDedupStore.OWN_ECHO_LIMIT)
                        writeText(target, kept.joinToString("\n") + "\n")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "could not record an own echo", e)
                }
                Unit
            }
        }

    override suspend fun loadOwnEchoes(): List<HexKey> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    ownEchoFile()
                        .takeIf { fileSystem.exists(it) }
                        ?.let { readLines(it) }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?.takeLast(MarmotIngestDedupStore.OWN_ECHO_LIMIT)
                        .orEmpty()
                } catch (e: Exception) {
                    Log.w(TAG, "could not read own echoes", e)
                    emptyList()
                }
            }
        }

    private fun appendText(
        file: Path,
        text: String,
    ) {
        fileSystem.appendingSink(file).buffer().use { it.writeUtf8(text) }
    }

    private fun writeText(
        file: Path,
        text: String,
    ) {
        fileSystem.write(file) { writeUtf8(text) }
    }

    /** `File.readLines()`, but with an empty last line after a trailing break; every caller drops blank lines. */
    private fun readLines(file: Path): List<String> = fileSystem.read(file) { readUtf8() }.lines()

    private fun length(file: Path): Long = fileSystem.metadataOrNull(file)?.size ?: 0L

    companion object {
        private const val TAG = "FileIngestDedupStore"
    }
}
