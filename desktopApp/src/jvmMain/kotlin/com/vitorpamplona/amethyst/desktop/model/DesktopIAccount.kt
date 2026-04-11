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

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.INwcSignerState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.Kind3FollowListRepository
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.Kind3FollowListState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.OldBookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.PinListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkListsState
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListRepository
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.chats.DmSendTracker
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
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
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
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
    override val signer: NostrSigner = NostrSignerWithClientTag(accountState.signer, CLIENT_TAG_NAME)

    override val pubKey: String = accountState.pubKeyHex

    // ----- State Classes (pin important notes via strong refs for GC retention) -----

    override val oldBookmarkState = OldBookmarkListState(signer, localCache, scope)
    override val bookmarkState = BookmarkListState(signer, localCache, scope)
    override val pinState = PinListState(signer, localCache, scope)
    override val labeledBookmarkLists = LabeledBookmarkListsState(signer, localCache, scope)
    override val emoji = EmojiPackState(signer, localCache, scope)
    override val cache: ICacheProvider = localCache
    override val settings: AccountSettings =
        AccountSettings(
            com.vitorpamplona.quartz.nip01Core.crypto
                .KeyPair(),
        )

    val kind3FollowList =
        Kind3FollowListState(
            signer,
            localCache,
            scope,
            object : Kind3FollowListRepository {
                override val backupContactList: ContactListEvent? = null

                override fun updateContactListTo(event: ContactListEvent) { /* no persistence yet */ }
            },
        )

    val nip65RelayList =
        Nip65RelayListState(
            signer,
            localCache,
            scope,
            object : Nip65RelayListRepository {
                override val backupNIP65RelayList: AdvertisedRelayListEvent? = null

                override fun updateNIP65RelayList(event: AdvertisedRelayListEvent) { /* no persistence yet */ }

                override val defaultOutboxRelays = relayManager.connectedRelays.value
                override val defaultInboxRelays = relayManager.connectedRelays.value
            },
        )

    // ---------------------------------------------------------------------------------

    override val showSensitiveContent: Boolean? = null

    override val hiddenWordsCase: List<DualCase> = emptyList()

    override val hiddenUsersHashCodes: Set<Int> = emptySet()

    override val spammersHashCodes: Set<Int> = emptySet()

    override val chatroomList: ChatroomList = ChatroomList(accountState.pubKeyHex)
    override val marmotGroupList =
        com.vitorpamplona.amethyst.commons.model.marmotGroups
            .MarmotGroupList()

    override val nip47SignerState: INwcSignerState =
        object : INwcSignerState {
            override suspend fun decryptResponse(event: LnZapPaymentResponseEvent): Response? = null

            override suspend fun decryptRequest(event: LnZapPaymentRequestEvent): Request? = null

            override fun isNIP47Author(pubKey: String?): Boolean = false

            override fun hasWalletConnectSetup(): Boolean = false

            override suspend fun sendNwcRequest(
                request: Request,
                onResponse: (Response?) -> Unit,
            ): Pair<LnZapPaymentRequestEvent, com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl> = throw UnsupportedOperationException("NWC not supported on desktop")

            override suspend fun sendNwcRequestToWallet(
                walletUri: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm?,
                request: Request,
                onResponse: (Response?) -> Unit,
            ): Pair<LnZapPaymentRequestEvent, com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl> = throw UnsupportedOperationException("NWC not supported on desktop")

            override suspend fun sendZapPaymentRequestFor(
                bolt11: String,
                zappedNote: com.vitorpamplona.amethyst.commons.model.Note?,
                onResponse: (Response?) -> Unit,
            ): Pair<LnZapPaymentRequestEvent, com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl> = throw UnsupportedOperationException("NWC not supported on desktop")
        }

    override val privateZapsDecryptionCache: IPrivateZapsDecryptionCache =
        object : IPrivateZapsDecryptionCache {
            override fun cachedPrivateZap(event: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null

            override suspend fun decryptPrivateZap(event: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null
        }

    override fun userProfile(): User = localCache.getOrCreateUser(pubKey)

    override fun isWriteable(): Boolean = !accountState.isReadOnly

    override fun followingKeySet(): Set<String> = kind3FollowList.flow.value.authors

    override fun isHidden(user: User): Boolean = false

    override fun isHidden(userHex: String): Boolean = false

    override fun isFollowing(user: User): Boolean =
        kind3FollowList.flow.value.authors
            .contains(user.pubkeyHex)

    override fun isAcceptable(note: Note): Boolean {
        // Accept all notes on desktop for now
        val event = note.event ?: return true
        return !localCache.hasBeenDeleted(event)
    }

    override suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<PrivateDmEvent>) {
        if (!isWriteable()) return

        val signedEvent = signer.sign(eventTemplate)
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

    override suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return

        val result = NIP17Factory().createEncryptedFileNIP17(template, signer)

        // Optimistic local add
        val innerEvent = result.msg as ChatMessageEncryptedFileHeaderEvent
        addEventToChatroom(innerEvent, innerEvent.chatroomKey(pubKey))

        // Collect wraps with target relays and send
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

    override fun sendMyPublicAndPrivateOutbox(event: com.vitorpamplona.quartz.nip01Core.core.Event?) {
        // Desktop stub - no relay publishing
    }

    override fun sendMyPublicAndPrivateOutbox(events: List<com.vitorpamplona.quartz.nip01Core.core.Event>) {
        // Desktop stub - no relay publishing
    }

    companion object {
        const val CLIENT_TAG_NAME = "Amethyst"
    }
}
