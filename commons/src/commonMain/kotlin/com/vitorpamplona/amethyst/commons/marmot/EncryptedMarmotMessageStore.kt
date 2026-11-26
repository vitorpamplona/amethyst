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
import com.vitorpamplona.amethyst.commons.storage.EncryptedAppendLog
import com.vitorpamplona.amethyst.commons.util.deleteQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.marmot.groups.MarmotMessageStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * File-backed [MarmotMessageStore], encrypted at rest.
 *
 * Stored alongside the [EncryptedMlsGroupStateStore] data:
 * ```
 * <rootDir>/mls_groups/<nostrGroupId>/messages    — encrypted message log
 * ```
 *
 * Every file here is an [EncryptedAppendLog], which owns the on-disk format and
 * the migration from the original whole-blob one. Recording a message appends a
 * small encrypted segment instead of rewriting the conversation, which is what
 * keeps the cost of a send flat as the history grows.
 */
class EncryptedMarmotMessageStore(
    private val rootDir: Path,
    private val encryption: SecretEncryption = SecretEncryption(),
    private val fileSystem: FileSystem = platformFileSystem,
) : MarmotMessageStore {
    private val logMutex = Mutex()

    init {
        Log.d(TAG) {
            "Initialized EncryptedMarmotMessageStore: rootDir=$rootDir"
        }
    }

    private fun groupDir(nostrGroupId: String): Path {
        require(nostrGroupId.matches(HEX_PATTERN)) {
            "Invalid nostrGroupId: must be a hex string"
        }
        return rootDir / "mls_groups" / nostrGroupId
    }

    private fun messagesFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "messages"

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                val file = messagesFile(nostrGroupId)
                if (log.contains(file, innerEventJson)) {
                    Log.d(TAG) { "appendMessage($nostrGroupId): duplicate entry skipped" }
                    return@withLock
                }
                log.append(file, innerEventJson)
                Event.fromJsonOrNull(innerEventJson)?.createdAt?.let { recordNewest(nostrGroupId, it) }
                Log.d(TAG) {
                    "appendMessage($nostrGroupId): now ${log.readAll(file).size} message(s) persisted"
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
                val messages = logMutex.withLock { readAll(nostrGroupId) }
                Log.d(TAG) {
                    "loadMessages($nostrGroupId): loaded ${messages.size} message(s)"
                }
                messages
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadMessages($nostrGroupId) FAILED: ${e.message}", e)
                emptyList()
            }
        }

    /**
     * The newest inner `created_at` the group's log holds, read on every start to resume the
     * group's subscription. Kept in its own small encrypted file as messages are appended, so a
     * start decrypts no message log. A log written before that file existed is read once, and the
     * answer stored.
     */
    override suspend fun newestMessageCreatedAt(nostrGroupId: String): Long? =
        withContext(Dispatchers.IO) {
            try {
                logMutex.withLock { newestFor(nostrGroupId) }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "newestMessageCreatedAt($nostrGroupId) FAILED", e)
                null
            }
        }

    private fun newestFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "newest"

    // Guarded by [logMutex]: each group's newest created_at once read, null for a group with none.
    private val newest = mutableMapOf<String, Long?>()

    private fun newestFor(nostrGroupId: String): Long? {
        if (nostrGroupId in newest) return newest[nostrGroupId]
        val stored = readAllFrom(newestFile(nostrGroupId)).firstOrNull()?.trim()?.toLongOrNull()
        val value =
            stored ?: readAll(nostrGroupId)
                .maxOfOrNull { json -> Event.fromJsonOrNull(json)?.createdAt ?: 0L }
                ?.also { writeAllTo(newestFile(nostrGroupId), listOf(it.toString())) }
        newest[nostrGroupId] = value
        return value
    }

    /** Guarded by [logMutex]. */
    private fun recordNewest(
        nostrGroupId: String,
        createdAt: Long,
    ) {
        val current = newestFor(nostrGroupId)
        if (current != null && current >= createdAt) return
        writeAllTo(newestFile(nostrGroupId), listOf(createdAt.toString()))
        newest[nostrGroupId] = createdAt
    }

    override suspend fun delete(nostrGroupId: String) {
        withContext(Dispatchers.IO) {
            logMutex.withLock {
                expiries.remove(nostrGroupId)
                newest.remove(nostrGroupId)
                for (file in listOf(newestFile(nostrGroupId), messagesFile(nostrGroupId), epochsFile(nostrGroupId), snapshotFile(nostrGroupId), expiriesFile(nostrGroupId), epochRetentionsFile(nostrGroupId), mediaSecretsFile(nostrGroupId), epochAdminsFile(nostrGroupId), verdictsFile(nostrGroupId))) {
                    log.forget(file)
                    if (fileSystem.exists(file) && !fileSystem.deleteQuietly(file)) {
                        Log.w(TAG) { "delete($nostrGroupId): failed to remove $file" }
                    }
                }
            }
        }
    }

    private fun epochsFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "epochs"

    /**
     * Which MLS epoch delivered an inner event. Agent text streams bind the
     * epoch into their record key context, so a receiver needs the epoch that
     * carried the stream's kind:1200 anchor rather than the group's current
     * one — a commit landing in between would otherwise derive a different key
     * and render nothing.
     *
     * Stored through the same encrypted codec as the messages: the ids are as
     * sensitive as the payloads they point at.
     */
    override suspend fun recordEpoch(
        nostrGroupId: String,
        innerEventId: String,
        epoch: Long,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                val line = "$innerEventId $epoch"
                val existing = readAllFrom(epochsFile(nostrGroupId)).toMutableList()
                if (line in existing) return@withLock
                existing.add(line)
                writeAllTo(epochsFile(nostrGroupId), existing)
            } catch (e: Exception) {
                Log.e(TAG, "recordEpoch($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    override suspend fun loadEpochs(nostrGroupId: String): Map<String, Long> =
        withContext(Dispatchers.IO) {
            try {
                logMutex
                    .withLock { readAllFrom(epochsFile(nostrGroupId)) }
                    .mapNotNull { line ->
                        val parts = line.trim().split(' ')
                        if (parts.size != 2) return@mapNotNull null
                        val epoch = parts[1].toLongOrNull() ?: return@mapNotNull null
                        parts[0] to epoch
                    }.toMap()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadEpochs($nostrGroupId) FAILED: ${e.message}", e)
                emptyMap()
            }
        }

    private fun expiriesFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "expiries"

    /**
     * When a message stops being displayable, for a group that expires them.
     *
     * Encrypted like the messages: an expiry names an inner event id and says
     * roughly when it was sent, which is conversation metadata.
     *
     * First write wins. The expiry is pinned to the retention of the message's
     * own source epoch, so re-persisting the same message after a restart —
     * which happens, because the ratchet rewinds and relays replay — must not
     * re-time it under whatever the setting has since become.
     *
     * An append, not a rewrite: this runs for every message a disappearing group persists, and
     * rewriting re-encrypted the whole log each time. The bytes are the same log format either way.
     */
    override suspend fun recordExpiry(
        nostrGroupId: String,
        innerEventId: String,
        expiresAtSecs: Long,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                val current = expiriesFor(nostrGroupId)
                if (current.containsKey(innerEventId)) return@withLock
                log.append(expiriesFile(nostrGroupId), "$innerEventId $expiresAtSecs")
                expiries[nostrGroupId] = current.put(innerEventId, expiresAtSecs)
            } catch (e: Exception) {
                Log.e(TAG, "recordExpiry($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    /**
     * The group's expiries, as an immutable snapshot of the in-memory copy: the expiry prune runs
     * on every message received, and re-parsing the whole log for each one made a catch-up
     * quadratic.
     */
    override suspend fun loadExpiries(nostrGroupId: String): Map<String, Long> =
        withContext(Dispatchers.IO) {
            try {
                logMutex.withLock { expiriesFor(nostrGroupId) }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadExpiries($nostrGroupId) FAILED: ${e.message}", e)
                emptyMap()
            }
        }

    /**
     * Delete messages and forget their expiries, rewriting both logs.
     *
     * A rewrite rather than a tombstone: the point of a disappearing message
     * is that the plaintext is gone from disk, and this store holds the only
     * copy — the ratchet moved past the ciphertext it came from long ago.
     */
    override suspend fun removeMessages(
        nostrGroupId: String,
        innerEventIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        if (innerEventIds.isEmpty()) return@withContext
        logMutex.withLock {
            try {
                val kept =
                    readAll(nostrGroupId).filter { json ->
                        val id = Event.fromJsonOrNull(json)?.id
                        id == null || id !in innerEventIds
                    }
                writeAll(nostrGroupId, kept)

                val keptExpiries =
                    readAllFrom(expiriesFile(nostrGroupId)).filter { it.substringBefore(' ') !in innerEventIds }
                writeAllTo(expiriesFile(nostrGroupId), keptExpiries)
                expiries[nostrGroupId] = parseExpiries(keptExpiries)
            } catch (e: Exception) {
                Log.e(TAG, "removeMessages($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    private fun mediaSecretsFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "media_secrets"

    /**
     * Each epoch's encrypted-media key, captured while the epoch was still
     * held. An attachment opens only under the key of its own message's epoch,
     * and the MLS state drops an epoch's exporter secret a few commits later.
     *
     * These are secrets, so they go through the same encrypted codec as the
     * messages. First write wins per epoch: an epoch's key never changes.
     */
    override suspend fun recordMediaSecret(
        nostrGroupId: String,
        epoch: Long,
        mediaSecret: ByteArray,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                val existing = readAllFrom(mediaSecretsFile(nostrGroupId))
                if (existing.any { it.substringBefore(' ') == epoch.toString() }) return@withLock
                // Appended, not rewritten: the log is append-only, and a rewrite re-encrypts every record.
                log.append(mediaSecretsFile(nostrGroupId), "$epoch ${mediaSecret.toHexKey()}")
            } catch (e: Exception) {
                Log.e(TAG, "recordMediaSecret($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    override suspend fun loadMediaSecrets(nostrGroupId: String): Map<Long, ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                logMutex
                    .withLock { readAllFrom(mediaSecretsFile(nostrGroupId)) }
                    .mapNotNull { line ->
                        val parts = line.trim().split(' ')
                        if (parts.size != 2) return@mapNotNull null
                        val epoch = parts[0].toLongOrNull() ?: return@mapNotNull null
                        val secret = runCatching { parts[1].hexToByteArray() }.getOrNull() ?: return@mapNotNull null
                        epoch to secret
                    }.toMap()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadMediaSecrets($nostrGroupId) FAILED: ${e.message}", e)
                emptyMap()
            }
        }

    private fun epochAdminsFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "epoch_admins"

    private fun verdictsFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "moderation_verdicts"

    /**
     * The active admins of each epoch, kept because an admin deletion is
     * authorized by its source epoch's admins, not today's. First write wins:
     * an epoch's admin set is fixed the moment it exists.
     */
    override suspend fun recordEpochAdmins(
        nostrGroupId: String,
        epoch: Long,
        admins: Set<String>,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                val existing = readAllFrom(epochAdminsFile(nostrGroupId))
                if (existing.any { it.substringBefore(' ') == epoch.toString() }) return@withLock
                // Appended, not rewritten: the log is append-only, and a rewrite re-encrypts every record.
                log.append(epochAdminsFile(nostrGroupId), "$epoch ${admins.joinToString(",")}")
            } catch (e: Exception) {
                Log.e(TAG, "recordEpochAdmins($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    override suspend fun loadEpochAdmins(nostrGroupId: String): Map<Long, Set<String>> =
        withContext(Dispatchers.IO) {
            try {
                logMutex
                    .withLock { readAllFrom(epochAdminsFile(nostrGroupId)) }
                    .mapNotNull { line ->
                        val epoch = line.substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
                        val admins =
                            line
                                .substringAfter(' ', "")
                                .split(',')
                                .filter { it.isNotEmpty() }
                                .toSet()
                        epoch to admins
                    }.toMap()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadEpochAdmins($nostrGroupId) FAILED: ${e.message}", e)
                emptyMap()
            }
        }

    /** Whether each admin deletion was authorized, decided once. First write wins. */
    override suspend fun recordModerationVerdict(
        nostrGroupId: String,
        innerEventId: String,
        authorized: Boolean,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                val existing = readAllFrom(verdictsFile(nostrGroupId))
                if (existing.any { it.substringBefore(' ') == innerEventId }) return@withLock
                // Appended, not rewritten: the log is append-only, and a rewrite re-encrypts every record.
                log.append(verdictsFile(nostrGroupId), "$innerEventId ${if (authorized) 1 else 0}")
            } catch (e: Exception) {
                Log.e(TAG, "recordModerationVerdict($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    override suspend fun loadModerationVerdicts(nostrGroupId: String): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                logMutex
                    .withLock { readAllFrom(verdictsFile(nostrGroupId)) }
                    .mapNotNull { line ->
                        val parts = line.trim().split(' ')
                        if (parts.size != 2) return@mapNotNull null
                        parts[0] to (parts[1] == "1")
                    }.toMap()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadModerationVerdicts($nostrGroupId) FAILED: ${e.message}", e)
                emptyMap()
            }
        }

    private fun epochRetentionsFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "epoch_retentions"

    /**
     * What retention this group required at each epoch.
     *
     * First write wins per epoch: an epoch's required components are fixed the
     * moment it exists, so a second answer for the same epoch would be a bug
     * rather than an update.
     */
    override suspend fun recordEpochRetention(
        nostrGroupId: String,
        epoch: Long,
        retentionSecs: Long,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                val existing = readAllFrom(epochRetentionsFile(nostrGroupId)).toMutableList()
                if (existing.any { it.substringBefore(' ') == epoch.toString() }) return@withLock
                existing.add("$epoch $retentionSecs")
                writeAllTo(epochRetentionsFile(nostrGroupId), existing)
            } catch (e: Exception) {
                Log.e(TAG, "recordEpochRetention($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    override suspend fun loadEpochRetentions(nostrGroupId: String): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            try {
                logMutex
                    .withLock { readAllFrom(epochRetentionsFile(nostrGroupId)) }
                    .mapNotNull { line ->
                        val parts = line.trim().split(' ')
                        if (parts.size != 2) return@mapNotNull null
                        val epoch = parts[0].toLongOrNull() ?: return@mapNotNull null
                        val secs = parts[1].toLongOrNull() ?: return@mapNotNull null
                        epoch to secs
                    }.toMap()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadEpochRetentions($nostrGroupId) FAILED: ${e.message}", e)
                emptyMap()
            }
        }

    private fun snapshotFile(nostrGroupId: String): Path = groupDir(nostrGroupId) / "snapshot"

    /**
     * The group state the last kind:1210 rows were derived from.
     *
     * Encrypted like everything else here: it names members and admins, which
     * is the group's membership written down.
     *
     * A single entry rather than an append log — this is one baseline, not a
     * history, and the previous one is worthless the moment rows are derived
     * against it.
     */
    override suspend fun recordGroupSnapshot(
        nostrGroupId: String,
        snapshotJson: String,
    ) = withContext(Dispatchers.IO) {
        logMutex.withLock {
            try {
                writeAllTo(snapshotFile(nostrGroupId), listOf(snapshotJson))
            } catch (e: Exception) {
                Log.e(TAG, "recordGroupSnapshot($nostrGroupId) FAILED: ${e.message}", e)
            }
        }
    }

    override suspend fun loadGroupSnapshot(nostrGroupId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                logMutex.withLock { readAllFrom(snapshotFile(nostrGroupId)) }.firstOrNull()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "loadGroupSnapshot($nostrGroupId) FAILED: ${e.message}", e)
                null
            }
        }

    // The segmented, constant-time-append log every file here is stored as.
    // Guarded by [logMutex]: it caches decrypted entries so an append never has
    // to read the log back, and that cache assumes a single owner.
    private val log =
        EncryptedAppendLog(
            encrypt = { encryption.encrypt(it) },
            // EncryptedAppendLog requires null, not a throw, for a segment it
            // cannot open — SecretEncryption.decrypt rethrows. Without this
            // one bad segment would abort the whole read, and a caller that
            // then sees an empty log can overwrite a history that was merely
            // unreadable.
            decrypt = {
                try {
                    encryption.decrypt(it)
                } catch (e: Exception) {
                    Log.w(TAG, "a log segment could not be decrypted and was skipped: ${e.message}", e)
                    null
                }
            },
            fileSystem = fileSystem,
        )

    // Guarded by [logMutex]: each group's expiries, parsed once from its log and kept in step with
    // every write to it ([recordExpiry], [removeMessages], [delete]).
    private val expiries = mutableMapOf<String, PersistentMap<String, Long>>()

    private fun expiriesFor(nostrGroupId: String): PersistentMap<String, Long> = expiries.getOrPut(nostrGroupId) { parseExpiries(readAllFrom(expiriesFile(nostrGroupId))) }

    /** `<inner event id> <expires at>` lines; a malformed one is skipped, and a repeated id keeps its last value. */
    private fun parseExpiries(lines: List<String>): PersistentMap<String, Long> {
        val builder = persistentMapOf<String, Long>().builder()
        for (line in lines) {
            val parts = line.trim().split(' ')
            if (parts.size != 2) continue
            val at = parts[1].toLongOrNull() ?: continue
            builder[parts[0]] = at
        }
        return builder.build()
    }

    private fun readAll(nostrGroupId: String): List<String> = readAllFrom(messagesFile(nostrGroupId))

    private fun readAllFrom(file: Path): List<String> = log.readAll(file)

    private fun writeAll(
        nostrGroupId: String,
        messages: List<String>,
    ) = writeAllTo(messagesFile(nostrGroupId), messages)

    private fun writeAllTo(
        file: Path,
        messages: List<String>,
    ) = log.rewrite(file, messages)

    companion object {
        private const val TAG = "EncryptedMarmotMessageStore"
        private val HEX_PATTERN = Regex("^[0-9a-fA-F]+$")
    }
}
