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
package com.vitorpamplona.amethyst.desktop.model

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.INwcSignerState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.chats.DmSendTracker
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip57Zaps.IPrivateZapsDecryptionCache
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Desktop implementation of IAccount.
 *
 * Bridges the desktop AccountState.LoggedIn and DesktopLocalCache to the
 * shared IAccount interface used by commons ViewModels (ChatroomFeedViewModel,
 * ChatNewMessageState, etc.).
 *
 * Bridges the desktop relay client for DM sending (NIP-04 and NIP-17).
 */
class DesktopIAccount(
    private val accountState: AccountState.LoggedIn,
    private val localCache: DesktopLocalCache,
    private val relayManager: RelayConnectionManager,
    val dmSendTracker: DmSendTracker,
    private val scope: CoroutineScope,
) : IAccount {
    override val signer: NostrSigner = accountState.signer

    override val pubKey: String = accountState.pubKeyHex

    override val showSensitiveContent: Boolean? = null

    override val hiddenWordsCase: List<DualCase> = emptyList()

    override val hiddenUsersHashCodes: Set<Int> = emptySet()

    override val spammersHashCodes: Set<Int> = emptySet()

    override val chatroomList: ChatroomList = ChatroomList(accountState.pubKeyHex)

    override val nip47SignerState: INwcSignerState =
        object : INwcSignerState {
            override suspend fun decryptResponse(event: LnZapPaymentResponseEvent): Response? = null

            override suspend fun decryptRequest(event: LnZapPaymentRequestEvent): Request? = null

            override fun isNIP47Author(pubKey: String?): Boolean = false
        }

    override val privateZapsDecryptionCache: IPrivateZapsDecryptionCache =
        object : IPrivateZapsDecryptionCache {
            override fun cachedPrivateZap(zapRequest: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null

            override suspend fun decryptPrivateZap(zapRequest: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null
        }

    override fun userProfile(): User = localCache.getOrCreateUser(pubKey)

    override fun isWriteable(): Boolean = !accountState.isReadOnly

    override fun followingKeySet(): Set<String> = emptySet()

    override fun isHidden(user: User): Boolean = false

    override fun isAcceptable(note: Note): Boolean {
        // Accept all notes on desktop for now
        val event = note.event ?: return true
        return !localCache.hasBeenDeleted(event)
    }

    override suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<PrivateDmEvent>) {
        if (!isWriteable()) return

        val signedEvent = signer.sign<PrivateDmEvent>(eventTemplate)
        val recipient = signedEvent.verifiedRecipientPubKey()

        // Optimistic local add so the message appears immediately
        addEventToChatroom(signedEvent, signedEvent.chatroomKey(pubKey))

        // Broadcast to connected relays + recipient's DM inbox relays
        val targetRelays = relayManager.connectedRelays.value.toMutableSet()
        if (recipient != null) {
            localCache.getOrCreateUser(recipient).dmInboxRelays()?.let {
                targetRelays.addAll(it)
            }
        }

        scope.launch { dmSendTracker.sendAndTrack(signedEvent, targetRelays) }
    }

    override suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>) {
        if (!isWriteable()) return

        val result = NIP17Factory().createMessageNIP17(template, signer)

        // Optimistic local add — use the inner ChatMessageEvent, not the wraps
        val innerMsg = result.msg as ChatMessageEvent
        addEventToChatroom(innerMsg, innerMsg.chatroomKey(pubKey))

        // Collect all wraps with their target relays for batch sending
        val batch =
            result.wraps.map { wrap ->
                val recipientKey = wrap.recipientPubKey()
                val targetRelays =
                    if (recipientKey != null) {
                        val dmRelays =
                            localCache
                                .getOrCreateUser(recipientKey)
                                .dmInboxRelays()
                                ?.toSet()
                        dmRelays?.ifEmpty { null }
                            ?: relayManager.connectedRelays.value
                    } else {
                        relayManager.connectedRelays.value
                    }
                wrap to targetRelays
            }

        scope.launch { dmSendTracker.sendBatch(batch) }
    }

    override suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>) {
        val batch =
            wraps.map { wrap ->
                val recipientKey = wrap.recipientPubKey()
                val targetRelays =
                    if (recipientKey != null) {
                        val dmRelays =
                            localCache
                                .getOrCreateUser(recipientKey)
                                .dmInboxRelays()
                                ?.toSet()
                        dmRelays?.ifEmpty { null }
                            ?: relayManager.connectedRelays.value
                    } else {
                        relayManager.connectedRelays.value
                    }
                wrap to targetRelays
            }

        scope.launch { dmSendTracker.sendBatch(batch) }
    }

    private fun addEventToChatroom(
        event: com.vitorpamplona.quartz.nip01Core.core.Event,
        roomKey: com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey,
    ) {
        val note = localCache.getOrCreateNote(event.id)
        val author = localCache.getOrCreateUser(event.pubKey)
        if (note.event == null) {
            note.loadEvent(event, author, emptyList())
        }
        chatroomList.addMessage(roomKey, note)
    }
}
