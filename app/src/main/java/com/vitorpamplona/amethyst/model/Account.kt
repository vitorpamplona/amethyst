package com.vitorpamplona.amethyst.model

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.Contact
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.DeletionEvent
import com.vitorpamplona.amethyst.service.model.IdentityClaim
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.service.model.MetadataEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.RelayPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.Persona
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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

@OptIn(DelicateCoroutinesApi::class)
class Account(
    val loggedIn: Persona,
    var followingChannels: Set<String> = DefaultChannels,
    var hiddenUsers: Set<String> = setOf(),
    var localRelays: Set<RelaySetupInfo> = Constants.defaultRelays.toSet(),
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
    var zapAmountChoices: List<Long> = listOf(500L, 1000L, 5000L),
    var hideDeleteRequestInfo: Boolean = false,
    var backupContactList: ContactListEvent? = null
) {
    var transientHiddenUsers: Set<String> = setOf()

    // Observers line up here.
    val live: AccountLiveData = AccountLiveData(this)
    val liveLanguages: AccountLiveData = AccountLiveData(this)
    val saveable: AccountLiveData = AccountLiveData(this)

    fun userProfile(): User {
        return LocalCache.getOrCreateUser(loggedIn.pubKey.toHexKey())
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

        if (contactList != null && follows.isNotEmpty()) {
            val event = ContactListEvent.create(
                follows,
                relays,
                loggedIn.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        } else {
            val event = ContactListEvent.create(listOf(), relays, loggedIn.privKey!!)

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

    fun createZapRequestFor(note: Note): LnZapRequestEvent? {
        if (!isWriteable()) return null

        note.event?.let {
            return LnZapRequestEvent.create(it, userProfile().relays?.keys?.ifEmpty { null } ?: localRelays.map { it.url }.toSet(), loggedIn.privKey!!)
        }

        return null
    }

    fun createZapRequestFor(user: User): LnZapRequestEvent? {
        return createZapRequestFor(user.pubkeyHex)
    }

    fun createZapRequestFor(userPubKeyHex: String): LnZapRequestEvent? {
        if (!isWriteable()) return null

        return LnZapRequestEvent.create(userPubKeyHex, userProfile().relays?.keys?.ifEmpty { null } ?: localRelays.map { it.url }.toSet(), loggedIn.privKey!!)
    }

    fun report(note: Note, type: ReportEvent.ReportType) {
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
            val event = ReportEvent.create(it, type, loggedIn.privKey!!)
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
        val follows = contactList?.follows() ?: emptyList()

        val event = if (contactList != null && follows.isNotEmpty()) {
            ContactListEvent.create(
                follows.plus(Contact(user.pubkeyHex, null)),
                userProfile().relays,
                loggedIn.privKey!!
            )
        } else {
            val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
            ContactListEvent.create(
                listOf(Contact(user.pubkeyHex, null)),
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
        val follows = contactList?.follows() ?: emptyList()

        if (contactList != null && follows.isNotEmpty()) {
            val event = ContactListEvent.create(
                follows.filter { it.pubKeyHex != user.pubkeyHex },
                userProfile().relays,
                loggedIn.privKey!!
            )

            Client.send(event)
            LocalCache.consume(event)
        }
    }

    fun sendPost(message: String, replyTo: List<Note>?, mentions: List<User>?) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        val signedEvent = TextNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            privateKey = loggedIn.privKey!!
        )
        Client.send(signedEvent)
        LocalCache.consume(signedEvent)
    }

    fun sendChannelMessage(message: String, toChannel: String, replyingTo: Note? = null, mentions: List<User>?) {
        if (!isWriteable()) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        val signedEvent = ChannelMessageEvent.create(
            message = message,
            channel = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            privateKey = loggedIn.privKey!!
        )
        Client.send(signedEvent)
        LocalCache.consume(signedEvent, null)
    }

    fun sendPrivateMeesage(message: String, toUser: String, replyingTo: Note? = null) {
        if (!isWriteable()) return
        val user = LocalCache.users[toUser] ?: return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = emptyList<String>()

        val signedEvent = PrivateDmEvent.create(
            recipientPubKey = user.pubkey(),
            publishedRecipientPubKey = user.pubkey(),
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
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

    fun changeZapAmounts(newAmounts: List<Long>) {
        zapAmountChoices = newAmounts
        live.invalidateData()
        saveable.invalidateData()
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
            var pubkeyToUse = event.pubKey

            val recepientPK = event.recipientPubKey()

            if (note.author == userProfile() && recepientPK != null) {
                pubkeyToUse = recepientPK
            }

            event.plainContent(loggedIn.privKey!!, pubkeyToUse.toByteArray())
        } else {
            event?.content()
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
        if (newContactList?.follows().isNullOrEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupContactList?.id != newContactList?.id) {
            backupContactList = newContactList
            saveable.invalidateData()
        }
    }

    // Takes a User's relay list and adds the types of feeds they are active for.
    fun activeRelays(): Array<Relay>? {
        var usersRelayList = userProfile().relays?.map {
            val localFeedTypes = localRelays.firstOrNull() { localRelay -> localRelay.url == it.key }?.feedTypes ?: FeedType.values().toSet()
            Relay(it.key, it.value.read, it.value.write, localFeedTypes)
        } ?: return null

        // Ugly, but forces nostr.band as the only search-supporting relay today.
        // TODO: Remove when search becomes more available.
        if (usersRelayList.none { it.activeTypes.contains(FeedType.SEARCH) }) {
            usersRelayList = usersRelayList + Relay(
                Constants.forcedRelayForSearch.url,
                Constants.forcedRelayForSearch.read,
                Constants.forcedRelayForSearch.write,
                Constants.forcedRelayForSearch.feedTypes
            )
        }

        return usersRelayList.toTypedArray()
    }

    fun convertLocalRelays(): Array<Relay> {
        return localRelays.map {
            Relay(it.url, it.read, it.write, it.feedTypes)
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

    fun isHidden(user: User) = user.pubkeyHex in hiddenUsers || user.pubkeyHex in transientHiddenUsers

    fun isAcceptable(user: User): Boolean {
        return !isHidden(user) && // if user hasn't hided this author
            user.reportsBy(userProfile()).isEmpty() && // if user has not reported this post
            user.countReportAuthorsBy(userProfile().follows) < 5
    }

    fun isAcceptableDirect(note: Note): Boolean {
        return note.reportsBy(userProfile()).isEmpty() && // if user has not reported this post
            note.countReportAuthorsBy(userProfile().follows) < 5 // if it has 5 reports by reliable users
    }

    fun isAcceptable(note: Note): Boolean {
        return note.author?.let { isAcceptable(it) } ?: true && // if user hasn't hided this author
            isAcceptableDirect(note) &&
            (
                note.event !is RepostEvent ||
                    (note.event is RepostEvent && note.replyTo?.firstOrNull { isAcceptableDirect(it) } != null)
                ) // is not a reaction about a blocked post
    }

    fun getRelevantReports(note: Note): Set<Note> {
        val followsPlusMe = userProfile().follows + userProfile()

        val innerReports = if (note.event is RepostEvent) {
            note.replyTo?.map { getRelevantReports(it) }?.flatten() ?: emptyList()
        } else {
            emptyList()
        }

        return (
            note.reportsBy(followsPlusMe) +
                (note.author?.reportsBy(followsPlusMe) ?: emptyList()) +
                innerReports
            ).toSet()
    }

    fun saveRelayList(value: List<RelaySetupInfo>) {
        localRelays = value.toSet()
        sendNewRelayList(value.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) })

        saveable.invalidateData()
    }

    fun setHideDeleteRequestInfo() {
        hideDeleteRequestInfo = true
        saveable.invalidateData()
    }

    init {
        backupContactList?.let {
            println("Loading saved contacts ${it.toJson()}")
            if (userProfile().latestContactList == null) {
                LocalCache.consume(it)
            }
        }

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
                        if (userToBlock != userProfile() && userToBlock !in userProfile().follows) {
                            transientHiddenUsers = transientHiddenUsers + it.pubkeyHex
                        }
                    }
                }
            }
        }
    }
}

class AccountLiveData(private val account: Account) : LiveData<AccountState>(AccountState(account)) {
    var handlerWaiting = AtomicBoolean()

    fun invalidateData() {
        if (handlerWaiting.getAndSet(true)) return

        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            try {
                delay(100)
                refresh()
            } finally {
                withContext(NonCancellable) {
                    handlerWaiting.set(false)
                }
            }
        }
    }

    fun refresh() {
        postValue(AccountState(account))
    }
}

class AccountState(val account: Account)
