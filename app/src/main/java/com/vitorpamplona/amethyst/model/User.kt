package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.quartz.encoders.Bech32
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentSetOf
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

    var privateChatrooms = mapOf<ChatroomKey, Chatroom>()
        private set

    fun pubkey() = Hex.decode(pubkeyHex)
    fun pubkeyNpub() = pubkey().toNpub()

    fun pubkeyDisplayHex() = pubkeyNpub().toShortenHex()

    fun toNostrUri() = "nostr:${pubkeyNpub()}"

    override fun toString(): String = pubkeyHex

    fun toBestShortFirstName(): String {
        val fullName = bestDisplayName() ?: bestUsername() ?: return pubkeyDisplayHex()

        val names = fullName.split(' ')

        val firstName = if (names[0].length <= 3) {
            // too short. Remove Dr.
            "${names[0]} ${names.getOrNull(1) ?: ""}"
        } else {
            names[0]
        }

        return firstName
    }

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

    fun removeZap(zapRequestOrZapEvent: Note) {
        if (zapRequestOrZapEvent in zaps.keys) {
            zaps = zaps.minus(zapRequestOrZapEvent)
            liveSet?.zaps?.invalidateData()
        } else if (zapRequestOrZapEvent in zaps.values) {
            zaps = zaps.filter { it.value != zapRequestOrZapEvent }
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
    private fun getOrCreatePrivateChatroomSync(key: ChatroomKey): Chatroom {
        checkNotInMainThread()

        return privateChatrooms[key] ?: run {
            val privateChatroom = Chatroom()
            privateChatrooms = privateChatrooms + Pair(key, privateChatroom)
            privateChatroom
        }
    }

    private fun getOrCreatePrivateChatroom(user: User): Chatroom {
        val key = ChatroomKey(persistentSetOf(user.pubkeyHex))
        return getOrCreatePrivateChatroom(key)
    }

    private fun getOrCreatePrivateChatroom(key: ChatroomKey): Chatroom {
        return privateChatrooms[key] ?: getOrCreatePrivateChatroomSync(key)
    }

    fun addMessage(room: ChatroomKey, msg: Note) {
        val privateChatroom = getOrCreatePrivateChatroom(room)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            liveSet?.messages?.invalidateData()
        }
    }

    fun addMessage(user: User, msg: Note) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            liveSet?.messages?.invalidateData()
        }
    }

    fun createChatroom(withKey: ChatroomKey) {
        getOrCreatePrivateChatroom(withKey)
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

    fun hasSentMessagesTo(key: ChatroomKey?): Boolean {
        val messagesToUser = privateChatrooms[key] ?: return false

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
            liveSet?.destroy()
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

    fun destroy() {
        follows.destroy()
        followers.destroy()
        reports.destroy()
        messages.destroy()
        relays.destroy()
        relayInfo.destroy()
        metadata.destroy()
        zaps.destroy()
        bookmarks.destroy()
    }
}

@Immutable
data class RelayInfo(
    val url: String,
    var lastEvent: Long,
    var counter: Long
)

@Stable
class Chatroom() {
    var roomMessages: Set<Note> = setOf()
    var subject: String? = null
    var subjectCreatedAt: Long? = null

    @Synchronized
    fun addMessageSync(msg: Note) {
        checkNotInMainThread()

        if (msg !in roomMessages) {
            roomMessages = roomMessages + msg

            val newSubject = msg.event?.subject()

            if (newSubject != null && (msg.createdAt() ?: 0) > (subjectCreatedAt ?: 0)) {
                subject = newSubject
                subjectCreatedAt = msg.createdAt()
            }
        }
    }

    @Synchronized
    fun removeMessageSync(msg: Note) {
        checkNotInMainThread()

        if (msg !in roomMessages) {
            roomMessages = roomMessages + msg

            roomMessages.filter { it.event?.subject() != null }.sortedBy { it.createdAt() }.lastOrNull()?.let {
                subject = it.event?.subject()
                subjectCreatedAt = it.createdAt()
            }
        }
    }

    fun senderIntersects(keySet: Set<HexKey>): Boolean {
        return roomMessages.any { it.author?.pubkeyHex in keySet }
    }

    fun pruneMessagesToTheLatestOnly(): Set<Note> {
        val sorted = roomMessages.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()

        val toKeep = if ((sorted.firstOrNull()?.createdAt() ?: 0) > TimeUtils.oneWeekAgo()) {
            // Recent messages, keep last 100
            sorted.take(100).toSet()
        } else {
            // Old messages, keep the last one.
            sorted.take(1).toSet()
        } + sorted.filter { it.liveSet?.isInUse() ?: false }

        val toRemove = roomMessages.minus(toKeep)
        roomMessages = toKeep
        return toRemove
    }
}

class UserLiveData(val user: User) : LiveData<UserState>(UserState(user)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    fun destroy() {
        bundler.cancel()
    }

    fun invalidateData() {
        if (!hasObservers()) return
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
