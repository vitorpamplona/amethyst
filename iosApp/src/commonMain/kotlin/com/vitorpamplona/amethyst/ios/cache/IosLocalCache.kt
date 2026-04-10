/*
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
package com.vitorpamplona.amethyst.ios.cache

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.threading.platformSynchronized
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.taggedAddresses
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS implementation of ICacheProvider.
 * Uses platformSynchronized from commons for thread safety on iOS.
 */
class IosLocalCache : ICacheProvider {
    private val users = HashMap<HexKey, User>()
    private val notes = HashMap<HexKey, Note>()
    private val addressableNotes = HashMap<String, AddressableNote>()
    private val deletedEvents = HashSet<HexKey>()
    private val lock = Any()

    val eventStream = IosCacheEventStream()

    private val _followedUsers = MutableStateFlow<Set<HexKey>>(emptySet())
    val followedUsers: StateFlow<Set<HexKey>> = _followedUsers.asStateFlow()

    private val _latestContactList = MutableStateFlow<ContactListEvent?>(null)
    val latestContactList: StateFlow<ContactListEvent?> = _latestContactList.asStateFlow()

    /** Stores the latest MetadataEvent per pubkey for use in profile editing. */
    private val latestMetadataEvents = HashMap<HexKey, MetadataEvent>()

    fun getLatestMetadataEvent(pubKeyHex: HexKey): MetadataEvent? = platformSynchronized(lock) { latestMetadataEvents[pubKeyHex] }

    // ----- User operations -----

    override fun getUserIfExists(pubkey: HexKey): User? = platformSynchronized(lock) { users[pubkey] }

    override fun getOrCreateUser(pubkey: HexKey): User =
        platformSynchronized(lock) {
            users.getOrPut(pubkey) {
                val nip65Note = getOrCreateNoteInternal("nip65:$pubkey")
                val dmNote = getOrCreateNoteInternal("dm:$pubkey")
                User(pubkey, nip65Note, dmNote)
            }
        }

    override fun countUsers(predicate: (String, User) -> Boolean): Int = platformSynchronized(lock) { users.count { (key, user) -> predicate(key, user) } }

    override fun findUsersStartingWith(
        prefix: String,
        limit: Int,
    ): List<User> {
        if (prefix.isBlank()) return emptyList()

        val dualCase = listOf(DualCase(prefix.lowercase(), prefix.uppercase()))

        val results = mutableListOf<User>()
        platformSynchronized(lock) {
            users.forEach { (_, user) ->
                val metadata = user.metadataOrNull()
                val matches =
                    if (metadata == null) {
                        user.pubkeyHex.startsWith(prefix, true)
                    } else {
                        metadata.anyNameOrAddressContains(dualCase) ||
                            user.pubkeyHex.startsWith(prefix, true)
                    }
                if (matches) results.add(user)
            }
        }
        return results
            .sortedWith(
                compareBy(
                    { it.metadataOrNull()?.anyNameStartsWith(dualCase) == false },
                    { it.toBestDisplayName().lowercase() },
                ),
            ).take(limit)
    }

    fun consumeMetadata(event: MetadataEvent) {
        val user = getOrCreateUser(event.pubKey)
        if (user.metadata().shouldUpdateWith(event)) {
            val newUserMetadata = event.contactMetaData()
            if (newUserMetadata != null) {
                user.updateUserInfo(newUserMetadata, event)
                platformSynchronized(lock) {
                    latestMetadataEvents[event.pubKey] = event
                }
            }
        }
    }

    // ----- Event consumption -----

    fun consume(
        event: Event,
        relay: NormalizedRelayUrl?,
    ): Boolean =
        when (event) {
            is MetadataEvent -> {
                consumeMetadata(event)
                false
            }

            is TextNoteEvent -> {
                consumeTextNote(event, relay)
            }

            is ReactionEvent -> {
                consumeReaction(event, relay)
            }

            is LnZapRequestEvent -> {
                consumeZapRequest(event, relay)
            }

            is LnZapEvent -> {
                consumeZap(event, relay)
            }

            is RepostEvent -> {
                consumeRepost(event, relay)
            }

            is GenericRepostEvent -> {
                consumeGenericRepost(event, relay)
            }

            is ContactListEvent -> {
                consumeContactList(event)
            }

            is CommentEvent -> {
                consumeComment(event, relay)
            }

            else -> {
                false
            }
        }

    private fun consumeTextNote(
        event: TextNoteEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val repliesTo = event.tagsWithoutCitations().map { getOrCreateNote(it) }
        note.loadEvent(event, author, repliesTo)
        relay?.let { note.addRelay(it) }
        repliesTo.forEach { it.addReply(note) }
        return true
    }

    private fun consumeComment(
        event: CommentEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val repliesTo = event.tagsWithoutCitations().map { getOrCreateNote(it) }
        note.loadEvent(event, author, repliesTo)
        relay?.let { note.addRelay(it) }
        repliesTo.forEach { it.addReply(note) }
        return true
    }

