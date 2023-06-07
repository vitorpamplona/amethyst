package com.vitorpamplona.amethyst.model

import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import nostr.postr.toNpub
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

object LocalCache {
    val antiSpam = AntiSpamFilter()

    val users = ConcurrentHashMap<HexKey, User>(5000)
    val notes = ConcurrentHashMap<HexKey, Note>(5000)
    val channels = ConcurrentHashMap<HexKey, Channel>()
    val addressables = ConcurrentHashMap<String, AddressableNote>(100)

    val awaitingPaymentRequests =
        ConcurrentHashMap<HexKey, Pair<Note?, (LnZapPaymentResponseEvent) -> Unit>>(10)

    fun checkGetOrCreateUser(key: String): User? {
        checkNotInMainThread()

        if (isValidHexNpub(key)) {
            return getOrCreateUser(key)
        }
        return null
    }

    @Synchronized
    fun getOrCreateUser(key: HexKey): User {
        // checkNotInMainThread()

        return users[key] ?: run {
            val answer = User(key)
            users.put(key, answer)
            answer
        }
    }

    fun checkGetOrCreateNote(key: String): Note? {
        checkNotInMainThread()

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
        checkNotInMainThread()

        return notes[idHex] ?: run {
            val answer = Note(idHex)
            notes.put(idHex, answer)
            answer
        }
    }

