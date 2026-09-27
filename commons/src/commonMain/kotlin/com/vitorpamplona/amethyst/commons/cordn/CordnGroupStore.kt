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

import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessageCodec
import com.vitorpamplona.quartz.cordn.sync.EchoState
import com.vitorpamplona.quartz.cordn.sync.GroupCursor
import com.vitorpamplona.quartz.cordn.sync.PendingEpochOperation
import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.codec.TlsWriter

/**
 * Local storage for cordn group state, keyed by the delivery `gid`.
 *
 * **Implementations MUST encrypt at rest.** The blobs are
 * `MlsGroupState.encodeTls()` output: private keys and epoch secrets.
 *
 * Separate from Marmot's `MlsGroupStateStore` rather than shared, and the key
 * is the reason. That interface is keyed on the Nostr group id — the MIP-01 `h`
 * tag — which cordn has no equivalent of; cordn's key is the coordinator's
 * delivery `gid`, which is opaque, is not the MLS `group_id`, and is not even
 * unique across coordinators. The two look alike and mean different things, so
 * one interface serving both would be an invitation to hand the wrong id to the
 * wrong store and silently find no group.
 *
 * The cursor lives here too because it is worthless apart from the state it
 * belongs to: a cursor restored against a group at a different epoch replays
 * messages the group can no longer decrypt.
 */
interface CordnGroupStore {
    suspend fun saveGroup(
        gid: String,
        state: ByteArray,
    )

    suspend fun loadGroup(gid: String): ByteArray?

    suspend fun deleteGroup(gid: String)

    /** Every `gid` with saved state, for restoring memberships at startup. */
    suspend fun listGroups(): List<String>

    suspend fun saveCursor(
        gid: String,
        cursor: GroupCursor,
    )

    suspend fun loadCursor(gid: String): GroupCursor?

    /**
     * Saves the record of which stream entries are this client's own.
     *
     * Beside the cursor because it is only meaningful with it, and durable for
     * the same reason: posting does not advance the cursor (a lower one may
     * still hold somebody else's unprocessed message), so the next run
     * re-reads what this one posted. Without this it cannot tell that it is
     * its own — a Commit sealed under an epoch key it has since left, a
     * message from a ratchet generation already consumed — and reports a gap
     * in its own conversation. See `EchoState`.
     */
    suspend fun saveEchoState(
        gid: String,
        state: EchoState,
    )

    /** The saved echo bookkeeping for [gid], or an empty one. */
    suspend fun loadEchoState(gid: String): EchoState

    /**
     * Records that admission to [gid] went through `join_request_store`.
     *
     * Per-group side state beside the cursor, and durable for the same reason:
     * it describes something the coordinator will not forget. §8.1 — a join
     * request names the asker's real npub against a specific group, so once it
     * has happened the coordinator can tie this account to this group forever.
     * Whether *we* still remember changes nothing about what it knows, which
     * is why this outlives the session that did it rather than resetting to a
     * cheerful default at every launch.
     */
    suspend fun saveJoinedViaRequest(gid: String)

    /** Whether [gid] was admitted through a join request. */
    suspend fun loadJoinedViaRequest(gid: String): Boolean

    /**
     * Saves the per-room state a screen needs but the protocol does not.
     *
     * An unsent draft and a read position are not MLS state and never leave
     * the device — but they go through the same store, and so the same
     * encryption, for one reason: a draft is the plaintext of a message that
     * was about to be end-to-end encrypted. Writing it somewhere softer than
     * the conversation it belongs to would make the composer the weakest point
     * in the whole feature.
     */
    suspend fun saveRoomState(
        gid: String,
        state: CordnRoomState,
    )

    /** The saved room state for [gid], or a blank one. */
    suspend fun loadRoomState(gid: String): CordnRoomState

    /**
     * Records one delivered message, as the only copy there will ever be.
     *
     * Not a cache. Both of a cordn payload's seal keys are derived per epoch,
     * so once the group ratchets forward the ciphertext the coordinator still
     * holds cannot be opened again — and the cursor has advanced past it in any
     * case, so it will not even be offered. Ingestion is the one moment the
     * message is in the clear.
     *
     * MUST be idempotent on the envelope's id. A client that dies between this
     * call and [saveCursor] re-fetches the message on the next catch-up, which
     * is the crash window the append order is chosen to land in — see
     * `commons/plans/2026-09-24-cordn-message-store.md`.
     */
    suspend fun appendMessage(
        gid: String,
        message: CordnDeliveredMessage,
    )

