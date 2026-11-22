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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/**
 * Tracks all Marmot MLS group chatrooms for an account.
 * Follows the same pattern as [com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList].
 */
class MarmotGroupList(
    val ownerPubKey: HexKey,
) {
    var rooms = LargeCache<HexKey, MarmotGroupChatroom>()
        private set

    private val noteToGroupIndex = LargeCache<HexKey, HexKey>()

    // Group + message id, for every message a group admin removed (kind 4891). A removal can
    // decrypt before its target: a catch-up returns newest first, and both sit in one epoch.
    // Unlike a kind-5 there is no LocalCache deletion index behind it, so without this record a
    // target arriving second would be added and stay, restarts included. Keyed by both, so an
    // admin of one group can neither remove a message of another nor overwrite its record.
    private val adminRemovedIds = LargeCache<String, Boolean>()

    // Group + report id, for every report a group admin dismissed (kind 1985), for the same
    // reason: the dismissal can decrypt before the report it names.
    private val dismissedReportIds = LargeCache<String, Boolean>()

    private fun moderationKey(
        nostrGroupId: HexKey,
        id: HexKey,
    ) = nostrGroupId + id

    private val _groupListChanges = MutableSharedFlow<HexKey>(0, 20, BufferOverflow.DROP_OLDEST)
    val groupListChanges = _groupListChanges

    fun getOrCreateGroup(nostrGroupId: HexKey): MarmotGroupChatroom = rooms.getOrCreate(nostrGroupId) { MarmotGroupChatroom(nostrGroupId) }

    /**
     * Every group, newest activity first, split into Known and New Requests
     * ([MarmotGroupChatroom.isKnown]), recomputed on every group change and every change of
     * [follows]. A reply flips [MarmotGroupChatroom.ownerSentMessage] without changing which
     * rooms exist, so a screen that cached the split on the room list never moved the group out
     * of New Requests. [extraGroupIds] adds groups the MLS state holds but no message reached yet.
     */
    fun knownSplit(
        follows: Flow<Set<HexKey>>,
        extraGroupIds: () -> Collection<HexKey> = { emptyList() },
    ): Flow<MarmotGroupSplit> =
        combine(groupListChanges.onStart { emit("") }, follows) { _, following ->
            val groups = ArrayList<Pair<HexKey, MarmotGroupChatroom>>()
            rooms.forEach { key, chatroom -> groups.add(key to chatroom) }
            extraGroupIds().forEach { id -> if (groups.none { it.first == id }) groups.add(id to getOrCreateGroup(id)) }
            groups.sortByDescending { it.second.newestMessage?.createdAt() ?: 0L }
            val (known, requests) = groups.partition { it.second.isKnown(following) }
            MarmotGroupSplit(known, requests)
        }

    fun addMessage(
        nostrGroupId: HexKey,
        msg: Note,
    ) {
        if (!isDisplayableFeedMessage(msg)) return
        // An admin deletion applies to chat messages only (`features/content-moderation.md`).
        if (msg.event?.kind == ChatEvent.KIND && isAdminRemoved(nostrGroupId, msg.idHex)) return
        val chatroom = getOrCreateGroup(nostrGroupId)
        if (chatroom.addMessageSync(msg)) {
            noteToGroupIndex.getOrCreate(msg.idHex) { nostrGroupId }
            if (msg.author?.pubkeyHex == ownerPubKey) {
                chatroom.ownerSentMessage = true
            }
            _groupListChanges.tryEmit(nostrGroupId)
        }
    }

    fun groupIdForNote(noteId: HexKey): HexKey? = noteToGroupIndex.get(noteId)

    /**
     * The messages of [nostrGroupId] that [deletion] (a kind:5 inner event) takes down: the ones
     * this group shows and its sender wrote. Read before the deletion is applied, while they are
     * still here; hand the result to [leaveDeletedPlaceholders] afterwards.
     */
    fun messagesDeletedBy(
        nostrGroupId: HexKey,
        deletion: Event,
    ): List<Note> {
        if (deletion.kind != DELETION_KIND) return emptyList()
        val room = rooms.get(nostrGroupId) ?: return emptyList()
        val targets = deletion.tags.mapNotNullTo(HashSet()) { if (it.size > 1 && it[0] == "e") it[1] else null }
        if (targets.isEmpty()) return emptyList()
        return room.messages.filter { it.idHex in targets && it.author?.pubkeyHex == deletion.pubKey && it.event != null }
    }

    /**
     * Puts a [MarmotDeletedMessageNote] where each of [deleted] stood, once the deletion took it
     * out of [nostrGroupId]. One that is still there (the cache refused the deletion) is left alone.
     */
    fun leaveDeletedPlaceholders(
        nostrGroupId: HexKey,
        deleted: List<Note>,
    ) {
        if (deleted.isEmpty()) return
        val room = getOrCreateGroup(nostrGroupId)
        var changed = false
        deleted.forEach { gone ->
            if (gone in room.messages) return@forEach
            val createdAt = gone.event?.createdAt ?: gone.createdAt() ?: return@forEach
            if (room.addMessageSync(MarmotDeletedMessageNote(gone.idHex, gone.author, createdAt))) changed = true
        }
        if (changed) _groupListChanges.tryEmit(nostrGroupId)
    }

    /** True when [note] is one of [nostrGroupId]'s messages. */
    fun isInGroup(
        nostrGroupId: HexKey,
        note: Note,
    ): Boolean {
        val room = rooms.get(nostrGroupId) ?: return false
        return note.inGatherers?.any { it === room } == true
    }

    /**
     * Mark a group as "known" by the local user — used right after the user
     * creates a group, so the creator doesn't appear under "New Requests"
     * until they post their first message.
     */
    fun markAsKnown(nostrGroupId: HexKey) {
        val chatroom = getOrCreateGroup(nostrGroupId)
        if (!chatroom.ownerSentMessage) {
            chatroom.ownerSentMessage = true
            _groupListChanges.tryEmit(nostrGroupId)
        }
    }

    /**
     * Notify subscribers that a group's state has changed (e.g., the invitee
     * just joined via a Welcome and metadata was synced into the chatroom).
     * Used when no message addition occurs but the list UI should refresh.
     */
    fun notifyGroupChanged(nostrGroupId: HexKey) {
        _groupListChanges.tryEmit(nostrGroupId)
    }

    /**
     * Whether the local user has ever sent a message in this group (or
     * explicitly created it). Mirrors `ChatroomList.hasSentMessagesTo` for
     * private DMs.
     */
    fun hasSentMessagesTo(nostrGroupId: HexKey): Boolean = rooms.get(nostrGroupId)?.ownerSentMessage == true

    fun removeMessage(
        nostrGroupId: HexKey,
        msg: Note,
    ) {
        val chatroom = getOrCreateGroup(nostrGroupId)
        if (chatroom.removeMessageSync(msg)) {
            _groupListChanges.tryEmit(nostrGroupId)
        }
    }

    /**
     * Applies an admin removal of [targetId] in [nostrGroupId]: drops [target] if it is one of
     * this group's messages, and keeps the removal so the message is refused if it arrives later.
     */
    fun applyAdminRemoval(
        nostrGroupId: HexKey,
        targetId: HexKey,
        target: Note?,
    ) {
        adminRemovedIds.put(moderationKey(nostrGroupId, targetId), true)
        if (target != null && target.event?.kind == ChatEvent.KIND && isInGroup(nostrGroupId, target)) removeMessage(nostrGroupId, target)
    }

    /**
     * Records that an admin of [nostrGroupId] dismissed the report [reportId]. Kept because a
     * dismissal can arrive before its report, which must then stay dismissed when it lands.
     */
    fun applyReportDismissal(
        nostrGroupId: HexKey,
        reportId: HexKey,
    ) {
        dismissedReportIds.put(moderationKey(nostrGroupId, reportId), true)
    }

    /** True when an admin of [nostrGroupId] dismissed the report [reportId]. */
    fun isReportDismissed(
        nostrGroupId: HexKey,
        reportId: HexKey,
    ): Boolean = dismissedReportIds.get(moderationKey(nostrGroupId, reportId)) == true

    /** True when an admin of [nostrGroupId] removed the message [noteId]. */
    fun isAdminRemoved(
        nostrGroupId: HexKey,
        noteId: HexKey,
    ): Boolean = adminRemovedIds.get(moderationKey(nostrGroupId, noteId)) == true

    /**
     * Drop a group from the in-memory list. Also clears the chatroom's own
     * message set and the note→group index: LocalCache holds notes weakly, so
     * once these strong references go away the decrypted inner messages
     * become eligible for GC and stop appearing in the Notification feed.
     */
    fun removeGroup(nostrGroupId: HexKey) {
        val chatroom = rooms.remove(nostrGroupId)
        chatroom?.clearAllMessagesSync()?.forEach { noteToGroupIndex.remove(it.idHex) }
        forgetModeration(adminRemovedIds, nostrGroupId)
        forgetModeration(dismissedReportIds, nostrGroupId)
        _groupListChanges.tryEmit(nostrGroupId)
    }

    private fun forgetModeration(
        records: LargeCache<String, Boolean>,
        nostrGroupId: HexKey,
    ) {
        val keys = mutableListOf<String>()
        records.forEach { key, _ -> if (key.startsWith(nostrGroupId)) keys.add(key) }
        keys.forEach { records.remove(it) }
    }

    fun allGroupIds(): List<HexKey> {
        val result = mutableListOf<HexKey>()
        rooms.forEach { key, _ -> result.add(key) }
        return result
    }

    /**
     * True if this inner event should appear as its own row in the group chat
     * feed: Marmot's timeline rule ([MarmotAppEvent.isTimelineRow]), which hides
     * the kinds that change or annotate another message (reactions, deletions,
     * edits, votes, ...) and shows a kind:1210 system row only when this client
     * derived it. Hidden kinds are still consumed into LocalCache, where they
     * drive the target note's reaction row, edit overlay and tally.
     *
     * The check has to live here rather than at ingest because rows reach the
     * feed by two routes — live decryption and the restart re-read of the
     * local log — and the log holds received payloads too.
     */
    private fun isDisplayableFeedMessage(msg: Note): Boolean {
        // Every path here indexes the decrypted inner event onto its note first, so a note
        // without one can't be classified and would only render as a "can't be found" row.
        val event = msg.event ?: return false
        return MarmotAppEvent.isTimelineRow(event.kind, event.pubKey, ownerPubKey)
    }
}

private const val DELETION_KIND = 5

/** [MarmotGroupList.knownSplit]: the groups under Known and under New Requests, newest first. */
data class MarmotGroupSplit(
    val known: List<Pair<HexKey, MarmotGroupChatroom>>,
    val requests: List<Pair<HexKey, MarmotGroupChatroom>>,
)
