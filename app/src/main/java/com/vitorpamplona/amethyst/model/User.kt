package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.Bech32
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.BookmarkListEvent
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.MetadataEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.toNpub
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import java.math.BigDecimal
import java.util.regex.Pattern

val lnurlpPattern = Pattern.compile("(?i:http|https):\\/\\/((.+)\\/)*\\.well-known\\/lnurlp\\/(.*)")

@Stable
class User(val pubkeyHex: String) {
    var info: UserMetadata? = null

    var latestContactList: ContactListEvent? = null
    var latestBookmarkList: BookmarkListEvent? = null

    var reports = mapOf<User, Set<Note>>()
        private set

    var latestEOSEs: Map<String, EOSETime> = emptyMap()

    var zaps = mapOf<Note, Note?>()
        private set

    var relaysBeingUsed = mapOf<String, RelayInfo>()
        private set

    var privateChatrooms = mapOf<User, Chatroom>()
        private set

    fun pubkey() = Hex.decode(pubkeyHex)
    fun pubkeyNpub() = pubkey().toNpub()

    fun pubkeyDisplayHex() = pubkeyNpub().toShortenHex()

    fun toNostrUri() = "nostr:${pubkeyNpub()}"

    override fun toString(): String = pubkeyHex

    fun toBestDisplayName(): String {
        return bestDisplayName() ?: bestUsername() ?: pubkeyDisplayHex()
    }

    fun bestUsername(): String? {
        return info?.name?.ifBlank { null } ?: info?.username?.ifBlank { null }
    }

    fun bestDisplayName(): String? {
        return info?.displayName?.ifBlank { null } ?: info?.display_name?.ifBlank { null }
    }

    fun nip05(): String? {
        return info?.nip05?.ifBlank { null }
    }

    fun profilePicture(): String? {
        if (info?.picture.isNullOrBlank()) info?.picture = null
        return info?.picture
    }

    fun updateBookmark(event: BookmarkListEvent) {
        if (event.id == latestBookmarkList?.id) return

        latestBookmarkList = event
        liveSet?.bookmarks?.invalidateData()
    }

    fun updateContactList(event: ContactListEvent) {
        if (event.id == latestContactList?.id) return

        val oldContactListEvent = latestContactList
        latestContactList = event

        // Update following of the current user
        liveSet?.follows?.invalidateData()

        // Update Followers of the past user list
        // Update Followers of the new contact list
        (oldContactListEvent)?.unverifiedFollowKeySet()?.forEach {
            LocalCache.users[it]?.liveSet?.followers?.invalidateData()
        }
        (latestContactList)?.unverifiedFollowKeySet()?.forEach {
            LocalCache.users[it]?.liveSet?.followers?.invalidateData()
        }

        liveSet?.relays?.invalidateData()
    }

    fun addReport(note: Note) {
        val author = note.author ?: return

        if (author !in reports.keys) {
            reports = reports + Pair(author, setOf(note))
            liveSet?.reports?.invalidateData()
        } else if (reports[author]?.contains(note) == false) {
            reports = reports + Pair(author, (reports[author] ?: emptySet()) + note)
            liveSet?.reports?.invalidateData()
        }
    }

    fun removeReport(deleteNote: Note) {
        val author = deleteNote.author ?: return

        if (author in reports.keys && reports[author]?.contains(deleteNote) == true) {
            reports[author]?.let {
                reports = reports + Pair(author, it.minus(deleteNote))
                liveSet?.reports?.invalidateData()
            }
        }
    }

    fun addZap(zapRequest: Note, zap: Note?) {
        if (zapRequest !in zaps.keys) {
            zaps = zaps + Pair(zapRequest, zap)
            liveSet?.zaps?.invalidateData()
        } else if (zapRequest in zaps.keys && zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            liveSet?.zaps?.invalidateData()
        }
    }

    fun zappedAmount(): BigDecimal {
        return zaps.mapNotNull { it.value?.event }
            .filterIsInstance<LnZapEvent>()
            .mapNotNull {
                it.amount
            }.sumOf { it }
    }

    fun reportsBy(user: User): Set<Note> {
        return reports[user] ?: emptySet()
    }

    fun reportAuthorsBy(users: Set<HexKey>): List<User> {
        return reports.keys.filter { it.pubkeyHex in users }
    }

    fun countReportAuthorsBy(users: Set<HexKey>): Int {
        return reports.keys.count { it.pubkeyHex in users }
    }

