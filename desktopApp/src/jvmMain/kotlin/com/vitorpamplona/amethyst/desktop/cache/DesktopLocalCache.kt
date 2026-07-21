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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.cache.LargeSoftCache
import com.vitorpamplona.amethyst.commons.service.nwc.NwcPaymentTracker
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.checkSignature
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.taggedAddresses
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.taggedQuotes
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop implementation of ICacheProvider.
 *
 * Provides in-memory caching of Users and Notes for the desktop application.
 * Supports searching users by name prefix for the search functionality.
 */
class DesktopLocalCache : ICacheProvider {
    val users = LargeSoftCache<HexKey, User>()
    val notes = LargeSoftCache<HexKey, Note>()
    val addressableNotes = LargeSoftCache<String, AddressableNote>()
    private val deletedEvents = ConcurrentHashMap.newKeySet<HexKey>()

    val eventStream = DesktopCacheEventStream()

    /** Local relay store for persisting events to SQLite. Set from Main.kt on account login. */
    var localRelayStore: com.vitorpamplona.amethyst.desktop.relay.LocalRelayStore? = null

    /** Cached follow set for the logged-in user. Thread-safe + Compose-observable. */
    private val _followedUsers = MutableStateFlow<Set<HexKey>>(emptySet())
    val followedUsers: StateFlow<Set<HexKey>> = _followedUsers.asStateFlow()

    /**
     * Active user's pubkey (hex). Set from Main.kt on login. When set, only
     * kind-3 events from this pubkey update [_followedUsers] and
     * [lastContactListEvent]. Other users' kind-3 events still flow through
     * [contactListEvents] for consumers like the WoT service.
     */
    @Volatile
    var accountPubkey: HexKey? = null

    /**
     * Fires for every accepted kind-3 event (both the active user's and
     * other users' — filtered downstream). Buffered so slow consumers don't
     * block the consume path.
     */
    private val _contactListEvents =
        MutableSharedFlow<ContactListEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val contactListEvents: SharedFlow<ContactListEvent> = _contactListEvents.asSharedFlow()

    /** Increments on each metadata update — observe to recompose when user names change. */
    private val _metadataVersion = MutableStateFlow(0L)
    val metadataVersion: StateFlow<Long> = _metadataVersion.asStateFlow()

    companion object {
    }

    /** Index of notes by author pubkey — for fast metadata invalidation */
    private val notesByAuthor = ConcurrentHashMap<HexKey, MutableSet<Note>>()

    val paymentTracker = NwcPaymentTracker()

    /**
     * Long-lived, cache-scoped coroutine scope for fire-and-forget work that must
     * outlive any single composition — e.g. the optimistic-consume → relay-broadcast
     * pair of a poll vote (see [com.vitorpamplona.amethyst.desktop.ui.voteOnPoll]).
     * Using a card's [androidx.compose.runtime.rememberCoroutineScope] there would let
     * scrolling the card out of composition cancel the broadcast after the local consume,
     * leaving the vote visible locally but never sent. Uses a [SupervisorJob] so one
     * failed job doesn't tear down the rest.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun trackNoteAuthor(
        note: Note,
        authorPubkey: HexKey,
    ) {
        notesByAuthor.getOrPut(authorPubkey) { ConcurrentHashMap.newKeySet() }.add(note)
    }

    // ----- User operations -----

    override fun getUserIfExists(pubkey: HexKey): User? = users.get(pubkey)

    override fun getOrCreateUser(pubkey: HexKey): User = users.getOrCreate(pubkey) { User(pubkey, userContext) }

    /**
     * [UserContext] bridge — resolves the [User]'s pinned replaceable notes
     * (kind:10002 outbox, kind:10050 DM inbox, kind:10019 nutzap info) against
     * the same [addressableNotes] map that [consume] writes into. Backing these
     * with [getOrCreateAddressableNote] (not the plain [notes] map) is what lets
     * `User.dmInboxRelaysStrict()` / `outboxRelays()` actually reflect events the
     * cache has ingested — the DM send path, the resolver's local lookup, and
     * tier-1 AUTH all read those accessors. User's lazy fields hold the
     * resulting note for its lifetime, same pinning guarantee as on Android.
     */
    private val userContext = UserContext { addr -> getOrCreateAddressableNote(addr) }

