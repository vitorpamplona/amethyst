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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.nip56Reports.UserReportCache
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.UserCardsCache
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.quartz.lightning.Lud06
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.containsAny
import java.math.BigDecimal

interface UserDependencies

@Stable
class User(
    val pubkeyHex: String,
    val nip65RelayListNote: Note,
    val dmRelayListNote: Note,
) {
    private var reports: UserReportCache? = null
    private var cards: UserCardsCache? = null

    // private var deps = ScatterMap<KClass<out UserDependencies>, UserDependencies>()

    var info: UserMetadata? = null

    var latestMetadata: MetadataEvent? = null
    var latestMetadataRelay: NormalizedRelayUrl? = null
    var latestContactList: ContactListEvent? = null

    var zaps = mapOf<Note, Note?>()
        private set

    var relaysBeingUsed = mapOf<NormalizedRelayUrl, RelayInfo>()
        private set

    var flowSet: UserFlowSet? = null

    fun pubkey() = Hex.decode(pubkeyHex)

    fun pubkeyNpub() = pubkey().toNpub()

    fun pubkeyDisplayHex() = pubkeyNpub().toShortDisplay()

    fun dmInboxRelayList() = dmRelayListNote.event as? ChatMessageRelayListEvent

    fun authorRelayList() = nip65RelayListNote.event as? AdvertisedRelayListEvent

    fun toNProfile() = NProfile.create(pubkeyHex, relayHints())

    fun outboxRelays() = authorRelayList()?.writeRelaysNorm()

    fun relayHints() = authorRelayList()?.writeRelaysNorm()?.take(3) ?: listOfNotNull(latestMetadataRelay)

    fun inboxRelays() = authorRelayList()?.readRelaysNorm()

    fun dmInboxRelays() = dmInboxRelayList()?.relays()?.ifEmpty { null } ?: inboxRelays()

    fun bestRelayHint() = authorRelayList()?.writeRelaysNorm()?.firstOrNull() ?: latestMetadataRelay

    fun toPTag() = PTag(pubkeyHex, bestRelayHint())

    fun toNostrUri() = "nostr:${toNProfile()}"

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

    fun updateContactList(event: ContactListEvent): Set<HexKey> {
        if (event.id == latestContactList?.id) return emptySet()

        val oldContactListEvent = latestContactList
        latestContactList = event

        // Update following of the current user
        flowSet?.follows?.invalidateData()

        val affectedUsers = event.verifiedFollowKeySet() + (oldContactListEvent?.verifiedFollowKeySet() ?: emptySet())

        return affectedUsers
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

    fun addRelayBeingUsed(
        relay: NormalizedRelayUrl,
        eventTime: Long,
    ) {
        val here = relaysBeingUsed[relay]
        if (here == null) {
            relaysBeingUsed = relaysBeingUsed + Pair(relay, RelayInfo(relay, eventTime, 1))
        } else {
            if (eventTime > here.lastEvent) {
                here.lastEvent = eventTime
            }
            here.counter++
        }

        flowSet?.usedRelays?.invalidateData()
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

    fun transientFollowCount(): Int? = latestContactList?.unverifiedFollowKeySet()?.size

    fun reportsOrNull(): UserReportCache? = reports

    fun reports(): UserReportCache = reports ?: UserReportCache().also { reports = it }

    // fun reportsOrNull(): UserReports? = deps[UserReports::class] as? UserReports

    // fun reports(): UserReports = deps.getOrPut(UserReports::class) { UserReports() } as UserReports

    fun cardsOrNull(): UserCardsCache? = cards

    fun cards(): UserCardsCache = cards ?: UserCardsCache().also { cards = it }

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
    val followers = UserBundledRefresherFlow(u)
    val usedRelays = UserBundledRefresherFlow(u)
    val zaps = UserBundledRefresherFlow(u)
    val statuses = UserBundledRefresherFlow(u)

    fun isInUse(): Boolean =
        metadata.hasObservers() ||
            follows.hasObservers() ||
            followers.hasObservers() ||
            usedRelays.hasObservers() ||
            zaps.hasObservers() ||
            statuses.hasObservers()
}

@Immutable
data class RelayInfo(
    val url: NormalizedRelayUrl,
    var lastEvent: Long,
    var counter: Long,
)

// Re-export from commons.state for backwards compatibility
typealias UserBundledRefresherFlow = com.vitorpamplona.amethyst.commons.state.UserMetadataState
typealias UserState = com.vitorpamplona.amethyst.commons.state.UserState

fun Set<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun Set<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()

fun List<User>.toHexes() = map { it.pubkeyHex }

fun List<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun List<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()
