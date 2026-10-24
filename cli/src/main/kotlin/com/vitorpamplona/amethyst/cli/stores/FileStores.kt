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
package com.vitorpamplona.amethyst.cli.stores

import com.vitorpamplona.amethyst.cli.SecureFileIO
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.marmot.MarmotIngestDedupStore
import com.vitorpamplona.marmot.groups.MarmotMessageStore
import com.vitorpamplona.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.marmot.protocolCore.MarmotPublishObligationStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile

/**
 * Test-harness file stores. **Unencrypted** — the production interfaces
 * document that implementations MUST encrypt at rest, but this CLI is a
 * throwaway interop driver running against scratch keys and local scratch
 * state. Do not point it at real account material.
 *
 * Writes go through [SecureFileIO] so files land with owner-only filesystem
 * permissions and overwrites are atomic. That blocks other OS users; it does
 * not block another app running as the same user — that still requires
 * encryption at rest.
 */

class FileMlsGroupStateStore(
    private val dir: File,
) : MlsGroupStateStore {
    init {
        SecureFileIO.secureMkdirs(dir)
    }

    private fun stateFile(id: String) = File(dir, "$id.state")

    private fun retainedFile(id: String) = File(dir, "$id.retained")

    private fun ratchetFile(id: String) = File(dir, "$id.ratchet")

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        SecureFileIO.writeBytesAtomic(stateFile(nostrGroupId), state)
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = stateFile(nostrGroupId).takeIf { it.exists() }?.readBytes()

    override suspend fun delete(nostrGroupId: String) {
        stateFile(nostrGroupId).deleteOrWarn("FileMlsGroupStateStore", "group state")
        retainedFile(nostrGroupId).deleteOrWarn("FileMlsGroupStateStore", "retained epochs")
        ratchetFile(nostrGroupId).deleteOrWarn("FileMlsGroupStateStore", "sender ratchet")
    }

    // The CLI reopens a fresh context per command, so "restart between two
    // sends" is its normal mode rather than an edge case — it is the caller
    // most dependent on this record being written, and read back, correctly.
    override suspend fun saveSenderRatchet(
        nostrGroupId: String,
        state: ByteArray,
    ): Boolean {
        SecureFileIO.writeBytesAtomic(ratchetFile(nostrGroupId), state)
        return true
    }

    override suspend fun loadSenderRatchet(nostrGroupId: String): ByteArray? = ratchetFile(nostrGroupId).takeIf { it.exists() }?.readBytes()

    override suspend fun listGroups(): List<String> =
        dir
            .listFiles { f -> f.name.endsWith(".state") }
            ?.map { it.name.removeSuffix(".state") }
            ?: emptyList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        // Layout: [u32 count][(u32 len, bytes) …] — tiny framing so readers
        // can recover independent byte arrays without TLS plumbing.
        SecureFileIO.writeAtomic(retainedFile(nostrGroupId)) { out ->
            val buf = java.nio.ByteBuffer.allocate(4)
            buf.putInt(retainedSecrets.size)
            out.write(buf.array())
            for (secret in retainedSecrets) {
                buf.clear()
                buf.putInt(secret.size)
                out.write(buf.array())
                out.write(secret)
            }
        }
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> {
        val f = retainedFile(nostrGroupId)
        if (!f.exists()) return emptyList()
        val bytes = f.readBytes()
        if (bytes.size < 4) return emptyList()
        val buf = java.nio.ByteBuffer.wrap(bytes)
        val count = buf.int
        val result = ArrayList<ByteArray>(count)
        repeat(count) {
            val len = buf.int
            val arr = ByteArray(len)
            buf.get(arr)
            result.add(arr)
        }
        return result
    }
}

class FileKeyPackageBundleStore(
    private val file: File,
) : KeyPackageBundleStore {
    override suspend fun save(snapshot: ByteArray) {
        SecureFileIO.writeBytesAtomic(file, snapshot)
    }

    override suspend fun load(): ByteArray? = file.takeIf { it.exists() }?.readBytes()

    override suspend fun delete() {
        file.deleteOrWarn("FileKeyPackageBundleStore", "key package bundle")
    }
}

/** A pubkey or event id, in hex. */
private const val KEY_HEX_LENGTH = 64

/** A Marmot media key is a 32-byte MLS exporter secret. */
private const val MEDIA_SECRET_HEX_LENGTH = 64