    override fun countUsers(predicate: (String, User) -> Boolean): Int = users.count { key, user -> predicate(key, user) }

    override fun findUsersStartingWith(
        prefix: String,
        limit: Int,
    ): List<User> {
        if (prefix.isBlank()) return emptyList()

        // Check if it's a valid pubkey/npub first
        val pubkeyHex = decodePublicKeyAsHexOrNull(prefix)
        if (pubkeyHex != null) {
            val user = getUserIfExists(pubkeyHex)
            if (user != null) return listOf(user)
        }

        val dualCase =
            listOf(
                DualCase(prefix.lowercase(), prefix.uppercase()),
            )

        // Search by name/displayName/nip05/lud16
        val results = mutableListOf<User>()
        users.forEach { _, user ->
            val metadata = user.metadataOrNull()
            val matches =
                if (metadata == null) {
                    user.pubkeyHex.startsWith(prefix, true) ||
                        user.pubkeyNpub().startsWith(prefix, true)
                } else {
                    metadata.anyNameOrAddressContains(dualCase) ||
                        user.pubkeyHex.startsWith(prefix, true) ||
                        user.pubkeyNpub().startsWith(prefix, true)
                }
            if (matches) results.add(user)
        }
        return results
            .sortedWith(
                compareBy(
                    { it.metadataOrNull()?.anyNameStartsWith(dualCase) == false },
                    { it.metadataOrNull()?.anyAddressStartsWith(dualCase) == false },
                    { it.toBestDisplayName().lowercase() },
                    { it.pubkeyHex },
                ),
            ).take(limit)
    }

    /**
     * Invoked (with the author's pubkey) every time a kind:0 profile is
     * ingested. Wired by the subscriptions coordinator to back-fill that
     * author's kind:10002 (NIP-65 outbox) — so whenever we learn who a user
     * is, we also learn where they write, which is where their kind:10050 and
     * other replaceables live. The handler dedupes and skips authors whose
     * 10002 is already cached, so firing on every kind:0 is cheap.
     */
    var onProfileMetadataConsumed: ((HexKey) -> Unit)? = null

    /**
     * Updates user metadata from a MetadataEvent.
     * Called when receiving kind 0 events from relays.
     */
    fun consumeMetadata(event: MetadataEvent) {
        val user = getOrCreateUser(event.pubKey)

        if (user.metadata().shouldUpdateWith(event)) {
            val newUserMetadata = event.contactMetaData()
            if (newUserMetadata != null) {
                user.updateUserInfo(newUserMetadata, event)
                _metadataVersion.value++
                // Invalidate metadata flows on notes by this author (O(K) via index)
                notesByAuthor[event.pubKey]?.forEach { note ->
                    if (note.flowSet?.metadata?.hasObservers() == true) {
                        note.flowSet?.metadata?.invalidateData()
                    }
                }
            }
        }

        // Learn where this author writes (kind:10002) so their replaceables —
        // kind:10050 included — can be read from their own outbox relays.
        onProfileMetadataConsumed?.invoke(event.pubKey)
    }

    fun justVerify(event: Event): Boolean =
        if (!event.verify()) {
            try {
                event.checkSignature()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("Event Verification Failed") {
                    "Kind: ${event.kind} createdAt=${event.createdAt} id=${event.id} pubkey=${event.pubKey} reason=${e.message}"
                }
            }
            false
        } else {
            true
        }

    // ----- Event consumption -----

    fun consume(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean = false,
    ): Boolean {
        if (!wasVerified && !justVerify(event)) return false
        val consumed = route(event, relay)
        // Write-through to local store, but skip if event came from local store (hydration)
        if (consumed && relay != com.vitorpamplona.amethyst.desktop.relay.LocalRelayStore.LOCAL_RELAY_URL) {
            localRelayStore?.enqueue(event)
        }
        return consumed
    }