    fun reportsBy(users: Set<HexKey>): List<Note> {
        return reportAuthorsBy(users).mapNotNull {
            reports[it]
        }.flatten()
    }

    @Synchronized
    private fun getOrCreatePrivateChatroomSync(user: User): Chatroom {
        checkNotInMainThread()

        return privateChatrooms[user] ?: run {
            val privateChatroom = Chatroom()
            privateChatrooms = privateChatrooms + Pair(user, privateChatroom)
            privateChatroom
        }
    }

    private fun getOrCreatePrivateChatroom(user: User): Chatroom {
        return privateChatrooms[user] ?: getOrCreatePrivateChatroomSync(user)
    }

    fun addMessage(user: User, msg: Note) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            liveSet?.messages?.invalidateData()
        }
    }

    fun removeMessage(user: User, msg: Note) {
        checkNotInMainThread()

        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg in privateChatroom.roomMessages) {
            privateChatroom.removeMessageSync(msg)
            liveSet?.messages?.invalidateData()
        }
    }

    fun addRelayBeingUsed(relay: Relay, eventTime: Long) {
        val here = relaysBeingUsed[relay.url]
        if (here == null) {
            relaysBeingUsed = relaysBeingUsed + Pair(relay.url, RelayInfo(relay.url, eventTime, 1))
        } else {
            if (eventTime > here.lastEvent) {
                here.lastEvent = eventTime
            }
            here.counter++
        }

        liveSet?.relayInfo?.invalidateData()
    }

    fun updateUserInfo(newUserInfo: UserMetadata, latestMetadata: MetadataEvent) {
        info = newUserInfo
        info?.latestMetadata = latestMetadata
        info?.updatedMetadataAt = latestMetadata.createdAt
        info?.tags = latestMetadata.tags.toImmutableListOfLists()

        if (newUserInfo.lud16.isNullOrBlank() && newUserInfo.lud06?.lowercase()?.startsWith("lnurl") == true) {
            try {
                val url = String(Bech32.decodeBytes(newUserInfo.lud06!!, false).second)

                val matcher = lnurlpPattern.matcher(url)
                while (matcher.find()) {
                    val domain = matcher.group(2)
                    val username = matcher.group(3)

                    info?.lud16 = "$username@$domain"
                }
            } catch (t: Throwable) {
                // Doesn't create errors.
            }
        }

        liveSet?.metadata?.invalidateData()
    }

    fun isFollowing(user: User): Boolean {
        return latestContactList?.isTaggedUser(user.pubkeyHex) ?: false
    }

    fun isFollowingHashtag(tag: String): Boolean {
        return latestContactList?.isTaggedHash(tag) ?: false
    }

    fun isFollowingHashtagCached(tag: String): Boolean {
        return latestContactList?.verifiedFollowTagSet?.let {
            return tag.lowercase() in it
        } ?: false
    }

    fun isFollowingGeohashCached(geoTag: String): Boolean {
        return latestContactList?.verifiedFollowGeohashSet?.let {
            return geoTag.lowercase() in it
        } ?: false
    }

    fun isFollowingCached(user: User): Boolean {
        return latestContactList?.verifiedFollowKeySet?.let {
            return user.pubkeyHex in it
        } ?: false
    }

    fun isFollowingCached(userHex: String): Boolean {
        return latestContactList?.verifiedFollowKeySet?.let {
            return userHex in it
        } ?: false
    }

    fun transientFollowCount(): Int? {
        return latestContactList?.unverifiedFollowKeySet()?.size
    }

    suspend fun transientFollowerCount(): Int {
        return LocalCache.users.values.count { it.latestContactList?.isTaggedUser(pubkeyHex) ?: false }
    }

    fun cachedFollowingKeySet(): Set<HexKey> {
        return latestContactList?.verifiedFollowKeySet ?: emptySet()
    }

    fun cachedFollowingTagSet(): Set<HexKey> {
        return latestContactList?.verifiedFollowTagSet ?: emptySet()
    }

    fun cachedFollowingGeohashSet(): Set<HexKey> {
        return latestContactList?.verifiedFollowGeohashSet ?: emptySet()
    }

    fun cachedFollowingCommunitiesSet(): Set<HexKey> {
        return latestContactList?.verifiedFollowCommunitySet ?: emptySet()
    }

    fun cachedFollowCount(): Int? {
        return latestContactList?.verifiedFollowKeySet?.size
    }

    suspend fun cachedFollowerCount(): Int {
        return LocalCache.users.values.count { it.latestContactList?.isTaggedUser(pubkeyHex) ?: false }
    }

    fun hasSentMessagesTo(user: User?): Boolean {
        val messagesToUser = privateChatrooms[user] ?: return false

        return messagesToUser.roomMessages.any { this.pubkeyHex == it.author?.pubkeyHex }
    }

    fun hasReport(loggedIn: User, type: ReportEvent.ReportType): Boolean {
        return reports[loggedIn]?.firstOrNull() {
            it.event is ReportEvent && (it.event as ReportEvent).reportedAuthor().any { it.reportType == type }
        } != null
    }

    fun anyNameStartsWith(username: String): Boolean {
        return info?.anyNameStartsWith(username) ?: false
    }

    var liveSet: UserLiveSet? = null

    fun live(): UserLiveSet {
        if (liveSet == null) {
            liveSet = UserLiveSet(this)
        }
        return liveSet!!
    }

    fun clearLive() {
        if (liveSet != null && liveSet?.isInUse() == false) {
            liveSet = null
        }
    }
}