    fun checkGetOrCreateChannel(key: String): Channel? {
        checkNotInMainThread()

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
        checkNotInMainThread()

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
    fun getOrCreateAddressableNoteInternal(key: ATag): AddressableNote {
        checkNotInMainThread()

        // we can't use naddr here because naddr might include relay info and
        // the preferred relay should not be part of the index.
        return addressables[key.toTag()] ?: run {
            val answer = AddressableNote(key)
            addressables.put(key.toTag(), answer)
            answer
        }
    }

    fun getOrCreateAddressableNote(key: ATag): AddressableNote {
        val note = getOrCreateAddressableNoteInternal(key)
        // Loads the user outside a Syncronized block to avoid blocking
        if (note.author == null) {
            note.author = checkGetOrCreateUser(key.pubKeyHex)
        }
        return note
    }

    fun consume(event: MetadataEvent) {
        // new event
        val oldUser = getOrCreateUser(event.pubKey)
        if (oldUser.info == null || event.createdAt > oldUser.info!!.updatedMetadataAt) {
            val newUserMetadata = event.contactMetaData()
            if (newUserMetadata != null) {
                oldUser.updateUserInfo(newUserMetadata, event)
            }
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

    fun consume(event: PeopleListEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
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

    private fun consume(event: PinListEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
    }

    private fun consume(event: RelaySetEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
    }

    private fun consume(event: AudioTrackEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
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

    fun consume(event: AppDefinitionEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return

        note.loadEvent(event, author, emptyList())

        refreshObservers(note)
    }

    fun consume(event: AppRecommendationEvent) {
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
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

    fun consume(event: PrivateDmEvent, relay: Relay?): Note {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return note

        val recipient = event.verifiedRecipientPubKey()?.let { getOrCreateUser(it) }

        // Log.d("PM", "${author.toBestDisplayName()} to ${recipient?.toBestDisplayName()}")

        val repliesTo = event.tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
            .mapNotNull { checkGetOrCreateNote(it) }

        note.loadEvent(event, author, repliesTo)

        if (recipient != null) {
            author.addMessage(recipient, note)
            recipient.addMessage(author, note)
        }

        refreshObservers(note)

        return note
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
                    masterNote.removeZapPayment(deleteNote)
                    masterNote.removeReport(deleteNote)
                }

                deleteNote.channelHex()?.let {
                    val channel = checkGetOrCreateChannel(it)
                    channel?.removeNote(deleteNote)
                }

                if (deleteNote.event is PrivateDmEvent) {
                    val author = deleteNote.author
                    val recipient = (deleteNote.event as? PrivateDmEvent)?.verifiedRecipientPubKey()?.let { checkGetOrCreateUser(it) }

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
        } else {
            repliesTo.forEach {
                it.addReport(note)
            }

            mentions.forEach {
                // doesn't add to reports, but triggers recounts
                it.liveSet?.reports?.invalidateData()
            }
        }

        refreshObservers(note)
    }

    fun consume(event: ChannelCreateEvent) {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreateChannel(event.id)
        val author = getOrCreateUser(event.pubKey)

        val note = getOrCreateNote(event.id)
        if (note.event == null) {
            oldChannel.addNote(note)
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }

        if (event.createdAt <= oldChannel.updatedMetadataAt) {
            return // older data, does nothing
        }
        if (oldChannel.creator == null || oldChannel.creator == author) {
            oldChannel.updateChannelInfo(author, event.channelInfo(), event.createdAt)
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
            }
        } else {
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }

        val note = getOrCreateNote(event.id)
        if (note.event == null) {
            oldChannel.addNote(note)
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
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

    fun consume(event: FileHeaderEvent, relay: Relay?) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        note.loadEvent(event, author, emptyList())

        refreshObservers(note)
    }

    fun consume(event: FileStorageHeaderEvent, relay: Relay?) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        note.loadEvent(event, author, emptyList())

        refreshObservers(note)
    }

    fun consume(event: HighlightEvent, relay: Relay?) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        note.loadEvent(event, author, emptyList())

        // Adds to user profile
        author.addNote(note)

        refreshObservers(note)
    }

    fun consume(event: FileStorageEvent, relay: Relay?) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        try {
            val cachePath = File(Amethyst.instance.applicationContext.externalCacheDir, "NIP95")
            cachePath.mkdirs()
            val file = File(cachePath, event.id)
            if (!file.exists()) {
                val stream = FileOutputStream(file)
                stream.write(event.decode())
                stream.close()
                Log.i("FileStorageEvent", "NIP95 File received from ${relay?.url} and saved to disk as $file")
            }
        } catch (e: IOException) {
            Log.e("FileStorageEvent", "FileStorageEvent save to disk error: " + event.id, e)
        }

        // Already processed this event.
        if (note.event != null) return

        // this is an invalid event. But we don't need to keep the data in memory.
        val eventNoData = FileStorageEvent(event.id, event.pubKey, event.createdAt, event.tags, "", event.sig)

        note.loadEvent(eventNoData, author, emptyList())

        refreshObservers(note)
    }

    fun consume(event: LnZapPaymentRequestEvent) {
        // Does nothing without a response callback.
    }

    fun consume(event: LnZapPaymentRequestEvent, zappedNote: Note?, onResponse: (LnZapPaymentResponseEvent) -> Unit) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return

        note.loadEvent(event, author, emptyList())

        zappedNote?.addZapPayment(note, null)

        awaitingPaymentRequests.put(event.id, Pair(zappedNote, onResponse))

        refreshObservers(note)
    }

    fun consume(event: LnZapPaymentResponseEvent) {
        val requestId = event.requestId()
        val pair = awaitingPaymentRequests[requestId] ?: return

        val (zappedNote, responseCallback) = pair

        val requestNote = requestId?.let { checkGetOrCreateNote(requestId) }

        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return

        note.loadEvent(event, author, emptyList())

        requestNote?.let { request ->
            zappedNote?.addZapPayment(request, note)
        }

        if (responseCallback != null) {
            responseCallback(event)
        }
    }

    fun findUsersStartingWith(username: String): List<User> {
        checkNotInMainThread()

        val key = decodePublicKeyAsHexOrNull(username)

        if (key != null && users[key] != null) {
            return listOfNotNull(users[key])
        }

        return users.values.filter {
            (it.anyNameStartsWith(username)) ||
                it.pubkeyHex.startsWith(username, true) ||
                it.pubkeyNpub().startsWith(username, true)
        }
    }

    fun findNotesStartingWith(text: String): List<Note> {
        checkNotInMainThread()

        val key = try {
            Nip19.uriToRoute(text)?.hex ?: Hex.decode(text).toHexKey()
        } catch (e: Exception) {
            null
        }

        if (key != null && (notes[key] ?: addressables[key]) != null) {
            return listOfNotNull(notes[key] ?: addressables[key])
        }

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
        checkNotInMainThread()

        val key = try {
            Nip19.uriToRoute(text)?.hex ?: Hex.decode(text).toHexKey()
        } catch (e: Exception) {
            null
        }

        if (key != null && channels[key] != null) {
            return listOfNotNull(channels[key])
        }

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
        checkNotInMainThread()

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
        checkNotInMainThread()

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
        checkNotInMainThread()

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

    fun verifyAndConsume(event: Event, relay: Relay?) {
        checkNotInMainThread()

        if (!event.hasValidSignature()) {
            try {
                event.checkSignature()
            } catch (e: Exception) {
                Log.w("Event failed retest ${event.kind}", e.message ?: "")
            }
            return
        }

        try {
            when (event) {
                is AppDefinitionEvent -> consume(event)
                is AppRecommendationEvent -> consume(event)
                is AudioTrackEvent -> consume(event)
                is BadgeAwardEvent -> consume(event)
                is BadgeDefinitionEvent -> consume(event)
                is BadgeProfilesEvent -> consume(event)
                is BookmarkListEvent -> consume(event)
                is ChannelCreateEvent -> consume(event)
                is ChannelHideMessageEvent -> consume(event)
                is ChannelMessageEvent -> consume(event, relay)
                is ChannelMetadataEvent -> consume(event)
                is ChannelMuteUserEvent -> consume(event)
                is ContactListEvent -> consume(event)
                is DeletionEvent -> consume(event)

                is FileHeaderEvent -> consume(event, relay)
                is FileStorageEvent -> consume(event, relay)
                is FileStorageHeaderEvent -> consume(event, relay)
                is HighlightEvent -> consume(event, relay)
                is LnZapEvent -> {
                    event.zapRequest?.let {
                        verifyAndConsume(it, relay)
                    }
                    consume(event)
                }
                is LnZapRequestEvent -> consume(event)
                is LnZapPaymentRequestEvent -> consume(event)
                is LnZapPaymentResponseEvent -> consume(event)
                is LongTextNoteEvent -> consume(event, relay)
                is MetadataEvent -> consume(event)
                is PrivateDmEvent -> consume(event, relay)
                is PinListEvent -> consume(event)
                is PeopleListEvent -> consume(event)
                is ReactionEvent -> consume(event)
                is RecommendRelayEvent -> consume(event)
                is RelaySetEvent -> consume(event)
                is ReportEvent -> consume(event, relay)
                is RepostEvent -> {
                    event.containedPost()?.let {
                        verifyAndConsume(it, relay)
                    }
                    consume(event)
                }
                is TextNoteEvent -> consume(event, relay)
                is PollNoteEvent -> consume(event, relay)
                else -> {
                    Log.w("Event Not Supported", event.toJson())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class LocalCacheLiveData {
    private val _newEventBundles = MutableSharedFlow<Set<Note>>()
    val newEventBundles = _newEventBundles.asSharedFlow() // read-only public view

    // Refreshes observers in batches.
    private val bundler = BundledInsert<Note>(300, Dispatchers.IO)

    fun invalidateData(newNote: Note) {
        bundler.invalidateList(newNote) { bundledNewNotes ->
            _newEventBundles.emit(bundledNewNotes)
        }
    }
}
