package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nostr.postr.events.ContactListEvent
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent

class User(val pubkey: ByteArray) {
    val pubkeyHex = pubkey.toHexKey()
    val pubkeyDisplayHex = pubkey.toShortenHex()

    var info = UserMetadata()

    var updatedMetadataAt: Long = 0;
    var updatedFollowsAt: Long = 0;

    var latestContactList: ContactListEvent? = null
    var latestMetadata: MetadataEvent? = null

    val notes = Collections.synchronizedSet(mutableSetOf<Note>())
    val follows = Collections.synchronizedSet(mutableSetOf<User>())

    val taggedPosts = Collections.synchronizedSet(mutableSetOf<Note>())

    var relays: Map<String, ContactListEvent.ReadWrite>? = null

    val followers = Collections.synchronizedSet(mutableSetOf<User>())
    val messages = ConcurrentHashMap<User, MutableSet<Note>>()

    fun toBestDisplayName(): String {
        return bestDisplayName() ?: bestUsername() ?: pubkeyDisplayHex
    }

    fun bestUsername(): String? {
        return info.name?.ifBlank { null } ?: info.username?.ifBlank { null }
    }

    fun bestDisplayName(): String? {
        return info.displayName?.ifBlank { null } ?: info.display_name?.ifBlank { null }
    }

    fun profilePicture(): String {
        if (info.picture.isNullOrBlank()) info.picture = null
        return info.picture ?: "https://robohash.org/${pubkeyHex}.png"
    }

    fun follow(user: User, followedAt: Long) {
        follows.add(user)
        user.followers.add(this)

        invalidateData()
        user.invalidateData()

        listeners.forEach {
            it.onFollowsChange()
        }
    }
    fun unfollow(user: User) {
        follows.remove(user)
        user.followers.remove(this)

        invalidateData()
        user.invalidateData()

        updateSubscribers {
            it.onFollowsChange()
        }
    }

    fun addTaggedPost(note: Note) {
        taggedPosts.add(note)
        updateSubscribers { it.onNewPosts() }
    }

    @Synchronized
    fun getOrCreateChannel(user: User): MutableSet<Note> {
        return messages[user] ?: run {
            val channel = mutableSetOf<Note>()
            messages[user] = channel
            channel
        }
    }

    fun addMessage(user: User, msg: Note) {
        getOrCreateChannel(user).add(msg)
        live.refresh()
        updateSubscribers { it.onNewMessage() }
    }

    fun updateFollows(newFollows: Set<User>, updateAt: Long) {
        val toBeAdded = synchronized(follows) {
            newFollows - follows
        }
        val toBeRemoved = synchronized(follows) {
            follows - newFollows
        }
        toBeAdded.forEach {
            follow(it, updateAt)
        }
        toBeRemoved.forEach {
            unfollow(it)
        }

        updatedFollowsAt = updateAt
    }

    fun updateRelays(relayUse: Map<String, ContactListEvent.ReadWrite>) {
        if (relays != relayUse) {
            relays = relayUse
            listeners.forEach {
                it.onRelayChange()
            }
        }
    }

    fun updateUserInfo(newUserInfo: UserMetadata, updateAt: Long) {
        info = newUserInfo
        updatedMetadataAt = updateAt

        invalidateData()
    }

    fun isFollowing(user: User): Boolean {
        return synchronized(follows) {
            follows.contains(user)
        }
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
        open fun onNewPosts() = Unit
        open fun onNewMessage() = Unit
    }

    // Refreshes observers in batches.
    var modelHandlerWaiting = false
    @Synchronized
    fun updateSubscribers(on: (Listener) -> Unit) {
        if (modelHandlerWaiting) return

        modelHandlerWaiting = true
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            delay(100)
            listeners.forEach {
                on(it)
            }
            modelHandlerWaiting = false
        }
    }

    // UI Observers line up here.
    val live: UserLiveData = UserLiveData(this)

    // Refreshes observers in batches.
    var handlerWaiting = false
    @Synchronized
    fun invalidateData() {
        if (handlerWaiting) return

        handlerWaiting = true
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            delay(100)
            live.refresh()
            handlerWaiting = false
        }
    }
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
    fun refresh() {
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
