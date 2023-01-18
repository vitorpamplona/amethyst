package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nostr.postr.events.ContactListEvent

class User(val pubkey: ByteArray) {
    val pubkeyHex = pubkey.toHexKey()
    val pubkeyDisplayHex = pubkey.toShortenHex()

    var info = UserMetadata()

    var updatedMetadataAt: Long = 0;
    var updatedFollowsAt: Long = 0;

    var lastestContactList: ContactListEvent? = null

    val notes = Collections.synchronizedSet(mutableSetOf<Note>())
    val follows = Collections.synchronizedSet(mutableSetOf<User>())
    val taggedPosts = Collections.synchronizedSet(mutableSetOf<Note>())

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

    fun follow(user: User) {
        follows.add(user)
        user.followers.add(this)
    }
    fun unfollow(user: User) {
        follows.remove(user)
        user.followers.remove(this)
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
    }

    fun updateFollows(newFollows: List<User>, updateAt: Long) {
        val toBeAdded = synchronized(follows) {
            newFollows - follows
        }
        val toBeRemoved = synchronized(follows) {
            follows - newFollows
        }
        toBeAdded.forEach {
            follow(it)
        }
        toBeRemoved.forEach {
            unfollow(it)
        }

        updatedFollowsAt = updateAt

        refreshObservers()
    }

    fun updateUserInfo(newUserInfo: UserMetadata, updateAt: Long) {
        info = newUserInfo
        updatedMetadataAt = updateAt

        refreshObservers()
    }

    fun isFollowing(user: User): Boolean {
        return synchronized(follows) {
            follows.contains(user)
        }
    }

    // Observers line up here.
    val live: UserLiveData = UserLiveData(this)

    private fun refreshObservers() {
        live.refresh()
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
    val scope = CoroutineScope(Job() + Dispatchers.Main)

    fun refresh() {
        postValue(UserState(user))
    }

    override fun onActive() {
        super.onActive()
        scope.launch {
            NostrSingleUserDataSource.add(user.pubkeyHex)
        }
    }

    override fun onInactive() {
        super.onInactive()
        scope.launch {
            NostrSingleUserDataSource.remove(user.pubkeyHex)
        }
    }
}

class UserState(val user: User)
