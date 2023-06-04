package com.vitorpamplona.amethyst.model

import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.NostrLnZapPaymentResponseDataSource
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.ui.actions.ServersAvailable
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.Nip47URI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nostr.postr.Persona
import nostr.postr.Utils
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
    val loggedIn: Persona,
    var followingChannels: Set<String> = DefaultChannels,
    var hiddenUsers: Set<String> = setOf(),
    var localRelays: Set<RelaySetupInfo> = Constants.defaultRelays.toSet(),
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
    var zapAmountChoices: List<Long> = listOf(500L, 1000L, 5000L),
    var defaultZapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PRIVATE,
    var defaultFileServer: ServersAvailable = ServersAvailable.NOSTR_BUILD,
    var defaultHomeFollowList: String = KIND3_FOLLOWS,
    var defaultStoriesFollowList: String = GLOBAL_FOLLOWS,
    var defaultNotificationFollowList: String = GLOBAL_FOLLOWS,
    var zapPaymentRequest: Nip47URI? = null,
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var backupContactList: ContactListEvent? = null,
    var proxy: Proxy?,
    var proxyPort: Int,
    var showSensitiveContent: Boolean? = null
) {
    var transientHiddenUsers: Set<String> = setOf()

    // Observers line up here.
    val live: AccountLiveData = AccountLiveData(this)
    val liveLanguages: AccountLiveData = AccountLiveData(this)
    val saveable: AccountLiveData = AccountLiveData(this)

    var userProfileCache: User? = null

    fun userProfile(): User {
        return userProfileCache ?: run {
            val myUser: User = LocalCache.getOrCreateUser(loggedIn.pubKey.toHexKey())
            userProfileCache = myUser
            myUser
        }
    }

    fun followingChannels(): List<Channel> {
        return followingChannels.map { LocalCache.getOrCreateChannel(it) }
    }

    fun hiddenUsers(): List<User> {
        return (hiddenUsers + transientHiddenUsers).map { LocalCache.getOrCreateUser(it) }
    }

    fun isWriteable(): Boolean {
        return loggedIn.privKey != null
    }

    fun sendNewRelayList(relays: Map<String, ContactListEvent.ReadWrite>) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList
        val follows = contactList?.follows() ?: emptyList()
        val followsTags = contactList?.unverifiedFollowTagSet() ?: emptyList()

        if (contactList != null && follows.isNotEmpty()) {
            val event = ContactListEvent.create(
                follows,
                followsTags,
                relays,
                loggedIn.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        } else {
            val event = ContactListEvent.create(listOf(), listOf(), relays, loggedIn.privKey!!)

            // Keep this local to avoid erasing a good contact list.
            // Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun sendNewUserMetadata(toString: String, identities: List<IdentityClaim>) {
        if (!isWriteable()) return

        loggedIn.privKey?.let {
            val event = MetadataEvent.create(toString, identities, loggedIn.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun reactionTo(note: Note): List<Note> {
        return note.reactedBy(userProfile(), "+")
    }

    fun hasBoosted(note: Note): Boolean {
        return boostsTo(note).isNotEmpty()
    }

    fun boostsTo(note: Note): List<Note> {
        return note.boostedBy(userProfile())
    }

    fun hasReacted(note: Note): Boolean {
        return note.hasReacted(userProfile(), "+")
    }

    fun reactTo(note: Note) {
        if (!isWriteable()) return

        if (hasReacted(note)) {
            // has already liked this note
            return
        }

        note.event?.let {
            val event = ReactionEvent.createLike(it, loggedIn.privKey!!)
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
                loggedIn.privKey!!,
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
        val privKey = zapPaymentRequest?.secret?.hexToByteArray() ?: loggedIn.privKey

        if (privKey == null) return false

        val pubKey = Utils.pubkeyCreate(privKey).toHexKey()
        return (pubKey == pubkeyHex)
    }

    fun decryptZapPaymentResponseEvent(zapResponseEvent: LnZapPaymentResponseEvent): Response? {
        val myNip47 = zapPaymentRequest ?: return null

        val privKey = myNip47.secret?.hexToByteArray() ?: loggedIn.privKey
        val pubKey = myNip47.pubKeyHex.hexToByteArray()

        if (privKey == null) return null

        return zapResponseEvent.response(privKey, pubKey)
    }

    fun calculateIfNoteWasZappedByAccount(zappedNote: Note?): Boolean {
        return zappedNote?.isZappedBy(userProfile(), this) == true
    }

    fun calculateZappedAmount(zappedNote: Note?): BigDecimal {
        val privKey = zapPaymentRequest?.secret?.hexToByteArray() ?: loggedIn.privKey
        val pubKey = zapPaymentRequest?.pubKeyHex?.hexToByteArray()
        return zappedNote?.zappedAmount(privKey, pubKey) ?: BigDecimal.ZERO
    }

    fun sendZapPaymentRequestFor(bolt11: String, zappedNote: Note?, onResponse: (Response?) -> Unit) {
        if (!isWriteable()) return

        zapPaymentRequest?.let { nip47 ->
            val event = LnZapPaymentRequestEvent.create(bolt11, nip47.pubKeyHex, nip47.secret?.hexToByteArray() ?: loggedIn.privKey!!)

            val wcListener = NostrLnZapPaymentResponseDataSource(
                fromServiceHex = nip47.pubKeyHex,
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                authSigningKey = nip47.secret?.hexToByteArray() ?: loggedIn.privKey!!
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
            loggedIn.privKey!!,
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
            val event = ReactionEvent.createWarning(it, loggedIn.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }

        note.event?.let {
            val event = ReportEvent.create(it, type, loggedIn.privKey!!, content = content)
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

        val event = ReportEvent.create(user.pubkeyHex, type, loggedIn.privKey!!)
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
            val event = DeletionEvent.create(myNotes, loggedIn.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun boost(note: Note) {
        if (!isWriteable()) return

        if (note.hasBoostedInTheLast5Minutes(userProfile())) {
            // has already bosted in the past 5mins
            return
        }

        note.event?.let {
            val event = RepostEvent.create(it, loggedIn.privKey!!)
            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun broadcast(note: Note) {
        note.event?.let {
            Client.send(it)
        }
    }

    fun follow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList
        val followingUsers = contactList?.follows() ?: emptyList()
        val followingTags = contactList?.unverifiedFollowTagSet() ?: emptyList()

        val event = if (contactList != null) {
            ContactListEvent.create(
                followingUsers.plus(Contact(user.pubkeyHex, null)),
                followingTags,
                contactList.relays(),
                loggedIn.privKey!!
            )
        } else {
            val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
            ContactListEvent.create(
                listOf(Contact(user.pubkeyHex, null)),
                followingTags,
                relays,
                loggedIn.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun follow(tag: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList
        val followingUsers = contactList?.follows() ?: emptyList()
        val followingTags = contactList?.unverifiedFollowTagSet() ?: emptyList()

        val event = if (contactList != null) {
            ContactListEvent.create(
                followingUsers,
                followingTags.plus(tag),
                contactList.relays(),
                loggedIn.privKey!!
            )
        } else {
            val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
            ContactListEvent.create(
                followingUsers,
                followingTags.plus(tag),
                relays,
                loggedIn.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun unfollow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList
        val followingUsers = contactList?.follows() ?: emptyList()
        val followingTags = contactList?.unverifiedFollowTagSet() ?: emptyList()

        if (contactList != null && (followingUsers.isNotEmpty() || followingTags.isNotEmpty())) {
            val event = ContactListEvent.create(
                followingUsers.filter { it.pubKeyHex != user.pubkeyHex },
                followingTags,
                contactList.relays(),
                loggedIn.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun unfollow(tag: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList
        val followingUsers = contactList?.follows() ?: emptyList()
        val followingTags = contactList?.unverifiedFollowTagSet() ?: emptyList()

        if (contactList != null && (followingUsers.isNotEmpty() || followingTags.isNotEmpty())) {
            val event = ContactListEvent.create(
                followingUsers,
                followingTags.filter { !it.equals(tag, ignoreCase = true) },
                contactList.relays(),
                loggedIn.privKey!!
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
            privateKey = loggedIn.privKey!!
        )

        val signedEvent = FileStorageHeaderEvent.create(
            data,
            mimeType = headerInfo.mimeType,
            hash = headerInfo.hash,
            size = headerInfo.size.toString(),
            dimensions = headerInfo.dim,
            blurhash = headerInfo.blurHash,
            description = headerInfo.description,
            privateKey = loggedIn.privKey!!
        )

        return Pair(data, signedEvent)
    }

    fun sendNip95(data: FileStorageEvent, signedEvent: FileStorageHeaderEvent): Note? {
        if (!isWriteable()) return null

        Client.send(data)
        LocalCache.consume(data, null)

        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)

        return LocalCache.notes[signedEvent.id]
    }

    fun sendHeader(headerInfo: FileHeader): Note? {
        if (!isWriteable()) return null

        val signedEvent = FileHeaderEvent.create(
            url = headerInfo.url,
            mimeType = headerInfo.mimeType,
            hash = headerInfo.hash,
            size = headerInfo.size.toString(),
            dimensions = headerInfo.dim,
            blurhash = headerInfo.blurHash,
            description = headerInfo.description,
            privateKey = loggedIn.privKey!!
        )

        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)

        return LocalCache.notes[signedEvent.id]
    }

    fun sendPost(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        tags: List<String>? = null,
        zapReceiver: String? = null,
        wantsToMarkAsSensitive: Boolean
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
            privateKey = loggedIn.privKey!!
        )

        Client.send(signedEvent)
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
        wantsToMarkAsSensitive: Boolean
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
            privateKey = loggedIn.privKey!!,
            pollOptions = pollOptions,
            valueMaximum = valueMaximum,
            valueMinimum = valueMinimum,
            consensusThreshold = consensusThreshold,
            closedAt = closedAt,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive
        )
        // println("Sending new PollNoteEvent: %s".format(signedEvent.toJson()))
        Client.send(signedEvent)
        LocalCache.consume(signedEvent)
    }

    fun sendChannelMessage(message: String, toChannel: String, replyTo: List<Note>?, mentions: List<User>?, zapReceiver: String? = null, wantsToMarkAsSensitive: Boolean) {
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
            privateKey = loggedIn.privKey!!
        )
        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendPrivateMessage(message: String, toUser: String, replyingTo: Note? = null, mentions: List<User>?, zapReceiver: String? = null, wantsToMarkAsSensitive: Boolean) {
        if (!isWriteable()) return
        val user = LocalCache.users[toUser] ?: return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        val signedEvent = PrivateDmEvent.create(
            recipientPubKey = user.pubkey(),
            publishedRecipientPubKey = user.pubkey(),
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            privateKey = loggedIn.privKey!!,
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
            privateKey = loggedIn.privKey!!
        )

        Client.send(event)
        LocalCache.consume(event)

        joinChannel(event.id)
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

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!)?.plus(note.address) ?: listOf(note.address),

                loggedIn.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!)?.plus(note.idHex) ?: listOf(note.idHex),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!) ?: emptyList(),

                loggedIn.privKey!!
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

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!) ?: emptyList(),

                loggedIn.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents()?.plus(note.idHex) ?: listOf(note.idHex),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!) ?: emptyList(),

                loggedIn.privKey!!
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

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!)?.minus(note.address) ?: listOf(),

                loggedIn.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents() ?: emptyList(),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!)?.minus(note.idHex) ?: listOf(),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!) ?: emptyList(),

                loggedIn.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun createAuthEvent(relay: Relay, challenge: String): RelayAuthEvent? {
        if (!isWriteable()) return null

        return RelayAuthEvent.create(relay.url, challenge, loggedIn.privKey!!)
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

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!) ?: emptyList(),

                loggedIn.privKey!!
            )
        } else {
            BookmarkListEvent.create(
                "bookmark",
                bookmarks?.taggedEvents()?.minus(note.idHex),
                bookmarks?.taggedUsers() ?: emptyList(),
                bookmarks?.taggedAddresses() ?: emptyList(),

                bookmarks?.privateTaggedEvents(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedUsers(privKey = loggedIn.privKey!!) ?: emptyList(),
                bookmarks?.privateTaggedAddresses(privKey = loggedIn.privKey!!) ?: emptyList(),

                loggedIn.privKey!!
            )
        }

        Client.send(event)
        LocalCache.consume(event)
    }

    fun isInPrivateBookmarks(note: Note): Boolean {
        if (!isWriteable()) return false

        if (note is AddressableNote) {
            return userProfile().latestBookmarkList?.privateTaggedAddresses(loggedIn.privKey!!)
                ?.contains(note.address) == true
        } else {
            return userProfile().latestBookmarkList?.privateTaggedEvents(loggedIn.privKey!!)
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

    fun joinChannel(idHex: String) {
        followingChannels = followingChannels + idHex
        live.invalidateData()

        saveable.invalidateData()
    }

    fun leaveChannel(idHex: String) {
        followingChannels = followingChannels - idHex
        live.invalidateData()

        saveable.invalidateData()
    }

    fun hideUser(pubkeyHex: String) {
        hiddenUsers = hiddenUsers + pubkeyHex
        live.invalidateData()
        saveable.invalidateData()
    }

    fun showUser(pubkeyHex: String) {
        hiddenUsers = hiddenUsers - pubkeyHex
        transientHiddenUsers = transientHiddenUsers - pubkeyHex
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

    fun changeZapAmounts(newAmounts: List<Long>) {
        zapAmountChoices = newAmounts
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

        val privKey = loggedIn.privKey

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

        return emptySet()
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
            privateKey = loggedIn.privKey!!
        )

        Client.send(event)
        LocalCache.consume(event)

        joinChannel(event.id)
    }

    fun decryptContent(note: Note): String? {
        val event = note.event
        return if (event is PrivateDmEvent && loggedIn.privKey != null) {
            event.plainContent(loggedIn.privKey!!, event.talkingWith(userProfile().pubkeyHex).hexToByteArray())
        } else if (event is LnZapRequestEvent && loggedIn.privKey != null) {
            decryptZapContentAuthor(note)?.content()
        } else {
            event?.content()
        }
    }

    fun decryptZapContentAuthor(note: Note): Event? {
        val event = note.event
        val loggedInPrivateKey = loggedIn.privKey

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
                        val altPubKeyFromPrivate = Utils.pubkeyCreate(altPrivateKeyToUse).toHexKey()

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

    fun reconnectIfRelaysHaveChanged() {
        val newRelaySet = activeRelays() ?: convertLocalRelays()
        if (!Client.isSameRelaySetConfig(newRelaySet)) {
            Client.disconnect()
            Client.connect(newRelaySet)
            RelayPool.requestAndWatch()
        }
    }

    fun isHidden(user: User) = isHidden(user.pubkeyHex)
    fun isHidden(userHex: String) = userHex in hiddenUsers || userHex in transientHiddenUsers

    fun followingKeySet(): Set<HexKey> {
        return userProfile().cachedFollowingKeySet()
    }

    fun followingTagSet(): Set<HexKey> {
        return userProfile().cachedFollowingTagSet()
    }

    fun isAcceptable(user: User): Boolean {
        return !isHidden(user) && // if user hasn't hided this author
            user.reportsBy(userProfile()).isEmpty() // if user has not reported this post
    }

    fun isAcceptableDirect(note: Note): Boolean {
        return note.reportsBy(userProfile()).isEmpty() // if user has not reported this post
    }

    fun isFollowing(user: User): Boolean {
        return user.pubkeyHex in followingKeySet()
    }

    fun isAcceptable(note: Note): Boolean {
        return note.author?.let { isAcceptable(it) } ?: true && // if user hasn't hided this author
            isAcceptableDirect(note) &&
            (
                note.event !is RepostEvent ||
                    (note.replyTo?.firstOrNull { isAcceptableDirect(it) } != null)
                ) // is not a reaction about a blocked post
    }

    fun getRelevantReports(note: Note): Set<Note> {
        val followsPlusMe = userProfile().latestContactList?.verifiedFollowKeySetAndMe ?: emptySet()

        val innerReports = if (note.event is RepostEvent) {
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

    fun registerObservers() {
        // Observes relays to restart connections
        userProfile().live().relays.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                reconnectIfRelaysHaveChanged()
            }
        }

        // saves contact list for the next time.
        userProfile().live().follows.observeForever {
            updateContactListTo(userProfile().latestContactList)
        }

        // imports transient blocks due to spam.
        LocalCache.antiSpam.liveSpam.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                it.cache.spamMessages.snapshot().values.forEach {
                    if (it.pubkeyHex !in transientHiddenUsers && it.duplicatedMessages.size >= 5) {
                        val userToBlock = LocalCache.getOrCreateUser(it.pubkeyHex)
                        if (userToBlock != userProfile() && userToBlock.pubkeyHex !in followingKeySet()) {
                            transientHiddenUsers = transientHiddenUsers + it.pubkeyHex
                            live.invalidateData()
                        }
                    }
                }
            }
        }
    }

    init {
        backupContactList?.let {
            println("Loading saved contacts ${it.toJson()}")
            if (userProfile().latestContactList == null) {
                LocalCache.consume(it)
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