class UserLiveSet(u: User) {
    // UI Observers line up here.
    val follows: UserLiveData = UserLiveData(u)
    val followers: UserLiveData = UserLiveData(u)
    val reports: UserLiveData = UserLiveData(u)
    val messages: UserLiveData = UserLiveData(u)
    val relays: UserLiveData = UserLiveData(u)
    val relayInfo: UserLiveData = UserLiveData(u)
    val metadata: UserLiveData = UserLiveData(u)
    val zaps: UserLiveData = UserLiveData(u)
    val bookmarks: UserLiveData = UserLiveData(u)

    fun isInUse(): Boolean {
        return follows.hasObservers() ||
            followers.hasObservers() ||
            reports.hasObservers() ||
            messages.hasObservers() ||
            relays.hasObservers() ||
            relayInfo.hasObservers() ||
            metadata.hasObservers() ||
            zaps.hasObservers() ||
            bookmarks.hasObservers()
    }
}

@Immutable
data class RelayInfo(
    val url: String,
    var lastEvent: Long,
    var counter: Long
)

class Chatroom() {
    var roomMessages: Set<Note> = setOf()

    @Synchronized
    fun addMessageSync(msg: Note) {
        checkNotInMainThread()

        if (msg !in roomMessages) {
            roomMessages = roomMessages + msg
        }
    }

    @Synchronized
    fun removeMessageSync(msg: Note) {
        checkNotInMainThread()

        if (msg !in roomMessages) {
            roomMessages = roomMessages + msg
        }
    }
}

@Stable
class UserMetadata {
    var name: String? = null
    var username: String? = null
    var display_name: String? = null
    var displayName: String? = null
    var picture: String? = null
    var banner: String? = null
    var website: String? = null
    var about: String? = null

    var nip05: String? = null
    var nip05Verified: Boolean = false
    var nip05LastVerificationTime: Long? = 0

    var domain: String? = null
    var lud06: String? = null
    var lud16: String? = null

    var publish: String? = null
    var iris: String? = null
    var main_relay: String? = null
    var twitter: String? = null

    var updatedMetadataAt: Long = 0
    var latestMetadata: MetadataEvent? = null
    var tags: ImmutableListOfLists<String>? = null

    fun anyName(): String? {
        return display_name ?: displayName ?: name ?: username
    }

    fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(name, username, display_name, displayName, nip05, lud06, lud16)
            .any { it.contains(prefix, true) }
    }

    fun lnAddress(): String? {
        return (lud16?.trim() ?: lud06?.trim())?.ifBlank { null }
    }

    fun bestUsername(): String? {
        return name?.ifBlank { null } ?: username?.ifBlank { null }
    }

    fun bestDisplayName(): String? {
        return displayName?.ifBlank { null } ?: display_name?.ifBlank { null }
    }

    fun nip05(): String? {
        return nip05?.ifBlank { null }
    }

    fun profilePicture(): String? {
        if (picture.isNullOrBlank()) picture = null
        return picture
    }
}

class UserLiveData(val user: User) : LiveData<UserState>(UserState(user)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate() {
            checkNotInMainThread()

            if (hasActiveObservers()) {
                postValue(UserState(user))
            }
        }
    }

    override fun onActive() {
        super.onActive()
        NostrSingleUserDataSource.add(user)
    }

    override fun onInactive() {
        super.onInactive()
        NostrSingleUserDataSource.remove(user)
    }
}

@Immutable
class UserState(val user: User)
