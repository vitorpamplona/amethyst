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
package com.vitorpamplona.amethyst.ios.ui.chats

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Represents a conversation entry in the iOS DM list.
 */
@Stable
data class IosConversationItem(
    val roomKey: ChatroomKey,
    val chatroom: Chatroom,
    val users: List<User>,
    val displayName: String,
    val lastMessagePreview: String,
    val lastMessageTimestamp: Long,
    val hasUnread: Boolean,
)

/**
 * Manages conversation list state for the iOS DM screen.
 *
 * Periodically refreshes from IAccount.chatroomList, sorting by most recent message.
 * Also handles fetching metadata for unknown users.
 */
@Stable
class IosChatroomListState(
    private val account: IAccount,
    private val cacheProvider: ICacheProvider,
    private val relayManager: IosRelayConnectionManager,
    private val scope: CoroutineScope,
) {
    private val _conversations = MutableStateFlow<List<IosConversationItem>>(emptyList())
    val conversations: StateFlow<List<IosConversationItem>> = _conversations.asStateFlow()

    private val decryptedContentCache = mutableMapOf<String, String>()
    private val fetchedMetadataKeys = mutableSetOf<String>()
    private var metadataSubCounter = 0

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshRooms()
                delay(2000)
            }
        }
    }

    private suspend fun decryptPreview(event: Event?): String {
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

    private fun fetchMetadataIfNeeded(pubkeys: List<String>) {
        val needed = pubkeys.filter { it !in fetchedMetadataKeys && it.length == 64 }
        if (needed.isEmpty()) return

        val relays = relayManager.connectedRelays.value
        if (relays.isEmpty()) return

        fetchedMetadataKeys.addAll(needed)

        val subId = generateSubId("dm-meta-$metadataSubCounter")
        metadataSubCounter++

        relayManager.subscribe(
            subId,
            listOf(FilterBuilders.userMetadataMultiple(needed)),
            relays,
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event is com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent) {
                        (cacheProvider as? com.vitorpamplona.amethyst.ios.cache.IosLocalCache)
                            ?.consumeMetadata(event)
                    }
                }
            },
        )

        scope.launch {
            delay(15_000)
            relayManager.unsubscribe(subId)
        }
    }

    private suspend fun refreshRooms() {
        val chatroomList = account.chatroomList
        val items = mutableListOf<IosConversationItem>()
        val pubkeysNeedingMetadata = mutableListOf<String>()

        val entries = mutableListOf<Pair<ChatroomKey, Chatroom>>()
        chatroomList.rooms.forEach { key, chatroom -> entries.add(key to chatroom) }

        for ((key, chatroom) in entries) {
            if (chatroom.messages.isEmpty()) continue

            val users = key.users.mapNotNull { cacheProvider.getUserIfExists(it) }

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

            val newestMessage = chatroom.newestMessage
            val lastPreview = decryptPreview(newestMessage?.event)
            val lastTimestamp = newestMessage?.createdAt() ?: 0L

            items.add(
                IosConversationItem(
                    roomKey = key,
                    chatroom = chatroom,
                    users = users,
                    displayName = displayName,
                    lastMessagePreview = lastPreview,
                    lastMessageTimestamp = lastTimestamp,
                    hasUnread = !chatroom.ownerSentMessage && newestMessage != null,
                ),
            )
        }

        fetchMetadataIfNeeded(pubkeysNeedingMetadata)
        _conversations.value = items.sortedByDescending { it.lastMessageTimestamp }
    }
}
