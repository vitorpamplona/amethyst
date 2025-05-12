/**
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
package com.vitorpamplona.amethyst.model

import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.observables.LatestByKindAndAuthor
import com.vitorpamplona.amethyst.model.observables.LatestByKindWithETag
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.note.dateFormatter
import com.vitorpamplona.ammolite.relays.BundledInsert
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.quartz.blossom.BlossomServersEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.relationshipStatus.RelationshipStatusEvent
import com.vitorpamplona.quartz.experimental.tipping.TipEvent
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
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
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
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
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
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
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
import com.vitorpamplona.quartz.utils.LargeCache
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
    fun markAsSeen(
        string: String,
        relay: RelayBriefInfoCache.RelayBriefInfo,
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
        if (isValidHex(key)) {
            return getOrCreateUser(key)
        }
        return null
    }

    fun getOrCreateUser(key: HexKey): User {
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

    fun getChannelIfExists(key: RoomId): Channel? = channels.get(key.toKey())

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

    fun checkGetOrCreateChannel(key: RoomId): Channel? =
        channels.getOrCreate(key.toKey()) {
            EphemeralChatChannel(key)
        }

    fun checkGetOrCreateChannel(key: String): Channel? {
        checkNotInMainThread()

        if (key.contains("@")) {
            return channels.getOrCreate(key) {
                val idParts = key.split("@")
                EphemeralChatChannel(RoomId(idParts[0], idParts[1]))
            }
        }

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
            note.addRelay(relay)
        }
        return note
    }

    fun consume(
        event: MetadataEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        // new event
        val oldUser = getOrCreateUser(event.pubKey)
        val currentMetadata = oldUser.latestMetadata

        if (currentMetadata == null || event.createdAt > currentMetadata.createdAt) {
            oldUser.latestMetadata = event

            val newUserMetadata = event.contactMetaData()
            if (newUserMetadata != null && (wasVerified || justVerify(event))) {
                oldUser.updateUserInfo(newUserMetadata, event)
                if (relay != null) {
                    oldUser.addRelayBeingUsed(relay, event.createdAt)
                    if (!RelayUrlFormatter.isLocalHost(relay.url)) {
                        oldUser.latestMetadataRelay = relay.url
                    }
                }

                return true
            }
        }

        return false
    }

    fun consume(
        event: ContactListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val user = getOrCreateUser(event.pubKey)

        // avoids processing empty contact lists.
        if (event.createdAt > (user.latestContactList?.createdAt ?: 0) && !event.tags.isEmpty() && (wasVerified || justVerify(event))) {
            user.updateContactList(event)
            // Log.d("CL", "Consumed contact list ${user.toNostrUri()} ${event.relays()?.size}")

            updateObservables(event)

            return true
        }

        return false
    }

    fun consume(
        event: BookmarkListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val user = getOrCreateUser(event.pubKey)
        if (user.latestBookmarkList == null || event.createdAt > user.latestBookmarkList!!.createdAt) {
            if (event.dTag() == "bookmark") {
                if (wasVerified || justVerify(event)) {
                    user.updateBookmark(event)
                    return true
                }
            }
        }

        return false
    }

    fun formattedDateTime(timestamp: Long): String =
        Instant
            .ofEpochSecond(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("uuuu MMM d hh:mm a"))

    fun consume(
        event: TextNoteEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo? = null,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: TorrentEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: InteractiveStoryPrologueEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: InteractiveStorySceneEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: InteractiveStoryReadingStateEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consumeRegularEvent(
        event: Event,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (event is BaseThreadedEvent && antiSpam.isSpam(event, relay)) {
            return false
        }

        if (wasVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            note.loadEvent(event, author, replyTo)

            // Counts the replies
            replyTo.forEach { it.addReply(note) }

            refreshObservers(note)

            return true
        } else {
            return false
        }
    }

    fun consume(
        event: PictureEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: TorrentCommentEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90ContentDiscoveryResponseEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90ContentDiscoveryRequestEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90StatusEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90UserDiscoveryResponseEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90UserDiscoveryRequestEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: GitPatchEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: GitIssueEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: GitReplyEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: LongTextNoteEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (isVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            if (event.createdAt > (note.createdAt() ?: 0)) {
                note.loadEvent(event, author, replyTo)

                refreshObservers(note)

                return true
            }
        }

        return false
    }

    fun consume(
        event: WikiNoteEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (isVerified || justVerify(event)) {
            if (event.createdAt > (note.createdAt() ?: 0)) {
                val replyTo = computeReplyTo(event)

                note.loadEvent(event, author, replyTo)

                refreshObservers(note)

                return true
            }
        }

        return false
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
            is TipEvent ->
                event.tippedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
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
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    private fun consume(
        event: LiveActivitiesEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (note.event?.id == event.id) return false

        if (event.createdAt > (note.createdAt() ?: 0) && (isVerified || justVerify(event))) {
            note.loadEvent(event, author, emptyList())

            val channel = getOrCreateChannel(note.idHex) { LiveActivitiesChannel(note.address) } as? LiveActivitiesChannel

            if (relay != null) {
                channel?.addRelay(relay)
            }

            val creator = event.host()?.let { checkGetOrCreateUser(it.pubKey) } ?: author

            channel?.updateChannelInfo(creator, event, event.createdAt)

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: MuteListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: CommunityListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: GitRepositoryEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: ChannelListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: BlossomServersEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: FileServersEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: PeopleListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: EphemeralChatListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: FollowListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: AdvertisedRelayListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: ChatMessageRelayListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: PrivateOutboxRelayListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: SearchRelayListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CommunityDefinitionEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: EmojiPackSelectionEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: EmojiPackEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: ClassifiedsEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: PinListEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: RelaySetEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: AudioTrackEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: VideoVerticalEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: VideoHorizontalEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: StatusEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        // Already processed this event.
        if (note.event?.id == event.id) return false

        if (event.createdAt > (note.createdAt() ?: 0) && (isVerified || justVerify(event))) {
            note.loadEvent(event, author, emptyList())

            author.flowSet?.statuses?.invalidateData()

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: RelationshipStatusEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: OtsEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (version.event?.id == event.id) return false

        if (wasVerified || justVerify(event)) {
            if (version.event == null) {
                version.loadEvent(event, author, emptyList())
                version.flowSet?.ots?.invalidateData()
            }

            refreshObservers(version)
            return true
        }

        return false
    }

    fun consume(
        event: BadgeDefinitionEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: BadgeProfilesEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: BadgeAwardEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    private fun consume(
        event: NNSEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: AppDefinitionEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarDateSlotEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarTimeSlotEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarRSVPEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consumeBaseReplaceable(
        event: BaseAddressableEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val replaceableNote = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(replaceableNote)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            replaceableNote.addRelay(relay)
        }

        // Already processed this event.
        if (replaceableNote.event?.id == event.id) return isVerified

        if (event.createdAt > (replaceableNote.createdAt() ?: 0) && (isVerified || justVerify(event))) {
            replaceableNote.loadEvent(event, author, computeReplyTo(event))

            refreshObservers(replaceableNote)

            return true
        } else {
            return false
        }
    }

    fun consume(
        event: AppRecommendationEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: AppSpecificDataEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: PrivateDmEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val recipient = event.verifiedRecipientPubKey()?.let { getOrCreateUser(it) }

            // Log.d("PM", "${author.toBestDisplayName()} to ${recipient?.toBestDisplayName()}")

            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            if (recipient != null) {
                author.addMessage(recipient, note)
                recipient.addMessage(author, note)
            }

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: DeletionEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        if (deletionIndex.add(event, wasVerified)) {
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

            return true
        }

        return false
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

    fun consume(
        event: RepostEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                justConsumeInner(it, relay, false)
            }

            refreshObservers(note)

            return true
        }
        return false
    }

    fun consume(
        event: GenericRepostEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                justConsumeInner(it, relay, false)
            }

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: CommunityPostApprovalEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)

            val communities = event.communityAddresses()
            val eventsApproved = computeReplyTo(event)

            val repliesTo = communities.map { getOrCreateAddressableNote(it) }

            note.loadEvent(event, author, eventsApproved)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                justConsumeInner(it, relay, false)
            }

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ReactionEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return true

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addReaction(note) }

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ReportEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
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

            return true
        }

        return false
    }

    fun consume(
        event: ChannelCreateEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreateChannel(event.id) { PublicChatChannel(it) }
        val author = getOrCreateUser(event.pubKey)
        val note = getOrCreateNote(event.id)

        val isVerified =
            if (note.event == null && (wasVerified || justVerify(event))) {
                oldChannel.addNote(note, relay)
                note.loadEvent(event, author, emptyList())

                refreshObservers(note)
                true
            } else {
                wasVerified
            }

        if (event.createdAt <= oldChannel.updatedMetadataAt) {
            return false // older data, does nothing
        }

        if (oldChannel.creator == null || oldChannel.creator == author) {
            if (oldChannel is PublicChatChannel && (isVerified || justVerify(event))) {
                oldChannel.updateChannelInfo(author, event)
            }
        }

        return isVerified
    }

    fun consume(
        event: ChannelMetadataEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val channelId = event.channelId()
        if (channelId.isNullOrBlank()) return false

        // new event
        val oldChannel = checkGetOrCreateChannel(channelId) ?: return false

        val author = getOrCreateUser(event.pubKey)
        val isVerified =
            if (event.createdAt > oldChannel.updatedMetadataAt) {
                if (oldChannel is PublicChatChannel && (wasVerified || justVerify(event))) {
                    oldChannel.updateChannelInfo(author, event)
                    true
                } else {
                    wasVerified
                }
            } else {
                wasVerified
            }

        val note = getOrCreateNote(event.id)
        if (note.event == null && (isVerified || justVerify(event))) {
            oldChannel.addNote(note, relay)
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)
        }

        return isVerified
    }

    fun consume(
        event: ChannelMessageEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val channelId = event.channelId()

        if (channelId.isNullOrBlank()) return false

        val channel = checkGetOrCreateChannel(channelId) ?: return false

        val note = getOrCreateNote(event.id)
        channel.addNote(note, relay)

        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (wasVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            note.loadEvent(event, author, replyTo)

            // Log.d("CM", "New Chat Note (${note.author?.toBestDisplayName()} ${note.event?.content}
            // ${formattedDateTime(event.createdAt)}")

            // Counts the replies
            replyTo.forEach { it.addReply(note) }

            refreshObservers(note)
        }

        return true
    }

    fun consume(
        event: EphemeralChatEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val relayUrl = event.relay().ifBlank { return false }

        val channelId = RoomId(event.room(), relayUrl)

        val channel = checkGetOrCreateChannel(channelId) ?: return false

        val note = getOrCreateNote(event.id)
        channel.addNote(note, relay)

        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: CommentEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: LiveActivitiesChatMessageEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val activityAddress = event.activityAddress() ?: return false

        val channel = getOrCreateChannel(activityAddress.toValue()) { LiveActivitiesChannel(activityAddress) }

        val note = getOrCreateNote(event.id)
        channel.addNote(note, relay)

        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (wasVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            note.loadEvent(event, author, replyTo)

            // Counts the replies
            replyTo.forEach { it.addReply(note) }

            refreshObservers(note)

            return true
        }

        return false
    }

    @Suppress("UNUSED_PARAMETER")
    fun consume(
        event: ChannelHideMessageEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun consume(
        event: ChannelMuteUserEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean = false

    fun consume(
        event: LnZapEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val existingZapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }

            if (existingZapRequest == null || existingZapRequest.event == null) {
                // tries to add it
                event.zapRequest?.let {
                    justConsumeInner(it, relay, false)
                }
            }

            val zapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }

            if (zapRequest == null || zapRequest.event !is LnZapRequestEvent) {
                Log.e("ZP", "Zap Request not found. Unable to process Zap {${event.toJson()}}")
                return false
            }

            val author = getOrCreateUser(event.pubKey)
            val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addZap(zapRequest, note) }
            mentions.forEach { it.addZap(zapRequest, note) }

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: LnZapRequestEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addZap(note, null) }
            mentions.forEach { it.addZap(note, null) }

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: AudioHeaderEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FileHeaderEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: ProfileGalleryEntryEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FileStorageHeaderEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FhirResourceEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: TextNoteModificationEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            event.editedNote()?.let {
                checkGetOrCreateNote(it.eventId)?.let { editedNote ->
                    modificationCache.remove(editedNote.idHex)
                    // must update list of Notes to quickly update the user.
                    editedNote.flowSet?.edits?.invalidateData()
                }
            }

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: HighlightEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FileStorageEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        var isVerified =
            try {
                val cachePath = Amethyst.instance.nip95cache
                cachePath.mkdirs()
                val file = File(cachePath, event.id)
                if (!file.exists() && (wasVerified || justVerify(event))) {
                    val stream = FileOutputStream(file)
                    stream.write(event.decode())
                    stream.close()
                    Log.i(
                        "FileStorageEvent",
                        "NIP95 File received from ${relay?.url} and saved to disk as $file",
                    )
                    true
                } else {
                    wasVerified
                }
            } catch (e: IOException) {
                Log.e("FileStorageEvent", "FileStorageEvent save to disk error: " + event.id, e)
                wasVerified
            }

        // Already processed this event.
        if (note.event != null) return false

        if (isVerified || justVerify(event)) {
            // this is an invalid event. But we don't need to keep the data in memory.
            val eventNoData =
                FileStorageEvent(event.id, event.pubKey, event.createdAt, event.tags, "", event.sig)

            note.loadEvent(eventNoData, author, emptyList())

            refreshObservers(note)

            return true
        }

        return false
    }

    private fun consume(
        event: ChatMessageEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
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

            return true
        }

        return false
    }

    private fun consume(
        event: ChatMessageEncryptedFileHeaderEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
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

            return true
        }

        return false
    }

    fun consume(
        event: SealedRumorEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())
            refreshObservers(note)
            return true
        }

        return false
    }

    fun consume(
        event: GiftWrapEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())
            refreshObservers(note)
            return true
        }

        return false
    }

    fun consume(
        event: LnZapPaymentRequestEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        // Does nothing without a response callback.
        return true
    }

    fun consume(
        event: LnZapPaymentRequestEvent,
        zappedNote: Note?,
        wasVerified: Boolean,
        onResponse: (LnZapPaymentResponseEvent) -> Unit,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            zappedNote?.addZapPayment(note, null)

            awaitingPaymentRequests.put(event.id, Pair(zappedNote, onResponse))

            refreshObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: LnZapPaymentResponseEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        val requestId = event.requestId()
        val pair = awaitingPaymentRequests[requestId] ?: return false

        val (zappedNote, responseCallback) = pair

        val requestNote = requestId?.let { checkGetOrCreateNote(requestId) }

        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            requestNote?.let { request -> zappedNote?.addZapPayment(request, note) }

            responseCallback(event)

            return true
        }

        return false
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
                note.event is FileHeaderEvent ||
                note.event is TipEvent
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
                        note.event is TipEvent ||
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
            masterNote.removeTip(note)
            masterNote.removeReport(note)
        }

        val noteEvent = note.event

        if (noteEvent is TipEvent) {
            noteEvent.tippedAuthor().forEach {
                val author = getUserIfExists(it)
                author?.removeTip(note)
            }
        }
        if (noteEvent is LnZapEvent) {
            noteEvent.zappedAuthor().forEach {
                val author = getUserIfExists(it)
                author?.removeZap(note)
            }
        }
        if (noteEvent is LnZapRequestEvent) {
            noteEvent.zappedAuthor().mapNotNull {
                val author = getUserIfExists(it)
                author?.removeZap(note)
            }
        }
        if (noteEvent is ReportEvent) {
            noteEvent.reportedAuthor().mapNotNull {
                val author = getUserIfExists(it.pubkey)
                author?.removeReport(note)
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
        relay: RelayBriefInfoCache.RelayBriefInfo,
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

    fun justVerify(event: Event): Boolean {
        checkNotInMainThread()

        return if (!event.verify()) {
            try {
                event.checkSignature()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("Event Verification Failed", "Kind: ${event.kind} from ${dateFormatter(event.createdAt, "", "")} with message ${e.message}: ${event.toJson()}")
            }
            false
        } else {
            true
        }
    }

    fun consume(
        event: DraftEvent,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean {
        if (!event.isDeleted()) {
            if (consumeBaseReplaceable(event, relay, wasVerified)) {
                return true
            }
        }

        return false
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
            is EphemeralChatEvent -> {
                checkGetOrCreateChannel(draft.roomId().toKey())?.addNote(note, null)
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
                    checkGetOrCreateChannel(channelId)?.removeNote(draftWrap)
                }
            }
            is EphemeralChatEvent -> {
                checkGetOrCreateChannel(draft.roomId())?.removeNote(draftWrap)
            }
            is TextNoteEvent -> {
                val replyTo = computeReplyTo(draft)
                replyTo.forEach { it.removeReply(draftWrap) }
            }
        }
    }

    fun justConsumeMyOwnEvent(event: Event) = justConsumeInner(event, null, true)

    fun justConsume(
        event: Event,
        relay: Relay?,
        wasVerified: Boolean,
    ): Boolean {
        if (deletionIndex.hasBeenDeleted(event)) {
            // update relay with deletion event from another.
            if (relay != null) {
                deletionIndex.hasBeenDeletedBy(event)?.let {
                    Log.d("LocalCache", "Updating ${relay.url} with a Deletion Event ${it.toJson()} because of ${event.toJson()}")
                    relay.send(it)
                }
            }
            return false
        }

        if (event is AddressableEvent && relay != null) {
            // updates relay with a new event.
            getAddressableNoteIfExists(event.addressTag())?.let { note ->
                note.event?.let { existingEvent ->
                    if (existingEvent.createdAt > event.createdAt && !note.hasRelay(relay.brief)) {
                        Log.d("LocalCache", "Updating ${relay.url} with a new version of ${event.toJson()} to ${existingEvent.toJson()}")
                        relay.send(existingEvent)
                    }
                }
            }
        }

        return justConsumeInner(event, relay?.brief, wasVerified)
    }

    fun justConsumeInner(
        event: Event,
        relay: RelayBriefInfoCache.RelayBriefInfo?,
        wasVerified: Boolean,
    ): Boolean =
        try {
            when (event) {
                is AdvertisedRelayListEvent -> consume(event, relay, wasVerified)
                is AppDefinitionEvent -> consume(event, relay, wasVerified)
                is AppRecommendationEvent -> consume(event, relay, wasVerified)
                is AppSpecificDataEvent -> consume(event, relay, wasVerified)
                is AudioHeaderEvent -> consume(event, relay, wasVerified)
                is AudioTrackEvent -> consume(event, relay, wasVerified)
                is BadgeAwardEvent -> consume(event, relay, wasVerified)
                is BadgeDefinitionEvent -> consume(event, relay, wasVerified)
                is BadgeProfilesEvent -> consume(event, relay, wasVerified)
                is BlossomServersEvent -> consume(event, relay, wasVerified)
                is BookmarkListEvent -> consume(event, relay, wasVerified)
                is CalendarEvent -> consume(event, relay, wasVerified)
                is CalendarDateSlotEvent -> consume(event, relay, wasVerified)
                is CalendarTimeSlotEvent -> consume(event, relay, wasVerified)
                is CalendarRSVPEvent -> consume(event, relay, wasVerified)
                is ChannelCreateEvent -> consume(event, relay, wasVerified)
                is ChannelListEvent -> consume(event, relay, wasVerified)
                is ChannelHideMessageEvent -> consume(event, relay, wasVerified)
                is ChannelMessageEvent -> consume(event, relay, wasVerified)
                is ChannelMetadataEvent -> consume(event, relay, wasVerified)
                is ChannelMuteUserEvent -> consume(event, relay, wasVerified)
                is ChatMessageEncryptedFileHeaderEvent -> consume(event, relay, wasVerified)
                is ChatMessageEvent -> consume(event, relay, wasVerified)
                is ChatMessageRelayListEvent -> consume(event, relay, wasVerified)
                is ClassifiedsEvent -> consume(event, relay, wasVerified)
                is CommentEvent -> consume(event, relay, wasVerified)
                is CommunityDefinitionEvent -> consume(event, relay, wasVerified)
                is CommunityListEvent -> consume(event, relay, wasVerified)
                is CommunityPostApprovalEvent -> consume(event, relay, wasVerified)
                is ContactListEvent -> consume(event, relay, wasVerified)
                is DeletionEvent -> consume(event, relay, wasVerified)
                is DraftEvent -> consume(event, relay, wasVerified)
                is EmojiPackEvent -> consume(event, relay, wasVerified)
                is EmojiPackSelectionEvent -> consume(event, relay, wasVerified)
                is EphemeralChatEvent -> consume(event, relay, wasVerified)
                is EphemeralChatListEvent -> consume(event, relay, wasVerified)
                is GenericRepostEvent -> consume(event, relay, wasVerified)
                is FhirResourceEvent -> consume(event, relay, wasVerified)
                is FileHeaderEvent -> consume(event, relay, wasVerified)
                is ProfileGalleryEntryEvent -> consume(event, relay, wasVerified)
                is FileServersEvent -> consume(event, relay, wasVerified)
                is FileStorageEvent -> consume(event, relay, wasVerified)
                is FileStorageHeaderEvent -> consume(event, relay, wasVerified)
                is FollowListEvent -> consume(event, relay, wasVerified)
                is GiftWrapEvent -> consume(event, relay, wasVerified)
                is GitIssueEvent -> consume(event, relay, wasVerified)
                is GitReplyEvent -> consume(event, relay, wasVerified)
                is GitPatchEvent -> consume(event, relay, wasVerified)
                is GitRepositoryEvent -> consume(event, relay, wasVerified)
                is HighlightEvent -> consume(event, relay, wasVerified)
                is InteractiveStoryPrologueEvent -> consume(event, relay, wasVerified)
                is InteractiveStorySceneEvent -> consume(event, relay, wasVerified)
                is InteractiveStoryReadingStateEvent -> consume(event, relay, wasVerified)
                is LiveActivitiesEvent -> consume(event, relay, wasVerified)
                is LiveActivitiesChatMessageEvent -> consume(event, relay, wasVerified)
                is LnZapEvent -> consume(event, relay, wasVerified)
                is LnZapRequestEvent -> consume(event, relay, wasVerified)
                is NIP90StatusEvent -> consume(event, relay, wasVerified)
                is NIP90ContentDiscoveryResponseEvent -> consume(event, relay, wasVerified)
                is NIP90ContentDiscoveryRequestEvent -> consume(event, relay, wasVerified)
                is NIP90UserDiscoveryResponseEvent -> consume(event, relay, wasVerified)
                is NIP90UserDiscoveryRequestEvent -> consume(event, relay, wasVerified)
                is LnZapPaymentRequestEvent -> consume(event, relay, wasVerified)
                is LnZapPaymentResponseEvent -> consume(event, relay, wasVerified)
                is LongTextNoteEvent -> consume(event, relay, wasVerified)
                is MetadataEvent -> consume(event, relay, wasVerified)
                is MuteListEvent -> consume(event, relay, wasVerified)
                is NNSEvent -> consume(event, relay, wasVerified)
                is OtsEvent -> consume(event, relay, wasVerified)
                is PictureEvent -> consume(event, relay, wasVerified)
                is PrivateDmEvent -> consume(event, relay, wasVerified)
                is PrivateOutboxRelayListEvent -> consume(event, relay, wasVerified)
                is PinListEvent -> consume(event, relay, wasVerified)
                is PeopleListEvent -> consume(event, relay, wasVerified)
                is PollNoteEvent -> consume(event, relay, wasVerified)
                is ReactionEvent -> consume(event, relay, wasVerified)
                is RelationshipStatusEvent -> consume(event, relay, wasVerified)
                is RelaySetEvent -> consume(event, relay, wasVerified)
                is ReportEvent -> consume(event, relay, wasVerified)
                is RepostEvent -> consume(event, relay, wasVerified)
                is SealedRumorEvent -> consume(event, relay, wasVerified)
                is SearchRelayListEvent -> consume(event, relay, wasVerified)
                is StatusEvent -> consume(event, relay, wasVerified)
                is TextNoteEvent -> consume(event, relay, wasVerified)
                is TextNoteModificationEvent -> consume(event, relay, wasVerified)
                is TorrentEvent -> consume(event, relay, wasVerified)
                is TorrentCommentEvent -> consume(event, relay, wasVerified)
                is VideoHorizontalEvent -> consume(event, relay, wasVerified)
                is VideoVerticalEvent -> consume(event, relay, wasVerified)
                is WikiNoteEvent -> consume(event, relay, wasVerified)
                else -> {
                    Log.w("Event Not Supported", event.toJson())
                    false
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            false
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
            toNote.addRelay(it)
        }
    }

    fun copyRelaysFromTo(
        from: Note,
        to: HexKey,
    ) {
        val toNote = getOrCreateNote(to)
        from.relays.forEach {
            toNote.addRelay(it)
        }
    }
}

@Stable
class LocalCacheFlow {
    private val _newEventBundles = MutableSharedFlow<Set<Note>>(0, 10, BufferOverflow.DROP_OLDEST)
    val newEventBundles = _newEventBundles.asSharedFlow() // read-only public view

    // Refreshes observers in batches.
    private val bundler = BundledInsert<Note>(1000, Dispatchers.Default)

    fun invalidateData(newNote: Note) {
        bundler.invalidateList(newNote) { bundledNewNotes ->
            _newEventBundles.emit(bundledNewNotes)
        }
    }
}
