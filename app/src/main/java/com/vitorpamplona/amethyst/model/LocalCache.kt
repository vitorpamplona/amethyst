package com.vitorpamplona.amethyst.model

import android.util.Log
import androidx.lifecycle.LiveData
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.service.NostrAccountDataSource.account
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.*
import nostr.postr.toNpub
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

object LocalCache {
    val metadataParser by lazy {
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readerFor(UserMetadata::class.java)
    }

    val antiSpam = AntiSpamFilter()

    val users = ConcurrentHashMap<HexKey, User>(5000)
    val notes = ConcurrentHashMap<HexKey, Note>(5000)
    val channels = ConcurrentHashMap<HexKey, Channel>()
    val addressables = ConcurrentHashMap<String, AddressableNote>(100)

    val awaitingPaymentRequests = ConcurrentHashMap<HexKey, (LnZapPaymentResponseEvent) -> Unit>(10)

    fun checkGetOrCreateUser(key: String): User? {
        if (isValidHexNpub(key)) {
            return getOrCreateUser(key)
        }
        return null
    }

    @Synchronized
    fun getOrCreateUser(key: HexKey): User {
        return users[key] ?: run {
            val answer = User(key)
            users.put(key, answer)
            answer
        }
    }

    fun checkGetOrCreateNote(key: String): Note? {
        if (ATag.isATag(key)) {
            return checkGetOrCreateAddressableNote(key)
        }
        if (isValidHexNpub(key)) {
            return getOrCreateNote(key)
        }
        return null
    }

    @Synchronized
    fun getOrCreateNote(idHex: String): Note {
        return notes[idHex] ?: run {
            val answer = Note(idHex)
            notes.put(idHex, answer)
            answer
        }
    }

    fun checkGetOrCreateChannel(key: String): Channel? {
        if (isValidHexNpub(key)) {
            return getOrCreateChannel(key)
        }
        return null
    }

    private fun isValidHexNpub(key: String): Boolean {
        return try {
            Hex.decode(key).toNpub()
            true
        } catch (e: IllegalArgumentException) {
            Log.e("LocalCache", "Invalid Key to create user: $key", e)
            false
        }
    }

    @Synchronized
    fun getOrCreateChannel(key: String): Channel {
        return channels[key] ?: run {
            val answer = Channel(key)
            channels.put(key, answer)
            answer
        }
    }

    fun checkGetOrCreateAddressableNote(key: String): AddressableNote? {
        return try {
            val addr = ATag.parse(key, null) // relay doesn't matter for the index.
            if (addr != null) {
                getOrCreateAddressableNote(addr)
            } else {
                null
            }
        } catch (e: IllegalArgumentException) {
            Log.e("LocalCache", "Invalid Key to create channel: $key", e)
            null
        }
    }

    @Synchronized
    fun getOrCreateAddressableNote(key: ATag): AddressableNote {
        // we can't use naddr here because naddr might include relay info and
        // the preferred relay should not be part of the index.
        return addressables[key.toTag()] ?: run {
            val answer = AddressableNote(key)
            answer.author = checkGetOrCreateUser(key.pubKeyHex)
            addressables.put(key.toTag(), answer)
            answer
        }
    }

    fun consume(event: MetadataEvent) {
        // new event
        val oldUser = getOrCreateUser(event.pubKey)
        if (oldUser.info == null || event.createdAt > oldUser.info!!.updatedMetadataAt) {
            val newUser = try {
                metadataParser.readValue(
                    ByteArrayInputStream(event.content.toByteArray(Charsets.UTF_8)),
                    UserMetadata::class.java
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w("MT", "Content Parse Error ${e.localizedMessage} ${event.content}")
                return
            }

            oldUser.updateUserInfo(newUser, event)
            // Log.d("MT", "New User Metadata ${oldUser.pubkeyDisplayHex} ${oldUser.toBestDisplayName()}")
        } else {
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }
    }

    fun consume(event: BookmarkListEvent) {
        val user = getOrCreateUser(event.pubKey)
        if (user.latestBookmarkList == null || event.createdAt > user.latestBookmarkList!!.createdAt) {
            if (event.dTag() == "bookmark") {
                user.updateBookmark(event)
            }
            // Log.d("MT", "New User Metadata ${oldUser.pubkeyDisplayHex} ${oldUser.toBestDisplayName()}")
        } else {
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }
    }

    fun formattedDateTime(timestamp: Long): String {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("uuuu MMM d hh:mm a"))
    }

