/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Lud06
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

@Stable
class User(val pubkeyHex: String) {
    var info: UserMetadata? = null

    var latestMetadata: MetadataEvent? = null
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
        val fullName = toBestDisplayName()

        val names = fullName.split(' ')

        val firstName =
            if (names[0].length <= 3) {
                // too short. Remove Dr.
                "${names[0]} ${names.getOrNull(1) ?: ""}"
            } else {
                names[0]
            }

        return firstName
    }

    fun toBestDisplayName(): String {
        return info?.bestName() ?: pubkeyDisplayHex()
    }

    fun nip05(): String? {
        return info?.nip05
    }

    fun profilePicture(): String? {
        return info?.picture
    }

    fun updateBookmark(event: BookmarkListEvent) {
        if (event.id == latestBookmarkList?.id) return

        latestBookmarkList = event
        liveSet?.innerBookmarks?.invalidateData()
    }

    fun clearEOSE() {
        latestEOSEs = emptyMap()
    }

    fun updateContactList(event: ContactListEvent) {
        if (event.id == latestContactList?.id) return

        val oldContactListEvent = latestContactList
        latestContactList = event

        // Update following of the current user
        liveSet?.innerFollows?.invalidateData()
        flowSet?.follows?.invalidateData()

        // Update Followers of the past user list
        // Update Followers of the new contact list
        (oldContactListEvent)?.unverifiedFollowKeySet()?.forEach {
            LocalCache.getUserIfExists(it)?.liveSet?.innerFollowers?.invalidateData()
        }
        (latestContactList)?.unverifiedFollowKeySet()?.forEach {
            LocalCache.getUserIfExists(it)?.liveSet?.innerFollowers?.invalidateData()
        }

        liveSet?.innerRelays?.invalidateData()
        flowSet?.relays?.invalidateData()
    }

    fun addReport(note: Note) {
        val author = note.author ?: return

        val reportsBy = reports[author]
        if (reportsBy == null) {
            reports = reports + Pair(author, setOf(note))
            liveSet?.innerReports?.invalidateData()
        } else if (!reportsBy.contains(note)) {
            reports = reports + Pair(author, reportsBy + note)
            liveSet?.innerReports?.invalidateData()
        }
    }

    fun removeReport(deleteNote: Note) {
        val author = deleteNote.author ?: return

        if (reports[author]?.contains(deleteNote) == true) {
            reports[author]?.let {
                reports = reports + Pair(author, it.minus(deleteNote))
                liveSet?.innerReports?.invalidateData()
            }
        }
    }

    fun addZap(
        zapRequest: Note,
        zap: Note?,
    ) {
        if (zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            liveSet?.innerZaps?.invalidateData()
        }
    }

    fun removeZap(zapRequestOrZapEvent: Note) {
        if (zaps.containsKey(zapRequestOrZapEvent)) {
            zaps = zaps.minus(zapRequestOrZapEvent)
            liveSet?.innerZaps?.invalidateData()
        } else if (zaps.containsValue(zapRequestOrZapEvent)) {
            zaps = zaps.filter { it.value != zapRequestOrZapEvent }
            liveSet?.innerZaps?.invalidateData()
        }
    }

    fun zappedAmount(): BigDecimal {
        var amount = BigDecimal.ZERO
        zaps.forEach {
            val itemValue = (it.value?.event as? LnZapEvent)?.amount
            if (itemValue != null) {
                amount += itemValue
            }
        }

        return amount
    }

    fun reportsBy(user: User): Set<Note> {
        return reports[user] ?: emptySet()
    }

    fun countReportAuthorsBy(users: Set<HexKey>): Int {
        return reports.count { it.key.pubkeyHex in users }
    }

    fun reportsBy(users: Set<HexKey>): List<Note> {
        return reports
            .mapNotNull {
                if (it.key.pubkeyHex in users) {
                    it.value
                } else {
                    null
                }
            }
            .flatten()
    }

    @Synchronized
    private fun getOrCreatePrivateChatroomSync(key: ChatroomKey): Chatroom {
        checkNotInMainThread()

        return privateChatrooms[key]
            ?: run {
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

    fun addMessage(
        room: ChatroomKey,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(room)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            liveSet?.innerMessages?.invalidateData()
        }
    }

    fun addMessage(
        user: User,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            liveSet?.innerMessages?.invalidateData()
        }
    }

    fun createChatroom(withKey: ChatroomKey) {
        getOrCreatePrivateChatroom(withKey)
    }

    fun removeMessage(
        user: User,
        msg: Note,
    ) {
        checkNotInMainThread()

        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg in privateChatroom.roomMessages) {
            privateChatroom.removeMessageSync(msg)
            liveSet?.innerMessages?.invalidateData()
        }
    }

    fun removeMessage(
        room: ChatroomKey,
        msg: Note,
    ) {
        checkNotInMainThread()
        val privateChatroom = getOrCreatePrivateChatroom(room)
        if (msg in privateChatroom.roomMessages) {
            privateChatroom.removeMessageSync(msg)
            liveSet?.innerMessages?.invalidateData()
        }
    }

    fun addRelayBeingUsed(
        relay: Relay,
        eventTime: Long,
    ) {
        val here = relaysBeingUsed[relay.url]
        if (here == null) {
            relaysBeingUsed = relaysBeingUsed + Pair(relay.url, RelayInfo(relay.url, eventTime, 1))
        } else {
            if (eventTime > here.lastEvent) {
                here.lastEvent = eventTime
            }
            here.counter++
        }

        liveSet?.innerRelayInfo?.invalidateData()
    }

    fun updateUserInfo(
        newUserInfo: UserMetadata,
        latestMetadata: MetadataEvent,
    ) {
        info = newUserInfo
        info?.tags = latestMetadata.tags.toImmutableListOfLists()
        info?.cleanBlankNames()

        if (newUserInfo.lud16.isNullOrBlank()) {
            info?.lud06?.let {
                if (it.lowercase().startsWith("lnurl")) {
                    info?.lud16 = Lud06().toLud16(it)
                }
            }
        }

        liveSet?.innerMetadata?.invalidateData()
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
        }
            ?: false
    }

    fun isFollowingGeohashCached(geoTag: String): Boolean {
        return latestContactList?.verifiedFollowGeohashSet?.let {
            return geoTag.lowercase() in it
        }
            ?: false
    }

    fun isFollowingCached(user: User): Boolean {
        return latestContactList?.verifiedFollowKeySet?.let {
            return user.pubkeyHex in it
        }
            ?: false
    }

    fun isFollowingCached(userHex: String): Boolean {
        return latestContactList?.verifiedFollowKeySet?.let {
            return userHex in it
        }
            ?: false
    }

    fun transientFollowCount(): Int? {
        return latestContactList?.unverifiedFollowKeySet()?.size
    }

    suspend fun transientFollowerCount(): Int {
        return LocalCache.users.count { _, it -> it.latestContactList?.isTaggedUser(pubkeyHex) ?: false }
    }

    fun cachedFollowingKeySet(): Set<HexKey> {
        return latestContactList?.verifiedFollowKeySet ?: emptySet()
    }

    fun cachedFollowingTagSet(): Set<String> {
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
        return LocalCache.users.count { _, it -> it.latestContactList?.isTaggedUser(pubkeyHex) ?: false }
    }

    fun hasSentMessagesTo(key: ChatroomKey?): Boolean {
        val messagesToUser = privateChatrooms[key] ?: return false

        return messagesToUser.authors.any { this == it }
    }

    fun hasReport(
        loggedIn: User,
        type: ReportEvent.ReportType,
    ): Boolean {
        return reports[loggedIn]?.firstOrNull {
            it.event is ReportEvent &&
                (it.event as ReportEvent).reportedAuthor().any { it.reportType == type }
        } != null
    }

    fun anyNameStartsWith(username: String): Boolean {
        return info?.anyNameStartsWith(username) ?: false
    }

    var liveSet: UserLiveSet? = null
    var flowSet: UserFlowSet? = null

    fun live(): UserLiveSet {
        if (liveSet == null) {
            createOrDestroyLiveSync(true)
        }
        return liveSet!!
    }

    fun clearLive() {
        if (liveSet != null && liveSet?.isInUse() == false) {
            createOrDestroyLiveSync(false)
        }
    }

    @Synchronized
    fun createOrDestroyLiveSync(create: Boolean) {
        if (create) {
            if (liveSet == null) {
                liveSet = UserLiveSet(this)
            }
        } else {
            if (liveSet != null && liveSet?.isInUse() == false) {
                liveSet?.destroy()
                liveSet = null
            }
        }
    }

    @Synchronized
    fun createOrDestroyFlowSync(create: Boolean) {
        if (create) {
            if (flowSet == null) {
                flowSet = UserFlowSet(this)
            }
        } else {
            if (flowSet != null && flowSet?.isInUse() == false) {
                flowSet?.destroy()
                flowSet = null
            }
        }
    }

    fun flow(): UserFlowSet {
        if (flowSet == null) {
            createOrDestroyFlowSync(true)
        }
        return flowSet!!
    }

    fun clearFlow() {
        if (flowSet != null && flowSet?.isInUse() == false) {
            createOrDestroyFlowSync(false)
        }
    }
}

