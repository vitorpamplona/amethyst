package com.vitorpamplona.amethyst.model

import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import com.vitorpamplona.amethyst.OptOutFromFilters
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.KeyPair
import com.vitorpamplona.amethyst.service.NostrLnZapPaymentResponseDataSource
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.combineWith
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

val GLOBAL_FOLLOWS = " Global "
val KIND3_FOLLOWS = " All Follows "

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
    var backupContactList: ContactListEvent? = null,
    var proxy: Proxy?,
    var proxyPort: Int,
    var showSensitiveContent: Boolean? = null,
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: Boolean = true,
    var lastReadPerRoute: Map<String, Long> = mapOf<String, Long>(),
    var settings: Settings = Settings()
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

    val liveHiddenUsers: LiveData<LiveHiddenUsers> = live.combineWith(getBlockListNote().live().metadata) { localLive, liveMuteListEvent ->
        val liveBlockedUsers = (liveMuteListEvent?.note?.event as? PeopleListEvent)?.publicAndPrivateUsers(keyPair.privKey)
        LiveHiddenUsers(
            hiddenUsers = liveBlockedUsers ?: persistentSetOf(),
            spammers = localLive?.account?.transientHiddenUsers ?: persistentSetOf(),
            showSensitiveContent = showSensitiveContent
        )
    }.distinctUntilChanged()

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
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            val event = ContactListEvent.updateRelayList(
                earlierVersion = contactList,
                relayUse = relays,
                privateKey = keyPair.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        } else {
            val event = ContactListEvent.createFromScratch(
                followUsers = listOf(),
                followTags = listOf(),
                followGeohashes = listOf(),
                followCommunities = listOf(),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                privateKey = keyPair.privKey!!
            )

            // Keep this local to avoid erasing a good contact list.
            // Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun sendNewUserMetadata(toString: String, identities: List<IdentityClaim>) {
        if (!isWriteable()) return

        keyPair.privKey?.let {
            val event = MetadataEvent.create(toString, identities, keyPair.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }
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
        if (!isWriteable()) return

        if (hasReacted(note, reaction)) {
            // has already liked this note
            return
        }

        if (reaction.startsWith(":")) {
            val emojiUrl = EmojiUrl.decode(reaction)
            if (emojiUrl != null) {
                note.event?.let {
                    val event = ReactionEvent.create(emojiUrl, it, keyPair.privKey!!)
                    Client.send(event)
                    LocalCache.consume(event)
                }

                return
            }
        }

        note.event?.let {
            val event = ReactionEvent.create(reaction, it, keyPair.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun createZapRequestFor(note: Note, pollOption: Int?, message: String = "", zapType: LnZapEvent.ZapType): LnZapRequestEvent? {
        if (!isWriteable()) return null

        note.event?.let { event ->
            return LnZapRequestEvent.create(
                event,
                userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                    ?: localRelays.map { it.url }.toSet(),
                keyPair.privKey!!,
                pollOption,
                message,
                zapType
            )
        }
        return null
    }

    fun hasWalletConnectSetup(): Boolean {
        return zapPaymentRequest != null
    }

    fun isNIP47Author(pubkeyHex: String?): Boolean {
        val privKey = zapPaymentRequest?.secret?.hexToByteArray() ?: keyPair.privKey

        if (privKey == null) return false

        val pubKey = CryptoUtils.pubkeyCreate(privKey).toHexKey()
        return (pubKey == pubkeyHex)
    }

    fun decryptZapPaymentResponseEvent(zapResponseEvent: LnZapPaymentResponseEvent): Response? {
        val myNip47 = zapPaymentRequest ?: return null

        val privKey = myNip47.secret?.hexToByteArray() ?: keyPair.privKey
        val pubKey = myNip47.pubKeyHex.hexToByteArray()

        if (privKey == null) return null

        return zapResponseEvent.response(privKey, pubKey)
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
        if (!isWriteable()) return

        zapPaymentRequest?.let { nip47 ->
            val event = LnZapPaymentRequestEvent.create(bolt11, nip47.pubKeyHex, nip47.secret?.hexToByteArray() ?: keyPair.privKey!!)

            val wcListener = NostrLnZapPaymentResponseDataSource(
                fromServiceHex = nip47.pubKeyHex,
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                authSigningKey = nip47.secret?.hexToByteArray() ?: keyPair.privKey!!
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
        if (!isWriteable()) return null

        return LnZapRequestEvent.create(
            userPubKeyHex,
            userProfile().latestContactList?.relays()?.keys?.ifEmpty { null } ?: localRelays.map { it.url }.toSet(),
            keyPair.privKey!!,
            message,
            zapType
        )
    }

    fun report(note: Note, type: ReportEvent.ReportType, content: String = "") {
        if (!isWriteable()) return

        if (note.hasReacted(userProfile(), "⚠️")) {
            // has already liked this note
            return
        }

        note.event?.let {
            val event = ReactionEvent.createWarning(it, keyPair.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }

        note.event?.let {
            val event = ReportEvent.create(it, type, keyPair.privKey!!, content = content)
            Client.send(event)
            LocalCache.consume(event, null)
        }
    }

    fun report(user: User, type: ReportEvent.ReportType) {
        if (!isWriteable()) return

        if (user.hasReport(userProfile(), type)) {
            // has already reported this note
            return
        }

        val event = ReportEvent.create(user.pubkeyHex, type, keyPair.privKey!!)
        Client.send(event)
        LocalCache.consume(event, null)
    }

    fun delete(note: Note) {
        delete(listOf(note))
    }

    fun delete(notes: List<Note>) {
        if (!isWriteable()) return

        val myNotes = notes.filter { it.author == userProfile() }.map { it.idHex }

        if (myNotes.isNotEmpty()) {
            val event = DeletionEvent.create(myNotes, keyPair.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun createHTTPAuthorization(url: String, method: String, body: String? = null): HTTPAuthorizationEvent? {
        if (!isWriteable()) return null

        return HTTPAuthorizationEvent.create(url, method, body, keyPair.privKey!!)
    }

    fun boost(note: Note) {
        if (!isWriteable()) return

        if (note.hasBoostedInTheLast5Minutes(userProfile())) {
            // has already bosted in the past 5mins
            return
        }

        note.event?.let {
            if (it.kind() == 1) {
                val event = RepostEvent.create(it, keyPair.privKey!!)
                Client.send(event)
                LocalCache.consume(event)
            } else {
                val event = GenericRepostEvent.create(it, keyPair.privKey!!)
                Client.send(event)
                LocalCache.consume(event)
            }
        }
    }

    fun broadcast(note: Note) {
        note.event?.let {
            Client.send(it)
        }
    }

    private fun migrateCommunitiesAndChannelsIfNeeded(latestContactList: ContactListEvent?): ContactListEvent? {
        if (latestContactList == null) return latestContactList

        var returningContactList: ContactListEvent = latestContactList

        if (followingCommunities.isNotEmpty()) {
            followingCommunities.forEach {
                ATag.parse(it, null)?.let {
                    returningContactList = ContactListEvent.followAddressableEvent(returningContactList, it, keyPair.privKey!!)
                }
            }
            followingCommunities = emptySet()
        }

        if (followingChannels.isNotEmpty()) {
            followingChannels.forEach {
                returningContactList = ContactListEvent.followEvent(returningContactList, it, keyPair.privKey!!)
            }
            followingChannels = emptySet()
        }

        return returningContactList
    }

    fun follow(user: User) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        val event = if (contactList != null) {
            ContactListEvent.followUser(contactList, user.pubkeyHex, keyPair.privKey!!)
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(Contact(user.pubkeyHex, null)),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                privateKey = keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun follow(channel: Channel) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        val event = if (contactList != null) {
            ContactListEvent.followEvent(contactList, channel.idHex, keyPair.privKey!!)
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList().plus(channel.idHex),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                privateKey = keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun follow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        val event = if (contactList != null) {
            ContactListEvent.followAddressableEvent(contactList, community.address, keyPair.privKey!!)
        } else {
            val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = listOf(community.address),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                privateKey = keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun followHashtag(tag: String) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        val event = if (contactList != null) {
            ContactListEvent.followHashtag(
                contactList,
                tag,
                keyPair.privKey!!
            )
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = listOf(tag),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                privateKey = keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun followGeohash(geohash: String) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        val event = if (contactList != null) {
            ContactListEvent.followGeohash(
                contactList,
                geohash,
                keyPair.privKey!!
            )
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = listOf(geohash),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                privateKey = keyPair.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun unfollow(user: User) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            val event = ContactListEvent.unfollowUser(
                contactList,
                user.pubkeyHex,
                keyPair.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollowHashtag(tag: String) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            val event = ContactListEvent.unfollowHashtag(
                contactList,
                tag,
                keyPair.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollowGeohash(geohash: String) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            val event = ContactListEvent.unfollowGeohash(
                contactList,
                geohash,
                keyPair.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollow(channel: Channel) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            val event = ContactListEvent.unfollowEvent(
                contactList,
                channel.idHex,
                keyPair.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = migrateCommunitiesAndChannelsIfNeeded(userProfile().latestContactList)

        if (contactList != null && contactList.tags.isNotEmpty()) {
            val event = ContactListEvent.unfollowAddressableEvent(
                contactList,
                community.address,
                keyPair.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun createNip95(byteArray: ByteArray, headerInfo: FileHeader): Pair<FileStorageEvent, FileStorageHeaderEvent>? {
        if (!isWriteable()) return null

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

    fun sendNip95(data: FileStorageEvent, signedEvent: FileStorageHeaderEvent, relayList: List<Relay>? = null): Note? {
        if (!isWriteable()) return null

        Client.send(data, relayList = relayList)
        LocalCache.consume(data, null)

        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

        return LocalCache.notes[signedEvent.id]
    }

    fun sendHeader(headerInfo: FileHeader, relayList: List<Relay>? = null): Note? {
        if (!isWriteable()) return null

        val signedEvent = FileHeaderEvent.create(
            url = headerInfo.url,
            mimeType = headerInfo.mimeType,
            hash = headerInfo.hash,
            size = headerInfo.size.toString(),
            dimensions = headerInfo.dim,
            blurhash = headerInfo.blurHash,
            description = headerInfo.description,
            sensitiveContent = headerInfo.sensitiveContent,
            privateKey = keyPair.privKey!!
        )

        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

        return LocalCache.notes[signedEvent.id]
    }

    fun sendPost(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        tags: List<String>? = null,
        zapReceiver: String? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        replyingTo: String?,
        root: String?,
        directMentions: Set<HexKey>,
        relayList: List<Relay>? = null,
        geohash: String? = null
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.filter { it.address() == null }?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        val signedEvent = TextNoteEvent.create(
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
            privateKey = keyPair.privKey!!
        )

        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent)
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
        zapReceiver: String? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        relayList: List<Relay>? = null,
        geohash: String? = null
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        val signedEvent = PollNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            privateKey = keyPair.privKey!!,
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
        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent)
    }

    fun sendChannelMessage(message: String, toChannel: String, replyTo: List<Note>?, mentions: List<User>?, zapReceiver: String? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable()) return

        // val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        val signedEvent = ChannelMessageEvent.create(
            message = message,
            channel = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            privateKey = keyPair.privKey!!
        )
        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendLiveMessage(message: String, toChannel: ATag, replyTo: List<Note>?, mentions: List<User>?, zapReceiver: String? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable()) return

        // val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        val signedEvent = LiveActivitiesChatMessageEvent.create(
            message = message,
            activity = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            privateKey = keyPair.privKey!!
        )
        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendPrivateMessage(message: String, toUser: User, replyingTo: Note? = null, mentions: List<User>?, zapReceiver: String? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable()) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        val signedEvent = PrivateDmEvent.create(
            recipientPubKey = toUser.pubkey(),
            publishedRecipientPubKey = toUser.pubkey(),
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            privateKey = keyPair.privKey!!,
            advertiseNip18 = false
        )
        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendCreateNewChannel(name: String, about: String, picture: String) {
        if (!isWriteable()) return

        val metadata = ChannelCreateEvent.ChannelData(
            name,
            about,
            picture
        )

        val event = ChannelCreateEvent.create(
            channelInfo = metadata,
            privateKey = keyPair.privKey!!
        )

        Client.send(event)
        LocalCache.consume(event)

        LocalCache.getChannelIfExists(event.id)?.let {
            follow(it)
        }
    }

    fun removeEmojiPack(usersEmojiList: Note, emojiList: Note) {
        if (!isWriteable()) return

        val noteEvent = usersEmojiList.event
        if (noteEvent !is EmojiPackSelectionEvent) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        val event = EmojiPackSelectionEvent.create(
            noteEvent.taggedAddresses().filter { it != emojiListEvent.address() },
            keyPair.privKey!!
        )

        Client.send(event)
        LocalCache.consume(event)
    }

    fun addEmojiPack(usersEmojiList: Note, emojiList: Note) {
        if (!isWriteable()) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        val event = if (usersEmojiList.event == null) {
            EmojiPackSelectionEvent.create(
                listOf(emojiListEvent.address()),
                keyPair.privKey!!
            )
        } else {
            val noteEvent = usersEmojiList.event
            if (noteEvent !is EmojiPackSelectionEvent) return

            if (noteEvent.taggedAddresses().any { it == emojiListEvent.address() }) {
                return
            }

            EmojiPackSelectionEvent.create(
                noteEvent.taggedAddresses().plus(emojiListEvent.address()),
                keyPair.privKey!!
            )
        }

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
        if (!isWriteable()) return null

        return RelayAuthEvent.create(relay.url, challenge, keyPair.privKey!!)
    }

    fun removePublicBookmark(note: Note) {
        if (!isWriteable()) return

        val bookmarks = userProfile().latestBookmarkList

        val event = if (note is AddressableNote) {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
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

    fun isInPrivateBookmarks(note: Note): Boolean {
        if (!isWriteable()) return false

        if (note is AddressableNote) {
            return userProfile().latestBookmarkList?.privateTaggedAddresses(keyPair.privKey!!)
                ?.contains(note.address) == true
        } else {
            return userProfile().latestBookmarkList?.privateTaggedEvents(keyPair.privKey!!)
                ?.contains(note.idHex) == true
        }
    }

    fun isInPublicBookmarks(note: Note): Boolean {
        if (!isWriteable()) return false

        if (note is AddressableNote) {
            return userProfile().latestBookmarkList?.taggedAddresses()?.contains(note.address) == true
        } else {
            return userProfile().latestBookmarkList?.taggedEvents()?.contains(note.idHex) == true
        }
    }

    fun getBlockListNote(): AddressableNote {
        val aTag = ATag(PeopleListEvent.kind, userProfile().pubkeyHex, PeopleListEvent.blockList, null)
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

        live.invalidateData()
        saveable.invalidateData()
    }

    fun showUser(pubkeyHex: String) {
        val blockList = migrateHiddenUsersIfNeeded(getBlockList())

        if (blockList != null) {
            val event = PeopleListEvent.removeUser(
                earlierVersion = blockList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                privateKey = keyPair.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
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
            val aTag = ATag(PeopleListEvent.kind, userProfile().pubkeyHex, listName, null).toTag()
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
            val aTag = ATag(PeopleListEvent.kind, userProfile().pubkeyHex, listName, null).toTag()
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
            val aTag = ATag(PeopleListEvent.kind, userProfile().pubkeyHex, listName, null).toTag()
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
            val aTag = ATag(PeopleListEvent.kind, userProfile().pubkeyHex, listName, null).toTag()
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
        if (!isWriteable()) return

        val metadata = ChannelCreateEvent.ChannelData(
            name,
            about,
            picture
        )

        val event = ChannelMetadataEvent.create(
            newChannelInfo = metadata,
            originalChannelIdHex = channel.idHex,
            privateKey = keyPair.privKey!!
        )

        Client.send(event)
        LocalCache.consume(event)

        follow(channel)
    }

    fun decryptContent(note: Note): String? {
        val event = note.event
        return if (event is PrivateDmEvent && keyPair.privKey != null) {
            event.plainContent(keyPair.privKey!!, event.talkingWith(userProfile().pubkeyHex).hexToByteArray())
        } else if (event is LnZapRequestEvent && keyPair.privKey != null) {
            decryptZapContentAuthor(note)?.content()
        } else {
            event?.content()
        }
    }

    fun decryptZapContentAuthor(note: Note): Event? {
        val event = note.event
        val loggedInPrivateKey = keyPair.privKey

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
        if (usersRelayList.none { it.activeTypes.contains(FeedType.SEARCH) }) {
            usersRelayList = usersRelayList + Relay(
                Constants.forcedRelayForSearch.url,
                Constants.forcedRelayForSearch.read,
                Constants.forcedRelayForSearch.write,
                Constants.forcedRelayForSearch.feedTypes,
                proxy
            )
        }

        return usersRelayList.toTypedArray()
    }

    fun convertLocalRelays(): Array<Relay> {
        return localRelays.map {
            Relay(it.url, it.read, it.write, it.feedTypes, proxy)
        }.toTypedArray()
    }

    fun convertGlobalRelays(): Array<String> {
        return localRelays.filter { it.feedTypes.contains(FeedType.GLOBAL) }
            .map { it.url }
            .toTypedArray()
    }

    fun reconnectIfRelaysHaveChanged() {
        val newRelaySet = activeRelays() ?: convertLocalRelays()
        if (!Client.isSameRelaySetConfig(newRelaySet)) {
            Client.disconnect()
            Client.connect(newRelaySet)
            RelayPool.requestAndWatch()
        }
    }

    fun isHidden(user: User) = isHidden(user.pubkeyHex)
    fun isHidden(userHex: String): Boolean {
        val blockList = getBlockList()

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
        localRelays = value.toSet()
        sendNewRelayList(value.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) })

        saveable.invalidateData()
    }

    fun setHideDeleteRequestDialog() {
        hideDeleteRequestDialog = true
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