    private fun route(
        event: Event,
        relay: NormalizedRelayUrl?,
    ): Boolean =
        when (event) {
            is MetadataEvent -> {
                consumeMetadata(event)
                false // metadata updates User, not Note — skip event stream
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

            is LongTextNoteEvent -> {
                consumeLongTextNote(event, relay)
            }

            is BookmarkListEvent -> {
                consumeBookmarkList(event)
            }

            is OldBookmarkListEvent -> {
                consumeOldBookmarkList(event)
            }

            is FollowListEvent -> {
                consumeFollowList(event)
            }

            is CommentEvent -> {
                consumeComment(event, relay)
            }

            is AdvertisedRelayListEvent -> {
                consumeAdvertisedRelayList(event, relay)
            }

            is ChatMessageRelayListEvent -> {
                consumeChatMessageRelayList(event, relay)
            }

            is BlossomServersEvent -> {
                consumeBlossomServerList(event, relay)
            }

            is PollEvent -> {
                consumePoll(event, relay)
            }

            is PollResponseEvent -> {
                consumePollResponse(event, relay)
            }

            else -> {
                false
            }
        }

    /**
     * Consumes a kind 10063 (NIP-B7) Blossom media server list event. Stores
     * the newest per-author copy in [addressableNotes] so state holders like
     * [com.vitorpamplona.amethyst.commons.model.nipB7Blossom.BlossomServerListState]
     * observe it via their flows. This is the same event the Amethyst mobile
     * app uses for the media server list. Emits nothing to the event stream —
     * the UI doesn't render kind 10063s directly.
     */
    private fun consumeBlossomServerList(
        event: BlossomServersEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val addressableNote = getOrCreateAddressableNote(event.address())
        val existing = addressableNote.event
        if (existing != null && existing.createdAt >= event.createdAt) return false
        val author = getOrCreateUser(event.pubKey)
        addressableNote.loadEvent(event, author, emptyList())
        relay?.let { addressableNote.addRelay(it) }
        return false
    }

    /**
     * Consumes a kind 10002 (NIP-65) advertised relay list event. Stores
     * the newest per-author copy in [addressableNotes] so the outbox
     * dispatcher can look up each follow's declared write relays without
     * a fresh REQ. Emits nothing to the event stream — the UI doesn't
     * render kind 10002s directly.
     */
    private fun consumeAdvertisedRelayList(
        event: AdvertisedRelayListEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val addressableNote = getOrCreateAddressableNote(event.address())
        val existing = addressableNote.event
        if (existing != null && existing.createdAt >= event.createdAt) return false
        val author = getOrCreateUser(event.pubKey)
        addressableNote.loadEvent(event, author, emptyList())
        relay?.let { addressableNote.addRelay(it) }
        return false
    }

    /**
     * Consumes a kind 10050 (NIP-17) DM relay-list event. Stores the newest
     * per-author copy in [addressableNotes] so [User.dmInboxRelaysStrict] and
     * the DM send path can resolve a recipient's inbox without a fresh REQ.
     *
     * Without this branch, [route] dropped kind:10050 entirely — recipient DM
     * relay lists arriving through the normal feed / DM subscriptions never
     * landed in the cache, forcing every send to re-discover them via the
     * indexer fan-out. Mirrors [consumeAdvertisedRelayList]; emits nothing to
     * the event stream because the UI doesn't render kind 10050s directly.
     */
    private fun consumeChatMessageRelayList(
        event: ChatMessageRelayListEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val addressableNote = getOrCreateAddressableNote(event.address())
        val existing = addressableNote.event
        if (existing != null && existing.createdAt >= event.createdAt) return false
        val author = getOrCreateUser(event.pubKey)
        addressableNote.loadEvent(event, author, emptyList())
        relay?.let { addressableNote.addRelay(it) }
        return false
    }

    /**
     * Returns the cached kind-10002 event for [pubkey], if any. Used by the
     * outbox dispatcher to skip a Phase-1 REQ for authors whose write-relay
     * list is already in the store (from a previous session's local relay
     * hydration or an in-session discovery).
     */
    fun cachedAdvertisedRelayList(pubkey: HexKey): AdvertisedRelayListEvent? = addressableNotes.get(AdvertisedRelayListEvent.createAddress(pubkey).toValue())?.event as? AdvertisedRelayListEvent

    /**
     * Consumes a kind 1 text note event.
     * Creates/updates Note in cache and links reply relationships.
     */
    private fun consumeTextNote(
        event: TextNoteEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val repliesTo = event.tagsWithoutCitations().map { getOrCreateNote(it) }
        note.loadEvent(event, author, repliesTo)
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        repliesTo.forEach { it.addReply(note) }
        addQuoteBoosts(event, note, repliesTo)
        return true
    }

    /**
     * Consumes a kind 1111 comment event (NIP-22).
     * Like text notes but uses BaseThreadedEvent reply structure.
     */
    private fun consumeComment(
        event: CommentEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val repliesTo = event.tagsWithoutCitations().map { getOrCreateNote(it) }
        note.loadEvent(event, author, repliesTo)
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        repliesTo.forEach { it.addReply(note) }
        addQuoteBoosts(event, note, repliesTo)
        return true
    }

    /**
     * Consumes a kind 1068 poll event (NIP-88).
     * Creates a Note in the cache like a text note, minus reply-linking — a poll is
     * always a root post. The [Note.pollState] tally is populated by the responses.
     */
    private fun consumePoll(
        event: PollEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        note.loadEvent(event, author, emptyList())
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        return true
    }

    /**
     * Consumes a kind 1018 poll response event (NIP-88).
     * Resolves the referenced poll, loads the response note, and links it into the
     * poll's tally. Mirrors Android `LocalCache.consume(PollResponseEvent)`: the
     * [com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache.addResponse]
     * call and the `true` return happen only on a genuinely new event, so a relay echo
     * of the user's own optimistically-consumed vote can't double-count (id-dedup here
     * plus `addResponse`'s own containment guard).
     */
    private fun consumePollResponse(
        event: PollResponseEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val pollId = event.poll()?.eventId ?: return false
        val pollNote = getOrCreateNote(pollId)
        val responseNote = getOrCreateNote(event.id)
        if (responseNote.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        responseNote.loadEvent(event, author, emptyList())
        trackNoteAuthor(responseNote, event.pubKey)
        relay?.let { responseNote.addRelay(it) }
        pollNote.pollState().addResponse(responseNote)
        return true
    }

    /**
     * NIP-18 quote reposts: a note carrying a `q` tag is a quote-repost of the quoted
     * note, so it counts as a boost in the quoted note's repost counter alongside
     * kind:6/kind:16 reposts. The quoted note is kept out of `replyTo` so the quote
     * still renders as a root post; targets already replied to are skipped to avoid
     * double-counting, and self-quotes are ignored.
     */
    private fun addQuoteBoosts(
        event: Event,
        note: Note,
        repliesTo: List<Note>,
    ) {
        event.taggedQuotes().forEach { qTag ->
            val quoted =
                when (qTag) {
                    is QEventTag -> getOrCreateNote(qTag.eventId)
                    is QAddressableTag -> getOrCreateAddressableNote(qTag.address)
                    else -> null
                }
            if (quoted != null && quoted != note && quoted !in repliesTo) {
                quoted.addBoost(note)
            }
        }
    }

    /**
     * Consumes a kind 7 reaction event.
     * Links reaction to target notes via e-tags and a-tags.
     */
    private fun consumeReaction(
        event: ReactionEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        val reactedTo =
            event.originalPost().mapNotNull { getNoteIfExists(it) } +
                event.taggedAddresses().mapNotNull { addressableNotes.get(it.toValue()) }
        note.loadEvent(event, author, reactedTo)
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        reactedTo.forEach { it.addReaction(note) }
        return true
    }

    /**
     * Consumes a kind 9734 zap request event.
     * Must be consumed before the corresponding LnZapEvent (kind 9735).
     */
    private fun consumeZapRequest(
        event: LnZapRequestEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        note.loadEvent(event, author, emptyList())
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        return true
    }

    /**
     * Consumes a kind 9735 zap receipt event.
     * Links zap to target notes via the embedded zap request.
     */
    private fun consumeZap(
        event: LnZapEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)

        // Get or consume the embedded zap request
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
                event.taggedAddresses().mapNotNull { addressableNotes.get(it.toValue()) }

        note.loadEvent(event, author, zappedNotes)
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }

        // Link zap to target notes
        if (zapRequestNote != null) {
            zappedNotes.forEach { it.addZap(zapRequestNote, note) }
        }

        return true
    }