@Stable
class UserFlowSet(u: User) {
    // Observers line up here.
    val follows = UserBundledRefresherFlow(u)
    val relays = UserBundledRefresherFlow(u)

    fun isInUse(): Boolean {
        return relays.stateFlow.subscriptionCount.value > 0 || follows.stateFlow.subscriptionCount.value > 0
    }

    fun destroy() {
        relays.destroy()
        follows.destroy()
    }
}

@Stable
class UserLiveSet(u: User) {
    val innerMetadata = UserBundledRefresherLiveData(u)

    // UI Observers line up here.
    val innerFollows = UserBundledRefresherLiveData(u)
    val innerFollowers = UserBundledRefresherLiveData(u)
    val innerReports = UserBundledRefresherLiveData(u)
    val innerMessages = UserBundledRefresherLiveData(u)
    val innerRelays = UserBundledRefresherLiveData(u)
    val innerRelayInfo = UserBundledRefresherLiveData(u)
    val innerZaps = UserBundledRefresherLiveData(u)
    val innerBookmarks = UserBundledRefresherLiveData(u)
    val innerStatuses = UserBundledRefresherLiveData(u)

    // UI Observers line up here.
    val metadata = innerMetadata.map { it }
    val follows = innerFollows.map { it }
    val followers = innerFollowers.map { it }
    val reports = innerReports.map { it }
    val messages = innerMessages.map { it }
    val relays = innerRelays.map { it }
    val relayInfo = innerRelayInfo.map { it }
    val zaps = innerZaps.map { it }
    val bookmarks = innerBookmarks.map { it }
    val statuses = innerStatuses.map { it }

