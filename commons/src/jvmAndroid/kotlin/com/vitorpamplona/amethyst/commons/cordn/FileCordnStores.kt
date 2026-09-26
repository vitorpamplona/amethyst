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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.storage.EncryptedAppendLog
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessageCodec
import com.vitorpamplona.quartz.cordn.sync.EchoState
import com.vitorpamplona.quartz.cordn.sync.GroupCursor
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Where one account's state for one coordinator lives on disk.
 *
 * Both halves of the key matter and neither is optional. Per account, because
 * two accounts on one device must not read each other's groups. Per
 * coordinator, because a `gid` is unique only within one (`spec/00.md` §4) —
 * two coordinators can both serve `gid = "abc"` as unrelated groups, and a
 * layout that ignored the coordinator would have one silently overwrite the
 * other's ratchet tree.
 */
@OptIn(ExperimentalEncodingApi::class)
object CordnStorageLayout {
    /**
     * `<root>/cordn/<account>/<coordinator>`.
     *
     * Both keys are validated as hex before they reach a path. They always are
     * — they are Nostr pubkeys — so this costs nothing and closes the one place
     * a caller could smuggle `..` into a directory name.
     */
    fun directoryFor(
        root: File,
        accountPubKey: HexKey,
        coordinatorPubKey: HexKey,
    ): File {
        require(accountPubKey.matches(HEX)) { "account pubkey must be hex" }
        require(coordinatorPubKey.matches(HEX)) { "coordinator pubkey must be hex" }
        return File(root, "cordn/$accountPubKey/$coordinatorPubKey")
    }

    /**
     * `<root>/cordn/<account>` — the account's own directory, above any
     * coordinator's.
     *
     * The coordinator list lives here rather than inside a coordinator's
     * directory, because it is the list OF them: storing it under one would
     * make that coordinator's removal delete the record of the others.
     */
    fun accountDirectoryFor(
        root: File,
        accountPubKey: HexKey,
    ): File {
        require(accountPubKey.matches(HEX)) { "account pubkey must be hex" }
        return File(root, "cordn/$accountPubKey")
    }

    /**
     * A filename for an arbitrary caller-chosen key.
     *
     * Base64url rather than the key itself, and deliberately NOT the hex
     * validation Marmot's store uses. A Marmot group id is a hash and is
     * always hex; a cordn `gid` is whatever the group's creator picked (§4 —
     * the coordinator never interprets it, and the reference client happens to
     * use a UUID). So a `gid` can contain `/`, `..`, a NUL, or a name that is
     * special on some filesystem, and rejecting those would refuse groups the
     * protocol allows. Encoding accepts every one of them and can still be
     * reversed, which is what lets [FileCordnGroupStore.listGroups] give the
     * real `gid` back rather than an opaque hash.
     */
    fun encodeKey(key: String): String {
        val encoded = Base64.UrlSafe.encode(key.encodeToByteArray()).trimEnd('=')
        require(encoded.length <= MAX_NAME) { "key is too long to store: ${key.length} chars" }
        return encoded
    }

    /** The inverse of [encodeKey]; null when the name was not written by us. */
    fun decodeKey(name: String): String? =
        try {
            Base64.UrlSafe.decode(name.padEnd((name.length + 3) / 4 * 4, '=')).decodeToString()
        } catch (e: IllegalArgumentException) {
            // A stray file in our directory. Ignoring it is better than failing
            // the whole listing and hiding every real group behind it.
            null
        }

    private val HEX = Regex("^[0-9a-fA-F]{64}$")

    /** Comfortably under the 255-byte limit every filesystem we target has. */
    private const val MAX_NAME = 200
}

private const val TAG = "CordnStores"

/**
 * Writes a blob to [file] so that a crash leaves either the old bytes or the
 * new ones, never half of each.
 *
 * MLS makes this sharper than ordinary durability: a truncated `MlsGroupState`
 * is not a stale group, it is an unreadable one, and the group cannot be
 * re-derived from anywhere else on this device.
 */
private fun atomicWrite(
    file: File,
    data: ByteArray,
) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeBytes(data)
    if (!temp.renameTo(file)) {
        temp.copyTo(file, overwrite = true)
        temp.delete()
    }
}

