/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.model

import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.data.DeletionIndex
import com.vitorpamplona.amethyst.commons.data.LargeCache
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.HexValidator
import com.vitorpamplona.quartz.encoders.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.encoders.decodePublicKeyAsHexOrNull
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
import com.vitorpamplona.quartz.events.ChannelListEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChannelMuteUserEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityListEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.DeletionEvent
import com.vitorpamplona.quartz.events.DirectMessageRelayListEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.FhirResourceEvent
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileServersEvent
import com.vitorpamplona.quartz.events.FileStorageEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.GitIssueEvent
import com.vitorpamplona.quartz.events.GitPatchEvent
import com.vitorpamplona.quartz.events.GitReplyEvent
import com.vitorpamplona.quartz.events.GitRepositoryEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.NNSEvent
import com.vitorpamplona.quartz.events.OtsEvent
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
import com.vitorpamplona.quartz.events.TextNoteModificationEvent
import com.vitorpamplona.quartz.events.VideoHorizontalEvent
import com.vitorpamplona.quartz.events.VideoVerticalEvent
import com.vitorpamplona.quartz.events.WikiNoteEvent
import com.vitorpamplona.quartz.events.WrappedEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
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

    val users = LargeCache<HexKey, User>()
    val notes = LargeCache<HexKey, Note>()
    val addressables = LargeCache<String, AddressableNote>()
    val channels = LargeCache<HexKey, Channel>()
    val awaitingPaymentRequests = ConcurrentHashMap<HexKey, Pair<Note?, (LnZapPaymentResponseEvent) -> Unit>>(10)

    val deletionIndex = DeletionIndex()

    fun checkGetOrCreateUser(key: String): User? {
        // checkNotInMainThread()

        if (isValidHex(key)) {
            return getOrCreateUser(key)
        }
        return null
    }

    fun getOrCreateUser(key: HexKey): User {
        // checkNotInMainThread()
        require(isValidHex(key = key)) { "$key is not a valid hex" }

        return users.getOrCreate(key) {
            User(it)
        }
    }

    fun getUserIfExists(key: String): User? {
        if (key.isEmpty()) return null
        return users.get(key)
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? {
        return addressables.get(key)
    }

    fun getNoteIfExists(key: String): Note? {
        return addressables.get(key) ?: notes.get(key)
    }

    fun getChannelIfExists(key: String): Channel? {
        return channels.get(key)
    }

    fun getNoteIfExists(event: Event): Note? {
        return if (event is AddressableEvent) {
            getAddressableNoteIfExists(event.addressTag())
        } else {
            getNoteIfExists(event.id)
        }
    }

    fun getOrCreateNote(event: Event): Note {
        return if (event is AddressableEvent) {
            getOrCreateAddressableNote(event.address())
        } else {
            getOrCreateNote(event.id)
        }
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

    fun getOrAddAliasNote(
        idHex: String,
        note: Note,
    ): Note {
        checkNotInMainThread()

        require(isValidHex(idHex)) { "$idHex is not a valid hex" }

        return notes.getOrCreate(idHex) {
            note
        }
    }

    fun getOrCreateNote(idHex: String): Note {
        checkNotInMainThread()

        require(isValidHex(idHex)) { "$idHex is not a valid hex" }

        return notes.getOrCreate(idHex) {
            Note(idHex)
        }
    }

    fun getOrCreateChannel(
        key: String,
        channelFactory: (String) -> Channel,
    ): Channel {
        checkNotInMainThread()

        return channels.getOrCreate(key, channelFactory)
    }

    fun checkGetOrCreateChannel(key: String): Channel? {
        checkNotInMainThread()

        if (isValidHex(key)) {
            return channels.getOrCreate(key) { PublicChatChannel(key) }
        }
        val aTag = ATag.parse(key, null)
        if (aTag != null) {
            return channels.getOrCreate(aTag.toTag()) { LiveActivitiesChannel(aTag) }
        }
        return null
    }

    private fun isValidHex(key: String): Boolean {
        if (key.isBlank()) return false
        if (key.contains(":")) return false

        return HexValidator.isHex(key)
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
        return addressables.getOrCreate(key.toTag()) {
            AddressableNote(key)
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

    fun consume(
        event: MetadataEvent,
        relay: Relay?,
    ) {
        // new event
        val oldUser = getOrCreateUser(event.pubKey)
        val currentMetadata = oldUser.latestMetadata

        if (currentMetadata == null || event.createdAt > currentMetadata.createdAt) {
            oldUser.latestMetadata = event

            val newUserMetadata = event.contactMetaData()
            if (newUserMetadata != null) {
                oldUser.updateUserInfo(newUserMetadata, event)
            }
            // Log.d("MT", "New User Metadata ${oldUser.pubkeyDisplayHex()} ${oldUser.toBestDisplayName()} from ${relay?.url}")
        } else {
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()}
            // ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }
    }

    fun consume(event: ContactListEvent) {
        val user = getOrCreateUser(event.pubKey)

        // avoids processing empty contact lists.
        if (event.createdAt > (user.latestContactList?.createdAt ?: 0) && !event.tags.isEmpty()) {
            user.updateContactList(event)
            // Log.d("CL", "Consumed contact list ${user.toNostrUri()} ${event.relays()?.size}")
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
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()}
            // ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }
    }

    fun formattedDateTime(timestamp: Long): String {
        return Instant.ofEpochSecond(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("uuuu MMM d hh:mm a"))
    }

    fun consume(
        event: TextNoteEvent,
        relay: Relay? = null,
    ) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let { it.spamCounter++ }
            return
        }

        val replyTo = computeReplyTo(event)

        note.loadEvent(event, author, replyTo)

        // Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()}
        // ${note.event?.content()?.split("\n")?.take(100)} ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    fun consume(
        event: GitPatchEvent,
        relay: Relay? = null,
    ) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let { it.spamCounter++ }
            return
        }

        note.loadEvent(event, author, emptyList())

        refreshObservers(note)
    }

    fun consume(
        event: GitIssueEvent,
        relay: Relay? = null,
    ) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let { it.spamCounter++ }
            return
        }

        note.loadEvent(event, author, emptyList())

        refreshObservers(note)
    }

    fun consume(
        event: GitReplyEvent,
        relay: Relay? = null,
    ) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let { it.spamCounter++ }
            return
        }

        val replyTo = computeReplyTo(event)

        // println("New GitReply ${event.id} for ${replyTo.firstOrNull()?.event?.id()} ${event.tagsWithoutCitations().filter { it != event.repository()?.toTag() }.firstOrNull()}")

        note.loadEvent(event, author, replyTo)

        // Counts the replies
        replyTo.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    fun consume(
        event: LongTextNoteEvent,
        relay: Relay?,
    ) {
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
            relay?.let { it.spamCounter++ }
            return
        }

        val replyTo = computeReplyTo(event)

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            refreshObservers(note)
        }
    }

    fun consume(
        event: WikiNoteEvent,
        relay: Relay?,
    ) {
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
            relay?.let { it.spamCounter++ }
            return
        }

        val replyTo = computeReplyTo(event)

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            refreshObservers(note)
        }
    }

    fun computeReplyTo(event: Event): List<Note> {
        return when (event) {
            is PollNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is WikiNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is LongTextNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is GitReplyEvent -> event.tagsWithoutCitations().filter { it != event.repository()?.toTag() }.mapNotNull { checkGetOrCreateNote(it) }
            is TextNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is ChatMessageEvent -> event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            is LnZapEvent ->
                event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) } +
                    (event.zapRequest?.taggedAddresses()?.map { getOrCreateAddressableNote(it) } ?: emptyList())
            is LnZapRequestEvent ->
                event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is BadgeProfilesEvent ->
                event.badgeAwardEvents().mapNotNull { checkGetOrCreateNote(it) } +
                    event.badgeAwardDefinitions().map { getOrCreateAddressableNote(it) }
            is BadgeAwardEvent -> event.awardDefinition().map { getOrCreateAddressableNote(it) }
            is PrivateDmEvent -> event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            is RepostEvent ->
                event.boostedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is GenericRepostEvent ->
                event.boostedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is CommunityPostApprovalEvent -> event.approvedEvents().mapNotNull { checkGetOrCreateNote(it) }
            is ReactionEvent ->
                event.originalPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is ReportEvent ->
                event.reportedPost().mapNotNull { checkGetOrCreateNote(it.key) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is ChannelMessageEvent ->
                event
                    .tagsWithoutCitations()
                    .filter { it != event.channel() }
                    .mapNotNull { checkGetOrCreateNote(it) }
            is LiveActivitiesChatMessageEvent ->
                event
                    .tagsWithoutCitations()
                    .filter { it != event.activity()?.toTag() }
                    .mapNotNull { checkGetOrCreateNote(it) }

            is DraftEvent -> {
                event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) } + event.taggedAddresses().mapNotNull { checkGetOrCreateAddressableNote(it.toTag()) }
            }

            else -> emptyList<Note>()
        }
    }

    fun consume(
        event: PollNoteEvent,
        relay: Relay? = null,
    ) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            relay?.let { it.spamCounter++ }
            return
        }

        val replyTo = computeReplyTo(event)

        note.loadEvent(event, author, replyTo)

        // Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()}
        // ${note.event?.content()?.split("\n")?.take(100)} ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    private fun consume(
        event: LiveActivitiesEvent,
        relay: Relay?,
    ) {
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

            val channel =
                getOrCreateChannel(note.idHex) { LiveActivitiesChannel(note.address) }
                    as? LiveActivitiesChannel

            val creator = event.host()?.ifBlank { null }?.let { checkGetOrCreateUser(it) } ?: author

            channel?.updateChannelInfo(creator, event, event.createdAt)

            refreshObservers(note)
        }
    }

    fun consume(
        event: MuteListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: CommunityListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: GitRepositoryEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: ChannelListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: FileServersEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: PeopleListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: AdvertisedRelayListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: DirectMessageRelayListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: CommunityDefinitionEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: EmojiPackSelectionEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: EmojiPackEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: ClassifiedsEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: PinListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: RelaySetEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: AudioTrackEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: VideoVerticalEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: VideoHorizontalEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: StatusEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: OtsEvent,
        relay: Relay?,
    ) {
        val version = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (version.event?.id() == event.id()) return

        // makes sure the OTS has a valid certificate
        if (event.cacheVerify() == null) return // no valid OTS

        if (version.event == null) {
            version.loadEvent(event, author, emptyList())
            version.liveSet?.innerOts?.invalidateData()
        }

        refreshObservers(version)
    }

    fun consume(
        event: BadgeDefinitionEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

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

        val replyTo = computeReplyTo(event)

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            refreshObservers(note)
        }
    }

    fun consume(event: BadgeAwardEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()}
        // ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)
        val awardDefinition = computeReplyTo(event)

        note.loadEvent(event, author, awardDefinition)

        // Replies of an Badge Definition are Award Events
        awardDefinition.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    private fun comsume(
        event: NNSEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: AppDefinitionEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: CalendarEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: CalendarDateSlotEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: CalendarTimeSlotEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: CalendarRSVPEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consumeBaseReplaceable(
        event: BaseAddressableEvent,
        relay: Relay?,
    ) {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val replyTos = computeReplyTo(event)

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

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTos)

            refreshObservers(note)
        }
    }

    fun consume(
        event: AppRecommendationEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    @Suppress("UNUSED_PARAMETER")
    fun consume(event: RecommendRelayEvent) {
        //        // Log.d("RR", event.toJson())
    }

    fun consume(
        event: PrivateDmEvent,
        relay: Relay?,
    ): Note {
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

        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        if (recipient != null) {
            author.addMessage(recipient, note)
            recipient.addMessage(author, note)
        }

        refreshObservers(note)

        return note
    }

    fun consume(event: DeletionEvent) {
        if (deletionIndex.add(event)) {
            var deletedAtLeastOne = false

            event.deleteEvents()
                .mapNotNull { getNoteIfExists(it) }
                .forEach { deleteNote ->
                    // must be the same author
                    if (deleteNote.author?.pubkeyHex == event.pubKey) {
                        // reverts the add
                        deleteNote(deleteNote)

                        deletedAtLeastOne = true
                    }
                }

            val addressList = event.deleteAddressTags()
            val addressSet = addressList.toSet()

            addressList
                .mapNotNull { getAddressableNoteIfExists(it) }
                .forEach { deleteNote ->
                    // must be the same author
                    if (deleteNote.author?.pubkeyHex == event.pubKey && (deleteNote.createdAt() ?: 0) <= event.createdAt) {
                        // Counts the replies
                        deleteNote(deleteNote)

                        addressables.remove(deleteNote.idHex)

                        deletedAtLeastOne = true
                    }
                }

            notes.forEach { key, note ->
                val noteEvent = note.event
                if (noteEvent is AddressableEvent && noteEvent.addressTag() in addressSet) {
                    if (noteEvent.pubKey() == event.pubKey && noteEvent.createdAt() <= event.createdAt) {
                        deleteNote(note)
                        deletedAtLeastOne = true
                    }
                }
            }

            if (deletedAtLeastOne) {
                val note = Note(event.id)
                note.loadEvent(event, getOrCreateUser(event.pubKey), emptyList())
                refreshObservers(note)
            }
        }
    }

    private fun deleteNote(deleteNote: Note) {
        val deletedEvent = deleteNote.event

        val mentions =
            deleteNote.event
                ?.tags()
                ?.filter { it.firstOrNull() == "p" }
                ?.mapNotNull { it.getOrNull(1) }
                ?.mapNotNull { checkGetOrCreateUser(it) }

        mentions?.forEach { user -> user.removeReport(deleteNote) }

        // Counts the replies
        deleteNote.replyTo?.forEach { masterNote ->
            masterNote.removeReply(deleteNote)
            masterNote.removeBoost(deleteNote)
            masterNote.removeReaction(deleteNote)
            masterNote.removeZap(deleteNote)
            masterNote.removeZapPayment(deleteNote)
            masterNote.removeReport(deleteNote)
        }

        deleteNote.channelHex()?.let { getChannelIfExists(it)?.removeNote(deleteNote) }

        (deletedEvent as? LiveActivitiesChatMessageEvent)?.activity()?.let {
            getChannelIfExists(it.toTag())?.removeNote(deleteNote)
        }

        if (deletedEvent is PrivateDmEvent) {
            val author = deleteNote.author
            val recipient =
                deletedEvent.verifiedRecipientPubKey()?.let {
                    checkGetOrCreateUser(it)
                }

            if (recipient != null && author != null) {
                author.removeMessage(recipient, deleteNote)
                recipient.removeMessage(author, deleteNote)
            }
        }

        if (deletedEvent is DraftEvent) {
            deletedEvent.allCache().forEach {
                it?.let {
                    deindexDraftAsRealEvent(deleteNote, it)
                }
            }
        }

        if (deletedEvent is WrappedEvent) {
            deleteWraps(deletedEvent)
        }

        notes.remove(deleteNote.idHex)
    }

    fun deleteWraps(event: WrappedEvent) {
        event.host?.let {
            // seal
            getNoteIfExists(it.id)?.let {
                val noteEvent = it.event
                if (noteEvent is WrappedEvent) {
                    deleteWraps(noteEvent)
                }
            }
            notes.remove(it.id)
        }
    }

    fun consume(event: RepostEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()}
        // ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        // Counts the replies
        repliesTo.forEach { it.addBoost(note) }

        refreshObservers(note)
    }

    fun consume(event: GenericRepostEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()}
        // ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        // Counts the replies
        repliesTo.forEach { it.addBoost(note) }

        refreshObservers(note)
    }

    fun consume(event: CommunityPostApprovalEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        // Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()}
        // ${formattedDateTime(event.createdAt)}")

        val author = getOrCreateUser(event.pubKey)

        val communities = event.communities()
        val eventsApproved = computeReplyTo(event)

        val repliesTo = communities.map { getOrCreateAddressableNote(it) }

        note.loadEvent(event, author, eventsApproved)

        // Counts the replies
        repliesTo.forEach { it.addBoost(note) }

        refreshObservers(note)
    }

    fun consume(event: ReactionEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        // Log.d("RE", "New Reaction ${event.content} (${notes.size},${users.size})
        // ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        repliesTo.forEach { it.addReaction(note) }

        refreshObservers(note)
    }

    fun consume(
        event: ReportEvent,
        relay: Relay?,
    ) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        val mentions = event.reportedAuthor().mapNotNull { checkGetOrCreateUser(it.key) }
        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        // Log.d("RP", "New Report ${event.content} by ${note.author?.toBestDisplayName()}
        // ${formattedDateTime(event.createdAt)}")
        // Adds notifications to users.
        if (repliesTo.isEmpty()) {
            mentions.forEach { it.addReport(note) }
        } else {
            repliesTo.forEach { it.addReport(note) }

            mentions.forEach {
                // doesn't add to reports, but triggers recounts
                it.liveSet?.innerReports?.invalidateData()
            }
        }

        refreshObservers(note)
    }

    fun consume(event: ChannelCreateEvent) {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreateChannel(event.id) { PublicChatChannel(it) }
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
        // Log.d("MT", "New PublicChatMetadata ${event.channelInfo()}")
        if (channelId.isNullOrBlank()) return

        // new event
        val oldChannel = checkGetOrCreateChannel(channelId) ?: return

        val author = getOrCreateUser(event.pubKey)
        if (event.createdAt > oldChannel.updatedMetadataAt) {
            if (oldChannel is PublicChatChannel) {
                oldChannel.updateChannelInfo(author, event.channelInfo(), event.createdAt)
            }
        } else {
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()}
            // ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }

        val note = getOrCreateNote(event.id)
        if (note.event == null) {
            oldChannel.addNote(note)
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
    }

    fun consume(
        event: ChannelMessageEvent,
        relay: Relay?,
    ) {
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
            relay?.let { it.spamCounter++ }
            return
        }

        val replyTo = computeReplyTo(event)

        note.loadEvent(event, author, replyTo)

        // Log.d("CM", "New Chat Note (${note.author?.toBestDisplayName()} ${note.event?.content()}
        // ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    fun consume(
        event: LiveActivitiesChatMessageEvent,
        relay: Relay?,
    ) {
        val activityId = event.activity() ?: return

        val channel = getOrCreateChannel(activityId.toTag()) { LiveActivitiesChannel(activityId) }

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
            relay?.let { it.spamCounter++ }
            return
        }

        val replyTo = computeReplyTo(event)

        note.loadEvent(event, author, replyTo)

        // Counts the replies
        replyTo.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    @Suppress("UNUSED_PARAMETER")
    fun consume(event: ChannelHideMessageEvent) {}

    @Suppress("UNUSED_PARAMETER")
    fun consume(event: ChannelMuteUserEvent) {}

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
        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        // Log.d("ZP", "New ZapEvent ${event.content} (${notes.size},${users.size})
        // ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        repliesTo.forEach { it.addZap(zapRequest, note) }
        mentions.forEach { it.addZap(zapRequest, note) }

        refreshObservers(note)
    }

    fun consume(event: LnZapRequestEvent) {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return

        val author = getOrCreateUser(event.pubKey)
        val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        // Log.d("ZP", "New Zap Request ${event.content} (${notes.size},${users.size})
        // ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

        repliesTo.forEach { it.addZap(note, null) }
        mentions.forEach { it.addZap(note, null) }

        refreshObservers(note)
    }

    fun consume(
        event: AudioHeaderEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: FileHeaderEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: FileStorageHeaderEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: FhirResourceEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: TextNoteModificationEvent,
        relay: Relay?,
    ) {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        note.loadEvent(event, author, emptyList())

        event.editedNote()?.let {
            checkGetOrCreateNote(it)?.let { editedNote ->
                modificationCache.remove(editedNote.idHex)
                // must update list of Notes to quickly update the user.
                editedNote.liveSet?.innerModifications?.invalidateData()
            }
        }

        refreshObservers(note)
    }

    fun consume(
        event: HighlightEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: FileStorageEvent,
        relay: Relay?,
    ) {
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
                Log.i(
                    "FileStorageEvent",
                    "NIP95 File received from ${relay?.url} and saved to disk as $file",
                )
            }
        } catch (e: IOException) {
            Log.e("FileStorageEvent", "FileStorageEvent save to disk error: " + event.id, e)
        }

        // Already processed this event.
        if (note.event != null) return

        // this is an invalid event. But we don't need to keep the data in memory.
        val eventNoData =
            FileStorageEvent(event.id, event.pubKey, event.createdAt, event.tags, "", event.sig)

        note.loadEvent(eventNoData, author, emptyList())

        refreshObservers(note)
    }

    private fun consume(
        event: ChatMessageEvent,
        relay: Relay?,
    ) {
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

        val repliesTo = computeReplyTo(event)

        note.loadEvent(event, author, repliesTo)

        if (recipients.isNotEmpty()) {
            recipients.forEach {
                val groupMinusRecipient = recipientsHex.minus(it.pubkeyHex)

                val authorGroup =
                    if (groupMinusRecipient.isEmpty()) {
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

    fun consume(
        event: SealedGossipEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: GiftWrapEvent,
        relay: Relay?,
    ) {
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

    fun consume(
        event: LnZapPaymentRequestEvent,
        zappedNote: Note?,
        onResponse: (LnZapPaymentResponseEvent) -> Unit,
    ) {
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

        requestNote?.let { request -> zappedNote?.addZapPayment(request, note) }

        if (responseCallback != null) {
            responseCallback(event)
        }
    }

    fun findUsersStartingWith(username: String): List<User> {
        checkNotInMainThread()

        val key = decodePublicKeyAsHexOrNull(username)

        if (key != null) {
            val user = getUserIfExists(key)
            if (user != null) {
                return listOfNotNull(user)
            }
        }

        return users.filter { _, user: User ->
            (user.anyNameStartsWith(username)) ||
                user.pubkeyHex.startsWith(username, true) ||
                user.pubkeyNpub().startsWith(username, true)
        }
    }

    fun findNotesStartingWith(text: String): List<Note> {
        checkNotInMainThread()

        val key = decodeEventIdAsHexOrNull(text)

        if (key != null) {
            val note = getNoteIfExists(key)
            if (note != null) {
                return listOfNotNull(note)
            }
        }

        return notes.filter { _, note ->
            if (note.event is GenericRepostEvent ||
                note.event is RepostEvent ||
                note.event is CommunityPostApprovalEvent ||
                note.event is ReactionEvent ||
                note.event is LnZapEvent ||
                note.event is LnZapRequestEvent
            ) {
                return@filter false
            }

            if (note.event?.matchTag1With(text) == true ||
                note.idHex.startsWith(text, true) ||
                note.idNote().startsWith(text, true)
            ) {
                return@filter true
            }

            if (note.event?.isContentEncoded() == false) {
                return@filter note.event?.content()?.contains(text, true) ?: false
            }

            return@filter false
        } +
            addressables.filter { _, addressable ->
                if (addressable.event is GenericRepostEvent ||
                    addressable.event is RepostEvent ||
                    addressable.event is CommunityPostApprovalEvent ||
                    addressable.event is ReactionEvent ||
                    addressable.event is LnZapEvent ||
                    addressable.event is LnZapRequestEvent
                ) {
                    return@filter false
                }

                if (addressable.event?.matchTag1With(text) == true ||
                    addressable.idHex.startsWith(text, true)
                ) {
                    return@filter true
                }

                if (addressable.event?.isContentEncoded() == false) {
                    return@filter addressable.event?.content()?.contains(text, true) ?: false
                }

                return@filter false
            }
    }

    fun findChannelsStartingWith(text: String): List<Channel> {
        checkNotInMainThread()

        val key = decodeEventIdAsHexOrNull(text)
        if (key != null && getChannelIfExists(key) != null) {
            return listOfNotNull(getChannelIfExists(key))
        }

        return channels.filter { _, channel ->
            channel.anyNameStartsWith(text) ||
                channel.idHex.startsWith(text, true) ||
                channel.idNote().startsWith(text, true)
        }
    }

    suspend fun findStatusesForUser(user: User): ImmutableList<AddressableNote> {
        checkNotInMainThread()

        return addressables.filter { _, it ->
            val noteEvent = it.event
            (
                noteEvent is StatusEvent &&
                    noteEvent.pubKey == user.pubkeyHex &&
                    !noteEvent.isExpired() &&
                    noteEvent.content.isNotBlank()
            )
        }
            .sortedWith(compareBy({ it.event?.expiration() ?: it.event?.createdAt() }, { it.idHex }))
            .reversed()
            .toImmutableList()
    }

    suspend fun findEarliestOtsForNote(note: Note): Long? {
        checkNotInMainThread()

        var minTime: Long? = null
        val time = TimeUtils.now()

        notes.forEach { _, item ->
            val noteEvent = item.event
            if ((noteEvent is OtsEvent && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time))) {
                noteEvent.verifiedTime?.let { stampedTime ->
                    if (minTime == null || stampedTime < (minTime ?: Long.MAX_VALUE)) {
                        minTime = stampedTime
                    }
                }
            }
        }

        return minTime
    }

    val modificationCache = LruCache<HexKey, List<Note>>(20)

    fun cachedModificationEventsForNote(note: Note): List<Note>? {
        return modificationCache[note.idHex]
    }

    suspend fun findLatestModificationForNote(note: Note): List<Note> {
        checkNotInMainThread()

        val originalAuthor = note.author?.pubkeyHex ?: return emptyList()

        modificationCache[note.idHex]?.let {
            return it
        }

        val time = TimeUtils.now()

        val newNotes =
            notes.filter { _, item ->
                val noteEvent = item.event

                noteEvent is TextNoteModificationEvent && noteEvent.pubKey == originalAuthor && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time)
            }.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))

        modificationCache.put(note.idHex, newNotes)

        return newNotes
    }

    fun cleanObservers() {
        notes.forEach { _, it -> it.clearLive() }
        addressables.forEach { _, it -> it.clearLive() }
        users.forEach { _, it -> it.clearLive() }
    }

    fun pruneOldAndHiddenMessages(account: Account) {
        checkNotInMainThread()

        channels.forEach { _, channel ->
            val toBeRemoved = channel.pruneOldAndHiddenMessages(account)

            val childrenToBeRemoved = mutableListOf<Note>()

            toBeRemoved.forEach {
                removeFromCache(it)

                childrenToBeRemoved.addAll(it.removeAllChildNotes())
            }

            removeFromCache(childrenToBeRemoved)

            if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
                println(
                    "PRUNE: ${toBeRemoved.size} messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
                )
            }
        }

        users.forEach { _, user ->
            user.privateChatrooms.values.map {
                val toBeRemoved = it.pruneMessagesToTheLatestOnly()

                val childrenToBeRemoved = mutableListOf<Note>()

                toBeRemoved.forEach {
                    removeFromCache(it)

                    childrenToBeRemoved.addAll(it.removeAllChildNotes())
                }

                removeFromCache(childrenToBeRemoved)

                if (toBeRemoved.size > 1) {
                    println(
                        "PRUNE: ${toBeRemoved.size} private messages with ${user.toBestDisplayName()} removed. ${it.roomMessages.size} kept",
                    )
                }
            }
        }
    }

    fun prunePastVersionsOfReplaceables() {
        val toBeRemoved =
            notes.filter { _, note ->
                val noteEvent = note.event
                if (noteEvent is AddressableEvent) {
                    noteEvent.createdAt() <
                        (addressables.get(noteEvent.address().toTag())?.event?.createdAt() ?: 0)
                } else {
                    false
                }
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            val newerVersion = (it.event as? AddressableEvent)?.address()?.toTag()?.let { tag -> addressables.get(tag) }
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

        val toBeRemoved =
            notes.filter { _, note ->
                (
                    (note.event is TextNoteEvent && !note.isNewThread()) ||
                        note.event is ReactionEvent ||
                        note.event is LnZapEvent ||
                        note.event is LnZapRequestEvent ||
                        note.event is ReportEvent ||
                        note.event is GenericRepostEvent
                ) &&
                    note.replyTo?.any { it.liveSet?.isInUse() == true } != true &&
                    note.liveSet?.isInUse() != true && // don't delete if observing.
                    note.author?.pubkeyHex !in
                    accounts && // don't delete if it is the logged in account
                    note.event?.isTaggedUsers(accounts) !=
                    true // don't delete if it's a notification to the logged in user
            }

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
        nextToBeRemoved.forEach { note -> removeFromCache(note) }
    }

    fun pruneExpiredEvents() {
        checkNotInMainThread()

        val now = TimeUtils.now()
        val toBeRemoved = notes.filter { _, it -> it.event?.isExpirationBefore(now) == true }

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

        val toBeRemoved =
            account.liveHiddenUsers.value
                ?.hiddenUsers
                ?.map { userHex ->
                    (notes.filter { _, it -> it.event?.pubKey() == userHex } + addressables.filter { _, it -> it.event?.pubKey() == userHex }).toSet()
                }
                ?.flatten()
                ?: emptyList()

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
        users.forEach { _, user ->
            if (
                user.pubkeyHex !in loggedIn &&
                (user.liveSet == null || user.liveSet?.isInUse() == false) &&
                user.latestContactList != null
            ) {
                user.latestContactList = null
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

    fun verifyAndConsume(
        event: Event,
        relay: Relay?,
    ) {
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
                if (e is CancellationException) throw e
                Log.w("Event failed retest ${event.kind}", (e.message ?: "") + event.toJson())
            }
            false
        } else {
            true
        }
    }

    fun consume(
        event: DraftEvent,
        relay: Relay?,
    ) {
        if (!event.isDeleted()) {
            consumeBaseReplaceable(event, relay)

            event.allCache().forEach {
                it?.let {
                    indexDraftAsRealEvent(event, it)
                }
            }
        }
    }

    fun indexDraftAsRealEvent(
        draftWrap: DraftEvent,
        draft: Event,
    ) {
        val note = getOrCreateAddressableNote(draftWrap.address())
        val author = getOrCreateUser(draftWrap.pubKey)

        when (draft) {
            is PrivateDmEvent -> {
                draft.verifiedRecipientPubKey()?.let { getOrCreateUser(it) }?.let { recipient ->
                    author.addMessage(recipient, note)
                    recipient.addMessage(author, note)
                }
            }
            is ChatMessageEvent -> {
                val recipientsHex = draft.recipientsPubKey().plus(draftWrap.pubKey).toSet()
                val recipients = recipientsHex.mapNotNull { checkGetOrCreateUser(it) }.toSet()

                if (recipients.isNotEmpty()) {
                    recipients.forEach {
                        val groupMinusRecipient = recipientsHex.minus(it.pubkeyHex)

                        val authorGroup =
                            if (groupMinusRecipient.isEmpty()) {
                                // note to self
                                ChatroomKey(persistentSetOf(it.pubkeyHex))
                            } else {
                                ChatroomKey(groupMinusRecipient.toImmutableSet())
                            }

                        it.addMessage(authorGroup, note)
                    }
                }
            }
            is ChannelMessageEvent -> {
                draft.channel()?.let { channelId ->
                    checkGetOrCreateChannel(channelId)?.let { channel ->
                        channel.addNote(note)
                    }
                }
            }
            is TextNoteEvent -> {
                val replyTo = computeReplyTo(draft)
                val author = getOrCreateUser(draftWrap.pubKey)
                note.loadEvent(draftWrap, author, replyTo)
                replyTo.forEach { it.addReply(note) }
            }
        }
    }

    fun deindexDraftAsRealEvent(
        draftWrap: Note,
        draft: Event,
    ) {
        val author = draftWrap.author ?: return

        when (draft) {
            is PrivateDmEvent -> {
                draft.verifiedRecipientPubKey()?.let { getOrCreateUser(it) }?.let { recipient ->
                    author.removeMessage(recipient, draftWrap)
                    recipient.removeMessage(author, draftWrap)
                }
            }
            is ChatMessageEvent -> {
                val recipientsHex = draft.recipientsPubKey().plus(author.pubkeyHex).toSet()
                val recipients = recipientsHex.mapNotNull { checkGetOrCreateUser(it) }.toSet()

                if (recipients.isNotEmpty()) {
                    recipients.forEach {
                        val groupMinusRecipient = recipientsHex.minus(it.pubkeyHex)

                        val authorGroup =
                            if (groupMinusRecipient.isEmpty()) {
                                // note to self
                                ChatroomKey(persistentSetOf(it.pubkeyHex))
                            } else {
                                ChatroomKey(groupMinusRecipient.toImmutableSet())
                            }

                        it.removeMessage(authorGroup, draftWrap)
                    }
                }
            }
            is ChannelMessageEvent -> {
                draft.channel()?.let { channelId ->
                    checkGetOrCreateChannel(channelId)?.let { channel ->
                        channel.removeNote(draftWrap)
                    }
                }
            }
            is TextNoteEvent -> {
                val replyTo = computeReplyTo(draft)
                replyTo.forEach { it.removeReply(draftWrap) }
            }
        }
    }

    fun justConsume(
        event: Event,
        relay: Relay?,
    ) {
        if (deletionIndex.hasBeenDeleted(event)) return

        checkNotInMainThread()

        try {
            when (event) {
                is AdvertisedRelayListEvent -> consume(event, relay)
                is AppDefinitionEvent -> consume(event, relay)
                is AppRecommendationEvent -> consume(event, relay)
                is AudioHeaderEvent -> consume(event, relay)
                is AudioTrackEvent -> consume(event, relay)
                is BadgeAwardEvent -> consume(event)
                is BadgeDefinitionEvent -> consume(event, relay)
                is BadgeProfilesEvent -> consume(event)
                is BookmarkListEvent -> consume(event)
                is CalendarEvent -> consume(event, relay)
                is CalendarDateSlotEvent -> consume(event, relay)
                is CalendarTimeSlotEvent -> consume(event, relay)
                is CalendarRSVPEvent -> consume(event, relay)
                is ChannelCreateEvent -> consume(event)
                is ChannelListEvent -> consume(event, relay)
                is ChannelHideMessageEvent -> consume(event)
                is ChannelMessageEvent -> consume(event, relay)
                is ChannelMetadataEvent -> consume(event)
                is ChannelMuteUserEvent -> consume(event)
                is ChatMessageEvent -> consume(event, relay)
                is ClassifiedsEvent -> consume(event, relay)
                is CommunityDefinitionEvent -> consume(event, relay)
                is CommunityListEvent -> consume(event, relay)
                is CommunityPostApprovalEvent -> {
                    event.containedPost()?.let { verifyAndConsume(it, relay) }
                    consume(event)
                }
                is ContactListEvent -> consume(event)
                is DeletionEvent -> consume(event)
                is DirectMessageRelayListEvent -> consume(event, relay)
                is DraftEvent -> consume(event, relay)
                is EmojiPackEvent -> consume(event, relay)
                is EmojiPackSelectionEvent -> consume(event, relay)
                is SealedGossipEvent -> consume(event, relay)
                is FhirResourceEvent -> consume(event, relay)
                is FileHeaderEvent -> consume(event, relay)
                is FileServersEvent -> consume(event, relay)
                is FileStorageEvent -> consume(event, relay)
                is FileStorageHeaderEvent -> consume(event, relay)
                is GiftWrapEvent -> consume(event, relay)
                is GitIssueEvent -> consume(event, relay)
                is GitReplyEvent -> consume(event, relay)
                is GitPatchEvent -> consume(event, relay)
                is GitRepositoryEvent -> consume(event, relay)
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
                is MetadataEvent -> consume(event, relay)
                is MuteListEvent -> consume(event, relay)
                is NNSEvent -> comsume(event, relay)
                is OtsEvent -> consume(event, relay)
                is PrivateDmEvent -> consume(event, relay)
                is PinListEvent -> consume(event, relay)
                is PeopleListEvent -> consume(event, relay)
                is PollNoteEvent -> consume(event, relay)
                is ReactionEvent -> consume(event)
                is RecommendRelayEvent -> consume(event)
                is RelaySetEvent -> consume(event, relay)
                is ReportEvent -> consume(event, relay)
                is RepostEvent -> {
                    event.containedPost()?.let { verifyAndConsume(it, relay) }
                    consume(event)
                }
                is GenericRepostEvent -> {
                    event.containedPost()?.let { verifyAndConsume(it, relay) }
                    consume(event)
                }
                is StatusEvent -> consume(event, relay)
                is TextNoteEvent -> consume(event, relay)
                is TextNoteModificationEvent -> consume(event, relay)
                is VideoHorizontalEvent -> consume(event, relay)
                is VideoVerticalEvent -> consume(event, relay)
                is WikiNoteEvent -> consume(event, relay)
                else -> {
                    Log.w("Event Not Supported", event.toJson())
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    fun hasConsumed(notificationEvent: Event): Boolean {
        return if (notificationEvent is AddressableEvent) {
            val note = addressables.get(notificationEvent.addressTag())
            val noteEvent = note?.event
            noteEvent != null && notificationEvent.createdAt <= noteEvent.createdAt()
        } else {
            val note = notes.get(notificationEvent.id)
            note?.event != null
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
        bundler.invalidateList(newNote) {
                bundledNewNotes ->
            _newEventBundles.emit(bundledNewNotes)
        }
    }
}
