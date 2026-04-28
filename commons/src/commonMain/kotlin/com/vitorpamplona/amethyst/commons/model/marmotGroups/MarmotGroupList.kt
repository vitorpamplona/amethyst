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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

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

    private val _groupListChanges = MutableSharedFlow<HexKey>(0, 20, BufferOverflow.DROP_OLDEST)
    val groupListChanges = _groupListChanges

    fun getOrCreateGroup(nostrGroupId: HexKey): MarmotGroupChatroom = rooms.getOrCreate(nostrGroupId) { MarmotGroupChatroom(nostrGroupId) }

    fun addMessage(
        nostrGroupId: HexKey,
        msg: Note,
    ) {
        if (!isDisplayableFeedMessage(msg)) return
        val chatroom = getOrCreateGroup(nostrGroupId)
        val isSelfAuthored = msg.author?.pubkeyHex == ownerPubKey
        // Use the quiet path for our own messages so the relay round-trip
        // doesn't mark the user's own outgoing message as unread.
        val added =
            if (isSelfAuthored) {
                chatroom.restoreMessageSync(msg)
            } else {
                chatroom.addMessageSync(msg)
            }
        if (added) {
            noteToGroupIndex.getOrCreate(msg.idHex) { nostrGroupId }
            if (isSelfAuthored) {
                chatroom.ownerSentMessage = true
            }
            _groupListChanges.tryEmit(nostrGroupId)
        }
    }

    /**
     * Add a message that was restored from persistent storage at app startup.
     * Does not bump the chatroom's unread counter.
     */
    fun restoreMessage(
        nostrGroupId: HexKey,
        msg: Note,
    ) {
        if (!isDisplayableFeedMessage(msg)) return
        val chatroom = getOrCreateGroup(nostrGroupId)
        if (chatroom.restoreMessageSync(msg)) {
            noteToGroupIndex.getOrCreate(msg.idHex) { nostrGroupId }
            if (msg.author?.pubkeyHex == ownerPubKey) {
                chatroom.ownerSentMessage = true
            }
            _groupListChanges.tryEmit(nostrGroupId)
        }
    }

    fun groupIdForNote(noteId: HexKey): HexKey? = noteToGroupIndex.get(noteId)

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
     * Drop a group from the in-memory list. Also clears the chatroom's own
     * message set and the note→group index: LocalCache holds notes weakly, so
     * once these strong references go away the decrypted inner messages
     * become eligible for GC and stop appearing in the Notification feed.
     */
    fun removeGroup(nostrGroupId: HexKey) {
        val chatroom = rooms.remove(nostrGroupId)
        chatroom?.clearAllMessagesSync()?.forEach { noteToGroupIndex.remove(it.idHex) }
        _groupListChanges.tryEmit(nostrGroupId)
    }

    fun allGroupIds(): List<HexKey> {
        val result = mutableListOf<HexKey>()
        rooms.forEach { key, _ -> result.add(key) }
        return result
    }

    /**
     * True if this inner event should appear as its own bubble in the group
     * chat feed. Side-channel kinds (reactions, deletions) must still be
     * consumed into LocalCache — they drive the reaction row on the target
     * note and, for kind:5, revoke a prior reaction — but they must NOT show
     * up as standalone messages.
     *
     * Needed because WhiteNoise emits plain kind:7 reactions (emoji content +
     * `e` tag) and kind:5 unreacts inside kind:445, and the Marmot pipeline
     * blindly routed every inner event into the chatroom. The reaction then
     * rendered as a chat bubble containing just the emoji, with a quoted
     * citation of the target message — which reads exactly like a reply.
     */
    private fun isDisplayableFeedMessage(msg: Note): Boolean {
        val kind = msg.event?.kind ?: return true
        return kind != MARMOT_INNER_KIND_REACTION && kind != MARMOT_INNER_KIND_DELETION
    }

    companion object {
        private const val MARMOT_INNER_KIND_DELETION = 5
        private const val MARMOT_INNER_KIND_REACTION = 7
    }
}