/**
 * A [CordnGroupStore] on the filesystem, encrypted through [cipher].
 *
 * ```
 * <dir>/groups/<base64url(gid)>/state       — encrypted MlsGroupState
 * <dir>/groups/<base64url(gid)>/cursor      — encrypted GroupCursor
 * <dir>/groups/<base64url(gid)>/via-request — present iff admitted by request
 * <dir>/groups/<base64url(gid)>/room        — encrypted draft + read position
 * ```
 *
 * Scope [dir] with [CordnStorageLayout.directoryFor]; this class trusts that it
 * already belongs to exactly one (account, coordinator) pair.
 *
 * Not a refactor of Marmot's `AndroidMlsGroupStateStore` and not shareable with
 * it: that one keys by a hex Nostr group id and persists retained epoch secrets
 * for Marmot's own rotation, neither of which exists here. The resemblance is
 * that both encrypt blobs, which is not an abstraction worth having.
 */
class FileCordnGroupStore(
    private val dir: File,
    private val cipher: CordnBlobCipher,
) : CordnGroupStore {
    private fun groupDir(gid: String) = File(dir, "groups/${CordnStorageLayout.encodeKey(gid)}")

    private fun stateFile(gid: String) = File(groupDir(gid), "state")

    private fun cursorFile(gid: String) = File(groupDir(gid), "cursor")

    private fun joinOriginFile(gid: String) = File(groupDir(gid), "via-request")

    private fun roomStateFile(gid: String) = File(groupDir(gid), "room")

    private fun echoStateFile(gid: String) = File(groupDir(gid), "echoes")

    /**
     * Inside [groupDir] so [deleteGroup]'s recursive delete already covers it:
     * a group that left its history behind would keep the plaintext of an
     * end-to-end encrypted conversation after the key that read it was gone.
     */
    private fun messagesFile(gid: String) = File(groupDir(gid), "messages")

    private fun summaryFile(gid: String) = File(groupDir(gid), "newest")

    /**
     * Appending a segment rather than rewriting the conversation, so the cost
     * of receiving a message does not grow with how much has been said.
     */
    private val messageLog =
        EncryptedAppendLog(
            encrypt = cipher::encrypt,
            // The log treats null as "this segment is unreadable" and carries on
            // with the rest, which is what one corrupt segment should cost.
            decrypt = { runCatching { cipher.decrypt(it) }.getOrNull() },
        )

    private val messageLock = Mutex()

    /**
     * Envelope ids already in a group's log.
     *
     * Dedup cannot be the log's own whole-entry comparison: the same message
     * re-delivered after a crash carries the same envelope but not necessarily
     * the same cursor, so the entries differ as strings while naming one
     * message. Built once per group from the log the first time it is touched.
     */
    private val seenIds = mutableMapOf<String, MutableSet<HexKey>>()

    private fun idsFor(gid: String): MutableSet<HexKey> =
        seenIds.getOrPut(gid) {
            messageLog
                .readAll(messagesFile(gid))
                .mapNotNullTo(mutableSetOf()) { CordnDeliveredMessageCodec.decodeOrNull(it)?.envelope?.id }
        }

    override suspend fun saveGroup(
        gid: String,
        state: ByteArray,
    ) = withContext(Dispatchers.IO) {
        atomicWrite(stateFile(gid), cipher.encrypt(state))
    }

    override suspend fun loadGroup(gid: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = stateFile(gid)
            if (!file.exists()) null else cipher.decrypt(file.readBytes())
        }

    override suspend fun deleteGroup(gid: String) {
        withContext(Dispatchers.IO) {
            messageLock.withLock {
                // The log caches a file's entries by path, so dropping the
                // directory alone would leave a re-join of the same gid reading
                // the previous membership's messages out of memory.
                messageLog.forget(messagesFile(gid))
                seenIds.remove(gid)
            }
            // The cursor goes with it. Leaving one behind would mean a later
            // re-join of the same gid resumes from a cursor belonging to a
            // group it is no longer in, skipping everything before it.
            groupDir(gid).deleteRecursively()
        }
    }

    override suspend fun listGroups(): List<String> =
        withContext(Dispatchers.IO) {
            File(dir, "groups")
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && File(it, "state").exists() }
                .mapNotNull { CordnStorageLayout.decodeKey(it.name) }
        }

    override suspend fun saveCursor(
        gid: String,
        cursor: GroupCursor,
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteBuffer.allocate(16).putLong(cursor.fetchCursor).putLong(cursor.lastCursor)
        atomicWrite(cursorFile(gid), cipher.encrypt(buffer.array()))
    }

    override suspend fun loadCursor(gid: String): GroupCursor? =
        withContext(Dispatchers.IO) {
            val file = cursorFile(gid)
            if (!file.exists()) return@withContext null
            val bytes = cipher.decrypt(file.readBytes())
            if (bytes.size < 16) return@withContext null
            val buffer = ByteBuffer.wrap(bytes)
            GroupCursor(fetchCursor = buffer.long, lastCursor = buffer.long)
        }

    // Existence IS the flag, so there is nothing to encrypt and nothing to
    // read back wrong. It only ever goes from absent to present -- a join
    // request cannot be unsent -- and it is removed with the group because a
    // later re-join of the same gid is a different admission.
    override suspend fun saveJoinedViaRequest(gid: String) {
        withContext(Dispatchers.IO) {
            atomicWrite(joinOriginFile(gid), ByteArray(0))
        }
    }

    override suspend fun loadJoinedViaRequest(gid: String): Boolean = withContext(Dispatchers.IO) { joinOriginFile(gid).exists() }

    override suspend fun saveRoomState(
        gid: String,
        state: CordnRoomState,
    ) = withContext(Dispatchers.IO) {
        // Deleted rather than blanked when there is nothing to remember, so an
        // emptied draft leaves no plaintext behind in an old file.
        if (state.isBlank) {
            roomStateFile(gid).delete()
            return@withContext
        }
        atomicWrite(roomStateFile(gid), cipher.encrypt(CordnRoomStateCodec.encode(state)))
    }

    override suspend fun loadRoomState(gid: String): CordnRoomState =
        withContext(Dispatchers.IO) {
            val file = roomStateFile(gid)
            if (!file.exists()) return@withContext CordnRoomState()
            try {
                CordnRoomStateCodec.decode(cipher.decrypt(file.readBytes()))
            } catch (e: Exception) {
                CordnRoomState()
            }
        }

    override suspend fun appendMessage(
        gid: String,
        message: CordnDeliveredMessage,
    ) = withContext(Dispatchers.IO) {
        messageLock.withLock {
            val ids = idsFor(gid)
            // Idempotent on the envelope id. The crash window between this and
            // saveCursor is deliberate — see the store interface — and it is
            // this check that makes re-delivery free rather than duplicating.
            if (!ids.add(message.envelope.id)) return@withContext

            groupDir(gid).mkdirs()
            messageLog.append(messagesFile(gid), CordnDeliveredMessageCodec.encode(message))
            // Written on the same beat, so the inbox preview cannot disagree
            // with the room. Whole-blob rather than appended: it is one entry
            // that is always overwritten.
            atomicWrite(summaryFile(gid), cipher.encrypt(CordnMessageSummaryCodec.encode(message, ids.size)))
        }
    }

    override suspend fun loadMessages(gid: String): List<CordnDeliveredMessage> =
        withContext(Dispatchers.IO) {
            messageLock.withLock {
                // One unreadable entry costs that message, not the conversation
                // behind it in the file.
                messageLog.readAll(messagesFile(gid)).mapNotNull { CordnDeliveredMessageCodec.decodeOrNull(it) }
            }
        }

    override suspend fun loadMessageSummary(gid: String): CordnMessageSummary? =
        withContext(Dispatchers.IO) {
            val file = summaryFile(gid)
            if (!file.exists()) return@withContext null
            try {
                CordnMessageSummaryCodec.decode(cipher.decrypt(file.readBytes()))
            } catch (e: Exception) {
                // A summary is a derived convenience; losing one costs a preview
                // line until the next message, not the history it summarises.
                Log.w("FileCordnGroupStore", "unreadable message summary for $gid: ${e.message}", e)
                null
            }
        }

    override suspend fun saveEchoState(
        gid: String,
        state: EchoState,
    ) = withContext(Dispatchers.IO) {
        // Deleted when there is nothing pending, so the common steady state is
        // no file rather than an empty one.
        if (state.isEmpty) {
            echoStateFile(gid).delete()
            return@withContext
        }
        atomicWrite(echoStateFile(gid), cipher.encrypt(EchoStateCodec.encode(state)))
    }

    override suspend fun loadEchoState(gid: String): EchoState =
        withContext(Dispatchers.IO) {
            val file = echoStateFile(gid)
            if (!file.exists()) return@withContext EchoState()
            try {
                EchoStateCodec.decode(cipher.decrypt(file.readBytes()))
            } catch (e: Exception) {
                EchoState()
            }
        }
}

