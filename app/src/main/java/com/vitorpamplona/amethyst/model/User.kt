package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.relays.Relay
import kotlinx.coroutines.*
import nostr.postr.Bech32
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

val lnurlpPattern = Pattern.compile("(?i:http|https):\\/\\/((.+)\\/)*\\.well-known\\/lnurlp\\/(.*)")

class User(override val pubkeyHex: String) : UserInterface {
    override var info: UserMetadata? = null
    override var latestContactList: ContactListEvent? = null
    override var latestBookmarkList: BookmarkListEvent? = null
    override var notes = setOf<Note>()
    override var reports = mapOf<UserInterface, Set<Note>>()
    override var latestReportTime: Long = 0
    override var zaps = mapOf<Note, Note?>()
    override var relaysBeingUsed = mapOf<String, RelayInfo>()
    override var privateChatrooms = mapOf<UserInterface, Chatroom>()
    override var acceptedBadges: AddressableNote? = null
    override var liveSet: UserLiveSet? = null

    override fun toString(): String = pubkeyHex

    override fun toBestDisplayName(): String {
        return bestDisplayName() ?: bestUsername() ?: pubkeyDisplayHex()
    }

    override fun bestUsername(): String? {
        return info?.name?.ifBlank { null } ?: info?.username?.ifBlank { null }
    }

    override fun bestDisplayName(): String? {
        return info?.displayName?.ifBlank { null } ?: info?.display_name?.ifBlank { null }
    }

    override fun nip05(): String? {
        return info?.nip05?.ifBlank { null }
    }

    override fun profilePicture(): String? {
        if (info?.picture.isNullOrBlank()) info?.picture = null
        return info?.picture
    }

    override fun updateBookmark(event: BookmarkListEvent) {
        if (event.id == latestBookmarkList?.id) return

        latestBookmarkList = event
        liveSet?.bookmarks?.invalidateData()
    }

    override fun updateContactList(event: ContactListEvent) {
        if (event.id == latestContactList?.id) return

        val oldContactListEvent = latestContactList
        latestContactList = event

        // Update following of the current user
        liveSet?.follows?.invalidateData()

        // Update Followers of the past user list
        // Update Followers of the new contact list
        (oldContactListEvent)?.unverifiedFollowKeySet()?.forEach {
            LocalCache.users[it]?.liveSet?.follows?.invalidateData()
        }
        (latestContactList)?.unverifiedFollowKeySet()?.forEach {
            LocalCache.users[it]?.liveSet?.follows?.invalidateData()
        }

        liveSet?.relays?.invalidateData()
    }

    override fun addNote(note: Note) {
        if (note !in notes) {
            notes = notes + note
            // No need for Listener yet
        }
    }

    override fun removeNote(note: Note) {
        notes = notes - note
    }

    override fun clearNotes() {
        notes = setOf<Note>()
    }

    override fun addReport(note: Note) {
        val author = note.author ?: return

        if (author !in reports.keys) {
            reports = reports + Pair(author, setOf(note))
            liveSet?.reports?.invalidateData()
        } else if (reports[author]?.contains(note) == false) {
            reports = reports + Pair(author, (reports[author] ?: emptySet()) + note)
            liveSet?.reports?.invalidateData()
        }

        val reportTime = note.createdAt() ?: 0
        if (reportTime > latestReportTime) {
            latestReportTime = reportTime
        }
    }

    override fun removeReport(deleteNote: Note) {
        val author = deleteNote.author ?: return

        if (author in reports.keys && reports[author]?.contains(deleteNote) == true) {
            reports[author]?.let {
                reports = reports + Pair(author, it.minus(deleteNote))
                liveSet?.reports?.invalidateData()
            }
        }
    }

    override fun updateAcceptedBadges(note: AddressableNote) {
        acceptedBadges = note
        liveSet?.badges?.invalidateData()
    }

    override fun addZap(zapRequest: Note, zap: Note?) {
        if (zapRequest !in zaps.keys) {
            zaps = zaps + Pair(zapRequest, zap)
            liveSet?.zaps?.invalidateData()
        } else if (zapRequest in zaps.keys && zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            liveSet?.zaps?.invalidateData()
        }
    }

    override fun zappedAmount(): BigDecimal {
        return zaps.mapNotNull { it.value?.event }
            .filterIsInstance<LnZapEvent>()
            .mapNotNull {
                it.amount
            }.sumOf { it }
    }

    override fun reportsBy(user: UserInterface): Set<Note> {
        return reports[user] ?: emptySet()
    }

    override fun reportAuthorsBy(users: Set<HexKey>): List<UserInterface> {
        return reports.keys.filter { it.pubkeyHex in users }
    }

    override fun countReportAuthorsBy(users: Set<HexKey>): Int {
        return reports.keys.count { it.pubkeyHex in users }
    }

    override fun reportsBy(users: Set<HexKey>): List<Note> {
        return reportAuthorsBy(users).mapNotNull {
            reports[it]
        }.flatten()
    }

    @Synchronized
    override fun getOrCreatePrivateChatroom(user: UserInterface): Chatroom {
        return privateChatrooms[user] ?: run {
            val privateChatroom = Chatroom(setOf<Note>())
            privateChatrooms = privateChatrooms + Pair(user, privateChatroom)
            privateChatroom
        }
    }

