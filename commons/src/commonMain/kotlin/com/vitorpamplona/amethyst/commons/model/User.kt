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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserMetadataCache
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserRelaysCache
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.UserNip05Cache
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.UserStatusCache
import com.vitorpamplona.amethyst.commons.model.nip56Reports.UserReportCache
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.UserCardsCache
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Hex

interface UserDependencies

@Stable
class User(
    val pubkeyHex: String,
    val nip65RelayListNote: Note,
    val dmRelayListNote: Note,
) {
    // These objects are designed to keep the cache
    // while this user obj is being used anywhere.
    private var metadata: UserMetadataCache? = null
    private var reports: UserReportCache? = null
    private var cards: UserCardsCache? = null
    private var nip05: UserNip05Cache? = null
    private var status: UserStatusCache? = null
    private var relays: UserRelaysCache? = null

    fun pubkey() = Hex.decode(pubkeyHex)

    fun pubkeyNpub() = pubkey().toNpub()

    fun pubkeyDisplayHex() = pubkeyNpub().toShortDisplay(5)

    fun dmInboxRelayList() = dmRelayListNote.event as? ChatMessageRelayListEvent

    fun authorRelayList() = nip65RelayListNote.event as? AdvertisedRelayListEvent

    fun toNProfile() = NProfile.create(pubkeyHex, relayHints())

    fun outboxRelays() = authorRelayList()?.writeRelaysNorm()

    fun relayHints() = outboxRelays()?.take(3) ?: relays?.mostUsed()?.take(3) ?: emptyList()

    fun inboxRelays() = authorRelayList()?.readRelaysNorm()

    fun dmInboxRelays() = dmInboxRelayList()?.relays()?.ifEmpty { null } ?: inboxRelays()

    fun bestRelayHint() = authorRelayList()?.writeRelaysNorm()?.firstOrNull() ?: mostUsedNonLocalRelay()

    fun allUsedRelaysOrNull() = relays?.allOrNull()

    fun allUsedRelays() = relays?.allOrNull() ?: emptySet()

    fun mostUsedNonLocalRelay() = relays?.mostUsedNonLocalRelay()

    fun toPTag() = PTag(pubkeyHex, bestRelayHint())

    fun toNostrUri() = "nostr:${toNProfile()}"

    fun toBestDisplayName(): String = metadataOrNull()?.bestName() ?: pubkeyDisplayHex()

    fun profilePicture(): String? = metadataOrNull()?.profilePicture()

    fun lnAddress(): String? = metadataOrNull()?.lnAddress()

    fun addRelayBeingUsed(
        relay: NormalizedRelayUrl,
        eventTime: Long,
    ) = relayState().add(relay, eventTime)

    fun updateUserInfo(
        newUserInfo: UserMetadata,
        metaEvent: MetadataEvent,
    ) {
        newUserInfo.cleanBlankNames()
        newUserInfo.convertLud06toLud16IfNeeded()

        metadata().newMetadata(newUserInfo, metaEvent)
        // doesn't create Nip05 unless needed.
        nip05StateOrNull()?.newMetadata(newUserInfo.nip05, metaEvent.pubKey)
    }

    fun reportsOrNull(): UserReportCache? = reports

    fun reports(): UserReportCache = reports ?: UserReportCache().also { reports = it }

    fun cardsOrNull(): UserCardsCache? = cards

    fun cards(): UserCardsCache = cards ?: UserCardsCache().also { cards = it }

    fun metadataOrNull(): UserMetadataCache? = metadata

    fun metadata(): UserMetadataCache = metadata ?: UserMetadataCache().also { metadata = it }

    fun nip05StateOrNull(): UserNip05Cache? = nip05

    fun nip05State(): UserNip05Cache =
        nip05 ?: UserNip05Cache().also {
            nip05 = it
            val meta = metadata().flow.value
            if (meta != null) {
                it.newMetadata(meta.info.nip05, pubkeyHex)
            }
        }

    fun statusStateOrNull(): UserStatusCache? = status

    fun statusState(): UserStatusCache = status ?: UserStatusCache().also { status = it }

    fun relayStateOrNull(): UserRelaysCache? = relays

    fun relayState(): UserRelaysCache = relays ?: UserRelaysCache().also { relays = it }

    fun containsAny(hiddenWordsCase: List<DualCase>) = metadataOrNull()?.containsAny(hiddenWordsCase) == true
}

fun Set<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun Set<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()

fun List<User>.toHexes() = map { it.pubkeyHex }

fun List<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun List<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()