/**
 * A [CordnKeyPackageStore] on the filesystem, encrypted through [cipher].
 *
 * ```
 * <dir>/keypackages/<base64url(kp_ref)>  — encrypted KeyPackageBundle
 * ```
 *
 * Scope [dir] per coordinator like the group store: a `kp_ref` is the
 * coordinator's primary key for a KeyPackage (§4.2) and the same account
 * publishes different ones to different coordinators.
 */
class FileCordnKeyPackageStore(
    private val dir: File,
    private val cipher: CordnBlobCipher,
) : CordnKeyPackageStore {
    private fun bundleFile(keyPackageRef: String) = File(dir, "keypackages/${CordnStorageLayout.encodeKey(keyPackageRef)}")

    override suspend fun save(
        keyPackageRef: String,
        bundle: ByteArray,
    ) = withContext(Dispatchers.IO) {
        atomicWrite(bundleFile(keyPackageRef), cipher.encrypt(bundle))
    }

    override suspend fun load(keyPackageRef: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = bundleFile(keyPackageRef)
            if (!file.exists()) null else cipher.decrypt(file.readBytes())
        }

    override suspend fun delete(keyPackageRef: String) {
        withContext(Dispatchers.IO) {
            bundleFile(keyPackageRef).delete()
        }
    }

    override suspend fun list(): List<String> =
        withContext(Dispatchers.IO) {
            File(dir, "keypackages")
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                // No explicit ".tmp" exclusion, because the encoding already
                // is one: '.' is not in the base64url alphabet, so a temp file
                // left behind by a crashed write cannot decode to a ref and
                // [CordnStorageLayout.decodeKey] drops it. A second filter
                // saying the same thing would be a branch no test can reach.
                .mapNotNull { CordnStorageLayout.decodeKey(it.name) }
        }
}

