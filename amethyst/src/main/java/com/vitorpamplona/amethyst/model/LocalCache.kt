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
import com.vitorpamplona.amethyst.model.observables.LatestByKindAndAuthor
import com.vitorpamplona.amethyst.model.observables.LatestByKindWithETag
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.ammolite.relays.BundledInsert
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.quartz.blossom.BlossomServersEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.relationshipStatus.RelationshipStatusEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.checkSignature
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.tagValueContains
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.addressables.mapTaggedAddress
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedAddresses
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip01Core.tags.events.forEachTaggedEventId
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.mapTaggedEventId
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUsers
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip03Timestamp.VerificationState
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.isATag
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.RelaySetEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

interface ILocalCache {
    fun verifyAndConsume(
        event: Event,
        relay: Relay?,
    )

    fun justVerify(event: Event): Boolean

    fun consume(
        event: DraftEvent,
        relay: Relay?,
    )

    fun markAsSeen(
        string: String,
        relay: Relay,
    ) {}
}

object LocalCache : ILocalCache {
    val antiSpam = AntiSpamFilter()

    val users = LargeCache<HexKey, User>()
    val notes = LargeCache<HexKey, Note>()
    val addressables = LargeCache<String, AddressableNote>()
    val channels = LargeCache<HexKey, Channel>()
    val awaitingPaymentRequests = ConcurrentHashMap<HexKey, Pair<Note?, (LnZapPaymentResponseEvent) -> Unit>>(10)

    val deletionIndex = DeletionIndex()

    val observablesByKindAndETag = ConcurrentHashMap<Int, ConcurrentHashMap<HexKey, LatestByKindWithETag<Event>>>(10)
    val observablesByKindAndAuthor = ConcurrentHashMap<Int, ConcurrentHashMap<HexKey, LatestByKindAndAuthor<Event>>>(10)

    val onNewEvents = mutableListOf<(Note) -> Unit>()

    fun <T : Event> observeETag(
        kind: Int,
        eventId: HexKey,
        scope: CoroutineScope,
    ): LatestByKindWithETag<T> {
        var eTagList = observablesByKindAndETag.get(kind)

        if (eTagList == null) {
            eTagList = ConcurrentHashMap<HexKey, LatestByKindWithETag<T>>(1) as ConcurrentHashMap<HexKey, LatestByKindWithETag<Event>>
            observablesByKindAndETag.put(kind, eTagList)
        }

        val value = eTagList.get(eventId)

        return if (value != null) {
            value
        } else {
            val newObject = LatestByKindWithETag<T>(kind, eventId) as LatestByKindWithETag<Event>
            val obj = eTagList.putIfAbsent(eventId, newObject) ?: newObject
            if (obj == newObject) {
                // initialize
                scope.launch(Dispatchers.IO) {
                    obj.init()
                }
            }
            obj
        } as LatestByKindWithETag<T>
    }

    fun <T : Event> observeAuthor(
        kind: Int,
        pubkey: HexKey,
        scope: CoroutineScope,
    ): LatestByKindAndAuthor<T> {
        var authorObsList = observablesByKindAndAuthor.get(kind)

        if (authorObsList == null) {
            authorObsList = ConcurrentHashMap<HexKey, LatestByKindAndAuthor<T>>(1) as ConcurrentHashMap<HexKey, LatestByKindAndAuthor<Event>>
            observablesByKindAndAuthor.put(kind, authorObsList)
        }

        val value = authorObsList.get(pubkey)

        return if (value != null) {
            value
        } else {
            val newObject = LatestByKindAndAuthor<T>(kind, pubkey) as LatestByKindAndAuthor<Event>
            val obj = authorObsList.putIfAbsent(pubkey, newObject) ?: newObject
            if (obj == newObject) {
                // initialize
                scope.launch(Dispatchers.IO) {
                    obj.init()
                }
            }
            obj
        } as LatestByKindAndAuthor<T>
    }

    private fun updateObservables(event: Event) {
        observablesByKindAndETag[event.kind]?.let { observablesOfKind ->
            event.forEachTaggedEventId {
                observablesOfKind[it]?.updateIfMatches(event)
            }
        }

        observablesByKindAndAuthor[event.kind]?.get(event.pubKey)?.updateIfMatches(event)
    }

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

    fun getAddressableNoteIfExists(key: String): AddressableNote? = addressables.get(key)

    fun getAddressableNoteIfExists(address: Address): AddressableNote? = getAddressableNoteIfExists(address.toValue())

