package com.vitorpamplona.amethyst.model

import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import com.vitorpamplona.amethyst.OptOutFromFilters
import com.vitorpamplona.amethyst.service.AmberUtils
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.NostrLnZapPaymentResponseDataSource
import com.vitorpamplona.amethyst.service.SignerType
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.combineWith
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.*
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.net.Proxy
import java.util.Locale

val DefaultChannels = setOf(
    "25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb", // -> Anigma's Nostr
    "42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5" // -> Amethyst's Group
)

fun getLanguagesSpokenByUser(): Set<String> {
    val languageList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
    val codedList = mutableSetOf<String>()
    for (i in 0 until languageList.size()) {
        languageList.get(i)?.let { codedList.add(it.language) }
    }
    return codedList
}

val GLOBAL_FOLLOWS = " Global " // This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val KIND3_FOLLOWS = " All Follows " // This has spaces to avoid mixing with a potential NIP-51 list with the same name.

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val keyPair: KeyPair,

    var followingChannels: Set<String> = DefaultChannels, // deprecated
    var followingCommunities: Set<String> = setOf(), // deprecated
    var hiddenUsers: Set<String> = setOf(), // deprecated

    var localRelays: Set<RelaySetupInfo> = Constants.defaultRelays.toSet(),
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
    var zapAmountChoices: List<Long> = listOf(500L, 1000L, 5000L),
    var reactionChoices: List<String> = listOf("+"),
    var defaultZapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PRIVATE,
    var defaultFileServer: ServersAvailable = ServersAvailable.NOSTR_BUILD,
    var defaultHomeFollowList: String = KIND3_FOLLOWS,
    var defaultStoriesFollowList: String = GLOBAL_FOLLOWS,
    var defaultNotificationFollowList: String = GLOBAL_FOLLOWS,
    var defaultDiscoveryFollowList: String = GLOBAL_FOLLOWS,
    var zapPaymentRequest: Nip47URI? = null,
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP24WarningDialog: Boolean = false,
    var backupContactList: ContactListEvent? = null,
    var proxy: Proxy? = null,
    var proxyPort: Int = 9050,
    var showSensitiveContent: Boolean? = null,
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: Boolean = true,
    var lastReadPerRoute: Map<String, Long> = mapOf<String, Long>(),
    var settings: Settings = Settings(),
    var loginWithAmber: Boolean = false
) {
    var transientHiddenUsers: ImmutableSet<String> = persistentSetOf()

    // Observers line up here.
    val live: AccountLiveData = AccountLiveData(this)
    val liveLanguages: AccountLiveData = AccountLiveData(this)
    val liveLastRead: AccountLiveData = AccountLiveData(this)
    val saveable: AccountLiveData = AccountLiveData(this)

    @Immutable
    data class LiveHiddenUsers(
        val hiddenUsers: ImmutableSet<String>,
        val spammers: ImmutableSet<String>,
        val showSensitiveContent: Boolean?
    )

    val liveHiddenUsers: LiveData<LiveHiddenUsers> by lazy {
        live.combineWith(getBlockListNote().live().metadata) { localLive, liveMuteListEvent ->
            val blockList = liveMuteListEvent?.note?.event as? PeopleListEvent
            if (loginWithAmber) {
                val id = blockList?.id
                if (id != null) {
                    if (blockList.decryptedContent == null) {
                        GlobalScope.launch(Dispatchers.IO) {
                            val content = blockList.content
                            if (content.isEmpty()) return@launch
                            AmberUtils.decryptBlockList(
                                content,
                                keyPair.pubKey.toHexKey(),
                                blockList.id()
                            )
                            blockList.decryptedContent = AmberUtils.cachedDecryptedContent[blockList.id]
                            live.invalidateData()
                        }

                        LiveHiddenUsers(
                            hiddenUsers = persistentSetOf(),
                            spammers = localLive?.account?.transientHiddenUsers
                                ?: persistentSetOf(),
                            showSensitiveContent = showSensitiveContent
                        )
                    } else {
                        blockList.decryptedContent = AmberUtils.cachedDecryptedContent[blockList.id]
                        val liveBlockedUsers = blockList.publicAndPrivateUsers(blockList.decryptedContent ?: "")
                        LiveHiddenUsers(
                            hiddenUsers = liveBlockedUsers,
                            spammers = localLive?.account?.transientHiddenUsers
                                ?: persistentSetOf(),
                            showSensitiveContent = showSensitiveContent
                        )
                    }
                } else {
                    LiveHiddenUsers(
                        hiddenUsers = persistentSetOf(),
                        spammers = localLive?.account?.transientHiddenUsers
                            ?: persistentSetOf(),
                        showSensitiveContent = showSensitiveContent
                    )
                }
            } else {
                val liveBlockedUsers = blockList?.publicAndPrivateUsers(keyPair.privKey)
                LiveHiddenUsers(
                    hiddenUsers = liveBlockedUsers ?: persistentSetOf(),
                    spammers = localLive?.account?.transientHiddenUsers ?: persistentSetOf(),
                    showSensitiveContent = showSensitiveContent
                )
            }
        }.distinctUntilChanged()
    }

    var userProfileCache: User? = null

    fun updateAutomaticallyStartPlayback(
        automaticallyStartPlayback: ConnectivityType
    ) {
        settings.automaticallyStartPlayback = automaticallyStartPlayback
        live.invalidateData()
        saveable.invalidateData()
    }

    fun updateAutomaticallyShowUrlPreview(
        automaticallyShowUrlPreview: ConnectivityType
    ) {
        settings.automaticallyShowUrlPreview = automaticallyShowUrlPreview
        live.invalidateData()
        saveable.invalidateData()
    }

    fun updateAutomaticallyShowImages(
        automaticallyShowImages: ConnectivityType
    ) {
        settings.automaticallyShowImages = automaticallyShowImages
        live.invalidateData()
        saveable.invalidateData()
    }

    fun updateOptOutOptions(warnReports: Boolean, filterSpam: Boolean) {
        warnAboutPostsWithReports = warnReports
        filterSpamFromStrangers = filterSpam
        OptOutFromFilters.start(warnAboutPostsWithReports, filterSpamFromStrangers)
        if (!filterSpamFromStrangers) {
            transientHiddenUsers = persistentSetOf()
        }
        live.invalidateData()
        saveable.invalidateData()
    }

    fun userProfile(): User {
        return userProfileCache ?: run {
            val myUser: User = LocalCache.getOrCreateUser(keyPair.pubKey.toHexKey())
            userProfileCache = myUser
            myUser
        }
    }

    fun isWriteable(): Boolean {
        return keyPair.privKey != null
    }

    fun sendNewRelayList(relays: Map<String, ContactListEvent.ReadWrite>) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            var event = ContactListEvent.updateRelayList(
                earlierVersion = contactList,
                relayUse = relays,
                keyPair = keyPair
            )

            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val content = AmberUtils.content[event.id] ?: ""
                if (content.isBlank()) {
                    return
                }
                event = ContactListEvent.create(event, content)
            }

            Client.send(event)
            LocalCache.consume(event)
        } else {
            var event = ContactListEvent.createFromScratch(
                followUsers = listOf(),
                followTags = listOf(),
                followGeohashes = listOf(),
                followCommunities = listOf(),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                keyPair = keyPair
            )

            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val content = AmberUtils.content[event.id]
                if (content.isBlank()) {
                    return
                }
                event = ContactListEvent.create(event, content)
            }

            // Keep this local to avoid erasing a good contact list.
            // Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun sendNewUserMetadata(toString: String, identities: List<IdentityClaim>) {
        if (!isWriteable() && !loginWithAmber) return

        var event = MetadataEvent.create(toString, identities, keyPair.pubKey.toHexKey(), keyPair.privKey)
        if (loginWithAmber) {
            val content = AmberUtils.content[event.id]
            if (content.isBlank()) {
                return
            }
            event = MetadataEvent.create(event, content)
        }
        Client.send(event)
        LocalCache.consume(event)

        return
    }

    fun reactionTo(note: Note, reaction: String): List<Note> {
        return note.reactedBy(userProfile(), reaction)
    }

    fun hasBoosted(note: Note): Boolean {
        return boostsTo(note).isNotEmpty()
    }

    fun boostsTo(note: Note): List<Note> {
        return note.boostedBy(userProfile())
    }

    fun hasReacted(note: Note, reaction: String): Boolean {
        return note.hasReacted(userProfile(), reaction)
    }

    fun reactTo(note: Note, reaction: String) {
        if (!isWriteable() && !loginWithAmber) return

        if (hasReacted(note, reaction)) {
            // has already liked this note
            return
        }

        if (note.event is ChatMessageEvent) {
            val event = note.event as ChatMessageEvent
            val users = event.recipientsPubKey().plus(event.pubKey).toSet().toList()

            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrl.decode(reaction)
                if (emojiUrl != null) {
                    note.event?.let {
                        if (loginWithAmber) {
                            val senderPublicKey = keyPair.pubKey.toHexKey()

                            var senderReaction = ReactionEvent.create(
                                emojiUrl,
                                it,
                                keyPair
                            )

                            AmberUtils.openAmber(senderReaction)
                            val reactionContent = AmberUtils.content[event.id]
                            if (reactionContent.isBlank()) return
                            senderReaction = ReactionEvent.create(senderReaction, reactionContent)

                            val giftWraps = users.plus(senderPublicKey).map {
                                val gossip = Gossip.create(senderReaction)
                                val content = Gossip.toJson(gossip)
                                AmberUtils.encrypt(content, it, gossip.id!!, SignerType.NIP44_ENCRYPT)
                                val encryptedContent = AmberUtils.content[gossip.id]
                                if (encryptedContent.isBlank()) return

                                var sealedEvent = SealedGossipEvent.create(
                                    encryptedContent = encryptedContent,
                                    pubKey = senderPublicKey
                                )
                                AmberUtils.openAmber(sealedEvent)
                                val eventContent = AmberUtils.content[sealedEvent.id] ?: ""
                                if (eventContent.isBlank()) return
                                sealedEvent = SealedGossipEvent.create(sealedEvent, eventContent)

                                GiftWrapEvent.create(
                                    event = sealedEvent,
                                    recipientPubKey = it
                                )
                            }

                            broadcastPrivately(giftWraps)
                        } else {
                            val giftWraps = NIP24Factory().createReactionWithinGroup(
                                emojiUrl = emojiUrl,
                                originalNote = it,
                                to = users,
                                from = keyPair
                            )
                            broadcastPrivately(giftWraps)
                        }
                    }

                    return
                }
            }

            note.event?.let {
                if (loginWithAmber) {
                    val senderPublicKey = keyPair.pubKey.toHexKey()

                    var senderReaction = ReactionEvent.create(
                        reaction,
                        it,
                        keyPair
                    )

                    AmberUtils.openAmber(senderReaction)
                    val reactionContent = AmberUtils.content[senderReaction.id] ?: ""
                    if (reactionContent.isBlank()) return
                    senderReaction = ReactionEvent.create(senderReaction, reactionContent)

                    val newUsers = users.plus(senderPublicKey)
                    newUsers.forEach {
                        val gossip = Gossip.create(senderReaction)
                        val content = Gossip.toJson(gossip)
                        AmberUtils.encrypt(content, it, gossip.id!!, SignerType.NIP44_ENCRYPT)
                        val encryptedContent = AmberUtils.content[gossip.id]
                        if (encryptedContent.isBlank()) return

                        var sealedEvent = SealedGossipEvent.create(
                            encryptedContent = encryptedContent,
                            pubKey = senderPublicKey
                        )
                        AmberUtils.openAmber(sealedEvent)
                        val sealedContent = AmberUtils.content[sealedEvent.id] ?: ""
                        if (sealedContent.isBlank()) return
                        sealedEvent = SealedGossipEvent.create(sealedEvent, sealedContent)

                        val giftWraps = GiftWrapEvent.create(
                            event = sealedEvent,
                            recipientPubKey = it
                        )

                        broadcastPrivately(listOf(giftWraps))
                    }
                } else {
                    val giftWraps = NIP24Factory().createReactionWithinGroup(
                        content = reaction,
                        originalNote = it,
                        to = users,
                        from = keyPair
                    )

                    broadcastPrivately(giftWraps)
                }
            }
            return
        } else {
            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrl.decode(reaction)
                if (emojiUrl != null) {
                    note.event?.let {
                        var event = ReactionEvent.create(emojiUrl, it, keyPair)
                        if (loginWithAmber) {
                            AmberUtils.openAmber(event)
                            val content = AmberUtils.content[event.id] ?: ""
                            if (content.isBlank()) {
                                return
                            }
                            event = ReactionEvent.create(event, content)
                        }
                        Client.send(event)
                        LocalCache.consume(event)
                    }

                    return
                }
            }

            note.event?.let {
                var event = ReactionEvent.create(reaction, it, keyPair)
                if (loginWithAmber) {
                    AmberUtils.openAmber(event)
                    val content = AmberUtils.content[event.id] ?: ""
                    if (content.isBlank()) {
                        return
                    }
                    event = ReactionEvent.create(event, content)
                }
                Client.send(event)
                LocalCache.consume(event)
            }
        }
    }

    fun createZapRequestFor(note: Note, pollOption: Int?, message: String = "", zapType: LnZapEvent.ZapType, toUser: User?): LnZapRequestEvent? {
        if (!isWriteable() && !loginWithAmber) return null

        note.event?.let { event ->
            if (loginWithAmber) {
                when (zapType) {
                    LnZapEvent.ZapType.ANONYMOUS -> {
                        return LnZapRequestEvent.createAnonymous(
                            event,
                            userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                                ?: localRelays.map { it.url }.toSet(),
                            pollOption,
                            message,
                            toUser?.pubkeyHex
                        )
                    }
                    LnZapEvent.ZapType.PUBLIC -> {
                        val unsignedEvent = LnZapRequestEvent.createPublic(
                            event,
                            userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                                ?: localRelays.map { it.url }.toSet(),
                            keyPair.pubKey.toHexKey(),
                            pollOption,
                            message,
                            toUser?.pubkeyHex
                        )
                        AmberUtils.openAmber(unsignedEvent)
                        val content = AmberUtils.content[unsignedEvent.id] ?: ""
                        if (content.isBlank()) return null

                        return LnZapRequestEvent.create(
                            unsignedEvent,
                            content
                        )
                    }

                    LnZapEvent.ZapType.PRIVATE -> {
                        val unsignedEvent = LnZapRequestEvent.createPrivateZap(
                            event,
                            userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                                ?: localRelays.map { it.url }.toSet(),
                            keyPair.pubKey.toHexKey(),
                            pollOption,
                            message,
                            toUser?.pubkeyHex
                        )
                        AmberUtils.openAmber(unsignedEvent, "event")
                        val content = AmberUtils.content[unsignedEvent.id] ?: ""
                        if (content.isBlank()) return null

                        return Event.fromJson(content) as LnZapRequestEvent
                    }
                    else -> null
                }
            } else {
                return LnZapRequestEvent.create(
                    event,
                    userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                        ?: localRelays.map { it.url }.toSet(),
                    keyPair.privKey!!,
                    pollOption,
                    message,
                    zapType,
                    toUser?.pubkeyHex
                )
            }
        }
        return null
    }

    fun hasWalletConnectSetup(): Boolean {
        return zapPaymentRequest != null
    }

    fun isNIP47Author(pubkeyHex: String?): Boolean {
        val privKey = zapPaymentRequest?.secret?.hexToByteArray() ?: keyPair.privKey

        if (privKey == null && !loginWithAmber) return false

        if (privKey != null) {
            val pubKey = CryptoUtils.pubkeyCreate(privKey).toHexKey()
            return (pubKey == pubkeyHex)
        }

        return (keyPair.pubKey.toHexKey() == pubkeyHex)
    }

    fun decryptZapPaymentResponseEvent(zapResponseEvent: LnZapPaymentResponseEvent): Response? {
        val myNip47 = zapPaymentRequest ?: return null

        val privKey = myNip47.secret?.hexToByteArray() ?: keyPair.privKey
        val pubKey = myNip47.pubKeyHex.hexToByteArray()

        if (privKey == null && !loginWithAmber) return null

        if (privKey != null) return zapResponseEvent.response(privKey, pubKey)

        AmberUtils.decrypt(zapResponseEvent.content, pubKey.toHexKey(), zapResponseEvent.id)
        val decryptedContent = AmberUtils.content[zapResponseEvent.id] ?: ""
        if (decryptedContent.isBlank()) return null
        return zapResponseEvent.response(decryptedContent)
    }

    fun calculateIfNoteWasZappedByAccount(zappedNote: Note?): Boolean {
        return zappedNote?.isZappedBy(userProfile(), this) == true
    }

    fun calculateZappedAmount(zappedNote: Note?): BigDecimal {
        val privKey = zapPaymentRequest?.secret?.hexToByteArray() ?: keyPair.privKey
        val pubKey = zapPaymentRequest?.pubKeyHex?.hexToByteArray()
        return zappedNote?.zappedAmount(privKey, pubKey) ?: BigDecimal.ZERO
    }

    fun sendZapPaymentRequestFor(bolt11: String, zappedNote: Note?, onResponse: (Response?) -> Unit) {
        if (!isWriteable() && !loginWithAmber) return

        zapPaymentRequest?.let { nip47 ->
            val privateKey = if (loginWithAmber) nip47.secret?.hexToByteArray() else nip47.secret?.hexToByteArray() ?: keyPair.privKey
            if (privateKey == null) return
            val event = LnZapPaymentRequestEvent.create(bolt11, nip47.pubKeyHex, privateKey)

            val wcListener = NostrLnZapPaymentResponseDataSource(
                fromServiceHex = nip47.pubKeyHex,
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                authSigningKey = privateKey
            )
            wcListener.start()

            LocalCache.consume(event, zappedNote) {
                // After the response is received.
                val privKey = nip47.secret?.hexToByteArray()
                if (privKey != null) {
                    onResponse(it.response(privKey, nip47.pubKeyHex.hexToByteArray()))
                }
            }

            Client.send(event, nip47.relayUri, wcListener.feedTypes) {
                wcListener.destroy()
            }
        }
    }

    fun createZapRequestFor(user: User): LnZapRequestEvent? {
        return createZapRequestFor(user)
    }

    fun createZapRequestFor(userPubKeyHex: String, message: String = "", zapType: LnZapEvent.ZapType): LnZapRequestEvent? {
        if (!isWriteable() && !loginWithAmber) return null
        if (loginWithAmber) {
            return when (zapType) {
                LnZapEvent.ZapType.ANONYMOUS -> {
                    return LnZapRequestEvent.createAnonymous(
                        userPubKeyHex,
                        userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                            ?: localRelays.map { it.url }.toSet(),
                        message
                    )
                }
                LnZapEvent.ZapType.PUBLIC -> {
                    val unsignedEvent = LnZapRequestEvent.createPublic(
                        userPubKeyHex,
                        userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                            ?: localRelays.map { it.url }.toSet(),
                        keyPair.pubKey.toHexKey(),
                        message
                    )
                    AmberUtils.openAmber(unsignedEvent)
                    val content = AmberUtils.content[unsignedEvent.id] ?: ""
                    if (content.isBlank()) return null

                    return LnZapRequestEvent.create(
                        unsignedEvent,
                        content
                    )
                }

                LnZapEvent.ZapType.PRIVATE -> {
                    val unsignedEvent = LnZapRequestEvent.createPrivateZap(
                        userPubKeyHex,
                        userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                            ?: localRelays.map { it.url }.toSet(),
                        keyPair.pubKey.toHexKey(),
                        message
                    )
                    AmberUtils.openAmber(unsignedEvent, "event")
                    val content = AmberUtils.content[unsignedEvent.id] ?: ""
                    if (content.isBlank()) return null

                    return Event.fromJson(content) as LnZapRequestEvent
                }
                else -> null
            }
        } else {
            return LnZapRequestEvent.create(
                userPubKeyHex,
                userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                    ?: localRelays.map { it.url }.toSet(),
                keyPair.privKey!!,
                message,
                zapType
            )
        }
    }

    fun report(note: Note, type: ReportEvent.ReportType, content: String = "") {
        if (!isWriteable() && !loginWithAmber) return

        if (note.hasReacted(userProfile(), "⚠️")) {
            // has already liked this note
            return
        }

        note.event?.let {
            var event = ReactionEvent.createWarning(it, keyPair)
            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) return
                event = ReactionEvent(
                    event.id,
                    event.pubKey,
                    event.createdAt,
                    event.tags,
                    event.content,
                    eventContent
                )
            }
            Client.send(event)
            LocalCache.consume(event)
        }

        note.event?.let {
            var event = ReportEvent.create(it, type, keyPair, content = content)
            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) return
                event = ReportEvent(
                    event.id,
                    event.pubKey,
                    event.createdAt,
                    event.tags,
                    event.content,
                    eventContent
                )
            }
            Client.send(event)
            LocalCache.consume(event, null)
        }
    }

    fun report(user: User, type: ReportEvent.ReportType) {
        if (!isWriteable() && !loginWithAmber) return

        if (user.hasReport(userProfile(), type)) {
            // has already reported this note
            return
        }

        var event = ReportEvent.create(user.pubkeyHex, type, keyPair)
        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) return
            event = ReportEvent(
                event.id,
                event.pubKey,
                event.createdAt,
                event.tags,
                event.content,
                eventContent
            )
        }
        Client.send(event)
        LocalCache.consume(event, null)
    }

    fun delete(note: Note) {
        return delete(listOf(note))
    }

    fun delete(notes: List<Note>) {
        if (!isWriteable() && !loginWithAmber) return

        val myNotes = notes.filter { it.author == userProfile() }.map { it.idHex }

        if (myNotes.isNotEmpty()) {
            var event = DeletionEvent.create(myNotes, keyPair)
            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) return
                event = DeletionEvent(
                    event.id,
                    event.pubKey,
                    event.createdAt,
                    event.tags,
                    event.content,
                    eventContent
                )
            }
            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun createHTTPAuthorization(url: String, method: String, body: String? = null): HTTPAuthorizationEvent? {
        if (!isWriteable() && !loginWithAmber) return null

        var event = HTTPAuthorizationEvent.create(url, method, body, keyPair)
        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return null
            }
            event = HTTPAuthorizationEvent.create(event, eventContent)
        }
        return event
    }

    fun boost(note: Note) {
        if (!isWriteable() && !loginWithAmber) return

        if (note.hasBoostedInTheLast5Minutes(userProfile())) {
            // has already bosted in the past 5mins
            return
        }

        note.event?.let {
            if (it.kind() == 1) {
                var event = RepostEvent.create(it, keyPair)
                if (loginWithAmber) {
                    AmberUtils.openAmber(event)
                    val eventContent = AmberUtils.content[event.id] ?: ""
                    if (eventContent.isBlank()) return
                    event = RepostEvent(
                        event.id,
                        event.pubKey,
                        event.createdAt,
                        event.tags,
                        event.content,
                        eventContent
                    )
                }
                Client.send(event)
                LocalCache.consume(event)
            } else {
                var event = GenericRepostEvent.create(it, keyPair)
                if (loginWithAmber) {
                    AmberUtils.openAmber(event)
                    val eventContent = AmberUtils.content[event.id] ?: ""
                    if (eventContent.isBlank()) return
                    event = GenericRepostEvent(
                        event.id,
                        event.pubKey,
                        event.createdAt,
                        event.tags,
                        event.content,
                        eventContent
                    )
                }
                Client.send(event)
                LocalCache.consume(event)
            }
        }
    }

    fun broadcast(note: Note) {
        note.event?.let {
            if (it is WrappedEvent && it.host != null) {
                it.host?.let { hostEvent ->
                    Client.send(hostEvent)
                }
            } else {
                Client.send(it)
            }
        }
    }

    private fun migrateCommunitiesAndChannelsIfNeeded(latestContactList: ContactListEvent?): ContactListEvent? {
        if (latestContactList == null) return latestContactList

        var returningContactList: ContactListEvent = latestContactList

        if (followingCommunities.isNotEmpty()) {
            followingCommunities.forEach {
                ATag.parse(it, null)?.let {
                    if (loginWithAmber) {
                        val unsignedEvent = ContactListEvent.followAddressableEvent(
                            returningContactList,
                            it,
                            keyPair
                        )
                        AmberUtils.openAmber(unsignedEvent)
                        val eventContent = AmberUtils.content[unsignedEvent.id] ?: ""
                        returningContactList = if (eventContent.isBlank()) {
                            latestContactList
                        } else {
                            ContactListEvent.create(unsignedEvent, eventContent)
                        }
                    } else {
                        returningContactList = ContactListEvent.followAddressableEvent(
                            returningContactList,
                            it,
                            keyPair
                        )
                    }
                }
            }
            followingCommunities = emptySet()
        }

        if (followingChannels.isNotEmpty()) {
            followingChannels.forEach {
                returningContactList = ContactListEvent.followEvent(returningContactList, it, keyPair)
            }
            followingChannels = emptySet()
        }

        return returningContactList
    }

    fun follow(user: User) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        var event = if (contactList != null) {
            ContactListEvent.followUser(contactList, user.pubkeyHex, keyPair)
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(Contact(user.pubkeyHex, null)),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                keyPair = keyPair
            )
        }

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = ContactListEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun follow(channel: Channel) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        var event = if (contactList != null) {
            ContactListEvent.followEvent(contactList, channel.idHex, keyPair)
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList().plus(channel.idHex),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                keyPair = keyPair
            )
        }

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = ContactListEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun follow(community: AddressableNote) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        var event = if (contactList != null) {
            ContactListEvent.followAddressableEvent(contactList, community.address, keyPair)
        } else {
            val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = listOf(community.address),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                keyPair = keyPair
            )
        }

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = ContactListEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun followHashtag(tag: String) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        var event = if (contactList != null) {
            ContactListEvent.followHashtag(
                contactList,
                tag,
                keyPair
            )
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = listOf(tag),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                keyPair = keyPair
            )
        }

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = ContactListEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun followGeohash(geohash: String) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        var event = if (contactList != null) {
            ContactListEvent.followGeohash(
                contactList,
                geohash,
                keyPair
            )
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = listOf(geohash),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                keyPair = keyPair
            )
        }

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = ContactListEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun unfollow(user: User) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            var event = ContactListEvent.unfollowUser(
                contactList,
                user.pubkeyHex,
                keyPair
            )

            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) {
                    return
                }
                event = ContactListEvent.create(event, eventContent)
            }

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollowHashtag(tag: String) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            var event = ContactListEvent.unfollowHashtag(
                contactList,
                tag,
                keyPair
            )

            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) {
                    return
                }
                event = ContactListEvent.create(event, eventContent)
            }

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollowGeohash(geohash: String) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            var event = ContactListEvent.unfollowGeohash(
                contactList,
                geohash,
                keyPair
            )

            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) {
                    return
                }
                event = ContactListEvent.create(event, eventContent)
            }

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollow(channel: Channel) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            var event = ContactListEvent.unfollowEvent(
                contactList,
                channel.idHex,
                keyPair
            )

            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) {
                    return
                }
                event = ContactListEvent.create(event, eventContent)
            }

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollow(community: AddressableNote) {
        if (!isWriteable() && !loginWithAmber) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            var event = ContactListEvent.unfollowAddressableEvent(
                contactList,
                community.address,
                keyPair
            )

            if (loginWithAmber) {
                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) {
                    return
                }
                event = ContactListEvent.create(event, eventContent)
            }

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun createNip95(byteArray: ByteArray, headerInfo: FileHeader): Pair<FileStorageEvent, FileStorageHeaderEvent>? {
        if (!isWriteable() && !loginWithAmber) return null

        if (loginWithAmber) {
            val unsignedData = FileStorageEvent.create(
                mimeType = headerInfo.mimeType ?: "",
                data = byteArray,
                pubKey = keyPair.pubKey.toHexKey()
            )

            AmberUtils.openAmber(unsignedData)
            val eventContent = AmberUtils.content[unsignedData.id] ?: ""
            if (eventContent.isBlank()) return null
            val data = FileStorageEvent(
                unsignedData.id,
                unsignedData.pubKey,
                unsignedData.createdAt,
                unsignedData.tags,
                unsignedData.content,
                eventContent
            )

            val unsignedEvent = FileStorageHeaderEvent.create(
                data,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toString(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash,
                description = headerInfo.description,
                sensitiveContent = headerInfo.sensitiveContent,
                pubKey = keyPair.pubKey.toHexKey()
            )

            AmberUtils.openAmber(unsignedEvent)
            val unsignedEventContent = AmberUtils.content[unsignedEvent.id] ?: ""
            if (unsignedEventContent.isBlank()) return null
            val signedEvent = FileStorageHeaderEvent(
                unsignedEvent.id,
                unsignedEvent.pubKey,
                unsignedEvent.createdAt,
                unsignedEvent.tags,
                unsignedEvent.content,
                unsignedEventContent
            )

            return Pair(data, signedEvent)
        } else {
            val data = FileStorageEvent.create(
                mimeType = headerInfo.mimeType ?: "",
                data = byteArray,
                privateKey = keyPair.privKey!!
            )

            val signedEvent = FileStorageHeaderEvent.create(
                data,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toString(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash,
                description = headerInfo.description,
                sensitiveContent = headerInfo.sensitiveContent,
                privateKey = keyPair.privKey!!
            )

            return Pair(data, signedEvent)
        }
    }

    fun sendNip95(data: FileStorageEvent, signedEvent: FileStorageHeaderEvent, relayList: List<Relay>? = null): Note? {
        if (!isWriteable() && !loginWithAmber) return null

        Client.send(data, relayList = relayList)
        LocalCache.consume(data, null)

        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

        return LocalCache.notes[signedEvent.id]
    }

    private fun sendHeader(signedEvent: FileHeaderEvent, relayList: List<Relay>? = null): Note? {
        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

        return LocalCache.notes[signedEvent.id]
    }

    fun sendHeader(headerInfo: FileHeader, relayList: List<Relay>? = null): Note? {
        if (!isWriteable() && !loginWithAmber) return null

        if (loginWithAmber) {
            val unsignedEvent = FileHeaderEvent.create(
                url = headerInfo.url,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toString(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash,
                description = headerInfo.description,
                sensitiveContent = headerInfo.sensitiveContent,
                keyPair = keyPair
            )
            AmberUtils.openAmber(unsignedEvent)
            val eventContent = AmberUtils.content[unsignedEvent.id] ?: ""
            if (eventContent.isBlank()) return null
            val signedEvent = FileHeaderEvent(
                unsignedEvent.id,
                unsignedEvent.pubKey,
                unsignedEvent.createdAt,
                unsignedEvent.tags,
                unsignedEvent.content,
                eventContent
            )

            return sendHeader(signedEvent, relayList = relayList)
        } else {
            val signedEvent = FileHeaderEvent.create(
                url = headerInfo.url,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toString(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash,
                description = headerInfo.description,
                sensitiveContent = headerInfo.sensitiveContent,
                keyPair = keyPair
            )

            return sendHeader(signedEvent, relayList = relayList)
        }
    }

    fun sendPost(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        tags: List<String>? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        replyingTo: String?,
        root: String?,
        directMentions: Set<HexKey>,
        relayList: List<Relay>? = null,
        geohash: String? = null
    ) {
        if (!isWriteable() && !loginWithAmber) return

        val repliesToHex = replyTo?.filter { it.address() == null }?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        var signedEvent = TextNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            extraTags = tags,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            replyingTo = replyingTo,
            root = root,
            directMentions = directMentions,
            geohash = geohash,
            keyPair = keyPair
        )

        if (loginWithAmber) {
            AmberUtils.openAmber(signedEvent)
            val eventContent = AmberUtils.content[signedEvent.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            signedEvent = TextNoteEvent.create(signedEvent, eventContent)
        }

        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent)

        // broadcast replied notes
        replyingTo?.let {
            LocalCache.getNoteIfExists(replyingTo)?.event?.let {
                Client.send(it, relayList = relayList)
            }
        }
        replyTo?.forEach {
            it.event?.let {
                Client.send(it, relayList = relayList)
            }
        }
        addresses?.forEach {
            LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                Client.send(it, relayList = relayList)
            }
        }
    }

    fun sendPoll(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        pollOptions: Map<Int, String>,
        valueMaximum: Int?,
        valueMinimum: Int?,
        consensusThreshold: Int?,
        closedAt: Int?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        relayList: List<Relay>? = null,
        geohash: String? = null
    ) {
        if (!isWriteable() && !loginWithAmber) return

        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        var signedEvent = PollNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            pubKey = keyPair.pubKey.toHexKey(),
            privateKey = keyPair.privKey,
            pollOptions = pollOptions,
            valueMaximum = valueMaximum,
            valueMinimum = valueMinimum,
            consensusThreshold = consensusThreshold,
            closedAt = closedAt,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash
        )
        // println("Sending new PollNoteEvent: %s".format(signedEvent.toJson()))

        if (loginWithAmber) {
            AmberUtils.openAmber(signedEvent)
            val eventContent = AmberUtils.content[signedEvent.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            signedEvent = PollNoteEvent.create(signedEvent, eventContent)
        }

        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent)

        replyTo?.forEach {
            it.event?.let {
                Client.send(it, relayList = relayList)
            }
        }
        addresses?.forEach {
            LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                Client.send(it, relayList = relayList)
            }
        }
    }

    fun sendChannelMessage(message: String, toChannel: String, replyTo: List<Note>?, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable() && !loginWithAmber) return

        // val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        var signedEvent = ChannelMessageEvent.create(
            message = message,
            channel = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            keyPair = keyPair
        )

        if (loginWithAmber) {
            AmberUtils.openAmber(signedEvent)
            val eventContent = AmberUtils.content[signedEvent.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            signedEvent = ChannelMessageEvent.create(signedEvent, eventContent)
        }

        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendLiveMessage(message: String, toChannel: ATag, replyTo: List<Note>?, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable() && !loginWithAmber) return

        // val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        var signedEvent = LiveActivitiesChatMessageEvent.create(
            message = message,
            activity = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            keyPair = keyPair
        )

        if (loginWithAmber) {
            AmberUtils.openAmber(signedEvent)
            val eventContent = AmberUtils.content[signedEvent.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            signedEvent = LiveActivitiesChatMessageEvent.create(signedEvent, eventContent)
        }

        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendPrivateMessage(message: String, toUser: User, replyingTo: Note? = null, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        sendPrivateMessage(message, toUser.pubkeyHex, replyingTo, mentions, zapReceiver, wantsToMarkAsSensitive, zapRaiserAmount, geohash)
    }

    fun sendPrivateMessage(message: String, toUser: HexKey, replyingTo: Note? = null, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable() && !loginWithAmber) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        var localMessage = message
        if (loginWithAmber) {
            AmberUtils.encrypt(localMessage, toUser, "encrypt")
            val eventContent = AmberUtils.content["encrypt"] ?: ""
            if (eventContent.isBlank()) return
            localMessage = eventContent
            AmberUtils.content.remove("encrypt")
        }

        var signedEvent = PrivateDmEvent.create(
            recipientPubKey = toUser.hexToByteArray(),
            publishedRecipientPubKey = toUser.hexToByteArray(),
            msg = localMessage,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            keyPair = keyPair,
            advertiseNip18 = false
        )

        if (loginWithAmber) {
            AmberUtils.openAmber(signedEvent)
            val eventContent = AmberUtils.content[signedEvent.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            signedEvent = PrivateDmEvent.create(signedEvent, eventContent)
        }

        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendNIP24PrivateMessage(
        message: String,
        toUsers: List<HexKey>,
        subject: String? = null,
        replyingTo: Note? = null,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        geohash: String? = null
    ) {
        if (!isWriteable() && !loginWithAmber) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        if (loginWithAmber) {
            var chatMessageEvent = ChatMessageEvent.create(
                msg = message,
                to = toUsers,
                keyPair = keyPair,
                subject = subject,
                replyTos = repliesToHex,
                mentions = mentionsHex,
                zapReceiver = zapReceiver,
                markAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = zapRaiserAmount,
                geohash = geohash
            )

            AmberUtils.openAmber(chatMessageEvent)
            val eventContent = AmberUtils.content[chatMessageEvent.id] ?: ""
            if (eventContent.isBlank()) return
            chatMessageEvent = ChatMessageEvent.create(chatMessageEvent, eventContent)
            val senderPublicKey = keyPair.pubKey.toHexKey()
            toUsers.plus(senderPublicKey).toSet().forEach {
                val gossip = Gossip.create(chatMessageEvent)
                val content = Gossip.toJson(gossip)
                AmberUtils.encrypt(content, it, gossip.id!!, SignerType.NIP44_ENCRYPT)
                val gossipContent = AmberUtils.content[gossip.id] ?: ""
                if (gossipContent.isNotBlank()) {
                    var sealedEvent = SealedGossipEvent.create(
                        encryptedContent = gossipContent,
                        pubKey = senderPublicKey
                    )
                    AmberUtils.openAmber(sealedEvent)
                    val sealedEventContent = AmberUtils.content[sealedEvent.id] ?: ""
                    if (sealedEventContent.isBlank()) return
                    sealedEvent = SealedGossipEvent.create(sealedEvent, sealedEventContent)

                    val giftWraps = GiftWrapEvent.create(
                        event = sealedEvent,
                        recipientPubKey = it
                    )
                    broadcastPrivately(listOf(giftWraps))
                }
            }
        } else {
            val signedEvents = NIP24Factory().createMsgNIP24(
                msg = message,
                to = toUsers,
                subject = subject,
                replyTos = repliesToHex,
                mentions = mentionsHex,
                zapReceiver = zapReceiver,
                markAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = zapRaiserAmount,
                geohash = geohash,
                keyPair = keyPair
            )

            broadcastPrivately(signedEvents)
        }
    }

    fun broadcastPrivately(signedEvents: List<GiftWrapEvent>) {
        signedEvents.forEach {
            Client.send(it)

            // Only keep in cache the GiftWrap for the account.
            if (it.recipientPubKey() == keyPair.pubKey.toHexKey()) {
                if (loginWithAmber) {
                    AmberUtils.decrypt(it.content, it.pubKey, it.id, SignerType.NIP44_DECRYPT)
                    val decryptedContent = AmberUtils.cachedDecryptedContent[it.id] ?: ""
                    if (decryptedContent.isEmpty()) return
                    it.cachedGift(keyPair.pubKey, decryptedContent)?.let { cached ->
                        if (cached is SealedGossipEvent) {
                            AmberUtils.decrypt(cached.content, cached.pubKey, cached.id, SignerType.NIP44_DECRYPT)
                            val localDecryptedContent = AmberUtils.cachedDecryptedContent[cached.id] ?: ""
                            if (localDecryptedContent.isEmpty()) return
                            cached.cachedGossip(keyPair.pubKey, localDecryptedContent)?.let { gossip ->
                                LocalCache.justConsume(gossip, null)
                            }
                        } else {
                            LocalCache.justConsume(it, null)
                        }
                    }
                } else {
                    it.cachedGift(keyPair.privKey!!)?.let {
                        if (it is SealedGossipEvent) {
                            it.cachedGossip(keyPair.privKey!!)?.let {
                                LocalCache.justConsume(it, null)
                            }
                        } else {
                            LocalCache.justConsume(it, null)
                        }
                    }
                }

                LocalCache.consume(it, null)
            }
        }
    }

    fun sendCreateNewChannel(name: String, about: String, picture: String) {
        if (!isWriteable() && !loginWithAmber) return

        val metadata = ChannelCreateEvent.ChannelData(
            name,
            about,
            picture
        )

        var event = ChannelCreateEvent.create(
            channelInfo = metadata,
            keyPair = keyPair
        )

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = ChannelCreateEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)

        LocalCache.getChannelIfExists(event.id)?.let {
            follow(it)
        }
    }

    fun updateStatus(oldStatus: AddressableNote, newStatus: String) {
        if (!isWriteable() && !loginWithAmber) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        var event = StatusEvent.update(oldEvent, newStatus, keyPair)
        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = StatusEvent.create(event, eventContent)
        }
        Client.send(event)
        LocalCache.consume(event, null)
    }

    fun createStatus(newStatus: String) {
        if (!isWriteable() && !loginWithAmber) return

        var event = StatusEvent.create(newStatus, "general", expiration = null, keyPair)
        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = StatusEvent.create(event, eventContent)
        }
        Client.send(event)
        LocalCache.consume(event, null)
    }

    fun deleteStatus(oldStatus: AddressableNote) {
        if (!isWriteable() && !loginWithAmber) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        var event = StatusEvent.clear(oldEvent, keyPair)
        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = StatusEvent.create(event, eventContent)
        }
        Client.send(event)
        LocalCache.consume(event, null)

        var event2 = DeletionEvent.create(listOf(event.id), keyPair)
        if (loginWithAmber) {
            AmberUtils.openAmber(event2)
            val event2Content = AmberUtils.content[event2.id] ?: ""
            if (event2Content.isBlank()) {
                return
            }
            event2 = DeletionEvent.create(event2, event2Content)
        }
        Client.send(event2)
        LocalCache.consume(event2)
    }

    fun removeEmojiPack(usersEmojiList: Note, emojiList: Note) {
        if (!isWriteable() && !loginWithAmber) return

        val noteEvent = usersEmojiList.event
        if (noteEvent !is EmojiPackSelectionEvent) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        var event = EmojiPackSelectionEvent.create(
            noteEvent.taggedAddresses().filter { it != emojiListEvent.address() },
            keyPair
        )

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = EmojiPackSelectionEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun addEmojiPack(usersEmojiList: Note, emojiList: Note) {
        if (!isWriteable() && !loginWithAmber) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        var event = if (usersEmojiList.event == null) {
            EmojiPackSelectionEvent.create(
                listOf(emojiListEvent.address()),
                keyPair
            )
        } else {
            val noteEvent = usersEmojiList.event
            if (noteEvent !is EmojiPackSelectionEvent) return

            if (noteEvent.taggedAddresses().any { it == emojiListEvent.address() }) {
                return
            }

            EmojiPackSelectionEvent.create(
                noteEvent.taggedAddresses().plus(emojiListEvent.address()),
                keyPair
            )
        }

        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return
            }
            event = EmojiPackSelectionEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun addPrivateBookmark(note: Note, decryptedContent: String) {
        val bookmarks = userProfile().latestBookmarkList
        val privTags = mutableListOf<List<String>>()

        val privEvents = if (note is AddressableNote) {
            bookmarks?.privateTaggedEvents(decryptedContent) ?: emptyList()
        } else {
            bookmarks?.privateTaggedEvents(decryptedContent)?.plus(note.idHex) ?: listOf(note.idHex)
        }
        val privUsers = bookmarks?.privateTaggedUsers(decryptedContent) ?: emptyList()
        val privAddresses = if (note is AddressableNote) {
            bookmarks?.privateTaggedAddresses(decryptedContent)?.plus(note.address) ?: listOf(note.address)
        } else {
            bookmarks?.privateTaggedAddresses(decryptedContent) ?: emptyList()
        }

        privEvents.forEach {
            privTags.add(listOf("e", it))
        }
        privUsers.forEach {
            privTags.add(listOf("p", it))
        }
        privAddresses.forEach {
            privTags.add(listOf("a", it.toTag()))
        }
        val msg = Event.mapper.writeValueAsString(privTags)

        AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), "encrypt")
        val encryptedContent = AmberUtils.content["encrypt"] ?: ""
        AmberUtils.content.remove("encrypt")
        if (encryptedContent.isBlank()) {
            return
        }

        var event = BookmarkListEvent.create(
            "bookmark",
            bookmarks?.taggedEvents() ?: emptyList(),
            bookmarks?.taggedUsers() ?: emptyList(),
            bookmarks?.taggedAddresses() ?: emptyList(),

            encryptedContent,

            keyPair.pubKey.toHexKey()
        )

        AmberUtils.openAmber(event)
        val eventContent = AmberUtils.content[event.id] ?: ""
        if (eventContent.isBlank()) {
            return
        }
        event = BookmarkListEvent.create(event, eventContent)

        Client.send(event)
        LocalCache.consume(event)
    }

    fun removePrivateBookmark(note: Note, decryptedContent: String) {
        val bookmarks = userProfile().latestBookmarkList
        val privTags = mutableListOf<List<String>>()

        val privEvents = if (note is AddressableNote) {
            bookmarks?.privateTaggedEvents(decryptedContent) ?: emptyList()
        } else {
            bookmarks?.privateTaggedEvents(decryptedContent)?.minus(note.idHex) ?: listOf(note.idHex)
        }
        val privUsers = bookmarks?.privateTaggedUsers(decryptedContent) ?: emptyList()
        val privAddresses = if (note is AddressableNote) {
            bookmarks?.privateTaggedAddresses(decryptedContent)?.minus(note.address) ?: listOf(note.address)
        } else {
            bookmarks?.privateTaggedAddresses(decryptedContent) ?: emptyList()
        }

        privEvents.forEach {
            privTags.add(listOf("e", it))
        }
        privUsers.forEach {
            privTags.add(listOf("p", it))
        }
        privAddresses.forEach {
            privTags.add(listOf("a", it.toTag()))
        }
        val msg = Event.mapper.writeValueAsString(privTags)

        AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), "encrypt")
        val encryptedContent = AmberUtils.content["encrypt"] ?: ""
        AmberUtils.content.remove("encrypt")
        if (encryptedContent.isBlank()) {
            return
        }

        var event = BookmarkListEvent.create(
            "bookmark",
            bookmarks?.taggedEvents() ?: emptyList(),
            bookmarks?.taggedUsers() ?: emptyList(),
            bookmarks?.taggedAddresses() ?: emptyList(),

            encryptedContent,

            keyPair.pubKey.toHexKey()
        )

        AmberUtils.openAmber(event)
        val eventContent = AmberUtils.content[event.id] ?: ""
        if (eventContent.isBlank()) {
            return
        }
        event = BookmarkListEvent.create(event, eventContent)

        Client.send(event)
        LocalCache.consume(event)
    }

    fun addPrivateBookmark(note: Note) {
        if (!isWriteable()) return

        val bookmarks = userProfile().latestBookmarkList

        val event = if (note is AddressableNote) {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!)?.plus(note.address) ?: listOf(note.address),

                keyPair.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!)?.plus(note.idHex) ?: listOf(note.idHex),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!) ?: emptyList(),

                keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun addPublicBookmark(note: Note, decryptedContent: String) {
        val bookmarks = userProfile().latestBookmarkList

        val privTags = mutableListOf<List<String>>()

        val privEvents = bookmarks?.privateTaggedEvents(decryptedContent) ?: emptyList()
        val privUsers = bookmarks?.privateTaggedUsers(decryptedContent) ?: emptyList()
        val privAddresses = bookmarks?.privateTaggedAddresses(decryptedContent) ?: emptyList()

        privEvents.forEach {
            privTags.add(listOf("e", it))
        }
        privUsers.forEach {
            privTags.add(listOf("p", it))
        }
        privAddresses.forEach {
            privTags.add(listOf("a", it.toTag()))
        }
        val msg = Event.mapper.writeValueAsString(privTags)

        AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), "encrypt")
        val encryptedContent = AmberUtils.content["encrypt"] ?: ""
        AmberUtils.content.remove("encrypt")
        if (encryptedContent.isBlank()) {
            return
        }

        var event = if (note is AddressableNote) {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses()?.plus(note.address) ?: listOf(note.address),
                encryptedContent,
                keyPair.pubKey.toHexKey()
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents()?.plus(note.idHex) ?: listOf(note.idHex),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),
                encryptedContent,
                keyPair.pubKey.toHexKey()
            )
        }
        AmberUtils.openAmber(event)
        val eventContent = AmberUtils.content[event.id] ?: ""
        if (eventContent.isBlank()) {
            return
        }
        event = BookmarkListEvent.create(event, encryptedContent)

        Client.send(event)
        LocalCache.consume(event)
    }

    fun addPublicBookmark(note: Note) {
        if (!isWriteable()) return

        val bookmarks = userProfile().latestBookmarkList

        val event = if (note is AddressableNote) {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses()?.plus(note.address) ?: listOf(note.address),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!) ?: emptyList(),

                keyPair.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents()?.plus(note.idHex) ?: listOf(note.idHex),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!) ?: emptyList(),

                keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun removePrivateBookmark(note: Note) {
        if (!isWriteable()) return

        val bookmarks = userProfile().latestBookmarkList

        val event = if (note is AddressableNote) {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!)?.minus(note.address) ?: listOf(),

                keyPair.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!)?.minus(note.idHex) ?: listOf(),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!) ?: emptyList(),

                keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun createAuthEvent(relay: Relay, challenge: String): RelayAuthEvent? {
        if (!isWriteable() && !loginWithAmber) return null

        var event = RelayAuthEvent.create(relay.url, challenge, keyPair.pubKey.toHexKey(), keyPair.privKey)
        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) {
                return null
            }
            event = RelayAuthEvent.create(event, eventContent)
        }

        return event
    }

    fun removePublicBookmark(note: Note) {
        if (!isWriteable()) return

        val bookmarks = userProfile().latestBookmarkList

        val event = if (note is AddressableNote) {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents()?.minus(note.address.toTag()) ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses()?.minus(note.address),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!) ?: emptyList(),

                keyPair.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents()?.minus(note.idHex),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = keyPair.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = keyPair.privKey!!) ?: emptyList(),

                keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun removePublicBookmark(note: Note, decryptedContent: String) {
        val bookmarks = userProfile().latestBookmarkList

        val privTags = mutableListOf<List<String>>()

        val privEvents = bookmarks?.privateTaggedEvents(decryptedContent) ?: emptyList()
        val privUsers = bookmarks?.privateTaggedUsers(decryptedContent) ?: emptyList()
        val privAddresses = bookmarks?.privateTaggedAddresses(decryptedContent) ?: emptyList()

        privEvents.forEach {
            privTags.add(listOf("e", it))
        }
        privUsers.forEach {
            privTags.add(listOf("p", it))
        }
        privAddresses.forEach {
            privTags.add(listOf("a", it.toTag()))
        }
        val msg = Event.mapper.writeValueAsString(privTags)

        AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), "encrypt")
        val encryptedContent = AmberUtils.content["encrypt"] ?: ""
        AmberUtils.content.remove("encrypt")
        if (encryptedContent.isBlank()) {
            return
        }

        var event = if (note is AddressableNote) {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses()?.minus(note.address),
                encryptedContent,
                keyPair.pubKey.toHexKey()
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents()?.minus(note.idHex),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),
                encryptedContent,
                keyPair.pubKey.toHexKey()
            )
        }

        AmberUtils.openAmber(event)
        val eventContent = AmberUtils.content[event.id] ?: ""
        if (eventContent.isBlank()) {
            return
        }
        event = BookmarkListEvent.create(event, eventContent)

        Client.send(event)
        LocalCache.consume(event)
    }

    fun isInPrivateBookmarks(note: Note): Boolean {
        if (!isWriteable() && !loginWithAmber) return false

        if (loginWithAmber) {
            return if (note is AddressableNote) {
                userProfile().latestBookmarkList?.privateTaggedAddresses(userProfile().latestBookmarkList?.decryptedContent ?: "")
                    ?.contains(note.address) == true
            } else {
                userProfile().latestBookmarkList?.privateTaggedEvents(userProfile().latestBookmarkList?.decryptedContent ?: "")
                    ?.contains(note.idHex) == true
            }
        } else {
            return if (note is AddressableNote) {
                userProfile().latestBookmarkList?.privateTaggedAddresses(keyPair.privKey!!)
                    ?.contains(note.address) == true
            } else {
                userProfile().latestBookmarkList?.privateTaggedEvents(keyPair.privKey!!)
                    ?.contains(note.idHex) == true
            }
        }
    }

    fun isInPublicBookmarks(note: Note): Boolean {
        if (!isWriteable() && !loginWithAmber) return false

        if (note is AddressableNote) {
            return userProfile().latestBookmarkList?.taggedAddresses()?.contains(note.address) == true
        } else {
            return userProfile().latestBookmarkList?.taggedEvents()?.contains(note.idHex) == true
        }
    }

    fun getBlockListNote(): AddressableNote {
        val aTag = ATag(
            PeopleListEvent.kind,
            userProfile().pubkeyHex,
            PeopleListEvent.blockList,
            null
        )
        return LocalCache.getOrCreateAddressableNote(aTag)
    }

    fun getBlockList(): PeopleListEvent? {
        return getBlockListNote().event as? PeopleListEvent
    }

    private fun migrateHiddenUsersIfNeeded(latestList: PeopleListEvent?): PeopleListEvent? {
        if (latestList == null) return latestList

        var returningList: PeopleListEvent = latestList

        if (hiddenUsers.isNotEmpty()) {
            returningList = PeopleListEvent.addUsers(returningList, hiddenUsers.toList(), true, keyPair.privKey!!)
            hiddenUsers = emptySet()
        }

        return returningList
    }

    fun hideUser(pubkeyHex: String) {
        val blockList = migrateHiddenUsersIfNeeded(getBlockList())
        if (loginWithAmber) {
            val id = blockList?.id
            val encryptedContent = if (id == null) {
                val privateTags = listOf(listOf("p", pubkeyHex))
                val msg = Event.mapper.writeValueAsString(privateTags)

                AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), "encrypt")
                val encryptedContent = AmberUtils.content["encrypted"] ?: ""
                AmberUtils.content.remove("encrypted")
                if (encryptedContent.isBlank()) return
                encryptedContent
            } else {
                var decryptedContent = AmberUtils.cachedDecryptedContent[id]
                if (decryptedContent == null) {
                    AmberUtils.decrypt(blockList.content, keyPair.pubKey.toHexKey(), id)
                    val content = AmberUtils.content[id] ?: ""
                    if (content.isBlank()) return
                    decryptedContent = content
                }

                val privateTags = blockList.privateTagsOrEmpty(decryptedContent).plus(element = listOf("p", pubkeyHex))
                val msg = Event.mapper.writeValueAsString(privateTags)
                AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), id)
                val eventContent = AmberUtils.content[id] ?: ""
                if (eventContent.isBlank()) return
                eventContent
            }

            var event = if (blockList != null) {
                PeopleListEvent.addUser(
                    earlierVersion = blockList,
                    pubKeyHex = pubkeyHex,
                    isPrivate = true,
                    pubKey = keyPair.pubKey.toHexKey(),
                    encryptedContent
                )
            } else {
                PeopleListEvent.createListWithUser(
                    name = PeopleListEvent.blockList,
                    pubKeyHex = pubkeyHex,
                    isPrivate = true,
                    pubKey = keyPair.pubKey.toHexKey(),
                    encryptedContent
                )
            }

            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) return
            event = PeopleListEvent(
                event.id,
                event.pubKey,
                event.createdAt,
                event.tags,
                event.content,
                eventContent
            )

            Client.send(event)
            LocalCache.consume(event)
        } else {
            val event = if (blockList != null) {
                PeopleListEvent.addUser(
                    earlierVersion = blockList,
                    pubKeyHex = pubkeyHex,
                    isPrivate = true,
                    privateKey = keyPair.privKey!!
                )
            } else {
                PeopleListEvent.createListWithUser(
                    name = PeopleListEvent.blockList,
                    pubKeyHex = pubkeyHex,
                    isPrivate = true,
                    privateKey = keyPair.privKey!!
                )
            }

            Client.send(event)
            LocalCache.consume(event)
        }

        live.invalidateData()
        saveable.invalidateData()
    }

    fun showUser(pubkeyHex: String) {
        val blockList = migrateHiddenUsersIfNeeded(getBlockList())

        if (blockList != null) {
            if (loginWithAmber) {
                val content = blockList.content
                val encryptedContent = if (content.isBlank()) {
                    val privateTags = listOf(listOf("p", pubkeyHex))
                    val msg = Event.mapper.writeValueAsString(privateTags)

                    AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), blockList.id)
                    val eventContent = AmberUtils.content[blockList.id] ?: ""
                    if (eventContent.isBlank()) return
                    eventContent
                } else {
                    var decryptedContent = AmberUtils.cachedDecryptedContent[blockList.id]
                    if (decryptedContent == null) {
                        AmberUtils.decrypt(blockList.content, keyPair.pubKey.toHexKey(), blockList.id)
                        val eventContent = AmberUtils.content[blockList.id] ?: ""
                        if (eventContent.isBlank()) return
                        decryptedContent = eventContent
                    }
                    val privateTags = blockList.privateTagsOrEmpty(decryptedContent).minus(element = listOf("p", pubkeyHex))
                    val msg = Event.mapper.writeValueAsString(privateTags)
                    AmberUtils.encrypt(msg, keyPair.pubKey.toHexKey(), blockList.id)
                    val eventContent = AmberUtils.content[blockList.id] ?: ""
                    if (eventContent.isBlank()) return
                    eventContent
                }

                var event = PeopleListEvent.addUser(
                    earlierVersion = blockList,
                    pubKeyHex = pubkeyHex,
                    isPrivate = true,
                    pubKey = keyPair.pubKey.toHexKey(),
                    encryptedContent
                )

                AmberUtils.openAmber(event)
                val eventContent = AmberUtils.content[event.id] ?: ""
                if (eventContent.isBlank()) return
                event = PeopleListEvent.create(event, eventContent)

                Client.send(event)
                LocalCache.consume(event)
            } else {
                val event = PeopleListEvent.removeUser(
                    earlierVersion = blockList,
                    pubKeyHex = pubkeyHex,
                    isPrivate = true,
                    privateKey = keyPair.privKey!!
                )

                Client.send(event)
                LocalCache.consume(event)
            }
        }

        transientHiddenUsers = (transientHiddenUsers - pubkeyHex).toImmutableSet()
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultZapType(zapType: LnZapEvent.ZapType) {
        defaultZapType = zapType
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultFileServer(server: ServersAvailable) {
        defaultFileServer = server
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultHomeFollowList(name: String) {
        defaultHomeFollowList = name
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultStoriesFollowList(name: String) {
        defaultStoriesFollowList = name
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultNotificationFollowList(name: String) {
        defaultNotificationFollowList = name
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultDiscoveryFollowList(name: String) {
        defaultDiscoveryFollowList = name
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeZapAmounts(newAmounts: List<Long>) {
        zapAmountChoices = newAmounts
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeReactionTypes(newTypes: List<String>) {
        reactionChoices = newTypes
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeZapPaymentRequest(newServer: Nip47URI?) {
        zapPaymentRequest = newServer
        live.invalidateData()
        saveable.invalidateData()
    }

    fun selectedUsersFollowList(listName: String?): Set<String>? {
        if (listName == GLOBAL_FOLLOWS) return null
        if (listName == KIND3_FOLLOWS) return userProfile().cachedFollowingKeySet()

        val privKey = keyPair.privKey

        return if (listName != null) {
            val aTag = ATag(
                PeopleListEvent.kind,
                userProfile().pubkeyHex,
                listName,
                null
            ).toTag()
            val list = LocalCache.addressables[aTag]
            if (list != null) {
                val publicHexList = (list.event as? PeopleListEvent)?.bookmarkedPeople() ?: emptySet()
                val privateHexList = privKey?.let {
                    (list.event as? PeopleListEvent)?.privateTaggedUsers(it)
                } ?: emptySet()

                (publicHexList + privateHexList).toSet()
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
    }

    fun selectedTagsFollowList(listName: String?): Set<String>? {
        if (listName == GLOBAL_FOLLOWS) return null
        if (listName == KIND3_FOLLOWS) return userProfile().cachedFollowingTagSet()

        val privKey = keyPair.privKey

        return if (listName != null) {
            val aTag = ATag(
                PeopleListEvent.kind,
                userProfile().pubkeyHex,
                listName,
                null
            ).toTag()
            val list = LocalCache.addressables[aTag]
            if (list != null) {
                val publicAddresses = list.event?.hashtags() ?: emptySet()
                val privateAddresses = privKey?.let {
                    (list.event as? GeneralListEvent)?.privateHashtags(it)
                } ?: emptySet()

                (publicAddresses + privateAddresses).toSet()
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
    }

    fun selectedGeohashesFollowList(listName: String?): Set<String>? {
        if (listName == GLOBAL_FOLLOWS) return null
        if (listName == KIND3_FOLLOWS) return userProfile().cachedFollowingGeohashSet()

        val privKey = keyPair.privKey

        return if (listName != null) {
            val aTag = ATag(
                PeopleListEvent.kind,
                userProfile().pubkeyHex,
                listName,
                null
            ).toTag()
            val list = LocalCache.addressables[aTag]
            if (list != null) {
                val publicAddresses = list.event?.geohashes() ?: emptySet()
                val privateAddresses = privKey?.let {
                    (list.event as? GeneralListEvent)?.privateGeohashes(it)
                } ?: emptySet()

                (publicAddresses + privateAddresses).toSet()
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
    }

    fun selectedCommunitiesFollowList(listName: String?): Set<String>? {
        if (listName == GLOBAL_FOLLOWS) return null
        if (listName == KIND3_FOLLOWS) return userProfile().cachedFollowingCommunitiesSet()

        val privKey = keyPair.privKey

        return if (listName != null) {
            val aTag = ATag(
                PeopleListEvent.kind,
                userProfile().pubkeyHex,
                listName,
                null
            ).toTag()
            val list = LocalCache.addressables[aTag]
            if (list != null) {
                val publicAddresses = list.event?.taggedAddresses()?.map { it.toTag() } ?: emptySet()
                val privateAddresses = privKey?.let {
                    (list.event as? GeneralListEvent)?.privateTaggedAddresses(it)?.map { it.toTag() }
                } ?: emptySet()

                (publicAddresses + privateAddresses).toSet()
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
    }

    fun selectedChatsFollowList(): Set<String> {
        val contactList = userProfile().latestContactList
        return contactList?.taggedEvents()?.toSet() ?: DefaultChannels
    }

    fun sendChangeChannel(name: String, about: String, picture: String, channel: Channel) {
        if (!isWriteable() && !loginWithAmber) return

        val metadata = ChannelCreateEvent.ChannelData(
            name,
            about,
            picture
        )

        var event = ChannelMetadataEvent.create(
            newChannelInfo = metadata,
            originalChannelIdHex = channel.idHex,
            keyPair = keyPair
        )
        if (loginWithAmber) {
            AmberUtils.openAmber(event)
            val eventContent = AmberUtils.content[event.id] ?: ""
            if (eventContent.isBlank()) return
            event = ChannelMetadataEvent.create(event, eventContent)
        }

        Client.send(event)
        LocalCache.consume(event)

        follow(channel)
    }

    fun unwrap(event: GiftWrapEvent): Event? {
        if (!isWriteable() && !loginWithAmber) return null

        if (loginWithAmber) {
            var decryptedContent = AmberUtils.cachedDecryptedContent[event.id]
            if (decryptedContent == null) {
                AmberUtils.decrypt(event.content, event.pubKey, event.id, SignerType.NIP44_DECRYPT)
            }
            decryptedContent = AmberUtils.cachedDecryptedContent[event.id] ?: ""
            if (decryptedContent.isEmpty()) return null
            return event.cachedGift(keyPair.pubKey, decryptedContent)
        }

        return event.cachedGift(keyPair.privKey!!)
    }

    fun unseal(event: SealedGossipEvent): Event? {
        if (!isWriteable() && !loginWithAmber) return null

        if (loginWithAmber) {
            var decryptedContent = AmberUtils.cachedDecryptedContent[event.id]
            if (decryptedContent == null) {
                AmberUtils.decrypt(event.content, event.pubKey, event.id, SignerType.NIP44_DECRYPT)
            }
            decryptedContent = AmberUtils.cachedDecryptedContent[event.id] ?: ""
            if (decryptedContent.isEmpty()) return null
            return event.cachedGossip(keyPair.pubKey, decryptedContent)
        }

        return event.cachedGossip(keyPair.privKey!!)
    }

    fun decryptContent(note: Note): String? {
        val privKey = keyPair.privKey
        val event = note.event
        return if (event is PrivateDmEvent && privKey != null) {
            event.plainContent(privKey, event.talkingWith(userProfile().pubkeyHex).hexToByteArray())
        } else if (event is LnZapRequestEvent && privKey != null) {
            decryptZapContentAuthor(note)?.content()
        } else {
            event?.content()
        }
    }

    fun decryptContentWithAmber(note: Note): String? = with(Dispatchers.IO) {
        val event = note.event
        return when (event) {
            is PrivateDmEvent -> {
                if (AmberUtils.cachedDecryptedContent[event.id] == null) {
                    AmberUtils.decryptDM(
                        event.content,
                        event.talkingWith(userProfile().pubkeyHex),
                        event.id
                    )
                    AmberUtils.cachedDecryptedContent[event.id]
                } else {
                    AmberUtils.cachedDecryptedContent[event.id]
                }
            }
            is LnZapRequestEvent -> {
                decryptZapContentAuthor(note)?.content()
            }
            else -> {
                event?.content()
            }
        }
    }

    fun decryptZapContentAuthor(note: Note): Event? {
        val event = note.event
        val loggedInPrivateKey = keyPair.privKey

        if (loginWithAmber && event is LnZapRequestEvent && event.isPrivateZap()) {
            val decryptedContent = AmberUtils.cachedDecryptedContent[event.id]
            if (decryptedContent != null) {
                return try {
                    Event.fromJson(decryptedContent)
                } catch (e: Exception) {
                    null
                }
            }
            AmberUtils.decryptZapEvent(event)
            return null
        }

        return if (event is LnZapRequestEvent && loggedInPrivateKey != null && event.isPrivateZap()) {
            val recipientPK = event.zappedAuthor().firstOrNull()
            val recipientPost = event.zappedPost().firstOrNull()
            if (recipientPK == userProfile().pubkeyHex) {
                // if the receiver is logged in, these are the params.
                val privateKeyToUse = loggedInPrivateKey
                val pubkeyToUse = event.pubKey

                event.getPrivateZapEvent(privateKeyToUse, pubkeyToUse)
            } else {
                // if the sender is logged in, these are the params
                val altPubkeyToUse = recipientPK
                val altPrivateKeyToUse = if (recipientPost != null) {
                    LnZapRequestEvent.createEncryptionPrivateKey(
                        loggedInPrivateKey.toHexKey(),
                        recipientPost,
                        event.createdAt
                    )
                } else if (recipientPK != null) {
                    LnZapRequestEvent.createEncryptionPrivateKey(
                        loggedInPrivateKey.toHexKey(),
                        recipientPK,
                        event.createdAt
                    )
                } else {
                    null
                }

                try {
                    if (altPrivateKeyToUse != null && altPubkeyToUse != null) {
                        val altPubKeyFromPrivate = CryptoUtils.pubkeyCreate(altPrivateKeyToUse).toHexKey()

                        if (altPubKeyFromPrivate == event.pubKey) {
                            val result = event.getPrivateZapEvent(altPrivateKeyToUse, altPubkeyToUse)

                            if (result == null) {
                                Log.w(
                                    "Private ZAP Decrypt",
                                    "Fail to decrypt Zap from ${note.author?.toBestDisplayName()} ${note.idNote()}"
                                )
                            }
                            result
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("Account", "Failed to create pubkey for ZapRequest ${event.id}", e)
                    null
                }
            }
        } else {
            null
        }
    }

    fun addDontTranslateFrom(languageCode: String) {
        dontTranslateFrom = dontTranslateFrom.plus(languageCode)
        liveLanguages.invalidateData()

        saveable.invalidateData()
    }

    fun updateTranslateTo(languageCode: String) {
        translateTo = languageCode
        liveLanguages.invalidateData()

        saveable.invalidateData()
    }

    fun prefer(source: String, target: String, preference: String) {
        languagePreferences = languagePreferences + Pair("$source,$target", preference)
        saveable.invalidateData()
    }

    fun preferenceBetween(source: String, target: String): String? {
        return languagePreferences.get("$source,$target")
    }

    private fun updateContactListTo(newContactList: ContactListEvent?) {
        if (newContactList == null || newContactList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupContactList?.id != newContactList.id) {
            backupContactList = newContactList
            saveable.invalidateData()
        }
    }

    // Takes a User's relay list and adds the types of feeds they are active for.
    fun activeRelays(): Array<Relay>? {
        var usersRelayList = userProfile().latestContactList?.relays()?.map {
            val localFeedTypes = localRelays.firstOrNull() { localRelay -> localRelay.url == it.key }?.feedTypes ?: FeedType.values().toSet()
            Relay(it.key, it.value.read, it.value.write, localFeedTypes, proxy)
        } ?: return null

        // Ugly, but forces nostr.band as the only search-supporting relay today.
        // TODO: Remove when search becomes more available.
        val searchRelays = usersRelayList.filter { it.url.removeSuffix("/") in Constants.forcedRelaysForSearchSet }
        val hasSearchRelay = usersRelayList.any { it.activeTypes.contains(FeedType.SEARCH) }
        if (!hasSearchRelay && searchRelays.isEmpty()) {
            usersRelayList = usersRelayList + Constants.forcedRelayForSearch.map {
                Relay(
                    it.url,
                    it.read,
                    it.write,
                    it.feedTypes,
                    proxy
                )
            }
        }

        return usersRelayList.toTypedArray()
    }

    fun convertLocalRelays(): Array<Relay> {
        return localRelays.map {
            Relay(it.url, it.read, it.write, it.feedTypes, proxy)
        }.toTypedArray()
    }

    fun activeGlobalRelays(): Array<String> {
        return (activeRelays() ?: convertLocalRelays()).filter { it.activeTypes.contains(FeedType.GLOBAL) }
            .map { it.url }
            .toTypedArray()
    }

    fun activeWriteRelays(): List<Relay> {
        return (activeRelays() ?: convertLocalRelays()).filter { it.write }
    }

    fun reconnectIfRelaysHaveChanged() {
        val newRelaySet = activeRelays() ?: convertLocalRelays()
        if (!Client.isSameRelaySetConfig(newRelaySet)) {
            Client.disconnect()
            Client.connect(newRelaySet)
            RelayPool.requestAndWatch()
        }
    }

    fun isAllHidden(users: Set<HexKey>): Boolean {
        return users.all { isHidden(it) }
    }

    fun isHidden(user: User) = isHidden(user.pubkeyHex)

    fun isHidden(userHex: String): Boolean {
        val blockList = getBlockList()
        val decryptedContent = blockList?.decryptedContent ?: ""

        if (loginWithAmber) {
            if (decryptedContent.isBlank()) return false
            return (blockList?.publicAndPrivateUsers(decryptedContent)?.contains(userHex) ?: false) || userHex in transientHiddenUsers
        }
        return (blockList?.publicAndPrivateUsers(keyPair.privKey)?.contains(userHex) ?: false) || userHex in transientHiddenUsers
    }

    fun followingKeySet(): Set<HexKey> {
        return userProfile().cachedFollowingKeySet()
    }

    fun followingTagSet(): Set<HexKey> {
        return userProfile().cachedFollowingTagSet()
    }

    fun isAcceptable(user: User): Boolean {
        if (!warnAboutPostsWithReports) {
            return !isHidden(user) && // if user hasn't hided this author
                user.reportsBy(userProfile()).isEmpty() // if user has not reported this post
        }
        return !isHidden(user) && // if user hasn't hided this author
            user.reportsBy(userProfile()).isEmpty() && // if user has not reported this post
            user.countReportAuthorsBy(followingKeySet()) < 5
    }

    private fun isAcceptableDirect(note: Note): Boolean {
        if (!warnAboutPostsWithReports) {
            return !note.hasReportsBy(userProfile())
        }
        return !note.hasReportsBy(userProfile()) && // if user has not reported this post
            note.countReportAuthorsBy(followingKeySet()) < 5 // if it has 5 reports by reliable users
    }

    fun isFollowing(user: User): Boolean {
        return user.pubkeyHex in followingKeySet()
    }

    fun isFollowing(user: HexKey): Boolean {
        return user in followingKeySet()
    }

    fun isAcceptable(note: Note): Boolean {
        return note.author?.let { isAcceptable(it) } ?: true && // if user hasn't hided this author
            isAcceptableDirect(note) &&
            (
                (note.event !is RepostEvent && note.event !is GenericRepostEvent) ||
                    (note.replyTo?.firstOrNull { isAcceptableDirect(it) } != null)
                ) // is not a reaction about a blocked post
    }

    fun getRelevantReports(note: Note): Set<Note> {
        val followsPlusMe = userProfile().latestContactList?.verifiedFollowKeySetAndMe ?: emptySet()

        val innerReports = if (note.event is RepostEvent || note.event is GenericRepostEvent) {
            note.replyTo?.map { getRelevantReports(it) }?.flatten() ?: emptyList()
        } else {
            emptyList()
        }

        return (
            note.reportsBy(followsPlusMe) +
                (
                    note.author?.reportsBy(followsPlusMe) ?: emptyList()
                    ) + innerReports
            ).toSet()
    }

    fun saveRelayList(value: List<RelaySetupInfo>) {
        try {
            localRelays = value.toSet()
            return sendNewRelayList(value.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) })
        } finally {
            saveable.invalidateData()
        }
    }

    fun setHideDeleteRequestDialog() {
        hideDeleteRequestDialog = true
        saveable.invalidateData()
    }

    fun setHideNIP24WarningDialog() {
        hideNIP24WarningDialog = true
        saveable.invalidateData()
    }

    fun setHideBlockAlertDialog() {
        hideBlockAlertDialog = true
        saveable.invalidateData()
    }

    fun updateShowSensitiveContent(show: Boolean?) {
        showSensitiveContent = show
        saveable.invalidateData()
        live.invalidateData()
    }

    fun markAsRead(route: String, timestampInSecs: Long) {
        val lastTime = lastReadPerRoute[route]
        if (lastTime == null || timestampInSecs > lastTime) {
            lastReadPerRoute = lastReadPerRoute + Pair(route, timestampInSecs)
            saveable.invalidateData()
            liveLastRead.invalidateData()
        }
    }

    fun loadLastRead(route: String): Long {
        return lastReadPerRoute[route] ?: 0
    }

    fun registerObservers() {
        // Observes relays to restart connections
        userProfile().live().relays.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                reconnectIfRelaysHaveChanged()
            }
        }

        // saves contact list for the next time.
        userProfile().live().follows.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                updateContactListTo(userProfile().latestContactList)
            }
        }

        // imports transient blocks due to spam.
        LocalCache.antiSpam.liveSpam.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                it.cache.spamMessages.snapshot().values.forEach {
                    if (it.pubkeyHex !in transientHiddenUsers && it.duplicatedMessages.size >= 5) {
                        if (it.pubkeyHex != userProfile().pubkeyHex && it.pubkeyHex !in followingKeySet()) {
                            transientHiddenUsers = (transientHiddenUsers + it.pubkeyHex).toImmutableSet()
                            live.invalidateData()
                        }
                    }
                }
            }
        }
    }

    init {
        Log.d("Init", "Account")
        backupContactList?.let {
            println("Loading saved contacts ${it.toJson()}")

            if (userProfile().latestContactList == null) {
                GlobalScope.launch(Dispatchers.IO) {
                    LocalCache.consume(it)
                }
            }
        }
    }
}

class AccountLiveData(private val account: Account) : LiveData<AccountState>(AccountState(account)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.Default)

    fun invalidateData() {
        bundler.invalidate() {
            if (hasActiveObservers()) {
                refresh()
            }
        }
    }

    fun refresh() {
        postValue(AccountState(account))
    }
}

@Immutable
class AccountState(val account: Account)
