/*
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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Hex

interface UserDependencies

/**
 * Lookup capability the [User] needs from the surrounding cache. Kept
 * narrow on purpose — only what the lazy pinned-note accessors require,
 * so test fakes are a one-liner and User stays decoupled from the full
 * `LocalCache` surface.
 */
fun interface UserContext {
    fun addressableNote(addr: Address): Note
}

@Stable
class User(
    val pubkeyHex: String,
    private val context: UserContext,
) {
    // ============================================================
    // Per-user pinned replaceable notes (kind:10002 / 10050 / 10019)
    // ============================================================
    // Each is resolved lazily on first read and then held by this
    // strong-reference field until the User itself is collected. The
    // underlying `LocalCache.addressables` map is WeakReference-backed,
    // so without these strong refs the note shells (and any event
    // loaded into them) could vanish on the next GC even though the
    // event was successfully delivered to the cache. Adding a new
    // pinned kind is a one-liner here.

    val nip65RelayListNote: Note by lazy {
        context.addressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))
    }

    val dmRelayListNote: Note by lazy {
        context.addressableNote(ChatMessageRelayListEvent.createAddress(pubkeyHex))
    }

    val nutzapInfoNote: Note by lazy {
        context.addressableNote(NutzapInfoEvent.createAddress(pubkeyHex))
    }

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

    fun nutzapInfo() = nutzapInfoNote.event as? NutzapInfoEvent

    /** True when this user has published a kind:10019 with a P2PK pubkey. */
    fun acceptsNutzaps(): Boolean = nutzapInfo()?.p2pkPubkey() != null

    /** Mints the user has declared accept nutzaps. Empty when no kind:10019. */
    fun nutzapMints(): List<NutzapMintTag> = nutzapInfo()?.mints().orEmpty()

    /** The recipient P2PK pubkey nutzaps to this user must lock to. */
    fun nutzapP2pkPubkey(): String? = nutzapInfo()?.p2pkPubkey()

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
}

fun Set<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun Set<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()

fun List<User>.toHexes() = map { it.pubkeyHex }

fun List<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun List<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()