class FileMarmotMessageStore(
    private val dir: File,
) : MarmotMessageStore {
    init {
        SecureFileIO.secureMkdirs(dir)
    }

    private fun file(id: String) = File(dir, "$id.messages")

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) {
        // Each line is one inner event JSON. Appends must be idempotent:
        // post-restart relay replays re-decrypt already-persisted messages.
        val line = innerEventJson.replace("\n", " ")
        val target = file(nostrGroupId)
        if (target.exists() && target.readLines().any { it == line }) return
        SecureFileIO.appendText(target, line + "\n")
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> = file(nostrGroupId).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: emptyList()

    override suspend fun delete(nostrGroupId: String) {
        file(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group messages")
        epochFile(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group message epochs")
        snapshotFile(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group system-row baseline")
        expiryFile(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group message expiries")
        epochRetentionFile(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group epoch retentions")
        mediaSecretFile(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group media keys")
        epochAdminsFile(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group epoch admins")
        verdictsFile(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group moderation verdicts")
    }

    private fun snapshotFile(id: String) = File(dir, "$id.snapshot")

    override suspend fun recordGroupSnapshot(
        nostrGroupId: String,
        snapshotJson: String,
    ) {
        // Overwritten, not appended: this is one baseline, not a history.
        SecureFileIO.writeBytesAtomic(snapshotFile(nostrGroupId), snapshotJson.encodeToByteArray())
    }

    override suspend fun loadGroupSnapshot(nostrGroupId: String): String? = snapshotFile(nostrGroupId).takeIf { it.exists() }?.readText()

    private fun expiryFile(id: String) = File(dir, "$id.expiries")

    /**
     * First write wins: an expiry is pinned to the retention of the message's
     * own source epoch, so re-persisting the same message after a replay must
     * not re-time it under a setting that has since changed.
     */
    override suspend fun recordExpiry(
        nostrGroupId: String,
        innerEventId: String,
        expiresAtSecs: Long,
    ) {
        val target = expiryFile(nostrGroupId)
        if (target.exists() && target.readLines().any { it.substringBefore(' ') == innerEventId }) return
        SecureFileIO.appendText(target, "$innerEventId $expiresAtSecs\n")
    }

    override suspend fun loadExpiries(nostrGroupId: String): Map<String, Long> =
        expiryFile(nostrGroupId)
            .takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 2) return@mapNotNull null
                val at = parts[1].toLongOrNull() ?: return@mapNotNull null
                parts[0] to at
            }?.toMap()
            ?: emptyMap()

    /** Rewrites both logs: a disappearing message has to actually leave the disk. */
    override suspend fun removeMessages(
        nostrGroupId: String,
        innerEventIds: Set<String>,
    ) {
        if (innerEventIds.isEmpty()) return
        val target = file(nostrGroupId)
        if (target.exists()) {
            val kept =
                target.readLines().filter { line ->
                    line.isNotBlank() && Event.fromJsonOrNull(line)?.id !in innerEventIds
                }
            SecureFileIO.writeBytesAtomic(target, (kept.joinToString("\n") + if (kept.isEmpty()) "" else "\n").encodeToByteArray())
        }
        val expiries = expiryFile(nostrGroupId)
        if (expiries.exists()) {
            val kept = expiries.readLines().filter { it.isNotBlank() && it.substringBefore(' ') !in innerEventIds }
            SecureFileIO.writeBytesAtomic(expiries, (kept.joinToString("\n") + if (kept.isEmpty()) "" else "\n").encodeToByteArray())
        }
    }

    private fun epochRetentionFile(id: String) = File(dir, "$id.epoch-retentions")

    /** First write wins: an epoch's required components are fixed once it exists. */
    override suspend fun recordEpochRetention(
        nostrGroupId: String,
        epoch: Long,
        retentionSecs: Long,
    ) {
        val target = epochRetentionFile(nostrGroupId)
        if (target.exists() && target.readLines().any { it.substringBefore(' ') == epoch.toString() }) return
        SecureFileIO.appendText(target, "$epoch $retentionSecs\n")
    }

    override suspend fun loadEpochRetentions(nostrGroupId: String): Map<Long, Long> =
        epochRetentionFile(nostrGroupId)
            .takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 2) return@mapNotNull null
                val epoch = parts[0].toLongOrNull() ?: return@mapNotNull null
                val secs = parts[1].toLongOrNull() ?: return@mapNotNull null
                epoch to secs
            }?.toMap()
            ?: emptyMap()

    private fun mediaSecretFile(id: String) = File(dir, "$id.media-secrets")

    /** First write wins: an epoch's media key never changes. */
    override suspend fun recordMediaSecret(
        nostrGroupId: String,
        epoch: Long,
        mediaSecret: ByteArray,
    ) {
        // Checked with the parser that loads it: a line a crash cut short is not a record.
        if (epoch in loadMediaSecrets(nostrGroupId)) return
        appendRecord(mediaSecretFile(nostrGroupId), "$epoch ${mediaSecret.toHexKey()}")
    }

    override suspend fun loadMediaSecrets(nostrGroupId: String): Map<Long, ByteArray> =
        mediaSecretFile(nostrGroupId)
            .takeIf { it.exists() }
            ?.completeRecords()
            ?.mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 2) return@mapNotNull null
                val epoch = parts[0].toLongOrNull() ?: return@mapNotNull null
                if (!parts[1].isHex(MEDIA_SECRET_HEX_LENGTH)) return@mapNotNull null
                epoch to parts[1].hexToByteArray()
            }?.toMap()
            ?: emptyMap()

    private fun epochAdminsFile(id: String) = File(dir, "$id.epoch-admins")

    private fun verdictsFile(id: String) = File(dir, "$id.moderation-verdicts")

    /** First write wins: an epoch's admin set is fixed once it exists. */
    override suspend fun recordEpochAdmins(
        nostrGroupId: String,
        epoch: Long,
        admins: Set<String>,
    ) {
        if (epoch in loadEpochAdmins(nostrGroupId)) return
        appendRecord(epochAdminsFile(nostrGroupId), "$epoch ${admins.joinToString(",")}")
    }

    override suspend fun loadEpochAdmins(nostrGroupId: String): Map<Long, Set<String>> =
        epochAdminsFile(nostrGroupId)
            .takeIf { it.exists() }
            ?.completeRecords()
            ?.mapNotNull { line ->
                val epoch = line.substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
                val admins =
                    line
                        .substringAfter(' ', "")
                        .split(',')
                        .filter { it.isNotEmpty() }
                        .toSet()
                if (admins.any { !it.isHex(KEY_HEX_LENGTH) }) return@mapNotNull null
                epoch to admins
            }?.toMap()
            ?: emptyMap()

    /** First write wins: a deletion's authority is decided once. */
    override suspend fun recordModerationVerdict(
        nostrGroupId: String,
        innerEventId: String,
        authorized: Boolean,
    ) {
        if (innerEventId in loadModerationVerdicts(nostrGroupId)) return
        appendRecord(verdictsFile(nostrGroupId), "$innerEventId ${if (authorized) 1 else 0}")
    }

    override suspend fun loadModerationVerdicts(nostrGroupId: String): Map<String, Boolean> =
        verdictsFile(nostrGroupId)
            .takeIf { it.exists() }
            ?.completeRecords()
            ?.mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 2 || !parts[0].isHex(KEY_HEX_LENGTH) || parts[1] !in setOf("0", "1")) return@mapNotNull null
                parts[0] to (parts[1] == "1")
            }?.toMap()
            ?: emptyMap()

    /**
     * Appends one record line. A crash can leave the last line unterminated: that partial record is
     * cut off first, since terminating it would turn it into a record that looks whole.
     */
    private fun appendRecord(
        target: File,
        line: String,
    ) {
        if (target.exists() && target.length() > 0 && target.lastByte() != '\n'.code.toByte()) {
            val text = target.readText()
            SecureFileIO.writeTextAtomic(target, text.substring(0, text.lastIndexOf('\n') + 1))
        }
        SecureFileIO.appendText(target, line + "\n")
    }

    private fun File.lastByte(): Byte =
        RandomAccessFile(this, "r").use {
            it.seek(it.length() - 1)
            it.readByte()
        }

    /**
     * The records [appendRecord] finished: every one ends in a newline, so an unterminated last line
     * is a write a crash cut short. Parsed, it could still look valid (an admin set missing its
     * last keys) and, first write wins, would never be corrected.
     */
    private fun File.completeRecords(): List<String> = readText().split('\n').dropLast(1)

    private fun String.isHex(length: Int) = this.length == length && all { it in '0'..'9' || it in 'a'..'f' }

    private fun epochFile(id: String) = File(dir, "$id.epochs")

    override suspend fun recordEpoch(
        nostrGroupId: String,
        innerEventId: String,
        epoch: Long,
    ) {
        val line = "$innerEventId $epoch"
        val target = epochFile(nostrGroupId)
        if (target.exists() && target.readLines().any { it == line }) return
        SecureFileIO.appendText(target, line + "\n")
    }

    override suspend fun loadEpochs(nostrGroupId: String): Map<String, Long> =
        epochFile(nostrGroupId)
            .takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 2) return@mapNotNull null
                val epoch = parts[1].toLongOrNull() ?: return@mapNotNull null
                parts[0] to epoch
            }?.toMap() ?: emptyMap()
}

