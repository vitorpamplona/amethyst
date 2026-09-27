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
package com.vitorpamplona.quartz.cordn.sync

import com.vitorpamplona.quartz.cordn.spec00Coordinator.GroupMessage

/**
 * Where a group's catch-up has reached on one coordinator.
 *
 * A cursor is coordinator-local and group-scoped (`spec/00.md` §4): it orders
 * this group's stream on this coordinator and means nothing anywhere else.
 * Persist it per (coordinator, gid) pair and never treat it as a message
 * identity — the canonical id is the envelope's (`spec/02.md` §7).
 */
data class GroupCursor(
    /**
     * The value to pass as `after` on the next fetch.
     *
     * Advances past every message the stream delivers, INCLUDING ones we could
     * not or chose not to process. That is not sloppiness: a message sealed
     * under an epoch we never had is undecryptable forever, so a cursor that
     * refused to move past it would re-fetch it on every catch-up and never
     * reach the messages behind it.
     */
    val fetchCursor: Long = 0,
    /** The highest cursor ever seen, which only ever moves forward. */
    val lastCursor: Long = 0,
) {
    fun advancedTo(cursor: Long) = GroupCursor(fetchCursor = cursor, lastCursor = maxOf(lastCursor, cursor))

    /** What to send as `after`, or null on a first-ever fetch. */
    fun afterOrNull(): Long? = fetchCursor.takeIf { it > 0 }
}

/**
 * What to do with one inbound message.
 *
 * The decision is separated from the doing because it is the part that is easy
 * to get wrong and hard to notice: every branch advances the cursor, and the
 * difference between them is only whether MLS state moves.
 */
sealed interface Ingestion {
    /** The cursor to advance to, whatever the outcome. */
    val cursor: Long

    /**
     * Our own Commit coming back, already applied locally when we posted it.
     *
     * Skip it. Feeding our own Commit back through the engine would advance
     * the epoch a second time and desynchronize us from every other member —
     * the failure looks like "my messages stopped decrypting" several epochs
     * later, with nothing pointing at the cause.
     */
    data class SelfEchoConfirmed(
        override val cursor: Long,
        val sealedBase64: String,
    ) : Ingestion

    /**
     * Our own Commit coming back, NOT yet applied locally.
     *
     * Process it. This is the crash-recovery case: we posted, the coordinator
     * accepted, and we died before adopting the new epoch. The echo is the only
     * copy of that Commit we will ever be offered.
     */
    data class SelfEchoUnapplied(
        override val cursor: Long,
        val sealedBase64: String,
    ) : Ingestion

    /** An application message we sent. Already in our own history. */
    data class OwnMessage(
        override val cursor: Long,
    ) : Ingestion

    /** Someone else's traffic. Hand it to MLS. */
    data class Process(
        override val cursor: Long,
        val sealedBase64: String,
    ) : Ingestion
}

/**
 * A Commit we have posted and are waiting to see come back.
 *
 * Keyed by the exact sealed base64, which is what makes the match reliable:
 * `spec/03.md` §4 requires a fresh random nonce per payload, so the sealed form
 * of a Commit is unique to the one posting of it. Matching on cursor instead
 * would break the moment a coordinator renumbered, and matching on the
 * plaintext would need us to decrypt our own traffic to recognise it.
 */
data class PendingEpochOperation(
    val sealedBase64: String,
    /**
     * False when we posted but had not yet adopted the post-Commit state —
     * the case [Ingestion.SelfEchoUnapplied] exists for.
     */
    val localStateApplied: Boolean = true,
)

/**
 * Per-group ingestion state: the cursor, our pending Commits, and the cursors
 * of application messages we sent.
 *
 * One path for catch-up and live delivery, as the reference requires: a message
 * arriving over `msg_sub_many` and the same message arriving over
 * `msg_fetch_many` must be treated identically, or a client that reconnects
 * mid-stream processes something twice.
 */