    /** Every stored message for [gid], oldest first. Empty when there are none. */
    suspend fun loadMessages(gid: String): List<CordnDeliveredMessage>

    /**
     * The newest message and the count, without reading the whole log.
     *
     * The inbox needs a preview line for every room before any room is opened,
     * and reading every group's history at login is what
     * `CordnRuntime.restoreRoomState` deliberately avoids. This is the small
     * read that makes the preview possible: one key per group, written on the
     * same beat as [appendMessage].
     */
    suspend fun loadMessageSummary(gid: String): CordnMessageSummary?
}

/**
 * What the inbox needs to know about a room without opening it.
 *
 * @param newest the last message delivered, for the preview line.
 * @param count how many are stored, for anything that wants to say "empty" and
 *   mean it rather than meaning "not loaded yet".
 */
data class CordnMessageSummary(
    val newest: CordnDeliveredMessage,
    val count: Int,
)

/**
 * What a cordn room remembers between visits.
 *
 * @param draft text typed and not sent.
 * @param lastReadCursor the newest cursor this account has seen in the room.
 *   A cursor rather than a timestamp, because the coordinator's order is the
 *   only one every member agrees on (`spec/00.md` §4) and a sender's clock is
 *   a claim — the same reason the room itself sorts on it.
 */
data class CordnRoomState(
    val draft: String = "",
    val lastReadCursor: Long = 0L,
) {
    val isBlank: Boolean get() = draft.isEmpty() && lastReadCursor == 0L
}

/**
 * The on-disk form of a [CordnMessageSummary].
 *
 * The message itself rides as its own JSON entry rather than being taken apart
 * into TLS fields, so there is exactly one definition of what a stored message
 * looks like and the summary cannot drift from the log it summarises.
 */
object CordnMessageSummaryCodec {
    const val VERSION = 1

    fun encode(
        newest: CordnDeliveredMessage,
        count: Int,
    ): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(VERSION)
        writer.putUint32(count.toLong())
        writer.putOpaque2(CordnDeliveredMessageCodec.encode(newest).encodeToByteArray())
        return writer.toByteArray()
    }

    /** Null on anything unreadable: a preview line is not worth failing a login over. */
    fun decode(bytes: ByteArray): CordnMessageSummary? =
        try {
            val reader = TlsReader(bytes)
            if (reader.readUint16() != VERSION) {
                null
            } else {
                val count = reader.readUint32().toInt()
                val newest = CordnDeliveredMessageCodec.decode(reader.readOpaque2().decodeToString())
                CordnMessageSummary(newest, count)
            }
        } catch (e: Exception) {
            null
        }
}

/** The on-disk layout of a [CordnRoomState]. */
object CordnRoomStateCodec {
    const val VERSION = 1

    fun encode(state: CordnRoomState): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(VERSION)
        writer.putOpaque2(state.draft.encodeToByteArray())
        writer.putUint64(state.lastReadCursor)
        return writer.toByteArray()
    }

    /** Blank on anything unreadable: a lost draft must not cost the room. */
    fun decode(bytes: ByteArray): CordnRoomState =
        try {
            val reader = TlsReader(bytes)
            if (reader.readUint16() != VERSION) {
                CordnRoomState()
            } else {
                CordnRoomState(reader.readOpaque2().decodeToString(), reader.readUint64())
            }
        } catch (e: Exception) {
            CordnRoomState()
        }
}

/**
 * The on-disk layout of an [EchoState].
 *
 * ```
 * version:u16
 * pending_commits: vector2 of { sealed:opaque2, applied:u8 }
 * own_cursors: vector2 of u64
 * ```
 *
 * Empty on anything unreadable, which is the safe direction: losing the record
 * costs a client one run of reporting its own traffic as a gap, while
 * accepting a half-decoded one could let it skip somebody else's message as
 * though it were its own.
 */