/**
 * Durable publish obligations, one file per obligation under [dir].
 *
 * Publish-before-apply only means anything if the obligation outlives the
 * process: the whole point is that a commit is prepared, recorded, published,
 * and only then applied, so a crash between record and publish must leave a
 * trace. With a non-durable store that window silently becomes "the commit
 * never happened", and on relaunch the client mints a REPLACEMENT commit for
 * the same epoch — forking itself against the peers that accepted the first
 * one.
 *
 * A file per obligation rather than one appended log: obligations resolve out
 * of order (two groups publish concurrently), and deleting one must not
 * rewrite the others.
 */
class FilePublishObligationStore(
    private val dir: File,
) : MarmotPublishObligationStore {
    init {
        SecureFileIO.secureMkdirs(dir)
    }

    private fun file(obligationId: String) = File(dir, "$obligationId.obligation")

    override suspend fun save(
        obligationId: HexKey,
        bytes: ByteArray,
    ) {
        SecureFileIO.writeBytesAtomic(file(obligationId), bytes)
    }

    override suspend fun delete(obligationId: HexKey) {
        file(obligationId).deleteOrWarn("FilePublishObligationStore", "publish obligation")
    }

    override suspend fun loadAll(): List<ByteArray> =
        dir
            .listFiles { f -> f.isFile && f.name.endsWith(".obligation") }
            ?.sortedBy { it.name }
            ?.mapNotNull { runCatching { it.readBytes() }.getOrNull() }
            .orEmpty()

    private fun gateFile(groupId: String) = File(dir, "$groupId.gate")

    /**
     * Outbound gates live beside the obligations and are durable for the same
     * reason: `Disbanding` must survive "publication failure, restart, and a
     * losing branch", and every `amy` verb is its own process — so an
     * in-memory gate would not survive even the next command, let alone a
     * crash.
     */
    override suspend fun saveGate(
        groupId: HexKey,
        gate: String,
    ) {
        SecureFileIO.writeBytesAtomic(gateFile(groupId), gate.encodeToByteArray())
    }

    override suspend fun deleteGate(groupId: HexKey) {
        gateFile(groupId).deleteOrWarn("FilePublishObligationStore", "outbound gate")
    }

    override suspend fun loadGates(): Map<HexKey, String> =
        dir
            .listFiles { f -> f.isFile && f.name.endsWith(".gate") }
            ?.mapNotNull { file ->
                runCatching { file.name.removeSuffix(".gate") to file.readText().trim() }.getOrNull()
            }?.toMap()
            .orEmpty()
}

/**
 * Durable "already decided" markers, one hex id per line.
 *
 * Append-only and capped: the point is to stop re-deciding backdated gift
 * wraps forever, not to remember every event this account has ever seen. When
 * the cap is hit the oldest half is dropped — the worst case for a forgotten
 * marker is one wasted re-decision, so trading memory for exactness is the
 * right way round.
 */
class FileIngestDedupStore(
    private val file: File,
    private val maxEntries: Int = 20_000,
) : MarmotIngestDedupStore {
    private val mutex = Mutex()

    override suspend fun mark(eventId: HexKey) =
        mutex.withLock {
            SecureFileIO.appendText(file, eventId + "\n")
            if (file.length() > maxEntries.toLong() * 65L) {
                val kept = file.readLines().filter { it.isNotBlank() }.takeLast(maxEntries / 2)
                SecureFileIO.writeBytesAtomic(file, (kept.joinToString("\n") + "\n").encodeToByteArray())
            }
        }

    override suspend fun loadAll(): Set<HexKey> =
        mutex.withLock {
            file
                .takeIf { it.exists() }
                ?.readLines()
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
        }
}
