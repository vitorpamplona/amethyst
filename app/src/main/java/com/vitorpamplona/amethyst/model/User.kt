package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
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

    var reports = setOf<Note>()
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

        updateSubscribers {
            it.onFollowsChange()
        }

        user.updateSubscribers {
            it.onFollowsChange()
        }
    }
    fun unfollow(user: User) {
        follows = follows - user
        user.followers = user.followers - this

        liveFollows.invalidateData()
        user.liveFollows.invalidateData()

        updateSubscribers {
            it.onFollowsChange()
        }

        user.updateSubscribers {
            it.onFollowsChange()
        }
    }

    fun follow(users: Set<User>, followedAt: Long) {
        follows = follows + users
        users.forEach {
            it.followers = it.followers + this
            it.liveFollows.invalidateData()
            it.updateSubscribers {
                it.onFollowsChange()
            }
        }

        liveFollows.invalidateData()
        updateSubscribers {
            it.onFollowsChange()
        }
    }
    fun unfollow(users: Set<User>) {
        follows = follows - users
        users.forEach {
            it.followers = it.followers - this
            it.liveFollows.invalidateData()
            it.updateSubscribers {
                it.onFollowsChange()
            }
        }
        liveFollows.invalidateData()
        updateSubscribers {
            it.onFollowsChange()
        }
    }

    fun addTaggedPost(note: Note) {
        if (note !in taggedPosts) {
            taggedPosts = taggedPosts + note
            updateSubscribers { it.onNewTaggedPosts() }
        }
    }

    fun addNote(note: Note) {
        if (note !in notes) {
            notes = notes + note
            updateSubscribers { it.onNewNotes() }
        }
    }

    fun addReport(note: Note) {
        if (note !in reports) {
            reports = reports + note
            liveReports.invalidateData()
        }
    }

    fun reportsBy(user: User): List<Note> {
        return reports.filter { it.author == user }
    }

    fun reportsBy(users: Set<User>): List<Note> {
        return reports.filter { it.author in users }
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
            updateSubscribers { it.onNewMessage() }
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

        updateSubscribers { it.onNewRelayInfo() }
        liveRelayInfo.invalidateData()
    }
    
    fun updateFollows(newFollows: Set<User>, updateAt: Long) {
        val toBeAdded = newFollows - follows
        val toBeRemoved = follows - newFollows

        follow(toBeAdded, updateAt)
        unfollow(toBeRemoved)

        updatedFollowsAt = updateAt
    }

    data class RelayMetadata(val read: Boolean, val write: Boolean, val activeTypes: Set<FeedType>)

    fun updateRelays(relayUse: Map<String, ContactListEvent.ReadWrite>) {
        if (relays != relayUse) {
            relays = relayUse
            listeners.forEach {
                it.onRelayChange()
            }
        }

        liveRelays.invalidateData()
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
        return reports.firstOrNull {
            it.author == loggedIn
              && it.event is ReportEvent
              && (it.event as ReportEvent).reportType.contains(type)
        } != null
    }

    // Model Observers
    private var listeners = setOf<Listener>()

    fun subscribe(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun unsubscribe(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    abstract class Listener {
        open fun onRelayChange() = Unit
        open fun onFollowsChange() = Unit
        open fun onNewTaggedPosts() = Unit
        open fun onNewNotes() = Unit
        open fun onNewMessage() = Unit
        open fun onNewRelayInfo() = Unit
        open fun onNewReports() = Unit
    }

    // Refreshes observers in batches.
    var modelHandlerWaiting = AtomicBoolean()

    @Synchronized
    fun updateSubscribers(on: (Listener) -> Unit) {
        if (modelHandlerWaiting.getAndSet(true)) return

        modelHandlerWaiting.set(true)
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            delay(100)
            listeners.forEach {
                on(it)
            }
            modelHandlerWaiting.set(false)
        }
    }

    // UI Observers line up here.
    val liveFollows: UserLiveData = UserLiveData(this)
    val liveReports: UserLiveData = UserLiveData(this)
    val liveMessages: UserLiveData = UserLiveData(this)
    val liveRelays: UserLiveData = UserLiveData(this)
    val liveRelayInfo: UserLiveData = UserLiveData(this)
    val liveMetadata: UserLiveData = UserLiveData(this)
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
