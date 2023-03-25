package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.service.model.BookmarkListEvent
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.MetadataEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.relays.Relay
import java.math.BigDecimal

interface UserInterface {
    val pubkeyHex: String

    var info: UserMetadata?
    var latestContactList: ContactListEvent?
    var latestBookmarkList: BookmarkListEvent?
    var zaps: Map<Note, Note?>
    var relaysBeingUsed: Map<String, RelayInfo>
    var notes: Set<Note>
    var reports: Map<UserInterface, Set<Note>>
    var latestReportTime: Long
    var privateChatrooms: Map<UserInterface, Chatroom>
    var acceptedBadges: AddressableNote?
    var liveSet: UserLiveSet?

    fun pubkey(): ByteArray

    fun pubkeyNpub(): String

    fun pubkeyDisplayHex(): String

    fun toBestDisplayName(): String

    fun bestUsername(): String?

    fun bestDisplayName(): String?

    fun nip05(): String?

    fun profilePicture(): String?

    fun updateBookmark(event: BookmarkListEvent)

    fun updateContactList(event: ContactListEvent)

    fun addNote(note: Note)

    fun removeNote(note: Note)

    fun clearNotes()

    fun addReport(note: Note)

    fun removeReport(deleteNote: Note)

    fun updateAcceptedBadges(note: AddressableNote)

    fun addZap(zapRequest: Note, zap: Note?)

    fun zappedAmount(): BigDecimal

    fun reportsBy(user: UserInterface): Set<Note>

    fun reportAuthorsBy(users: Set<HexKey>): List<UserInterface>

    fun countReportAuthorsBy(users: Set<HexKey>): Int

    fun reportsBy(users: Set<HexKey>): List<Note>

    fun getOrCreatePrivateChatroom(user: UserInterface): Chatroom

    fun addMessage(user: UserInterface, msg: Note)

    fun removeMessage(user: UserInterface, msg: Note)

    fun addRelayBeingUsed(relay: Relay, eventTime: Long)

    fun updateUserInfo(newUserInfo: UserMetadata, latestMetadata: MetadataEvent)

    fun isFollowing(user: UserInterface): Boolean

    fun isFollowingHashtag(tag: String): Boolean
    fun isFollowingHashtagCached(tag: String): Boolean

    fun isFollowingCached(user: UserInterface): Boolean

    fun transientFollowCount(): Int?

    fun transientFollowerCount(): Int

    fun cachedFollowingKeySet(): Set<HexKey>

    fun cachedFollowingTagSet(): Set<HexKey>

    fun cachedFollowCount(): Int?

    fun cachedFollowerCount(): Int

    fun hasSentMessagesTo(user: UserInterface?): Boolean

    fun hasReport(loggedIn: UserInterface, type: ReportEvent.ReportType): Boolean

    fun anyNameStartsWith(username: String): Boolean

    fun live(): UserLiveSet

    fun clearLive()
}
