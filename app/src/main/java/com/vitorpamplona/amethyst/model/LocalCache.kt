package com.vitorpamplona.amethyst.model

import android.util.Log
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.HexValidator
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.encoders.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.AppRecommendationEvent
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.BadgeAwardEvent
import com.vitorpamplona.quartz.events.BadgeDefinitionEvent
import com.vitorpamplona.quartz.events.BadgeProfilesEvent
import com.vitorpamplona.quartz.events.BaseAddressableEvent
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.CalendarDateSlotEvent
import com.vitorpamplona.quartz.events.CalendarEvent
import com.vitorpamplona.quartz.events.CalendarRSVPEvent
import com.vitorpamplona.quartz.events.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelHideMessageEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChannelMuteUserEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.DeletionEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.NNSEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.RecommendRelayEvent
import com.vitorpamplona.quartz.events.RelaySetEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.StatusEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        // checkNotInMainThread()

        if (isValidHex(key)) {
            return getOrCreateUser(key)
        }
        return null
    }

    fun getOrCreateUser(key: HexKey): User {
        // checkNotInMainThread()

        return users[key] ?: run {
            require(isValidHex(key = key)) {
                "$key is not a valid hex"
            }

            val newObject = User(key)
            users.putIfAbsent(key, newObject) ?: newObject
        }
    }

    fun getUserIfExists(key: String): User? {
        if (key.isEmpty()) return null
        return users[key]
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? {
        return addressables[key]
    }

    fun getNoteIfExists(key: String): Note? {
        return addressables[key] ?: notes[key]
    }

    fun getChannelIfExists(key: String): Channel? {
        return channels[key]
    }

    fun checkGetOrCreateNote(key: String): Note? {
        checkNotInMainThread()

        if (ATag.isATag(key)) {
            return checkGetOrCreateAddressableNote(key)
        }
        if (isValidHex(key)) {
            val note = getOrCreateNote(key)
            val noteEvent = note.event
            return if (noteEvent is AddressableEvent) {
                // upgrade to the latest
                val newNote = checkGetOrCreateAddressableNote(noteEvent.address().toTag())

                if (newNote != null && noteEvent is Event && newNote.event == null) {
                    val author = note.author ?: getOrCreateUser(noteEvent.pubKey)
                    newNote.loadEvent(noteEvent as Event, author, emptyList())
                    note.moveAllReferencesTo(newNote)
                }

                newNote
            } else {
                note
            }
        }
        return null
    }

    fun getOrAddAliasNote(idHex: String, note: Note): Note {
        checkNotInMainThread()

        return notes.get(idHex) ?: run {
            require(isValidHex(idHex)) {
                "$idHex is not a valid hex"
            }

            notes.putIfAbsent(idHex, note) ?: note
        }
    }

    fun getOrCreateNote(idHex: String): Note {
        checkNotInMainThread()

        return notes.get(idHex) ?: run {
            require(isValidHex(idHex)) {
                "$idHex is not a valid hex"
            }

            val newObject = Note(idHex)
            notes.putIfAbsent(idHex, newObject) ?: newObject
        }
    }

    fun checkGetOrCreateChannel(key: String): Channel? {
        checkNotInMainThread()

        if (isValidHex(key)) {
            return getOrCreateChannel(key) {
                PublicChatChannel(key)
            }
        }
        val aTag = ATag.parse(key, null)
        if (aTag != null) {
            return getOrCreateChannel(aTag.toTag()) {
                LiveActivitiesChannel(aTag)
            }
        }
        return null
    }

    private fun isValidHex(key: String): Boolean {
        if (key.isBlank()) return false
        if (key.contains(":")) return false

        return HexValidator.isHex(key)
    }

    fun getOrCreateChannel(key: String, channelFactory: (String) -> Channel): Channel {
        checkNotInMainThread()

        return channels[key] ?: run {
            val newObject = channelFactory(key)
            channels.putIfAbsent(key, newObject) ?: newObject
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

    fun getOrCreateAddressableNoteInternal(key: ATag): AddressableNote {
        // checkNotInMainThread()

        // we can't use naddr here because naddr might include relay info and
        // the preferred relay should not be part of the index.
        return addressables[key.toTag()] ?: run {
            val newObject = AddressableNote(key)
            addressables.putIfAbsent(key.toTag(), newObject) ?: newObject
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

    fun consume(event: ContactListEvent) {
        val user = getOrCreateUser(event.pubKey)

        // avoids processing empty contact lists.
        if (event.createdAt > (user.latestContactList?.createdAt ?: 0) && !event.tags.isEmpty()) {
            user.updateContactList(event)
            // Log.d("CL", "AAA ${user.toBestDisplayName()} ${follows.size}")
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

        // Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content()?.split("\n")?.take(100)} ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach {
            it.addReply(note)
        }

        refreshObservers(note)
    }

    fun consume(event: LongTextNoteEvent, relay: Relay?) {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (version.event == null) {
            version.loadEvent(event, author, emptyList())
            version.moveAllReferencesTo(note)
        }

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

        // Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content()?.split("\n")?.take(100)} ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach {
            it.addReply(note)
        }

        refreshObservers(note)
    }

    private fun consume(event: LiveActivitiesEvent, relay: Relay?) {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (version.event == null) {
            version.loadEvent(event, author, emptyList())
            version.moveAllReferencesTo(note)
        }

        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            val channel = getOrCreateChannel(note.idHex) {
                LiveActivitiesChannel(note.address)
            } as? LiveActivitiesChannel

            val creator = event.host()?.ifBlank { null }?.let {
                checkGetOrCreateUser(it)
            } ?: author

            channel?.updateChannelInfo(creator, event, event.createdAt)

            refreshObservers(note)
        }
    }

    fun consume(event: PeopleListEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: AdvertisedRelayListEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: CommunityDefinitionEvent, relay: Relay?) { consumeBaseReplaceable(event) }
    fun consume(event: EmojiPackSelectionEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: EmojiPackEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: ClassifiedsEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: PinListEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: RelaySetEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: AudioTrackEvent) { consumeBaseReplaceable(event) }
    fun consume(event: StatusEvent, relay: Relay?) {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (version.event == null) {
            version.loadEvent(event, author, emptyList())
            version.moveAllReferencesTo(note)
        }

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            author.liveSet?.innerStatuses?.invalidateData()

            refreshObservers(note)
        }
    }

    fun consume(event: BadgeDefinitionEvent) { consumeBaseReplaceable(event) }

    fun consume(event: BadgeProfilesEvent) {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (version.event == null) {
            version.loadEvent(event, author, emptyList())
            version.moveAllReferencesTo(note)
        }

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        val replyTo = event.badgeAwardEvents().mapNotNull { checkGetOrCreateNote(it) } +
            event.badgeAwardDefinitions().map { getOrCreateAddressableNote(it) }

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            refreshObservers(note)
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

    private fun comsume(event: NNSEvent) { consumeBaseReplaceable(event) }
    fun consume(event: AppDefinitionEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: CalendarEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: CalendarDateSlotEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: CalendarTimeSlotEvent) { consumeBaseReplaceable(event) }
    private fun consume(event: CalendarRSVPEvent) { consumeBaseReplaceable(event) }

    private fun consumeBaseReplaceable(event: BaseAddressableEvent) {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        if (version.event == null) {
            version.loadEvent(event, author, emptyList())
            version.moveAllReferencesTo(note)
        }

        // Already processed this event.
        if (note.event?.id() == event.id()) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
    }

    fun consume(event: AppRecommendationEvent) { consumeBaseReplaceable(event) }

    @Suppress("UNUSED_PARAMETER")
    fun consume(event: RecommendRelayEvent) {
//        // Log.d("RR", event.toJson())
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

        val repliesTo = event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }

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
                    channels[it]?.removeNote(deleteNote)
                }

                (deleteNote.event as? LiveActivitiesChatMessageEvent)?.activity()?.let {
                    channels[it.toTag()]?.removeNote(deleteNote)
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
            event.taggedAddresses().map { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, repliesTo)

        // Counts the replies
        repliesTo.forEach {
            it.addBoost(note)
        }

        refreshObservers(note)
    }

    fun consume(event: GenericRepostEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = event.boostedPost().mapNotNull { checkGetOrCreateNote(it) } +
            event.taggedAddresses().map { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, repliesTo)

        // Counts the replies
        repliesTo.forEach {
            it.addBoost(note)
        }

        refreshObservers(note)
    }

    fun consume(event: CommunityPostApprovalEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)

        val communities = event.communities()
        val eventsApproved = event.approvedEvents().mapNotNull { checkGetOrCreateNote(it) }

        val repliesTo = communities.map { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, eventsApproved)

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

        repliesTo.forEach {
            it.addReaction(note)
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
                it.liveSet?.innerReports?.invalidateData()
            }
        }

        refreshObservers(note)
    }

    fun consume(event: ChannelCreateEvent) {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreateChannel(event.id) {
            PublicChatChannel(it)
        }
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
            if (oldChannel is PublicChatChannel) {
                oldChannel.updateChannelInfo(author, event.channelInfo(), event.createdAt)
            }
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
                if (oldChannel is PublicChatChannel) {
                    oldChannel.updateChannelInfo(author, event.channelInfo(), event.createdAt)
                }
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

        // Log.d("CM", "New Chat Note (${note.author?.toBestDisplayName()} ${note.event?.content()} ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach {
            it.addReply(note)
        }

        refreshObservers(note)
    }

    fun consume(event: LiveActivitiesChatMessageEvent, relay: Relay?) {
        val activityId = event.activity() ?: return

        val channel = getOrCreateChannel(activityId.toTag()) {
            LiveActivitiesChannel(activityId)
        }

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
            .filter { it != event.activity()?.toTag() }
            .mapNotNull { checkGetOrCreateNote(it) }

        note.loadEvent(event, author, replyTo)

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

        val zapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }

        if (zapRequest == null || zapRequest.event !is LnZapRequestEvent) {
            Log.e("ZP", "Zap Request not found. Unable to process Zap {${event.toJson()}}")
            return
        }

        val author = getOrCreateUser(event.pubKey)
        val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
        val repliesTo = event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
            event.taggedAddresses().map { getOrCreateAddressableNote(it) } +
            (
                (zapRequest.event as? LnZapRequestEvent)?.taggedAddresses()?.map { getOrCreateAddressableNote(it) } ?: emptySet<Note>()
                )

        note.loadEvent(event, author, repliesTo)

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

    fun consume(event: AudioHeaderEvent, relay: Relay?) {
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
            val cachePath = File(Amethyst.instance.applicationContext.cacheDir, "NIP95")
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

    private fun consume(event: ChatMessageEvent, relay: Relay?) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        val recipientsHex = event.recipientsPubKey().plus(event.pubKey).toSet()
        val recipients = recipientsHex.mapNotNull { checkGetOrCreateUser(it) }.toSet()

        // Log.d("PM", "${author.toBestDisplayName()} to ${recipient?.toBestDisplayName()}")

        val repliesTo = event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }

        note.loadEvent(event, author, repliesTo)

        if (recipients.isNotEmpty()) {
            recipients.forEach {
                val groupMinusRecipient = recipientsHex.minus(it.pubkeyHex)

                val authorGroup = if (groupMinusRecipient.isEmpty()) {
                    // note to self
                    ChatroomKey(persistentSetOf(it.pubkeyHex))
                } else {
                    ChatroomKey(groupMinusRecipient.toImmutableSet())
                }

                it.addMessage(authorGroup, note)
            }
        }

        refreshObservers(note)
    }

    private fun consume(event: SealedGossipEvent, relay: Relay?) {
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

    fun consume(event: GiftWrapEvent, relay: Relay?) {
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
            (it.event !is GenericRepostEvent && it.event !is RepostEvent && it.event !is CommunityPostApprovalEvent && it.event !is ReactionEvent && it.event !is GiftWrapEvent && it.event !is LnZapEvent && it.event !is LnZapRequestEvent) &&
                (
                    it.event?.content()?.contains(text, true) ?: false ||
                        it.event?.matchTag1With(text) ?: false ||
                        it.idHex.startsWith(text, true) ||
                        it.idNote().startsWith(text, true)
                    )
        } + addressables.values.filter {
            (it.event !is GenericRepostEvent && it.event !is RepostEvent && it.event !is CommunityPostApprovalEvent && it.event !is ReactionEvent && it.event !is GiftWrapEvent && it.event !is LnZapEvent && it.event !is LnZapRequestEvent) &&
                (
                    it.event?.content()?.contains(text, true) ?: false ||
                        it.event?.matchTag1With(text) ?: false ||
                        it.idHex.startsWith(text, true)
                    )
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

    suspend fun findStatusesForUser(user: User): ImmutableList<AddressableNote> {
        checkNotInMainThread()

        return addressables.filter {
            val noteEvent = it.value.event
            (noteEvent is StatusEvent && noteEvent.pubKey == user.pubkeyHex && !noteEvent.isExpired() && noteEvent.content.isNotBlank())
        }.values
            .sortedWith(compareBy({ it.event?.expiration() ?: it.event?.createdAt() }, { it.idHex }))
            .reversed()
            .toImmutableList()
    }

    fun cleanObservers() {
        notes.forEach {
            it.value.clearLive()
        }

        addressables.forEach {
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

            val childrenToBeRemoved = mutableListOf<Note>()

            toBeRemoved.forEach {
                removeFromCache(it)

                childrenToBeRemoved.addAll(it.removeAllChildNotes())
            }

            removeFromCache(childrenToBeRemoved)

            if (toBeRemoved.size > 100 || it.value.notes.size > 100) {
                println("PRUNE: ${toBeRemoved.size} messages removed from ${it.value.toBestDisplayName()}. ${it.value.notes.size} kept")
            }
        }

        users.forEach { userPair ->
            userPair.value.privateChatrooms.values.map {
                val toBeRemoved = it.pruneMessagesToTheLatestOnly()

                val childrenToBeRemoved = mutableListOf<Note>()

                toBeRemoved.forEach {
                    removeFromCache(it)

                    childrenToBeRemoved.addAll(it.removeAllChildNotes())
                }

                removeFromCache(childrenToBeRemoved)

                if (toBeRemoved.size > 1) {
                    println("PRUNE: ${toBeRemoved.size} private messages with ${userPair.value.toBestDisplayName()} removed. ${it.roomMessages.size} kept")
                }
            }
        }
    }

    fun prunePastVersionsOfReplaceables() {
        val toBeRemoved = notes.filter {
            val noteEvent = it.value.event
            if (noteEvent is AddressableEvent) {
                noteEvent.createdAt() < (addressables[noteEvent.address().toTag()]?.event?.createdAt() ?: 0)
            } else {
                false
            }
        }.values

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            val newerVersion = addressables[(it.event as? AddressableEvent)?.address()?.toTag()]
            if (newerVersion != null) {
                it.moveAllReferencesTo(newerVersion)
            }

            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} old version of addressables removed.")
        }
    }

    fun pruneRepliesAndReactions(accounts: Set<HexKey>) {
        checkNotInMainThread()

        val toBeRemoved = notes.filter {
            (
                (it.value.event is TextNoteEvent && !it.value.isNewThread()) ||
                    it.value.event is ReactionEvent || it.value.event is LnZapEvent || it.value.event is LnZapRequestEvent ||
                    it.value.event is ReportEvent || it.value.event is GenericRepostEvent
                ) &&
                it.value.replyTo?.any { it.liveSet?.isInUse() == true } != true &&
                it.value.liveSet?.isInUse() != true && // don't delete if observing.
                it.value.author?.pubkeyHex !in accounts && // don't delete if it is the logged in account
                it.value.event?.isTaggedUsers(accounts) != true // don't delete if it's a notification to the logged in user
        }.values

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        toBeRemoved.forEach {
            it.replyTo?.forEach { masterNote ->
                masterNote.clearEOSE() // allows reloading of these events
            }
        }

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} thread replies removed.")
        }
    }

    private fun removeFromCache(note: Note) {
        note.replyTo?.forEach { masterNote ->
            masterNote.removeReply(note)
            masterNote.removeBoost(note)
            masterNote.removeReaction(note)
            masterNote.removeZap(note)
            masterNote.removeReport(note)
            masterNote.clearEOSE() // allows reloading of these events if needed
        }

        val noteEvent = note.event

        if (noteEvent is LnZapEvent) {
            noteEvent.zappedAuthor().forEach {
                val author = getUserIfExists(it)
                author?.removeZap(note)
                author?.clearEOSE()
            }
        }
        if (noteEvent is LnZapRequestEvent) {
            noteEvent.zappedAuthor().mapNotNull {
                val author = getUserIfExists(it)
                author?.removeZap(note)
                author?.clearEOSE()
            }
        }
        if (noteEvent is ReportEvent) {
            noteEvent.reportedAuthor().mapNotNull {
                val author = getUserIfExists(it.key)
                author?.removeReport(note)
                author?.clearEOSE()
            }
        }

        notes.remove(note.idHex)
    }

    fun removeFromCache(nextToBeRemoved: List<Note>) {
        nextToBeRemoved.forEach { note ->
            removeFromCache(note)
        }
    }

    fun pruneExpiredEvents() {
        checkNotInMainThread()

        val toBeRemoved = notes.filter {
            it.value.event?.isExpired() == true
        }.values

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} thread replies removed.")
        }
    }

    fun pruneHiddenMessages(account: Account) {
        checkNotInMainThread()

        val childrenToBeRemoved = mutableListOf<Note>()

        val toBeRemoved = account.hiddenUsers.map { userHex ->
            (
                notes.values.filter {
                    it.event?.pubKey() == userHex
                } + addressables.values.filter {
                    it.event?.pubKey() == userHex
                }
                ).toSet()
        }.flatten()

        toBeRemoved.forEach {
            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        println("PRUNE: ${toBeRemoved.size} messages removed because they were Hidden")
    }

    fun pruneContactLists(loggedIn: Set<HexKey>) {
        checkNotInMainThread()

        var removingContactList = 0
        users.values.forEach {
            if (it.pubkeyHex !in loggedIn &&
                (it.liveSet == null || it.liveSet?.isInUse() == false) &&
                it.latestContactList != null
            ) {
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
        if (justVerify(event)) {
            justConsume(event, relay)
        }
    }

    fun justVerify(event: Event): Boolean {
        checkNotInMainThread()

        return if (!event.hasValidSignature()) {
            try {
                event.checkSignature()
            } catch (e: Exception) {
                Log.w("Event failed retest ${event.kind}", (e.message ?: "") + event.toJson())
            }
            false
        } else {
            true
        }
    }

    fun justConsume(event: Event, relay: Relay?) {
        checkNotInMainThread()

        try {
            when (event) {
                is AdvertisedRelayListEvent -> consume(event)
                is AppDefinitionEvent -> consume(event)
                is AppRecommendationEvent -> consume(event)
                is AudioHeaderEvent -> consume(event, relay)
                is AudioTrackEvent -> consume(event)
                is BadgeAwardEvent -> consume(event)
                is BadgeDefinitionEvent -> consume(event)
                is BadgeProfilesEvent -> consume(event)
                is BookmarkListEvent -> consume(event)
                is CalendarEvent -> consume(event)
                is CalendarDateSlotEvent -> consume(event)
                is CalendarTimeSlotEvent -> consume(event)
                is CalendarRSVPEvent -> consume(event)
                is ChannelCreateEvent -> consume(event)
                is ChannelHideMessageEvent -> consume(event)
                is ChannelMessageEvent -> consume(event, relay)
                is ChannelMetadataEvent -> consume(event)
                is ChannelMuteUserEvent -> consume(event)
                is ChatMessageEvent -> consume(event, relay)
                is ClassifiedsEvent -> consume(event)
                is CommunityDefinitionEvent -> consume(event, relay)
                is CommunityPostApprovalEvent -> {
                    event.containedPost()?.let {
                        verifyAndConsume(it, relay)
                    }
                    consume(event)
                }
                is ContactListEvent -> consume(event)
                is DeletionEvent -> consume(event)
                is EmojiPackEvent -> consume(event)
                is EmojiPackSelectionEvent -> consume(event)
                is SealedGossipEvent -> consume(event, relay)

                is FileHeaderEvent -> consume(event, relay)
                is FileStorageEvent -> consume(event, relay)
                is FileStorageHeaderEvent -> consume(event, relay)
                is GiftWrapEvent -> consume(event, relay)
                is HighlightEvent -> consume(event, relay)
                is LiveActivitiesEvent -> consume(event, relay)
                is LiveActivitiesChatMessageEvent -> consume(event, relay)
                is LnZapEvent -> {
                    event.zapRequest?.let {
                        // must have a valid request
                        verifyAndConsume(it, relay)
                        consume(event)
                    }
                }
                is LnZapRequestEvent -> consume(event)
                is LnZapPaymentRequestEvent -> consume(event)
                is LnZapPaymentResponseEvent -> consume(event)
                is LongTextNoteEvent -> consume(event, relay)
                is MetadataEvent -> consume(event)
                is NNSEvent -> comsume(event)
                is PrivateDmEvent -> consume(event, relay)
                is PinListEvent -> consume(event)
                is PeopleListEvent -> consume(event)
                is PollNoteEvent -> consume(event, relay)
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
                is GenericRepostEvent -> {
                    event.containedPost()?.let {
                        verifyAndConsume(it, relay)
                    }
                    consume(event)
                }
                is StatusEvent -> consume(event, relay)
                is TextNoteEvent -> consume(event, relay)

                else -> {
                    Log.w("Event Not Supported", event.toJson())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Stable
class LocalCacheLiveData {
    private val _newEventBundles = MutableSharedFlow<Set<Note>>(0, 10, BufferOverflow.DROP_OLDEST)
    val newEventBundles = _newEventBundles.asSharedFlow() // read-only public view

    // Refreshes observers in batches.
    private val bundler = BundledInsert<Note>(1000, Dispatchers.IO)

    fun invalidateData(newNote: Note) {
        bundler.invalidateList(newNote) { bundledNewNotes ->
            _newEventBundles.emit(bundledNewNotes)
        }
    }
}
