package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.Bech32
import nostr.postr.events.ContactListEvent
import nostr.postr.events.MetadataEvent
import nostr.postr.toNpub

val lnurlpPattern = Pattern.compile("(?i:http|https):\\/\\/((.+)\\/)*\\.well-known\\/lnurlp\\/(.*)")

class User(val pubkeyHex: String) {
    var info: UserMetadata? = null

    var updatedFollowsAt: Long = 0;
    var latestContactList: ContactListEvent? = null

    var follows = setOf<User>()
        private set
    var followers = setOf<User>()
        private set

    var notes = setOf<Note>()
        private set
    var taggedPosts = setOf<Note>()
        private set

    var reports = mapOf<User, Set<Note>>()
        private set
    var latestReportTime: Long = 0

    var zaps = mapOf<Note, Note?>()
        private set

    var relays: Map<String, ContactListEvent.ReadWrite>? = null
        private set

    var relaysBeingUsed = mapOf<String, RelayInfo>()
        private set

    var privateChatrooms = mapOf<User, Chatroom>()
        private set

    fun pubkey() = Hex.decode(pubkeyHex)
    fun pubkeyNpub() = pubkey().toNpub()
    fun pubkeyDisplayHex() = pubkeyNpub().toShortenHex()

    fun toBestDisplayName(): String {
        return bestDisplayName() ?: bestUsername() ?: pubkeyDisplayHex()
    }

    fun bestUsername(): String? {
        return info?.name?.ifBlank { null } ?: info?.username?.ifBlank { null }
    }

    fun bestDisplayName(): String? {
        return info?.displayName?.ifBlank { null } ?: info?.display_name?.ifBlank { null }
    }

    fun profilePicture(): String? {
        if (info?.picture.isNullOrBlank()) info?.picture = null
        return info?.picture
    }

    fun follow(user: User, followedAt: Long) {
        follows = follows + user
        user.followers = user.followers + this

        liveSet?.follows?.invalidateData()
        user.liveSet?.follows?.invalidateData()
    }

    fun unfollow(user: User) {
        follows = follows - user
        user.followers = user.followers - this

        liveSet?.follows?.invalidateData()
        user.liveSet?.follows?.invalidateData()
    }

    fun follow(users: Set<User>, followedAt: Long) {
        follows = follows + users
        users.forEach {
            if (this !in it.followers && it.liveSet?.isInUse() == true) {
                it.followers = it.followers + this
                it.liveSet?.follows?.invalidateData()
            }
        }

        liveSet?.follows?.invalidateData()
    }

    fun unfollow(users: Set<User>) {
        follows = follows - users
        users.forEach {
            if (this in it.followers && it.liveSet?.isInUse() == true) {
                it.followers = it.followers - this
                it.liveSet?.follows?.invalidateData()
            }
        }
        liveSet?.follows?.invalidateData()
    }

    fun addTaggedPost(note: Note) {
        if (note !in taggedPosts) {
            taggedPosts = taggedPosts + note
            // No need for Listener yet
        }
    }

    fun removeTaggedPost(note: Note) {
        taggedPosts = taggedPosts - note
    }

    fun addNote(note: Note) {
        if (note !in notes) {
            notes = notes + note
            // No need for Listener yet
        }
    }

    fun clearNotes() {
        notes = setOf<Note>()
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

        val reportTime = note.event?.createdAt ?: 0
        if (reportTime > latestReportTime) {
            latestReportTime = reportTime
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

    fun reportAuthorsBy(users: Set<User>): List<User> {
        return reports.keys.filter { it in users }
    }

    fun reportsBy(users: Set<User>): List<Note> {
        return reportAuthorsBy(users).mapNotNull {
            reports[it]
        }.flatten()
    }

    @Synchronized
    fun getOrCreatePrivateChatroom(user: User): Chatroom {
        return privateChatrooms[user] ?: run {
            val privateChatroom = Chatroom(setOf<Note>())
            privateChatrooms = privateChatrooms + Pair(user, privateChatroom)
            privateChatroom
        }
    }

    fun addMessage(user: User, msg: Note) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.roomMessages = privateChatroom.roomMessages + msg
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
    
    fun updateFollows(newFollows: Set<User>, updateAt: Long) {
        val toBeAdded = newFollows - follows
        val toBeRemoved = follows - newFollows

        follow(toBeAdded, updateAt)
        unfollow(toBeRemoved)

        updatedFollowsAt = updateAt
    }

    fun updateRelays(relayUse: Map<String, ContactListEvent.ReadWrite>) {
        // no need to test if relays are different. The Account will check for us.
        relays = relayUse
        liveSet?.relays?.invalidateData()
    }

    fun updateUserInfo(newUserInfo: UserMetadata, latestMetadata: MetadataEvent) {
        info = newUserInfo
        info?.latestMetadata = latestMetadata
        info?.updatedMetadataAt = latestMetadata.createdAt

        if (newUserInfo.lud16.isNullOrBlank() && newUserInfo.lud06?.toLowerCase()?.startsWith("lnurl") == true) {
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
        return follows.contains(user)
    }

    fun hasSentMessagesTo(user: User?): Boolean {
        val messagesToUser = privateChatrooms[user] ?: return false

        return messagesToUser.roomMessages.any { this == it.author }
    }

    fun hasReport(loggedIn: User, type: ReportEvent.ReportType): Boolean {
        return reports[loggedIn]?.firstOrNull() {
              it.event is ReportEvent && (it.event as ReportEvent).reportType.contains(type)
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
    val reports: UserLiveData = UserLiveData(u)
    val messages: UserLiveData = UserLiveData(u)
    val relays: UserLiveData = UserLiveData(u)
    val relayInfo: UserLiveData = UserLiveData(u)
    val metadata: UserLiveData = UserLiveData(u)
    val zaps: UserLiveData = UserLiveData(u)

    fun isInUse(): Boolean {
        return follows.hasObservers()
          || reports.hasObservers()
          || messages.hasObservers()
          || relays.hasObservers()
          || relayInfo.hasObservers()
          || metadata.hasObservers()
          || zaps.hasObservers()
    }
}

data class RelayInfo (
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
    var domain: String? = null
    var lud06: String? = null
    var lud16: String? = null

    var publish: String? = null
    var iris: String? = null
    var main_relay: String? = null
    var twitter: String? = null

    var updatedMetadataAt: Long = 0;
    var latestMetadata: MetadataEvent? = null

    fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(name, username, display_name, displayName, nip05, lud06, lud16)
              .filter { it.startsWith(prefix, true) }.isNotEmpty()
    }
}

class UserLiveData(val user: User): LiveData<UserState>(UserState(user)) {

    // Refreshes observers in batches.
    var handlerWaiting = AtomicBoolean()

    @Synchronized
    fun invalidateData() {
        if (handlerWaiting.getAndSet(true)) return

        handlerWaiting.set(true)
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

class UserState(val user: User)

