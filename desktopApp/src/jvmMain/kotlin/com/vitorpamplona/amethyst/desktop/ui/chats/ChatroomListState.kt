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
package com.vitorpamplona.amethyst.desktop.ui.chats

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Represents a conversation entry in the list pane.
 */
@Stable
data class ConversationItem(
    val roomKey: ChatroomKey,
    val chatroom: Chatroom,
    val users: List<User>,
    val displayName: String,
    val lastMessagePreview: String,
    val lastMessageTimestamp: Long,
    val isGroup: Boolean,
    val hasUnread: Boolean,
)

/**
 * Tab selection for the conversation list.
 */
enum class ConversationTab {
    KNOWN,
    NEW,
}

/**
 * Manages conversation list state for the desktop DM screen.
 *
 * Derives known/new rooms from IAccount.chatroomList:
 * - Known: rooms where the user has sent a message (ownerSentMessage = true)
 * - New: rooms where the user has NOT sent a message (incoming from unknown)
 *
 * Provides selectedTab and selectedRoom StateFlows that drive the UI.
 */
@Stable
class ChatroomListState(
    private val account: IAccount,
    private val cacheProvider: ICacheProvider,
    private val relayManager: RelayConnectionManager,
    private val localCache: DesktopLocalCache,
    private val scope: CoroutineScope,
) {
    private val _selectedTab = MutableStateFlow(ConversationTab.KNOWN)
    val selectedTab: StateFlow<ConversationTab> = _selectedTab.asStateFlow()

    private val _selectedRoom = MutableStateFlow<ChatroomKey?>(null)
    val selectedRoom: StateFlow<ChatroomKey?> = _selectedRoom.asStateFlow()

    private val _knownRooms = MutableStateFlow<List<ConversationItem>>(emptyList())
    val knownRooms: StateFlow<List<ConversationItem>> = _knownRooms.asStateFlow()

    private val _newRooms = MutableStateFlow<List<ConversationItem>>(emptyList())
    val newRooms: StateFlow<List<ConversationItem>> = _newRooms.asStateFlow()

    // Cache decrypted NIP-04 content by event id to avoid re-decrypting every poll
    private val decryptedContentCache = mutableMapOf<String, String>()

    // Track pubkeys we've already requested metadata for
    private val fetchedMetadataKeys = mutableSetOf<String>()

    // Track pubkeys whose NIP-17 DM relay list (kind 10050) we've already
    // prewarmed, so the 2s refresh loop only kicks off one fetch per peer.
    private val prewarmedDmRelayKeys = mutableSetOf<String>()

    // Bound concurrent kind:10050 lookups so a large conversation list doesn't
    // flood the curated indexer relays with a burst of parallel fan-outs.
    private val dmRelayPrewarmLimiter = Semaphore(4)

    // Timestamp (createdAt) of the newest message seen by the user per room, set when a room is
    // opened. A room is unread when its newest incoming message is newer than this mark.
    private val lastSeen = mutableMapOf<ChatroomKey, Long>()

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshRooms()
                delay(2000)
            }
        }
    }

    fun selectTab(tab: ConversationTab) {
        _selectedTab.value = tab
    }

    fun selectRoom(roomKey: ChatroomKey) {
        _selectedRoom.value = roomKey
        // Mark everything currently in the room as seen so it stops showing as unread.
        val newest =
            account.chatroomList.rooms
                .get(roomKey)
                ?.newestMessage
                ?.createdAt() ?: 0L
        lastSeen[roomKey] = maxOf(lastSeen[roomKey] ?: 0L, newest)
        scope.launch(Dispatchers.IO) { refreshRooms() }
    }

    fun clearSelection() {
        _selectedRoom.value = null
    }

    private suspend fun decryptPreview(event: com.vitorpamplona.quartz.nip01Core.core.Event?): String {
        if (event == null) return ""
        return when (event) {
            is PrivateDmEvent -> {
                decryptedContentCache.getOrPut(event.id) {
                    try {
                        event.decryptContent(account.signer)
                    } catch (_: Exception) {
                        event.content
                    }
                }
            }

            else -> {
                event.content
            }
        }.take(80)
    }

    private var metadataSubCounter = 0

    private fun fetchMetadataIfNeeded(pubkeys: List<String>) {
        val needed = pubkeys.filter { it !in fetchedMetadataKeys && it.length == 64 }
        if (needed.isEmpty()) return

        val relays = relayManager.relayStatuses.value.keys
        if (relays.isEmpty()) return

        fetchedMetadataKeys.addAll(needed)

        val subId = "dm-meta-${metadataSubCounter++}"
        val filter =
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = needed,
                limit = needed.size,
            )

        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event is MetadataEvent) {
                        localCache.consumeMetadata(event)
                    }
                }
            }

        relayManager.subscribe(subId, listOf(filter), relays, listener)

        // Auto-close after 15s — enough time for all relays to respond
        scope.launch {
            delay(15_000)
            relayManager.unsubscribe(subId)
        }
    }

    /**
     * Proactively download every conversation peer's NIP-17 DM relay list
     * (kind 10050) as soon as their room shows up in the list, rather than
     * waiting until the user opens the room or hits send.
     *
     * NIP-17 gift wraps (both messages and reactions) MUST be delivered to the
     * recipient's kind:10050 relays; the desktop send path resolves those via
     * [DesktopIAccount.dmInboxResolver]. Warming the resolver here means the
     * first send/reaction resolves from cache instead of paying an indexer
     * round-trip, and the composer's "recipient has no DM relays" gate settles
     * before the user has typed anything.
     *
     * The resolver already dedupes and caches, but we also keep our own
     * [prewarmedDmRelayKeys] guard so the 2s refresh loop launches at most one
     * fetch per peer, and cap concurrency via [dmRelayPrewarmLimiter] so a big
     * conversation list doesn't fan out to the indexers all at once.
     */
    private fun prewarmDmRelayLists(pubkeys: Collection<String>) {
        val resolver = (account as? DesktopIAccount)?.dmInboxResolver ?: return
        val needed = pubkeys.filter { it.length == 64 && prewarmedDmRelayKeys.add(it) }
        if (needed.isEmpty()) return

        for (pubkey in needed) {
            scope.launch(Dispatchers.IO) {
                dmRelayPrewarmLimiter.withPermit {
                    try {
                        resolver.resolve(pubkey)
                    } catch (_: Exception) {
                        // Best-effort prefetch; the send path resolves again on demand.
                    }
                }
            }
        }
    }

    private suspend fun refreshRooms() {
        val chatroomList = account.chatroomList
        val known = mutableListOf<ConversationItem>()
        val new = mutableListOf<ConversationItem>()

        // Collect room entries (forEach is non-suspend)
        val entries = mutableListOf<Pair<ChatroomKey, Chatroom>>()
        chatroomList.rooms.forEach { key, chatroom -> entries.add(key to chatroom) }

        // Collect pubkeys needing metadata across all rooms
        val pubkeysNeedingMetadata = mutableListOf<String>()

        // Collect every peer pubkey so we can proactively download their NIP-17
        // DM relay list (kind 10050) — see [prewarmDmRelayLists].
        val dmPeerPubkeys = mutableSetOf<String>()

        for ((key, chatroom) in entries) {
            // Skip rooms with no messages
            if (chatroom.messages.isEmpty()) continue

            // Hide rooms whose latest message is from a muted/blocked author or otherwise filtered.
            val newestMessage = chatroom.newestMessage
            if (newestMessage != null && !account.isAcceptable(newestMessage)) continue

            dmPeerPubkeys.addAll(key.users)

            val users = key.users.mapNotNull { cacheProvider.getUserIfExists(it) }

            // Collect pubkeys without profile info
            for (pubkey in key.users) {
                val user = cacheProvider.getUserIfExists(pubkey)
                if (user == null || user.metadataOrNull() == null) {
                    pubkeysNeedingMetadata.add(pubkey)
                }
            }

            val displayName =
                if (users.isNotEmpty()) {
                    users.joinToString(", ") { it.toBestDisplayName() }
                } else {
                    key.users
                        .firstOrNull()
                        ?.take(12)
                        ?.let { "$it..." } ?: "Unknown"
                }

            val lastPreview = decryptPreview(newestMessage?.event)
            val lastTimestamp = newestMessage?.createdAt() ?: 0L

            // Unread when the newest message is incoming (not authored by us) and newer than the
            // last time the user opened this room.
            val incoming = newestMessage != null && newestMessage.author?.pubkeyHex != account.pubKey
            val hasUnread = incoming && lastTimestamp > (lastSeen[key] ?: 0L)

            val item =
                ConversationItem(
                    roomKey = key,
                    chatroom = chatroom,
                    users = users,
                    displayName = displayName,
                    lastMessagePreview = lastPreview,
                    lastMessageTimestamp = lastTimestamp,
                    isGroup = key.users.size > 1,
                    hasUnread = hasUnread,
                )

            if (chatroom.ownerSentMessage) {
                known.add(item)
            } else {
                new.add(item)
            }
        }

        // Batch-request metadata for all users who need it
        fetchMetadataIfNeeded(pubkeysNeedingMetadata)

        // Proactively download each peer's DM relay list (kind 10050) so the
        // NIP-17 send path resolves it from cache instead of on demand.
        prewarmDmRelayLists(dmPeerPubkeys)

        // Sort by most recent message
        _knownRooms.value = known.sortedByDescending { it.lastMessageTimestamp }
        _newRooms.value = new.sortedByDescending { it.lastMessageTimestamp }
    }
}