/**
 * A [CordnCoordinatorStore] on the filesystem, encrypted through [cipher].
 *
 * ```
 * <dir>/coordinators  — encrypted CoordinatorListCodec blob
 * ```
 *
 * Scope [dir] with [CordnStorageLayout.accountDirectoryFor]: one file per
 * account, sitting above the per-coordinator directories it names.
 */
class FileCordnCoordinatorStore(
    private val dir: File,
    private val cipher: CordnBlobCipher,
) : CordnCoordinatorStore {
    private val file get() = File(dir, "coordinators")

    override suspend fun save(configs: List<CoordinatorConfig>) =
        withContext(Dispatchers.IO) {
            atomicWrite(file, cipher.encrypt(CoordinatorListCodec.encode(configs)))
        }

    override suspend fun load(): List<CoordinatorConfig> =
        withContext(Dispatchers.IO) {
            val stored = file
            if (!stored.exists()) return@withContext emptyList()
            try {
                CoordinatorListCodec.decode(cipher.decrypt(stored.readBytes()))
            } catch (e: Exception) {
                // A list written by a future build, or one the keystore can no
                // longer decrypt. Returning nothing loses the coordinators but
                // keeps the account usable; throwing here would fail login.
                //
                // Say so, though. Losing this list loses every cordn group with
                // it — the MLS state stays on disk but a gid whose coordinator
                // is unknown is not a group anyone can open — and silently it
                // looks exactly like an account that never had a coordinator:
                // the rooms are gone from the inbox and the invitations screen
                // says there is nowhere to look. One line is the difference
                // between a diagnosable fault and a mystery.
                Log.w(TAG, "could not read ${stored.length()} bytes of coordinators, losing them: ${e.message}", e)
                emptyList()
            }
        }
}

/**
 * The handoff flag, as the presence of a file.
 *
 * `<accountDir>/handed-off`. A zero-byte marker rather than an encrypted blob:
 * it carries no secret, and a flag that failed to decrypt would fail open —
 * which here means a device that quietly resumes committing after handing its
 * groups to another one, the exact fork the flag exists to prevent.
 */
class FileCordnHandoffStore(
    private val accountDir: File,
) : CordnHandoffStore {
    override suspend fun load(): Boolean = marker().exists()

    override suspend fun save(handedOff: Boolean) {
        val file = marker()
        if (handedOff) {
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(0))
        } else {
            file.delete()
        }
    }

    private fun marker() = File(accountDir, "handed-off")
}