    fun consume(event: TextNoteEvent, relay: Relay? = null) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let {
                it.spamCounter++
            }
            return
        }

        val replyTo = event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }

        note.loadEvent(event, author, replyTo)

        // Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content()?.take(100)} ${formattedDateTime(event.createdAt)}")

        // Prepares user's profile view.
        author.addNote(note)

        // Counts the replies
        replyTo.forEach {
            it.addReply(note)
        }

        refreshObservers(note)
    }

    fun consume(event: LongTextNoteEvent, relay: Relay?) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let {
                it.spamCounter++
            }
            return
        }

        val replyTo = event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            author.addNote(note)

            refreshObservers(note)
        }
    }

    fun consume(event: PollNoteEvent, relay: Relay? = null) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let {
                it.spamCounter++
            }
            return
        }

        val replyTo = event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }

        note.loadEvent(event, author, replyTo)

        // Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content()?.take(100)} ${formattedDateTime(event.createdAt)}")

        // Prepares user's profile view.
        author.addNote(note)

        // Counts the replies
        replyTo.forEach {
            it.addReply(note)
        }

        refreshObservers(note)
    }

    fun consume(event: BadgeDefinitionEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList<Note>())

            refreshObservers(note)
        }
    }

    fun consume(event: BadgeProfilesEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        val replyTo = event.badgeAwardEvents().mapNotNull { checkGetOrCreateNote(it) } +
            event.badgeAwardDefinitions().mapNotNull { getOrCreateAddressableNote(it) }

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            author.updateAcceptedBadges(note)
        }
    }

    fun consume(event: BadgeAwardEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)
        val awardDefinition = event.awardDefinition().map { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, awardDefinition)

        // Replies of an Badge Definition are Award Events
        awardDefinition.forEach {
            it.addReply(note)
        }

        refreshObservers(note)
    }

    @Suppress("UNUSED_PARAMETER")
    fun consume(event: RecommendRelayEvent) {
//        // Log.d("RR", event.toJson())
    }

    fun consume(event: ContactListEvent) {
        val user = getOrCreateUser(event.pubKey)

        // avoids processing empty contact lists.
        if (event.createdAt > (user.latestContactList?.createdAt ?: 0) && !event.tags.isEmpty()) {
            user.updateContactList(event)
            // Log.d("CL", "AAA ${user.toBestDisplayName()} ${follows.size}")
        }
    }

    fun consume(event: PrivateDmEvent, relay: Relay?) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        val recipient = event.recipientPubKey()?.let { getOrCreateUser(it) }

        // Log.d("PM", "${author.toBestDisplayName()} to ${recipient?.toBestDisplayName()}")

        val repliesTo = event.tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
            .mapNotNull { checkGetOrCreateNote(it) }

        note.loadEvent(event, author, repliesTo)

        if (recipient != null) {
            author.addMessage(recipient, note)
            recipient.addMessage(author, note)
        }

        refreshObservers(note)
    }

    fun consume(event: DeletionEvent) {
        var deletedAtLeastOne = false

        event.deleteEvents().mapNotNull { notes[it] }.forEach { deleteNote ->
            // must be the same author
            if (deleteNote.author?.pubkeyHex == event.pubKey) {
                deleteNote.author?.removeNote(deleteNote)

                // reverts the add
                val mentions = deleteNote.event?.tags()?.filter { it.firstOrNull() == "p" }
                    ?.mapNotNull { it.getOrNull(1) }?.mapNotNull { checkGetOrCreateUser(it) }

                mentions?.forEach { user ->
                    user.removeReport(deleteNote)
                }

                // Counts the replies
                deleteNote.replyTo?.forEach { masterNote ->
                    masterNote.removeReply(deleteNote)
                    masterNote.removeBoost(deleteNote)
                    masterNote.removeReaction(deleteNote)
                    masterNote.removeZap(deleteNote)
                    masterNote.removeReport(deleteNote)
                }

                val channel = deleteNote.channel()
                channel?.removeNote(deleteNote)

                if (deleteNote.event is PrivateDmEvent) {
                    val author = deleteNote.author
                    val recipient = (deleteNote.event as? PrivateDmEvent)?.recipientPubKey()?.let { checkGetOrCreateUser(it) }

                    if (recipient != null && author != null) {
                        author.removeMessage(recipient, deleteNote)
                        recipient.removeMessage(author, deleteNote)
                    }
                }

                notes.remove(deleteNote.idHex)

                deletedAtLeastOne = true
            }
        }

        if (deletedAtLeastOne) {
            // refreshObservers()
        }
    }

    fun consume(event: RepostEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = event.boostedPost().mapNotNull { checkGetOrCreateNote(it) } +
            event.taggedAddresses().mapNotNull { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, repliesTo)

        // Prepares user's profile view.
        author.addNote(note)

        // Counts the replies
        repliesTo.forEach {
            it.addBoost(note)
        }

        refreshObservers(note)
    }

    fun consume(event: ReactionEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = event.originalPost().mapNotNull { checkGetOrCreateNote(it) } +
            event.taggedAddresses().mapNotNull { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, repliesTo)

        // Log.d("RE", "New Reaction ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        if (
            event.content == "" ||
            event.content == "+" ||
            event.content == "\u2764\uFE0F" || // red heart
            event.content == "\uD83E\uDD19" || // call me hand
            event.content == "\uD83D\uDC4D" // thumbs up
        ) {
            // Counts the replies
            repliesTo.forEach {
                it.addReaction(note)
            }
        }

        if (event.content == "!" || // nostr_console hide.
            event.content == "\u26A0\uFE0F" // Warning sign
        ) {
            // Counts the replies
            repliesTo.forEach {
                it.addReport(note)
            }
        }

        refreshObservers(note)
    }

    fun consume(event: ReportEvent, relay: Relay?) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        val mentions = event.reportedAuthor().mapNotNull { checkGetOrCreateUser(it.key) }
        val repliesTo = event.reportedPost().mapNotNull { checkGetOrCreateNote(it.key) } +
            event.taggedAddresses().map { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, repliesTo)

        // Log.d("RP", "New Report ${event.content} by ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")
        // Adds notifications to users.
        if (repliesTo.isEmpty()) {
            mentions.forEach {
                it.addReport(note)
            }
        }
        repliesTo.forEach {
            it.addReport(note)
        }

        refreshObservers(note)
    }

    fun consume(event: ChannelCreateEvent) {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreateChannel(event.id)
        val author = getOrCreateUser(event.pubKey)
        if (event.createdAt <= oldChannel.updatedMetadataAt) {
            return // older data, does nothing
        }
        if (oldChannel.creator == null || oldChannel.creator == author) {
            oldChannel.updateChannelInfo(author, event.channelInfo(), event.createdAt)

            val note = getOrCreateNote(event.id)
            oldChannel.addNote(note)
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
    }

    fun consume(event: ChannelMetadataEvent) {
        val channelId = event.channel()
        // Log.d("MT", "New User ${users.size} ${event.contactMetaData.name}")
        if (channelId.isNullOrBlank()) return

        // new event
        val oldChannel = checkGetOrCreateChannel(channelId) ?: return
        val author = getOrCreateUser(event.pubKey)
        if (event.createdAt > oldChannel.updatedMetadataAt) {
            if (oldChannel.creator == null || oldChannel.creator == author) {
                oldChannel.updateChannelInfo(author, event.channelInfo(), event.createdAt)

                val note = getOrCreateNote(event.id)
                oldChannel.addNote(note)
                note.loadEvent(event, author, emptyList())

                refreshObservers(note)
            }
        } else {
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }
    }

    fun consume(event: ChannelMessageEvent, relay: Relay?) {
        val channelId = event.channel()

        if (channelId.isNullOrBlank()) return

        val channel = checkGetOrCreateChannel(channelId) ?: return

        val note = getOrCreateNote(event.id)
        channel.addNote(note)

        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let {
                it.spamCounter++
            }
            return
        }

        val replyTo = event.tagsWithoutCitations()
            .filter { it != event.channel() }
            .mapNotNull { checkGetOrCreateNote(it) }

        note.loadEvent(event, author, replyTo)

        // Log.d("CM", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content()} ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach {
            it.addReply(note)
        }

        refreshObservers(note)
    }

    @Suppress("UNUSED_PARAMETER")
    fun consume(event: ChannelHideMessageEvent) {
    }

    @Suppress("UNUSED_PARAMETER")
    fun consume(event: ChannelMuteUserEvent) {
    }

    fun consume(event: LnZapEvent) {
        val note = getOrCreateNote(event.id)
        // Already processed this event.
        if (note.event != null) return

        val zapRequest = event.zapRequest?.id?.let { getOrCreateNote(it) }

        val author = getOrCreateUser(event.pubKey)
        val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
        val repliesTo = event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
            event.taggedAddresses().map { getOrCreateAddressableNote(it) } +
            (
                (zapRequest?.event as? LnZapRequestEvent)?.taggedAddresses()?.map { getOrCreateAddressableNote(it) } ?: emptySet<Note>()
                )

        note.loadEvent(event, author, repliesTo)

        if (zapRequest == null) {
            Log.e("ZP", "Zap Request not found. Unable to process Zap {${event.toJson()}}")
            return
        }

        // Log.d("ZP", "New ZapEvent ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        repliesTo.forEach {
            it.addZap(zapRequest, note)
        }
        mentions.forEach {
            it.addZap(zapRequest, note)
        }

        refreshObservers(note)
    }

    fun consume(event: LnZapRequestEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        val author = getOrCreateUser(event.pubKey)
        val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
        val repliesTo = event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
            event.taggedAddresses().map { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, repliesTo)

        // Log.d("ZP", "New Zap Request ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        repliesTo.forEach {
            it.addZap(note, null)
        }
        mentions.forEach {
            it.addZap(note, null)
        }

        refreshObservers(note)
    }

    fun consume(event: FileHeaderEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        val author = getOrCreateUser(event.pubKey)

        note.loadEvent(event, author, emptyList())

        refreshObservers(note)
    }

    fun consume(event: LnZapPaymentRequestEvent) {
        // Does nothing without a response callback.
    }

    fun consume(event: LnZapPaymentRequestEvent, onResponse: (LnZapPaymentResponseEvent) -> Unit) {
        awaitingPaymentRequests.put(event.id, onResponse)
    }

    fun consume(event: LnZapPaymentResponseEvent) {
        val responseCallback = awaitingPaymentRequests[event.requestId()]

        if (responseCallback != null) {
            responseCallback(event)
        }
    }

    fun findUsersStartingWith(username: String): List<User> {
        return users.values.filter {
            (it.anyNameStartsWith(username)) ||
                it.pubkeyHex.startsWith(username, true) ||
                it.pubkeyNpub().startsWith(username, true)
        }
    }

    fun findNotesStartingWith(text: String): List<Note> {
        return notes.values.filter {
            (it.event is TextNoteEvent && it.event?.content()?.contains(text, true) ?: false) ||
                (it.event is PollNoteEvent && it.event?.content()?.contains(text, true) ?: false) ||
                (it.event is ChannelMessageEvent && it.event?.content()?.contains(text, true) ?: false) ||
                it.idHex.startsWith(text, true) ||
                it.idNote().startsWith(text, true)
        } + addressables.values.filter {
            (it.event as? LongTextNoteEvent)?.content?.contains(text, true) ?: false ||
                (it.event as? LongTextNoteEvent)?.title()?.contains(text, true) ?: false ||
                (it.event as? LongTextNoteEvent)?.summary()?.contains(text, true) ?: false ||
                it.idHex.startsWith(text, true)
        }
    }

    fun findChannelsStartingWith(text: String): List<Channel> {
        return channels.values.filter {
            it.anyNameStartsWith(text) ||
                it.idHex.startsWith(text, true) ||
                it.idNote().startsWith(text, true)
        }
    }

    fun cleanObservers() {
        notes.forEach {
            it.value.clearLive()
        }

        users.forEach {
            it.value.clearLive()
        }
    }

    fun pruneOldAndHiddenMessages(account: Account) {
        channels.forEach { it ->
            val toBeRemoved = it.value.pruneOldAndHiddenMessages(account)

            toBeRemoved.forEach {
                notes.remove(it.idHex)
                // Doesn't need to clean up the replies and mentions.. Too small to matter.

                // Counts the replies
                it.replyTo?.forEach { _ ->
                    it.removeReply(it)
                }
            }

            println("PRUNE: ${toBeRemoved.size} messages removed from ${it.value.info.name}")
        }
    }

    fun pruneHiddenMessages(account: Account) {
        val toBeRemoved = account.hiddenUsers.map {
            (users[it]?.notes ?: emptySet())
        }.flatten()

        account.hiddenUsers.forEach {
            users[it]?.clearNotes()
        }

        toBeRemoved.forEach {
            it.author?.removeNote(it)

            // Counts the replies
            it.replyTo?.forEach { masterNote ->
                masterNote.removeReply(it)
                masterNote.removeBoost(it)
                masterNote.removeReaction(it)
                masterNote.removeZap(it)
                masterNote.removeReport(it)
            }

            notes.remove(it.idHex)
        }

        println("PRUNE: ${toBeRemoved.size} messages removed because they were Hidden")
    }

    fun pruneContactLists(userAccount: Account) {
        var removingContactList = 0
        users.values.forEach {
            if (it != userAccount.userProfile() && (it.liveSet == null || it.liveSet?.isInUse() == false) && it.latestContactList != null) {
                it.latestContactList = null
                removingContactList++
            }
        }

        println("PRUNE: $removingContactList contact lists")
    }

    // Observers line up here.
    val live: LocalCacheLiveData = LocalCacheLiveData()

    private fun refreshObservers(newNote: Note) {
        live.invalidateData(newNote)
    }
}

class LocalCacheLiveData : LiveData<Set<Note>>(setOf<Note>()) {

    // Refreshes observers in batches.
    private val bundler = BundledInsert<Note>(300, Dispatchers.Main)

    fun invalidateData(newNote: Note) {
        bundler.invalidateList(newNote) { bundledNewNotes ->
            if (hasActiveObservers()) {
                postValue(bundledNewNotes)
            }
        }
    }
}