    /**
     * Consumes a kind 6 repost event.
     * Links repost to target note via e-tag.
     * Uses getOrCreateNote for the boosted note so the link exists even if
     * the original note hasn't arrived yet (it will be filled in later).
     */
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
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        boostedNote?.addBoost(note)
        return true
    }

    /**
     * Consumes a kind 16 generic repost event.
     * Links repost to target note via e-tag.
     */
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
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        boostedNote?.addBoost(note)
        return true
    }

    /**
     * Consumes a kind 3 contact list event (replaceable).
     *
     * Tracks the newest kind-3 per author (not a single global scalar) so
     * ingesting other users' follow lists (e.g. for WoT scoring) doesn't
     * corrupt the active user's state. Only the active user's kind-3 updates
     * [_followedUsers] / [lastContactListEvent]. Every accepted event fans
     * out on [_contactListEvents] for downstream consumers.
     */
    private val lastContactListByAuthor = ConcurrentHashMap<HexKey, Long>()

    var lastContactListEvent: ContactListEvent? = null
        private set

    private fun consumeContactList(event: ContactListEvent): Boolean {
        // Replaceable event — only accept newer contact lists per author.
        val prev = lastContactListByAuthor[event.pubKey] ?: 0L
        if (event.createdAt <= prev) return false

        // Stamp lastContactListByAuthor *only* on branches where we know
        // whether this event is the active user's own kind-3. If accountPubkey
        // hasn't been bound yet (login/hydration ordering window), skip the
        // stamp entirely so a later relay retry — after Main.kt binds
        // accountPubkey — is not rejected by the createdAt gate. The
        // _followedUsers state remains untouched in that case; downstream
        // consumers still get the fan-out via _contactListEvents (WoT etc).
        val currentAccountPubkey = accountPubkey
        when {
            event.pubKey == currentAccountPubkey -> {
                lastContactListEvent = event
                _followedUsers.value = event.verifiedFollowKeySet()
                lastContactListByAuthor[event.pubKey] = event.createdAt
            }
            currentAccountPubkey != null -> {
                // Known-not-self: safe to stamp.
                lastContactListByAuthor[event.pubKey] = event.createdAt
            }
            else -> {
                // accountPubkey not bound yet — cannot tell if this is self.
                // Defer stamping so the relay retry that arrives after bind
                // will still populate _followedUsers.
            }
        }

        // Store in addressableNotes too — Kind3FollowListState.getFollowListEvent
        // reads from getOrCreateAddressableNote(...) and would otherwise see a
        // null event, causing buildFollowBatch to start a fresh kind-3 (wiping
        // existing follows).
        val addressableNote = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)
        addressableNote.loadEvent(event, author, emptyList())

        _contactListEvents.tryEmit(event)
        return true
    }

    /**
     * Consumes a kind 30023 long-form text note event.
     * Creates Note in cache like TextNoteEvent.
     */
    private fun consumeLongTextNote(
        event: LongTextNoteEvent,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false
        val author = getOrCreateUser(event.pubKey)
        note.loadEvent(event, author, emptyList())
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }
        return true
    }

    /**
     * Consumes a kind 30001 bookmark list event (addressable/replaceable).
     * Stores in addressableNotes cache.
     */
    private fun consumeBookmarkList(event: BookmarkListEvent): Boolean {
        val address = event.address()
        val addressableNote = getOrCreateAddressableNote(address)
        val author = getOrCreateUser(event.pubKey)

        // Only update if newer
        val existingEvent = addressableNote.event
        if (existingEvent != null && existingEvent.createdAt >= event.createdAt) return false

        addressableNote.loadEvent(event, author, emptyList())
        return true
    }

    private fun consumeOldBookmarkList(event: OldBookmarkListEvent): Boolean {
        val address = event.address()
        val addressableNote = getOrCreateAddressableNote(address)
        val author = getOrCreateUser(event.pubKey)

        // Only update if newer
        val existingEvent = addressableNote.event
        if (existingEvent != null && existingEvent.createdAt >= event.createdAt) return false

        addressableNote.loadEvent(event, author, emptyList())
        return true
    }

    /**
     * Consumes a kind 39089 follow pack event (NIP-51).
     * Addressable replaceable event — keyed by (kind, pubkey, dTag).
     * Bumps [followPackVersion] so observers can refresh their snapshot.
     */
    private val _followPackVersion = MutableStateFlow(0L)
    val followPackVersion: StateFlow<Long> = _followPackVersion.asStateFlow()

    private fun consumeFollowList(event: FollowListEvent): Boolean {
        val address = event.address()
        val addressableNote = getOrCreateAddressableNote(address)
        val author = getOrCreateUser(event.pubKey)

        val existingEvent = addressableNote.event
        if (existingEvent != null && existingEvent.createdAt >= event.createdAt) return false

        addressableNote.loadEvent(event, author, emptyList())
        _followPackVersion.value++
        return true
    }

    /**
     * Snapshot all 39089 (FollowListEvent) addressable notes currently in cache.
     * Use [followPackVersion] to drive reactive recomputation.
     */
    fun snapshotFollowPacks(): List<FollowListEvent> {
        val out = mutableListOf<FollowListEvent>()
        addressableNotes.forEach { _, note ->
            val ev = note.event
            if (ev is FollowListEvent) out.add(ev)
        }
        return out
    }

    // ----- NWC Payment operations -----

    /**
     * Consumes a NIP-47 payment request event.
     * Registers the request with the tracker and links it to the zapped note.
     *
     * @param event The payment request event
     * @param zappedNote The note being zapped (if this payment is for a zap)
     * @param relay The relay this event came from
     * @param onResponse Callback invoked when wallet responds
     * @return true if event was processed, false if already seen
     */
    fun consume(
        event: LnZapPaymentRequestEvent,
        zappedNote: Note?,
        relay: NormalizedRelayUrl?,
        onResponse: suspend (LnZapPaymentResponseEvent) -> Unit,
        wasVerified: Boolean = false,
    ): Boolean {
        if (!wasVerified && !justVerify(event)) return false
        val expectedServicePubkey =
            event.walletServicePubKey() ?: run {
                Log.w("DesktopLocalCache") { "NWC request ${event.id} has no `p` tag; cannot register for response." }
                return false
            }
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event
        if (note.event != null) return false

        note.loadEvent(event, author, emptyList())
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }

        zappedNote?.addZapPayment(note, null)
        paymentTracker.registerRequest(event.id, expectedServicePubkey, zappedNote, onResponse)

        return true
    }

    /**
     * Consumes a NIP-47 payment response event.
     * Matches to pending request, links notes, and invokes callback.
     *
     * @param event The payment response event
     * @param relay The relay this event came from
     * @return true if event was processed, false if no matching request
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun consume(
        event: LnZapPaymentResponseEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean = false,
    ): Boolean {
        if (!wasVerified && !justVerify(event)) return false
        val requestId = event.requestId()
        val pending =
            when (val match = paymentTracker.onResponseReceived(requestId, event.pubKey)) {
                is NwcPaymentTracker.MatchResult.Matched -> match.pending
                is NwcPaymentTracker.MatchResult.WrongAuthor -> {
                    Log.w("DesktopLocalCache") {
                        "Rejecting NWC response ${event.id}: expected author ${match.expected} but event was signed by ${match.actual}. " +
                            "This may be a spoofed reply — keeping the request pending for the legitimate wallet response."
                    }
                    return false
                }
                NwcPaymentTracker.MatchResult.NoMatch -> return false
            }

        val requestNote = requestId?.let { getNoteIfExists(it) }
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event
        if (note.event != null) return false

        note.loadEvent(event, author, emptyList())
        trackNoteAuthor(note, event.pubKey)
        relay?.let { note.addRelay(it) }

        // Link response to zapped note via request
        requestNote?.let { req -> pending.zappedNote?.addZapPayment(req, note) }

        // Invoke callback on IO dispatcher
        appScope.launch {
            pending.onResponse(event)
        }

        return true
    }

    // ----- Note operations -----

    override fun getNoteIfExists(hexKey: HexKey): Note? = notes.get(hexKey)

    override fun checkGetOrCreateNote(hexKey: HexKey): Note = getOrCreateNote(hexKey)

    fun getOrCreateNote(hexKey: HexKey): Note =
        notes.getOrCreate(hexKey) {
            Note(hexKey)
        }

    override fun getOrCreateAddressableNote(key: Address): AddressableNote =
        addressableNotes.getOrCreate(key.toValue()) {
            AddressableNote(key)
        }

    // ----- Channel operations -----

    override fun getAnyChannel(note: Note): Channel? {
        // Desktop doesn't support channels yet
        return null
    }

    // ----- Deletion tracking -----

    override fun hasBeenDeleted(event: Any): Boolean =
        when (event) {
            is Note -> deletedEvents.contains(event.idHex)
            is Event -> deletedEvents.contains(event.id)
            else -> false
        }

    fun markAsDeleted(eventId: HexKey) {
        deletedEvents.add(eventId)
    }

    // ----- Own event consumption -----

    override fun justConsumeMyOwnEvent(event: Event): Boolean {
        if (!justVerify(event)) return false
        // For addressable/replaceable events, store in the addressable note cache
        // so state holders (Nip65RelayListState, etc.) pick it up via their flows
        if (event is AddressableEvent) {
            val address = event.address()
            val note = getOrCreateAddressableNote(address)
            val author = getOrCreateUser(event.pubKey)
            if (note.event == null || (note.event?.createdAt ?: 0) <= event.createdAt) {
                note.loadEvent(event, author, emptyList())
                return true
            }
        }
        return false
    }

    // ----- Event stream -----

    override fun getEventStream(): ICacheEventStream = eventStream

    /**
     * Emits a new note bundle to observers.
     */
    suspend fun emitNewNotes(notes: Set<Note>) {
        eventStream.emitNewNotes(notes)
    }

    /**
     * Emits deleted notes to observers.
     */
    suspend fun emitDeletedNotes(notes: Set<Note>) {
        eventStream.emitDeletedNotes(notes)
    }

    // ----- Profile count cache -----

    private val followerCounts = ConcurrentHashMap<HexKey, Int>()
    private val followingCounts = ConcurrentHashMap<HexKey, Int>()

    fun getCachedFollowerCount(pubkey: HexKey): Int = followerCounts[pubkey] ?: 0

    fun getCachedFollowingCount(pubkey: HexKey): Int = followingCounts[pubkey] ?: 0

    fun cacheFollowerCount(
        pubkey: HexKey,
        count: Int,
    ) {
        followerCounts[pubkey] = count
    }

    fun cacheFollowingCount(
        pubkey: HexKey,
        count: Int,
    ) {
        followingCounts[pubkey] = count
    }

    // ----- Memory Cleanup -----

    fun cleanMemory() {
        notes.cleanUp()
        addressableNotes.cleanUp()
        users.cleanUp()
    }

    // ----- Stats -----

    fun userCount(): Int = users.size()

    fun noteCount(): Int = notes.size()

    fun clear() {
        users.clear()
        notes.clear()
        addressableNotes.clear()
        deletedEvents.clear()
        _followedUsers.value = emptySet()
        followerCounts.clear()
        followingCounts.clear()
        notesByAuthor.clear()
        lastContactListByAuthor.clear()
        lastContactListEvent = null
        accountPubkey = null
    }
}

/**
 * Desktop implementation of ICacheEventStream.
 */
class DesktopCacheEventStream : ICacheEventStream {
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