    val profilePictureChanges = innerMetadata.map { it.user.profilePicture() }.distinctUntilChanged()

    val nip05Changes = innerMetadata.map { it.user.nip05() }.distinctUntilChanged()

    val userMetadataInfo = innerMetadata.map { it.user.info }.distinctUntilChanged()

    fun isInUse(): Boolean {
        return metadata.hasObservers() ||
            follows.hasObservers() ||
            followers.hasObservers() ||
            reports.hasObservers() ||
            messages.hasObservers() ||
            relays.hasObservers() ||
            relayInfo.hasObservers() ||
            zaps.hasObservers() ||
            bookmarks.hasObservers() ||
            statuses.hasObservers() ||
            profilePictureChanges.hasObservers() ||
            nip05Changes.hasObservers() ||
            userMetadataInfo.hasObservers()
    }

    fun destroy() {
        innerMetadata.destroy()
        innerFollows.destroy()
        innerFollowers.destroy()
        innerReports.destroy()
        innerMessages.destroy()
        innerRelays.destroy()
        innerRelayInfo.destroy()
        innerZaps.destroy()
        innerBookmarks.destroy()
        innerStatuses.destroy()
    }
}

@Immutable
data class RelayInfo(
    val url: String,
    var lastEvent: Long,
    var counter: Long,
)

class UserBundledRefresherLiveData(val user: User) : LiveData<UserState>(UserState(user)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    fun destroy() {
        bundler.cancel()
    }

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate {
            checkNotInMainThread()

            postValue(UserState(user))
        }
    }

    fun <Y> map(transform: (UserState) -> Y): UserLoadingLiveData<Y> {
        val initialValue = this.value?.let { transform(it) }
        val result = UserLoadingLiveData(user, initialValue)
        result.addSource(this) { x -> result.value = transform(x) }
        return result
    }
}

@Stable
class UserBundledRefresherFlow(val user: User) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(500, Dispatchers.IO)
    val stateFlow = MutableStateFlow(UserState(user))

    fun destroy() {
        bundler.cancel()
    }

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate {
            checkNotInMainThread()

            stateFlow.emit(UserState(user))
        }
    }
}

class UserLoadingLiveData<Y>(val user: User, initialValue: Y?) : MediatorLiveData<Y>(initialValue) {
    override fun onActive() {
        super.onActive()
        NostrSingleUserDataSource.add(user)
    }

    override fun onInactive() {
        super.onInactive()
        NostrSingleUserDataSource.remove(user)
    }
}

@Immutable class UserState(val user: User)
