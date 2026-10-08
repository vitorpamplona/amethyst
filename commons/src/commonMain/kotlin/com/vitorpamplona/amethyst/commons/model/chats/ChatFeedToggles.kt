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
package com.vitorpamplona.amethyst.commons.model.chats

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.commons.model.privateChats.DM_CHAT_FEED_TYPES
import com.vitorpamplona.amethyst.commons.model.privateChats.chatFeedType
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * The single owner of one account's Settings › Messages load toggles. Everything that loads, shows or
 * notifies a chat type asks [isEnabled] here — never the raw [AccountSettings.enabledChatFeeds] — and
 * rebuilds off [applied], so a flip is applied in one order:
 *
 *  1. **The gate moves.** [isEnabled] answers the new value at once: a protocol turned off stops
 *     entering rooms, paging and notifications; one turned on starts entering before the cache is
 *     re-indexed, so nothing that arrives in between is lost.
 *  2. **The rooms catch up.** A DM protocol turned off is unloaded: its messages leave every room, its
 *     history paging is forgotten (so turning it back on pages from the top instead of trusting
 *     cursors for messages that are gone) and [onDmProtocolOff] runs. One turned on is re-indexed from
 *     what the cache still holds — those notes won't come through the new-event stream again.
 *  3. **[applied] publishes the new set.** Subscriptions, pagers and feeds rebuild off it, so none of
 *     them sees a half-applied toggle.
 *
 * Because the gate only moves inside [apply], a quick off-and-on that the collector sees as no change
 * never closed it, so nothing was dropped in between.
 */
class ChatFeedToggles(
    private val settings: AccountSettings,
    private val chatroomList: ChatroomList,
    /** The rumor a draft wrap stands for, when it is already decrypted. */
    private val draftRumor: (DraftWrapEvent) -> Event?,
    /** The cached notes matching a predicate; re-indexing walks the cache once per protocol turned on. */
    private val findCachedNotes: ((Note) -> Boolean) -> List<Note>,
    /** Routes one cached draft back through the account's draft intake (DM messages are re-added in bulk). */
    private val reindexDraft: suspend (Note) -> Unit,
    /** Extra cleanup once a DM protocol is off, e.g. ending a call that rode it. */
    private val onDmProtocolOff: suspend (ChatFeedType) -> Unit = {},
) {
    @Volatile
    private var gate: Set<ChatFeedType> = settings.enabledChatFeeds.value

    // The room side of the gate: a DM is checked and added under it ([admit]) and an unload clears the
    // rooms under it, so a message that passed the gate just before it closed can't land after the unload.
    private val roomLock = KmpLock()

    private val appliedState = MutableStateFlow(gate)

    /** The enabled chat types, published once the rooms have caught up with them (step 3). */
    val applied: StateFlow<Set<ChatFeedType>> = appliedState

    /** Whether [type] may load, show and notify right now. */
    fun isEnabled(type: ChatFeedType): Boolean = type in gate

    /** Whether this DM's protocol is on; a message of an unknown kind is not ours to block. */
    fun isEnabled(event: ChatroomKeyable): Boolean = event.chatFeedType()?.let { isEnabled(it) } ?: true

    /** Runs [add] (putting [event] in its room) only if its protocol is on, atomically with an unload. */
    fun admit(
        event: ChatroomKeyable,
        add: () -> Unit,
    ): Boolean =
        roomLock.withLock {
            if (isEnabled(event)) {
                add()
                true
            } else {
                false
            }
        }

    /**
     * NIP-AC calls signal through NIP-17's gift-wrap inbox (kind 21059), so turning NIP-17 off turns
     * voice and video calls off too, whatever [AccountSettings.callsEnabled] says.
     */
    fun isCallingActive(): Boolean = settings.callsEnabled.value && isEnabled(ChatFeedType.NIP17)

    /** [isCallingActive] as a flow, for screens that show or hide call controls. */
    val callingActive: Flow<Boolean> =
        combine(settings.callsEnabled, applied) { calls, feeds -> calls && ChatFeedType.NIP17 in feeds }.distinctUntilChanged()

    /** The DM protocol a room message belongs to, or null if it is not a DM. Drafts count by their rumor. */
    fun dmChatFeedTypeOf(note: Note): ChatFeedType? =
        when (val event = note.event) {
            is ChatroomKeyable -> event.chatFeedType()
            is DraftWrapEvent -> (draftRumor(event) as? ChatroomKeyable)?.chatFeedType()
            else -> null
        }

    /**
     * Applies the setting as it changes, for as long as [scope] lives. First clears any DM protocol that
     * starts off: the rooms are shared per pubkey in the cache, so an earlier account object for the same
     * key may have filled them with it.
     */
    fun start(scope: CoroutineScope): Job =
        scope.launch(Dispatchers.IO) {
            DM_CHAT_FEED_TYPES.forEach { if (it !in gate) unload(it) }
            settings.enabledChatFeeds.collect { apply(it) }
        }

    /** Steps 1–3 for one new value of the setting. */
    suspend fun apply(enabled: Set<ChatFeedType>) {
        val previous = gate
        gate = enabled
        DM_CHAT_FEED_TYPES.forEach { type ->
            if (type in previous && type !in enabled) unload(type)
            if (type !in previous && type in enabled) reload(type)
        }
        appliedState.value = enabled
    }

    private suspend fun unload(type: ChatFeedType) {
        roomLock.withLock { chatroomList.removeMessagesIf { dmChatFeedTypeOf(it) == type } }
        when (type) {
            ChatFeedType.NIP04 -> chatroomList.resetNip04History()
            ChatFeedType.NIP17 -> chatroomList.giftWrapHistory.reset()
            else -> {}
        }
        onDmProtocolOff(type)
    }

    // Messages go back into their rooms in one batch per room; drafts take the draft intake.
    private suspend fun reload(type: ChatFeedType) {
        val (drafts, messages) = findCachedNotes { dmChatFeedTypeOf(it) == type }.partition { it.event is DraftWrapEvent }
        roomLock.withLock { chatroomList.addAll(messages) }
        drafts.forEach { reindexDraft(it) }
    }
}
