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
import com.vitorpamplona.quartz.marmot.MarmotIngestDedupStore
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotPublishObligationStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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
    }

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
