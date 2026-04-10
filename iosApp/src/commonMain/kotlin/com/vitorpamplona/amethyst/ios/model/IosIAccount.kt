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
package com.vitorpamplona.amethyst.ios.model

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.INwcSignerState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip57Zaps.IPrivateZapsDecryptionCache
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope

/**
 * iOS implementation of IAccount.
 *
 * Bridges the iOS AccountState.LoggedIn and IosLocalCache to the shared
 * IAccount interface used by commons ViewModels (ChatroomFeedViewModel,
 * ChatNewMessageState, etc.).
 */
class IosIAccount(
    private val accountState: AccountState.LoggedIn,
    private val localCache: IosLocalCache,
    private val relayManager: IosRelayConnectionManager,
    private val scope: CoroutineScope,
) : IAccount {
    override val signer: NostrSigner = accountState.signer

    override val pubKey: String = accountState.pubKeyHex

    override val chatroomList: ChatroomList = ChatroomList(accountState.pubKeyHex)

    override val marmotGroupList: MarmotGroupList = MarmotGroupList()

    override val showSensitiveContent: Boolean? = null
    override val hiddenWordsCase: List<DualCase> = emptyList()
    override val hiddenUsersHashCodes: Set<Int> = emptySet()
    override val spammersHashCodes: Set<Int> = emptySet()

    override val nip47SignerState: INwcSignerState =
        object : INwcSignerState {
            override suspend fun decryptResponse(event: LnZapPaymentResponseEvent): Response? = null

            override suspend fun decryptRequest(event: LnZapPaymentRequestEvent): Request? = null

            override fun isNIP47Author(pubKey: String?): Boolean = false
        }

    override val privateZapsDecryptionCache: IPrivateZapsDecryptionCache =
        object : IPrivateZapsDecryptionCache {
            override fun cachedPrivateZap(event: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null

            override suspend fun decryptPrivateZap(event: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null
        }

    override fun userProfile(): User = localCache.getOrCreateUser(pubKey)

    override fun isWriteable(): Boolean = !accountState.isReadOnly

    override fun followingKeySet(): Set<String> = localCache.followedUsers.value

    override fun isHidden(user: User): Boolean = false

    override fun isAcceptable(note: Note): Boolean {
        val event = note.event ?: return true
        return !localCache.hasBeenDeleted(event)
    }

    override suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<PrivateDmEvent>) {
        if (!isWriteable()) return

        val signedEvent = signer.sign(eventTemplate)

        // Optimistic local add
        val note = localCache.getOrCreateNote(signedEvent.id)
        val author = localCache.getOrCreateUser(signedEvent.pubKey)
        if (note.event == null) {
            note.loadEvent(signedEvent, author, emptyList())
        }
        chatroomList.addMessage(signedEvent.chatroomKey(pubKey), note)

        relayManager.broadcastToAll(signedEvent)
    }

    override suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>) {
        if (!isWriteable()) return

        val result = NIP17Factory().createMessageNIP17(template, signer)

        // Optimistic local add with the inner ChatMessageEvent
        val innerMsg = result.msg as ChatMessageEvent
        val note = localCache.getOrCreateNote(innerMsg.id)
        val author = localCache.getOrCreateUser(innerMsg.pubKey)
        if (note.event == null) {
            note.loadEvent(innerMsg, author, emptyList())
        }
        chatroomList.addMessage(innerMsg.chatroomKey(pubKey), note)

        // Broadcast all wraps
        result.wraps.forEach { wrap ->
            relayManager.broadcastToAll(wrap)
        }
    }

    override suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return

        val result = NIP17Factory().createEncryptedFileNIP17(template, signer)
        val innerMsg = result.msg as ChatMessageEncryptedFileHeaderEvent
        val note = localCache.getOrCreateNote(innerMsg.id)
        val author = localCache.getOrCreateUser(innerMsg.pubKey)
        if (note.event == null) {
            note.loadEvent(innerMsg, author, emptyList())
        }
        chatroomList.addMessage(innerMsg.chatroomKey(pubKey), note)

        result.wraps.forEach { wrap ->
            relayManager.broadcastToAll(wrap)
        }
    }

    override suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>) {
        wraps.forEach { wrap ->
            relayManager.broadcastToAll(wrap)
        }
    }
}
