package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import java.math.BigDecimal
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nostr.postr.events.ContactListEvent
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent
import nostr.postr.toNpub

class User(val pubkeyHex: String) {
    val pubkey = Hex.decode(pubkeyHex)
    val pubkeyDisplayHex = pubkey.toNpub().toShortenHex()

    var info = UserMetadata()

    var updatedMetadataAt: Long = 0;
    var updatedFollowsAt: Long = 0;

    var latestContactList: ContactListEvent? = null
    var latestMetadata: MetadataEvent? = null

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

    var zaps = mapOf<Note, Note?>()
        private set

    var relays: Map<String, ContactListEvent.ReadWrite>? = null
        private set

    var relaysBeingUsed = mapOf<String, RelayInfo>()
        private set

    var messages = mapOf<User, Set<Note>>()
        private set

    var latestMetadataRequestEOSE: Long? = null
    var latestReportRequestEOSE: Long? = null

    fun toBestDisplayName(): String {
        return bestDisplayName() ?: bestUsername() ?: pubkeyDisplayHex
    }

    fun bestUsername(): String? {
        return info.name?.ifBlank { null } ?: info.username?.ifBlank { null }
    }

    fun bestDisplayName(): String? {
        return info.displayName?.ifBlank { null } ?: info.display_name?.ifBlank { null }
    }

    fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) info.picture = null
        return info.picture
    }

    fun follow(user: User, followedAt: Long) {
        follows = follows + user
        user.followers = user.followers + this

        liveFollows.invalidateData()
        user.liveFollows.invalidateData()
    }

    fun unfollow(user: User) {
        follows = follows - user
        user.followers = user.followers - this

        liveFollows.invalidateData()
        user.liveFollows.invalidateData()
    }

    fun follow(users: Set<User>, followedAt: Long) {
        follows = follows + users
        users.forEach {
            it.followers = it.followers + this
            it.liveFollows.invalidateData()
        }

        liveFollows.invalidateData()
    }

    fun unfollow(users: Set<User>) {
        follows = follows - users
        users.forEach {
            it.followers = it.followers - this
            it.liveFollows.invalidateData()
        }
        liveFollows.invalidateData()
    }

    fun addTaggedPost(note: Note) {
        if (note !in taggedPosts) {
            taggedPosts = taggedPosts + note
            // No need for Listener yet
        }
    }

    fun addNote(note: Note) {
        if (note !in notes) {
            notes = notes + note
            // No need for Listener yet
        }
    }

    fun addReport(note: Note) {
        val author = note.author ?: return

        if (author !in reports.keys) {
            reports = reports + Pair(author, setOf(note))
            liveReports.invalidateData()
        } else {
            reports = reports + Pair(author, (reports[author] ?: emptySet()) + note)
            liveReports.invalidateData()
        }
    }

    fun addZap(zapRequest: Note, zap: Note?) {
        if (zapRequest !in zaps.keys) {
            zaps = zaps + Pair(zapRequest, zap)
            liveZaps.invalidateData()
        } else if (zapRequest in zaps.keys && zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            liveZaps.invalidateData()
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
    fun getOrCreateChannel(user: User): Set<Note> {
        return messages[user] ?: run {
            val channel = setOf<Note>()
            messages = messages + Pair(user, channel)
            channel
        }
    }

    fun addMessage(user: User, msg: Note) {
        val channel = getOrCreateChannel(user)
        if (msg !in channel) {
            messages = messages + Pair(user, channel + msg)
            liveMessages.invalidateData()
        }
    }

    data class RelayInfo (
        val url: String,
        var lastEvent: Long,
        var counter: Long
    )

    fun addRelay(relay: Relay, eventTime: Long) {
        val here = relaysBeingUsed[relay.url]
        if (here == null) {
            relaysBeingUsed = relaysBeingUsed + Pair(relay.url, RelayInfo(relay.url, eventTime, 1))
        } else {
            if (eventTime > here.lastEvent) {
                here.lastEvent = eventTime
            }
            here.counter++
        }

        liveRelayInfo.invalidateData()
    }
    
    fun updateFollows(newFollows: Set<User>, updateAt: Long) {
        val toBeAdded = newFollows - follows
        val toBeRemoved = follows - newFollows

        follow(toBeAdded, updateAt)
        unfollow(toBeRemoved)

        updatedFollowsAt = updateAt
    }

    fun updateRelays(relayUse: Map<String, ContactListEvent.ReadWrite>) {
        if (relays != relayUse) {
            relays = relayUse
            liveRelays.invalidateData()
        }
    }

    fun updateUserInfo(newUserInfo: UserMetadata, updateAt: Long) {
        info = newUserInfo
        updatedMetadataAt = updateAt

        liveMetadata.invalidateData()
    }

    fun isFollowing(user: User): Boolean {
        return follows.contains(user)
    }

    fun hasSentMessagesTo(user: User?): Boolean {
        val messagesToUser = messages[user] ?: return false

        return messagesToUser.firstOrNull { this == it.author } != null
    }

    fun hasReport(loggedIn: User, type: ReportEvent.ReportType): Boolean {
        return reports[loggedIn]?.firstOrNull() {
              it.event is ReportEvent && (it.event as ReportEvent).reportType.contains(type)
        } != null
    }

    // UI Observers line up here.
    val liveFollows: UserLiveData = UserLiveData(this)
    val liveReports: UserLiveData = UserLiveData(this)
    val liveMessages: UserLiveData = UserLiveData(this)
    val liveRelays: UserLiveData = UserLiveData(this)
    val liveRelayInfo: UserLiveData = UserLiveData(this)
    val liveMetadata: UserLiveData = UserLiveData(this)
    val liveZaps: UserLiveData = UserLiveData(this)
}

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
        if (!hasActiveObservers()) return
        if (handlerWaiting.getAndSet(true)) return

        handlerWaiting.set(true)
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            delay(100)
            refresh()
            handlerWaiting.set(false)
        }
    }

    private fun refresh() {
        postValue(UserState(user))
    }

    override fun onActive() {
        super.onActive()
        NostrSingleUserDataSource.add(user.pubkeyHex)
    }

    override fun onInactive() {
        super.onInactive()
        NostrSingleUserDataSource.remove(user.pubkeyHex)
    }
}

class UserState(val user: User)