    fun getNoteIfExists(key: String): Note? = addressables.get(key) ?: notes.get(key)

    fun getNoteIfExists(key: ETag): Note? = notes.get(key.eventId)

    fun getChannelIfExists(key: String): Channel? = channels.get(key)

    fun getNoteIfExists(event: Event): Note? =
        if (event is AddressableEvent) {
            getAddressableNoteIfExists(event.addressTag())
        } else {
            getNoteIfExists(event.id)
        }

    fun getOrCreateNote(event: Event): Note =
        if (event is AddressableEvent) {
            getOrCreateAddressableNote(event.address())
        } else {
            getOrCreateNote(event.id)
        }

    fun checkGetOrCreateNote(etag: ETag): Note? {
        checkNotInMainThread()

        if (isValidHex(etag.eventId)) {
            return getOrCreateNote(etag)
        }
        return null
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
                val newNote = checkGetOrCreateAddressableNote(noteEvent.aTag().toTag())

                if (newNote != null && newNote.event == null) {
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

        val address = Address.parse(key)
        if (address != null) {
            return channels.getOrCreate(address.toValue()) { LiveActivitiesChannel(address) }
        }
        return null
    }

    private fun isValidHex(key: String): Boolean {
        if (key.isBlank()) return false
        if (key.contains(":")) return false

        return Hex.isHex(key)
    }

    fun checkGetOrCreateAddressableNote(key: String): AddressableNote? =
        try {
            val addr = Address.parse(key)
            if (addr != null) {
                getOrCreateAddressableNote(addr)
            } else {
                null
            }
        } catch (e: IllegalArgumentException) {
            Log.e("LocalCache", "Invalid Key to create channel: $key", e)
            null
        }

    fun getOrCreateAddressableNoteInternal(key: Address): AddressableNote =
        addressables.getOrCreate(key.toValue()) {
            AddressableNote(key)
        }

    fun getOrCreateAddressableNote(key: Address): AddressableNote {
        val note = getOrCreateAddressableNoteInternal(key)
        // Loads the user outside a Syncronized block to avoid blocking
        if (note.author == null) {
            note.author = checkGetOrCreateUser(key.pubKeyHex)
        }
        return note
    }

    fun getOrCreateNote(key: GenericETag): Note {
        val note = getOrCreateNote(key.eventId)
        // Loads the user outside a Syncronized block to avoid blocking
        val possibleAuthor = key.author
        if (note.author == null && possibleAuthor != null) {
            note.author = checkGetOrCreateUser(possibleAuthor)
        }
        val relayHint = key.relay
        if (!relayHint.isNullOrBlank()) {
            val relay = RelayBriefInfoCache.get(RelayUrlFormatter.normalize(relayHint))
            note.addRelayBrief(relay)
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
                if (relay != null) {
                    oldUser.addRelayBeingUsed(relay, event.createdAt)
                    if (!RelayUrlFormatter.isLocalHost(relay.url)) {
                        oldUser.latestMetadataRelay = relay.url
                    }
                }
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

            updateObservables(event)
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

    fun formattedDateTime(timestamp: Long): String =
        Instant
            .ofEpochSecond(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("uuuu MMM d hh:mm a"))

    fun consume(
        event: TextNoteEvent,
        relay: Relay? = null,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: TorrentEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: InteractiveStoryPrologueEvent,
        relay: Relay?,
    ) = consumeBaseReplaceable(event, relay)

    fun consume(
        event: InteractiveStorySceneEvent,
        relay: Relay?,
    ) = consumeBaseReplaceable(event, relay)

    fun consume(
        event: InteractiveStoryReadingStateEvent,
        relay: Relay?,
    ) = consumeBaseReplaceable(event, relay)

    fun consumeRegularEvent(
        event: Event,
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

        val replyTo = computeReplyTo(event)

        if (event is BaseThreadedEvent && antiSpam.isSpam(event, relay)) {
            return
        }

        note.loadEvent(event, author, replyTo)

        // Counts the replies
        replyTo.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    fun consume(
        event: PictureEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: TorrentCommentEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: NIP90ContentDiscoveryResponseEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: NIP90ContentDiscoveryRequestEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: NIP90StatusEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: NIP90UserDiscoveryResponseEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: NIP90UserDiscoveryRequestEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: GitPatchEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: GitIssueEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: GitReplyEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

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
        if (note.event?.id == event.id) return

        if (antiSpam.isSpam(event, relay)) {
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
        if (note.event?.id == event.id) return

        if (antiSpam.isSpam(event, relay)) {
            return
        }

        val replyTo = computeReplyTo(event)

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            refreshObservers(note)
        }
    }

    fun computeReplyTo(event: Event): List<Note> =
        when (event) {
            is PollNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is WikiNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is LongTextNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is GitReplyEvent -> event.tagsWithoutCitations().filter { it != event.repository()?.toTag() }.mapNotNull { checkGetOrCreateNote(it) }
            is TextNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is CommentEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }

            is ChatMessageEvent -> event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            is ChatMessageEncryptedFileHeaderEvent -> event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }

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
                event.boostedEventIds().mapNotNull { checkGetOrCreateNote(it) } +
                    event.boostedAddresses().map { getOrCreateAddressableNote(it) }
            is GenericRepostEvent ->
                event.boostedEventIds().mapNotNull { checkGetOrCreateNote(it) } +
                    event.boostedAddresses().map { getOrCreateAddressableNote(it) }
            is CommunityPostApprovalEvent ->
                event.approvedEvents().mapNotNull { checkGetOrCreateNote(it) } +
                    event.approvedAddresses().map { getOrCreateAddressableNote(it) }
            is ReactionEvent ->
                event.originalPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is ReportEvent ->
                event.reportedPost().mapNotNull { checkGetOrCreateNote(it.eventId) } +
                    event.reportedAddresses().map { getOrCreateAddressableNote(it.address) }
            is ChannelMessageEvent ->
                event
                    .tagsWithoutCitations()
                    .filter { it != event.channelId() }
                    .mapNotNull { checkGetOrCreateNote(it) }
            is LiveActivitiesChatMessageEvent ->
                event
                    .tagsWithoutCitations()
                    .filter { it != event.activity()?.toTag() }
                    .mapNotNull { checkGetOrCreateNote(it) }
            is TorrentCommentEvent ->
                event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }

            is DraftEvent -> {
                event.mapTaggedEventId { checkGetOrCreateNote(it) } + event.mapTaggedAddress { checkGetOrCreateAddressableNote(it) }
            }

            else -> emptyList<Note>()
        }

    fun consume(
        event: PollNoteEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

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

        if (note.event?.id == event.id) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            val channel = getOrCreateChannel(note.idHex) { LiveActivitiesChannel(note.address) } as? LiveActivitiesChannel

            if (relay != null) {
                channel?.addRelay(relay)
            }

            val creator = event.host()?.let { checkGetOrCreateUser(it.pubKey) } ?: author

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
        event: BlossomServersEvent,
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
        event: ChatMessageRelayListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: PrivateOutboxRelayListEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    private fun consume(
        event: SearchRelayListEvent,
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
        if (note.event?.id == event.id) return

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, emptyList())

            author.flowSet?.statuses?.invalidateData()

            refreshObservers(note)
        }
    }

