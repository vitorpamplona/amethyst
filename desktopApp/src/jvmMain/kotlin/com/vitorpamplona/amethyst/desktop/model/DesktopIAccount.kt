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
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.Kind3FollowListRepository
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.Kind3FollowListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.OldBookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListRepository
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.commons.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.commons.moderation.PreferencesSensitiveContentSettings
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.DmInboxRelayResolver
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.chats.DmSendTracker
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.IPrivateZapsDecryptionCache
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

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
    private val accountRelays: DesktopAccountRelays? = null,
    val dmInboxResolver: DmInboxRelayResolver? = null,
) : IAccount {
    override val signer: NostrSigner = NostrSignerWithClientTag(accountState.signer, CLIENT_TAG_NAME)

    override val pubKey: String = accountState.pubKeyHex

    // ----- State Classes (pin important notes via strong refs for GC retention) -----

    val oldBookmarkState = OldBookmarkListState(signer, localCache, scope)
    val bookmarkState = BookmarkListState(signer, localCache, scope)

    /**
     * User's Blossom media server list (NIP-B7 / kind 10063). Loads from the
     * same event kind the Amethyst mobile app uses, so a server list configured
     * on mobile shows up here too. Backed by [localCache]; populated by the
     * account-config subscription in Main.kt.
     */
    val blossomServerList = BlossomServerListState(signer, localCache, scope)

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

    /**
     * Friends-of-friends trust score. Populated by Main.kt's login flow
     * from batch kind-3 fetches on the active user's follow set.
     */
    val wotService =
        com.vitorpamplona.amethyst.commons.wot
            .WoTService(scope)

    val nip65RelayList =
        Nip65RelayListState(
            signer,
            localCache,
            scope,
            object : Nip65RelayListRepository {
                override val backupNIP65RelayList: AdvertisedRelayListEvent? =
                    accountRelays?.loadPersistedNip65Event()

                override fun updateNIP65RelayList(event: AdvertisedRelayListEvent) {
                    accountRelays?.consumePublishedEvent(event)
                }

                override val defaultOutboxRelays = relayManager.connectedRelays.value
                override val defaultInboxRelays = relayManager.connectedRelays.value
            },
        )

    // ---------------------------------------------------------------------------------

    /**
     * "Always show sensitive content" preference (NIP-36). `null` = respect
     * content warnings (blur), `true` = always show. Persisted via
     * [PreferencesSensitiveContentSettings] (shared with `amy`); flip it with
     * [setAlwaysShowSensitive].
     */
    private val sensitiveContentSettings = PreferencesSensitiveContentSettings()
    val showSensitiveContentSetting: StateFlow<Boolean?> = sensitiveContentSettings.showSensitiveContent

    fun setAlwaysShowSensitive(alwaysShow: Boolean) = sensitiveContentSettings.setAlwaysShow(alwaysShow)

    /**
     * Mute (kind 10000) + block (kind 30000 `d=mute`) state, assembled into a
     * live [com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers] used by the
     * feed filters and [isHidden]/[isAcceptable]. See [DesktopHiddenUsersState].
     */
    val hiddenUsersState = DesktopHiddenUsersState(signer, localCache, scope, showSensitiveContentSetting)

    /** Current moderation choices — feeds observe this to re-filter live on mute/block. */
    val hiddenUsers: StateFlow<LiveHiddenUsers> get() = hiddenUsersState.flow

    override val showSensitiveContent: Boolean? get() = hiddenUsersState.flow.value.showSensitiveContent

    override val hiddenWordsCase: List<DualCase> get() = hiddenUsersState.flow.value.hiddenWordsCase

    override val hiddenUsersHashCodes: Set<Int> get() = hiddenUsersState.flow.value.hiddenUsersHashCodes

    override val spammersHashCodes: Set<Int> get() = hiddenUsersState.flow.value.spammersHashCodes

    override val chatroomList: ChatroomList = ChatroomList(accountState.pubKeyHex)
    override val marmotGroupList =
        com.vitorpamplona.amethyst.commons.model.marmotGroups
            .MarmotGroupList(signer.pubKey)

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

    override fun followingKeySet(): Set<String> = kind3FollowList.flow.value.authors

    override fun isHidden(user: User): Boolean = hiddenUsersState.flow.value.isUserHidden(user.pubkeyHex)

    override fun isAcceptable(note: Note): Boolean {
        val event = note.event ?: return true
        if (localCache.hasBeenDeleted(event)) return false
        return !note.isHiddenFor(hiddenUsersState.flow.value)
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

        val hints = recipientRelayHints(template.tags)
        val result = NIP17Factory().createMessageNIP17(template, signer, recipientRelayHints = { hints[it] })

        // Optimistic local add — use the inner ChatMessageEvent, not the wraps
        val innerMsg = result.msg as ChatMessageEvent
        addEventToChatroom(innerMsg, innerMsg.chatroomKey(pubKey))

        // Collect all wraps with their target relays for batch sending
        val batch =
            result.wraps.map { wrap ->
                val recipientKey = wrap.recipientPubKey()
                val targetRelays = resolveDmInboxRelaysStrict(recipientKey)
                wrap to targetRelays
            }

        scope.launch { dmSendTracker.sendBatch(batch) }
    }

    override suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return

        val hints = recipientRelayHints(template.tags)
        val result = NIP17Factory().createEncryptedFileNIP17(template, signer, recipientRelayHints = { hints[it] })

        // Optimistic local add
        val innerEvent = result.msg as ChatMessageEncryptedFileHeaderEvent
        addEventToChatroom(innerEvent, innerEvent.chatroomKey(pubKey))

        // Collect wraps with target relays and send
        val batch =
            result.wraps.map { wrap ->
                val recipientKey = wrap.recipientPubKey()
                val targetRelays = resolveDmInboxRelaysStrict(recipientKey)
                wrap to targetRelays
            }

        scope.launch { dmSendTracker.sendBatch(batch) }
    }

    override suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>) {
        val batch =
            wraps.map { wrap ->
                val recipientKey = wrap.recipientPubKey()
                val targetRelays = resolveDmInboxRelaysStrict(recipientKey)
                wrap to targetRelays
            }

        scope.launch { dmSendTracker.sendBatch(batch) }
    }

    /**
     * NIP-17 inbox-relay resolution, strict variant — no fallback to the
     * user's connected relays.
     *
     * Per NIP-17 §Publishing, a gift wrap MUST only land on relays advertised
     * in the recipient's kind:10050. Falling back to the sender's connected
     * relays when 10050 is missing publishes the wrap to relays the recipient
     * does NOT consult — at best the message never arrives, at worst it leaks
     * the conversation metadata (recipient pubkey + send timestamp) to relays
     * outside the recipient's chosen inbox.
     *
     * Three-layer lookup when a [dmInboxResolver] is injected (default in
     * Main.kt):
     *   1. LocalCache hit (fast, no I/O)
     *   2. Resolver's in-memory LRU cache
     *   3. Curated indexer fan-out via an unauthenticated NostrClient
     *
     * Without a resolver (legacy / tests), falls back to LocalCache-only.
     *
     * Empty result means the wrap will not be sent; [DmSendTracker.sendBatch]
     * surfaces this as a "No relays available" failure to the user.
     */
    private suspend fun resolveDmInboxRelaysStrict(recipientKey: HexKey?): Set<NormalizedRelayUrl> = resolveDmInboxRelaysStrictOrdered(recipientKey).toSet()

    /**
     * Ordered variant of [resolveDmInboxRelaysStrict]. Preserves the relay
     * order declared in the recipient's kind:10050 so the first element is the
     * recipient's *primary* DM inbox — used as the NIP-17 gift-wrap `p`-tag
     * relay hint. The unordered [resolveDmInboxRelaysStrict] derives from this.
     */
    private suspend fun resolveDmInboxRelaysStrictOrdered(recipientKey: HexKey?): List<NormalizedRelayUrl> {
        if (recipientKey == null) return emptyList()
        val resolver = dmInboxResolver
        return if (resolver != null) {
            resolver.resolve(recipientKey)
        } else {
            localCache
                .getOrCreateUser(recipientKey)
                .dmInboxRelaysStrict()
                ?.ifEmpty { null }
                ?: emptyList()
        }
    }

    /**
     * Per-recipient primary DM-inbox relay, keyed by recipient pubkey, for the
     * NIP-17 gift-wrap `p`-tag hint (`["p", <pubkey>, <primary-relay>]`). Built
     * from the recipient `p` tags on the outgoing message template. A recipient
     * with no resolvable kind:10050 maps to `null`, which keeps the historical
     * 2-element `p` tag for that recipient.
     */
    private suspend fun recipientRelayHints(tags: Array<Array<String>>): Map<HexKey, NormalizedRelayUrl?> {
        val recipients = tags.mapNotNull { if (it.size >= 2 && it[0] == "p") it[1] else null }.toSet()
        return recipients.associateWith { resolveDmInboxRelaysStrictOrdered(it).firstOrNull() }
    }

    // Peers whose kind:10050 we've already kicked off a prewarm for. Prewarm is
    // best-effort and idempotent — once requested we don't re-request, because
    // the resolver's own LRU (and, once a 10050 arrives, LocalCache) serves
    // subsequent lookups. The send/pre-send paths call the resolver directly
    // and are NOT gated by this set, so they always see fresh-within-TTL data.
    private val prewarmedDmInboxKeys = ConcurrentHashMap.newKeySet<HexKey>()

    // Cap concurrent kind:10050 fan-outs so scrolling a long conversation list
    // (or opening several rooms quickly) doesn't burst the indexer relays.
    private val dmInboxPrewarmLimiter = Semaphore(4)

    /**
     * Proactively resolve (and cache) the NIP-17 DM inbox relays (kind:10050)
     * for [pubkeys], so the first message/reaction sent to any of them resolves
     * from cache instead of paying an indexer round-trip, and the composer's
     * "recipient has no DM relays" gate settles before the user starts typing.
     *
     * Reusable app-wide: any surface that displays a DM (the visible rows of the
     * conversation list, an opened room, a future notification/preview) should
     * call this so the relay list is downloaded before the user drops into the
     * room. Viewport-scoped by design — callers pass only the peers they are
     * actually showing, so a large conversation list only warms what's visible
     * and warms more as the user scrolls.
     */
    fun prewarmDmInboxRelays(pubkeys: Collection<HexKey>) {
        val resolver = dmInboxResolver ?: return
        for (pubkey in pubkeys) {
            if (pubkey.length != 64 || !prewarmedDmInboxKeys.add(pubkey)) continue
            scope.launch(Dispatchers.IO) {
                dmInboxPrewarmLimiter.withPermit {
                    try {
                        resolver.resolve(pubkey)
                    } catch (_: Exception) {
                        // Best-effort prefetch; the send path resolves again on demand.
                        prewarmedDmInboxKeys.remove(pubkey)
                    }
                }
            }
        }
    }

    // ----- Moderation write actions (NIP-51 mute list + NIP-56 reports) -----

    /** Mute a user (private entry). Persists to the kind-10000 mute list and hides live. */
    suspend fun hideUser(pubkeyHex: HexKey) = updateMuteList(UserTag(pubkeyHex), isPrivate = true, add = true)

    /** Un-mute a user. */
    suspend fun showUser(pubkeyHex: HexKey) = updateMuteList(UserTag(pubkeyHex), isPrivate = true, add = false)

    /** Hide a word/phrase (private entry). Notes containing it collapse. */
    suspend fun hideWord(word: String) = updateMuteList(WordTag(word), isPrivate = true, add = true)

    suspend fun showWord(word: String) = updateMuteList(WordTag(word), isPrivate = true, add = false)

    /** Mute a thread by its root event id. */
    suspend fun hideThread(rootIdHex: HexKey) = updateMuteList(EventTag(rootIdHex), isPrivate = true, add = true)

    suspend fun showThread(rootIdHex: HexKey) = updateMuteList(EventTag(rootIdHex), isPrivate = true, add = false)

    private suspend fun updateMuteList(
        tag: MuteTag,
        isPrivate: Boolean,
        add: Boolean,
    ) {
        if (!isWriteable()) return
        try {
            val current = hiddenUsersState.currentMuteList()
            val event =
                when {
                    !add -> if (current != null) MuteListEvent.remove(current, tag, signer) else return
                    current == null -> MuteListEvent.create(tag, isPrivate, signer)
                    else -> MuteListEvent.add(current, tag, isPrivate, signer)
                }
            // Optimistic local apply so enforcement + the management screens update
            // immediately, then fan out to relays.
            localCache.justConsumeMyOwnEvent(event)
            publishModeration(event, if (add) "mute+" else "mute-")
        } catch (e: Exception) {
            moderationLog.warning("[Moderation] mute list update failed: ${e.message}")
            throw e
        }
    }

    /** Publish a NIP-56 (kind 1984) report about a note. */
    suspend fun report(
        note: Note,
        type: ReportType,
        comment: String = "",
    ) {
        val reported = note.event ?: return
        reportEvent(reported, type, comment)
    }

    /** Publish a NIP-56 (kind 1984) report about a raw event. */
    suspend fun reportEvent(
        reportedEvent: Event,
        type: ReportType,
        comment: String = "",
    ) {
        if (!isWriteable()) return
        try {
            val signed = signer.sign(ReportEvent.build(reportedEvent, type, comment))
            publishModeration(signed, "report(${type.code})")
        } catch (e: Exception) {
            moderationLog.warning("[Moderation] report failed: ${e.message}")
            throw e
        }
    }

    /** Publish a NIP-56 (kind 1984) report about a user. */
    suspend fun report(
        userPubKeyHex: HexKey,
        type: ReportType,
        comment: String = "",
    ) {
        if (!isWriteable()) return
        try {
            val signed = signer.sign(ReportEvent.build(userPubKeyHex, type, comment))
            publishModeration(signed, "report-user(${type.code})")
        } catch (e: Exception) {
            moderationLog.warning("[Moderation] user report failed: ${e.message}")
            throw e
        }
    }

    /**
     * Broadcast a moderation event and log the outcome — including the relay
     * count, so a publish to zero connected relays is visible rather than silent.
     */
    private fun publishModeration(
        event: Event,
        action: String,
    ) {
        val relayCount = relayManager.connectedRelays.value.size
        relayManager.broadcastToAll(event)
        if (relayCount == 0) {
            moderationLog.warning("[Moderation] $action kind=${event.kind} id=${event.id.take(8)} → 0 connected relays (not delivered)")
        } else {
            moderationLog.info("[Moderation] $action kind=${event.kind} id=${event.id.take(8)} → $relayCount relays")
        }
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

    companion object {
        const val CLIENT_TAG_NAME = "Amethyst"
        private val moderationLog: Logger = Logger.getLogger("DesktopModeration")
    }
}
