/**
 * Copyright (c) 2025 Vitor Pamplona
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
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache.RelayBriefInfo
import com.vitorpamplona.quartz.lightning.Lud06
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.tags.geohash.isTaggedGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHash
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.containsAny
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

@Stable
class User(
    val pubkeyHex: String,
) {
    var info: UserMetadata? = null

    var latestMetadata: MetadataEvent? = null
    var latestMetadataRelay: String? = null
    var latestContactList: ContactListEvent? = null
    var latestBookmarkList: BookmarkListEvent? = null

    var reports = mapOf<User, Set<Note>>()
        private set

    var zaps = mapOf<Note, Note?>()
        private set

    var relaysBeingUsed = mapOf<String, RelayInfo>()
        private set

    var privateChatrooms = mapOf<ChatroomKey, Chatroom>()
        private set

    fun pubkey() = Hex.decode(pubkeyHex)

    fun pubkeyNpub() = pubkey().toNpub()

    fun pubkeyDisplayHex() = pubkeyNpub().toShortenHex()

    fun authorRelayList() = (LocalCache.getAddressableNoteIfExists(AdvertisedRelayListEvent.createAddressTag(pubkeyHex))?.event as? AdvertisedRelayListEvent)

    fun toNProfile() = NProfile.create(pubkeyHex, relayHints())

    fun relayHints() = authorRelayList()?.writeRelays()?.take(3) ?: listOfNotNull(latestMetadataRelay)

    fun bestRelayHint() = authorRelayList()?.writeRelays()?.firstOrNull() ?: latestMetadataRelay

    fun toPTag() = PTag(pubkeyHex, bestRelayHint())

    fun toNostrUri() = "nostr:${toNProfile()}"

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

    fun toBestDisplayName(): String = info?.bestName() ?: pubkeyDisplayHex()

    fun nip05(): String? = info?.nip05

    fun profilePicture(): String? = info?.picture

    fun updateBookmark(event: BookmarkListEvent) {
        if (event.id == latestBookmarkList?.id) return

        latestBookmarkList = event
        flowSet?.bookmarks?.invalidateData()
    }

    fun updateContactList(event: ContactListEvent) {
        if (event.id == latestContactList?.id) return

        val oldContactListEvent = latestContactList
        latestContactList = event

        // Update following of the current user
        flowSet?.follows?.invalidateData()

        // Update Followers of the past user list
        // Update Followers of the new contact list
        (oldContactListEvent)?.unverifiedFollowKeySet()?.forEach {
            LocalCache
                .getUserIfExists(it)
                ?.flowSet
                ?.followers
                ?.invalidateData()
        }
        (latestContactList)?.unverifiedFollowKeySet()?.forEach {
            LocalCache
                .getUserIfExists(it)
                ?.flowSet
                ?.followers
                ?.invalidateData()
        }

        flowSet?.relays?.invalidateData()
    }

    fun addReport(note: Note) {
        val author = note.author ?: return

        val reportsBy = reports[author]
        if (reportsBy == null) {
            reports = reports + Pair(author, setOf(note))
            flowSet?.reports?.invalidateData()
        } else if (!reportsBy.contains(note)) {
            reports = reports + Pair(author, reportsBy + note)
            flowSet?.reports?.invalidateData()
        }
    }

    fun removeReport(deleteNote: Note) {
        val author = deleteNote.author ?: return

        if (reports[author]?.contains(deleteNote) == true) {
            reports[author]?.let {
                reports = reports + Pair(author, it.minus(deleteNote))
                flowSet?.reports?.invalidateData()
            }
        }
    }

    fun addZap(
        zapRequest: Note,
        zap: Note?,
    ) {
        if (zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            flowSet?.zaps?.invalidateData()
        }
    }

    fun removeZap(zapRequestOrZapEvent: Note) {
        if (zaps.containsKey(zapRequestOrZapEvent)) {
            zaps = zaps.minus(zapRequestOrZapEvent)
            flowSet?.zaps?.invalidateData()
        } else if (zaps.containsValue(zapRequestOrZapEvent)) {
            zaps = zaps.filter { it.value != zapRequestOrZapEvent }
            flowSet?.zaps?.invalidateData()
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

    fun reportsBy(user: User): Set<Note> = reports[user] ?: emptySet()

    fun countReportAuthorsBy(users: Set<HexKey>): Int = reports.count { it.key.pubkeyHex in users }

    fun reportsBy(users: Set<HexKey>): List<Note> =
        reports
            .mapNotNull {
                if (it.key.pubkeyHex in users) {
                    it.value
                } else {
                    null
                }
            }.flatten()

    @Synchronized
    private fun getOrCreatePrivateChatroomSync(key: ChatroomKey): Chatroom =
        privateChatrooms[key]
            ?: run {
                val privateChatroom = Chatroom()
                privateChatrooms = privateChatrooms + Pair(key, privateChatroom)
                privateChatroom
            }

    private fun getOrCreatePrivateChatroom(user: User): Chatroom {
        val key = ChatroomKey(persistentSetOf(user.pubkeyHex))
        return getOrCreatePrivateChatroom(key)
    }

    private fun getOrCreatePrivateChatroom(key: ChatroomKey): Chatroom = privateChatrooms[key] ?: getOrCreatePrivateChatroomSync(key)

    fun addMessage(
        room: ChatroomKey,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(room)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            flowSet?.messages?.invalidateData()
        }
    }

    fun addMessage(
        user: User,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            flowSet?.messages?.invalidateData()
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
            flowSet?.messages?.invalidateData()
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
            flowSet?.messages?.invalidateData()
        }
    }

    fun addRelayBeingUsed(
        relay: RelayBriefInfo,
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

        flowSet?.relayInfo?.invalidateData()
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

        flowSet?.metadata?.invalidateData()
    }

    fun isFollowing(user: User): Boolean = latestContactList?.isTaggedUser(user.pubkeyHex) ?: false

    fun isFollowingHashtag(tag: String) = latestContactList?.isTaggedHash(tag) ?: false

    fun isFollowingGeohash(geoTag: String) = latestContactList?.isTaggedGeoHash(geoTag) ?: false

    fun transientFollowCount(): Int? = latestContactList?.unverifiedFollowKeySet()?.size

    suspend fun transientFollowerCount(): Int = LocalCache.users.count { _, it -> it.latestContactList?.isTaggedUser(pubkeyHex) ?: false }

    fun hasSentMessagesTo(key: ChatroomKey?): Boolean {
        val messagesToUser = privateChatrooms[key] ?: return false

        return messagesToUser.authors.any { this == it }
    }

    fun hasReport(
        loggedIn: User,
        type: ReportType,
    ): Boolean =
        reports[loggedIn]?.firstOrNull {
            it.event is ReportEvent &&
                (it.event as ReportEvent).reportedAuthor().any { it.type == type }
        } != null

    fun containsAny(hiddenWordsCase: List<DualCase>): Boolean {
        if (hiddenWordsCase.isEmpty()) return false

        if (toBestDisplayName().containsAny(hiddenWordsCase)) {
            return true
        }

        if (profilePicture()?.containsAny(hiddenWordsCase) == true) {
            return true
        }

        if (info?.banner?.containsAny(hiddenWordsCase) == true) {
            return true
        }

        if (info?.about?.containsAny(hiddenWordsCase) == true) {
            return true
        }

        if (info?.lud06?.containsAny(hiddenWordsCase) == true) {
            return true
        }

        if (info?.lud16?.containsAny(hiddenWordsCase) == true) {
            return true
        }

        if (info?.nip05?.containsAny(hiddenWordsCase) == true) {
            return true
        }

        return false
    }

    fun anyNameStartsWith(username: String): Boolean = info?.anyNameStartsWith(username) ?: false

    var flowSet: UserFlowSet? = null

    @Synchronized
    fun createOrDestroyFlowSync(create: Boolean) {
        if (create) {
            if (flowSet == null) {
                flowSet = UserFlowSet(this)
            }
        } else {
            if (flowSet != null && flowSet?.isInUse() == false) {
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
class UserFlowSet(
    u: User,
) {
    // Observers line up here.
    val metadata = UserBundledRefresherFlow(u)
    val follows = UserBundledRefresherFlow(u)
    val relays = UserBundledRefresherFlow(u)
    val followers = UserBundledRefresherFlow(u)
    val reports = UserBundledRefresherFlow(u)
    val messages = UserBundledRefresherFlow(u)
    val relayInfo = UserBundledRefresherFlow(u)
    val zaps = UserBundledRefresherFlow(u)
    val bookmarks = UserBundledRefresherFlow(u)
    val statuses = UserBundledRefresherFlow(u)

    fun isInUse(): Boolean =
        metadata.hasObservers() ||
            relays.hasObservers() ||
            follows.hasObservers() ||
            followers.hasObservers() ||
            reports.hasObservers() ||
            messages.hasObservers() ||
            relayInfo.hasObservers() ||
            zaps.hasObservers() ||
            bookmarks.hasObservers() ||
            statuses.hasObservers()
}

@Immutable
data class RelayInfo(
    val url: String,
    var lastEvent: Long,
    var counter: Long,
)

@Stable
class UserBundledRefresherFlow(
    val user: User,
) {
    val stateFlow = MutableStateFlow(UserState(user))

    fun invalidateData() {
        stateFlow.tryEmit(UserState(user))
    }

    fun hasObservers() = stateFlow.subscriptionCount.value > 0
}

@Immutable
class UserState(
    val user: User,
)