    fun consume(
        event: RelationshipStatusEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
    }

    fun consume(
        event: OtsEvent,
        relay: Relay?,
    ) {
        val version = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (version.event?.id == event.id) return

        if (version.event == null) {
            version.loadEvent(event, author, emptyList())
            version.flowSet?.ots?.invalidateData()
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
        if (note.event?.id == event.id) return

        val replyTo = computeReplyTo(event)

        if (event.createdAt > (note.createdAt() ?: 0)) {
            note.loadEvent(event, author, replyTo)

            refreshObservers(note)
        }
    }

    fun consume(
        event: BadgeAwardEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

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
        if (note.event?.id == event.id) return

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

    fun consume(
        event: AppSpecificDataEvent,
        relay: Relay?,
    ) {
        consumeBaseReplaceable(event, relay)
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

            event
                .deleteEvents()
                .mapNotNull { getNoteIfExists(it) }
                .forEach { deleteNote ->
                    val deleteNoteEvent = deleteNote.event
                    if (deleteNoteEvent is AddressableEvent) {
                        val addressableNote = getAddressableNoteIfExists(deleteNoteEvent.addressTag())
                        if (addressableNote?.author?.pubkeyHex == event.pubKey && (addressableNote.createdAt() ?: 0) <= event.createdAt) {
                            // Counts the replies
                            deleteNote(addressableNote)

                            addressables.remove(addressableNote.idHex)

                            deletedAtLeastOne = true
                        }
                    }

                    // must be the same author
                    if (deleteNote.author?.pubkeyHex == event.pubKey) {
                        // reverts the add
                        deleteNote(deleteNote)

                        deletedAtLeastOne = true
                    }
                }

            val addressList = event.deleteAddressIds()
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
                    if (noteEvent.pubKey == event.pubKey && noteEvent.createdAt <= event.createdAt) {
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
                ?.tags
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

        (deletedEvent as? TorrentCommentEvent)?.torrentIds()?.let {
            getNoteIfExists(it)?.removeReply(deleteNote)
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

        deleteNote.clearFlow()

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
                it.clearFlow()
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

        val communities = event.communityAddresses()
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

        val mentions = event.reportedAuthor().mapNotNull { checkGetOrCreateUser(it.pubkey) }
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
                it.flowSet?.reports?.invalidateData()
            }
        }

        refreshObservers(note)
    }

    fun consume(
        event: ChannelCreateEvent,
        relay: Relay?,
    ) {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreateChannel(event.id) { PublicChatChannel(it) }
        val author = getOrCreateUser(event.pubKey)

        val note = getOrCreateNote(event.id)
        if (note.event == null) {
            oldChannel.addNote(note, relay)
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }

        if (event.createdAt <= oldChannel.updatedMetadataAt) {
            return // older data, does nothing
        }
        if (oldChannel.creator == null || oldChannel.creator == author) {
            if (oldChannel is PublicChatChannel) {
                oldChannel.updateChannelInfo(author, event)
            }
        }
    }

    fun consume(
        event: ChannelMetadataEvent,
        relay: Relay?,
    ) {
        val channelId = event.channelId()
        // Log.d("MT", "New PublicChatMetadata ${event.channelInfo()}")
        if (channelId.isNullOrBlank()) return

        // new event
        val oldChannel = checkGetOrCreateChannel(channelId) ?: return

        val author = getOrCreateUser(event.pubKey)
        if (event.createdAt > oldChannel.updatedMetadataAt) {
            if (oldChannel is PublicChatChannel) {
                oldChannel.updateChannelInfo(author, event)
            }
        } else {
            // Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()}
            // ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
        }

        val note = getOrCreateNote(event.id)
        if (note.event == null) {
            oldChannel.addNote(note, relay)
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }
    }

    fun consume(
        event: ChannelMessageEvent,
        relay: Relay?,
    ) {
        val channelId = event.channelId()

        if (channelId.isNullOrBlank()) return

        val channel = checkGetOrCreateChannel(channelId) ?: return

        val note = getOrCreateNote(event.id)
        channel.addNote(note, relay)

        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
            return
        }

        val replyTo = computeReplyTo(event)

        note.loadEvent(event, author, replyTo)

        // Log.d("CM", "New Chat Note (${note.author?.toBestDisplayName()} ${note.event?.content}
        // ${formattedDateTime(event.createdAt)}")

        // Counts the replies
        replyTo.forEach { it.addReply(note) }

        refreshObservers(note)
    }

    fun consume(
        event: CommentEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: LiveActivitiesChatMessageEvent,
        relay: Relay?,
    ) {
        val activityAddress = event.activityAddress() ?: return

        val channel = getOrCreateChannel(activityAddress.toValue()) { LiveActivitiesChannel(activityAddress) }

        val note = getOrCreateNote(event.id)
        channel.addNote(note, relay)

        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return

        if (antiSpam.isSpam(event, relay)) {
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

    fun consume(
        event: LnZapEvent,
        relay: Relay?,
    ) {
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
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: FileHeaderEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: ProfileGalleryEntryEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: FileStorageHeaderEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

    fun consume(
        event: FhirResourceEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

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
            checkGetOrCreateNote(it.eventId)?.let { editedNote ->
                modificationCache.remove(editedNote.idHex)
                // must update list of Notes to quickly update the user.
                editedNote.flowSet?.edits?.invalidateData()
            }
        }

        refreshObservers(note)
    }

    fun consume(
        event: HighlightEvent,
        relay: Relay?,
    ) = consumeRegularEvent(event, relay)

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
            val cachePath = Amethyst.instance.nip95cache
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

        val recipientsHex = event.groupMembers()
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

    private fun consume(
        event: ChatMessageEncryptedFileHeaderEvent,
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

        val recipientsHex = event.groupMembers()
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
        event: SealedRumorEvent,
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

        note.loadEvent(event.copyNoContent(), author, emptyList())

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

        note.loadEvent(event.copyNoContent(), author, emptyList())

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

        responseCallback(event)
    }

    fun findUsersStartingWith(
        username: String,
        forAccount: Account?,
    ): List<User> {
        if (username.isBlank()) return emptyList()

        checkNotInMainThread()

        val key = decodePublicKeyAsHexOrNull(username)

        if (key != null) {
            val user = getUserIfExists(key)
            if (user != null) {
                return listOfNotNull(user)
            }
        }

        val finds =
            users.filter { _, user: User ->
                (
                    (user.anyNameStartsWith(username)) ||
                        user.pubkeyHex.startsWith(username, true) ||
                        user.pubkeyNpub().startsWith(username, true)
                ) &&
                    (forAccount == null || (!forAccount.isHidden(user) && !user.containsAny(forAccount.flowHiddenUsers.value.hiddenWordsCase)))
            }

        return finds.sortedWith(
            compareBy(
                { forAccount?.isFollowing(it) == false },
                { !it.toBestDisplayName().startsWith(username, ignoreCase = true) },
                { it.toBestDisplayName().lowercase() },
                { it.pubkeyHex },
            ),
        )
    }

    /**
     * Will return true if supplied note is one of events to be excluded from
     * search results.
     */
    private fun excludeNoteEventFromSearchResults(note: Note): Boolean =
        (
            note.event is GenericRepostEvent ||
                note.event is RepostEvent ||
                note.event is CommunityPostApprovalEvent ||
                note.event is ReactionEvent ||
                note.event is LnZapEvent ||
                note.event is LnZapRequestEvent ||
                note.event is FileHeaderEvent
        )

    fun findNotesStartingWith(
        text: String,
        forAccount: Account,
    ): List<Note> {
        checkNotInMainThread()

        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)

        if (key != null) {
            val note = getNoteIfExists(key)
            if ((note != null) && !excludeNoteEventFromSearchResults(note)) {
                return listOfNotNull(note)
            }
        }

        return notes.filter { _, note ->
            if (excludeNoteEventFromSearchResults(note)) {
                return@filter false
            }

            if (note.event?.tags?.tagValueContains(text, true) == true ||
                note.idHex.startsWith(text, true)
            ) {
                if (!note.isHiddenFor(forAccount.flowHiddenUsers.value)) {
                    return@filter true
                } else {
                    return@filter false
                }
            }

            if (note.event?.isContentEncoded() == false) {
                if (!note.isHiddenFor(forAccount.flowHiddenUsers.value)) {
                    return@filter note.event?.content?.contains(text, true) ?: false
                } else {
                    return@filter false
                }
            }

            return@filter false
        } +
            addressables.filter { _, addressable ->
                if (excludeNoteEventFromSearchResults(addressable)) {
                    return@filter false
                }

                if (addressable.event?.tags?.tagValueContains(text, true) == true ||
                    addressable.idHex.startsWith(text, true)
                ) {
                    if (!addressable.isHiddenFor(forAccount.flowHiddenUsers.value)) {
                        return@filter true
                    } else {
                        return@filter false
                    }
                }

                if (addressable.event?.isContentEncoded() == false) {
                    if (!addressable.isHiddenFor(forAccount.flowHiddenUsers.value)) {
                        return@filter addressable.event?.content?.contains(text, true) ?: false
                    } else {
                        return@filter false
                    }
                }

                return@filter false
            }
    }

    fun findChannelsStartingWith(text: String): List<Channel> {
        checkNotInMainThread()

        if (text.isBlank()) return emptyList()

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

        return addressables
            .filter { _, it ->
                val noteEvent = it.event
                (
                    noteEvent is StatusEvent &&
                        noteEvent.pubKey == user.pubkeyHex &&
                        !noteEvent.isExpired() &&
                        noteEvent.content.isNotBlank()
                )
            }.sortedWith(compareBy({ it.event?.expiration() ?: it.event?.createdAt }, { it.idHex }))
            .reversed()
            .toImmutableList()
    }

    suspend fun findEarliestOtsForNote(
        note: Note,
        resolverBuilder: () -> OtsResolver,
    ): Long? {
        checkNotInMainThread()

        var minTime: Long? = null
        val time = TimeUtils.now()

        notes.forEach { _, item ->
            val noteEvent = item.event
            if ((noteEvent is OtsEvent && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time))) {
                (Amethyst.instance.otsVerifCache.cacheVerify(noteEvent, resolverBuilder) as? VerificationState.Verified)?.verifiedTime?.let { stampedTime ->
                    if (minTime == null || stampedTime < (minTime ?: Long.MAX_VALUE)) {
                        minTime = stampedTime
                    }
                }
            }
        }

        return minTime
    }

    val modificationCache = LruCache<HexKey, List<Note>>(20)

    fun cachedModificationEventsForNote(note: Note): List<Note>? = modificationCache[note.idHex]

    suspend fun findLatestModificationForNote(note: Note): List<Note> {
        checkNotInMainThread()

        val originalAuthor = note.author?.pubkeyHex ?: return emptyList()

        modificationCache[note.idHex]?.let {
            return it
        }

        val time = TimeUtils.now()

        val newNotes =
            notes
                .filter { _, item ->
                    val noteEvent = item.event

                    noteEvent is TextNoteModificationEvent && note.author == item.author && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time)
                }.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))

        modificationCache.put(note.idHex, newNotes)

        return newNotes
    }

    fun cleanObservers() {
        notes.forEach { _, it -> it.clearFlow() }
        addressables.forEach { _, it -> it.clearFlow() }
        users.forEach { _, it -> it.clearFlow() }

        observablesByKindAndETag.forEach { _, list ->
            list.forEach { key, value ->
                if (value.canDelete()) {
                    list.remove(key)
                }
            }
        }

        observablesByKindAndAuthor.forEach { _, list ->
            list.forEach { key, value ->
                if (value.canDelete()) {
                    list.remove(key)
                }
            }
        }
    }

    fun pruneHiddenMessages(account: Account) {
        channels.forEach { _, channel ->
            val toBeRemoved = channel.pruneHiddenMessages(account)

            val childrenToBeRemoved = mutableListOf<Note>()

            toBeRemoved.forEach {
                removeFromCache(it)

                childrenToBeRemoved.addAll(it.removeAllChildNotes())
            }

            removeFromCache(childrenToBeRemoved)

            if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
                println(
                    "PRUNE: ${toBeRemoved.size} hidden messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
                )
            }
        }
    }

    fun pruneOldMessages() {
        checkNotInMainThread()

        channels.forEach { _, channel ->
            val toBeRemoved = channel.pruneOldMessages()

            val childrenToBeRemoved = mutableListOf<Note>()

            toBeRemoved.forEach {
                removeFromCache(it)

                childrenToBeRemoved.addAll(it.removeAllChildNotes())
            }

            removeFromCache(childrenToBeRemoved)

            if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
                println(
                    "PRUNE: ${toBeRemoved.size} old messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
                )
            }
        }

        users.forEach { _, user ->
            user.privateChatrooms.values.map {
                val toBeRemoved = it.pruneMessagesToTheLatestOnly()

                val childrenToBeRemoved = mutableListOf<Note>()

                toBeRemoved.forEach {
                    // TODO: NEED TO TEST IF WRAPS COME BACK WHEN NEEDED BEFORE ACTIVATING
                    // childrenToBeRemoved.addAll(removeIfWrap(it))
                    removeFromCache(it)

                    childrenToBeRemoved.addAll(it.removeAllChildNotes())
                }

                removeFromCache(childrenToBeRemoved)

                if (toBeRemoved.size > 1) {
                    println(
                        "PRUNE: ${toBeRemoved.size} private messages from ${user.toBestDisplayName()} to ${it.authors.joinToString(", ") { it.toBestDisplayName() }} removed. ${it.roomMessages.size} kept",
                    )
                }
            }
        }
    }

    fun removeIfWrap(note: Note): List<Note> {
        val noteEvent = note.event

        val children =
            if (noteEvent is WrappedEvent) {
                noteEvent.host?.id?.let {
                    getNoteIfExists(it)?.let {
                        removeFromCache(it)
                        it.removeAllChildNotes()
                    }
                }
            } else {
                null
            }

        return children ?: emptyList()
    }

    fun prunePastVersionsOfReplaceables() {
        val toBeRemoved =
            notes.filter { _, note ->
                val noteEvent = note.event
                if (noteEvent is AddressableEvent) {
                    noteEvent.createdAt <
                        (addressables.get(noteEvent.aTag().toTag())?.event?.createdAt ?: 0)
                } else {
                    false
                }
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            val newerVersion = (it.event as? AddressableEvent)?.aTag()?.toTag()?.let { tag -> addressables.get(tag) }
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
                    note.replyTo?.any { it.flowSet?.isInUse() == true } != true &&
                    note.flowSet?.isInUse() != true &&
                    // don't delete if observing.
                    note.author?.pubkeyHex !in
                    accounts &&
                    // don't delete if it is the logged in account
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
                val author = getUserIfExists(it.pubkey)
                author?.removeReport(note)
                author?.clearEOSE()
            }
        }

        note.clearFlow()

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

    fun pruneHiddenEvents(account: Account) {
        checkNotInMainThread()

        val childrenToBeRemoved = mutableListOf<Note>()

        val toBeRemoved =
            account.flowHiddenUsers.value.hiddenUsers
                .map { userHex ->
                    (notes.filter { _, it -> it.event?.pubKey == userHex } + addressables.filter { _, it -> it.event?.pubKey == userHex }).toSet()
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
        users.forEach { _, user ->
            if (
                user.pubkeyHex !in loggedIn &&
                (user.flowSet == null || user.flowSet?.isInUse() == false) &&
                user.latestContactList != null
            ) {
                user.latestContactList = null
                removingContactList++
            }
        }

        println("PRUNE: $removingContactList contact lists")
    }

    override fun markAsSeen(
        eventId: String,
        relay: Relay,
    ) {
        val note = getNoteIfExists(eventId)

        note?.event?.let { noteEvent ->
            if (noteEvent is AddressableEvent) {
                getAddressableNoteIfExists(noteEvent.aTag().toTag())?.addRelay(relay)
            }
        }

        note?.addRelay(relay)
    }

    // Observers line up here.
    val live: LocalCacheFlow = LocalCacheFlow()

    private fun refreshObservers(newNote: Note) {
        val event = newNote.event as Event
        updateObservables(event)
        onNewEvents.forEach { it(newNote) }
        live.invalidateData(newNote)
    }

    override fun verifyAndConsume(
        event: Event,
        relay: Relay?,
    ) {
        if (justVerify(event)) {
            justConsume(event, relay)
        }
    }

    override fun justVerify(event: Event): Boolean {
        checkNotInMainThread()

        return if (!event.verify()) {
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

    override fun consume(
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
                val recipientsHex = draft.groupMembers()
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
            is ChatMessageEncryptedFileHeaderEvent -> {
                val recipientsHex = draft.groupMembers()
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
                draft.channelId()?.let { channelId ->
                    checkGetOrCreateChannel(channelId)?.addNote(note, null)
                }
            }
            is LiveActivitiesChatMessageEvent -> {
                draft.activityAddress()?.let { channelId ->
                    checkGetOrCreateChannel(channelId.toValue())?.addNote(note, null)
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
            is ChatMessageEncryptedFileHeaderEvent -> {
                val recipientsHex = draft.groupMembers()
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
                draft.channelId()?.let { channelId ->
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
        if (deletionIndex.hasBeenDeleted(event)) {
            // update relay with deletion event from another.
            if (relay != null) {
                deletionIndex.hasBeenDeletedBy(event)?.let {
                    Log.d("LocalCache", "Updating ${relay.url} with a Deletion Event ${it.toJson()} because of ${event.toJson()}")
                    relay.send(it)
                }
            }
            return
        }

        if (event is AddressableEvent && relay != null) {
            // updates relay with a new event.
            getAddressableNoteIfExists(event.addressTag())?.let { note ->
                note.event?.let { existingEvent ->
                    if (existingEvent.createdAt > event.createdAt && !note.hasRelay(relay)) {
                        Log.d("LocalCache", "Updating ${relay.url} with a new version of ${event.toJson()} to ${existingEvent.toJson()}")
                        relay.send(existingEvent)
                    }
                }
            }
        }

        checkNotInMainThread()

        try {
            when (event) {
                is AdvertisedRelayListEvent -> consume(event, relay)
                is AppDefinitionEvent -> consume(event, relay)
                is AppRecommendationEvent -> consume(event, relay)
                is AppSpecificDataEvent -> consume(event, relay)
                is AudioHeaderEvent -> consume(event, relay)
                is AudioTrackEvent -> consume(event, relay)
                is BadgeAwardEvent -> consume(event, relay)
                is BadgeDefinitionEvent -> consume(event, relay)
                is BadgeProfilesEvent -> consume(event)
                is BlossomServersEvent -> consume(event, relay)
                is BookmarkListEvent -> consume(event)
                is CalendarEvent -> consume(event, relay)
                is CalendarDateSlotEvent -> consume(event, relay)
                is CalendarTimeSlotEvent -> consume(event, relay)
                is CalendarRSVPEvent -> consume(event, relay)
                is ChannelCreateEvent -> consume(event, relay)
                is ChannelListEvent -> consume(event, relay)
                is ChannelHideMessageEvent -> consume(event)
                is ChannelMessageEvent -> consume(event, relay)
                is ChannelMetadataEvent -> consume(event, relay)
                is ChannelMuteUserEvent -> consume(event)
                is ChatMessageEncryptedFileHeaderEvent -> consume(event, relay)
                is ChatMessageEvent -> consume(event, relay)
                is ChatMessageRelayListEvent -> consume(event, relay)
                is ClassifiedsEvent -> consume(event, relay)
                is CommentEvent -> consume(event, relay)
                is CommunityDefinitionEvent -> consume(event, relay)
                is CommunityListEvent -> consume(event, relay)
                is CommunityPostApprovalEvent -> {
                    event.containedPost()?.let { verifyAndConsume(it, relay) }
                    consume(event)
                }
                is ContactListEvent -> consume(event)
                is DeletionEvent -> consume(event)
                is DraftEvent -> consume(event, relay)
                is EmojiPackEvent -> consume(event, relay)
                is EmojiPackSelectionEvent -> consume(event, relay)
                is GenericRepostEvent -> {
                    event.containedPost()?.let { verifyAndConsume(it, relay) }
                    consume(event)
                }
                is FhirResourceEvent -> consume(event, relay)
                is FileHeaderEvent -> consume(event, relay)
                is ProfileGalleryEntryEvent -> consume(event, relay)
                is FileServersEvent -> consume(event, relay)
                is FileStorageEvent -> consume(event, relay)
                is FileStorageHeaderEvent -> consume(event, relay)
                is GiftWrapEvent -> consume(event, relay)
                is GitIssueEvent -> consume(event, relay)
                is GitReplyEvent -> consume(event, relay)
                is GitPatchEvent -> consume(event, relay)
                is GitRepositoryEvent -> consume(event, relay)
                is HighlightEvent -> consume(event, relay)
                is InteractiveStoryPrologueEvent -> consume(event, relay)
                is InteractiveStorySceneEvent -> consume(event, relay)
                is InteractiveStoryReadingStateEvent -> consume(event, relay)
                is LiveActivitiesEvent -> consume(event, relay)
                is LiveActivitiesChatMessageEvent -> consume(event, relay)
                is LnZapEvent -> {
                    event.zapRequest?.let {
                        // must have a valid request
                        verifyAndConsume(it, relay)
                        consume(event, relay)
                    }
                }
                is LnZapRequestEvent -> consume(event)
                is NIP90StatusEvent -> consume(event, relay)
                is NIP90ContentDiscoveryResponseEvent -> consume(event, relay)
                is NIP90ContentDiscoveryRequestEvent -> consume(event, relay)
                is NIP90UserDiscoveryResponseEvent -> consume(event, relay)
                is NIP90UserDiscoveryRequestEvent -> consume(event, relay)
                is LnZapPaymentRequestEvent -> consume(event)
                is LnZapPaymentResponseEvent -> consume(event)
                is LongTextNoteEvent -> consume(event, relay)
                is MetadataEvent -> consume(event, relay)
                is MuteListEvent -> consume(event, relay)
                is NNSEvent -> comsume(event, relay)
                is OtsEvent -> consume(event, relay)
                is PictureEvent -> consume(event, relay)
                is PrivateDmEvent -> consume(event, relay)
                is PrivateOutboxRelayListEvent -> consume(event, relay)
                is PinListEvent -> consume(event, relay)
                is PeopleListEvent -> consume(event, relay)
                is PollNoteEvent -> consume(event, relay)
                is ReactionEvent -> consume(event)
                is RelationshipStatusEvent -> consume(event, relay)
                is RelaySetEvent -> consume(event, relay)
                is ReportEvent -> consume(event, relay)
                is RepostEvent -> {
                    event.containedPost()?.let { verifyAndConsume(it, relay) }
                    consume(event)
                }
                is SealedRumorEvent -> consume(event, relay)
                is SearchRelayListEvent -> consume(event, relay)
                is StatusEvent -> consume(event, relay)
                is TextNoteEvent -> consume(event, relay)
                is TextNoteModificationEvent -> consume(event, relay)
                is TorrentEvent -> consume(event, relay)
                is TorrentCommentEvent -> consume(event, relay)
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

    fun hasConsumed(notificationEvent: Event): Boolean =
        if (notificationEvent is AddressableEvent) {
            val note = addressables.get(notificationEvent.addressTag())
            val noteEvent = note?.event
            noteEvent != null && notificationEvent.createdAt <= noteEvent.createdAt
        } else {
            val note = notes.get(notificationEvent.id)
            note?.event != null
        }

    fun copyRelaysFromTo(
        from: Note,
        to: Event,
    ) {
        val toNote = getOrCreateNote(to)
        from.relays.forEach {
            toNote.addRelayBrief(it)
        }
    }
}

@Stable
class LocalCacheFlow {
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