object EchoStateCodec {
    const val VERSION = 1

    fun encode(state: EchoState): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(VERSION)

        val commits = TlsWriter()
        state.pendingCommits.forEach {
            commits.putOpaque2(it.sealedBase64.encodeToByteArray())
            commits.putUint8(if (it.localStateApplied) 1 else 0)
        }
        writer.putOpaque2(commits.toByteArray())

        val cursors = TlsWriter()
        state.ownMessageCursors.forEach { cursors.putUint64(it) }
        writer.putOpaque2(cursors.toByteArray())

        return writer.toByteArray()
    }

    fun decode(bytes: ByteArray): EchoState =
        try {
            val reader = TlsReader(bytes)
            if (reader.readUint16() != VERSION) {
                EchoState()
            } else {
                val commits = TlsReader(reader.readOpaque2())
                val pending = mutableListOf<PendingEpochOperation>()
                while (commits.hasRemaining) {
                    val sealed = commits.readOpaque2().decodeToString()
                    pending += PendingEpochOperation(sealed, commits.readUint8() == 1)
                }

                val cursors = TlsReader(reader.readOpaque2())
                val own = mutableListOf<Long>()
                while (cursors.hasRemaining) {
                    own += cursors.readUint64()
                }

                EchoState(pending, own)
            }
        } catch (e: Exception) {
            EchoState()
        }
}

/** A [CordnGroupStore] that keeps everything in memory. Tests, and nothing else. */
class InMemoryCordnGroupStore : CordnGroupStore {
    private val groups = mutableMapOf<String, ByteArray>()
    private val cursors = mutableMapOf<String, GroupCursor>()
    private val viaRequest = mutableSetOf<String>()
    private val roomStates = mutableMapOf<String, CordnRoomState>()
    private val echoStates = mutableMapOf<String, EchoState>()
    private val messages = mutableMapOf<String, MutableList<CordnDeliveredMessage>>()

    override suspend fun saveGroup(
        gid: String,
        state: ByteArray,
    ) {
        groups[gid] = state
    }

    override suspend fun loadGroup(gid: String): ByteArray? = groups[gid]

    override suspend fun deleteGroup(gid: String) {
        groups.remove(gid)
        cursors.remove(gid)
        viaRequest.remove(gid)
        roomStates.remove(gid)
        echoStates.remove(gid)
        // Leaving a group that left its history behind would keep the plaintext
        // of an end-to-end encrypted conversation on disk after the one thing
        // that could read it was thrown away.
        messages.remove(gid)
    }

    override suspend fun listGroups(): List<String> = groups.keys.toList()

    override suspend fun saveJoinedViaRequest(gid: String) {
        viaRequest += gid
    }

    override suspend fun loadJoinedViaRequest(gid: String): Boolean = gid in viaRequest

    override suspend fun saveRoomState(
        gid: String,
        state: CordnRoomState,
    ) {
        roomStates[gid] = state
    }

    override suspend fun loadRoomState(gid: String): CordnRoomState = roomStates[gid] ?: CordnRoomState()

    override suspend fun appendMessage(
        gid: String,
        message: CordnDeliveredMessage,
    ) {
        val log = messages.getOrPut(gid) { mutableListOf() }
        if (log.none { it.envelope.id == message.envelope.id }) log.add(message)
    }

    override suspend fun loadMessages(gid: String): List<CordnDeliveredMessage> = messages[gid]?.toList() ?: emptyList()

    override suspend fun loadMessageSummary(gid: String): CordnMessageSummary? = messages[gid]?.lastOrNull()?.let { CordnMessageSummary(it, messages[gid]?.size ?: 0) }

    override suspend fun saveEchoState(
        gid: String,
        state: EchoState,
    ) {
        echoStates[gid] = state
    }

    override suspend fun loadEchoState(gid: String): EchoState = echoStates[gid] ?: EchoState()

    override suspend fun saveCursor(
        gid: String,
        cursor: GroupCursor,
    ) {
        cursors[gid] = cursor
    }

    override suspend fun loadCursor(gid: String): GroupCursor? = cursors[gid]
}