class GroupInbox(
    var cursor: GroupCursor = GroupCursor(),
    echoes: EchoState = EchoState(),
) {
    private val pendingOperations = echoes.pendingCommits.associateByTo(mutableMapOf()) { it.sealedBase64 }
    private val ownMessageCursors = echoes.ownMessageCursors.toMutableSet()

    /** Records a Commit we just posted, so its echo is recognised. */
    fun expectEcho(operation: PendingEpochOperation) {
        pendingOperations[operation.sealedBase64] = operation
    }

    /** Records an application message we sent, by the cursor the coordinator gave it. */
    fun recordOwnMessage(cursor: Long) {
        ownMessageCursors += cursor
    }

    /** Pending Commits still unconfirmed. */
    fun pending(): List<PendingEpochOperation> = pendingOperations.values.toList()

    /**
     * Classifies [message] and advances the cursor.
     *
     * Advancing here rather than at each call site is the whole point: three of
     * the four outcomes do nothing else, and a caller that forgot one would
     * silently re-fetch forever.
     */
    fun accept(message: GroupMessage): Ingestion {
        cursor = cursor.advancedTo(message.cursor)

        val pending = pendingOperations[message.sealedBase64]
        if (pending != null) {
            pendingOperations.remove(message.sealedBase64)
            return if (pending.localStateApplied) {
                Ingestion.SelfEchoConfirmed(message.cursor, message.sealedBase64)
            } else {
                Ingestion.SelfEchoUnapplied(message.cursor, message.sealedBase64)
            }
        }

        if (ownMessageCursors.remove(message.cursor)) {
            return Ingestion.OwnMessage(message.cursor)
        }

        return Ingestion.Process(message.cursor, message.sealedBase64)
    }

    /**
     * Advances past a message MLS could not process.
     *
     * Undecryptable is not always an error worth stopping for: a message from
     * an epoch we joined after, or one from a generation whose ratchet has
     * moved on, is permanently unreadable and will be just as unreadable next
     * time. The cursor has already advanced in [accept]; this exists so the
     * intent is written down at the call site rather than inferred from its
     * absence.
     */
    fun skipUnprocessable(cursor: Long) {
        this.cursor = this.cursor.advancedTo(cursor)
    }

    /**
     * The bookkeeping to persist beside [cursor].
     *
     * Own-message cursors at or below the fetch cursor are dropped: the stream
     * has already delivered them, so they can never come round again, and
     * keeping them would grow the record for the life of the group.
     *
     * Pending Commits are kept until their echo matches, however long that
     * takes, because the echo is the only copy that will ever be offered and
     * [Ingestion.SelfEchoUnapplied] is how a client that died mid-post applies
     * its own Commit.
     */
    fun echoes(): EchoState =
        EchoState(
            pendingCommits = pendingOperations.values.toList(),
            ownMessageCursors = ownMessageCursors.filter { it > cursor.fetchCursor }.sorted(),
        )
}

/**
 * The part of a [GroupInbox] that has to outlive the process.
 *
 * Found by running against a real coordinator, not by reading. Posting
 * deliberately does not advance the cursor — a lower cursor may still hold
 * somebody else's unprocessed message — so a client that exits between
 * posting and ingesting comes back with a cursor that will re-read its own
 * traffic. Without this record it cannot tell that it is its own: its Commit
 * was sealed under an epoch key it has since left, and its message came from a
 * ratchet generation already consumed, so both arrive as
 * [Ingestion.Process] and fail to open.
 *
 * The visible damage is a gap in the sender's own conversation. The worse,
 * narrower damage is that [Ingestion.SelfEchoUnapplied] — the recovery path
 * for a client that posted a Commit and died before adopting it — depends on
 * exactly the record that dying used to destroy.
 *
 * Every client needs this, not only a process-per-command one: a phone killed
 * between sending a message and syncing is the ordinary case, not a corner.
 */
data class EchoState(
    val pendingCommits: List<PendingEpochOperation> = emptyList(),
    val ownMessageCursors: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = pendingCommits.isEmpty() && ownMessageCursors.isEmpty()
}
