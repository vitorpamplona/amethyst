package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.ui.note.toDisplayHex
import java.util.Collections

class User(val pubkey: ByteArray) {
    val pubkeyHex = pubkey.toHexKey()
    val pubkeyDisplayHex = pubkey.toDisplayHex()

    var info = UserMetadata()

    var updatedMetadataAt: Long = 0;
    var updatedFollowsAt: Long = 0;

    val notes = Collections.synchronizedSet(mutableSetOf<Note>())
    val follows = Collections.synchronizedSet(mutableSetOf<User>())
    val taggedPosts = Collections.synchronizedSet(mutableSetOf<Note>())

    var follower: Number? = null

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

    fun updateFollows(newFollows: List<User>, updateAt: Long) {
        follows.clear()
        follows.addAll(newFollows)
        updatedFollowsAt = updateAt

        live.refresh()
    }

    fun updateUserInfo(newUserInfo: UserMetadata, updateAt: Long) {
        info = newUserInfo
        updatedMetadataAt = updateAt

        live.refresh()
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