    @Synchronized
    override fun addMessage(user: UserInterface, msg: Note) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.roomMessages = privateChatroom.roomMessages + msg
            liveSet?.messages?.invalidateData()
        }
    }

    @Synchronized
    override fun removeMessage(user: UserInterface, msg: Note) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg in privateChatroom.roomMessages) {
            privateChatroom.roomMessages = privateChatroom.roomMessages - msg
            liveSet?.messages?.invalidateData()
        }
    }

    override fun addRelayBeingUsed(relay: Relay, eventTime: Long) {
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

    override fun updateUserInfo(newUserInfo: UserMetadata, latestMetadata: MetadataEvent) {
        info = newUserInfo
        info?.latestMetadata = latestMetadata
        info?.updatedMetadataAt = latestMetadata.createdAt

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

    override fun isFollowing(user: UserInterface): Boolean {
        return latestContactList?.unverifiedFollowKeySet()?.toSet()?.let {
            return user.pubkeyHex in it
        } ?: false
    }

    override fun isFollowingHashtag(tag: String): Boolean {
        return latestContactList?.unverifiedFollowTagSet()?.toSet()?.let {
            return tag in it
        } ?: false
    }

    override fun isFollowingHashtagCached(tag: String): Boolean {
        return latestContactList?.verifiedFollowTagSet?.let {
            return tag.lowercase() in it
        } ?: false
    }

    override fun isFollowingCached(user: UserInterface): Boolean {
        return latestContactList?.verifiedFollowKeySet?.let {
            return user.pubkeyHex in it
        } ?: false
    }

    override fun transientFollowCount(): Int? {
        return latestContactList?.unverifiedFollowKeySet()?.size
    }

    override fun transientFollowerCount(): Int {
        return LocalCache.users.values.count { it.latestContactList?.let { pubkeyHex in it.unverifiedFollowKeySet() } ?: false }
    }

    override fun cachedFollowingKeySet(): Set<HexKey> {
        return latestContactList?.verifiedFollowKeySet ?: emptySet()
    }

    override fun cachedFollowingTagSet(): Set<HexKey> {
        return latestContactList?.verifiedFollowTagSet ?: emptySet()
    }

    override fun cachedFollowCount(): Int? {
        return latestContactList?.verifiedFollowKeySet?.size
    }

    override fun cachedFollowerCount(): Int {
        return LocalCache.users.values.count { it.latestContactList?.let { pubkeyHex in it.unverifiedFollowKeySet() } ?: false }
    }

    override fun hasSentMessagesTo(user: UserInterface?): Boolean {
        val messagesToUser = privateChatrooms[user] ?: return false

        return messagesToUser.roomMessages.any { this == it.author }
    }

    override fun hasReport(loggedIn: UserInterface, type: ReportEvent.ReportType): Boolean {
        return reports[loggedIn]?.firstOrNull() {
            it.event is ReportEvent && (it.event as ReportEvent).reportedAuthor().any { it.reportType == type }
        } != null
    }

    override fun anyNameStartsWith(username: String): Boolean {
        return info?.anyNameStartsWith(username) ?: false
    }

    override fun live(): UserLiveSet {
        if (liveSet == null) {
            liveSet = UserLiveSet(this)
        }
        return liveSet!!
    }

    override fun clearLive() {
        if (liveSet != null && liveSet?.isInUse() == false) {
            liveSet = null
        }
    }
}

class UserLiveSet(u: UserInterface) {
    // UI Observers line up here.
    val follows: UserLiveData = UserLiveData(u)
    val reports: UserLiveData = UserLiveData(u)
    val messages: UserLiveData = UserLiveData(u)
    val relays: UserLiveData = UserLiveData(u)
    val relayInfo: UserLiveData = UserLiveData(u)
    val metadata: UserLiveData = UserLiveData(u)
    val zaps: UserLiveData = UserLiveData(u)
    val badges: UserLiveData = UserLiveData(u)
    val bookmarks: UserLiveData = UserLiveData(u)

    fun isInUse(): Boolean {
        return follows.hasObservers() ||
            reports.hasObservers() ||
            messages.hasObservers() ||
            relays.hasObservers() ||
            relayInfo.hasObservers() ||
            metadata.hasObservers() ||
            zaps.hasObservers() ||
            badges.hasObservers() ||
            bookmarks.hasObservers()
    }
}

data class RelayInfo(
    val url: String,
    var lastEvent: Long,
    var counter: Long
)

data class Chatroom(var roomMessages: Set<Note>)

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

    fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(name, username, display_name, displayName, nip05, lud06, lud16)
            .any { it.startsWith(prefix, true) }
    }
}

class UserLiveData(val user: UserInterface) : LiveData<UserState>(UserState(user)) {

    // Refreshes observers in batches.
    var handlerWaiting = AtomicBoolean()

    fun invalidateData() {
        if (handlerWaiting.getAndSet(true)) return

        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            try {
                delay(100)
                refresh()
            } finally {
                withContext(NonCancellable) {
                    handlerWaiting.set(false)
                }
            }
        }
    }

    private fun refresh() {
        postValue(UserState(user))
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

class UserState(val user: UserInterface)