    private fun consumeReaction(
        event: ReactionEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val reactedTo =
            event.originalPost().mapNotNull { getNoteIfExists(it) } +
                event.taggedAddresses().mapNotNull { platformSynchronized(lock) { addressableNotes[it.toValue()] } }
        note.loadEvent(event, author, reactedTo)
        relay?.let { note.addRelay(it) }
        reactedTo.forEach { it.addReaction(note) }
        return true
    }

    private fun consumeZapRequest(
        event: LnZapRequestEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        note.loadEvent(event, author, emptyList())
        relay?.let { note.addRelay(it) }
        return true
    }

    private fun consumeZap(
        event: LnZapEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)

        val zapRequestEvent = event.zapRequest
        val zapRequestNote =
            if (zapRequestEvent != null) {
                consumeZapRequest(zapRequestEvent, relay)
                getOrCreateNote(zapRequestEvent.id)
            } else {
                null
            }

        val zappedNotes =
            event.zappedPost().mapNotNull { getNoteIfExists(it) } +
                event.taggedAddresses().mapNotNull { platformSynchronized(lock) { addressableNotes[it.toValue()] } }

        note.loadEvent(event, author, zappedNotes)
        relay?.let { note.addRelay(it) }

        if (zapRequestNote != null) {
            zappedNotes.forEach { it.addZap(zapRequestNote, note) }
        }

        return true
    }

    private fun consumeRepost(
        event: RepostEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val boostedId = event.boostedEventId()
        val boostedNote = boostedId?.let { getOrCreateNote(it) }
        val repliesTo = listOfNotNull(boostedNote)
        note.loadEvent(event, author, repliesTo)
        relay?.let { note.addRelay(it) }
        boostedNote?.addBoost(note)
        return true
    }

    private fun consumeGenericRepost(
        event: GenericRepostEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val boostedNote = event.boostedEventId()?.let { getOrCreateNote(it) }
        val repliesTo = listOfNotNull(boostedNote)
        note.loadEvent(event, author, repliesTo)
        relay?.let { note.addRelay(it) }
        boostedNote?.addBoost(note)
        return true
    }

    private var lastContactListCreatedAt = 0L

    private fun consumeContactList(event: ContactListEvent): Boolean {
        if (event.createdAt <= lastContactListCreatedAt) return false
        lastContactListCreatedAt = event.createdAt
        _followedUsers.value = event.verifiedFollowKeySet()
        _latestContactList.value = event
        return true
    }

    // ----- Note operations -----

    override fun getNoteIfExists(hexKey: HexKey): Note? = platformSynchronized(lock) { notes[hexKey] }

    override fun checkGetOrCreateNote(hexKey: HexKey): Note = getOrCreateNote(hexKey)

    fun getOrCreateNote(hexKey: HexKey): Note =
        platformSynchronized(lock) {
            getOrCreateNoteInternal(hexKey)
        }

    // Internal version that doesn't lock — call from within platformSynchronized blocks
    private fun getOrCreateNoteInternal(hexKey: HexKey): Note = notes.getOrPut(hexKey) { Note(hexKey) }

    override fun getOrCreateAddressableNote(key: Address): AddressableNote =
        platformSynchronized(lock) {
            addressableNotes.getOrPut(key.toValue()) { AddressableNote(key) }
        }

    override fun getAnyChannel(note: Note): Channel? = null

    override fun hasBeenDeleted(event: Any): Boolean =
        when (event) {
            is Note -> platformSynchronized(lock) { deletedEvents.contains(event.idHex) }
            is Event -> platformSynchronized(lock) { deletedEvents.contains(event.id) }
            else -> false
        }

    override fun justConsumeMyOwnEvent(event: Event): Boolean = false

    override fun getEventStream(): ICacheEventStream = eventStream

    suspend fun emitNewNotes(notes: Set<Note>) {
        eventStream.emitNewNotes(notes)
    }

    fun userCount(): Int = platformSynchronized(lock) { users.size }

    fun noteCount(): Int = platformSynchronized(lock) { notes.size }

    /**
     * Returns all notes currently in cache. Used by feed filters.
     */
    fun allNotes(): List<Note> = platformSynchronized(lock) { notes.values.toList() }

    fun clear() {
        platformSynchronized(lock) {
            users.clear()
            notes.clear()
            addressableNotes.clear()
            deletedEvents.clear()
        }
        _followedUsers.value = emptySet()
        _latestContactList.value = null
    }
}

class IosCacheEventStream : ICacheEventStream {
    private val _newEventBundles =
        MutableSharedFlow<Set<Note>>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val _deletedEventBundles =
        MutableSharedFlow<Set<Note>>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val newEventBundles: SharedFlow<Set<Note>> = _newEventBundles
    override val deletedEventBundles: SharedFlow<Set<Note>> = _deletedEventBundles

    suspend fun emitNewNotes(notes: Set<Note>) {
        _newEventBundles.emit(notes)
    }

    suspend fun emitDeletedNotes(notes: Set<Note>) {
        _deletedEventBundles.emit(notes)
    }
}
